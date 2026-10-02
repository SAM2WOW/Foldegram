# Foldegram wordmark

Approved option B: **Bricolage Grotesque**, weight **650**, width **100**, optical size **24**. The approved crane polygons are unchanged.

The official Google Fonts source is pinned to commit `6ce172f74aa355ea43eb964fa4a91570a4d3064d`:

- [Variable font and source directory](https://github.com/google/fonts/tree/6ce172f74aa355ea43eb964fa4a91570a4d3064d/ofl/bricolagegrotesque)
- Original variable TTF SHA-256: `413e7357809ddd12fd80a96a8a396de0e401638d4acd3cb3e37532f0472ac682`
- Copyright 2022 The Bricolage Grotesque Project Authors. Font software, including the derived static instance, remains under [SIL OFL 1.1](../../TMessagesProj/src/main/assets/fonts/BricolageGrotesque-OFL.txt), not the client's GPL. The complete original license/copyright file is packaged beside the font in APK assets.

`Tools/generate_foldegram_wordmark.py` takes the official variable TTF as an argument, verifies its SHA-256, creates a static 650/100/24 instance with fontTools, and shapes “Foldegram” using HarfBuzz. It emits the Android vector lockup and outlined README SVG. The static instance works on the client's older supported Android versions without variable-font APIs. Regeneration requires fontTools and uharfbuzz.

The app name alone uses this face in the home header, welcome page and Settings/About version label. Story counts, upload status, conversations, messages and ordinary controls keep native Telegram typography. The collapsed-stories header shows the outlined crane/wordmark when it fits and falls back to the original crane when space is constrained. The narrow rail remains icon-only. The outlined SVG makes the GitHub README independent of remote webfont loading.
