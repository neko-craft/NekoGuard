package cn.apisium.nekoguard;

import cn.apisium.nekoguard.changes.*;
import cn.apisium.nekoguard.mappers.Mappers;
import cn.apisium.nekoguard.utils.SimpleTimeClause;
import cn.apisium.nekoguard.utils.Utils;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import com.google.common.collect.EvictingQueue;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpecBuilder;
import org.apache.commons.lang.ObjectUtils;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.WeakHashMap;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

@SuppressWarnings("UnstableApiUsage")
@CommandAlias("nekoguard|guard|ng")
public final class Commands extends BaseCommand {
    private final API api;
    private final Main main;
    private final Messages messages;
    private final WeakHashMap<CommandSender, EvictingQueue<ChangeList>> commandActions = new WeakHashMap<>();
    private final static OptionParser LOOKUP_CHATS = new OptionParser(),
            LOOKUP_COMMANDS = new OptionParser(),
            CONTAINER_ACTIONS = new OptionParser();

    static {
        LOOKUP_CHATS.acceptsAll(Arrays.asList("p", "player")).withOptionalArg().ofType(String.class);
        LOOKUP_CHATS.acceptsAll(Arrays.asList("t", "time")).withOptionalArg().ofType(String.class);

        LOOKUP_COMMANDS.acceptsAll(Arrays.asList("p", "performer")).withOptionalArg().ofType(String.class);
        LOOKUP_COMMANDS.acceptsAll(Arrays.asList("t", "time")).withOptionalArg().ofType(String.class);

        CONTAINER_ACTIONS.acceptsAll(Arrays.asList("p", "performer")).withOptionalArg().ofType(String.class);
        CONTAINER_ACTIONS.acceptsAll(Arrays.asList("t", "time")).withOptionalArg().ofType(String.class);
        CONTAINER_ACTIONS.acceptsAll(Arrays.asList("s", "source")).withOptionalArg().ofType(String.class);
        CONTAINER_ACTIONS.acceptsAll(Arrays.asList("a", "target")).withOptionalArg().ofType(String.class);
        CONTAINER_ACTIONS.acceptsAll(Arrays.asList("b", "both")).withOptionalArg().ofType(String.class);
    }

    Commands(final Main main) {
        this.api = main.getApi();
        this.messages = main.getMessages();
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
            messages.sendQueryChatMessage(sender, (String) cmd.valueOf("player"), 0,
                time == null ? null : it -> it.where(new SimpleTimeClause((String) time)));
        }

        @Subcommand("command")
        @CommandPermission("nekoguard.lookup.command")
        public void lookupCommands(final CommandSender sender, final String[] args) {
            final OptionSet cmd = LOOKUP_CHATS.parse(args);
            final Object time = cmd.valueOf("time");
            messages.sendQueryCommandMessage(sender, (String) cmd.valueOf("performer"), 0,
                time == null ? null : it -> it.where(new SimpleTimeClause((String) time)));
        }

        @Subcommand("block")
        @CommandPermission("nekoguard.lookup.block")
        public void lookupBlocks(final CommandSender sender, final String[] args) {
            final OptionParser parser = getParser(sender, false);
            parser.acceptsAll(Arrays.asList("b", "block")).withOptionalArg().ofType(String.class);
            final OptionSet result = parser.parse(args);
            messages.sendQueryBlockMessage(sender, 0, it -> {
                if (result.has("block")) it.where(
                    regex("data", "/^(minecraft:)?" + result.valueOf("block") + "/"));
                processQuery(result, it, sender, false);
            });
        }

        @Subcommand("death")
        @CommandPermission("nekoguard.lookup.death")
        public void lookupDeaths(final CommandSender sender, final String[] args) {
            final OptionParser parser = getParser(sender, false);
            parser.acceptsAll(Arrays.asList("e", "entity", "type")).withOptionalArg().ofType(String.class);
            parser.acceptsAll(Arrays.asList("l", "player")).withOptionalArg().ofType(String.class);
            final OptionSet result = parser.parse(args);
            messages.sendQueryDeathMessage(sender, 0, it -> {
                String type = null;
                if (result.has("type")) type = "@" + result.valueOf("type");
                if (result.has("player")) {
                    type = (String) result.valueOf("player");
                    if (type.length() != 36) type = Utils.getPerformerQueryName(type, sender);
                }
                if (type != null) it.where(eq("type", type));
                processQuery(result, it, sender, false);
            });
        }

