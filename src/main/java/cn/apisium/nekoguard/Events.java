package cn.apisium.nekoguard;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.influxdb.dto.QueryResult;

import java.util.List;
import java.util.stream.Collectors;

public final class Events implements Listener {
    private final API api;
    private final Main main;
    Events(final Main main) {
        this.api = main.getApi();
        this.main = main;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent e) {
        api.recordBlock(e.getBlock(), e.getPlayer().getUniqueId().toString(), "0");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent e) {
        api.recordBlock(e.getBlock(), e.getPlayer().getUniqueId().toString(), "1");
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        final Block b = e.getClickedBlock();
        if (!e.hasBlock() || b == null || e.getAction() != Action.LEFT_CLICK_BLOCK || !main.inspecting.contains(p)) return;
        e.setCancelled(true);
        api.inspectBlock(b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), ret -> {
            final List<QueryResult.Series> res = Utils.getFirstResult(ret);
            if (res == null) return;
            res.forEach(it -> {
                // TODO:
                p.sendMessage(it.getValues().stream().map(Object::toString).collect(Collectors.joining(" ")));
            });
        });
    }
}
