package cn.apisium.nekoguard.changes;

import cn.apisium.nekoguard.Main;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import cn.apisium.nekoguard.utils.NMSUtils;
import cn.apisium.nekoguard.utils.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class ContainerChangeList extends ChangeList {

    public ContainerChangeList(final SeriesMapper.Mapper mapper) {
        super(mapper);
    }

    @Override
    public void doChange(@NotNull final CommandSender sender, @Nullable final Consumer<ChangeList> callback) {
        Iterator<Object[]> iterator = mapper.all().iterator();
        Main.getInstance().getServer().getScheduler().runTaskTimer(Main.getInstance(), it -> {
            int i = 0;
            while (iterator.hasNext() && i++ < 10000) {
                final Object[] arr = iterator.next();
                final Inventory source = Utils.getInventory((String) arr[2], (String) arr[3], (Double) arr[4], (Double) arr[5], (Double) arr[6]),
                     target = Utils.getInventory((String) arr[7], (String) arr[8], (Double) arr[9], (Double) arr[10], (Double) arr[11]);
                if (source == null && target != null) continue;
                final ItemStack is = NMSUtils.deserializeItemStack((String) arr[1]);
                if (is == null) continue;
                if (source != null) source.addItem(is);
                if (target != null) {
                    final ItemStack[] array = target.getContents();
                    int count = is.getAmount(), j = array.length;
                    while (j-- > 0 && count > 0) {
                        final ItemStack item = array[j];
                        if (is.isSimilar(item)) {
                            final int c = item.getAmount();
                            if (count >= c) {
                                target.setItem(j, null);
                                count -= c;
                            } else {
                                item.setAmount(c - count);
                                target.setItem(j, item);
                                count = 0;
                            }
                        }
                    }
                }
            }
            if (!iterator.hasNext()) {
                it.cancel();
                if (callback != null) callback.accept(this);
            }
        }, 0, 2);
    }

    @Override
    public void undo(@NotNull final CommandSender sender, @Nullable final Consumer<ChangeList> callback) {

    }
}
