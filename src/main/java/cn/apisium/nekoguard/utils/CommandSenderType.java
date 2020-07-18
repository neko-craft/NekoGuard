package cn.apisium.nekoguard.utils;

import com.google.common.collect.Sets;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;

import java.util.HashSet;

public enum CommandSenderType {
    CONSOLE,
    MESSAGE,
    PROXIED,
    REMOTE,
    BLOCK,
    ENTITY,
    UNKNOWN;

    private final static HashSet<String> valueList = new HashSet<>();

    static {
        for (final CommandSenderType t : values()) valueList.add(t.name());
    }

    public static HashSet<String> getValueList() { return valueList; }

    public static CommandSenderType getCommandSenderType(final CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) return CONSOLE;
        else if (sender instanceof MessageCommandSender) return MESSAGE;
        else if (sender instanceof ProxiedCommandSender) return PROXIED;
        else if (sender instanceof RemoteConsoleCommandSender) return REMOTE;
        else if (sender instanceof BlockCommandSender) return BLOCK;
        else if (sender instanceof Entity) return ENTITY;
        else return UNKNOWN;
    }
}
