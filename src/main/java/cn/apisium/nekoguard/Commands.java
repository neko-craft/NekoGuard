package cn.apisium.nekoguard;

import cn.apisium.nekocommander.*;
import cn.apisium.nekocommander.completer.PlayersCompleter;
import cn.apisium.nekoguard.changes.*;
import cn.apisium.nekoguard.mappers.Mappers;
import cn.apisium.nekoguard.utils.SimpleTimeClause;
import cn.apisium.nekoguard.utils.Utils;
import com.google.common.collect.EvictingQueue;
import joptsimple.OptionSet;
import org.apache.commons.lang.ObjectUtils;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.WeakHashMap;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

@SuppressWarnings("UnstableApiUsage")
@Command("nekoguard")
public final class Commands implements BaseCommand {
    private final API api;
    private final Main main;
    private final Messages messages;
    private final WeakHashMap<CommandSender, EvictingQueue<ChangeList>> commandActions = new WeakHashMap<>();

    Commands(@NotNull final Main main) {
        this.api = main.getApi();
        this.messages = main.getMessages();
        this.main = main;
    }

    @Command("i")
    @Command("inspect")
    @Permission("nekoguard.inspect")
    public void inspect(@NotNull final Player player) {
        player.setGravity(true);
        if (main.inspecting.contains(player)) main.inspecting.remove(player);
        else main.inspecting.add(player);
    }

    @Command("l")
    @Command("lookup")
    public class LookupCommand implements BaseCommand {
        @Command("chat")
        @Permission("nekoguard.lookup.chat")
        public void lookupChats(
            @NotNull final CommandSender sender,
            @Argument({ "t", "time" }) final String time,
            @Nullable @Argument(value = { "p", "player" }, completer = PlayersCompleter.class) final String player
        ) {
            messages.sendQueryChatMessage(sender, player, 0,
                time == null ? null : it -> it.where(new SimpleTimeClause(time)));
        }

        @Command("command")
        @Permission("nekoguard.lookup.command")
        public void lookupCommands(
            @NotNull final CommandSender sender,
            @Nullable @Argument({ "t", "time" }) final String time,
            @Nullable @Argument(value = { "p", "performer" }) final String performer
        ) {
            messages.sendQueryCommandMessage(sender, performer, 0,
                time == null ? null : it -> it.where(new SimpleTimeClause(time)));
        }

        @Command("block")
        @Permission("nekoguard.lookup.block")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" })
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" })
        @Argument(value = { "b", "block" })
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void lookupBlocks(@NotNull final CommandSender sender, @NotNull final OptionSet result) {
            messages.sendQueryBlockMessage(sender, 0, it -> {
                if (result.has("block")) it.where(
                    regex("data", "/^(minecraft:)?" + result.valueOf("block") + "/"));
                processQuery(result, it, sender, false);
            });
        }

        @Command("entity")
        @Permission("nekoguard.lookup.entity")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" })
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" })
        @Argument(value = { "e", "entity", "type" })
        @Argument(value = { "l", "player" })
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void lookupEntities(@NotNull final CommandSender sender, @NotNull final OptionSet result) {
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

        @Command("container")
        @Permission("nekoguard.lookup.container")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" })
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" })
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        @Argument(value = { "e", "entity" })
        @Argument(value = { "se", "source-entity" })
        @Argument(value = { "sw", "source-world" })
        @Argument(value = { "sx", "source-x" }, type = Integer.class)
        @Argument(value = { "sy", "source-y" }, type = Integer.class)
        @Argument(value = { "sz", "source-z" }, type = Integer.class)
        @Argument(value = { "te", "target-entity" })
        @Argument(value = { "tw", "target-world" })
        @Argument(value = { "tx", "target-x" }, type = Integer.class)
        @Argument(value = { "ty", "target-y" }, type = Integer.class)
        @Argument(value = { "tz", "target-z" }, type = Integer.class)
        public void lookupContainer(@NotNull final CommandSender sender, @NotNull final OptionSet result) {
            try {
                messages.sendContainerActionsMessage(sender, 0, it -> processContainerQuery(result, it, sender, false));
            } catch (Exception e) {
                if (e != Constants.IGNORED_ERROR) e.printStackTrace();
            }
        }
    }

    @Command("rollback")
    public class RollbackCommand implements BaseCommand {
        @Command("block")
        @Permission("nekoguard.rollback.block")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" })
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" })
        @Argument(value = { "b", "block" })
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void rollbackBlocks(@NotNull final CommandSender sender, @NotNull final OptionSet result) {
            try {
                final SelectQueryImpl query = api.queryBlock().orderBy(asc());
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

        @Command("container")
        @Permission("nekoguard.rollback.container")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" })
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" })
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        @Argument(value = { "e", "entity" })
        @Argument(value = { "se", "source-entity" })
        @Argument(value = { "sw", "source-world" })
        @Argument(value = { "sx", "source-x" }, type = Integer.class)
        @Argument(value = { "sy", "source-y" }, type = Integer.class)
        @Argument(value = { "sz", "source-z" }, type = Integer.class)
        @Argument(value = { "te", "target-entity" })
        @Argument(value = { "tw", "target-world" })
        @Argument(value = { "tx", "target-x" }, type = Integer.class)
        @Argument(value = { "ty", "target-y" }, type = Integer.class)
        @Argument(value = { "tz", "target-z" }, type = Integer.class)
        public void rollbackContainerActions(@NotNull final CommandSender sender, @NotNull final OptionSet result) {
            try {
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

        @Command("entity")
        @Permission("nekoguard.rollback.entity")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" })
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" })
        @Argument(value = { "e", "entity", "type" })
        @Argument(value = { "l", "player" })
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void rollbackEntities(@NotNull final CommandSender sender, @NotNull final OptionSet result) {
            try {
                final SelectQueryImpl query = api.queryDeath().orderBy(asc());
                String type = null;
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
            Integer lx = (Integer) cmd.valueOf("x"),
                ly = (Integer) cmd.valueOf("y"),
                lz = (Integer) cmd.valueOf("z"),
                r = (Integer) ObjectUtils.defaultIfNull(cmd.valueOf("radius"), 5);
            String world = (String) cmd.valueOf("world");
            if (world == null) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("请提供查询位置!");
                    throw Constants.IGNORED_ERROR;
                }
                final Location loc = ((Player) sender).getLocation();
                world = loc.getWorld().getName();
                lx = loc.getBlockX();
                ly = loc.getBlockY();
                lz = loc.getBlockZ();
            }
            query.and(eq("world", world))
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
            if (w == null && sender instanceof Player) {
                final Location loc = ((Player) sender).getLocation();
                w = loc.getWorld().getName();
                if (x == null) x = loc.getBlockX();
                if (y == null) y = loc.getBlockY();
                if (z == null) z = loc.getBlockZ();
            }
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
