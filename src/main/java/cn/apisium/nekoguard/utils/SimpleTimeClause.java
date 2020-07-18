package cn.apisium.nekoguard.utils;

import org.influxdb.querybuilder.clauses.Clause;

public final class SimpleTimeClause implements Clause {
    private final String time;
    public SimpleTimeClause(final String time) { this.time = time; }
    @Override
    public void appendTo(StringBuilder stringBuilder) {
        stringBuilder.append("< now()").append(" - ").append(time);
    }
}
