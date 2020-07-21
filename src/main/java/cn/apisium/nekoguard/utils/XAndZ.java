package cn.apisium.nekoguard.utils;

public final class XAndZ {
    public final int x, z;
    public XAndZ(final int x, final int z) {
        this.x = x;
        this.z = z;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final XAndZ xAndZ = (XAndZ) o;
        return x == xAndZ.x &&
            z == xAndZ.z;
    }

    @Override
    public int hashCode() {
        return x | (z << 4);
    }

    public static int getKey(final int x, final int z) {
        return x | (z << 4);
    }
}
