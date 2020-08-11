package cn.apisium.nekoguard;

import cn.apisium.nekocommander.*;
import cn.apisium.nekocommander.completer.BlocksCompleter;
import cn.apisium.nekocommander.completer.PlayersCompleter;
import cn.apisium.nekocommander.completer.WorldsCompleter;
import cn.apisium.nekoguard.mappers.Mappers;
import cn.apisium.nekoguard.utils.SimpleTimeClause;
import cn.apisium.nekoguard.utils.Utils;
import joptsimple.OptionSet;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.ObjectUtils;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.influxdb.querybuilder.WhereQueryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Consumer;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.*;

@Command("nekoguard")
public final class Commands implements BaseCommand {
    private final API api;
    private final Main main;
    private final Messages messages;

    Commands(@NotNull final Main main) {
        api = main.getApi();
        messages = main.getMessages();
        this.main = main;
    }

    @Command("i")
    @Command("inspect")
    @Permission("nekoguard.inspect")
    @PlayerOnly
    public void inspect(@NotNull final ProxiedCommandSender player) {
        if (main.inspecting.containsKey(player)) {
            main.inspecting.remove(player);
            player.sendMessage("§e[NekoGuard] §b当前已经退出了审查模式!");
        } else {
            main.inspecting.put(player, null);
            player.sendMessage(Constants.IN_INSPECTING);
        }
    }

    @Command("page")
    public boolean page(@NotNull final ProxiedCommandSender sender, @NotNull final String[] args) {
        if (args.length != 1) return false;
        final Consumer<Integer> fn = main.commandHistories.get(sender);
        if (fn == null) sender.sendMessage(Constants.NO_RECORDS);
        else try {
            fn.accept(Integer.parseInt(args[0]));
        } catch (final Exception ignored) { return false; }
        return true;
    }

    @Command("undo")
    public boolean undo(@NotNull final ProxiedCommandSender sender, @NotNull final String[] args) {
        final LinkedList<ChangeList> list = main.commandActions.get(sender);
        if (list == null || list.isEmpty()) {
            sender.sendMessage(Constants.NO_RECORDS);
            return true;
        }
        sender.sendMessage(String.join(" ", args));
        if (args.length == 1) try {
            final int index = Integer.parseInt(args[0]);
            final ChangeList change = list.get(index);
            if (change == null) sender.sendMessage(Constants.NO_RECORDS);
            else {
                change.undo(it -> {
                    sender.sendMessage(Constants.SUCCESS);
                    list.remove(change);
                });
            }
        } catch (final Exception ignored) {
            return false;
        } else {
            sender.sendMessage(Constants.HEADER);
            final Iterator<ChangeList> iterator = list.iterator();
            int i = 0;
            while (iterator.hasNext()) {
                final TextComponent redo = new TextComponent("[撤销]");
                redo.setColor(ChatColor.RED);
                redo.setHoverEvent(Constants.REDO_HOVER);
                redo.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/nekoguard undo " + i));
                final TextComponent t = new TextComponent(" " + ++i + ". ");
                t.setColor(ChatColor.GRAY);
                sender.sendMessage(
                    Constants.SPACE,
                    redo,
                    t,
                    new TextComponent(iterator.next().getName())
                );
            }
            sender.sendMessage(Constants.FOOTER);
        }
        return true;
    }

    @Command("l")
    @Command("lookup")
    public class LookupCommand implements BaseCommand {
        @Command("chat")
        @Permission("nekoguard.lookup.chat")
        public void lookupChats(
            @NotNull final ProxiedCommandSender sender,
            @Argument({ "t", "time" }) final String time,
            @Nullable @Argument(value = { "p", "player" }, completer = PlayersCompleter.class) final String player
        ) {
            messages.sendQueryChatMessage(sender, Utils.PLATFORM.getPlayerUUIDByName(player, sender), 0,
                time == null ? null : it -> it.where(new SimpleTimeClause(time)));
        }

        @Command("command")
        @Permission("nekoguard.lookup.command")
        public void lookupCommands(
            @NotNull final ProxiedCommandSender sender,
            @Nullable @Argument({ "t", "time" }) final String time,
            @Nullable @Argument(value = { "p", "performer" }, completer = PlayersCompleter.class) final String performer
        ) {
            messages.sendQueryCommandMessage(sender, performer == null ? null : performer.startsWith("#") ? performer.substring(1)
                    : Utils.PLATFORM.getPlayerUUIDByName(performer, sender), 0,
                time == null ? null : it -> it.where(new SimpleTimeClause(time)));
        }

