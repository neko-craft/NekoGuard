package cn.apisium.nekoguard.bukkit.utils;

import cn.apisium.nekoguard.Constants;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;

public enum CommandSenderType {
    CONSOLE,
    MESSAGE,
    PROXIED,
    REMOTE,
    BLOCK,
    ENTITY,
    UNKNOWN;

    public static CommandSenderType getCommandSenderType(final CommandSender sender) {
        if (sender instanceof ConsoleCommandSender) return CONSOLE;
        else if (sender instanceof ProxiedCommandSender) return PROXIED;
        else if (sender instanceof RemoteConsoleCommandSender) return REMOTE;
        else if (sender instanceof BlockCommandSender) return BLOCK;
        else if (sender instanceof Entity) return ENTITY;
        else if (Constants.IS_PAPER && sender instanceof MessageCommandSender) return MESSAGE;
        else return UNKNOWN;
    }
}
