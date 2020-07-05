package cn.apisium.nekoguard;

import org.bukkit.Bukkit;
import org.influxdb.dto.QueryResult;

import java.util.List;

public final class Utils {
    private Utils() {}

    public static List<QueryResult.Result> getResult(final QueryResult res) {
        if (res.hasError()) {
            Bukkit.getLogger().warning(res.getError());
            return null;
        }
        return res.getResults();
    }

    public static List<QueryResult.Series> getFirstResult(final QueryResult res) {
        final List<QueryResult.Result> list = getResult(res);
        if (list == null || list.isEmpty()) return null;
        final QueryResult.Result ret = list.get(0);
        if (ret.hasError()) {
            Bukkit.getLogger().warning(ret.getError());
            return null;
        }
        return ret.getSeries();
    }
}
