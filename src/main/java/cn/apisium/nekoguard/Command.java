package cn.apisium.nekoguard;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import org.bukkit.entity.Player;

@CommandAlias("nekoguard|guard|ng")
public final class Command extends BaseCommand {
    private final API api;
    private final Main main;

    Command(final Main main) {
        this.api = main.getApi();
        this.main = main;
    }

    @Subcommand("inspect|i")
    @CommandPermission("nekoguard.inspect")
    public void onInspect(final Player player) {
        if (main.inspecting.contains(player)) main.inspecting.remove(player);
        else main.inspecting.add(player);
    }

    @Subcommand("lookup chat|l chat")
    @CommandPermission("nekoguard.lookup.chat")
    public void onLookupChat(final Player player) {
        api.sendLookupChatMessage(player, null, 0);
    }
}
