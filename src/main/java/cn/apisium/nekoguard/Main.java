package cn.apisium.nekoguard;

import cn.apisium.nekocommander.Commander;
import cn.apisium.nekoguard.changes.ChangeList;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.command.Command;
import org.bukkit.plugin.java.annotation.command.Commands;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.permission.Permissions;
import org.bukkit.plugin.java.annotation.plugin.*;
import org.bukkit.plugin.java.annotation.plugin.author.Author;

import java.util.*;
import java.util.function.Consumer;

@Plugin(name = "NekoGuard", version = "1.0")
@Description("An essential plugin used in NekoCraft.")
@Author("Shirasawa")
@Website("https://apisium.cn")
@ApiVersion(ApiVersion.Target.v1_13)
@Commands(@Command(name = "nekoguard", aliases = { "ng" }))
@Permissions({
    @Permission(name = "nekoguard.inspect"),
    @Permission(name = "nekoguard.lookup.chat"),
    @Permission(name = "nekoguard.lookup.command"),
    @Permission(name = "nekoguard.lookup.item"),
    @Permission(name = "nekoguard.lookup.block"),
    @Permission(name = "nekoguard.lookup.death"),
    @Permission(name = "nekoguard.lookup.container"),
    @Permission(name = "nekoguard.lookup.session"),
    @Permission(name = "nekoguard.fetch.action"),
    @Permission(name = "nekoguard.fetch.container"),
    @Permission(name = "nekoguard.rollback.block"),
    @Permission(name = "nekoguard.rollback.container"),
    @Permission(name = "nekoguard.rollback.entity"),
    @Permission(name = "nekoguard.fetch.action"),
    @Permission(name = "nekoguard.fetch.container")
})
public final class Main extends JavaPlugin {
    private Database db;
    private API api;
    private Messages messages;
    private static Main INSTANCE;
    protected int commandActionHistoryCount = 3;
    protected boolean recordMonsterKilledWithoutCustomName = false;
    public final Set<Player> inspecting = Collections.newSetFromMap(new WeakHashMap<>());
    public final WeakHashMap<CommandSender, LinkedList<ChangeList>> commandActions = new WeakHashMap<>();
    public final WeakHashMap<CommandSender, Consumer<Integer>> commandHistories = new WeakHashMap<>();

    { INSTANCE = this; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        final String url = getConfig().getString("url");
        if (url == null || url.equals("")) {
            getLogger().warning("No InfluexDB url provided.");
            setEnabled(false);
            return;
        }
        commandActionHistoryCount = getConfig().getInt("commandActionHistoryCount", 3);
        recordMonsterKilledWithoutCustomName = getConfig().getBoolean("recordMonsterKilledWithoutCustomName", false);
        db = new Database(
                Objects.requireNonNull(getConfig().getString("database")),
                url,
                getConfig().getString("username"),
                getConfig().getString("password", ""),
                getConfig().getString("retentionPolicy", "")
        );
        api = new API(Objects.requireNonNull(getConfig().getString("measurementPrefix")), this);
        messages = new Messages(this);
        new Commander(this)
            .setDefaultDescription("The main command of NekoGuard.")
            .setDefaultPermissionMessage("§c你没有足够的权限来执行当前指令!")
            .setDefaultUsage("§c指令用法错误!")
            .registerCommand(new cn.apisium.nekoguard.Commands(this));
        getServer().getPluginManager().registerEvents(new Events(this), this);

        if (Constants.IS_PAPER) getServer().getScheduler().runTaskTimerAsynchronously(this,
            () -> inspecting.forEach(it -> it.sendActionBar(Constants.IN_INSPECTING)), 20, 20);
    }

    @Override
    public void onDisable() {
        if (db != null) db.instance.close();
    }

    @SuppressWarnings("unused")
    public Database getDatabase() { return db; }

    public Messages getMessages() { return messages; }

    public API getApi() { return api; }

    public static Main getInstance() { return INSTANCE; }

    public void addCommandAction(final CommandSender sender, final ChangeList list) {
        final LinkedList<ChangeList> actions = commandActions.computeIfAbsent(sender, it -> new LinkedList<>());
        if (actions.size() >= commandActionHistoryCount) actions.removeLast();
        actions.addFirst(list);
    }

    public void addCommandHistory(final CommandSender sender, final Consumer<Integer> fn) {
        commandHistories.put(sender, fn);
    }
}
