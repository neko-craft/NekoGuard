package cn.apisium.nekoguard;

import cn.apisium.nekocommander.ProxiedCommandSender;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import net.md_5.bungee.api.chat.TextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PlatformImpl {
    @Nullable
    String getPlayerUUIDByName(@Nullable final String name, @NotNull final ProxiedCommandSender sender);

    @NotNull
    String getPerformerQueryName(@NotNull final String performer, @NotNull final ProxiedCommandSender sender);

    void fetchItemIntoInventory(@NotNull final ProxiedCommandSender player, @Nullable final String item);

    @NotNull
    TextComponent getEntityPerformerComponent(@NotNull final String entity, final boolean pad);

    @NotNull
    TextComponent getBlockPerformerComponent(@NotNull final String world, final int x, final int y, final int z);

    @NotNull
    String getItemName(@NotNull final String name);

    @NotNull
    String getPlayerName(@NotNull final String player);

    @NotNull
    ChangeList createBlockChangeList(@NotNull final SeriesMapper.Mapper mapper);

    @NotNull
    ChangeList createContainerChangeList(@NotNull final SeriesMapper.Mapper mapper);

    @NotNull
    ChangeList createEntityChangeList(@NotNull final SeriesMapper.Mapper mapper);
}
