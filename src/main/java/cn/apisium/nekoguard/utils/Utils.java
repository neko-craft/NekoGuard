package cn.apisium.nekoguard.utils;

import cn.apisium.nekoguard.Constants;
import com.google.common.base.Strings;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.influxdb.dto.QueryResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

public final class Utils {
    private final static SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Utils() {}

    static {
        FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @NotNull
    public static String getMaterialName(@NotNull final Material material) {
        return (material.isBlock() ? "block." : "item.") + material.getKey().toString().replace(':', '.');
    }
    @NotNull
    public static String getMaterialName(@NotNull final String data) {
        return "block." + getMaterialId(data).replace(':', '.');
    }
    @NotNull
    public static String getMaterialId(@NotNull final String data) {
        return data.split("\\[", 2)[0].split(Constants.TILE, 2)[0];
    }

    @NotNull
    public static String getEntityName(@NotNull final String data) {
        return "entity." + data.split("\\[", 2)[0].replace(':', '.');
    }

    @Nullable
    public static List<QueryResult.Result> getResult(@NotNull final QueryResult res) {
        if (res.hasError()) {
            Bukkit.getLogger().warning(res.getError());
            return null;
        }
        return res.getResults();
    }

    @Nullable
    public static QueryResult.Series getFirstResult(@NotNull final QueryResult res) {
        final List<QueryResult.Result> list = getResult(res);
        if (list == null || list.isEmpty()) return null;
        final QueryResult.Result ret = list.get(0);
        if (ret.hasError()) {
            Bukkit.getLogger().warning(ret.getError());
            return null;
        }
        return ret.getSeries() == null || ret.getSeries().isEmpty() ? null : ret.getSeries().get(0);
    }

    @NotNull
    public static String padPlayerName(@NotNull final String name) {
        return Strings.padEnd(name, 16, ' ');
    }

    @NotNull
    public static TextComponent getPlayerPerformerNameComponent(@NotNull final String performer, final boolean pad) {
        final String name = getPlayerName(performer);
        final TextComponent t = new TextComponent(pad ? padPlayerName(name) : name);
        t.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, name));
        t.setHoverEvent(genTextHoverEvent(performer));
        return t;
    }

    @NotNull
    public static BaseComponent getPerformerName(@NotNull final String performer) {
        final String s1, s2;
        if (performer.startsWith("#")) {
            s1 = "ʵ��:";
            s2 = getEntityName(performer.substring(1));
        } else if (performer.startsWith("%")) {
            s1 = "����:";
            s2 = getMaterialName(performer.substring(1));
        } else return getPlayerPerformerNameComponent(performer, true);
        final TextComponent t = new TextComponent(s1);
        t.setColor(ChatColor.GRAY);
        final TranslatableComponent t1 = new TranslatableComponent(Strings.padEnd(s2, 16, ' '));
        t1.setColor(ChatColor.WHITE);
        t.addExtra(t1);
        return t;
    }

    @NotNull
    public static String formatDuration(final long time) {
        final String str = DurationFormatUtils.formatDuration(time, "d��Hʱm��s��ǰ");
        return str
            .replace("0��", "")
            .replace("0ʱ", "");
    }

    public static long getCurrentTime() {
        final Instant inst = Instant.now();
        return inst.toEpochMilli() * 1000000L + inst.getNano();
    }

    @NotNull
    public static String getFullBlockData(@NotNull final BlockState block) {
        String str = block.getBlockData().getAsString();
        if (block instanceof TileState) {
            final String s = NMSUtils.serializeTileEntity(block);
            if (s != null) {
                str += Constants.TILE;
                str += s;
            }
        }
        return str;
    }

    public static void patchDataToBlock(@NotNull final Block block, @NotNull final String data) {
        final String[] arr = data.split(Constants.TILE, 2);
        block.setBlockData(Bukkit.createBlockData(arr[0]));
        if (arr.length == 2) NMSUtils.patchTileStateData(block, arr[1]);
    }

    @NotNull
    public static String getInventoryId(@NotNull final Inventory inventory) {
        final InventoryHolder holder = inventory.getHolder();
        if (holder == null) return "";
        if (holder instanceof Entity) return ((Entity) holder).getUniqueId().toString();
        if (holder instanceof Container) {
            final Container b = (Container) holder;
            return "#" + b.getWorld().getName() + "|"  + b.getX() + "|" + b.getY() + "|" + b.getZ();
        } else return "";
    }

    @NotNull
    public static String getBlockPerformer(@NotNull final Block block) {
        return getBlockPerformer(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }
    @NotNull
    public static String getBlockPerformer(@NotNull final String world, final int x, final int y, final int z) {
        return "#" + world + "|" + x + "|" + y + "|" + z;
    }

    @NotNull
    public static TextComponent getContainerPerformerName(@NotNull final String str) {
        if (str.startsWith("#")) {
            final TextComponent t = new TextComponent("�����:"), t1 = new TextComponent(Strings.padEnd(str.substring(1), 9, ' '));
            t.setColor(ChatColor.GRAY);
            t1.setColor(ChatColor.WHITE);
            t.addExtra(t1);
            return t;
        } else return getPlayerPerformerNameComponent(str, true);
    }

    @SuppressWarnings("deprecation")
    @NotNull
    private static HoverEvent genTextHoverEvent(@NotNull final String text) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] { new TextComponent(text) });
    }
    @SuppressWarnings("deprecation")
    @NotNull
    private static HoverEvent genItemHoverEvent(@NotNull final String text) {
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new TextComponent[] { new TextComponent(text) });
    }

    @NotNull
    public static BaseComponent getItemStackDetails(@NotNull final ItemStack is) {
        final ItemMeta im = is.getItemMeta();
        final BaseComponent t;
        if (im.hasDisplayName()) t = new TextComponent("[" + im.getDisplayName() + "]");
        else {
            t = new TextComponent("[");
            t.addExtra(new TranslatableComponent(getMaterialName(is.getType())));
            t.addExtra("]");
        }
        if (is.getAmount() > 1) {
            final TextComponent t1 = new TextComponent("x" + is.getAmount());
            t1.setColor(ChatColor.GRAY);
            t.addExtra(t1);
        }
        t.setHoverEvent(genItemHoverEvent(NMSUtils.serializeItemStack(is)));
        t.setColor(ChatColor.AQUA);
        return t;
    }

    @NotNull
    public static String getPlayerName(@NotNull final String player) {
        return (String) ObjectUtils.defaultIfNull(Bukkit.getOfflinePlayer(UUID.fromString(player)).getName(), "δ֪");
    }

    @NotNull
    public static TranslatableComponent getBlockComponent(@NotNull final String name) {
        final String id = getMaterialId(name);
        final TranslatableComponent t = new TranslatableComponent(Utils.getMaterialName(id));
        t.setColor(ChatColor.YELLOW);
        t.setHoverEvent(genItemHoverEvent("{id:\"" + id + "\",Count:1b}"));
        return t;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void throwSneaky(@NotNull Throwable exception) throws T {
        throw (T) exception;
    }

    @NotNull
    public static TextComponent formatTime(@NotNull final String time, final long now) {
        final Instant instant = Instant.parse(time);
        final TextComponent t = new TextComponent("   " + Strings.padEnd(
            formatDuration(now - instant.toEpochMilli()), 13, ' ') + "  ");
        t.setColor(ChatColor.GRAY);
        t.setItalic(true);
        t.setHoverEvent(genTextHoverEvent(FORMATTER.format(Date.from(instant))));
        return t;
    }

    @NotNull
    public static TextComponent getPlayerCommandNameComponent(@NotNull final String type, @NotNull final String performer) {
        if (type.length() == 36) return getPlayerPerformerNameComponent(type, false);
        final TextComponent t = new TextComponent("#" + type);
        if (CommandSenderType.BLOCK.name().equals(type)) {
            t.setHoverEvent(genTextHoverEvent(performer));
            t.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                "/tp " + performerToLocation(performer)[1]));
        } else if (CommandSenderType.ENTITY.name().equals(type)) {
            t.setHoverEvent(genTextHoverEvent(performer));
            t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + performer));
        }
        return t;
    }

    @NotNull
    public static TextComponent getBlockActionComponent(final boolean isAdd, @NotNull final String world, final int x, final int y, final int z) {
        final TextComponent t = new TextComponent(isAdd ? " + " : " - ");
        t.setColor(isAdd ? ChatColor.GREEN : ChatColor.RED);
        final String loc = " " + x + " " + y + " " + z;
        t.setHoverEvent(genTextHoverEvent(world + loc));
        t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp" + loc));
        return t;
    }
    @NotNull
    public static String[] performerToLocation(@NotNull final String name) {
        if (!name.startsWith("#")) return new String[] { "", name };
        final String[] arr = name.split("\\|", 2);
        final String str = arr[0].substring(1);
        return arr.length == 2
            ? new String[] { str, arr[1].replace('|', ' ') }
            : new String[] { "", str.replace('|', ' ') };
    }
    @NotNull
    public static TextComponent getContainerActionComponent(final boolean isAdd, @NotNull final String performer, @NotNull final String source, @NotNull final String target) {
        final TextComponent t = new TextComponent(isAdd ? " + " : " - ");
        t.setColor(isAdd ? ChatColor.GREEN : ChatColor.RED);
        final String p;
        if (performer.equals(source)) p = target;
        else if (performer.equals(target)) p = source;
        else p = source.isEmpty() ? target : source;
        final String[] arr = performerToLocation(p);
        t.setHoverEvent(genTextHoverEvent(arr[0] + " " + arr[1]));
        t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + arr[1]));
        return t;
    }

    @NotNull
    public static String getChunkKey(@NotNull final String world, final int x, final int z) {
        return world + "|" + (x >> 4) + "|" + (z >> 4);
    }
}
