package cn.apisium.nekoguard.utils;

import org.influxdb.querybuilder.clauses.Clause;

import java.util.regex.Pattern;

public final class SimpleTimeClause implements Clause {
    // https://v2.docs.influxdata.com/v2.0/reference/flux/language/lexical-elements/#duration-literals
    private final static Pattern PATTERN = Pattern.compile("^(\\d(y|mo|w|d|h|m|s|ms|us|ns)? *[-+*/]? *)+$");
    private final String time;
    public SimpleTimeClause(final String time) { this.time = PATTERN.matcher(time).matches() ? time : null; }
    @Override
    public void appendTo(StringBuilder stringBuilder) {
        if (time == null) stringBuilder.append("0 = 0");
        else stringBuilder.append("time < now() - ").append(time);
    }
}
