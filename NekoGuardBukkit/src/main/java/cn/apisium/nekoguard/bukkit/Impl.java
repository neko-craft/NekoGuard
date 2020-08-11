package cn.apisium.nekoguard.bukkit;

import cn.apisium.nekocommander.ProxiedCommandSender;
import cn.apisium.nekoguard.ChangeList;
import cn.apisium.nekoguard.Constants;
import cn.apisium.nekoguard.PlatformImpl;
import cn.apisium.nekoguard.bukkit.changes.*;
import cn.apisium.nekoguard.bukkit.utils.NMSUtils;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import cn.apisium.nekoguard.utils.Utils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
import org.apache.commons.lang.ObjectUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class Impl implements PlatformImpl {
    private final static HashSet<String> ITEMS = new HashSet<>();
    static {
        for (final Material type : Material.values()) if (!type.isBlock()) ITEMS.add(type.getKey().toString());
    }
    @Override
    @Nullable
    public String getPlayerUUIDByName(@Nullable String name, @NotNull ProxiedCommandSender sender) {
        if (name == null || name.length() == 36) return name;
        final OfflinePlayer p = Bukkit.getOfflinePlayer(name);
        if (p.hasPlayedBefore()) return p.getUniqueId().toString();
        sender.sendMessage(Constants.PLAYER_NOT_EXISTS);
        return null;
    }

    @Override
    @NotNull
    public String getPerformerQueryName(@NotNull final String performer, @NotNull final ProxiedCommandSender sender) {
        if (!performer.startsWith("#") && !performer.startsWith("@") && performer.length() != 36) {
            final OfflinePlayer p = Bukkit.getOfflinePlayer(performer);
            if (!p.hasPlayedBefore()) {
                sender.sendMessage(Constants.PLAYER_NOT_EXISTS);
                throw Constants.IGNORED_ERROR;
            }
            return p.getUniqueId().toString();
        } else return performer;
    }

    @Override
    public void fetchItemIntoInventory(final @NotNull ProxiedCommandSender player, final @Nullable String item) {
        if (item == null || !(player.origin instanceof InventoryHolder)) return;
        final ItemStack is = NMSUtils.deserializeItemStack(item);
        if (is != null) ((InventoryHolder) player.origin).getInventory().addItem(is);
    }

    @Override
    public @NotNull TextComponent getEntityPerformerComponent(@NotNull String entity, boolean pad) {
        final Entity e = Bukkit.getEntity(UUID.fromString(entity));
        if (e == null) return Constants.UNKNOWN;
        if (e instanceof OfflinePlayer) return Utils.getPlayerPerformerNameComponent(entity, pad);
        final TextComponent t = new TextComponent("实体:");
        t.setColor(ChatColor.GRAY);
        final TranslatableComponent t2 = new TranslatableComponent(Utils.getEntityName(e.getType().getKey().toString()));
        t2.setColor(ChatColor.WHITE);
        t2.setHoverEvent(Utils.genTextHoverEvent(Constants.TP_MESSAGE +entity));
        t2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + entity));
        t.addExtra(t2);
        return t;
    }

    @Override
    public @NotNull TextComponent getBlockPerformerComponent(@NotNull String world, int x, int y, int z) {
        final World w = Bukkit.getWorld(world);
        if (w == null) return Utils.getUnknownBlockPerformerComponent(world, x, y, z);
        final TextComponent t = new TextComponent("方块:");
        t.setColor(ChatColor.GRAY);
        final TranslatableComponent t2 = new TranslatableComponent(cn.apisium.nekoguard.bukkit.utils.Utils.getBlockName(w.getBlockAt(x, y, z).getType()));
        t2.setColor(ChatColor.WHITE);
        t.addExtra(t2);
        Utils.processActionComponent(t, world, x, y, z);
        return t;
    }

    @Override
    public @NotNull String getItemName(@NotNull String name) {
        return (ITEMS.contains(name) ? "item." : "block.") + name.replace(':', '.');
    }

    @Override
    public @NotNull String getPlayerName(@NotNull String player) {
        return (String) ObjectUtils.defaultIfNull(Bukkit.getOfflinePlayer(UUID.fromString(player)).getName(), "未知玩家");
    }

    @Override
    public @NotNull ChangeList createBlockChangeList(final @NotNull SeriesMapper.Mapper mapper) {
        return new BlockChangeList(mapper);
    }

    @Override
    public @NotNull ChangeList createContainerChangeList(final @NotNull SeriesMapper.Mapper mapper) {
        return new ContainerChangeList(mapper);
    }

    @Override
    public @NotNull ChangeList createEntityChangeList(final @NotNull SeriesMapper.Mapper mapper) {
        return new EntityChangeList(mapper);
    }
}
