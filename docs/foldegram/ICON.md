# Approved Foldegram crane

The white crane is a six-plane vector transcription of the user-approved generated draft (2026-10-01). It retains the left-facing beak, angular neck, small interior fold, central triangle, raised rear point and broad lower wing. Fold gaps are intentionally open; there is no downward-projecting triangle below the neck. The draft was generated using a user-provided online image as visual inspiration; no claim of exclusive copyright or stock-image ownership is made.

All six planes undergo the same uniform scale and translation, with their area-weighted centroid at (54,54). Their furthest vertex is approximately 32.8dp from the center of the 108dp canvas, inside the 33dp-radius adaptive safe circle. The actual non-square silhouette spans about 52.26 × 42.62dp. This preserves its approved proportions rather than stretching it into a square. Straight edges remain inside the safe circle. Background and foreground remain separate; masks are supplied by Android.

The standard foreground and Android 13+ monochrome layer share identical opaque white paths with transparent fold gaps. The launcher chooses themed tint. Pre-Android 8 and call/notification fallback vectors use the same crane with a full ink background. Both SVGs match the production polygons; the white SVG has a transparent background.

Circle and rounded-square previews were inspected at large size, 48px and 32px. This validates artwork, not device rendering. Run `python Tools/test_foldegram_icon.py` for geometry/resource checks. Reference: [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), checked 2026-10-01.
