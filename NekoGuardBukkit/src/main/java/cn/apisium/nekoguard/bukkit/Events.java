package cn.apisium.nekoguard.bukkit;

import cn.apisium.nekocommander.ProxiedCommandSender;
import cn.apisium.nekoguard.Constants;
import cn.apisium.nekoguard.Messages;
import cn.apisium.nekoguard.bukkit.utils.CommandSenderType;
import cn.apisium.nekoguard.bukkit.utils.NMSUtils;
import cn.apisium.nekoguard.bukkit.utils.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.vehicle.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class Events implements Listener {
    private final cn.apisium.nekoguard.API frontApi;
    private final API api;
    private final cn.apisium.nekoguard.Main main;
    private final Messages messages;
    private final cn.apisium.nekoguard.bukkit.Main plugin;
    Events(final cn.apisium.nekoguard.bukkit.Main plugin) {
        main = Main.getInstance();
        frontApi = main.getApi();
        api = plugin.getApi();
        this.plugin = plugin;
        this.messages = main.getMessages();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(final BlockBreakEvent e) {
        api.recordBlockAction(e.getBlock(), e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(final BlockPlaceEvent e) {
        if (main.isInspecting(e.getPlayer())) {
            final Block b = e.getBlock();
            e.setCancelled(true);
            messages.sendQueryBlockMessage(ProxiedCommandSender.newInstance(e.getPlayer()), b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0);
        } else api.recordBlockAction(e.getBlock(), e.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent e) {
        if (e.blockList().isEmpty()) return;
        api.recordBlocksBreak(e.blockList(), "@" + e.getEntityType().getKey().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent e) {
        if (e.blockList().isEmpty()) return;
        api.recordBlocksBreak(e.blockList(), "#" + e.getBlock().getType().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(final BlockSpreadEvent e) {
        final String id = "#" + e.getBlock().getType().getKey();
        api.recordBlockAction(e.getBlock(), id, true);
        api.recordBlockAction(e.getNewState(), id, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurn(final BlockBurnEvent e) {
        api.recordBlockAction(e.getBlock(), "#" + Material.FIRE.getKey(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketFill(final PlayerBucketFillEvent e) {
        api.recordBlockAction(e.getBlock(), e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBucketEmpty(final PlayerBucketEmptyEvent e) {
        api.recordBlockAction(e.getBlock(), e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(final BlockFormEvent e) {
        final String id = "#" + e.getNewState().getType().getKey();
        api.recordBlockAction(e.getBlock(), id, true);
        api.recordBlockAction(e.getNewState(), id, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(final BlockFromToEvent e) {
        api.recordBlockAction(e.getToBlock(), "#" + e.getBlock().getType().getKey(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBlockForm(final EntityBlockFormEvent e) {
        if (!(e.getEntity() instanceof Snowman)) return;
        final String id = "@" + e.getEntity().getType().getKey();
        api.recordBlockAction(e.getBlock(), id, true);
        api.recordBlockAction(e.getNewState(), id, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(final EntityChangeBlockEvent e) {
        final String id = "@" + e.getEntityType().getKey();
        api.recordBlockAction(e.getBlock(), id, true);
        if (!e.getTo().isAir()) {
            final Block block = e.getBlock();
            frontApi.recordBlockAction(id, false, block.getWorld().getName(), block.getX(), block.getY(),
                block.getZ(), e.getTo().getKey().toString());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent e) {
        frontApi.recordChat(e.getMessage(), e.getPlayer().getUniqueId().toString());
    }

    @EventHandler
    public void onPlayerInteract(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        final Block b = e.getClickedBlock();
        if (!e.hasBlock() || b == null || e.getAction() == Action.PHYSICAL ||
            (e.hasItem() && e.getAction() == Action.RIGHT_CLICK_BLOCK) ||
            !main.isInspecting(p)) return;
        e.setCancelled(true);
        final ProxiedCommandSender pcs = ProxiedCommandSender.newInstance(p);
        if (b.getState() instanceof Container) messages.sendContainerActionsMessage(pcs, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0);
        else messages.sendQueryBlockMessage(pcs, b.getWorld().getName(), b.getX(), b.getY(), b.getZ(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(final InventoryMoveItemEvent e) {
        if (e.getDestination() == e.getSource() ||
            Utils.isNeedToRecordContainerAction(e.getSource().getType()) ||
            Utils.isNeedToRecordContainerAction(e.getDestination().getType())) return;
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
        if (!Utils.isNeedToRecordContainerAction(type)) return;
        is = is.clone();
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                if (Utils.isNeedToRecordContainerAction(inv.getType()))
                    api.recordContainerAction(is, inv, e.getView().getBottomInventory());
            } else if (Utils.isNeedToRecordContainerAction(inv.getType()))
                api.recordContainerAction(is, e.getView().getBottomInventory(), inv);
            return;
        }
        if (e.getClickedInventory() != inv) return;
        switch (e.getAction()) {
            case SWAP_WITH_CURSOR:
                final ItemStack is2 = e.getView().getCursor();
                if (is2 != null && !is2.getType().isAir()) api.recordContainerAction(is2.clone(),
                    e.getView().getBottomInventory(), inv);
            case PICKUP_ALL:
            case PICKUP_HALF:
            case PICKUP_ONE:
            case PICKUP_SOME:
            case COLLECT_TO_CURSOR:
                api.recordContainerAction(is, inv, e.getView().getBottomInventory());
                break;
            case PLACE_SOME:
            case PLACE_ALL:
            case PLACE_ONE:
            case CLONE_STACK:
                api.recordContainerAction(is, e.getView().getBottomInventory(), inv);
                break;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(final PlayerCommandPreprocessEvent e) {
        frontApi.recordCommand(e.getMessage(), e.getPlayer().getUniqueId().toString());
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
            frontApi.recordCommand(e.getCommand(), type.name(), performer);
        } catch (final NoClassDefFoundError ignored) { }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(final EntityDeathEvent e) {
        final LivingEntity entity = e.getEntity();
        if (!plugin.recordMonsterKilledWithoutCustomName && entity.getCustomName() == null &&
            ((entity instanceof Monster && e.getEntityType() != EntityType.WITHER) ||
                entity instanceof Slime || entity instanceof Ambient)) return;
        final EntityDamageEvent cause = entity.getLastDamageCause();
        final String killer = Utils.getKiller(entity),
            reason = cause == null ? "" : cause.getCause().name();
        if (e instanceof PlayerDeathEvent) {
            final PlayerDeathEvent e2 = (PlayerDeathEvent) e;
            final Player p = e2.getEntity();
            final Location loc = p.getLocation();
            frontApi.recordPlayerDeath(killer, reason, p.getUniqueId().toString(), loc.getWorld().toString(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), e2.getKeepLevel() ? 0 : e2.getDroppedExp());
        } else api.recordDeath(killer, reason, entity);
    }

    @EventHandler
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent e) {
        final Entity entity = e.getDamager();
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !main.isInspecting(entity)) return;
        e.setCancelled(true);
        final ProxiedCommandSender pcs = ProxiedCommandSender.newInstance(e.getDamager());
        if (e.getEntity() instanceof InventoryHolder)
            messages.sendContainerActionsMessage(pcs, e.getEntity().getUniqueId().toString(), 0);
        else messages.sendQuerySpawnMessage(pcs, e.getEntity().getUniqueId().toString(), 0);
    }

    @EventHandler
    public void onVehicleDamage(final VehicleDamageEvent e) {
        final Entity entity = e.getAttacker();
        if (entity == null || !main.isInspecting(entity)) return;
        e.setCancelled(true);
        final ProxiedCommandSender pcs = ProxiedCommandSender.newInstance(entity);
        if (e.getVehicle() instanceof InventoryHolder)
            messages.sendContainerActionsMessage(pcs, e.getVehicle().getUniqueId().toString(), 0);
        else messages.sendQuerySpawnMessage(pcs, e.getVehicle().getUniqueId().toString(), 0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleDestroy(final VehicleDestroyEvent e) {
        api.recordDeath(Utils.getEntityPerformer(e.getAttacker()),
            e.getAttacker() == null ? "UNKNOWN" : "ENTITY_ATTACK", e.getVehicle());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(final HangingBreakEvent e) {
        if (e.getEntity() instanceof LeashHitch) return;
        final Entity remover = e instanceof HangingBreakByEntityEvent ? ((HangingBreakByEntityEvent) e).getRemover() : null;
        if (remover != null && main.isInspecting(remover)) {
            e.setCancelled(true);
            messages.sendQuerySpawnMessage(ProxiedCommandSender.newInstance(remover), e.getEntity().getUniqueId().toString(), 0);
        } else api.recordDeath(Utils.getEntityPerformer(remover), e.getCause().name(), e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(final EntitySpawnEvent e) {
        final Entity entity = e.getEntity();
        if (e.getEntity() instanceof LeashHitch || (cn.apisium.nekoguard.Constants.IS_PAPER &&
            entity.getEntitySpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL)) return;
        if (entity instanceof Animals || entity instanceof Hanging || entity instanceof Fish ||
            entity instanceof ArmorStand || entity instanceof Golem || entity instanceof Villager ||
            entity instanceof Wither) {
            api.recordSpawn(entity, cn.apisium.nekoguard.Constants.IS_PAPER ? entity.getEntitySpawnReason().name() : "");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleCreate(final VehicleCreateEvent e) {
        api.recordSpawn(e.getVehicle(), Constants.IS_PAPER ? e.getVehicle().getEntitySpawnReason().name() : "");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(final PlayerDropItemEvent e) {
        final Item item = e.getItemDrop();
        final Location loc = item.getLocation();
        frontApi.recordItemAction(NMSUtils.serializeItemStack(item.getItemStack()), true,
            e.getPlayer().getUniqueId().toString(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(final EntityPickupItemEvent e) {
        if (e.getEntityType() != EntityType.PLAYER) return;
        final Item item = e.getItem();
        final Location loc = item.getLocation();
        frontApi.recordItemAction(NMSUtils.serializeItemStack(item.getItemStack()), false,
            e.getEntity().getUniqueId().toString(), loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(final PlayerJoinEvent e) {
        api.recordPlayerSession(e.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent e) {
        api.recordPlayerSession(e.getPlayer(), false);
    }
}