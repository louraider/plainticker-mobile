# Bundled fonts

The app ships both faces of the Instrument design system (DESIGN.md section 3) as static TTF
resources, so type renders the same offline and on every device. Compose maps them in
`app/src/main/java/com/plainticker/mobile/ui/theme/Type.kt` (`Outfit`, `JetBrainsMono`).

| Face | Weights | Version | Upstream | Resource files (`app/src/main/res/font/`) |
|---|---|---|---|---|
| Outfit | 400, 500, 600 | 1.100 | github.com/Outfitio/Outfit-Fonts, `fonts/ttf/` at commit `9027738` (the repository google/fonts lists as upstream; `googlefonts/outfit` does not exist) | `outfit_regular.ttf`, `outfit_medium.ttf`, `outfit_semibold.ttf` |
| JetBrains Mono | 400, 500 | 2.304 | github.com/JetBrains/JetBrainsMono release `v2.304`, `fonts/ttf/` inside `JetBrainsMono-2.304.zip` | `jetbrains_mono_regular.ttf`, `jetbrains_mono_medium.ttf` |

Static instances, not the variable fonts: the app needs three and two weights, the static files are
smaller in total, and they are not subject to variable-font weight snapping on older Android releases.

## Licenses

Both families are licensed under the SIL Open Font License 1.1. Android font resources cannot carry
a text companion in `res/font/`, so the license texts ship as assets and are packaged into the APK:

- `app/src/main/assets/licenses/outfit_ofl.txt` (Copyright 2021 The Outfit Project Authors)
- `app/src/main/assets/licenses/jetbrains_mono_ofl.txt` (Copyright 2020 The JetBrains Mono Project Authors)

## Brand mark

The launcher and notification icons (DESIGN.md section 9) no longer borrow a letter from a font.
They are the tracking gauge, three rectangles written by `design/brand/glyph.py` from the geometry
in `design/brand/marks.py`, and they ship as drawables. Until 2026-09-13 they were the "P" of
`jetbrains_mono_medium.ttf` traced into vector paths, which the OFL permits (its Reserved Font Name
clause covers only derived fonts, and none was built); nothing now depends on that reading.

## Updating

1. Download the static TTFs from the upstream paths above (a pinned commit or release tag, never a
   moving branch) together with the matching `OFL.txt`.
2. Copy them over the files listed in the table; resource names stay lowercase with underscores.
3. Run `./gradlew :app:testDebugUnitTest`; `PlainTickerThemeTest` checks the families and weights
   the theme expects.
4. Record the new version here.
