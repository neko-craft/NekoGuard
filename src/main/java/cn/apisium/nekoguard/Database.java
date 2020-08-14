package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.SimpleTimeClause;
import cn.apisium.nekoguard.utils.Utils;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public final class Database {
    public final InfluxDB instance;
    protected final String database;

    @SuppressWarnings("deprecation")
    Database(final String database, final String url, final String name, final String password, final String retentionPolicy) {
        instance = name == null || name.equals("") ? InfluxDBFactory.connect(url) : InfluxDBFactory.connect(url, name, password);
        if (!instance.databaseExists(database)) instance.createDatabase(database);
        this.database = database;
        instance.setDatabase(database);
        instance.setConsistency(InfluxDB.ConsistencyLevel.ALL);
        if (!retentionPolicy.isEmpty()) instance.setRetentionPolicy(retentionPolicy);
        instance.enableBatch();
    }

    @SuppressWarnings("unused")
    @NotNull
    public QueryResult query(@NotNull final String command) {
        return instance.query(new Query(command, database));
    }

    public void query(@NotNull final String command, @NotNull final Consumer<QueryResult> onSuccess) {
        instance.query(new Query(command, database), onSuccess, Database::onFail);
    }

    @SuppressWarnings("unused")
    @NotNull
    public QueryResult query(@NotNull final Query query) {
        return instance.query(query);
    }

    public void query(@NotNull final Query query, @NotNull final Consumer<QueryResult> onSuccess) {
        instance.query(query, onSuccess, Database::onFail);
    }

    public void dropSeries(@NotNull final String table, @NotNull final Consumer<Boolean> onSuccess) {
        query("DROP SERIES FROM \"" + table + "\"", it -> {
            final List<QueryResult.Result> list = Utils.getResult(it);
            onSuccess.accept(list != null && list.size() == 1 && !list.get(0).hasError() && list.get(0).getSeries() == null);
        });
    }

    public void deleteSeries(@NotNull final String table, @Nullable final String time, @NotNull final Consumer<Boolean> onSuccess) {
        if (time == null) dropSeries(table, onSuccess);
        else if (SimpleTimeClause.PATTERN.matcher(time).matches()) query("DELETE FROM \"" + table + "\" WHERE time < now() - " + time,
            it -> onSuccess.accept(!it.hasError()));
        else onSuccess.accept(Boolean.FALSE);
    }

    private static void onFail(@NotNull final Throwable e) {
        e.printStackTrace();
    }

    public void write(@NotNull final Point point) { instance.write(database, "", point); }
}
