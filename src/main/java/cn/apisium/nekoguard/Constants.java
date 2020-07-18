package cn.apisium.nekoguard;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.event.inventory.InventoryType;

public final class Constants {
    public static final String HEADER = "¡ìb¡ìm                    ¡ìr ¡ìe[NekoGuard] ¡ìb¡ìm                    ";
    private static final String FOOTER = "¡ìb¡ìm                  ";

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

    public static TextComponent makeFooter(int page, final int all) {
        page++;
        final int allPage = (int) Math.ceil(all / 10D);
        final TextComponent t = new TextComponent("¡ìb¡ìm                  "),
            pageText = new TextComponent(" µ±Ç°Ò³Êý: "),
            pages = new TextComponent(page + "¡ì7/" + allPage),
            la = new TextComponent(" \u25c0"),
            lr = new TextComponent(" \u25b6 ");

        if (page == 1) la.setColor(ChatColor.GRAY);
        else {
            la.setColor(ChatColor.YELLOW);
            la.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nekoguard page " + (page - 1)));
        }
        if (page == allPage) lr.setColor(ChatColor.GRAY);
        else {
            lr.setColor(ChatColor.YELLOW);
            lr.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nekoguard page " + page));
        }
        pageText.setColor(ChatColor.GRAY);
        pages.setColor(ChatColor.WHITE);
        final TextComponent t3 = t.duplicate();
        t.addExtra(la);
        t.addExtra(pageText);
        t.addExtra(pages);
        t.addExtra(lr);
        t.addExtra(t3);
        return t;
    }
}
