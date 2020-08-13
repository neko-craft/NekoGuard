package cn.apisium.nekoguard.bukkit.utils;

import cn.apisium.nekoguard.utils.ContainerRecord;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Utils {
    private Utils() {}

    @NotNull
    public static String getBlockName(@NotNull final Material type) {
        final NamespacedKey k = type.getKey();
        return "block." + k.getNamespace() + "." + k.getKey();
    }

    @NotNull
    public static String getBlockPerformer(@NotNull final Block block) {
        return cn.apisium.nekoguard.utils.Utils.getBlockPerformer(block.getWorld().getName(),
            block.getX(), block.getY(), block.getZ());
    }

    public static boolean isNeedToRecordContainerAction(final InventoryType type) {
        switch (type) {
            case CHEST:
            case BARREL:
            case HOPPER:
            case SMOKER:
            case BREWING:
            case FURNACE:
            case DROPPER:
            case LECTERN:
            case CREATIVE:
            case PLAYER:
            case MERCHANT:
            case DISPENSER:
            case SHULKER_BOX:
            case BLAST_FURNACE:
                return true;
            default: return false;
        }
    }

    @NotNull
    public static String getKiller(@NotNull final LivingEntity entity) {
        if (entity.getKiller() != null) return entity.getKiller().getUniqueId().toString();
        final EntityDamageEvent cause = entity.getLastDamageCause();
        if (cause instanceof EntityDamageByBlockEvent) {
            final EntityDamageByBlockEvent c = (EntityDamageByBlockEvent) cause;
            if (c.getDamager() != null) return '#' + c.getDamager().getType().getKey().toString();
        } else if (cause instanceof EntityDamageByEntityEvent) return '@' + ((EntityDamageByEntityEvent) cause)
            .getDamager().getType().getKey().toString();
        return "";
    }

    @NotNull
    public static String getEntityPerformer(@Nullable final Entity entity) {
        return entity == null
            ? "" : entity instanceof OfflinePlayer
            ? entity.getUniqueId().toString()
            : '@' + entity.getType().getKey().toString();
    }

    @SuppressWarnings("ConstantConditions")
    @Nullable
    public static Inventory getInventory(@Nullable final String entity, @Nullable final String world, @Nullable final Double x,
                                         @Nullable final Double y, @Nullable final Double z) {
        if (entity != null) {
            final Entity e = Bukkit.getEntity(UUID.fromString(entity));
            return e instanceof InventoryHolder ? ((InventoryHolder) e).getInventory() : null;
        }
        if (world != null) {
            final World w = Bukkit.getWorld(world);
            if (w == null) return null;
            final BlockState state = w.getBlockAt(x.intValue(), y.intValue(), z.intValue()).getState();
            return state instanceof InventoryHolder ? ((InventoryHolder) state).getInventory() : null;
        }
        return null;
    }

    @Nullable
    public static ContainerRecord getContainerRecord(@Nullable final Inventory inv) {
        if (inv == null) return null;
        try {
            final InventoryHolder h = inv.getHolder();
            if (h instanceof Entity) return new ContainerRecord(((Entity) h).getUniqueId().toString());
            else if (h instanceof BlockInventoryHolder) {
                final Block b = ((BlockInventoryHolder) h).getBlock();
                return new ContainerRecord(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            } else return null;
        } catch (final Exception ignored) {
            return null;
        }
    }

    public static boolean hasDifferentSlot(@NotNull final InventoryType inv) {
        switch (inv) {
            case FURNACE:
            case SMOKER:
            case BLAST_FURNACE:
                return true;
            default: return false;
        }
    }

    public static int getItemStackId(@NotNull final Inventory source, @NotNull final Inventory target, @NotNull final ItemStack is) {
        final ItemMeta meta = is.getItemMeta();
        int hash = 31 + is.getType().hashCode();
        hash *= 31;
        hash += is.hasItemMeta() ? meta.hashCode() : 0;
        hash *= 31;
        hash += source.hashCode();
        hash *= 31;
        return hash + target.hashCode();
    }
}
