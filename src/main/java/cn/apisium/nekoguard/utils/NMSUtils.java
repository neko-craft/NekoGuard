package cn.apisium.nekoguard.utils;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class NMSUtils {
    private NMSUtils() {}
    private final static Class<?> nbtTagCompoundClass = ReflectionUtil.getNMSClass("NBTTagCompound");
    private final static Class<?> tileEntityClass = ReflectionUtil.getNMSClass("TileEntity");
    private final static Class<?> nmsEntityClass = ReflectionUtil.getNMSClass("Entity");
    private static final Class<?> nbtParserClass = ReflectionUtil.getNMSClass("MojangsonParser");
    private static final Class<?> nmsItemStackClass = ReflectionUtil.getNMSClass("ItemStack");
    private static final Class<?> nmsIBlockDataClass = ReflectionUtil.getNMSClass("IBlockData");
    private static final Class<?> craftCraftEntityClass = ReflectionUtil.getOBCClass("entity.CraftEntity");
    private final static Class<?> craftBlockEntityStateClass = ReflectionUtil.getOBCClass("block.CraftBlockEntityState");
    private static final Class<?> craftItemStackClass = ReflectionUtil.getOBCClass("inventory.CraftItemStack");
    private static final Class<?> craftBlockDataClass = ReflectionUtil.getOBCClass("block.data.CraftBlockData");
    private final static Method getTileEntity = ReflectionUtil.getMethod(craftBlockEntityStateClass, "getTileEntity");
    private final static Method tileEntityUpdate = ReflectionUtil.getMethod(tileEntityClass, "update");
    private final static Method tileEntitySave = ReflectionUtil.getMethod(tileEntityClass, "save", nbtTagCompoundClass);
    private final static Method tileEntityLoad = ReflectionUtil.getMethod(tileEntityClass, "load", nmsIBlockDataClass, nbtTagCompoundClass);
    private static final Method saveNmsItemStack = ReflectionUtil.getMethod(nmsItemStackClass, "save", nbtTagCompoundClass);
    private final static Method craftEntityGetHandle = ReflectionUtil.getMethod(craftCraftEntityClass, "getHandle");
    private final static Method entitySave = ReflectionUtil.getMethod(nmsEntityClass, "save", nbtTagCompoundClass);
    private final static Method entityLoad = ReflectionUtil.getMethod(nmsEntityClass, "load", nbtTagCompoundClass);
    private static final Method asNMSCopyMethod = ReflectionUtil.getMethod(craftItemStackClass, "asNMSCopy", ItemStack.class);
    private static final Method nbtParserParse = ReflectionUtil.getMethod(nbtParserClass, "parse", String.class);
    private static final Method craftBlockDataGetState = ReflectionUtil.getMethod(craftBlockDataClass, "getState");
    private static final Method itemStackFromCompound = ReflectionUtil.getMethod(nmsItemStackClass, "fromCompound", nbtTagCompoundClass);
    private static final Method itemStackAsCraftMirror = ReflectionUtil.getMethod(craftItemStackClass, "asCraftMirror", nmsItemStackClass);
    private static final Field craftItemStackHandleField = ReflectionUtil.getField(craftItemStackClass, "handle", true);

    @Nullable
    public static String serializeTileEntity(@NotNull final BlockState s) {
        final Object data = getTileEntity(s);
        return data == null ? null : data.toString();
    }

    @Nullable
    public static Object getTileEntity(@NotNull final BlockState s) {
        if (!craftBlockEntityStateClass.isInstance(s)) return null;
        try {
            return tileEntitySave.invoke(getTileEntity.invoke(s), nbtTagCompoundClass.newInstance());
        } catch (final Exception e) {
            Utils.throwSneaky(e);
            throw new RuntimeException();
        }
    }

    @NotNull
    public static String serializeItemStack(@NotNull final ItemStack itemStack) {
        try {
            return saveNmsItemStack.invoke(getNMSItemStack(itemStack), nbtTagCompoundClass.newInstance()).toString();
        } catch (final Exception t) {
            Utils.throwSneaky(t);
            throw new RuntimeException();
        }
    }

    @Nullable
    public static ItemStack deserializeItemStack(@NotNull final String data) {
        try {
            return (ItemStack) itemStackAsCraftMirror.invoke(null,
                itemStackFromCompound.invoke(null, nbtParserParse.invoke(null, data)));
        } catch (final Exception ignored) {
            return null;
        }
    }

    @NotNull
    public static String serializeEntity(@NotNull final Entity entity) {
        try {
            return entitySave.invoke(craftEntityGetHandle.invoke(entity), nbtTagCompoundClass.newInstance()).toString();
        } catch (final Exception t) {
            Utils.throwSneaky(t);
            throw new RuntimeException();
        }
    }

    public static void loadEntityData(@NotNull final Entity entity, @NotNull final String data) {
        try {
            entityLoad.invoke(craftEntityGetHandle.invoke(entity), nbtParserParse.invoke(null, data));
        } catch (final Exception t) {
            Utils.throwSneaky(t);
            throw new RuntimeException();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    private static Object getNMSItemStack(@NotNull final ItemStack itemStack) {
        try {
            Object nms = null;
            if (craftItemStackClass.isInstance(itemStack)) try {
                nms = craftItemStackHandleField.get(itemStack);
            } catch (final Exception ignored) { }
            return nms == null ? asNMSCopyMethod.invoke(null, itemStack) : nms;
        } catch (final Exception e) {
            Utils.throwSneaky(e);
            throw new RuntimeException();
        }
    }

    public static void loadTileStateData(@NotNull final Block block, @NotNull final String data) {
        final BlockState state = block.getState();
        if (!craftBlockEntityStateClass.isInstance(state)) return;
        try {
            final Object tile = getTileEntity.invoke(state);
            tileEntityLoad.invoke(tile, craftBlockDataGetState.invoke(block.getBlockData()),
                nbtParserParse.invoke(null, data));
            tileEntityUpdate.invoke(tile);
        } catch (Exception e) {
            Utils.throwSneaky(e);
            throw new RuntimeException();
        }
    }

    public static void loadTileStateData(@NotNull final Block block, @NotNull final Object data) {
        final BlockState state = block.getState();
        if (!craftBlockEntityStateClass.isInstance(state)) return;
        try {
            final Object tile = getTileEntity.invoke(state);
            tileEntityLoad.invoke(tile, craftBlockDataGetState.invoke(block.getBlockData()), data);
            tileEntityUpdate.invoke(tile);
        } catch (Exception e) {
            Utils.throwSneaky(e);
            throw new RuntimeException();
        }
    }
}
