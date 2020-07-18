package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.CommandSenderType;
import cn.apisium.nekoguard.utils.Utils;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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
        if (main.inspecting.contains(e.getPlayer())) {
            final Block b = e.getBlock();
            e.setCancelled(true);
            api.sendQueryBlockMessage(e.getPlayer(), b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0);
        } else api.recordBlock(e.getBlock(), e.getPlayer().getUniqueId().toString(), "1");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent e) {
        if (e.blockList().isEmpty()) return;
        api.recordBlocks(e.blockList(), "#" + e.getEntityType().getKey().toString(), "0");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent e) {
        api.recordChat(e.getMessage(), e.getPlayer().getUniqueId().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent e) {
        if (e.blockList().isEmpty()) return;
        api.recordBlocks(e.blockList(), "%" + e.getBlock().getType().getKey(), "0");
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        final Block b = e.getClickedBlock();
        if (!e.hasBlock() || b == null || e.getAction() == Action.PHYSICAL ||
            (e.hasItem() && e.getAction() == Action.RIGHT_CLICK_BLOCK) ||
            !main.inspecting.contains(p)) return;
        e.setCancelled(true);
        if (b.getState() instanceof Container) api.sendContainerActionsMessage(p, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0, null);
        else api.sendQueryBlockMessage(p, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(final InventoryMoveItemEvent e) {
        if (e.getDestination() == e.getSource() ||
            !Constants.isNeedToRecordContainerAction(e.getSource().getType()) ||
            !Constants.isNeedToRecordContainerAction(e.getDestination().getType())) return;
        final Inventory init = e.getInitiator();
        api.recordItemAction(e.getItem().clone(), "#" + init.getType().name(), e.getSource(), e.getDestination());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent e) {
        ItemStack is = e.getCurrentItem();
        final Inventory inv = e.getView().getTopInventory();
        final InventoryType type = inv.getType();
        if (is == null || is.getType().isAir()) return;
        if (e.getClickedInventory() == null) {
            return;
        }
        if (!Constants.isNeedToRecordContainerAction(type)) return;
        is = is.clone();
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            final String id = e.getWhoClicked().getUniqueId().toString();
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                if (Constants.isNeedToRecordContainerAction(inv.getType()))
                    api.recordItemAction(is, id, Utils.getInventoryId(inv), id);
            } else {
                if (Constants.isNeedToRecordContainerAction(inv.getType()))
                    api.recordItemAction(is, id, id, Utils.getInventoryId(inv));
            }
            return;
        }
        if (e.getClickedInventory() != inv) return;
        switch (e.getAction()) {
            case SWAP_WITH_CURSOR:
                final ItemStack is2 = e.getView().getCursor();
                if (is2 != null && !is2.getType().isAir()) api.recordItemAction(is2.clone(), e.getWhoClicked().getUniqueId().toString(),
                    "", Utils.getInventoryId(inv));
            case PICKUP_ALL:
            case PICKUP_HALF:
            case PICKUP_ONE:
            case PICKUP_SOME:
            case COLLECT_TO_CURSOR:
                api.recordItemAction(is, e.getWhoClicked().getUniqueId().toString(),
                    Utils.getInventoryId(inv), "");
                break;
            case PLACE_SOME:
            case PLACE_ALL:
            case PLACE_ONE:
            case CLONE_STACK:
                api.recordItemAction(is, e.getWhoClicked().getUniqueId().toString(),
                    "", Utils.getInventoryId(inv));
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(final PlayerCommandPreprocessEvent e) {
        api.recordCommand(e.getMessage(), e.getPlayer().getUniqueId().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(final ServerCommandEvent e) {
        final CommandSender sender = e.getSender();
        String performer = null;
        final CommandSenderType type = CommandSenderType.getCommandSenderType(sender);
        switch (type) {
            case BLOCK:
                performer = Utils.getBlockPerformer(((BlockCommandSender) sender).getBlock());
                break;
            case ENTITY:
                performer = ((Entity) sender).getUniqueId().toString();
        }
        api.recordCommand(e.getCommand(), type.name(), performer);
    }
}
