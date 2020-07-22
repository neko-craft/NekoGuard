package cn.apisium.nekoguard.utils;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ContainerAction {
    public final ItemStack item;
    public final long time;
    public int sourceX, sourceY, sourceZ, targetX, targetY, targetZ;
    public String sourceWorld, targetWorld, sourceEntity, targetEntity;
    public ContainerAction(@NotNull final ItemStack item, @Nullable final Inventory source, @Nullable final Inventory target, final long time) {
        this.item = item;
        this.time = time;
        setSource(source);
        setTarget(target);
    }

    public ContainerAction(@NotNull final ItemStack item, @Nullable final String source, @Nullable final String target, final long time) {
        this.item = item;
        this.time = time;
        sourceEntity = source;
        targetEntity = target;
    }

    public ContainerAction(@NotNull final ItemStack item, @Nullable final Inventory source, @Nullable final String target, final long time) {
        this.item = item;
        this.time = time;
        setSource(source);
        targetEntity = target;
    }

    public ContainerAction(@NotNull final ItemStack item, @Nullable final String source, @Nullable final Inventory target, final long time) {
        this.item = item;
        this.time = time;
        sourceEntity = source;
        setTarget(target);
    }

    private void setSource(@Nullable final Inventory source) {
        if (source != null) {
            final InventoryHolder holder = source.getHolder();
            if (holder == null) return;
            if (holder instanceof BlockInventoryHolder) {
                final Block block = ((BlockInventoryHolder) holder).getBlock();
                sourceWorld = block.getWorld().getName();
                sourceX = block.getX();
                sourceY = block.getY();
                sourceZ = block.getZ();
            } else if (holder instanceof Entity) sourceEntity = ((Entity) holder).getUniqueId().toString();
        }
    }
    private void setTarget(@Nullable final Inventory target) {
        if (target != null) {
            final InventoryHolder holder = target.getHolder();
            if (holder == null) return;
            if (holder instanceof BlockInventoryHolder) {
                final Block block = ((BlockInventoryHolder) holder).getBlock();
                targetWorld = block.getWorld().getName();
                targetX = block.getX();
                targetY = block.getY();
                targetZ = block.getZ();
            } else if (holder instanceof Entity) targetEntity = ((Entity) holder).getUniqueId().toString();
        }
    }
}
