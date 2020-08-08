package cn.apisium.nekoguard;

import cn.apisium.nekoguard.mappers.SeriesMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public abstract class ChangeList {
    protected int failedCount;
    protected int allCount;
    protected int successCount;
    protected final SeriesMapper.Mapper mapper;
    protected ChangeList(final SeriesMapper.Mapper mapper) {
        this.mapper = mapper;
        allCount = mapper.count;
    }
    protected ChangeList(final SeriesMapper.Mapper mapper, final int count) {
        this.mapper = mapper;
        allCount = count;
    }

    public abstract void doChange(@Nullable final Consumer<ChangeList> callback);
    @SuppressWarnings("unused")
    public abstract void undo(@Nullable final Consumer<ChangeList> callback);

    public int getFailed() { return failedCount; }
    public int getAllCount() { return allCount; }
    public int getSuccessCount() { return successCount; }

    @NotNull
    public abstract String getName();
}
