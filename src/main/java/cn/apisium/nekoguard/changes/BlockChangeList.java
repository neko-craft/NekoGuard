package cn.apisium.nekoguard.changes;

import cn.apisium.nekoguard.Main;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import cn.apisium.nekoguard.utils.HashCodeMap;
import cn.apisium.nekoguard.utils.KeyedChunk;
import cn.apisium.nekoguard.utils.Utils;
import cn.apisium.nekoguard.utils.Vector;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

public final class BlockChangeList extends ChangeList {
    public BlockChangeList(SeriesMapper.Mapper mapper) {
        super(mapper, 0);
    }

    @Override
    public void doChange(@NotNull final CommandSender sender, @Nullable final Consumer<ChangeList> callback) {
        final HashCodeMap<KeyedChunk, HashCodeMap<Vector, Object[]>> map = new HashCodeMap<>();
        for (final Object[] arr : mapper.allArray()) {
            final String world = (String) arr[4];
            final int x = ((Double) arr[5]).intValue(), z = ((Double) arr[7]).intValue();
            HashCodeMap<Vector, Object[]> m = map.getByHash(KeyedChunk.getKey(world, x >> 4, z >> 4));
            if (m == null) {
                m = new HashCodeMap<>();
                map.put(new KeyedChunk(world, x, z), m);
            }
            final int x2 = x & 15, z2 = z & 15, y = ((Double) arr[6]).intValue();
            if (!m.containsHash(Vector.getKey(x2, y, z2))) {
                m.put(new Vector(x2, y, z2), arr);
                allCount++;
            }
        }
        final Iterator<Map.Entry<KeyedChunk, HashCodeMap<Vector, Object[]>>> iterator = map.entrySet().iterator();
        Main.getInstance().getServer().getScheduler().runTaskTimer(Main.getInstance(), it -> {
            int i = 0;
            while (iterator.hasNext() && i++ < 50000) {
                final Map.Entry<KeyedChunk, HashCodeMap<Vector, Object[]>> entry = iterator.next();
                final KeyedChunk c = entry.getKey();
                final World world = Bukkit.getWorld(c.world);
                if (world == null) {
                    failedCount++;
                    i--;
                    continue;
                }
                final Chunk chunk = world.getChunkAt(c.x, c.z);
                entry.getValue().forEach((k, v) -> {
                    final Block block = chunk.getBlock(k.x, k.y, k.z);
                    if (v[1].equals("0")) Utils.patchDataToBlock(block, (String) v[0]);
                    else block.setType(Material.AIR);
                });
                i += entry.getValue().size();
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