        @Subcommand("container")
        @CommandPermission("nekoguard.lookup.container")
        public void lookupContainer(final CommandSender sender, final String[] args) {
            try {
                final OptionSet result = parserContainerActionQuery(sender, false, args);
                messages.sendContainerActionsMessage(sender, 0, it -> processContainerQuery(result, it, sender, false));
            } catch (Exception e) {
                if (e != Constants.IGNORED_ERROR) e.printStackTrace();
            }
        }
    }

    @Subcommand("rollback|r")
    public class RollbackCommand extends BaseCommand {
        @Subcommand("block")
        @CommandPermission("nekoguard.rollback.block")
        public void rollbackBlocks(final CommandSender sender, final String[] args) {
            try {
                final SelectQueryImpl query = api.queryBlock().orderBy(asc());
                final OptionParser parser = getParser(sender, true);
                parser.acceptsAll(Arrays.asList("b", "block")).withOptionalArg().ofType(String.class);
                final OptionSet result = parser.parse(args);
                processQuery(result, query, sender, true);
                if (result.has("block")) query.where(
                    regex("data", "/^(minecraft:)?" + result.valueOf("block") + "/"));
                main.getDatabase().query(query, res -> {
                    final QueryResult.Series data = Utils.getFirstResult(res);
                    if (data == null) return;
                    final BlockChangeList list = new BlockChangeList(Mappers.BLOCKS.parse(data));
                    addCommandAction(sender, list);
                    list.doChange(sender, it -> sender.sendMessage("Success"));
                });
            } catch (Exception e) {
                if (e != Constants.IGNORED_ERROR) e.printStackTrace();
            }
        }

        @Subcommand("container")
        @CommandPermission("nekoguard.rollback.container")
        public void rollbackContainerActions(final CommandSender sender, final String[] args) {
            try {
                final OptionSet result = parserContainerActionQuery(sender, true, args);
                final SelectQueryImpl query = api.queryContainerActions().orderBy(asc());
                processContainerQuery(result, query, sender, true);
                main.getDatabase().query(query, res -> {
                    final QueryResult.Series data = Utils.getFirstResult(res);
                    if (data == null) return;
                    final ContainerChangeList list = new ContainerChangeList(Mappers.CONTAINER_ACTIONS.parse(data));
                    addCommandAction(sender, list);
                    list.doChange(sender, it -> sender.sendMessage("Success"));
                });
            } catch (Exception e) {
                if (e != Constants.IGNORED_ERROR) e.printStackTrace();
            }
        }

        @Subcommand("entity")
        @CommandPermission("nekoguard.rollback.entity")
        public void rollbackEntities(final CommandSender sender, final String[] args) {
            try {
                final SelectQueryImpl query = api.queryDeath().orderBy(asc());
                final OptionParser parser = getParser(sender, true);
                parser.acceptsAll(Arrays.asList("e", "entity")).withOptionalArg().ofType(String.class);
                parser.acceptsAll(Arrays.asList("l", "player")).withOptionalArg().ofType(String.class);
                final OptionSet result = parser.parse(args);
                String type = null;
                parser.acceptsAll(Arrays.asList("p", "performer")).withOptionalArg().ofType(String.class);
                if (result.has("type")) type = "@" + result.valueOf("type");
                if (result.has("player")) {
                    type = (String) result.valueOf("player");
                    if (type.length() != 36) type = Utils.getPerformerQueryName(type, sender);
                }
                if (type != null) query.where(eq("type", type));
                processQuery(result, query, sender, true);
                main.getDatabase().query(query, res -> {
                    final QueryResult.Series data = Utils.getFirstResult(res);
                    if (data == null) return;
                    final EntityChangeList list = new EntityChangeList(Mappers.DEATHS.parse(data));
                    addCommandAction(sender, list);
                    list.doChange(sender, it -> sender.sendMessage("Success"));
                });
            } catch (Exception e) {
                if (e != Constants.IGNORED_ERROR) e.printStackTrace();
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandIssuer issuer, String commandLabel, String[] args, boolean isAsync) throws IllegalArgumentException {
        return Collections.emptyList();
    }

    private OptionParser getParser(final CommandSender sender, final boolean isRollback) {
        final OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("r", "radius")).withRequiredArg().ofType(Integer.class).defaultsTo(5);
        final OptionSpecBuilder b = parser.acceptsAll(Arrays.asList("t", "time"));
        (isRollback ? b.withRequiredArg() : b.withOptionalArg()).ofType(String.class);
        parser.acceptsAll(Arrays.asList("p", "performer")).withOptionalArg().ofType(String.class);
        parser.acceptsAll(Arrays.asList("g", "global")).withOptionalArg();

        final ArgumentAcceptingOptionSpec<Integer> x = parser.accepts("x").withRequiredArg().ofType(Integer.class),
            y = parser.accepts("y").withRequiredArg().ofType(Integer.class),
            z = parser.accepts("z").withRequiredArg().ofType(Integer.class);
        final ArgumentAcceptingOptionSpec<String> world = parser.acceptsAll(Arrays.asList("w", "world")).withRequiredArg().ofType(String.class);
        if (sender instanceof Player) {
            final Location p = ((Player) sender).getLocation();
            x.defaultsTo(p.getBlockX());
            y.defaultsTo(p.getBlockY());
            z.defaultsTo(p.getBlockZ());
            world.defaultsTo(p.getWorld().getName());
        }
        return parser;
    }

    private OptionSet parserContainerActionQuery(final CommandSender sender, final boolean isRollback, final String[] args) {
        final OptionParser parser = new OptionParser();
        parser.acceptsAll(Arrays.asList("r", "radius")).withRequiredArg().ofType(Integer.class).defaultsTo(5);
        final OptionSpecBuilder b = parser.acceptsAll(Arrays.asList("t", "time"));
        (isRollback ? b.withRequiredArg() : b.withOptionalArg()).ofType(String.class);
        parser.acceptsAll(Arrays.asList("g", "global")).withOptionalArg();

        final ArgumentAcceptingOptionSpec<String> world = parser.acceptsAll(Arrays.asList("w", "world")).withRequiredArg().ofType(String.class);
        final ArgumentAcceptingOptionSpec<Integer> x = parser.accepts("x").withOptionalArg().ofType(Integer.class),
            y = parser.accepts("y").withOptionalArg().ofType(Integer.class),
            z = parser.accepts("z").withOptionalArg().ofType(Integer.class);

        parser.acceptsAll(Arrays.asList("sx", "source-x")).withOptionalArg().ofType(Integer.class);
        parser.acceptsAll(Arrays.asList("sy", "source-y")).withOptionalArg().ofType(Integer.class);
        parser.acceptsAll(Arrays.asList("sz", "source-z")).withOptionalArg().ofType(Integer.class);

        parser.acceptsAll(Arrays.asList("tx", "target-x")).withOptionalArg().ofType(Integer.class);
        parser.acceptsAll(Arrays.asList("ty", "target-y")).withOptionalArg().ofType(Integer.class);
        parser.acceptsAll(Arrays.asList("tz", "target-z")).withOptionalArg().ofType(Integer.class);

        parser.acceptsAll(Arrays.asList("sw", "source-world")).withOptionalArg().ofType(String.class);
        parser.acceptsAll(Arrays.asList("tw", "target-world")).withOptionalArg().ofType(String.class);
        parser.acceptsAll(Arrays.asList("e", "entity")).withOptionalArg().ofType(String.class);
        parser.acceptsAll(Arrays.asList("se", "source-entity")).withOptionalArg().ofType(String.class);
        parser.acceptsAll(Arrays.asList("te", "target-entity")).withOptionalArg().ofType(String.class);

        if (sender instanceof Player) {
            final Location p = ((Player) sender).getLocation();
            x.defaultsTo(p.getBlockX());
            y.defaultsTo(p.getBlockY());
            z.defaultsTo(p.getBlockZ());
            world.defaultsTo(p.getWorld().getName());
        }
        return parser.parse(args);
    }

    private void processQuery(final OptionSet cmd, final SelectQueryImpl q, final CommandSender sender, final boolean isRollback) {
        final WhereQueryImpl<SelectQueryImpl> query = q.where();
        if (isRollback || cmd.has("time")) {
            final Object t = cmd.valueOf("time");
            if (t == null) {
                sender.sendMessage("请提供时间!");
                throw Constants.IGNORED_ERROR;
            }
            query.and(new SimpleTimeClause((String) t, isRollback ? '>' : null));
        }
        if (cmd.has("performer")) query.and(eq("performer",
            Utils.getPerformerQueryName((String) cmd.valueOf("performer"), sender)));
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

    private void processContainerQuery(final OptionSet cmd, final SelectQueryImpl q, final CommandSender sender, final boolean isRollback) {
        final WhereQueryImpl<SelectQueryImpl> query = q.where();
        if (isRollback || cmd.has("time")) {
            final Object t = cmd.valueOf("time");
            if (t == null) {
                sender.sendMessage("请提供时间!");
                throw Constants.IGNORED_ERROR;
            }
            query.and(new SimpleTimeClause((String) t, isRollback ? '>' : null));
        }
        if (cmd.has("performer")) query.and(eq("performer",
            Utils.getPerformerQueryName((String) cmd.valueOf("performer"), sender)));
        if (!cmd.has("global")) {
            Integer x = (Integer) cmd.valueOf("x"),
                y = (Integer) cmd.valueOf("y"),
                z = (Integer) cmd.valueOf("z"),
                sx = (Integer) cmd.valueOf("sx"),
                sy = (Integer) cmd.valueOf("sy"),
                sz = (Integer) cmd.valueOf("sz"),
                tx = (Integer) cmd.valueOf("tx"),
                ty = (Integer) cmd.valueOf("ty"),
                tz = (Integer) cmd.valueOf("tz"),
                r = (Integer) cmd.valueOf("radius");
            String e = (String) cmd.valueOf("e"),
                se = (String) cmd.valueOf("se"),
                te = (String) cmd.valueOf("te"),
                w = (String) cmd.valueOf("w"),
                sw = (String) cmd.valueOf("sw"),
                tw = (String) cmd.valueOf("tw");
            if (se == null && te == null && sw == null && tw == null) {
                if (e != null) query.andNested().and(eq("se", e)).or(eq("te", e)).close();
                else if (w != null && x != null && y != null && z != null) query.andNested().and(eq("sw", w))
                    .and(gte("sx", x - r)).and(lte("sx", x + r))
                    .and(gte("sy", y - r)).and(lte("sy", y + r))
                    .and(gte("sz", z - r)).and(lte("sz", z + r)).close()
                    .orNested().and(eq("tw", w))
                    .and(gte("tx", x - r)).and(lte("tx", x + r))
                    .and(gte("ty", y - r)).and(lte("ty", y + r))
                    .and(gte("tz", z - r)).and(lte("tz", z + r)).close();
                else {
                    sender.sendMessage("请至少提供一个查询条件!");
                    throw Constants.IGNORED_ERROR;
                }
            } else {
                if (se == null) {
                    if (sw != null) {
                        sx = (Integer) ObjectUtils.defaultIfNull(sx, x);
                        sy = (Integer) ObjectUtils.defaultIfNull(sy, y);
                        sz = (Integer) ObjectUtils.defaultIfNull(sz, z);
                        query.andNested().and(eq("sw", sw))
                            .and(gte("sx", sx - r)).and(lte("sx", sx + r))
                            .and(gte("sy", sy - r)).and(lte("sy", sy + r))
                            .and(gte("sz", sz - r)).and(lte("sz", sz + r)).close();
                    }
                } else query.and(eq("se", ObjectUtils.defaultIfNull(se, e)));
                if (te == null) {
                    if (tw != null) {
                        tx = (Integer) ObjectUtils.defaultIfNull(tx, x);
                        ty = (Integer) ObjectUtils.defaultIfNull(ty, y);
                        tz = (Integer) ObjectUtils.defaultIfNull(tz, z);
                        query.andNested().and(eq("tw", tw))
                            .and(gte("tx", tx - r)).and(lte("tx", tx + r))
                            .and(gte("ty", ty - r)).and(lte("ty", ty + r))
                            .and(gte("tz", tz - r)).and(lte("tz", tz + r)).close();
                    }
                } else query.and(eq("te", ObjectUtils.defaultIfNull(te, e)));
            }
        }
    }

    private void addCommandAction(final CommandSender sender, final ChangeList list) {
        commandActions.computeIfAbsent(sender, it -> EvictingQueue.create(main.commandActionHistoryCount)).add(list);
    }
}
