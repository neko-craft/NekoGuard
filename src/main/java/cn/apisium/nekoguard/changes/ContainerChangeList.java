package cn.apisium.nekoguard.changes;

import cn.apisium.nekoguard.Main;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import cn.apisium.nekoguard.utils.NMSUtils;
import cn.apisium.nekoguard.utils.Pair;
import cn.apisium.nekoguard.utils.Utils;
import cn.apisium.nekoguard.utils.XAndZ;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class ContainerChangeList extends ChangeList {
    private final HashMap<String, HashMap<XAndZ, ArrayList<Object[]>>> locationList = new HashMap<>();
    private final HashMap<String, ArrayList<Object[]>> entityList = new HashMap<>();
    private final HashMap<String, HashMap<XAndZ, ArrayList<Pair<Integer, String>>>> emptyLocationList = new HashMap<>();
    private final HashMap<String, ArrayList<String>> emptyEntityList = new HashMap<>();
    private int current;

    @SuppressWarnings("SuspiciousMethodCalls")
    public ContainerChangeList(SeriesMapper.Mapper mapper) {
        super(mapper);
        final List<World> worlds = Bukkit.getWorlds();
        final HashSet<String> set = new HashSet<>(worlds.size());
        worlds.forEach(it -> set.add(it.getName()));

        for (final Object[] arr : mapper.allArray()) {
            final String source = (String) arr[2];
            if (source == null || source.isEmpty()) {
                final String target = (String) arr[3];
                if (target == null || target.isEmpty()) continue;
                if (target.startsWith("#")) {
                    final String[] loc = target.substring(1).split("\\|", 4);
                    if (!set.contains(loc[0])) continue;
                    final int x = Integer.parseInt(loc[1]), z = Integer.parseInt(loc[3]), key2 = XAndZ.getKey(x, z);
                    final String key1 = Utils.getChunkKey(loc[0], x, z);
                    final HashMap<XAndZ, ArrayList<Pair<Integer, String>>> map =
                        emptyLocationList.computeIfAbsent(key1, it -> new HashMap<>());
                    ArrayList<Pair<Integer, String>> list = map.get(key2);
                    if (list == null) {
                        list = new ArrayList<>();
                        map.put(new XAndZ(x & 15, z & 15), list);
                    }
                    list.add(new Pair<>(Integer.parseInt(loc[2]), (String) arr[4]));
                } else emptyEntityList.computeIfAbsent(source, it -> new ArrayList<>()).add((String) arr[4]);
            } else if (source.startsWith("#")) {
                final String[] loc = source.substring(1).split("\\|", 4);
                if (!set.contains(loc[0])) {
                    failedCount++;
                    continue;
                }
                final int x = Integer.parseInt(loc[1]), z = Integer.parseInt(loc[3]), key2 = XAndZ.getKey(x, z);
                final String key1 = Utils.getChunkKey(loc[0], x, z);
                final HashMap<XAndZ, ArrayList<Object[]>> map = locationList.computeIfAbsent(key1, it -> new HashMap<>());
                ArrayList<Object[]> list = map.get(key2);
                if (list == null) {
                    list = new ArrayList<>();
                    map.put(new XAndZ(x & 15, z & 15), list);
                }
                list.add(arr);
                System.out.println(list.size());
            } else entityList.computeIfAbsent(source, it -> new ArrayList<>()).add(arr);
        }
    }

    @SuppressWarnings({ "ConstantConditions", "SuspiciousMethodCalls" })
    @Override
    public void doChange(@NotNull final CommandSender sender, @Nullable final Consumer<ChangeList> callback) {
        final LinkedHashMap<Block, ArrayList<Object[]>> map = new LinkedHashMap<>();
        locationList.forEach((k, v) -> {
            final String[] arr = k.split("\\|", 3);
            final Chunk ch = Bukkit.getWorld(arr[0]).getChunkAt(Integer.parseInt(arr[1]), Integer.parseInt(arr[2]));
            v.forEach((xz, obj) -> obj.forEach(it -> map.computeIfAbsent(ch.getBlock(xz.x, Integer.parseInt(((String) it[2])
                .split("\\|", 4)[2]), xz.z), a -> new ArrayList<>()).add(it)));
        });
        final Iterator<Map.Entry<Block, ArrayList<Object[]>>> iterator1 = map.entrySet().iterator();
        Main.getInstance().getServer().getScheduler().runTaskTimer(Main.getInstance(), it -> {
            int i = 0;
            final HashMap<String, Inventory> map1 = new HashMap<>();
            while (iterator1.hasNext() && i++ < 200) {
                final Map.Entry<Block, ArrayList<Object[]>> entry = iterator1.next();
                final BlockState tile = entry.getKey().getState();
                final Inventory inv0 = tile instanceof BlockInventoryHolder
                    ? ((BlockInventoryHolder) tile).getInventory() : null;
                entry.getValue().forEach(arr -> {
                    final ItemStack is = NMSUtils.deserializeItemStack((String) arr[4]);
                    if (is == null) {
                        failedCount++;
                        return;
                    }
                    if (inv0 != null) inv0.addItem(is);
                    Inventory inv;
                    if (map1.containsKey(arr[3])) inv = map1.get(arr[3]);
                    else {
                        inv = Utils.getInventory((String) arr[3]);
                        if (inv != null) map1.put((String) arr[3], inv);
                    }
                    if (inv != null) inv.remove(is);
                });
            }
            if (!iterator1.hasNext()) {
                it.cancel();
                if (callback != null && ++current == 4) callback.accept(this);
            }
        }, 0, 2);

        final Iterator<Map.Entry<String, ArrayList<Object[]>>> iterator2 = entityList.entrySet().iterator();
        Main.getInstance().getServer().getScheduler().runTaskTimer(Main.getInstance(), it -> {
            int i = 0;
            final HashMap<String, Inventory> map1 = new HashMap<>();
            while (iterator2.hasNext() && i++ < 200) {
                final Map.Entry<String, ArrayList<Object[]>> entry = iterator2.next();
                final Entity entity = Bukkit.getEntity(UUID.fromString(entry.getKey()));
                final Inventory inv0 = entity instanceof InventoryHolder
                    ? ((InventoryHolder) entity).getInventory() : null;
                entry.getValue().forEach(arr -> {
                    final ItemStack is = NMSUtils.deserializeItemStack((String) arr[4]);
                    if (is == null) {
                        failedCount++;
                        return;
                    }
                    if (inv0 != null) inv0.addItem(is);
                    Inventory inv;
                    if (map1.containsKey(arr[3])) inv = map1.get(arr[3]);
                    else {
                        inv = Utils.getInventory((String) arr[3]);
                        if (inv != null) map1.put((String) arr[3], inv);
                    }
                    if (inv != null) inv.remove(is);
                });
            }
            if (!iterator2.hasNext()) {
                it.cancel();
                if (callback != null && ++current == 4) callback.accept(this);
            }
        }, 0, 2);

        final LinkedHashMap<Block, ArrayList<String>> map5 = new LinkedHashMap<>();
        emptyLocationList.forEach((k, v) -> {
            final String[] arr = k.split("\\|", 3);
            final Chunk ch = Bukkit.getWorld(arr[0]).getChunkAt(Integer.parseInt(arr[1]), Integer.parseInt(arr[2]));
            v.forEach((xz, obj) -> obj.forEach(it -> map5.computeIfAbsent(ch.getBlock(xz.x, it.key,
                xz.z), a -> new ArrayList<>()).add(it.value)));
        });
        final Iterator<Map.Entry<Block, ArrayList<String>>> iterator3 = map5.entrySet().iterator();
        Main.getInstance().getServer().getScheduler().runTaskTimer(Main.getInstance(), it -> {
            int i = 0;
            while (iterator3.hasNext() && i++ < 200) {
                final Map.Entry<Block, ArrayList<String>> entry = iterator3.next();
                final BlockState state = entry.getKey().getState();
                if (!(state instanceof InventoryHolder)) continue;
                final Inventory inv = ((InventoryHolder) state).getInventory();
                entry.getValue().forEach(data -> {
                    final ItemStack is = NMSUtils.deserializeItemStack(data);
                    if (is == null) failedCount++;
                    else inv.remove(is);
                });
            }
            if (!iterator3.hasNext()) {
                it.cancel();
                if (callback != null && ++current == 4) callback.accept(this);
            }
        }, 0, 2);

        final Iterator<Map.Entry<String, ArrayList<String>>> iterator4 = emptyEntityList.entrySet().iterator();
        Main.getInstance().getServer().getScheduler().runTaskTimer(Main.getInstance(), it -> {
            int i = 0;
            while (iterator4.hasNext() && i++ < 200) {
                final Map.Entry<String, ArrayList<String>> entry = iterator4.next();
                final Entity entity = Bukkit.getEntity(UUID.fromString(entry.getKey()));
                if (!(entity instanceof InventoryHolder)) continue;
                final Inventory inv = ((InventoryHolder) entity).getInventory();
                entry.getValue().forEach(data -> {
                    final ItemStack is = NMSUtils.deserializeItemStack(data);
                    if (is == null) failedCount++;
                    else inv.remove(is);
                });
            }
            if (!iterator4.hasNext()) {
                it.cancel();
                if (callback != null && ++current == 4) callback.accept(this);
            }
        }, 0, 2);
    }

    @Override
    public void undo(@NotNull final CommandSender sender, @Nullable final Consumer<ChangeList> callback) {

    }
}
