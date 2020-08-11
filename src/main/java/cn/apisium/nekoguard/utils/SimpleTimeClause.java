package cn.apisium.nekoguard.utils;

import org.influxdb.querybuilder.clauses.Clause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

public final class SimpleTimeClause implements Clause {
    // https://v2.docs.influxdata.com/v2.0/reference/flux/language/lexical-elements/#duration-literals
    public final static Pattern PATTERN = Pattern.compile("^(\\d(y|mo|w|d|h|m|s|ms|us|ns)? *[-+*/]? *)+$");
    private final String time;
    private final char symbol;
    public SimpleTimeClause(@NotNull final String time) { this(time, '<'); }
    public SimpleTimeClause(@NotNull final String time, @Nullable final Character symbol) {
        this.time = PATTERN.matcher(time).matches() ? time : null;
        this.symbol = symbol == null ? '<' : symbol;
    }
    @Override
    public void appendTo(StringBuilder stringBuilder) {
        if (time == null) stringBuilder.append("0 = 0");
        else stringBuilder.append("time ").append(symbol).append(" now() - ").append(time);
    }
}
