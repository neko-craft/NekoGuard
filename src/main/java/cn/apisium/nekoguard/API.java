package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Piston;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.influxdb.InfluxDB;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

public final class API {
    private final Database db;
    private final String chatRecords;
    private final String deathRecords;
    private final String blockRecords;
    private final String spawnRecords;
    private final String commandRecords;
    private final String containerRecords;
    private final String itemsRecords;
    private final String sessionsRecords;
    private ArrayList<ContainerAction> containerActionList = new ArrayList<>();
    private long curTime = Utils.getCurrentTime();
    private final static Pattern ZERO_HEALTH = Pattern.compile(",Health:0\\.0f|Health:0\\.0f,|Health:0\\.0f");

    API(final String prefix, final Main plugin) {
        this.db = plugin.getDatabase();
        blockRecords = prefix + "Blocks";
        containerRecords = prefix + "Containers";
        chatRecords = prefix + "Chats";
        commandRecords = prefix + "Commands";
        deathRecords = prefix + "Deaths";
        spawnRecords = prefix + "Spawns";
        itemsRecords = prefix + "Items";
        sessionsRecords = prefix + "Sessions";

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> curTime = Utils.getCurrentTime(), 0, 1);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            final ArrayList<ContainerAction> list = containerActionList;
            if (list.isEmpty()) return;
            containerActionList = new ArrayList<>();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> list.forEach(it -> {
                final Point.Builder builder = Point.measurement(containerRecords)
                    .addField("item", NMSUtils.serializeItemStack(it.item))
                    .time(it.time, TimeUnit.NANOSECONDS);
                if (it.sourceWorld == null) {
                    if (it.sourceEntity != null) builder
                        .tag("se", it.sourceEntity)
                        .tag("sw", "")
                        .addField("sx", (Number) null)
                        .addField("sy", (Number) null)
                        .addField("sz", (Number) null);
                } else builder
                    .tag("se", "")
                    .tag("sw", it.sourceWorld)
                    .addField("sx", it.sourceX)
                    .addField("sy", it.sourceY)
                    .addField("sz", it.sourceZ);
                if (it.targetWorld == null) {
                    if (it.targetEntity != null) builder
                        .tag("te", it.targetEntity)
                        .tag("tw", "")
                        .addField("tx", (Number) null)
                        .addField("ty", (Number) null)
                        .addField("tz", (Number) null);
                } else {
                    builder
                        .tag("te", "")
                        .tag("tw", it.targetWorld)
                        .addField("tx", it.targetX)
                        .addField("ty", it.targetY)
                        .addField("tz", it.targetZ);
                }
                db.write(builder.build());
            }));
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

    public void recordItemAction(@NotNull final ItemStack is, final boolean isDrop, @NotNull final String performer, @NotNull final String world, final double x, final double y, final double z) {
        db.write(Point.measurement(itemsRecords)
            .tag("performer", performer)
            .tag("world", world)
            .tag("action", isDrop ? "0" : "1")
            .addField("item", NMSUtils.serializeItemStack(is))
            .addField("x", x)
            .addField("y", y)
            .addField("z", z)
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordSpawn(@NotNull final Entity entity, @Nullable final String reason) {
        final Location loc = entity.getLocation();
        final Point.Builder builder = Point.measurement(spawnRecords)
            .tag("type", entity.getType().getKey().toString())
            .tag("world", loc.getWorld().getName())
            .addField("x", loc.getBlockX())
            .addField("y", loc.getBlockY())
            .addField("z", loc.getBlockZ())
            .addField("id", entity.getUniqueId().toString())
            .time(curTime++, TimeUnit.NANOSECONDS);
        if (reason != null) builder.tag("reason", reason);
        db.write(builder.build());
    }

    public void recordDeath(@NotNull final String performer, @NotNull final Entity entity, @NotNull final String cause) {
        final Location loc = entity.getLocation();
        db.write(Point.measurement(deathRecords)
            .tag("performer", performer)
            .tag("cause", cause)
            .tag("type", '@' + entity.getType().getKey().toString())
            .tag("world", loc.getWorld().getName())
            .addField("entity", ZERO_HEALTH.matcher(NMSUtils.serializeEntity(entity)).replaceAll(""))
            .addField("x", loc.getBlockX())
            .addField("y", loc.getBlockY())
            .addField("z", loc.getBlockZ())
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }
    public void recordPlayerDeath(@NotNull final String performer, @NotNull final Player player, @NotNull final String cause, final int exp) {
        final Location loc = player.getLocation();
        db.write(Point.measurement(deathRecords)
            .tag("performer", performer)
            .tag("cause", cause)
            .tag("type", player.getUniqueId().toString())
            .tag("world", loc.getWorld().getName())
            .addField("entity", String.valueOf(exp))
            .addField("x", loc.getBlockX())
            .addField("y", loc.getBlockY())
            .addField("z", loc.getBlockZ())
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordPlayerSession(@NotNull final Player player, final boolean isLogin) {
        final Location loc = player.getLocation();
        db.write(Point.measurement(sessionsRecords)
            .tag("id", player.getUniqueId().toString())
            .tag("name", player.getName())
            .tag("action", isLogin ? "0" : "1")
            .tag("world", loc.getWorld().getName())
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
                .addField("block", Utils.getFullBlockData(block))
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
            .addField("block", type.getKey().toString())
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
            .addField("block", Utils.getFullBlockData(block.getState()))
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build());
        db.instance.write(builder.build());
    }

    @NotNull
    public SelectQueryImpl queryBlock() {
        return select().from(db.database, blockRecords).orderBy(desc());
    }

    @NotNull
    public SelectQueryImpl queryBlockCount() {
        return select().countAll().from(db.database, blockRecords);
    }

    @NotNull
    public SelectQueryImpl queryBlock(final int page) {
        final SelectQueryImpl query = queryBlock();
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }

    @NotNull
    public SelectQueryImpl queryChat(@Nullable final String player, final int page) {
        final SelectQueryImpl query = select().from(db.database, chatRecords).orderBy(desc());
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

    public void recordContainerAction(@NotNull final ItemStack is, @Nullable final Inventory source, @Nullable final Inventory target) {
        if (source == null && target == null) return;
        final ContainerAction action = new ContainerAction(is, source, target, curTime);
        if (action.sourceEntity == null && action.sourceWorld == null && action.targetEntity == null &&
            action.targetWorld == null) return;
        curTime++;
        containerActionList.add(action);
    }
    @SuppressWarnings("unused")
    public void recordContainerAction(@NotNull final ItemStack is, @Nullable final String source, @Nullable final String target) {
        if (source == null && target == null) return;
        containerActionList.add(new ContainerAction(is, source, target, curTime++));
    }
    public void recordContainerAction(@NotNull final ItemStack is, @Nullable final Inventory source, @Nullable final String target) {
        if (source == null && target == null) return;
        containerActionList.add(new ContainerAction(is, source, target, curTime++));
    }
    public void recordContainerAction(@NotNull final ItemStack is, @Nullable final String source, @Nullable final Inventory target) {
        if (source == null && target == null) return;
        containerActionList.add(new ContainerAction(is, source, target, curTime++));
    }

    @NotNull
    public SelectQueryImpl queryContainerActions() {
        return select().from(db.database, containerRecords).orderBy(desc());
    }

    @NotNull
    public SelectQueryImpl queryContainerActionsCount() {
        return select().countAll().from(db.database, containerRecords);
    }
    @NotNull
    public SelectQueryImpl queryContainerActions(final int page) {
        final SelectQueryImpl query = queryContainerActions();
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }

    @NotNull
    public SelectQueryImpl queryCommand(@Nullable final String type, final int page) {
        final SelectQueryImpl query = select().from(db.database, commandRecords).orderBy(desc());
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
        return select().from(db.database, deathRecords).orderBy(desc());
    }

    @NotNull
    public SelectQueryImpl queryDeathCount() {
        return select().countAll().from(db.database, deathRecords);
    }

    @NotNull
    public SelectQueryImpl queryDeath(final int page) {
        final SelectQueryImpl query = queryDeath();
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }

    public static void processSingleContainerBlockQuery(@NotNull final SelectQueryImpl query, @NotNull final String world, final int x, final int y, final int z) {
        query.where()
            .andNested().and(eq("sw", world)).and(eq("sx", x)).and(eq("sy", y)).and(eq("sz", z)).close()
            .orNested().and(eq("tw", world)).and(eq("tx", x)).and(eq("ty", y)).and(eq("tz", z)).close();
    }
    public static void processSingleContainerEntityQuery(@NotNull final SelectQueryImpl query, @NotNull final String entity) {
        query.where().andNested().and(eq("se", entity)).or(eq("te", entity)).close();
    }

    @NotNull
    public SelectQueryImpl querySpawn() {
        return select().from(db.database, spawnRecords).orderBy(desc());
    }

    @NotNull
    public SelectQueryImpl querySpawnCount() {
        return select().countAll().from(db.database, spawnRecords);
    }
    @NotNull
    public SelectQueryImpl querySpawn(final int page) {
        final SelectQueryImpl query = querySpawn();
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }

    @NotNull
    public SelectQueryImpl queryItemAction() {
        return select().from(db.database, itemsRecords).orderBy(desc());
    }

    @NotNull
    public SelectQueryImpl queryItemActionCount() {
        return select().countAll().from(db.database, itemsRecords);
    }
    @NotNull
    public SelectQueryImpl queryItemAction(final int page) {
        final SelectQueryImpl query = queryItemAction();
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }

    public void fetchActionItemIntoInventory(@NotNull final Inventory inv, @NotNull final String time, @Nullable final Consumer<Boolean> callback) {
        fetchItemIntoInventory(itemsRecords, inv, time, callback);
    }
    public void fetchContainerItemIntoInventory(@NotNull final Inventory inv, @NotNull final String time, @Nullable final Consumer<Boolean> callback) {
        fetchItemIntoInventory(containerRecords, inv, time, callback);
    }
    public void fetchItemIntoInventory(@NotNull final String table, @NotNull final Inventory inv, @NotNull final String time, @Nullable final Consumer<Boolean> callback) {
        db.query(select("item").from(db.database, table).where(eq("time", time)), res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null || data.getValues().size() == 0) {
                if (callback != null) callback.accept(false);
                return;
            }
            final ItemStack is = NMSUtils.deserializeItemStack((String) data.getValues().get(0)
                .get("item".equals(data.getColumns().get(0)) ? 0 : 1));
            if (is == null) {
                if (callback != null) callback.accept(false);
            } else {
                inv.addItem(is);
                if (callback != null) callback.accept(true);
            }
        });
    }

    @NotNull
    public SelectQueryImpl querySession() {
        return select().from(db.database, sessionsRecords).orderBy(desc());
    }

    @NotNull
    public SelectQueryImpl querySessionCount() {
        return select().countAll().from(db.database, sessionsRecords);
    }

    @NotNull
    public SelectQueryImpl querySession(final int page) {
        final SelectQueryImpl query = querySession();
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }
}
