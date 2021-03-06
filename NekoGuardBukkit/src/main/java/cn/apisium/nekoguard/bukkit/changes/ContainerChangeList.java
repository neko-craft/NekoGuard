package cn.apisium.nekoguard.bukkit.changes;

import cn.apisium.nekoguard.ChangeList;
import cn.apisium.nekoguard.bukkit.Main;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import cn.apisium.nekoguard.bukkit.utils.*;
import cn.apisium.nekoguard.utils.Pair;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class ContainerChangeList extends cn.apisium.nekoguard.ChangeList {
    private final ArrayList<Pair<Inventory, ItemStack>> added = new ArrayList<>();
    private final ArrayList<Pair<Inventory, ItemStack>> removed = new ArrayList<>();
    public ContainerChangeList(final SeriesMapper.Mapper mapper) {
        super(mapper);
    }

    @Override
    public void doChange(@Nullable final Consumer<cn.apisium.nekoguard.ChangeList> callback) {
        Iterator<Object[]> iterator = mapper.all().iterator();
        Bukkit.getScheduler().runTaskTimer(Main.getPlugin(), it -> {
            int i = 0;
            while (iterator.hasNext() && i++ < 10000) {
                final Object[] arr = iterator.next();
                final Inventory source = Utils.getInventory((String) arr[2], (String) arr[3], (Double) arr[4], (Double) arr[5], (Double) arr[6]),
                     target = Utils.getInventory((String) arr[7], (String) arr[8], (Double) arr[9], (Double) arr[10], (Double) arr[11]);
                if (source == null && target == null) {
                    failedCount++;
                    continue;
                }
                final ItemStack is = NMSUtils.deserializeItemStack((String) arr[1]);
                if (is == null) {
                    failedCount++;
                    continue;
                }
                if (source != null) {
                    if (arr[7] != null && !(target instanceof OfflinePlayer) && Utils.hasDifferentSlot(source.getType()))
                        NMSUtils.addItemToBottom(source, is);
                    else source.addItem(is);
                    added.add(new Pair<>(source, is));
                }
                if (target != null) remove(target, is, true);
                successCount++;
            }
            if (!iterator.hasNext()) {
                it.cancel();
                if (callback != null) callback.accept(this);
            }
        }, 0, 2);
    }

    private void remove(final @NotNull Inventory target, final @NotNull ItemStack is, final boolean record) {
        final ItemStack[] array = target.getContents();
        int count = is.getAmount(), j = array.length;
        while (j-- > 0 && count > 0) {
            final ItemStack item = array[j];
            if (is.isSimilar(item)) {
                final int c = item.getAmount();
                if (count >= c) {
                    target.setItem(j, null);
                    count -= c;
                    if (record) removed.add(new Pair<>(target, item));
                } else {
                    item.setAmount(c - count);
                    target.setItem(j, item);
                    if (record) {
                        final ItemStack copy = item.clone();
                        copy.setAmount(count);
                        removed.add(new Pair<>(target, copy));
                    }
                    count = 0;
                }
            }
        }
    }

    @Override
    public void undo(@Nullable final Consumer<ChangeList> callback) {
        final Iterator<Pair<Inventory, ItemStack>> iterator0 = added.iterator();
        Bukkit.getScheduler().runTaskTimer(Main.getPlugin(), it -> {
            int i = 0;
            while (iterator0.hasNext() && i++ < 10000) {
                final Pair<Inventory, ItemStack> pair = iterator0.next();
                remove(pair.left, pair.right, false);
            }
            if (!iterator0.hasNext()) {
                it.cancel();
                if (callback != null) callback.accept(this);
            }
        }, 0, 2);
        final Iterator<Pair<Inventory, ItemStack>> iterator1 = removed.iterator();
        Bukkit.getScheduler().runTaskTimer(Main.getPlugin(), it -> {
            int i = 0;
            while (iterator1.hasNext() && i++ < 10000) {
                final Pair<Inventory, ItemStack> pair = iterator1.next();
                pair.left.addItem(pair.right);
            }
            if (!iterator1.hasNext()) {
                it.cancel();
                if (callback != null) callback.accept(this);
            }
        }, 0, 2);
    }

    @Override
    public @NotNull String getName() {
        return "ContainerChange";
    }
}
