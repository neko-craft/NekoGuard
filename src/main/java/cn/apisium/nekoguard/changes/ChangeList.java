package cn.apisium.nekoguard.changes;

import cn.apisium.nekoguard.mappers.SeriesMapper;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public abstract class ChangeList {
    int failedCount;
    final int allCount;
    int successCount;
    ChangeList(final SeriesMapper.Mapper mapper) {
        allCount = mapper.count;
    }

    public abstract void doChange(@NotNull final CommandSender sender, @Nullable final Consumer<ChangeList> callback);
    @SuppressWarnings("unused")
    public abstract void undo(@NotNull final CommandSender sender, @Nullable final Consumer<ChangeList> callback);

    public int getFailed() { return failedCount; }
    public int getAllCount() { return allCount; }
    public int getSuccessCount() { return successCount; }
}
