package cn.apisium.nekoguard;

import cn.apisium.nekoguard.utils.CommandSenderType;
import cn.apisium.nekoguard.utils.Utils;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class Events implements Listener {
    private final API api;
    private final Main main;
    private final Messages messages;
    Events(final Main main) {
        this.api = main.getApi();
        this.messages = main.getMessages();
        this.main = main;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent e) {
        api.recordBlockBreak(e.getBlock(), e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent e) {
        if (main.inspecting.contains(e.getPlayer())) {
            final Block b = e.getBlock();
            e.setCancelled(true);
            messages.sendQueryBlockMessage(e.getPlayer(), b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0);
        } else api.recordBlockPlace(e.getBlock(), e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent e) {
        if (e.blockList().isEmpty()) return;
        api.recordBlocksBreak(e.blockList(), "#" + e.getEntityType().getKey().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent e) {
        if (e.blockList().isEmpty()) return;
        api.recordBlocksBreak(e.blockList(), "@" + e.getBlock().getType().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(final BlockSpreadEvent e) {
        final String id = "@" + e.getBlock().getType().getKey();
        api.recordBlockBreak(e.getBlock(), id);
        api.recordBlockPlace(e.getNewState(), id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(final BlockBurnEvent e) {
        api.recordBlockBreak(e.getBlock(), "@" + Material.FIRE.getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketFill(final PlayerBucketFillEvent e) {
        api.recordBlockBreak(e.getBlock(), e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent e) {
        api.recordBlockBreak(e.getBlock(), e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(final BlockFormEvent e) {
        final String id = "@" + e.getNewState().getType().getKey();
        api.recordBlockBreak(e.getBlock(), id);
        api.recordBlockPlace(e.getNewState(), id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBlockForm(final EntityBlockFormEvent e) {
        if (!(e.getEntity() instanceof Snowman)) return;
        final String id = "#" + e.getEntity().getType().getKey();
        api.recordBlockBreak(e.getBlock(), id);
        api.recordBlockPlace(e.getNewState(), id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(final EntityChangeBlockEvent e) {
        final String id = "#" + e.getEntityType().getKey();
        api.recordBlockBreak(e.getBlock(), id);
        if (!e.getTo().isAir()) api.recordBlockPlace(e.getBlock().getState(), e.getTo(), id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent e) {
        api.recordChat(e.getMessage(), e.getPlayer().getUniqueId().toString());
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        final Block b = e.getClickedBlock();
        if (!e.hasBlock() || b == null || e.getAction() == Action.PHYSICAL ||
            (e.hasItem() && e.getAction() == Action.RIGHT_CLICK_BLOCK) ||
            !main.inspecting.contains(p)) return;
        e.setCancelled(true);
        if (b.getState() instanceof Container) messages.sendContainerActionsMessage(p, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0, null);
        else messages.sendQueryBlockMessage(p, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(final InventoryMoveItemEvent e) {
        if (e.getDestination() == e.getSource() ||
            !Constants.isNeedToRecordContainerAction(e.getSource().getType()) ||
            !Constants.isNeedToRecordContainerAction(e.getDestination().getType())) return;
        api.recordContainerAction(e.getItem().clone(), e.getSource(), e.getDestination());
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
                    api.recordContainerAction(is, inv, id);
            } else if (Constants.isNeedToRecordContainerAction(inv.getType()))
                api.recordContainerAction(is, id, inv);
            return;
        }
        if (e.getClickedInventory() != inv) return;
        switch (e.getAction()) {
            case SWAP_WITH_CURSOR:
                final ItemStack is2 = e.getView().getCursor();
                if (is2 != null && !is2.getType().isAir()) api.recordContainerAction(is2.clone(),
                    e.getWhoClicked().getUniqueId().toString(), inv);
            case PICKUP_ALL:
            case PICKUP_HALF:
            case PICKUP_ONE:
            case PICKUP_SOME:
            case COLLECT_TO_CURSOR:
                api.recordContainerAction(is, inv, e.getWhoClicked().getUniqueId().toString());
                break;
            case PLACE_SOME:
            case PLACE_ALL:
            case PLACE_ONE:
            case CLONE_STACK:
                api.recordContainerAction(is, e.getWhoClicked().getUniqueId().toString(), inv);
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(final PlayerCommandPreprocessEvent e) {
        try {
            api.recordCommand(e.getMessage(), e.getPlayer().getUniqueId().toString());
        } catch (final NoClassDefFoundError ignored) { }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerCommand(final ServerCommandEvent e) {
        try {
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
        } catch (final NoClassDefFoundError ignored) { }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent e) {
        final LivingEntity entity = e.getEntity();
        if (!main.recordMonsterKilledWithoutCustomName && entity.getCustomName() == null &&
            (entity instanceof Monster || entity instanceof Slime)) return;
        final EntityDamageEvent cause = entity.getLastDamageCause();
        final String killer = Utils.getKiller(entity),
            reason = cause == null ? "" : cause.getCause().name();
        if (e instanceof PlayerDeathEvent) {
            final PlayerDeathEvent e2 = (PlayerDeathEvent) e;
            api.recordPlayerDeath(killer, e2.getEntity(), reason,
                e2.getKeepInventory() ? null : e2.getDrops(),
                e2.getKeepLevel() ? 0 : e2.getDroppedExp()
            );
        } else api.recordDeath(killer, entity, reason);
    }
}