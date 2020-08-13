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
import java.util.concurrent.ConcurrentHashMap;

public final class API {
    private final cn.apisium.nekoguard.API front;
    private ArrayList<Object[]> itemsList = new ArrayList<>();
    private final ConcurrentHashMap<Integer, Object[]> mergedItems = new ConcurrentHashMap<>();
    API(final cn.apisium.nekoguard.API front) {
        this.front = front;
        Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getPlugin(), () -> {
            final ArrayList<Object[]> list;
            synchronized (this) {
                if (itemsList.isEmpty()) return;
                list = itemsList;
                itemsList = new ArrayList<>();
            }
            for (final Object[] it : list) front.recordContainerAction(
                NMSUtils.serializeItemStack((ItemStack) it[0]),
                (ContainerRecord) it[1], (ContainerRecord) it[2], (long) it[3]
            );
        }, 1, 1);
        Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getPlugin(), () -> mergedItems.values().removeIf(arr -> {
            if (front.getFixedTime() - (Long) arr[0] < 60000000000L) return false;
            recordContainerAction((ItemStack) arr[1], (Inventory) arr[2], (Inventory) arr[3], (long) arr[0]);
            return true;
        }), 20 * 60, 20 * 60);
    }

    public void recordContainerAction(@NotNull final ItemStack is, @Nullable final Inventory source, @Nullable final Inventory target) {
        if (source == null && target == null) return;
        synchronized (this) {
            itemsList.add(new Object[]{is.clone(), Utils.getContainerRecord(source),
                Utils.getContainerRecord(target), front.getCurrentTime()});
        }
    }

    public void recordContainerAction(@NotNull final ItemStack is, @Nullable final Inventory source, @Nullable final Inventory target, final long time) {
        if (source == null && target == null) return;
        synchronized (this) {
            itemsList.add(new Object[]{is.clone(), Utils.getContainerRecord(source),
                Utils.getContainerRecord(target), time});
        }
    }

    public void recordContainerAction2(@NotNull final ItemStack is, @NotNull final Inventory source, @NotNull final Inventory target) {
        final int max = is.getMaxStackSize(), amount = is.getAmount();
        if (max <= amount) {
            recordContainerAction(is, source, target);
            return;
        }
        final int key = Utils.getItemStackId(source, target, is);
        final Object[] arr = mergedItems.get(key);
        if (arr == null) mergedItems.put(key, new Object[] { front.getFixedTime(), is, source, target });
        else {
            final ItemStack i = (ItemStack) arr[1];
            final long time = (long) arr[0];
            arr[0] = front.getFixedTime();
            final int added = i.getAmount() + amount;
            if (added > max) i.setAmount(added - max);
            else if (added < max) {
                i.setAmount(added);
                return;
            }
            is.setAmount(max);
            recordContainerAction(is, source, target, time);
        }
    }

    public void recordDeath(@NotNull final String performer, @NotNull final String cause, @NotNull final Entity entity) {
        final Location loc = entity.getLocation();
        front.recordDeath(performer, cause, Utils.getEntityPerformer(entity), loc.getWorld().getName(),
            NMSUtils.serializeEntity(entity), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public void recordSpawn(@NotNull final Entity entity, @NotNull final String reason) {
        final Location loc = entity.getLocation();
        front.recordSpawn(entity.getType().getKey().toString(), reason, loc.getWorld().getName(),
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
        front.recordPlayerSession(player.getUniqueId().toString(), player.getName(), isLogin, loc.getWorld().getName(),
            loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), player.getAddress() == null ? "" : player.getAddress().getHostString());
    }
}
