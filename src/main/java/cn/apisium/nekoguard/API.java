package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.*;
import org.influxdb.dto.Point;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

public final class API {
    public final String chatRecords;
    public final String deathRecords;
    public final String blockRecords;
    public final String spawnRecords;
    public final String commandRecords;
    public final String containerRecords;
    public final String itemsRecords;
    public final String sessionsRecords;
    public final String explosionsRecords;
    private final Database db;
    private long curTime = Utils.getCurrentTime();
    protected final Timer timer;
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
        explosionsRecords = prefix + "Explosions";

        timer = new Timer("NekoGuard-Timer");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                curTime = Utils.getCurrentTime();
            }
        }, 50, 50);
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

    public void recordItemAction(@NotNull final String item, final boolean isDrop, @NotNull final String performer, @NotNull final String world, final double x, final double y, final double z) {
        db.write(Point.measurement(itemsRecords)
            .tag("performer", performer)
            .tag("world", world)
            .tag("action", isDrop ? "0" : "1")
            .addField("item", item)
            .addField("x", x)
            .addField("y", y)
            .addField("z", z)
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordSpawn(@NotNull final String type, @Nullable final String reason, @NotNull final String world, final int x, final int y, final int z, @NotNull final String id) {
        final Point.Builder builder = Point.measurement(spawnRecords)
            .tag("type", type)
            .tag("reason", reason == null ? "" : reason)
            .tag("world", world)
            .addField("x", x)
            .addField("y", y)
            .addField("z", z)
            .addField("id", id)
            .time(curTime++, TimeUnit.NANOSECONDS);
        db.write(builder.build());
    }

    public void recordDeath(@NotNull final String performer, @NotNull final String cause, @NotNull final String type, @NotNull final String world, @NotNull final String data, final int x, final int y, final int z) {
        db.write(Point.measurement(deathRecords)
            .tag("performer", performer)
            .tag("cause", cause)
            .tag("type", '@' + type)
            .tag("world", world)
            .addField("entity", ZERO_HEALTH.matcher(data).replaceAll(""))
            .addField("x", x)
            .addField("y", y)
            .addField("z", z)
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }
    public void recordPlayerDeath(@NotNull final String performer, @NotNull final String cause, @NotNull final String id, @NotNull final String world, final int x, final int y, final int z, final int exp) {
        db.write(Point.measurement(deathRecords)
            .tag("performer", performer)
            .tag("cause", cause)
            .tag("type", id)
            .tag("world", world)
            .addField("x", x)
            .addField("y", y)
            .addField("z", z)
            .addField("entity", String.valueOf(exp))
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordPlayerSession(@NotNull final String id, @NotNull final String name, final boolean isLogin, @NotNull final String world, final int x, final int y, final int z, @NotNull final String address) {
        db.write(Point.measurement(sessionsRecords)
            .tag("id", id)
            .tag("name", name)
            .tag("action", isLogin ? "0" : "1")
            .tag("world", world)
            .addField("x", x)
            .addField("y", y)
            .addField("z", z)
            .addField("address", address)
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordBlockAction(@NotNull final String performer, final boolean isBreak, @NotNull final String world, final int x, final int y, final int z, @NotNull final String block) {
        db.write(Point.measurement(blockRecords)
            .tag("performer", performer)
            .tag("action", isBreak ? "0" : "1")
            .tag("world", world)
            .addField("x", x)
            .addField("y", y)
            .addField("z", z)
            .addField("block", block)
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
    }

    public void recordExplosion(@NotNull final String type, @NotNull final String target, final boolean isNear, @NotNull final String world, final int x, final int y, final int z) {
        db.write(Point.measurement(explosionsRecords)
            .tag("type", type)
            .tag("target", target)
            .tag("world", world)
            .tag("near", isNear ? "0" : "1")
            .addField("x", x)
            .addField("y", y)
            .addField("z", z)
            .time(curTime++, TimeUnit.NANOSECONDS)
            .build()
        );
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

    @SuppressWarnings("unused")
    public void recordContainerAction(@NotNull final String item, @Nullable final ContainerRecord source, @Nullable final ContainerRecord target, final long time) {
        if (source == null && target == null) return;
        final Point.Builder builder = Point.measurement(containerRecords)
            .addField("item", item)
            .time(time, TimeUnit.NANOSECONDS);
        if (source != null) {
            if (source.world == null) {
                if (source.entity != null) builder
                    .tag("se", source.entity)
                    .tag("sw", "")
                    .addField("sx", (Number) null)
                    .addField("sy", (Number) null)
                    .addField("sz", (Number) null);
            } else builder
                .tag("se", "")
                .tag("sw", source.world)
                .addField("sx", source.x)
                .addField("sy", source.y)
                .addField("sz", source.z);
        }
        if (target != null) {
            if (target.world == null) {
                if (target.entity != null) builder
                    .tag("te", target.entity)
                    .tag("tw", "")
                    .addField("tx", (Number) null)
                    .addField("ty", (Number) null)
                    .addField("tz", (Number) null);
            } else builder
                .tag("te", "")
                .tag("tw", target.world)
                .addField("tx", target.x)
                .addField("ty", target.y)
                .addField("tz", target.z);
        }
        db.write(builder.build());
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

    @SuppressWarnings("unused")
    public void fetchItem(@NotNull final String table, @NotNull final String time, @NotNull final Consumer<String> callback) {
        db.query(select("item").from(db.database, table).where(eq("time", time)), res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            callback.accept(data == null || data.getValues().size() == 0
                ? null
                : (String) data.getValues().get(0).get("item".equals(data.getColumns().get(0)) ? 0 : 1)
            );
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

    @NotNull
    public SelectQueryImpl queryExplosion() {
        return select().from(db.database, explosionsRecords).orderBy(desc());
    }

    @NotNull
    public SelectQueryImpl queryExplosionCount() {
        return select().countAll().from(db.database, explosionsRecords);
    }

    @NotNull
    public SelectQueryImpl queryExplosion(final int page) {
        final SelectQueryImpl query = queryExplosion();
        return page == 0 ? query.limit(10) : query.limit(10, page * 10);
    }

    public long getCurrentTime() { return curTime++; }
}
