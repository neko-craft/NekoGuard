package cn.apisium.nekoguard.changes;

import cn.apisium.nekoguard.Main;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import cn.apisium.nekoguard.utils.Pair;
import cn.apisium.nekoguard.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class BlockChangeList extends ChangeList {
    public BlockChangeList(SeriesMapper.Mapper mapper) {
        for (final Object[] arr : mapper.allArray())
            addAction(arr, (String) arr[4], ((Double) arr[5]).intValue(), ((Double) arr[7]).intValue());
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public void doChange(@NotNull final CommandSender sender, @Nullable final Runnable callback) {
        final HashMap<Block, Pair<String, Boolean>> map = new HashMap<>();
        actionList.forEach((k, v) -> {
            final String[] arr = k.split("\\|", 3);
            final Chunk ch = Bukkit.getWorld(arr[0]).getChunkAt(Integer.parseInt(arr[1]), Integer.parseInt(arr[2]));
            for (final Object[] it : v) {
                final Block b = ch.getBlock(((Double) it[5]).intValue() & 15,
                    ((Double) it[6]).intValue(), ((Double) it[7]).intValue() & 15);
                if (!map.containsKey(b)) map.put(b, new Pair<>((String) it[0], it[1].equals("0")));
            }
        });
        sender.sendMessage("Count: " + map.size());
        final Iterator<Map.Entry<Block, Pair<String, Boolean>>> iterator = map.entrySet().iterator();
        Main.getInstance().getServer().getScheduler().runTaskTimer(Main.getInstance(), it -> {
            int i = 0;
            while (iterator.hasNext() && i++ < 50000) {
                final Map.Entry<Block, Pair<String, Boolean>> entry = iterator.next();
                if (entry.getValue().value) Utils.patchDataToBlock(entry.getKey(), entry.getValue().key);
                else entry.getKey().setType(Material.AIR);
            }
            if (!iterator.hasNext()) {
                it.cancel();
                if (callback != null) callback.run();
            }
        }, 0, 2);
    }

    @Override
    public void undo() {

    }
}
