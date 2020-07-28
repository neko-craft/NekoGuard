package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.CommandSenderType;
import cn.apisium.nekoguard.mappers.Mappers;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import cn.apisium.nekoguard.utils.Utils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.influxdb.dto.QueryResult;
import org.influxdb.querybuilder.SelectQueryImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

import static org.influxdb.querybuilder.BuiltQuery.QueryBuilder.eq;

@SuppressWarnings("deprecation")
public final class Messages {
    final API api;
    final Database db;

    public Messages(final API api, final Database db) {
        this.api = api;
        this.db = db;
    }

    public void sendQueryCommandMessage(@NotNull final CommandSender sender, @Nullable String type, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final String type1;
        if (type != null && !CommandSenderType.getValueList().contains(type)) {
            final OfflinePlayer p = Bukkit.getOfflinePlayer(type);
            if (!p.hasPlayedBefore()) {
                sender.sendMessage(Constants.PLAYER_NOT_EXISTS);
                return;
            }
            type1 = p.getUniqueId().toString();
        }
        else type1 = type;
        final SelectQueryImpl q1 = api.queryCommandCount(type1), q2 = api.queryCommand(type1, page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all -> db.query(q2, res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) return;
            final SeriesMapper.Mapper mapper = Mappers.COMMANDS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            try {
                for (final Object[] arr : mapper.all()) sender.spigot().sendMessage(
                    Utils.getTimeComponent((String) arr[0], now),
                    Utils.getPlayerCommandNameComponent((String) arr[1], (String) arr[2]),
                    Utils.genCopyComponent((String) arr[3], ": ")
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
            sender.spigot().sendMessage(Constants.makeFooter(page, all));
        })));
    }

    public void sendQueryBlockMessage(@NotNull final CommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryBlockCount(), q2 = api.queryBlock(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.BLOCKS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                try {
                    for (final Object[] arr : mapper.all()) sender.spigot().sendMessage(
                        Utils.getBlockActionComponent(arr[1].equals("1"), (String) arr[4],
                            ((Double) arr[5]).intValue(), ((Double) arr[6]).intValue(), ((Double) arr[7]).intValue()),
                        Utils.getPerformerComponent((String) arr[2]),
                        Utils.getTimeComponent((String) arr[3], now),
                        Utils.getBlockComponent((String) arr[0])
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.spigot().sendMessage(Constants.makeFooter(page, all));
            })
        ));
    }
    public void sendQueryBlockMessage(@NotNull final CommandSender sender, @NotNull final String world, final int x, final int y, final int z, final int page) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(world);
        sendQueryBlockMessage(sender, page, it -> it.where(eq("world", world))
            .and(eq("x", x))
            .and(eq("y", y))
            .and(eq("z", z))
        );
    }

    public void sendQueryChatMessage(@NotNull final CommandSender sender, @Nullable String player, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        if (player != null && player.length() != 36) {
            final OfflinePlayer p = Bukkit.getOfflinePlayer(player);
            if (!p.hasPlayedBefore()) {
                sender.sendMessage(Constants.PLAYER_NOT_EXISTS);
                return;
            }
            player = p.getUniqueId().toString();
        }
        final SelectQueryImpl q1 = api.queryChatCount(player), q2 = api.queryChat(player, page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all -> db.query(q2, res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) return;
            final SeriesMapper.Mapper mapper = Mappers.CHATS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            try {
                for (final Object[] arr : mapper.all()) sender.spigot().sendMessage(
                    Utils.getTimeComponent((String) arr[0], now),
                    Utils.getPlayerPerformerNameComponent((String) arr[1], false),
                    Utils.genCopyComponent((String) arr[2], ": ")
                );
            } catch (Exception e) {
                e.printStackTrace();
            }
            sender.spigot().sendMessage(Constants.makeFooter(page, all));
        })));
    }

    public void sendContainerActionsMessage(@NotNull final CommandSender sender, @NotNull final String world, final int x, final int y, final int z, final int page) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(world);
        final SelectQueryImpl q1 = api.queryContainerActionsCount(), q2 = api.queryContainerActions(page);
        API.processSingleContainerBlockQuery(q1, world, x, y, z);
        API.processSingleContainerBlockQuery(q2, world, x, y, z);
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.CONTAINER_ACTIONS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                final boolean canCopy = sender.hasPermission("nekoguard.fetch.container");
                try {
                    for (final Object[] arr : mapper.all()) {
                        final TextComponent t = new TextComponent((String) arr[2]);
                        t.setColor(ChatColor.GRAY);
                        final boolean isAdd = Utils.isAddContainerAction(arr, world, x, y, z);
                        final String time = (String) arr[0];
                        sender.spigot().sendMessage(
                            Utils.getBlockActionComponent(isAdd, world, x, y, z),
                            Utils.getItemStackDetails((String) arr[1], canCopy ? "/ng fetch container " + time : null),
                            Constants.SPACE,
                            isAdd
                                ? Utils.getContainerPerformerName((String) arr[2], (String) arr[3], (Double) arr[4], (Double) arr[5], (Double) arr[6])
                                : Utils.getContainerPerformerName((String) arr[7], (String) arr[8], (Double) arr[9], (Double) arr[10], (Double) arr[11]),
                            Utils.getTimeComponent(time, now)
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.spigot().sendMessage(Constants.makeFooter(page, all));
            })
        ));
    }

    public void sendContainerActionsMessage(@NotNull final CommandSender sender, @NotNull final String entity, final int page) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(entity);
        final SelectQueryImpl q1 = api.queryContainerActionsCount(), q2 = api.queryContainerActions(page);
        API.processSingleContainerEntityQuery(q1, entity);
        API.processSingleContainerEntityQuery(q2, entity);
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.CONTAINER_ACTIONS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                final boolean canCopy = sender.hasPermission("nekoguard.fetch.container");
                try {
                    for (final Object[] arr : mapper.all()) {
                        final TextComponent t = new TextComponent((String) arr[2]);
                        t.setColor(ChatColor.GRAY);
                        final boolean isAdd = arr[7].equals(entity);
                        final String time = (String) arr[0];
                        sender.spigot().sendMessage(
                            Utils.getContainerActionComponent(isAdd, entity),
                            Utils.getItemStackDetails((String) arr[1], canCopy ? "/ng fetch container " + time : null),
                            Constants.SPACE,
                            isAdd
                                ? Utils.getContainerPerformerName((String) arr[2], (String) arr[3], (Double) arr[4], (Double) arr[5], (Double) arr[6])
                                : Utils.getContainerPerformerName((String) arr[7], (String) arr[8], (Double) arr[9], (Double) arr[10], (Double) arr[11]),
                            Utils.getTimeComponent(time, now)
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.spigot().sendMessage(Constants.makeFooter(page, all));
            })
        ));
    }

    public void sendContainerActionsMessage(@NotNull final CommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryContainerActionsCount(), q2 = api.queryContainerActions(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.CONTAINER_ACTIONS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                final boolean canCopy = sender.hasPermission("nekoguard.fetch.container");
                try {
                    for (final Object[] arr : mapper.all()) {
                        final String time = (String) arr[0];
                        sender.spigot().sendMessage(
                            Utils.getTimeComponent(time, now),
                            Utils.getItemStackDetails((String) arr[1], canCopy ? "/ng fetch container " + time : null),
                            Constants.SPACE,
                            Utils.getContainerPerformerNameRoughly((String) arr[2], (String) arr[3], (Double) arr[4], (Double) arr[5], (Double) arr[6]),
                            Constants.LEFT_ARROW,
                            Utils.getContainerPerformerNameRoughly((String) arr[7], (String) arr[8], (Double) arr[9], (Double) arr[10], (Double) arr[11])
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.spigot().sendMessage(Constants.makeFooter(page, all));
            })
        ));
    }

    public void sendQueryDeathMessage(@NotNull final CommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryDeathCount(), q2 = api.queryDeath(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.DEATHS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                try {
                    for (final Object[] arr : mapper.all()) {
                        final TextComponent reason = new TextComponent(StringUtils.rightPad((String) arr[4], 16, " ") + " "),
                            icon = new TextComponent(" \u2694 ");
                        reason.setColor(ChatColor.GRAY);
                        icon.setColor(ChatColor.RED);
                        Utils.processActionComponent(icon, (String) arr[5], ((Double) arr[6]).intValue(),
                            ((Double) arr[7]).intValue(), ((Double) arr[8]).intValue());
                        sender.spigot().sendMessage(
                            Utils.getTimeComponent((String) arr[0], now),
                            reason,
                            Utils.getPerformerComponent((String) arr[1], false),
                            icon,
                            Utils.getDeathEntityComponent((String) arr[2], (String) arr[3])
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.spigot().sendMessage(Constants.makeFooter(page, all));
            })
        ));
    }

    public void sendQuerySpawnMessage(@NotNull final CommandSender sender, @NotNull final String id, final int page) {
        Objects.requireNonNull(sender);
        Objects.requireNonNull(id);
        sendQuerySpawnMessage(sender, page, it -> it.where(eq("id", id)));
    }
    public void sendQuerySpawnMessage(@NotNull final CommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.querySpawnCount(), q2 = api.querySpawn(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all ->
            db.query(q2, res -> {
                final QueryResult.Series data = Utils.getFirstResult(res);
                if (data == null) return;
                final SeriesMapper.Mapper mapper = Mappers.SPAWNS.parse(data);
                sender.sendMessage(Constants.HEADER);
                final long now = Instant.now().toEpochMilli();
                try {
                    for (final Object[] arr : mapper.all()) {
                        final TextComponent reason = new TextComponent(StringUtils.rightPad(
                            (String) ObjectUtils.defaultIfNull(arr[1], "UNKNOWN"), 10, " "));
                        Utils.processActionComponent(reason, (String) arr[3], ((Double) arr[4]).intValue(),
                            ((Double) arr[5]).intValue(), ((Double) arr[6]).intValue());
                        sender.spigot().sendMessage(
                            Utils.getTimeComponent((String) arr[0], now),
                            reason,
                            Utils.getEntityTypePerformerComponent((String) arr[7], (String) arr[2])
                        );
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                sender.spigot().sendMessage(Constants.makeFooter(page, all));
            })
        ));
    }

    public void sendItemActionMessage(@NotNull final CommandSender sender, final int page, @Nullable final Consumer<SelectQueryImpl> fn) {
        Objects.requireNonNull(sender);
        final SelectQueryImpl q1 = api.queryItemActionCount(), q2 = api.queryItemAction(page);
        if (fn != null) {
            fn.accept(q1);
            fn.accept(q2);
        }
        db.query(q1, Utils.getCountConsumer(all -> db.query(q2, res -> {
            final QueryResult.Series data = Utils.getFirstResult(res);
            if (data == null) return;
            final SeriesMapper.Mapper mapper = Mappers.ITEM_ACTIONS.parse(data);
            sender.sendMessage(Constants.HEADER);
            final long now = Instant.now().toEpochMilli();
            final boolean canCopy = sender.hasPermission("nekoguard.fetch.action");
            try {
                for (final Object[] arr : mapper.all()) {
                    final String time = (String) arr[0];
                    sender.spigot().sendMessage(
                        Utils.getBlockActionComponent("1".equals(arr[2]), (String) arr[4], ((Double) arr[5]).intValue(),
                            ((Double) arr[6]).intValue(), ((Double) arr[7]).intValue()),
                        Utils.getItemStackDetails((String) arr[3], canCopy ? "/ng fetch action " + time : null),
                        Constants.SPACE,
                        Utils.getPlayerPerformerNameComponent((String) arr[1], true),
                        Utils.getTimeComponent(time, now)
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            sender.spigot().sendMessage(Constants.makeFooter(page, all));
        })));
    }
}
