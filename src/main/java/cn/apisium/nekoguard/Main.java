package cn.apisium.nekoguard;

import co.aikar.commands.PaperCommandManager;
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
@Commands(@Command(name = "nekoguard", permission = "nekoguard.use", aliases = { "guard", "ng" }))
@Permissions(@Permission(name = "nekoguard.use", defaultValue = PermissionDefault.TRUE))
public final class Main extends JavaPlugin {
    private Database db;
    private API api;

    protected final Set<Player> inspecting = Collections.newSetFromMap(new WeakHashMap<>());

    @SuppressWarnings("deprecation")
    @Override
    public void onEnable() {
        saveDefaultConfig();
        final String url = getConfig().getString("url");
        if (url == null || url.equals("")) {
            getLogger().warning("No InfluexDB url provided.");
            setEnabled(false);
            return;
        }
        db = new Database(
                Objects.requireNonNull(getConfig().getString("database")),
                url,
                getConfig().getString("username"),
                getConfig().getString("password", "")
        );
        api = new API(db, Objects.requireNonNull(getConfig().getString("measurementPrefix")), this);
        getServer().getPluginManager().registerEvents(new Events(this), this);
        final PaperCommandManager manager = new PaperCommandManager(this);
        manager.enableUnstableAPI("help");
        manager.registerCommand(new cn.apisium.nekoguard.Commands(this));
    }

    @Override
    public void onDisable() {
        if (db != null) db.instance.close();
    }

    @SuppressWarnings("unused")
    public Database getDatabase() { return db; }

    public API getApi() { return api; }
}
