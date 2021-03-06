package cn.apisium.nekoguard.bukkit.changes;

import cn.apisium.nekoguard.ChangeList;
import cn.apisium.nekoguard.Constants;
import cn.apisium.nekoguard.bukkit.Main;
import cn.apisium.nekoguard.bukkit.utils.NMSUtils;
import cn.apisium.nekoguard.mappers.SeriesMapper;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Consumer;

public final class EntityChangeList extends cn.apisium.nekoguard.ChangeList {
    private final ArrayList<Entity> spawned = new ArrayList<>(mapper.count);
    private static final HashMap<String, Class<? extends Entity>> ENTITY_TYPES = new HashMap<>(EntityType.values().length);
    static {
        for (final EntityType type : EntityType.values()) if (type != EntityType.UNKNOWN)
            ENTITY_TYPES.put(type.getKey().toString(), type.getEntityClass());
    }
    public EntityChangeList(final SeriesMapper.Mapper mapper) {
        super(mapper);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void doChange(@Nullable final Consumer<cn.apisium.nekoguard.ChangeList> callback) {
        final Iterator<Object[]> iterator = mapper.all().iterator();
        Bukkit.getScheduler().runTaskTimer(Main.getPlugin(), it -> {
            int i = 0;
            while (iterator.hasNext() && i++ < 2000) {
                final Object[] arr = iterator.next();
                if (!((String) arr[2]).startsWith("@")) {
                    i--;
                    failedCount++;
                    continue;
                }
                final Class<? extends Entity> clazz = ENTITY_TYPES.get(((String) arr[2]).substring(1));
                final World world = Bukkit.getWorld((String) arr[5]);
                if (clazz == null || world == null) {
                    i--;
                    failedCount++;
                    continue;
                }
                final Entity entity = Constants.IS_PAPER
                    ? world.spawn(new Location(world, (Double) arr[6], (Double) arr[7], (Double) arr[8]), clazz, (org.bukkit.util.Consumer) null)
                    : world.spawn(new Location(world, (Double) arr[6], (Double) arr[7], (Double) arr[8]), clazz);
                NMSUtils.loadEntityData(entity, (String) arr[3]);
                spawned.add(entity);
                successCount++;
            }
            if (!iterator.hasNext()) {
                it.cancel();
                if (callback != null) callback.accept(this);
            }
        }, 0, 2);
    }

    @Override
    public void undo(@Nullable final Consumer<ChangeList> callback) {
        final Iterator<Entity> iterator = spawned.iterator();
        Bukkit.getScheduler().runTaskTimer(Main.getPlugin(), it -> {
            int i = 0;
            while (iterator.hasNext() && i++ < 50000) iterator.next().remove();
            if (!iterator.hasNext()) {
                it.cancel();
                if (callback != null) callback.accept(this);
            }
        }, 0, 2);
    }

    @Override
    public @NotNull String getName() {
        return "EntityChange";
    }
}
