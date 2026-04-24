---
name: java-ref
description: Map a BS2BG porting topic (slider math, formatters, TableFilter, controllers, NPC import, settings, morphs) to the exact Java reference files under src/com/asdasfa/jbs2bg/. Use before porting any feature so the C# implementation follows the authoritative Java logic.
---

# java-ref

When the user asks you to port, verify, or debug a feature in BS2BG, consult the Java reference at `src/com/asdasfa/jbs2bg/` before writing C#. Use `$ARGUMENTS` as the topic if provided, otherwise ask which area the user means.

## Topic → reference files

| Topic | Files |
|---|---|
| Slider math, interpolation, rounding | `MainController.java` (search for `Math.round`, `BigDecimal`, `interpolate`), `data/SliderPreset.java` |
| `templates.ini` / `morphs.ini` writers | `MainController.java` (look for `BufferedWriter` / `PrintWriter` usage and `"\r\n"`) |
| BoS JSON export | `MainController.java` (search `minimal-json`, `Json.object()`, `Json.array()`), `data/MorphTarget.java` |
| Settings / profile JSON load + defaults | `data/Settings.java`, root `settings.json`, `settings_UUNP.json` |
| NPC model + pipe-delimited import | `data/NPC.java`, `PopupNpcDatabaseController.java` |
| TableFilter (custom control to re-implement) | `controlsfx/table/TableFilter.java`, `ColumnFilter.java`, `FilterPanel.java`, `FilterValue.java`, `DupeCounter.java`, `Localization.java` |
| Custom morphs | `data/CustomMorphTarget.java`, `data/MorphTarget.java`, `PopupSetSlidersController.java`, `SetSliderControl.java` |
| Project file (`.jbs2bg`) load/save | `data/Data.java`, `MainController.java` (search `saveProject` / `loadProject`) |
| Headless test harness | `testharness/FixtureDriver.java` |
| Keyboard navigation / hotkeys | `etc/KeyNavigationListener.java`, `MainController.java` |
| Utility helpers (chardet, file I/O) | `etc/MyUtils.java` |
| Popup controllers (About, Rename, BoS View, etc.) | `Popup*Controller.java` siblings of `MainController.java` |

## How to use

1. Open the listed files (Read tool, no offset is usually fine — they're small except `MainController.java` which is ~2000 lines; use Grep first there).
2. Quote the relevant Java logic in your response so the user can verify parity.
3. Call out any Java idioms that don't translate cleanly: `BigDecimal` rounding modes, `DecimalFormat`, `Preferences` API, JavaFX binding, etc. Cross-reference with `PRD.md` §7 (risks) if relevant.
4. Ignore `src/jfx-8u60-b08/` — it's an embedded OpenJFX source snapshot, not live code.
