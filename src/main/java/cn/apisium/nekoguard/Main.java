package cn.apisium.nekoguard;

import cn.apisium.nekocommander.Commander;
import cn.apisium.nekocommander.ProxiedCommandSender;
import cn.apisium.nekoguard.utils.HashCodeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class Main {
    private final Database db;
    private final API api;
    private final Messages messages;
    private static Main INSTANCE;
    protected final int commandActionHistoryCount;
    public final HashCodeMap<ProxiedCommandSender, Void> inspecting = new HashCodeMap<>();
    public final WeakHashMap<ProxiedCommandSender, LinkedList<ChangeList>> commandActions = new WeakHashMap<>();
    public final WeakHashMap<ProxiedCommandSender, Consumer<Integer>> commandHistories = new WeakHashMap<>();

    { INSTANCE = this; }

    public Main(
        @NotNull final String url,
        final int commandActionHistoryCount,
        final int commandSpeedLimit,
        @NotNull final String database,
        @Nullable final String username,
        @Nullable final String password,
        @Nullable final String retentionPolicy,
        @NotNull final String measurementPrefix,
        @NotNull final Commander<?, ?> commander
    ) {
        if (url.isEmpty()) throw new IllegalArgumentException("No InfluexDB url provided.");
        this.commandActionHistoryCount = commandActionHistoryCount;
        db = new Database(database, url, username, password, retentionPolicy);
        api = new API(Objects.requireNonNull(measurementPrefix), this);
        messages = new Messages(this);
        commander
            .setDefaultDescription("The main command of NekoGuard.")
            .setDefaultPermissionMessage("§c你没有足够的权限来执行当前指令!")
            .setDefaultUsage("§c指令用法错误!")
            .registerCommand(new cn.apisium.nekoguard.Commands(this));
        if (commandSpeedLimit > 0) {
            final WeakHashMap<ProxiedCommandSender, Long> map = new WeakHashMap<>();
            commander.onPreCommand((a, b, c, d) -> {
                if (a.hasPermission("nekoguard.commandlimit")) return true;
                final long time = System.currentTimeMillis();
                if (!map.containsKey(a)) {
                    map.put(a, time);
                    return true;
                }
                if (map.get(a) + commandSpeedLimit > time) {
                    a.sendMessage(Constants.COMMAND_LIMIT);
                    return false;
                } else {
                    map.put(a, time);
                    return true;
                }
            });
        }
    }

    public void onDisable() {
        db.instance.close();
        api.timer.cancel();
    }

    @SuppressWarnings("unused")
    @NotNull
    public Database getDatabase() { return db; }

    @NotNull
    public Messages getMessages() { return messages; }

    @NotNull
    public API getApi() { return api; }

    @NotNull
    @SuppressWarnings("unused")
    public static Main getInstance() { return INSTANCE; }

    public void addCommandAction(final ProxiedCommandSender sender, final ChangeList list) {
        final LinkedList<ChangeList> actions = commandActions.computeIfAbsent(sender, it -> new LinkedList<>());
        if (actions.size() >= commandActionHistoryCount) actions.removeLast();
        actions.addFirst(list);
    }

    public void addCommandHistory(final ProxiedCommandSender sender, final Consumer<Integer> fn) {
        commandHistories.put(sender, fn);
    }

    public boolean isInspecting(final int code) {
        return inspecting.containsHash(code);
    }
    public boolean isInspecting(final Object obj) {
        return isInspecting(obj.hashCode());
    }
}
