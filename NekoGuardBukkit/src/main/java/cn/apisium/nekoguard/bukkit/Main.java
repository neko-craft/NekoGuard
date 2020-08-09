package cn.apisium.nekoguard.bukkit;

import cn.apisium.nekocommander.ProxiedCommandSender;
import cn.apisium.nekocommander.impl.BukkitCommander;
import cn.apisium.nekoguard.Constants;
import cn.apisium.nekoguard.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.annotation.command.Command;
import org.bukkit.plugin.java.annotation.command.Commands;
import org.bukkit.plugin.java.annotation.permission.Permission;
import org.bukkit.plugin.java.annotation.permission.Permissions;
import org.bukkit.plugin.java.annotation.plugin.*;
import org.bukkit.plugin.java.annotation.plugin.author.Author;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
    @Permission(name = "nekoguard.lookup.session.address"),
    @Permission(name = "nekoguard.fetch.action"),
    @Permission(name = "nekoguard.fetch.container"),
    @Permission(name = "nekoguard.rollback.block"),
    @Permission(name = "nekoguard.rollback.container"),
    @Permission(name = "nekoguard.rollback.entity"),
    @Permission(name = "nekoguard.fetch.action"),
    @Permission(name = "nekoguard.fetch.container")
})
public final class Main extends JavaPlugin {
    private static cn.apisium.nekoguard.Main INSTANCE;
    private static Main PLUGIN;
    private API api;
    protected boolean recordMonsterKilledWithoutCustomName;
    protected boolean recordEntitiesNaturalSpawn;

    { PLUGIN = this; }

    static { Utils.PLATFORM = new Impl(); }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        final String url = getConfig().getString("url");
        if (url == null || url.equals("")) {
            getLogger().warning("No InfluexDB url provided.");
            setEnabled(false);
            return;
        }
        recordEntitiesNaturalSpawn = getConfig().getBoolean("recordEntitiesNaturalSpawn", false);
        recordMonsterKilledWithoutCustomName = getConfig().getBoolean("recordMonsterKilledWithoutCustomName", false);
        INSTANCE = new cn.apisium.nekoguard.Main(
            url,
            getConfig().getInt("commandActionHistoryCount", 3),
            Objects.requireNonNull(getConfig().getString("database")),
            getConfig().getString("username"),
            getConfig().getString("password", ""),
            getConfig().getString("retentionPolicy", ""),
            Objects.requireNonNull(getConfig().getString("measurementPrefix")),
            new BukkitCommander(this)
        );
        api = new cn.apisium.nekoguard.bukkit.API(INSTANCE.getApi());

        getServer().getPluginManager().registerEvents(new Events(this), this);

        if (cn.apisium.nekoguard.Constants.IS_PAPER) getServer().getScheduler().runTaskTimerAsynchronously(this,
            () -> INSTANCE.inspecting.forEach((it, v) -> ((Player) it.origin).sendActionBar(Constants.IN_INSPECTING)), 20, 20);
    }

    @Override
    public void onDisable() {
        INSTANCE.onDisable();
    }

    @NotNull
    public API getApi() {
        return api;
    }

    public static cn.apisium.nekoguard.Main getInstance() { return INSTANCE; }
    public static Main getPlugin() { return PLUGIN; }
}
