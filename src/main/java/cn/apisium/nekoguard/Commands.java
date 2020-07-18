package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.SimpleTimeClause;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

@CommandAlias("nekoguard|guard|ng")
public final class Commands extends BaseCommand {
    private final API api;
    private final Main main;
    private final static OptionParser LOOKUP_CHATS = new OptionParser(),
            LOOKUP_COMMANDS = new OptionParser();

    static {
        LOOKUP_CHATS.acceptsAll(Arrays.asList("p", "player")).withOptionalArg().ofType(String.class);
        LOOKUP_CHATS.acceptsAll(Arrays.asList("t", "time")).withOptionalArg().ofType(String.class);

        LOOKUP_COMMANDS.acceptsAll(Arrays.asList("p", "performer")).withOptionalArg().ofType(String.class);
        LOOKUP_COMMANDS.acceptsAll(Arrays.asList("t", "time")).withOptionalArg().ofType(String.class);
    }

    Commands(final Main main) {
        this.api = main.getApi();
        this.main = main;
    }

    @Subcommand("inspect|i")
    @CommandPermission("nekoguard.inspect")
    public void onInspect(final Player player) {
        if (main.inspecting.contains(player)) main.inspecting.remove(player);
        else main.inspecting.add(player);
    }

    @Subcommand("lookup|l")
    public class LookupCommand extends BaseCommand {
        @Subcommand("chat")
        @CommandPermission("nekoguard.lookup.chat")
        public void onLookupChat(final CommandSender sender, final String[] args) {
            final OptionSet cmd = LOOKUP_CHATS.parse(args);
            final Object time = cmd.valueOf("time");
            api.sendQueryChatMessage(sender, (String) cmd.valueOf("player"), 0,
                time == null ? null : it -> it.where(new SimpleTimeClause((String) time)));
        }

        @Subcommand("command")
        @CommandPermission("nekoguard.lookup.command")
        public void onLookupCommand(final CommandSender sender, final String[] args) {
            final OptionSet cmd = LOOKUP_CHATS.parse(args);
            final Object time = cmd.valueOf("time");
            api.sendQueryCommandMessage(sender, (String) cmd.valueOf("performer"), 0,
                time == null ? null : it -> it.where(new SimpleTimeClause((String) time)));
        }
    }
}
