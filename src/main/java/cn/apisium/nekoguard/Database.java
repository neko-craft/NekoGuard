package cn.apisium.nekoguard;

import org.influxdb.BatchOptions;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

import java.util.function.Consumer;

public final class Database {
    public final InfluxDB instance;
    protected final String database;

    @SuppressWarnings("deprecation")
    Database(final String database, final String url, final String name, final String password) {
        instance = name == null || name.equals("") ? InfluxDBFactory.connect(url) : InfluxDBFactory.connect(url, name, password);
        if (!instance.databaseExists(database)) instance.createDatabase(database);
        this.database = database;
        instance.enableBatch(BatchOptions.DEFAULTS);
    }

    public QueryResult query(final String command) {
        return instance.query(new Query(command, database));
    }

    public QueryResult query(final Query query) {
        return instance.query(query);
    }

    public void query(final Query query, final Consumer<QueryResult> onSuccess) {
        instance.query(query, onSuccess, Database::onFail);
    }

    private static void onFail(final Throwable e) {
        e.printStackTrace();
    }

    public void write(final Point point) { instance.write(database, "", point); }
}
