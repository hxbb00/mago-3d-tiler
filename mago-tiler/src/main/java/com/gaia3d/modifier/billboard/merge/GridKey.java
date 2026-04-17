package com.gaia3d.modifier.billboard.merge;

public class GridKey {
    public final int x;
    public final int y;
    public final int z;

    public GridKey(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GridKey key)) return false;
        return x == key.x && y == key.y && z == key.z;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * x + y) + z;
    }

    @Override
    public String toString() {
        return "GridKey{" + "x=" + x + ", y=" + y + ", z=" + z + '}';
    }
}
