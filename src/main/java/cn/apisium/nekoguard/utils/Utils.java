package cn.apisium.nekoguard.utils;

import cn.apisium.nekoguard.Constants;
import com.google.common.base.Strings;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.chat.ComponentSerializer;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
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
    private final static Pattern CUSTOM_NAME = Pattern.compile("CustomName:'(.+?)(?<!\\\\)'");
    private final static HashSet<String> ITEMS = new HashSet<>();
    private final static SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private Utils() {}

    static {
        for (final Material type : Material.values()) if (!type.isBlock()) ITEMS.add(type.getKey().toString());
    }

    @NotNull
    public static String getItemName(@NotNull final String name) {
        return (ITEMS.contains(name) ? "item." : "block.") + name.replace(':', '.');
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
    public static BaseComponent getPerformerComponent(@NotNull final String performer) {
        return getPerformerComponent(performer, true);
    }
    @NotNull
    public static BaseComponent getPerformerComponent(@NotNull final String performer, final boolean pad) {
        final String s1, s2;
        if (performer.startsWith("@")) {
            s1 = "实体:";
            s2 = getEntityName(performer.substring(1));
        } else if (performer.startsWith("#")) {
            s1 = "方块:";
            s2 = getMaterialName(performer.substring(1));
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
        final String str = DurationFormatUtils.formatDuration(time, "d天H时m分s秒前");
        return str
            .replace("0天", "")
            .replace("0时", "");
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
        if (arr.length == 2) NMSUtils.loadTileStateData(block, arr[1]);
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
    public static BaseComponent getItemStackDetails(@NotNull final String data) {
        final ItemStackParser p = new ItemStackParser(data);
        final TextComponent t;
        if (p.name == null){
            if (p.id == null) t = new TextComponent("[未知]");
            else {
                t = new TextComponent("[");
                t.addExtra(new TranslatableComponent(getItemName(p.id)));
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
        processActionComponent(t, world, x, y, z);
        return t;
    }
    public static void processActionComponent(final TextComponent t, @NotNull final String world, final int x, final int y, final int z) {
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
    public static TextComponent getContainerActionComponent(final boolean isAdd, @NotNull final String performer, @NotNull final String source, @NotNull final String target) {
        final TextComponent t = new TextComponent(isAdd ? " + " : " - ");
        t.setColor(isAdd ? ChatColor.GREEN : ChatColor.RED);
        final String p;
        if (performer.equals(source)) p = target;
        else if (performer.equals(target)) p = source;
        else p = source.isEmpty() ? target : source;
        final String[] arr = performerToLocation(p);
        t.setHoverEvent(genTextHoverEvent(Constants.TP_MESSAGE + arr[0] + " " + arr[1]));
        t.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tp " + arr[1]));
        return t;
    }

    @NotNull
    public static String getChunkKey(@NotNull final String world, final int x, final int z) {
        return world + "|" + (x >> 4) + "|" + (z >> 4);
    }

    @NotNull
    public static String getKiller(@NotNull final LivingEntity entity) {
        if (entity.getKiller() != null) return entity.getKiller().getUniqueId().toString();
        final EntityDamageEvent cause = entity.getLastDamageCause();
        if (cause instanceof EntityDamageByBlockEvent) {
            final EntityDamageByBlockEvent c = (EntityDamageByBlockEvent) cause;
            if (c.getDamager() != null) return '#' + c.getDamager().getType().getKey().toString();
        } else if (cause instanceof EntityDamageByEntityEvent) return '@' + ((EntityDamageByEntityEvent) cause)
            .getDamager().getType().getKey().toString();
        return "";
    }

    public static Consumer<QueryResult> getCountConsumer(@NotNull final Consumer<Integer> onSuccess) {
        return it -> {
            final QueryResult.Series data = Utils.getFirstResult(it);
            if (data == null) onSuccess.accept(0);
            else onSuccess.accept(((Double) data.getValues().get(0).get(1)).intValue());
        };
    }
}
