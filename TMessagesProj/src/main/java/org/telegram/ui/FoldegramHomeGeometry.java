package org.telegram.ui;

/** Window-local geometry only. Values use the caller's coordinate units (dp or pixels). */
final class FoldegramHomeGeometry {
    final int list, rail, firstX, firstWidth, secondX, secondWidth;
    FoldegramHomeGeometry(int width, int breakpoint, int railWidth, int listWidth, int gap,
                         boolean two, boolean expanded, boolean hasChat, int active) {
        width = Math.max(0, width);
        boolean wide = width >= breakpoint;
        rail = two ? Math.min(railWidth, width) : 0;
        list = expanded || !two ? (!hasChat || !wide ? (expanded || !hasChat ? width - rail : 0)
                : Math.min(listWidth, (width - rail) / 2)) : 0;
        int start = rail + list;
        int available = Math.max(0, width - start);
        boolean both = two && wide && (!expanded || available >= breakpoint);
        int gutter = both ? Math.min(gap, available) : 0;
        firstWidth = both ? (available - gutter) / 2 : !two || active == 0 ? available : 0;
        secondWidth = both ? available - gutter - firstWidth : two && active == 1 ? available : 0;
        firstX = start;
        secondX = both ? start + firstWidth + gutter : start;
    }
}
