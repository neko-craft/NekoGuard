package cn.apisium.nekoguard.changes;

import cn.apisium.nekoguard.mappers.SeriesMapper;
import cn.apisium.nekoguard.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public abstract class ChangeWithPositionList extends ChangeList {
    final HashMap<String, ArrayList<Object[]>> actionList = new HashMap<>();
    static int x, z, world;

    ChangeWithPositionList(SeriesMapper.Mapper mapper) {
        super(mapper);
        final List<World> worlds = Bukkit.getWorlds();
        final HashSet<String> set = new HashSet<>(worlds.size());
        worlds.forEach(it -> set.add(it.getName()));

        for (final Object[] arr : mapper.allArray()) {
            final String name = (String) arr[world];
            if (set.contains(name)) addAction(arr, name, ((Double) arr[x]).intValue(), ((Double) arr[z]).intValue());
            else failedCount++;
        }
    }

    void addAction(final Object[] action, final String world, final int x, final int z) {
        actionList.computeIfAbsent(Utils.getChunkKey(world, x, z), $ -> new ArrayList<>())
            .add(action);
    }
}
