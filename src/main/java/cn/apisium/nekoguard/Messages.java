package cn.apisium.nekoguard;

import cn.apisium.nekocommander.ProxiedCommandSender;
import cn.apisium.nekoguard.mappers.Mappers;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import cn.apisium.nekoguard.utils.Utils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;

public final class Messages {
    private final API api;
    private final Database db;
    private final Main main;

    public Messages(final Main main) {
        this.api = main.getApi();
        this.db = main.getDatabase();
        this.main = main;
    }

    public void sendQueryCommandMessage(@NotNull final ProxiedCommandSender sender, @Nullable String type, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryCommandCount(type), q2 = api.queryCommand(type, page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all -> db.query(q2, res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) {
                sender.sendMessage(Constants.NO_RECORDS);
                return;
            }
            final SeriesMapper.Mapper mapper = Mappers.COMMANDS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            for (final Object[] arr : mapper.all()) sender.sendMessage(
                Utils.getTimeComponent((String) arr[0], now),
                Utils.getPlayerCommandNameComponent((String) arr[1], (String) arr[2]),
                Utils.genCopyComponent((String) arr[3], ": ")
            );
            sender.sendMessage(Constants.makeFooter(page, all));
        })));
        main.addCommandHistory(sender, it -> sendQueryCommandMessage(sender, type, it, fn));
    }

    public void sendQueryBlockMessage(@NotNull final ProxiedCommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryBlockCount(), q2 = api.queryBlock(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) {
                    sender.sendMessage(Constants.NO_RECORDS);
                    return;
                }
                final SeriesMapper.Mapper mapper = Mappers.BLOCKS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                for (final Object[] arr : mapper.all()) sender.sendMessage(
                    Utils.getActionComponentOfLocation(arr[1].equals("1"), (String) arr[4],
                        ((Double) arr[5]).intValue(), ((Double) arr[6]).intValue(), ((Double) arr[7]).intValue()),
                    Utils.getPerformerComponent((String) arr[2]),
                    Utils.getTimeComponent((String) arr[3], now),
                    Utils.getBlockComponent((String) arr[0])
                );
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        ));
        main.addCommandHistory(sender, it -> sendQueryBlockMessage(sender, it, fn));
    }
    public void sendQueryBlockMessage(@NotNull final ProxiedCommandSender sender, @NotNull final String world, final int x, final int y, final int z, final int page) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(world);
        sendQueryBlockMessage(sender, page, it -> it.where(eq("world", world))
            .and(eq("x", x))
            .and(eq("y", y))
            .and(eq("z", z))
        );
        main.addCommandHistory(sender, it -> sendQueryBlockMessage(sender, world, x, y, z, it));
    }

    public void sendQueryChatMessage(@NotNull final ProxiedCommandSender sender, @Nullable final String player, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryChatCount(player), q2 = api.queryChat(player, page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all -> db.query(q2, res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) {
                sender.sendMessage(Constants.NO_RECORDS);
                return;
            }
            final SeriesMapper.Mapper mapper = Mappers.CHATS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            for (final Object[] arr : mapper.all()) sender.sendMessage(
                Utils.getTimeComponent((String) arr[0], now),
                Utils.getPlayerPerformerNameComponent((String) arr[1], false),
                Utils.genCopyComponent((String) arr[2], ": ")
            );
            sender.sendMessage(Constants.makeFooter(page, all));
        })));
        main.addCommandHistory(sender, it -> sendQueryChatMessage(sender, player, it, fn));
    }

    public void sendContainerActionsMessage(@NotNull final ProxiedCommandSender sender, @NotNull final String world, final int x, final int y, final int z, final int page) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(world);
        final SelectQueryImpl q1 = api.queryContainerActionsCount(), q2 = api.queryContainerActions(page);
        API.processSingleContainerBlockQuery(q1, world, x, y, z);
        API.processSingleContainerBlockQuery(q2, world, x, y, z);
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) {
                    sender.sendMessage(Constants.NO_RECORDS);
                    return;
                }
                final SeriesMapper.Mapper mapper = Mappers.CONTAINER_ACTIONS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                final boolean canCopy = sender.hasPermission("nekoguard.fetch.container");
                for (final Object[] arr : mapper.all()) {
                    final TextComponent t = new TextComponent((String) arr[2]);
                    t.setColor(ChatColor.GRAY);
                    final boolean isAdd = Utils.isAddContainerAction(arr, world, x, y, z);
                    final String time = (String) arr[0];
                    sender.sendMessage(
                        Utils.getActionComponentOfLocation(isAdd, world, x, y, z),
                        Utils.getItemStackDetails((String) arr[1], canCopy ? "/nekoguard fetch container " + time : null),
                        Constants.SPACE,
                        isAdd
                            ? Utils.getContainerPerformerName((String) arr[2], (String) arr[3], (Double) arr[4], (Double) arr[5], (Double) arr[6])
                            : Utils.getContainerPerformerName((String) arr[7], (String) arr[8], (Double) arr[9], (Double) arr[10], (Double) arr[11]),
                        Utils.getTimeComponent(time, now)
                    );
                }
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        ));
        main.addCommandHistory(sender, it -> sendContainerActionsMessage(sender, world, x, y, z, it));
    }

    public void sendContainerActionsMessage(@NotNull final ProxiedCommandSender sender, @NotNull final String entity, final int page) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(entity);
        final SelectQueryImpl q1 = api.queryContainerActionsCount(), q2 = api.queryContainerActions(page);
        API.processSingleContainerEntityQuery(q1, entity);
        API.processSingleContainerEntityQuery(q2, entity);
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) {
                    sender.sendMessage(Constants.NO_RECORDS);
                    return;
                }
                final SeriesMapper.Mapper mapper = Mappers.CONTAINER_ACTIONS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                final boolean canCopy = sender.hasPermission("nekoguard.fetch.container");
                for (final Object[] arr : mapper.all()) {
                    final TextComponent t = new TextComponent((String) arr[2]);
                    t.setColor(ChatColor.GRAY);
                    final boolean isAdd = arr[7].equals(entity);
                    final String time = (String) arr[0];
                    sender.sendMessage(
                        Utils.getActionComponentOfEntity(isAdd, entity),
                        Utils.getItemStackDetails((String) arr[1], canCopy ? "/nekoguard fetch container " + time : null),
                        Constants.SPACE,
                        isAdd
                            ? Utils.getContainerPerformerName((String) arr[2], (String) arr[3], (Double) arr[4], (Double) arr[5], (Double) arr[6])
                            : Utils.getContainerPerformerName((String) arr[7], (String) arr[8], (Double) arr[9], (Double) arr[10], (Double) arr[11]),
                        Utils.getTimeComponent(time, now)
                    );
                }
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        ));
        main.addCommandHistory(sender, it -> sendContainerActionsMessage(sender, entity, it));
    }

    public void sendContainerActionsMessage(@NotNull final ProxiedCommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryContainerActionsCount(), q2 = api.queryContainerActions(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) {
                    sender.sendMessage(Constants.NO_RECORDS);
                    return;
                }
                final SeriesMapper.Mapper mapper = Mappers.CONTAINER_ACTIONS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                final boolean canCopy = sender.hasPermission("nekoguard.fetch.container");
                for (final Object[] arr : mapper.all()) {
                    final String time = (String) arr[0];
                    sender.sendMessage(
                        Utils.getTimeComponent(time, now),
                        Utils.getItemStackDetails((String) arr[1], canCopy ? "/ng fetch container " + time : null),
                        Constants.SPACE,
                        Utils.getContainerPerformerNameRoughly((String) arr[2], (String) arr[3], (Double) arr[4], (Double) arr[5], (Double) arr[6]),
                        Constants.LEFT_ARROW,
                        Utils.getContainerPerformerNameRoughly((String) arr[7], (String) arr[8], (Double) arr[9], (Double) arr[10], (Double) arr[11])
                    );
                }
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        ));
        main.addCommandHistory(sender, it -> sendContainerActionsMessage(sender, page, fn));
    }

    public void sendQueryDeathMessage(@NotNull final ProxiedCommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryDeathCount(), q2 = api.queryDeath(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) {
                    sender.sendMessage(Constants.NO_RECORDS);
                    return;
                }
                final SeriesMapper.Mapper mapper = Mappers.DEATHS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                for (final Object[] arr : mapper.all()) {
                    final TextComponent reason = new TextComponent(StringUtils.rightPad((String) arr[4], 16, " ") + " "),
                        icon = new TextComponent(" \u2694 ");
                    reason.setColor(ChatColor.GRAY);
                    icon.setColor(ChatColor.RED);
                    Utils.processActionComponent(icon, (String) arr[5], ((Double) arr[6]).intValue(),
                        ((Double) arr[7]).intValue(), ((Double) arr[8]).intValue());
                    sender.sendMessage(
                        Utils.getTimeComponent((String) arr[0], now),
                        reason,
                        Utils.getPerformerComponent((String) arr[1], false),
                        icon,
                        Utils.getDeathEntityComponent((String) arr[2], (String) arr[3])
                    );
                }
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        ));
        main.addCommandHistory(sender, it -> sendQueryDeathMessage(sender, it, fn));
    }

    public void sendQuerySpawnMessage(@NotNull final ProxiedCommandSender sender, @NotNull final String id, final int page) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(id);
        sendQuerySpawnMessage(sender, page, it -> it.where(eq("id", id)));
    }
    public void sendQuerySpawnMessage(@NotNull final ProxiedCommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.querySpawnCount(), q2 = api.querySpawn(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) {
                    sender.sendMessage(Constants.NO_RECORDS);
                    return;
                }
                final SeriesMapper.Mapper mapper = Mappers.SPAWNS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                for (final Object[] arr : mapper.all()) {
                    final TextComponent reason = new TextComponent(StringUtils.rightPad(
                        (String) ObjectUtils.defaultIfNull(arr[1], "UNKNOWN"), 10, " "));
                    Utils.processActionComponent(reason, (String) arr[3], ((Double) arr[4]).intValue(),
                        ((Double) arr[5]).intValue(), ((Double) arr[6]).intValue());
                    sender.sendMessage(
                        Utils.getTimeComponent((String) arr[0], now),
                        reason,
                        Utils.getEntityTypePerformerComponent((String) arr[7], (String) arr[2])
                    );
                }
                sender.sendMessage(Constants.makeFooter(page, all));
            })
        ));
        main.addCommandHistory(sender, it -> sendQuerySpawnMessage(sender, it, fn));
    }

    public void sendItemActionMessage(@NotNull final ProxiedCommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryItemActionCount(), q2 = api.queryItemAction(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all -> db.query(q2, res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) {
                sender.sendMessage(Constants.NO_RECORDS);
                return;
            }
            final SeriesMapper.Mapper mapper = Mappers.ITEM_ACTIONS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            final boolean canCopy = sender.hasPermission("nekoguard.fetch.action");
            for (final Object[] arr : mapper.all()) {
                final String time = (String) arr[0];
                sender.sendMessage(
                    Utils.getActionComponentOfLocation("1".equals(arr[2]), (String) arr[4], ((Double) arr[5]).intValue(),
                        ((Double) arr[6]).intValue(), ((Double) arr[7]).intValue()),
                    Utils.getItemStackDetails((String) arr[3], canCopy ? "/ng fetch action " + time : null),
                    Constants.SPACE,
                    Utils.getPlayerPerformerNameComponent((String) arr[1], true),
                    Utils.getTimeComponent(time, now)
                );
            }
            sender.sendMessage(Constants.makeFooter(page, all));
        })));
        main.addCommandHistory(sender, it -> sendItemActionMessage(sender, it, fn));
    }

    public void sendSessionMessage(@NotNull final ProxiedCommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.querySessionCount(), q2 = api.querySession(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all -> db.query(q2, res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) {
                sender.sendMessage(Constants.NO_RECORDS);
                return;
            }
            final SeriesMapper.Mapper mapper = Mappers.SESSIONS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            for (final Object[] arr : mapper.all()) sender.sendMessage(
                Utils.getActionComponentOfLocation("0".equals(arr[3]), (String) arr[4], ((Double) arr[5]).intValue(),
                    ((Double) arr[6]).intValue(), ((Double) arr[7]).intValue()),
                Utils.getPlayerNameComponentWithUUID((String) arr[2], (String) arr[1], true),
                Utils.getAddressComponent((String) arr[8], sender.hasPermission("nekoguard.lookup.session.address")),
                Utils.getTimeComponent((String) arr[0], now)
            );
            sender.sendMessage(Constants.makeFooter(page, all));
        })));
        main.addCommandHistory(sender, it -> sendSessionMessage(sender, it, fn));
    }

    public void sendExplosionMessage(@NotNull final ProxiedCommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryExplosionCount(), q2 = api.queryExplosion(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all -> db.query(q2, res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) {
                sender.sendMessage(Constants.NO_RECORDS);
                return;
            }
            final SeriesMapper.Mapper mapper = Mappers.EXPLOSIONS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            for (final Object[] arr : mapper.all()) sender.sendMessage(
                Utils.getTimeComponent((String) arr[0], now),
                Utils.getEntityTypePerformerComponent((String) arr[1], (String) arr[3], ((Double) arr[4]).intValue(),
                    ((Double) arr[5]).intValue(), ((Double) arr[6]).intValue()),
                Constants.TARGET,
                Utils.getPerformerComponent((String) arr[2]),
                "0".equals(arr[7]) ? Constants.UNCERTAIN : Constants.EMPTY
            );
            sender.sendMessage(Constants.makeFooter(page, all));
        })));
        main.addCommandHistory(sender, it -> sendExplosionMessage(sender, it, fn));
    }
}
