package com.hms.util;

/**
 * Page-size cap for list endpoints. Every list takes {@code size} from the request; without a cap
 * a client could ask for {@code size=100000} and pull a whole table into memory in one call. The
 * cap is set at the largest page any current screen asks for (1000 -- some dashboards load a
 * lookup list in one call), so no screen changes; it blocks only the abusive request.
 */
public final class Pages {

    public static final int MAX_SIZE = 1000;

    private Pages() {
    }

    /** The requested page size, never above {@link #MAX_SIZE}. Lower bounds are left to PageRequest. */
    public static int cap(int size) {
        return Math.min(size, MAX_SIZE);
    }
}
