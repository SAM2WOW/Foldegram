package org.telegram.ui;

/** Pure, window-local sizing policy; does not depend on Telegram's process-wide tablet flag. */
final class FoldegramPaneGeometry {
    private FoldegramPaneGeometry() { }

    static boolean isDualPane(int width, int minimumWidth) {
        return width > 0 && width >= Math.max(1, minimumWidth);
    }

    static int width(int totalWidth, int minimumWidth, int dividerWidth, int pane) {
        int width = Math.max(0, totalWidth);
        if (!isDualPane(width, minimumWidth)) {
            return width;
        }
        int divider = Math.min(width, Math.max(0, dividerWidth));
        int first = (width - divider) / 2;
        return pane == 0 ? first : width - divider - first;
    }

    static int left(int totalWidth, int minimumWidth, int dividerWidth, int pane) {
        if (pane == 0 || !isDualPane(totalWidth, minimumWidth)) {
            return 0;
        }
        return width(totalWidth, minimumWidth, dividerWidth, 0)
                + Math.min(Math.max(0, totalWidth), Math.max(0, dividerWidth));
    }
}
