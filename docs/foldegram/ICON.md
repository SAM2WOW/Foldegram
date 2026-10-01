# Original Foldegram crane

The adaptive color foreground and white-only monochrome layer use a 108 × 108 viewport. All polygon vertices are within the centered radius-33 safe circle (largest radius about 31.96dp), with a 61 × 48dp silhouette. Straight edges also remain within that circle. The background fills the entire adaptive layer; launcher masks are not baked into either foreground. Two raised wings, a long neck, beak and tail keep the origami bird recognizable at small sizes.

Android 26+ uses separate foreground/background layers; Android 13+ additionally uses the white alpha silhouette for themed icons. The launcher chooses the actual themed tint. The SVG white variant has a transparent background. Color and white previews were inspected at 48px and 32px with circle and rounded-square masks. This is artwork validation, not launcher/device QA.

Reference: [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive), checked 2026-10-01. Run `python Tools/test_foldegram_icon.py` for geometry/resource checks.