        @Command("session")
        @Permission("nekoguard.lookup.session")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument(value = { "w", "world" }, completer = WorldsCompleter.class)
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void lookupSessions(
            @NotNull final ProxiedCommandSender sender,
            @Nullable @Argument({ "t", "time" }) final String time,
            @Nullable @Argument(value = { "p", "player" }, completer = PlayersCompleter.class) String player,
            @Nullable @Argument(value = { "n", "name" }, completer = PlayersCompleter.class) final String name,
            @Nullable @Argument(value = "near", type = Boolean.class) final Boolean near,
            @NotNull final OptionSet result
        ) {
            messages.sendSessionMessage(sender, 0, it -> {
                final WhereQueryImpl<SelectQueryImpl> query = it.where();
                if (time != null) query.and(new SimpleTimeClause(time));
                if (name != null) query.and(eq("name", name));
                final String id = Utils.PLATFORM.getPlayerUUIDByName(player, sender);
                if (id != null) query.and(eq("id", id));
                if (near != null && near) processLocationQuery(result, query, sender);
            });
        }

        @Command("item")
        @Permission("nekoguard.lookup.item")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" }, completer = PlayersCompleter.class)
        @Argument(value = { "i", "item" }, completer = BlocksCompleter.class)
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" }, completer = WorldsCompleter.class)
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void lookupItemActions(
            @NotNull final ProxiedCommandSender sender,
            @NotNull final OptionSet result,
            @Nullable @Argument(value = "drop", type = Boolean.class) final Boolean isDrop,
            @Nullable @Argument(value = "pickup", type = Boolean.class) final Boolean isPickup
        ) {
            messages.sendItemActionMessage(sender, 0, it -> {
                final boolean drop = isDrop == null || isDrop, pickup = isPickup == null || isPickup;
                if (drop && !pickup) it.where(eq("action", "0"));
                else if (!drop && pickup) it.where(eq("action", "1"));
                processQuery(result, it, sender, false);
            });
        }

        @Command("block")
        @Permission("nekoguard.lookup.block")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" }, completer = PlayersCompleter.class)
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" }, completer = WorldsCompleter.class)
        @Argument(value = { "b", "block" }, completer = BlocksCompleter.class)
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void lookupBlocks(@NotNull final ProxiedCommandSender sender, @NotNull final OptionSet result) {
            messages.sendQueryBlockMessage(sender, 0, it -> {
                if (result.has("block")) it.where(
                    regex("block", "/^(minecraft:)?" + result.valueOf("block") + "/"));
                processQuery(result, it, sender, false);
            });
        }

        @Command("death")
        @Permission("nekoguard.lookup.death")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" }, completer = PlayersCompleter.class)
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" }, completer = WorldsCompleter.class)
        @Argument({ "e", "entity", "type" })
        @Argument({ "l", "player" })
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void lookupEntities(@NotNull final ProxiedCommandSender sender, @NotNull final OptionSet result) {
            messages.sendQueryDeathMessage(sender, 0, it -> {
                String type = null;
                if (result.has("type")) type = "@" + result.valueOf("type");
                if (result.has("player")) {
                    type = (String) result.valueOf("player");
                    if (type.length() != 36) type = Utils.PLATFORM.getPerformerQueryName(type, sender);
                }
                if (type != null) it.where(eq("type", type));
                processQuery(result, it, sender, false);
            });
        }

