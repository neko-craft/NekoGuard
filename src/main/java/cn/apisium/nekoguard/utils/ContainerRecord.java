package cn.apisium.nekoguard.utils;

import org.jetbrains.annotations.NotNull;

public final class ContainerRecord {
    public int x, y, z;
    public String world, entity;

    public ContainerRecord(@NotNull final String world, final int x, final int y, final int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ContainerRecord(@NotNull final String entity) {
        this.entity = entity;
    }
}
