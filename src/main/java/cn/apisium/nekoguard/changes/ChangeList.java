package cn.apisium.nekoguard.changes;

import cn.apisium.nekoguard.mappers.SeriesMapper;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public abstract class ChangeList {
    int failedCount;
    int allCount;
    int successCount;
    final SeriesMapper.Mapper mapper;
    ChangeList(final SeriesMapper.Mapper mapper) {
        this.mapper = mapper;
        allCount = mapper.count;
    }
    ChangeList(final SeriesMapper.Mapper mapper, final int count) {
        this.mapper = mapper;
        allCount = count;
    }

    public abstract void doChange(@NotNull final CommandSender sender, @Nullable final Consumer<ChangeList> callback);
    @SuppressWarnings("unused")
    public abstract void undo(@NotNull final CommandSender sender, @Nullable final Consumer<ChangeList> callback);

    public int getFailed() { return failedCount; }
    public int getAllCount() { return allCount; }
    public int getSuccessCount() { return successCount; }
}
