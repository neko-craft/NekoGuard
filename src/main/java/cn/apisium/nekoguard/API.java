package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.*;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.ObjectUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
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
    private final String killRecords;
    private final String blockRecords;
    private final String commandRecords;
    private final String containerRecords;
    private ArrayList<ItemActionRecord> itemActionList = new ArrayList<>();
    private long curTime = Utils.getCurrentTime();

    API(final Database db, final String prefix, final Plugin plugin) {
        this.db = db;
        blockRecords = prefix + "Blocks";
        containerRecords = prefix + "Containers";
        chatRecords = prefix + "Chats";
        commandRecords = prefix + "Commands";
        killRecords = prefix + "Kills";

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> curTime = Utils.getCurrentTime(), 0, 1);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            final ArrayList<ItemActionRecord> list = itemActionList;
            if (list.isEmpty()) return;
            itemActionList = new ArrayList<>();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> list.forEach(it ->
                db.write(Point.measurement(containerRecords)
                    .tag("performer", it.performer)
                    .addField("item", NMSUtils.serializeItemStack(it.item))
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

    public void recordCommand(@NotNull final String command, @NotNull final String type, @Nullable final String performer) {
        final Point.Builder builder = Point.measurement(commandRecords)
            .tag("type", type)
            .addField("command", command);
        if (performer != null) builder.addField("performer", performer);
        db.write(builder
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordCommand(@NotNull final String command, @NotNull final String performer) {
        db.write(Point.measurement(commandRecords)
            .tag("type", performer)
            .addField("command", command)
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordBlockBreak(@NotNull final Block block, @NotNull final String performer) {
        recordBlockBreak(block.getState(), performer);
    }
    public void recordBlockBreak(@NotNull final Block block, @NotNull final Player performer) {
        recordBlockBreak(block.getState(), performer.getUniqueId().toString());
    }
    public void recordBlockBreak(@NotNull final BlockState block, @NotNull final String performer) {
        db.write(Point.measurement(blockRecords)
                .tag("world", block.getWorld().getName())
                .tag("performer", performer)
                .tag("action", Constants.BLOCK_ACTION_BREAK)
                .addField("x", block.getX())
                .addField("y", block.getY())
                .addField("z", block.getZ())
                .addField("data", Utils.getFullBlockData(block))
                .time(curTime++, TimeUnit.NANOSECONDS)
                .build()
        );
    }

    public void recordBlockPlace(@NotNull final Block block, @NotNull final Player performer) {
        recordBlockPlace(block.getState(), performer.getUniqueId().toString());
    }
    public void recordBlockPlace(@NotNull final BlockState block, @NotNull final String performer) {
        recordBlockPlace(block, block.getType(), performer);
    }
    public void recordBlockPlace(@NotNull final BlockState block, final Material type, @NotNull final String performer) {
        db.write(Point.measurement(blockRecords)
            .tag("world", block.getWorld().getName())
            .tag("performer", performer)
            .tag("action", Constants.BLOCK_ACTION_PLACE)
            .addField("x", block.getX())
            .addField("y", block.getY())
            .addField("z", block.getZ())
            .addField("data", type.getKey().toString())
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordBlocksBreak(@NotNull final List<Block> blocks, @NotNull final String performer) {
        final BatchPoints.Builder builder = BatchPoints.database(db.database)
            .tag("performer", performer)
            .tag("action", Constants.BLOCK_ACTION_BREAK)
            .consistency(InfluxDB.ConsistencyLevel.ALL);
        for (final Block block : blocks) builder.point(Point.measurement(blockRecords)
            .tag("world", block.getWorld().getName())
            .addField("x", block.getX())
            .addField("y", block.getY())
            .addField("z", block.getZ())
            .addField("data", Utils.getFullBlockData(block.getState()))
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build());
        db.instance.write(builder.build());
    }

    @NotNull
    public SelectQueryImpl queryBlock() {
        return select()
            .from(db.database, blockRecords)
            .orderBy(desc());
    }
    @NotNull
    public Consumer<SelectQueryImpl> getBlockQueryFunction(@NotNull final String world, final int x, final int y, final int z) {
        return it -> it.where(eq("world", world))
            .and(eq("x", x))
            .and(eq("y", y))
            .and(eq("z", z));
    }

    @NotNull
    public SelectQueryImpl queryBlockCount() {
        return select()
            .countAll()
            .from(db.database, blockRecords);
    }

    @NotNull
    public SelectQueryImpl queryBlock(final int page) {
        final SelectQueryImpl query = queryBlock();
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }

    public void sendQueryBlockMessage(@NotNull final CommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        final SelectQueryImpl q1 = queryBlockCount(), q2 = queryBlock(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.BLOCKS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                try {
                    for (final Object[] arr : mapper.all()) sender.sendMessage(
                        Utils.getBlockActionComponent(arr[1].equals("1"), (String) arr[4],
                            ((Double) arr[5]).intValue(), ((Double) arr[6]).intValue(), ((Double) arr[7]).intValue()),
                        Utils.getPerformerName((String) arr[2]),
                        Utils.formatTime((String) arr[3], now),
                        Utils.getBlockComponent((String) arr[0])
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        ));
    }
    public void sendQueryBlockMessage(@NotNull final CommandSender sender, @NotNull final String world, final int x, final int y, final int z, final int page) {
        sendQueryBlockMessage(sender, page, getBlockQueryFunction(world, x, y, z));
    }

    @NotNull
    public SelectQueryImpl queryChat(@Nullable final String player, final int page) {
        final SelectQueryImpl query = select()
            .from(db.database, chatRecords)
            .orderBy(desc());
        if (player == null) return page == 0 ? query.limit(10) : query.limit(10, page * 10);
        else {
            final WhereQueryImpl<?> query2 = query.where(eq("player", player));
            return page == 0 ? query2.limit(10) : query2.limit(10, page * 10);
        }
    }
    @NotNull
    public SelectQueryImpl queryChatCount(@Nullable final String player) {
        final SelectQueryImpl query = select().countAll().from(db.database, chatRecords);
        if (player != null) query.where(eq("player", player));
        return query;
    }

    public void sendQueryChatMessage(@NotNull final CommandSender sender, @Nullable final String player, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        final SelectQueryImpl q1 = queryChatCount(player), q2 = queryChat(player, page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, getCountConsumer(all -> db.query(q2, res -> {
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
        final String id = Utils.getBlockPerformer(world, x, y, z);
        return select()
            .from(db.database, containerRecords)
            .orderBy(desc())
            .where(eq("source", id))
            .or(eq("target", id));
    }

    @NotNull
    public SelectQueryImpl queryContainerActionsCount(@NotNull final String world, final int x, final int y, final int z) {
        final String id = Utils.getBlockPerformer(world, x, y, z);
        final SelectQueryImpl query = select()
            .countAll()
            .from(db.database, containerRecords);
        query
                .where(eq("source", id))
                .or(eq("target", id));
        return query;
    }
    @NotNull
    public SelectQueryImpl queryContainerActions(@NotNull final String world, final int x, final int y, final int z, final int page) {
        final WhereQueryImpl<?> query = queryContainerActions(world, x, y, z);
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }

    public void sendContainerActionsMessage(@NotNull final CommandSender sender, @NotNull final String world, final int x, final int y, final int z, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        final SelectQueryImpl q1 = queryContainerActionsCount(world, x, y, z), q2 = queryContainerActions(world, x, y, z, page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.CONTAINER_ACTIONS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                final String id = Utils.getBlockPerformer(world, x, y, z);
                try {
                    for (final Object[] arr : mapper.all()) {
                        final TextComponent t = new TextComponent((String) arr[2]);
                        t.setColor(ChatColor.GRAY);
                        sender.sendMessage(
                            Utils.getContainerActionComponent(!id.equals(arr[2]), (String) arr[1], (String) arr[2], (String) arr[3]),
                            Utils.getContainerPerformerName((String) arr[1]),
                            Utils.formatTime((String) arr[0], now),
                            Utils.getItemStackDetails(NMSUtils.deserializeItemStack((String) arr[4]))
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        ));
    }

    @NotNull
    public SelectQueryImpl queryCommand(@Nullable final String type, final int page) {
        final SelectQueryImpl query = select()
            .from(db.database, commandRecords)
            .orderBy(desc());
        if (type == null) return page == 0 ? query.limit(10) : query.limit(10, page * 10);
        else {
            final WhereQueryImpl<?> query2 = query.where(eq("type", type));
            return page == 0 ? query2.limit(10) : query2.limit(10, page * 10);
        }
    }
    @NotNull
    public SelectQueryImpl queryCommandCount(@Nullable final String type) {
        final SelectQueryImpl query = select().countAll().from(db.database, commandRecords);
        if (type != null) query.where(eq("type", type));
        return query;
    }

    @SuppressWarnings("deprecation")
    public void sendQueryCommandMessage(@NotNull final CommandSender sender, @Nullable String type, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        final String type1;
        if (type != null && !CommandSenderType.getValueList().contains(type)) type1 = Bukkit.getOfflinePlayer(type).getUniqueId().toString();
        else type1 = type;
        final SelectQueryImpl q1 = queryCommandCount(type1), q2 = queryCommand(type1, page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, getCountConsumer(all -> db.query(q2, res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) return;
            final SeriesMapper.Mapper mapper = Mappers.COMMANDS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            try {
                for (final Object[] arr : mapper.all()) {
                    final TextComponent t = new TextComponent(": " + arr[3]);
                    t.setColor(ChatColor.GRAY);
                    sender.sendMessage(Utils.formatTime((String) arr[0], now),
                        Utils.getPlayerCommandNameComponent((String) arr[1], (String) arr[2]),
                        t
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            sender.sendMessage(Constants.makeFooter(page, all));
        })));
    }
}
