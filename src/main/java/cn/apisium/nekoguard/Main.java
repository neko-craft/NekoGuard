package cn.apisium.nekoguard;

import cn.apisium.nekocommander.Commander;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.command.Command;
import org.bukkit.plugin.java.annotation.command.Commands;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.permission.Permissions;
import org.bukkit.plugin.java.annotation.plugin.*;
import org.bukkit.plugin.java.annotation.plugin.author.Author;

import java.util.*;

@Plugin(name = "NekoGuard", version = "1.0")
@Description("An essential plugin used in NekoCraft.")
@Author("Shirasawa")
@Website("https://apisium.cn")
@ApiVersion(ApiVersion.Target.v1_13)
@Commands(@Command(name = "nekoguard", permission = "nekoguard.use", aliases = { "ng" }))
@Permissions(@Permission(name = "nekoguard.use", defaultValue = PermissionDefault.TRUE))
public final class Main extends JavaPlugin {
    private Database db;
    private API api;
    private Messages messages;
    private static Main INSTANCE;
    protected int commandActionHistoryCount = 6;
    protected boolean recordMonsterKilledWithoutCustomName = false;
    protected final Set<Player> inspecting = Collections.newSetFromMap(new WeakHashMap<>());

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
        commandActionHistoryCount = getConfig().getInt("commandActionHistoryCount", 6);
        recordMonsterKilledWithoutCustomName = getConfig().getBoolean("recordMonsterKilledWithoutCustomName", false);
        db = new Database(
                Objects.requireNonNull(getConfig().getString("database")),
                url,
                getConfig().getString("username"),
                getConfig().getString("password", "")
        );
        api = new API(Objects.requireNonNull(getConfig().getString("measurementPrefix")), this);
        messages = new Messages(api, db);
        new Commander(this)
            .setDefaultDescription("The main command of NekoGuard.")
            .setDefaultPermissionMessage("§c你没有足够的权限来执行当前指令!")
            .setDefaultUsage("§c指令用法错误!")
            .registerCommand(new cn.apisium.nekoguard.Commands(this));
        getServer().getPluginManager().registerEvents(new Events(this), this);
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

//    @Nullable
//    @Override
//    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String alias, @NotNull String[] args) {
//        if (args.length != 0) sender.sendMessage(args[args.length - 1]);
//        return null;
//    }
}
