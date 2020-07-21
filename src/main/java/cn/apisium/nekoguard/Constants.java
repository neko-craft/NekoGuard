package cn.apisium.nekoguard;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.event.inventory.InventoryType;

public final class Constants {
    public static boolean IS_PAPER;
    public static final String TILE = "§TILE§";
    public static final String PLAYER_NOT_EXISTS = "该玩家还从未在本服务器游玩过!";
    public static final String BLOCK_ACTION_BREAK = "0", BLOCK_ACTION_PLACE = "1";
    public static final String HEADER = "§b§m                     §r §e[NekoGuard] §b§m                     ";
    public static final RuntimeException IGNORED_ERROR = new RuntimeException("IGNORED");
    public static final String TP_MESSAGE = "§a点击立即传送到: §f";

    static {
        try {
            Class.forName("com.destroystokyo.paper.entity.Pathfinder");
            IS_PAPER = true;
        } catch (final Exception ignored) { }
    }

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
        final TextComponent t = new TextComponent("§b§m                  "),
            pageText = new TextComponent(" 当前页数: "),
            pages = new TextComponent(page + "§7/" + allPage),
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
