package cn.apisium.nekoguard.bukkit;

import cn.apisium.nekoguard.Constants;
import cn.apisium.nekoguard.bukkit.utils.CommandSenderType;
import cn.apisium.nekoguard.bukkit.utils.NMSUtils;
import cn.apisium.nekoguard.bukkit.utils.Utils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.*;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
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
import org.bukkit.inventory.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Collection;
import java.util.List;

public final class Events implements Listener {
    private final cn.apisium.nekoguard.API frontApi;
    private final API api;
    private final cn.apisium.nekoguard.Main main;
    private final cn.apisium.nekoguard.bukkit.Main plugin;
    private static final String IGNITER_KEY = "nekoguard.igniter";

    Events(final cn.apisium.nekoguard.bukkit.Main plugin) {
        main = Main.getInstance();
        frontApi = main.getApi();
        api = plugin.getApi();
        this.plugin = plugin;

        if (plugin.recordGrowth) {
            plugin.getServer().getPluginManager()
                .registerEvent(BlockGrowEvent.class, this, EventPriority.MONITOR, (l, e1) -> {
                    final BlockGrowEvent e = (BlockGrowEvent) e1;
                    final String id = "#" + e.getNewState().getType().getKey();
                    api.recordBlockAction(e.getBlock(), id, true);
                    api.recordBlockAction(e.getNewState(), id, false);
                }, plugin, true);
            plugin.getServer().getPluginManager()
                .registerEvent(LeavesDecayEvent.class, this, EventPriority.MONITOR, (l, e1) -> {
                    final Block b = ((LeavesDecayEvent) e1).getBlock();
                    api.recordBlockAction(b, "#" + b.getType().getKey(), true);
                }, plugin, true);
        }
        if (plugin.recordContainerActionByNonPlayer) {
            plugin.getServer().getPluginManager()
                .registerEvent(InventoryMoveItemEvent.class, this, EventPriority.MONITOR, (l, e1) -> {
                    final InventoryMoveItemEvent e = (InventoryMoveItemEvent) e1;
                    if (e.getItem().getType().isEmpty()) return;
                    final Inventory s = e.getSource(), d = e.getDestination();
                    if (!Utils.isNeedToRecordContainerAction(s.getType()) || !Utils.isNeedToRecordContainerAction(d.getType())) return;
                    final InventoryHolder sh = s.getHolder(), dh = d.getHolder();
                    if (!NMSUtils.canItemBeAdded(d, e.getItem(),
                        sh instanceof BlockInventoryHolder && dh instanceof BlockInventoryHolder
                            ? ((BlockInventoryHolder) dh).getBlock().getFace(((BlockInventoryHolder) sh).getBlock()) : null)) return;
                    if (plugin.mergeContainerAction) api.recordContainerAction2(e.getItem().clone(), s, d);
                    else api.recordContainerAction(e.getItem().clone(), s, d);
                }, plugin, true);
            plugin.getServer().getPluginManager()
                .registerEvent(BlockDispenseEvent.class, this, EventPriority.MONITOR, (l, e1) -> {
                    final BlockDispenseEvent e = (BlockDispenseEvent) e1;
                    final BlockState state = e.getBlock().getState();
                    if (!(state instanceof BlockInventoryHolder)) return;
                    api.recordContainerAction(e.getItem(), ((BlockInventoryHolder) state).getInventory(), null);
                }, plugin, true);
        }
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
            api.inspectBlock(e.getPlayer(), b, false);
        } else api.recordBlockAction(e.getBlock(), e.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent e) {
        Entity entity = e.getEntity();
        String target = null;
        int radius = 8;
        switch (e.getEntityType()) {
            case PRIMED_TNT:
                Entity entity1 = ((TNTPrimed) entity).getSource();
                if (entity1 instanceof Projectile) {
                    final ProjectileSource shooter = ((Projectile) entity1).getShooter();
                    if (shooter instanceof Entity) entity1 = (Entity) shooter;
                }
                if (entity1 != null) target = Utils.getEntityPerformer(entity1);
                break;
            case FIREBALL:
                final ProjectileSource shooter = ((Fireball) entity).getShooter();
                if (shooter instanceof Entity) entity = (Entity) shooter;
                break;
            case CREEPER:
                radius = ((Creeper) entity).getExplosionRadius() + 1;
                if (entity.hasMetadata(IGNITER_KEY)) {
                    final List<MetadataValue> list = entity.getMetadata(IGNITER_KEY);
                    if (!list.isEmpty()) target = list.get(0).asString();
                }
        }
        if (target == null) target = entity instanceof Mob ? Utils.getEntityPerformer(((Mob) entity).getTarget()) : null;
        final String type = Utils.getEntityPerformer(entity);
        final Location loc = e.getLocation();
        boolean isNear = false;
        if (target == null) {
            Collection<Player> c = loc.getNearbyPlayers(radius);
            if (!c.isEmpty()) {
                target = ((Player) c.toArray()[0]).getUniqueId().toString();
                isNear = true;
            }
        }
        frontApi.recordExplosion(type, target == null ? "" : target, isNear,
            loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        if (e.blockList().isEmpty()) return;
        api.recordBlocksBreak(e.blockList(), type);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(final BlockExplodeEvent e) {
        if (e.blockList().isEmpty()) return;
        api.recordBlocksBreak(e.blockList(), "#" + e.getBlock().getType().getKey());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(final BlockSpreadEvent e) {
        if (!plugin.recordGrowth && Utils.isGrowing(e.getNewState().getType())) return;
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
        if (e.getEntity().getType() != EntityType.SNOWMAN) return;
        final String id = "@" + e.getEntity().getType().getKey();
        api.recordBlockAction(e.getBlock(), id, true);
        api.recordBlockAction(e.getNewState(), id, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(final EntityChangeBlockEvent e) {
        if (e.getBlock().getType() == Material.REDSTONE_ORE) return;
        final String id = "@" + e.getEntityType().getKey();
        api.recordBlockAction(e.getBlock(), id, true);
        if (e.getTo().isAir()) return;
        final Block block = e.getBlock();
        frontApi.recordBlockAction(id, false, block.getWorld().getName(), block.getX(), block.getY(),
            block.getZ(), e.getTo().getKey().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent e) {
        frontApi.recordChat(e.getMessage(), e.getPlayer().getUniqueId().toString());
    }

    @EventHandler
    public void onPlayerInteractEntity(final PlayerInteractEntityEvent e) {
        final Entity entity = e.getRightClicked();
        if (main.isInspecting(e.getPlayer())) {
            e.setCancelled(true);
            api.inspectEntity(e.getPlayer(), entity, true);
            return;
        }
        if (entity.getType() != EntityType.CREEPER) return;
        final Player player = e.getPlayer();
        final PlayerInventory inv = player.getInventory();
        if ((e.getHand() == EquipmentSlot.OFF_HAND ? inv.getItemInOffHand() : inv.getItemInMainHand())
            .getType() == Material.FLINT_AND_STEEL && !entity.hasMetadata(IGNITER_KEY)) entity.setMetadata(IGNITER_KEY,
            new FixedMetadataValue(plugin, player.getUniqueId().toString()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(final PlayerInteractEvent e) {
        final Player p = e.getPlayer();
        final Block b = e.getClickedBlock();
        final ItemStack item = e.getItem();
        if (b == null) return;
        if (e.getAction() == Action.PHYSICAL && b.getType() == Material.FARMLAND) {
            if (e.useInteractedBlock() != Event.Result.DENY) api.recordBlockAction(b, p, true);
            return;
        }
        if ((item != null || (e.getAction() == Action.LEFT_CLICK_BLOCK)) && main.isInspecting(p)) {
            e.setCancelled(true);
            api.inspectBlock(p, b, e.getAction() == Action.RIGHT_CLICK_BLOCK);
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.useInteractedBlock() != Event.Result.DENY) switch (b.getType()) {
            case LECTERN: {
                if (item == null || item.getType() != Material.WRITTEN_BOOK) return;
                api.recordBlockAction(b, p, true);
                return;
            }
            case REDSTONE_WIRE: {
                final RedstoneWire data = (RedstoneWire) b.getBlockData();
                final RedstoneWire.Connection c = data.getFace(BlockFace.EAST);
                if (c != data.getFace(BlockFace.NORTH) || c != data.getFace(BlockFace.SOUTH) ||
                    c != data.getFace(BlockFace.WEST)) return;
            }
            case REPEATER:
            case COMPARATOR:
            case NOTE_BLOCK:
            case DAYLIGHT_DETECTOR:
                api.recordBlockAction(b, p, true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityInteract(final EntityInteractEvent e) {
        if (e.getEntityType() != EntityType.PLAYER && e.getBlock().getType() == Material.FARMLAND)
            api.recordBlockAction(e.getBlock(), '@' + e.getEntity().getUniqueId().toString(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(final InventoryMoveItemEvent e) {

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent e) {
        ItemStack is = e.getCurrentItem();
        final Inventory inv = e.getView().getTopInventory();
        final InventoryType type = inv.getType();
        if (is == null || is.getType().isAir()) return;
        if (e.getClickedInventory() == null) return;
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
        api.inspectEntity((Player) entity, e.getEntity(), false);
    }

    @EventHandler
    public void onVehicleDamage(final VehicleDamageEvent e) {
        final Entity entity = e.getAttacker();
        if (entity == null || !main.isInspecting(entity)) return;
        e.setCancelled(true);
        api.inspectEntity((Player) entity, e.getVehicle(), false);
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
            api.inspectEntity((Player) remover, e.getEntity(), false);
        } else api.recordDeath(Utils.getEntityPerformer(remover), e.getCause().name(), e.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(final EntitySpawnEvent e) {
        final Entity entity = e.getEntity();
        if (e.getEntity() instanceof LeashHitch || (!plugin.recordEntitiesNaturalSpawn && cn.apisium.nekoguard.Constants.IS_PAPER &&
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeLecternBook(final PlayerTakeLecternBookEvent e) {
        api.recordBlockAction(e.getLectern().getBlock(), e.getPlayer(), true);
    }
}
