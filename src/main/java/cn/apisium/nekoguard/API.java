package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.*;
import com.google.common.base.Strings;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.Ordering;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

public final class API {
    private final Database db;
    private final String chatRecords;
    private final String blockRecords;
    private final String containerRecords;
    private final static SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    protected final WeakHashMap<CommandSender, InspectBlock> inspectedBlocks = new WeakHashMap<>();
    private ArrayList<ItemActionRecord> itemActionList = new ArrayList<>();
    private long curTime = Utils.getCurrentTime();

    static {
        DF.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    API(final Database db, final String prefix, final Plugin plugin) {
        this.db = db;
        blockRecords = prefix + "Blocks";
        containerRecords = prefix + "Containers";
        chatRecords = prefix + "Chats";

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> curTime = Utils.getCurrentTime(), 0, 1);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            final ArrayList<ItemActionRecord> list = itemActionList;
            if (list.isEmpty()) return;
            itemActionList = new ArrayList<>();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> list.forEach(it ->
                db.write(Point.measurement(containerRecords)
                    .tag("performer", it.performer)
                    .addField("item", Utils.serializeItemStack(it.item))
                    .addField("source", it.source)
                    .addField("target", it.target)
                    .time(it.time, TimeUnit.NANOSECONDS)
                    .build())
                )
            );
        }, 0, 1);

    }

    private static Consumer<QueryResult> getCountConsumer(final Consumer<Integer> onSuccess) {
        return it -> {
            final QueryResult.Series data = Utils.getFirstResult(it);
            if (data == null) onSuccess.accept(0);
            else onSuccess.accept(((Double) data.getValues().get(0).get(1)).intValue());
        };
    }

    public void recordChat(final String msg, final String user) {
        db.write(Point.measurement(chatRecords)
            .tag("player", user)
            .addField("message", msg)
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordBlock(final Block block, final String user, final String action) {
        db.write(Point.measurement(blockRecords)
                .tag("world", block.getWorld().getName())
                .tag("performer", user)
                .tag("action", action)
                .addField("x", block.getX())
                .addField("y", block.getY())
                .addField("z", block.getZ())
                .addField("data", Utils.getFullBlockData(block))
                .time(curTime++, TimeUnit.NANOSECONDS)
                .build()
        );
    }

    public void recordBlocks(final List<Block> blocks, final String user, final String action) {
        final BatchPoints.Builder builder = BatchPoints.database(db.database)
            .tag("performer", user)
            .tag("action", action)
            .consistency(InfluxDB.ConsistencyLevel.ALL);
        for (final Block block : blocks) builder.point(Point.measurement(blockRecords)
            .tag("world", block.getWorld().getName())
            .addField("x", block.getX())
            .addField("y", block.getY())
            .addField("z", block.getZ())
            .addField("data", Utils.getFullBlockData(block))
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build());
        db.instance.write(builder.build());
    }

    public WhereQueryImpl<?> inspectBlock(final String world, final int x, final int y, final int z) {
        return select(Mappers.INSPECT_BLOCKS.getColumns())
            .from(db.database, blockRecords)
            .orderBy(desc())
            .where(eq("world", world))
            .and(eq("x", x))
            .and(eq("y", y))
            .and(eq("z", z));
    }

    public void inspectBlockCount(final String world, final int x, final int y, final int z, final Consumer<Integer> onSuccess) {
        db.query(select()
            .countAll()
            .from(db.database, blockRecords)
            .where(eq("world", world))
            .and(eq("x", x))
            .and(eq("y", y))
            .and(eq("z", z)),
            getCountConsumer(onSuccess)
        );
    }
    public void inspectBlock(final String world, final int x, final int y, final int z, final int page, final Consumer<QueryResult> onSuccess) {
        final WhereQueryImpl<?> query = inspectBlock(world, x, y, z);
        db.query(page == 0 ? query.limit(10) : query.limit(10, page * 10), onSuccess);
    }

    public void sendInspectBlockMessage(final CommandSender sender, final String world, final int x, final int y, final int z, final int page) {
        inspectBlockCount(world, x, y, z, all ->
            inspectBlock(world, x, y, z, page, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.INSPECT_BLOCKS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                try {
                    for (final Object[] arr : mapper.all()) {
                        final TranslatableComponent t = new TranslatableComponent(
                            Utils.getMaterialName((String) arr[0]));
                        t.setColor(ChatColor.YELLOW);
                        sender.sendMessage(new TextComponent(" " + (arr[1].equals("0") ? "¡ìc-" : "¡ìa+") + " "),
                            Utils.getPerformerName((String) arr[2]),
                            new TextComponent(formatTime(arr[3], now) + "  "), t);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.sendMessage(Constants.FOOTER);
            })
        );
    }

    public void lookupChat(@Nullable final String player, final int page, final Consumer<QueryResult> onSuccess) {
        final SelectQueryImpl query = select()
            .from(db.database, chatRecords)
            .orderBy(desc());
        if (player == null) db.query(page == 0 ? query.limit(10) : query.limit(10, page * 10), onSuccess);
        else {
            final WhereQueryImpl<?> query2 = query.where(eq("player", player));
            db.query(page == 0 ? query2.limit(10) : query2.limit(10, page * 10), onSuccess);
        }
    }
    public void lookupChatCount(@Nullable final String player, final Consumer<Integer> onSuccess) {
        final SelectQueryImpl query = select().count("message").from(db.database, chatRecords);
        db.query(player == null ? query : query.where(eq("player", player)), getCountConsumer(onSuccess));
    }

    public void sendLookupChatMessage(final CommandSender sender, @Nullable final String player, final int page) {
        lookupChatCount(player, all ->
            lookupChat(player, page, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.CHATS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                try {
                    for (final Object[] arr : mapper.all()) {
                        final TextComponent t = new TextComponent((String) arr[2]);
                        t.setColor(ChatColor.GRAY);
                        if (player == null) sender.sendMessage(new TextComponent(formatTime(arr[0], now) + "  "), t);
                        else sender.sendMessage(new TextComponent(formatTime(arr[0], now) + "¡ì7:  "),
                            Utils.getPerformerName((String) arr[1]), t);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.sendMessage(Constants.FOOTER);
            })
        );
    }

    public void recordItemAction(final ItemStack is, final String performer, final Inventory source, final Inventory target) {
        recordItemAction(is, performer, Utils.getInventoryId(source), Utils.getInventoryId(target));
    }
    public void recordItemAction(final ItemStack is, final String performer, final String source, final String target) {
        itemActionList.add(new ItemActionRecord(is, performer, source, target, curTime++));
    }

    public WhereQueryImpl<?> queryContainerActions(final String world, final int x, final int y, final int z) {
        final String id = Utils.getBlockContainerId(world, x, y, z);
        return select()
            .from(db.database, containerRecords)
            .orderBy(desc())
            .where(eq("source", id))
            .or(eq("target", id));
    }

    public void queryContainerActionsCount(final String world, final int x, final int y, final int z, final Consumer<Integer> onSuccess) {
        final String id = Utils.getBlockContainerId(world, x, y, z);
        db.query(select()
                .countAll()
                .from(db.database, containerRecords)
                .where(eq("source", id))
                .or(eq("target", id)),
            getCountConsumer(onSuccess)
        );
    }
    public void queryContainerActions(final String world, final int x, final int y, final int z, final int page, final Consumer<QueryResult> onSuccess) {
        final WhereQueryImpl<?> query = queryContainerActions(world, x, y, z);
        db.query(page == 0 ? query.limit(10) : query.limit(10, page * 10), onSuccess);
    }

    public void sendContainerActionsMessage(final CommandSender sender, final String world, final int x, final int y, final int z, final int page) {
        queryContainerActionsCount(world, x, y, z, all ->
            queryContainerActions(world, x, y, z, page, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.CONTAINER_ACTIONS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                final String id = Utils.getBlockContainerId(world, x, y, z);
                try {
                    for (final Object[] arr : mapper.all()) {
                        final TextComponent t = new TextComponent((String) arr[2]);
                        t.setColor(ChatColor.GRAY);
                        sender.sendMessage(
                            new TextComponent(" " + (id.equals(arr[2]) ? "¡ìc-" : "¡ìa+") + " "),
                            Utils.getItemStackDetails(Utils.deserializeItemStack((String) arr[4])),
                            new TextComponent("  "),
                            Utils.getContainerPerformerName((String) arr[1]),
                            new TextComponent(formatTime(arr[0], now))
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.sendMessage(Constants.FOOTER);
            })
        );
    }

    private static String formatTime(final Object time, final long now) {
        return "   ¡ì7¡ìo" + Strings.padEnd(Utils.formatDuration((String) time, now), 13, ' ');
    }
}
