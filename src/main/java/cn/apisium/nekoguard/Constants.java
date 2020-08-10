package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.Utils;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public final class Constants {
    public static boolean IS_PAPER;
    public static final String TILE = "��TILE��";
    public static final String PLAYER_NOT_EXISTS = "��e[NekoGuard] ��c����һ���δ�ڱ������������!";
    public static final String HEADER = "��b��m                     ��r ��e[NekoGuard] ��b��m                     ",
        FOOTER = "��b��m                                                           ";
    public static final RuntimeException IGNORED_ERROR = new RuntimeException("IGNORED");
    public static final String TP_MESSAGE = "��a����������͵�: ��f";
    public static final TextComponent UNKNOWN = new TextComponent("δ֪"), SPACE = new TextComponent("  "), EMPTY = new TextComponent(),
        LEFT_ARROW = new TextComponent(" �� "), TARGET = new TextComponent("  Ŀ��:"), UNCERTAIN = new TextComponent("(��ȷ��)");
    @SuppressWarnings("unused")
    public static final String SUCCESS = "��e[NekoGuard] ��a�����ɹ�!", FAILED = "��e[NekoGuard] ��c����ʧ��!";
    public static final String IN_INSPECTING = "��e[NekoGuard] ��b����ǰ���������ģʽ!";
    public static final String NO_RECORDS = "��e[NekoGuard] ��c��ǰû���κμ�¼!";
    public static final String COMMAND_LIMIT = "��e[NekoGuard] ��cִ��������ٶ�̫��!";
    public static final HoverEvent REDO_HOVER = Utils.genTextHoverEvent("��c������ｫ��ֱ�ӳ�������!");
    public static final HoverEvent COPY_HOVER = Utils.genTextHoverEvent("����������ֱ�Ӹ���!");

    static {
        UNKNOWN.setColor(ChatColor.GRAY);
        LEFT_ARROW.setColor(ChatColor.GREEN);
        TARGET.setColor(ChatColor.GRAY);
        UNCERTAIN.setColor(ChatColor.RED);
        UNCERTAIN.setItalic(true);
        try {
            Class.forName("com.destroystokyo.paper.entity.Pathfinder");
            IS_PAPER = true;
        } catch (final Exception ignored) { }
    }

    public static TextComponent makeFooter(int page, final int all) {
        page++;
        final int allPage = (int) Math.ceil(all / 10D);
        final TextComponent t = new TextComponent("��b��m                  "),
            pageText = new TextComponent(" ��ǰҳ��: "),
            pages = new TextComponent(page + "��7/" + allPage),
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
