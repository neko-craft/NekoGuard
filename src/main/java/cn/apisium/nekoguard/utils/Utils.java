package cn.apisium.nekoguard.utils;

import com.google.common.base.Strings;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.TranslatableComponent;
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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

public final class Utils {
    private final static Class<?> nbtCompressedStreamToolsClass = ReflectionUtil.getNMSClass("NBTCompressedStreamTools");
    private final static Class<?> nbtTagCompoundClass = ReflectionUtil.getNMSClass("NBTTagCompound");
    private final static Class<?> tileEntityClass = ReflectionUtil.getNMSClass("TileEntity");
    private static final Class<?> nmsItemStackClass = ReflectionUtil.getNMSClass("ItemStack");
    private final static Class<?> craftBlockEntityStateClass = ReflectionUtil.getOBCClass("block.CraftBlockEntityState");
    private static final Class<?> craftItemStackClass = ReflectionUtil.getOBCClass("inventory.CraftItemStack");
    private final static Method getTileEntity = ReflectionUtil.getMethod(craftBlockEntityStateClass, "getTileEntity");
    private final static Method tileEntitySave = ReflectionUtil.getMethod(tileEntityClass, "save", nbtTagCompoundClass);
    private final static Method nbtSetInt = ReflectionUtil.getMethod(nbtTagCompoundClass, "setInt", String.class, int.class);
    private final static Method writeNBT = ReflectionUtil.getMethod(nbtCompressedStreamToolsClass, "writeNBT", nbtTagCompoundClass, OutputStream.class);
    private static final Method saveNmsItemStack = ReflectionUtil.getMethod(nmsItemStackClass, "save", nbtTagCompoundClass);
    private static final Method asNMSCopyMethod = ReflectionUtil.getMethod(craftItemStackClass, "asNMSCopy", ItemStack.class);
    private static final Field craftItemStackHandleField = ReflectionUtil.getField(craftItemStackClass, "handle", true);
    private Utils() {}

    public static String getMaterialName(final Material material) {
        return (material.isBlock() ? "block." : "item.") + material.getKey().toString().replace(':', '.');
    }
    public static String getMaterialName(final String data) {
        return "block." + data.split("\\[", 2)[0].replace(':', '.');
    }

    public static String getEntityName(final String data) {
        return "entity." + data.split("\\[", 2)[0].replace(':', '.');
    }

    public static List<QueryResult.Result> getResult(final QueryResult res) {
        if (res.hasError()) {
            Bukkit.getLogger().warning(res.getError());
            return null;
        }
        return res.getResults();
    }

    public static QueryResult.Series getFirstResult(final QueryResult res) {
        final List<QueryResult.Result> list = getResult(res);
        if (list == null || list.isEmpty()) return null;
        final QueryResult.Result ret = list.get(0);
        if (ret.hasError()) {
            Bukkit.getLogger().warning(ret.getError());
            return null;
        }
        return ret.getSeries() == null || ret.getSeries().isEmpty() ? null : ret.getSeries().get(0);
    }

    public static TextComponent getPlayerPerformerName(final String performer) {
        return new TextComponent(Strings.padEnd((String) ObjectUtils.defaultIfNull(
            Bukkit.getOfflinePlayer(UUID.fromString(performer)).getName(), "未知"), 16, ' '));
    }

    public static BaseComponent getPerformerName(final String performer) {
        final String s1, s2;
        if (performer.startsWith("#")) {
            s1 = "实体:";
            s2 = getEntityName(performer.substring(1));
        } else if (performer.startsWith("%")) {
            s1 = "方块:";
            s2 = getMaterialName(performer.substring(1));
        } else return getPlayerPerformerName(performer);
        final TextComponent t = new TextComponent(s1);
        t.setColor(ChatColor.GRAY);
        final TranslatableComponent t1 = new TranslatableComponent(Strings.padEnd(s2, 16, ' '));
        t1.setColor(ChatColor.WHITE);
        t.addExtra(t1);
        return t;
    }

    public static String formatDuration(final String start, final long end) {
        final String str = DurationFormatUtils.formatDuration(
            (end - Instant.parse(start).toEpochMilli()),
            "d天H时m分s秒前");
        return str
            .replace("0天", "")
            .replace("0时", "");
    }

    public static long getCurrentTime() {
        final Instant inst = Instant.now();
        return inst.toEpochMilli() * 1000000L + inst.getNano();
    }

    @SuppressWarnings({ "ConstantConditions", "deprecation" })
    public static String serializeTileEntity(final BlockState s) {
        if (!craftBlockEntityStateClass.isInstance(s)) return null;
        try (final ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            final Object nbt = tileEntitySave.invoke(getTileEntity.invoke(s), nbtTagCompoundClass.newInstance());
            nbtSetInt.invoke(nbt, "$v", Bukkit.getUnsafe().getDataVersion());
            writeNBT.invoke(null, nbt, stream);
            return Base64.getEncoder().encodeToString(stream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String serializeItemStack(final ItemStack is) {
        return Base64.getEncoder().encodeToString(is.serializeAsBytes());
    }
    public static ItemStack deserializeItemStack(final String data) {
        return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
    }

    public static String getFullBlockData(final Block block) {
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

    public static String getInventoryId(final Inventory inventory) {
        final InventoryHolder holder = inventory.getHolder();
        if (holder == null) return null;
        if (holder instanceof Entity) return ((Entity) holder).getUniqueId().toString();
        if (holder instanceof Container) {
            final Container b = (Container) holder;
            return "#" + b.getWorld().getName() + "|"  + b.getX() + "|" + b.getY() + "|" + b.getZ();
        } else return "";
    }

    public static String getBlockContainerId(final String world, final int x, final int y, final int z) {
        return "#" + world + "|" + x + "|" + y + "|" + z;
    }

    public static TextComponent getContainerPerformerName(final String str) {
        if (str.startsWith("#")) {
            final TextComponent t = new TextComponent("方块/实体:"), t1 = new TextComponent(str.substring(1));
            t.setColor(ChatColor.GRAY);
            t1.setColor(ChatColor.WHITE);
            t.addExtra(t1);
            return t;
        } else return getPlayerPerformerName(str);
    }

    @SuppressWarnings("ConstantConditions")
    private static String convertItemStackToJson(final ItemStack itemStack) {
        try {
            return saveNmsItemStack.invoke(getNMSItemStack(itemStack), nbtTagCompoundClass.newInstance()).toString();
        } catch (Exception t) {
            t.printStackTrace();
            return null;
        }
    }

    @SuppressWarnings("ConstantConditions")
    private static Object getNMSItemStack(final ItemStack itemStack) throws Exception {
        Object nms = null;
        if (craftItemStackClass.isInstance(itemStack)) try {
            nms = craftItemStackHandleField.get(itemStack);
        } catch (Exception ignored) { }
        return nms == null ? asNMSCopyMethod.invoke(null, itemStack) : nms;
    }

    @SuppressWarnings("deprecation")
    public static BaseComponent getItemStackDetails(final ItemStack is) {
        final String json = convertItemStackToJson(is);
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
        if (json != null) t.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM,
            new TextComponent[] { new TextComponent(json) }));
        t.setColor(ChatColor.AQUA);
        return t;
    }
}
