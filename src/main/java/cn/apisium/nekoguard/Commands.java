package cn.apisium.nekoguard;

import cn.apisium.nekoguard.changes.BlockChangeList;
import cn.apisium.nekoguard.utils.Mappers;
import cn.apisium.nekoguard.utils.SimpleTimeClause;
import cn.apisium.nekoguard.utils.Utils;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

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
    public void inspect(final Player player) {
        if (main.inspecting.contains(player)) main.inspecting.remove(player);
        else main.inspecting.add(player);
    }

    @Subcommand("lookup|l")
    public class LookupCommand extends BaseCommand {
        @Subcommand("chat")
        @CommandPermission("nekoguard.lookup.chat")
        public void lookupChats(final CommandSender sender, final String[] args) {
            final OptionSet cmd = LOOKUP_CHATS.parse(args);
            final Object time = cmd.valueOf("time");
            api.sendQueryChatMessage(sender, (String) cmd.valueOf("player"), 0,
                time == null ? null : it -> it.where(new SimpleTimeClause((String) time)));
        }

        @Subcommand("command")
        @CommandPermission("nekoguard.lookup.command")
        public void lookupCommands(final CommandSender sender, final String[] args) {
            final OptionSet cmd = LOOKUP_CHATS.parse(args);
            final Object time = cmd.valueOf("time");
            api.sendQueryCommandMessage(sender, (String) cmd.valueOf("performer"), 0,
                time == null ? null : it -> it.where(new SimpleTimeClause((String) time)));
        }

        @Subcommand("block")
        @CommandPermission("nekoguard.lookup.block")
        public void lookupBlocks(final CommandSender sender, final String[] args) {
            api.sendQueryBlockMessage(sender, 0, it -> queryActions(it, sender, args, false));
        }
    }

    @Subcommand("rollback|r")
    public class RollbackCommand extends BaseCommand {
        @Subcommand("block")
        @CommandPermission("nekoguard.rollback.block")
        public void rollbackBlocks(final CommandSender sender, final String[] args) {
            try {
                final SelectQueryImpl query = api.queryBlock().orderBy(asc());
                queryActions(query, sender, args, true);
                main.getDatabase().query(query, res -> {
                    final QueryResult.Series data = Utils.getFirstResult(res);
                    if (data == null) return;
                    new BlockChangeList(Mappers.BLOCKS.parse(data)).doChange(sender, () -> sender.sendMessage("Success"));
                });
            } catch (Exception e) {
                if (!e.getMessage().equals("IGNORED")) e.printStackTrace();
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandIssuer issuer, String commandLabel, String[] args, boolean isAsync) throws IllegalArgumentException {
        return Collections.emptyList();
    }

    private void queryActions(final SelectQueryImpl q, final CommandSender sender, final String[] args, final boolean isRollback) {
        final OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("r", "radius")).withRequiredArg().ofType(Integer.class).defaultsTo(5);
        final OptionSpecBuilder b = parser.acceptsAll(Arrays.asList("t", "time"));
        (isRollback ? b.withRequiredArg() : b.withOptionalArg()).ofType(String.class);
        parser.acceptsAll(Arrays.asList("p", "performer")).withOptionalArg().ofType(String.class);
        parser.acceptsAll(Arrays.asList("b", "block")).withOptionalArg().ofType(String.class);
        parser.acceptsAll(Arrays.asList("g", "global")).withOptionalArg();

        final ArgumentAcceptingOptionSpec<Integer> x = parser.accepts("x").withRequiredArg().ofType(Integer.class),
            y = parser.accepts("y").withRequiredArg().ofType(Integer.class),
            z = parser.accepts("z").withRequiredArg().ofType(Integer.class);
        final ArgumentAcceptingOptionSpec<String> world = parser.acceptsAll(Arrays.asList("w", "world")).withOptionalArg().ofType(String.class);
        if (sender instanceof Player) {
            final Location p = ((Player) sender).getLocation();
            x.defaultsTo(p.getBlockX());
            y.defaultsTo(p.getBlockY());
            z.defaultsTo(p.getBlockZ());
            world.defaultsTo(p.getWorld().getName());
        }
        final OptionSet cmd = parser.parse(args);
        final WhereQueryImpl<SelectQueryImpl> query = q.where();
        if (isRollback || cmd.has("time")) {
            final Object t = cmd.valueOf("time");
            if (t == null) {
                sender.sendMessage("请提供时间!");
                throw new RuntimeException("IGNORED");
            }
            query.and(new SimpleTimeClause((String) t, isRollback ? '>' : null));
        }
        if (cmd.has("performer")) query.and(eq("performer", cmd.valueOf("performer")));
        if (cmd.has("block")) query.and(regex("data", "/^(minecraft:)?" + cmd.valueOf("block") + "/"));
        if (!cmd.has("global")) {
            final Integer lx = (Integer) cmd.valueOf("x"),
                ly = (Integer) cmd.valueOf("y"),
                lz = (Integer) cmd.valueOf("z"),
                r = (Integer) cmd.valueOf("radius");
            query.and(eq("world", cmd.valueOf("world")))
                .and(gte("x", lx - r)).and(lte("x", lx + r))
                .and(gte("y", ly - r)).and(lte("y", ly + r))
                .and(gte("z", lz - r)).and(lte("z", lz + r));
        }
    }
}
