package cn.apisium.nekoguard;

import org.bukkit.event.inventory.InventoryType;

public final class Constants {
    public static final String HEADER = "¡ìb¡ìm                    ¡ìr ¡ìe[NekoGuard] ¡ìb¡ìm                    ";
    public static final String FOOTER = "¡ìb¡ìm                                                       ";

    public static boolean isNeedToRecordContainerAction(final InventoryType type) {
        switch (type) {
            case CHEST:
            case BARREL:
            case HOPPER:
            case SMOKER:
            case BREWING:
            case FURNACE:
            case DROPPER:
            case LECTERN:
            case CREATIVE:
            case PLAYER:
            case MERCHANT:
            case DISPENSER:
            case SHULKER_BOX:
            case BLAST_FURNACE:
                return true;
            default: return false;
        }
    }
}
