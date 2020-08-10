package cn.apisium.nekoguard.utils;

import cn.apisium.nekoguard.Constants;
import cn.apisium.nekoguard.PlatformImpl;
import com.google.common.base.Strings;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.chat.ComponentSerializer;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.influxdb.dto.QueryResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public final class Utils {
    public static PlatformImpl PLATFORM = null;
    private final static TextComponent EMPTY = new TextComponent();
    private final static Pattern CUSTOM_NAME = Pattern.compile("CustomName:'(.+?)(?<!\\\\)'");
    private final static SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Utils() {}

    @NotNull
    public static String getBlockName(@NotNull final String data) {
        return "block." + getMaterialId(data).replace(':', '.');
    }
    @NotNull
    public static String getMaterialId(@NotNull final String data) {
        return data.split("\\[", 2)[0].split(Constants.TILE, 2)[0];
    }

    @NotNull
    public static String getEntityName(@NotNull final String data) {
        return "entity." + data.replace(':', '.');
    }

    @Nullable
    public static BaseComponent[] parseName(@NotNull final String text) {
        try {
            return ComponentSerializer.parse(text.replace("\\'", "'").replace("\\\\", "\\"));
        } catch (final Exception ignored) { return null; }
    }

    @NotNull
    public static BaseComponent getDeathEntityComponent(@NotNull final String type, @NotNull final String data) {
        BaseComponent t;
        if (type.startsWith("@")) t = new TranslatableComponent(getEntityName(type.substring(1)));
        else if (type.isEmpty()) t = new TextComponent("未知");
        else return getPlayerPerformerNameComponent(type, true);
        final Matcher matcher = CUSTOM_NAME.matcher(data);
        if (matcher.find()) {
            final String str = matcher.group(1);
            if (!str.isEmpty()) {
                final BaseComponent[] name = parseName(str);
                if (name != null) {
                    final BaseComponent t2 = t;
                    t = new TextComponent(name);
                    t.setHoverEvent(genHoverEvent(t2));
                    t.setColor(ChatColor.LIGHT_PURPLE);
                }
            }
        }
        return t;
    }

    @Nullable
    public static List<QueryResult.Result> getResult(@NotNull final QueryResult res) {
        if (res.hasError()) {
            System.out.println(res.getError());
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
            System.out.println(ret.getError());
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
        final String name = PLATFORM.getPlayerName(performer);
        return getPlayerNameComponentWithUUID(name, performer, pad);
    }

    @NotNull
    public static TextComponent getPlayerNameComponentWithUUID(@NotNull final String name, @NotNull final String id, final boolean pad) {
        final TextComponent t = new TextComponent(pad ? padPlayerName(name) : name);
        t.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id));
        t.setHoverEvent(genTextHoverEvent(id));
        return t;
    }

    @NotNull
    public static BaseComponent getPerformerComponent(@Nullable final String performer) {
        return getPerformerComponent(performer, true);
    }
    @NotNull
    public static BaseComponent getPerformerComponent(@Nullable final String performer, final boolean pad) {
        final String s1, s2;
        if (performer == null || performer.isEmpty()) {
            s1 = "";
            s2 = "未知";
        } else if (performer.startsWith("@")) {
            s1 = "实体:";
            s2 = getEntityName(performer.substring(1));
        } else if (performer.startsWith("#")) {
            s1 = "方块:";
            s2 = getBlockName(performer.substring(1));
        } else return getPlayerPerformerNameComponent(performer, pad);
        final TextComponent t = new TextComponent(s1);
        t.setColor(ChatColor.GRAY);
        final TranslatableComponent t1 = new TranslatableComponent(Strings.padEnd(s2, 16, ' '));
        t1.setColor(ChatColor.WHITE);
        t.addExtra(t1);
        return t;
    }

    @NotNull
    public static String formatDuration(final long time) {
        final String str = DurationFormatUtils.formatDuration(time, "d天HH时m分s秒前", false);
        return str
            .replace("0分", "")
            .replace("0天", "")
            .replace("0时", "");
    }

    public static long getCurrentTime() {
        final Instant inst = Instant.now();
        return inst.toEpochMilli() * 1000000L + inst.getNano();
    }
    @NotNull
    public static String getBlockPerformer(@NotNull final String world, final int x, final int y, final int z) {
        return "#" + world + "|" + x + "|" + y + "|" + z;
    }

    public static TextComponent getEntityTypePerformerComponent(@NotNull final String id, @NotNull final String type) {
        final TextComponent t = new TextComponent("[");
        t.addExtra(new TranslatableComponent(getEntityName(type)));
        t.addExtra("]");
        t.setColor(ChatColor.LIGHT_PURPLE);
        t.setHoverEvent(genTextHoverEvent(Constants.TP_MESSAGE + id));
        t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + id));
        return t;
    }

    public static BaseComponent getEntityTypePerformerComponent(@NotNull final String type, @NotNull final String world, final int x, final int y, final int z) {
        final BaseComponent t = getPerformerComponent(type);
        processActionComponent(t, world, x, y, z);
        return t;
    }

    public static TextComponent getUnknownBlockPerformerComponent(@NotNull final String world, final int x, final int y, final int z) {
        final TextComponent t = new TextComponent("方块");
        processActionComponent(t, world, x, y, z);
        return t;
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    public static TextComponent getContainerPerformerName(@Nullable final String entity, @Nullable final String world, @Nullable final Number x, final Number y, final Number z) {
        return entity == null || entity.isEmpty()
            ? world == null || world.isEmpty() ? Constants.UNKNOWN : PLATFORM.getBlockPerformerComponent(world, x.intValue(), y.intValue(), z.intValue())
            : PLATFORM.getEntityPerformerComponent(entity, true);
    }

    @SuppressWarnings("ConstantConditions")
    @NotNull
    public static TextComponent getContainerPerformerNameRoughly(@Nullable final String entity, @Nullable final String world, @Nullable final Number x, final Number y, final Number z) {
        return entity == null || entity.isEmpty()
            ? world == null || world.isEmpty() ? Constants.UNKNOWN : getUnknownBlockPerformerComponent(world, x.intValue(), y.intValue(), z.intValue())
            : PLATFORM.getEntityPerformerComponent(entity, false);
    }

    @NotNull
    public static HoverEvent genTextHoverEvent(@NotNull final String text) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent[] { new TextComponent(text) });
    }
    @NotNull
    public static HoverEvent genHoverEvent(@NotNull final BaseComponent c) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new BaseComponent[] { c });
    }
    @SuppressWarnings("deprecation")
    @NotNull
    public static HoverEvent genItemHoverEvent(@NotNull final String text) {
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new TextComponent[] { new TextComponent(text) });
    }

    @NotNull
    public static BaseComponent getItemStackDetails(@NotNull final String data, @Nullable final String cmd) {
        final ItemStackParser p = new ItemStackParser(data);
        final TextComponent t;
        if (p.name == null){
            if (p.id == null) t = new TextComponent("[未知]");
            else {
                t = new TextComponent("[");
                t.addExtra(new TranslatableComponent(PLATFORM.getItemName(p.id)));
                t.addExtra("]");
            }
        } else {
            t = new TextComponent("[");
            for (final BaseComponent c : p.name) t.addExtra(c);
            t.addExtra("]");
        }
        if (p.amount > 1) {
            final TextComponent t1 = new TextComponent("x" + p.amount);
            t1.setColor(ChatColor.GRAY);
            t.addExtra(t1);
        }
        t.setHoverEvent(genItemHoverEvent(data));
        t.setColor(ChatColor.LIGHT_PURPLE);
        if (cmd != null) t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, cmd));
        return t;
    }

    @NotNull
    public static TextComponent getBlockComponent(@NotNull final String name) {
        final String id = getMaterialId(name);
        final TextComponent t = new TextComponent("[");
        t.addExtra(new TranslatableComponent(Utils.getBlockName(id)));
        t.addExtra("]");
        t.setColor(ChatColor.LIGHT_PURPLE);
        t.setHoverEvent(genItemHoverEvent("{id:\"" + id + "\",Count:1b}"));
        return t;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> void throwSneaky(@NotNull Throwable exception) throws T {
        throw (T) exception;
    }

    @NotNull
    public static TextComponent getTimeComponent(@NotNull final String time, final long now) {
        final Instant instant = Instant.parse(time);
        final TextComponent t = new TextComponent("  " + Strings.padEnd(
            formatDuration(now - instant.toEpochMilli()), 10, ' ') + "  ");
        t.setColor(ChatColor.GRAY);
        t.setItalic(true);
        t.setHoverEvent(genTextHoverEvent(FORMATTER.format(Date.from(instant))));
        return t;
    }

    @NotNull
    public static TextComponent getPlayerCommandNameComponent(@NotNull final String type, @NotNull final String performer) {
        if (type.length() == 36) return getPlayerPerformerNameComponent(type, false);
        final TextComponent t = new TextComponent("#" + type);
        if ("BLOCK".equals(type)) {
            t.setHoverEvent(genTextHoverEvent(Constants.TP_MESSAGE + performer));
            t.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                "/tp " + performerToLocation(performer)[1]));
        } else if ("ENTITY".equals(type)) {
            t.setHoverEvent(genTextHoverEvent(Constants.TP_MESSAGE + performer));
            t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + performer));
        }
        return t;
    }

    @NotNull
    public static TextComponent getActionComponentOfLocation(final boolean isAdd, @NotNull final String world, final int x, final int y, final int z) {
        final TextComponent t = getAddOrRemoveActionComponent(isAdd);
        processActionComponent(t, world, x, y, z);
        return t;
    }
    public static void processActionComponent(final BaseComponent t, @NotNull final String world, final int x, final int y, final int z) {
        final String loc = " " + x + " " + y + " " + z;
        t.setHoverEvent(genTextHoverEvent(Constants.TP_MESSAGE + world + loc));
        t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp" + loc));
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
    public static TextComponent getAddOrRemoveActionComponent(final boolean isAdd) {
        final TextComponent t = new TextComponent(isAdd ? " + " : " - ");
        t.setColor(isAdd ? ChatColor.GREEN : ChatColor.RED);
        return t;
    }

    @NotNull
    public static TextComponent getActionComponentOfEntity(boolean isAdd, @NotNull final String entity) {
        final TextComponent t = getAddOrRemoveActionComponent(isAdd);
        t.setHoverEvent(genTextHoverEvent(Constants.TP_MESSAGE + entity));
        t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + entity));
        return t;
    }

    public static boolean isAddContainerAction(@NotNull final Object[] arr, @NotNull final String world, final int x, final int y, final int z) {
        return world.equals(arr[8]) && ((Double) arr[9]).intValue() == x &&
            ((Double) arr[10]).intValue() == y && ((Double) arr[11]).intValue() == z;
    }

    @NotNull
    public static Consumer<QueryResult> getCountConsumer(@NotNull final Consumer<Integer> onSuccess) {
        return it -> {
            final QueryResult.Series data = Utils.getFirstResult(it);
            if (data == null) onSuccess.accept(0);
            else onSuccess.accept(((Double) data.getValues().get(0).get(1)).intValue());
        };
    }

    @NotNull
    public static TextComponent genCopyComponent(@NotNull final String text, @Nullable final String prefix) {
        final TextComponent t = new TextComponent(prefix == null ? text : prefix + text);
        t.setColor(ChatColor.GRAY);
        t.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text));
        return t;
    }

    public static TextComponent getAddressComponent(String text, final boolean hasPerm) {
        if (text == null || text.isEmpty()) return EMPTY;
        if (!hasPerm) {
            final String[] ret = text.split("\\.");
            if (ret.length == 4) text = ret[0] + "." + ret[1] + ".§k***§7.§k***";
            else {
                final int len = text.length() / 2;
                text = text.substring(0, len) + "§k" + StringUtils.repeat("*", len);
            }
        }
        final TextComponent t = new TextComponent(text);
        if (hasPerm) {
            t.setHoverEvent(Constants.COPY_HOVER);
            t.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text));
        }
        t.setColor(ChatColor.GRAY);
        return t;
    }
}
