package cn.apisium.nekoguard.utils;

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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

public final class Utils {
    private final static SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private final static Class<?> nbtTagCompoundClass = ReflectionUtil.getNMSClass("NBTTagCompound");
    private final static Class<?> tileEntityClass = ReflectionUtil.getNMSClass("TileEntity");
    private static final Class<?> nbtParserClass = ReflectionUtil.getNMSClass("MojangsonParser");
    private static final Class<?> nmsItemStackClass = ReflectionUtil.getNMSClass("ItemStack");
    private final static Class<?> craftBlockEntityStateClass = ReflectionUtil.getOBCClass("block.CraftBlockEntityState");
    private static final Class<?> craftItemStackClass = ReflectionUtil.getOBCClass("inventory.CraftItemStack");
    private final static Method getTileEntity = ReflectionUtil.getMethod(craftBlockEntityStateClass, "getTileEntity");
    private final static Method tileEntitySave = ReflectionUtil.getMethod(tileEntityClass, "save", nbtTagCompoundClass);
    private static final Method saveNmsItemStack = ReflectionUtil.getMethod(nmsItemStackClass, "save", nbtTagCompoundClass);
    private static final Method asNMSCopyMethod = ReflectionUtil.getMethod(craftItemStackClass, "asNMSCopy", ItemStack.class);
    private static final Method nbtParserParse = ReflectionUtil.getMethod(nbtParserClass, "parse", String.class);
    private static final Method itemStackFromCompound = ReflectionUtil.getMethod(nmsItemStackClass, "fromCompound", nbtTagCompoundClass);
    private static final Method itemStackAsCraftMirror = ReflectionUtil.getMethod(craftItemStackClass, "asCraftMirror", nmsItemStackClass);
    private static final Field craftItemStackHandleField = ReflectionUtil.getField(craftItemStackClass, "handle", true);
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
        return data.split("\\[", 2)[0].split("\\|\\$TILE\\$\\|", 2)[0];
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
            s1 = "实体:";
            s2 = getEntityName(performer.substring(1));
        } else if (performer.startsWith("%")) {
            s1 = "方块:";
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
        final String str = DurationFormatUtils.formatDuration(time, "d天H时m分s秒前");
        return str
            .replace("0天", "")
            .replace("0时", "");
    }

    public static long getCurrentTime() {
        final Instant inst = Instant.now();
        return inst.toEpochMilli() * 1000000L + inst.getNano();
    }

    @SuppressWarnings("ConstantConditions")
    @Nullable
    public static String serializeTileEntity(@NotNull final BlockState s) {
        if (!craftBlockEntityStateClass.isInstance(s)) return null;
        try {
            return tileEntitySave.invoke(getTileEntity.invoke(s), nbtTagCompoundClass.newInstance()).toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @NotNull
    public static String getFullBlockData(@NotNull final Block block) {
        String str = block.getBlockData().getAsString();
        final BlockState bs = block.getState();
        if (bs instanceof TileState) {
            final String s = Utils.serializeTileEntity(bs);
            if (s != null) {
                str += "|$TILE$|";
                str += s;
            }
        }
        return str;
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
            final TextComponent t = new TextComponent("非玩家:"), t1 = new TextComponent(Strings.padEnd(str.substring(1), 9, ' '));
            t.setColor(ChatColor.GRAY);
            t1.setColor(ChatColor.WHITE);
            t.addExtra(t1);
            return t;
        } else return getPlayerPerformerNameComponent(str, true);
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    public static String serializeItemStack(@NotNull final ItemStack itemStack) {
        try {
            return saveNmsItemStack.invoke(getNMSItemStack(itemStack), nbtTagCompoundClass.newInstance()).toString();
        } catch (Exception t) {
            throwSneaky(t);
            throw new RuntimeException();
        }
    }
    @NotNull
    public static ItemStack deserializeItemStack(@NotNull final String data) {
        try {
            return (ItemStack) itemStackAsCraftMirror.invoke(null,
                itemStackFromCompound.invoke(null, nbtParserParse.invoke(null, data)));
        } catch (Exception t) {
            throwSneaky(t);
            throw new RuntimeException();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    private static Object getNMSItemStack(@NotNull final ItemStack itemStack) throws Exception {
        Object nms = null;
        if (craftItemStackClass.isInstance(itemStack)) try {
            nms = craftItemStackHandleField.get(itemStack);
        } catch (Exception ignored) { }
        return nms == null ? asNMSCopyMethod.invoke(null, itemStack) : nms;
    }

    @SuppressWarnings("deprecation")
    private static HoverEvent genTextHoverEvent(@NotNull final String text) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] { new TextComponent(text) });
    }
    @SuppressWarnings("deprecation")
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
        t.setHoverEvent(genItemHoverEvent(serializeItemStack(is)));
        t.setColor(ChatColor.AQUA);
        return t;
    }

    @NotNull
    public static String getPlayerName(@NotNull final String player) {
        return (String) ObjectUtils.defaultIfNull(Bukkit.getOfflinePlayer(UUID.fromString(player)).getName(), "未知");
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
    private static <T extends Throwable> void throwSneaky(@NotNull Throwable exception) throws T {
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

    public static TextComponent getPlayerCommandNameComponent(@NotNull final String type, @NotNull final String performer) {
        if (type.length() == 36) return getPlayerPerformerNameComponent(type, false);
        final TextComponent t = new TextComponent("#" + type);
        if (CommandSenderType.BLOCK.name().equals(type)) {
            t.setHoverEvent(genTextHoverEvent(performer));
            final String[] arr = performer.split("\\|");
            t.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                "/tp " + arr[1] + " " + arr[2] + " " + arr[2] + " "));
        } else if (CommandSenderType.ENTITY.name().equals(type)) {
            t.setHoverEvent(genTextHoverEvent(performer));
            t.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp " + performer));
        }
        return t;
    }
}
