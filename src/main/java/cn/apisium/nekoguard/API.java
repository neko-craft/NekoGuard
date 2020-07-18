package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.*;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.ObjectUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
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
    private ArrayList<ItemActionRecord> itemActionList = new ArrayList<>();
    private long curTime = Utils.getCurrentTime();

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

    private static Consumer<QueryResult> getCountConsumer(@NotNull final Consumer<Integer> onSuccess) {
        return it -> {
            final QueryResult.Series data = Utils.getFirstResult(it);
            if (data == null) onSuccess.accept(0);
            else onSuccess.accept(((Double) data.getValues().get(0).get(1)).intValue());
        };
    }

    public void recordChat(@NotNull final String msg, @NotNull final String user) {
        db.write(Point.measurement(chatRecords)
            .tag("player", user)
            .addField("message", msg)
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordBlock(@NotNull final Block block, @NotNull final String user, @NotNull final String action) {
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

    public void recordBlocks(@NotNull final List<Block> blocks, @NotNull final String user, @NotNull final String action) {
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

    public WhereQueryImpl<?> queryBlock(@NotNull final String world, final int x, final int y, final int z) {
        return select(Mappers.INSPECT_BLOCKS.getColumns())
            .from(db.database, blockRecords)
            .orderBy(desc())
            .where(eq("world", world))
            .and(eq("x", x))
            .and(eq("y", y))
            .and(eq("z", z));
    }

    public void queryBlockCount(@NotNull final String world, final int x, final int y, final int z, @NotNull final Consumer<Integer> onSuccess) {
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
    public void queryBlock(@NotNull final String world, final int x, final int y, final int z, final int page, @NotNull final Consumer<QueryResult> onSuccess) {
        final WhereQueryImpl<?> query = queryBlock(world, x, y, z);
        db.query(page == 0 ? query.limit(10) : query.limit(10, page * 10), onSuccess);
    }

    public void sendQueryBlockMessage(@NotNull final CommandSender sender, @NotNull final String world, final int x, final int y, final int z, final int page) {
        queryBlockCount(world, x, y, z, all ->
            queryBlock(world, x, y, z, page, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.INSPECT_BLOCKS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                try {
                    for (final Object[] arr : mapper.all()) sender.sendMessage(new TextComponent(" " + (arr[1].equals("0") ? "¡ìc-" : "¡ìa+") + " "),
                        Utils.getPerformerName((String) arr[2]),
                        Utils.formatTime((String) arr[3], now),
                        Utils.getBlockComponent((String) arr[0])
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        );
    }

    @NotNull
    public SelectQueryImpl queryChat(@Nullable final String player, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        final SelectQueryImpl query = select()
            .from(db.database, chatRecords)
            .orderBy(desc());
        if (fn != null) fn.accept(query);
        if (player == null) return page == 0 ? query.limit(10) : query.limit(10, page * 10);
        else {
            final WhereQueryImpl<?> query2 = query.where(eq("player", player));
            return page == 0 ? query2.limit(10) : query2.limit(10, page * 10);
        }
    }
    @NotNull
    public Query queryChatCount(@Nullable final String player, @Nullable final Consumer<SelectQueryImpl> fn) {
        final SelectQueryImpl query = select().count("message").from(db.database, chatRecords);
        if (fn != null) fn.accept(query);
        return player == null ? query : query.where(eq("player", player));
    }

    public void sendQueryChatMessage(@NotNull final CommandSender sender, @Nullable final String player, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        db.query(queryChatCount(player, fn), getCountConsumer(all -> db.query(queryChat(player, page, fn), res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) return;
            final SeriesMapper.Mapper mapper = Mappers.CHATS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            final String username = player == null ? null : Utils.getPlayerName(player);
            try {
                for (final Object[] arr : mapper.all()) sender.sendMessage(Utils.formatTime((String) arr[0], now),
                    new TextComponent(ObjectUtils.defaultIfNull(username, Utils.getPlayerName((String) arr[1])) +
                    "¡ì7: " + arr[2]));
            } catch (Exception e) {
                e.printStackTrace();
            }
            sender.sendMessage(Constants.makeFooter(page, all));
        })));
    }

    public void recordItemAction(@NotNull final ItemStack is, @NotNull final String performer, @NotNull final Inventory source, @NotNull final Inventory target) {
        recordItemAction(is, performer, Utils.getInventoryId(source), Utils.getInventoryId(target));
    }
    public void recordItemAction(@NotNull final ItemStack is, @NotNull final String performer, @NotNull final String source, @NotNull final String target) {
        itemActionList.add(new ItemActionRecord(is, performer, source, target, curTime++));
    }

    @NotNull
    public WhereQueryImpl<?> queryContainerActions(@NotNull final String world, final int x, final int y, final int z) {
        final String id = Utils.getBlockContainerId(world, x, y, z);
        return select()
            .from(db.database, containerRecords)
            .orderBy(desc())
            .where(eq("source", id))
            .or(eq("target", id));
    }

    @NotNull
    public WhereQueryImpl<?> queryContainerActionsCount(@NotNull final String world, final int x, final int y, final int z, @Nullable final Consumer<WhereQueryImpl<?>> fn) {
        final String id = Utils.getBlockContainerId(world, x, y, z);
        final WhereQueryImpl<?> query = select()
                .countAll()
                .from(db.database, containerRecords)
                .where(eq("source", id))
                .or(eq("target", id));
        if (fn != null) fn.accept(query);
        return query;
    }
    @NotNull
    public SelectQueryImpl queryContainerActions(@NotNull final String world, final int x, final int y, final int z, final int page, @Nullable final Consumer<WhereQueryImpl<?>> fn) {
        final WhereQueryImpl<?> query = queryContainerActions(world, x, y, z);
        if (fn != null) fn.accept(query);
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }

    public void sendContainerActionsMessage(@NotNull final CommandSender sender, @NotNull final String world, final int x, final int y, final int z, final int page, @Nullable final Consumer<WhereQueryImpl<?>> fn) {
        db.query(queryContainerActionsCount(world, x, y, z, fn), getCountConsumer(all ->
            db.query(queryContainerActions(world, x, y, z, page, fn), res -> {
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
                            Utils.getContainerPerformerName((String) arr[1]),
                            Utils.formatTime((String) arr[0], now),
                            Utils.getItemStackDetails(Utils.deserializeItemStack((String) arr[4]))
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        ));
    }
}