        @Command("explosion")
        @Permission("nekoguard.lookup.explosion")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "a", "target" }, completer = PlayersCompleter.class)
        @Argument(value = { "type", "player", "p" }, completer = PlayersCompleter.class)
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" }, completer = WorldsCompleter.class)
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void lookupExplosions(@NotNull final ProxiedCommandSender sender, @NotNull final OptionSet result) {
            messages.sendExplosionMessage(sender, 0, it -> {
                String type = null;
                if (result.has("player")) {
                    type = (String) result.valueOf("player");
                    if (!type.startsWith("@") && type.length() != 36) type = Utils.PLATFORM.getPerformerQueryName(type, sender);
                }
                if (type != null) it.where(eq("type", type));
                type = null;
                if (result.has("target")) {
                    type = (String) result.valueOf("target");
                    if (!type.startsWith("@") && type.length() != 36) type = Utils.PLATFORM.getPerformerQueryName(type, sender);
                }
                if (type != null) it.where(eq("target", type));
                processQuery(result, it, sender, false);
            });
        }

        @Command("container")
        @Permission("nekoguard.lookup.container")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" }, completer = PlayersCompleter.class)
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" }, completer = WorldsCompleter.class)
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        @Argument({ "e", "entity" })
        @Argument({ "se", "source-entity" })
        @Argument(value = { "sw", "source-world" }, completer = WorldsCompleter.class)
        @Argument(value = { "sx", "source-x" }, type = Integer.class)
        @Argument(value = { "sy", "source-y" }, type = Integer.class)
        @Argument(value = { "sz", "source-z" }, type = Integer.class)
        @Argument({ "te", "target-entity" })
        @Argument(value = { "tw", "target-world" }, completer = WorldsCompleter.class)
        @Argument(value = { "tx", "target-x" }, type = Integer.class)
        @Argument(value = { "ty", "target-y" }, type = Integer.class)
        @Argument(value = { "tz", "target-z" }, type = Integer.class)
        public void lookupContainer(@NotNull final ProxiedCommandSender sender, @NotNull final OptionSet result) {
            try {
                messages.sendContainerActionsMessage(sender, 0, it -> processContainerQuery(result, it, sender, false));
            } catch (Exception e) {
                if (e != Constants.IGNORED_ERROR) e.printStackTrace();
            }
        }
    }

    @Command("fetch")
    public class FetchCommand implements BaseCommand {
        @Command("action")
        @Permission("nekoguard.fetch.action")
        @PlayerOnly
        public boolean fetchActionItem(final @NotNull ProxiedCommandSender sender, @NotNull final String[] args) {
            if (args.length != 1) return false;
            try {
                Instant.parse(args[0]);
                api.fetchItem(api.itemsRecords, args[0], it -> Utils.PLATFORM.fetchItemIntoInventory(sender, it));
                return true;
            } catch (final Exception ignored) {
                return false;
            }
        }

        @Command("container")
        @Permission("nekoguard.fetch.container")
        @PlayerOnly
        public boolean fetchContainerItem(final @NotNull ProxiedCommandSender sender, @NotNull final String[] args) {
            if (args.length != 1) return false;
            try {
                Instant.parse(args[0]);
                api.fetchItem(api.containerRecords, args[0], it -> Utils.PLATFORM.fetchItemIntoInventory(sender, it));
                return true;
            } catch (final Exception ignored) {
                return false;
            }
        }
    }

    @Command("rollback")
    public class RollbackCommand implements BaseCommand {
        @Command("block")
        @Permission("nekoguard.rollback.block")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" }, completer = PlayersCompleter.class)
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" }, completer = WorldsCompleter.class)
        @Argument(value = { "b", "block" }, completer = BlocksCompleter.class)
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void rollbackBlocks(@NotNull final ProxiedCommandSender sender, @NotNull final OptionSet result) {
            try {
                final SelectQueryImpl query = api.queryBlock().orderBy(asc());
                processQuery(result, query, sender, true);
                if (result.has("block")) query.where(
                    regex("block", "/^(minecraft:)?" + result.valueOf("block") + "/"));
                main.getDatabase().query(query, res -> {
                    final QueryResult.Series data = Utils.getFirstResult(res);
                    if (data == null) {
                        sender.sendMessage(Constants.NO_RECORDS);
                        return;
                    }
                    final ChangeList list = Utils.PLATFORM.createBlockChangeList(Mappers.BLOCKS.parse(data));
                    main.addCommandAction(sender, list);
                    list.doChange(sendFinishMessage(sender));
                });
            } catch (Exception e) {
                if (e != Constants.IGNORED_ERROR) e.printStackTrace();
            }
        }

        @Command("container")
        @Permission("nekoguard.rollback.container")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" }, completer = PlayersCompleter.class)
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" }, completer = WorldsCompleter.class)
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        @Argument({ "e", "entity" })
        @Argument({ "se", "source-entity" })
        @Argument(value = { "sw", "source-world" }, completer = WorldsCompleter.class)
        @Argument(value = { "sx", "source-x" }, type = Integer.class)
        @Argument(value = { "sy", "source-y" }, type = Integer.class)
        @Argument(value = { "sz", "source-z" }, type = Integer.class)
        @Argument({ "te", "target-entity" })
        @Argument(value = { "tw", "target-world" }, completer = WorldsCompleter.class)
        @Argument(value = { "tx", "target-x" }, type = Integer.class)
        @Argument(value = { "ty", "target-y" }, type = Integer.class)
        @Argument(value = { "tz", "target-z" }, type = Integer.class)
        public void rollbackContainerActions(@NotNull final ProxiedCommandSender sender, @NotNull final OptionSet result) {
            try {
                final SelectQueryImpl query = api.queryContainerActions().orderBy(asc());
                processContainerQuery(result, query, sender, true);
                main.getDatabase().query(query, res -> {
                    final QueryResult.Series data = Utils.getFirstResult(res);
                    if (data == null) {
                        sender.sendMessage(Constants.NO_RECORDS);
                        return;
                    }
                    final ChangeList list = Utils.PLATFORM.createContainerChangeList(Mappers.CONTAINER_ACTIONS.parse(data));
                    main.addCommandAction(sender, list);
                    list.doChange(sendFinishMessage(sender));
                });
            } catch (Exception e) {
                if (e != Constants.IGNORED_ERROR) e.printStackTrace();
            }
        }

        @Command("entity")
        @Permission("nekoguard.rollback.entity")
        @Argument(value = { "r", "radius" }, defaultValues = "5", type = Integer.class)
        @Argument({ "t", "time" })
        @Argument(value = { "p", "performer" }, completer = PlayersCompleter.class)
        @Argument(value = { "g", "global" }, type = Boolean.class)
        @Argument(value = { "w", "world" }, completer = WorldsCompleter.class)
        @Argument({ "e", "entity", "type" })
        @Argument({ "l", "player" })
        @Argument(value = "x", type = Integer.class)
        @Argument(value = "y", type = Integer.class)
        @Argument(value = "z", type = Integer.class)
        public void rollbackEntities(@NotNull final ProxiedCommandSender sender, @NotNull final OptionSet result) {
            try {
                final SelectQueryImpl query = api.queryDeath().orderBy(asc());
                String type = null;
                if (result.has("type")) type = "@" + result.valueOf("type");
                if (result.has("player")) {
                    type = (String) result.valueOf("player");
                    if (type.length() != 36) type = Utils.PLATFORM.getPerformerQueryName(type, sender);
                }
                if (type != null) query.where(eq("type", type));
                processQuery(result, query, sender, true);
                main.getDatabase().query(query, res -> {
                    final QueryResult.Series data = Utils.getFirstResult(res);
                    if (data == null) {
                        sender.sendMessage(Constants.NO_RECORDS);
                        return;
                    }
                    final ChangeList list = Utils.PLATFORM.createEntityChangeList(Mappers.DEATHS.parse(data));
                    main.addCommandAction(sender, list);
                    list.doChange(sendFinishMessage(sender));
                });
            } catch (Exception e) {
                if (e != Constants.IGNORED_ERROR) e.printStackTrace();
            }
        }
    }

    private void processLocationQuery(@NotNull final OptionSet cmd, final WhereQueryImpl<SelectQueryImpl> q, @NotNull final ProxiedCommandSender sender) {
        Integer lx = (Integer) cmd.valueOf("x"),
            ly = (Integer) cmd.valueOf("y"),
            lz = (Integer) cmd.valueOf("z"),
            r = (Integer) ObjectUtils.defaultIfNull(cmd.valueOf("radius"), 5);
        String world = (String) cmd.valueOf("world");
        if (world == null) {
            if (sender.world == null) {
                sender.sendMessage("请提供查询位置!");
                throw Constants.IGNORED_ERROR;
            }
            world = sender.world;
            lx = (int) sender.x;
            ly = (int) sender.y;
            lz = (int) sender.z;
        }
        q.andNested().and(eq("world", world))
            .and(gte("x", lx - r)).and(lte("x", lx + r))
            .and(gte("y", ly - r)).and(lte("y", ly + r))
            .and(gte("z", lz - r)).and(lte("z", lz + r)).close();
    }

    private void processQuery(@NotNull final OptionSet cmd, final SelectQueryImpl q, @NotNull final ProxiedCommandSender sender, final boolean isRollback) {
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
            Utils.PLATFORM.getPerformerQueryName((String) cmd.valueOf("performer"), sender)));
        if (!cmd.has("global")) processLocationQuery(cmd, query, sender);
    }

    private void processContainerQuery(final OptionSet cmd, final SelectQueryImpl q, final ProxiedCommandSender sender, final boolean isRollback) {
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
            Utils.PLATFORM.getPerformerQueryName((String) cmd.valueOf("performer"), sender)));
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
            if (w == null && sender.world != null) {
                w = sender.world;
                if (x == null) x = (int) sender.x;
                if (y == null) y = (int) sender.y;
                if (z == null) z = (int) sender.z;
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

    @NotNull
    private Consumer<ChangeList> sendFinishMessage(@NotNull final ProxiedCommandSender sender) {
        return it -> sender.sendMessage("§e[NekoGuard] §b操作完成! §7(" + it.getName() + ") §e总计操作:" +
            it.getAllCount() + " §a成功:" + it.getSuccessCount() + " §c失败:" + it.getFailed());
    }
}
