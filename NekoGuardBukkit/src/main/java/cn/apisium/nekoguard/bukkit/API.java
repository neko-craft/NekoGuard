package cn.apisium.nekoguard.bukkit;

import cn.apisium.nekoguard.bukkit.utils.NMSUtils;
import cn.apisium.nekoguard.bukkit.utils.Utils;
import cn.apisium.nekoguard.utils.ContainerRecord;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class API {
    private final cn.apisium.nekoguard.API front;
    private ArrayList<Object[]> itemsList = new ArrayList<>();
    API(final cn.apisium.nekoguard.API front) {
        this.front = front;
        Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getPlugin(), () -> {
            if (itemsList.isEmpty()) return;
            final ArrayList<Object[]> list = itemsList;
            itemsList = new ArrayList<>();
            list.forEach(it -> front.recordContainerAction(
                NMSUtils.serializeItemStack((ItemStack) it[0]),
                (ContainerRecord) it[1], (ContainerRecord) it[2], (long) it[3]
            ));
        }, 1, 1);
    }

    public void recordContainerAction(@NotNull final ItemStack is, @Nullable final Inventory source, @Nullable final Inventory target) {
        if (source == null && target == null) return;
        itemsList.add(new Object[] { is.clone(), Utils.getContainerRecord(source),
            Utils.getContainerRecord(target), front.getCurrentTime() });
    }

    public void recordDeath(@NotNull final String performer, @NotNull final String cause, @NotNull final Entity entity) {
        final Location loc = entity.getLocation();
        front.recordDeath(performer, cause, "@" + entity.getType().getKey(), loc.getWorld().toString(),
            NMSUtils.serializeEntity(entity), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public void recordSpawn(@NotNull final Entity entity, @NotNull final String reason) {
        final Location loc = entity.getLocation();
        front.recordSpawn(entity.getType().getKey().toString(), reason, loc.getWorld().toString(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), entity.getUniqueId().toString());
    }

    public void recordBlockAction(@NotNull final Block block, @NotNull final String performer, final boolean isBreak) {
        recordBlockAction(block.getState(), performer, isBreak);
    }
    public void recordBlockAction(@NotNull final Block block, @NotNull final Player performer, final boolean isBreak) {
        recordBlockAction(block.getState(), performer.getUniqueId().toString(), isBreak);
    }
    public void recordBlockAction(@NotNull final BlockState block, @NotNull final String performer, final boolean isBreak) {
        if (block.getType().isAir()) return;
        front.recordBlockAction(performer, isBreak, block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
            isBreak ? NMSUtils.getFullBlockData(block) : block.getType().getKey().toString());
    }

    public void recordBlocksBreak(@NotNull final List<Block> blocks, @NotNull final String performer) {
        blocks.forEach(it -> recordBlockAction(it, performer, true));
    }

    public void recordPlayerSession(@NotNull final Player player, final boolean isLogin) {
        final Location loc = player.getLocation();
        front.recordPlayerSession(player.getUniqueId().toString(), player.getName(), isLogin, loc.getWorld().toString(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), player.getAddress() == null ? "" : player.getAddress().getHostString());
    }
}
