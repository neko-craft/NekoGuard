package cn.apisium.nekoguard.utils;

import net.md_5.bungee.api.chat.BaseComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ItemStackParser {
    public final int amount;
    @Nullable
    public final String id;
    @Nullable
    public BaseComponent[] name;

    private final static Pattern NAME = Pattern.compile("display:\\{Name:'(.+?)(?<!\\\\)'}");
    private final static Pattern COUNT = Pattern.compile("Count:(\\d+)b");
    private final static Pattern ID = Pattern.compile("id:\"(.+?)\"");

    public ItemStackParser(@NotNull final String data) {
        Matcher m = ID.matcher(data);
        id = m.find() ? m.group(1) : null;
        m = NAME.matcher(data);
        final String itemName = m.find() ? m.group(1) : "";
        name = itemName == null || itemName.isEmpty() ? null : Utils.parseName(itemName);
        m = COUNT.matcher(name == null ? data : data.replace(itemName, ""));
        amount = m.find() ? Integer.parseInt(m.group(1)) : 1;
    }
}
