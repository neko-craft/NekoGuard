package cn.apisium.nekoguard.utils;

import org.bukkit.inventory.ItemStack;

public final class ContainerAction {
    public final ItemStack item;
    public final long time;
    public final String performer, source, target;
    public ContainerAction(final ItemStack item, final String performer, final String source, final String target, final long time) {
        this.performer = performer;
        this.item = item;
        this.source = source;
        this.target = target;
        this.time = time;
    }
}
