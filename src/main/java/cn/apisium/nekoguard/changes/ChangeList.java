package cn.apisium.nekoguard.changes;

import cn.apisium.nekoguard.utils.Utils;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class ChangeList {
    final HashMap<String, ArrayList<Object[]>> actionList = new HashMap<>();

    public abstract void doChange(@NotNull final CommandSender sender, @Nullable final Runnable callback);
    @SuppressWarnings("unused")
    public abstract void undo();

    void addAction(final Object[] action, final String world, final int x, final int z) {
        actionList.computeIfAbsent(Utils.getChunkKey(world, x, z), $ -> new ArrayList<>())
            .add(action);
    }
}
