package cn.apisium.nekoguard;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.clauses.Clause;

import java.util.function.Consumer;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

public final class API {
    private final Database db;
    private final String blocksRecords;

    API(final Database db, final String prefix) {
        this.db = db;
        blocksRecords = prefix + "Blocks";
    }

    public void recordBlock(final Block block, final String user, final String action) {
        db.write(Point.measurement(blocksRecords)
                .tag("world", block.getWorld().getName())
                .tag("performer", user)
                .tag("action", action)
                .addField("x", block.getX())
                .addField("y", block.getY())
                .addField("z", block.getZ())
                .addField("type", block.getType().name())
                .addField("data", block.getBlockData().getAsString())
                .build());
    }

    public void inspectBlock(final String world, final int x, final int y, final int z, final Consumer<QueryResult> onSuccess) {
        db.query(select("time", "performer", "action", "type").from(db.database, blocksRecords)
                .where(eq("world", world))
                .and(eq("x", x))
                .and(eq("y", y))
                .and(eq("z", z)),
                onSuccess
        );
    }
}
