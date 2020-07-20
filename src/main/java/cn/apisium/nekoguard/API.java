package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

public final class API {
    private final Database db;
    private final String chatRecords;
    private final String deathRecords;
    private final String blockRecords;
    private final String commandRecords;
    private final String containerRecords;
    private ArrayList<ItemActionRecord> itemActionList = new ArrayList<>();
    private long curTime = Utils.getCurrentTime();
    private final Main main;

    API(final String prefix, final Main plugin) {
        this.db = plugin.getDatabase();
        main = plugin;
        blockRecords = prefix + "Blocks";
        containerRecords = prefix + "Containers";
        chatRecords = prefix + "Chats";
        commandRecords = prefix + "Commands";
        deathRecords = prefix + "Deaths";

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

    public void recordChat(@NotNull final String msg, @NotNull final String performer) {
        db.write(Point.measurement(chatRecords)
            .tag("player", performer)
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

    public void recordDeath(@NotNull final String performer, @NotNull final Entity entity, @NotNull final String cause) {
        final Location loc = entity.getLocation();
        db.write(Point.measurement(deathRecords)
            .tag("performer", performer)
            .tag("cause", cause)
            .tag("type", '@' + entity.getType().getKey().toString())
            .tag("world", loc.getWorld().getName())
            .addField("entity", NMSUtils.serializeEntity(entity))
            .addField("x", loc.getBlockX())
            .addField("y", loc.getBlockY())
            .addField("z", loc.getBlockZ())
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }
    public void recordPlayerDeath(@NotNull final String performer, @NotNull final Player player, @NotNull final String cause, @Nullable final List<ItemStack> drops, final int exp) {
        final String str;
        if (main.recordItemDropsOfPlayerDeath) {
            final JsonObject json = new JsonObject();
            if (drops != null && !drops.isEmpty()) {
                final JsonArray arr = new JsonArray();
                drops.forEach(it -> arr.add(NMSUtils.serializeItemStack(it)));
                json.add("drops", arr);
            }
            if (exp > 0) json.addProperty("exp", exp);
            str = json.size() == 0 ? "" : json.toString();
        } else str = "";
        final Location loc = player.getLocation();
        db.write(Point.measurement(deathRecords)
            .tag("performer", performer)
            .tag("cause", cause)
            .tag("type", player.getUniqueId().toString())
            .tag("world", loc.getWorld().getName())
            .addField("entity", str)
            .addField("x", loc.getBlockX())
            .addField("y", loc.getBlockY())
            .addField("z", loc.getBlockZ())
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

    public void recordItemAction(@NotNull final ItemStack is, @NotNull final String performer, @NotNull final Inventory source, @NotNull final Inventory target) {
        recordItemAction(is, performer, Utils.getInventoryId(source), Utils.getInventoryId(target));
    }
    public void recordItemAction(@NotNull final ItemStack is, @NotNull final String performer, @NotNull final String source, @NotNull final String target) {
        if (source.isEmpty() && target.isEmpty()) return;
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

    @NotNull
    public SelectQueryImpl queryDeath() {
        return select()
            .from(db.database, deathRecords)
            .orderBy(desc());
    }

    @NotNull
    public SelectQueryImpl queryDeathCount() {
        return select()
            .countAll()
            .from(db.database, deathRecords);
    }

    @NotNull
    public SelectQueryImpl queryDeath(final int page) {
        final SelectQueryImpl query = queryDeath();
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }
}
