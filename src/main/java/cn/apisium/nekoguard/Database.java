package cn.apisium.nekoguard;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.jetbrains.annotations.NotNull;

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

    @SuppressWarnings("unused")
    @NotNull
    public QueryResult query(@NotNull final Query query) {
        return instance.query(query);
    }

    public void query(@NotNull final Query query, @NotNull final Consumer<QueryResult> onSuccess) {
        instance.query(query, onSuccess, Database::onFail);
    }

    private static void onFail(@NotNull final Throwable e) {
        e.printStackTrace();
    }

    public void write(@NotNull final Point point) { instance.write(database, "", point); }
}
