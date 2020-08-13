package cn.apisium.nekoguard.bukkit.utils;

import cn.apisium.nekoguard.Constants;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Piston;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import cn.apisium.nekoguard.utils.Utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class NMSUtils {
    private NMSUtils() {}
    private final static Class<?> nbtTagCompoundClass = ReflectionUtil.getNMSClass("NBTTagCompound"),
        tileEntityClass = ReflectionUtil.getNMSClass("TileEntity"),
        nmsEntityClass = ReflectionUtil.getNMSClass("Entity"),
        nbtParserClass = ReflectionUtil.getNMSClass("MojangsonParser"),
        nmsItemStackClass = ReflectionUtil.getNMSClass("ItemStack"),
        nmsIBlockDataClass = ReflectionUtil.getNMSClass("IBlockData"),
        nmsTileEntityHopperClass = ReflectionUtil.getNMSClass("TileEntityHopper"),
        nmsIInventoryClass = ReflectionUtil.getNMSClass("IInventory"),
        nmsIWorldInventoryClass = ReflectionUtil.getNMSClass("IWorldInventory"),
        nmsEnumDirectionClass = ReflectionUtil.getNMSClass("EnumDirection"),
        craftCraftEntityClass = ReflectionUtil.getOBCClass("entity.CraftEntity"),
        craftBlockEntityStateClass = ReflectionUtil.getOBCClass("block.CraftBlockEntityState"),
        craftItemStackClass = ReflectionUtil.getOBCClass("inventory.CraftItemStack"),
        craftBlockDataClass = ReflectionUtil.getOBCClass("block.data.CraftBlockData"),
        craftInventoryClass = ReflectionUtil.getOBCClass("inventory.CraftInventory");
    private final static Method tileEntityUpdate = ReflectionUtil.getMethod(tileEntityClass, "update"),
        tileEntitySave = ReflectionUtil.getMethod(tileEntityClass, "save", nbtTagCompoundClass),
        tileEntityLoad = ReflectionUtil.getMethod(tileEntityClass, "load", nmsIBlockDataClass, nbtTagCompoundClass),
        saveNmsItemStack = ReflectionUtil.getMethod(nmsItemStackClass, "save", nbtTagCompoundClass),
        craftEntityGetHandle = ReflectionUtil.getMethod(craftCraftEntityClass, "getHandle"),
        entitySave = ReflectionUtil.getMethod(nmsEntityClass, "save", nbtTagCompoundClass),
        entityLoad = ReflectionUtil.getMethod(nmsEntityClass, "load", nbtTagCompoundClass),
        asNMSCopyMethod = ReflectionUtil.getMethod(craftItemStackClass, "asNMSCopy", ItemStack.class),
        nbtParserParse = ReflectionUtil.getMethod(nbtParserClass, "parse", String.class),
        craftBlockDataGetState = ReflectionUtil.getMethod(craftBlockDataClass, "getState"),
        itemStackFromCompound = ReflectionUtil.getMethod(nmsItemStackClass, Constants.IS_PAPER ? "fromCompound" : "a", nbtTagCompoundClass),
        itemStackAsCraftMirror = ReflectionUtil.getMethod(craftItemStackClass, "asCraftMirror", nmsItemStackClass),
        getInventory = ReflectionUtil.getMethod(craftInventoryClass, "getInventory"),
        getSlotsForFace = ReflectionUtil.getMethod(nmsIWorldInventoryClass, "getSlotsForFace", nmsEnumDirectionClass),
        canPlaceItem = ReflectionUtil.getMethod(nmsTileEntityHopperClass, "a", true, nmsIInventoryClass, nmsItemStackClass, int.class, nmsEnumDirectionClass);
    private static final Field craftItemStackHandleField = ReflectionUtil.getField(craftItemStackClass, "handle", true),
        craftBlockEntityStateTileEntityField = ReflectionUtil.getField(craftBlockEntityStateClass, "tileEntity", true);
    private static final Object[] enumDirections = nmsEnumDirectionClass.getEnumConstants();

    @Nullable
    public static String serializeTileEntity(@NotNull final BlockState s) {
        final Object data = getTileEntity(s);
        return data == null ? null : data.toString();
    }

    @Nullable
    public static Object getTileEntity(@NotNull final BlockState s) {
        if (!craftBlockEntityStateClass.isInstance(s)) return null;
        try {
            return tileEntitySave.invoke(craftBlockEntityStateTileEntityField.get(s), nbtTagCompoundClass.newInstance());
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
            final Object tile = craftBlockEntityStateTileEntityField.get(state);
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
            final Object tile = craftBlockEntityStateTileEntityField.get(state);
            tileEntityLoad.invoke(tile, craftBlockDataGetState.invoke(block.getBlockData()), data);
            tileEntityUpdate.invoke(tile);
        } catch (Exception e) {
            Utils.throwSneaky(e);
            throw new RuntimeException();
        }
    }
    
    @NotNull
    public static String getFullBlockData(@NotNull final BlockState block) {
        BlockData data = block.getBlockData();
        switch (block.getType()) {
            case POTION:
            case STICKY_PISTON:
                data = data.clone();
                ((Piston) data).setExtended(false);
        }
        String str = data.getAsString();
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
        block.setBlockData(Bukkit.createBlockData(arr[0]), false);
        if (arr.length == 2) NMSUtils.loadTileStateData(block, arr[1]);
    }

    public static boolean canItemBeAdded(@NotNull final Inventory inventory, @NotNull final ItemStack itemStack, @Nullable final BlockFace face) {
        try {
            final Object inv = getInventory.invoke(inventory), direction = getEnumDirection(face), item = getNMSItemStack(itemStack);
            if (nmsIWorldInventoryClass.isInstance(inv)) {
                for (final int i : ((int[]) getSlotsForFace.invoke(inv, direction)))
                    if (canItemBeAddedToSlot(inventory, itemStack, i, direction, inv, item)) return true;
            } else {
                final int len = inventory.getSize();
                for (int i = 0; i < len; i++) if (canItemBeAddedToSlot(inventory, itemStack, i, direction, inv, item)) return true;
            }
            return false;
        } catch (final Exception e) {
            Utils.throwSneaky(e);
            throw new RuntimeException();
        }
    }

    public static boolean canItemBeAddedToSlot(@NotNull final Inventory inventory, @NotNull final ItemStack itemStack, final int i, @Nullable final Object direction, @Nullable final Object inv, @Nullable final Object item) {
        try {
            if (!(boolean) canPlaceItem.invoke(null, inv, item, i, direction)) return false;
            final ItemStack is = inventory.getItem(i);
            return is == null || is.getType().isEmpty() || (itemStack.isSimilar(is) && is.getAmount() < is.getMaxStackSize());
        } catch (final Exception e) {
            Utils.throwSneaky(e);
            throw new RuntimeException();
        }
    }

    public static void addItemToBottom(@NotNull final Inventory inventory, @NotNull final ItemStack itemStack) {
        try {
            final int size = itemStack.getMaxStackSize();
            int l = itemStack.getAmount();
            for (final int i : ((int[]) getSlotsForFace.invoke(getInventory.invoke(inventory), enumDirections[0]))) {
                final ItemStack is = inventory.getItem(i);
                if (is == null || is.getType().isEmpty()) {
                    inventory.setItem(i, itemStack);
                    return;
                }
                if (itemStack.isSimilar(is) && is.getAmount() < is.getMaxStackSize()) {
                    final int k = Math.min(l, size - is.getAmount());

                    l -= k;
                    is.add(k);
                    inventory.setItem(i, is);
                }
                if (l < 1) return;
            }
        } catch (final Exception e) {
            Utils.throwSneaky(e);
            throw new RuntimeException();
        }
    }

    @Nullable
    public static Object getEnumDirection(@Nullable final BlockFace face) {
        if (face == null) return null;
        switch (face) {
            case DOWN: return enumDirections[0];
            case UP: return enumDirections[1];
            case NORTH: return enumDirections[2];
            case SOUTH: return enumDirections[3];
            case WEST: return enumDirections[4];
            case EAST: return enumDirections[5];
            default: return null;
        }
    }
}
