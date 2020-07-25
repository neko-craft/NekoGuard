package cn.apisium.nekoguard.utils;

public final class Vector {
    public final int x, y, z;
    public Vector(final int x, final int y, final int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Vector vector = (Vector) o;
        return x == vector.x &&
            z == vector.z;
    }

    @Override
    public int hashCode() {
        return x | (z << 4) | (y << 8);
    }

    public static int getKey(final int x, final int y, final int z) {
        return x | (z << 4) | (y << 8);
    }
}
