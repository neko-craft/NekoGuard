package cn.apisium.nekoguard.utils;

public final class KeyedChunk {
    public final String world;
    public final int x, z, hash;
    public KeyedChunk(final String world, final int x, final int z) {
        this.world = world;
        this.x = x >> 4;
        this.z = z >> 4;
        hash = getKey(world, this.x, this.z);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return hash == ((KeyedChunk) o).hash;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    public static int getKey(final String world, final int x, final int z) {
        return (world + "|" + x + "|" + z).hashCode();
    }
}
