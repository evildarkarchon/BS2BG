# PRD: Bodyslide to Bodygen (BS2BG) — C# / Avalonia MVVM Port

**Full name:** Bodyslide to Bodygen
**Short name:** BS2BG
**Author (this port):** evildarkarchon
**Original author (Java jBS2BG, credited):** Totiman / asdasfa
**Source of truth for parity:** Java/JavaFX 8 implementation `jBS2BG` v1.1.2 in `src/com/asdasfa/jbs2bg`.
**Target stack:** .NET 9, C# 13, Avalonia 11 (MVVM via CommunityToolkit.Mvvm), Fluent theme + custom skin.
**Primary host OS:** Windows 10/11 (app is Skyrim/Fallout 4 modder tooling). Linux/macOS builds are free by virtue of Avalonia; ship Windows binaries first.

---

## 1. Background

**Bodyslide to Bodygen** (BS2BG) is a desktop tool for Skyrim/SSE and Fallout 4 modders. It converts BodySlide preset XML files into the `templates.ini` and `morphs.ini` files consumed by the **BodyGen** system (which randomizes NPC body shapes at runtime via RaceMenu / ECE morphs in Skyrim, and LooksMenu in Fallout 4). It also emits BodyTypes of Skyrim (BoS) JSON files. The app maintains a project file (`*.jbs2bg`) containing the user's imported slider presets, custom morph targets, and NPC-to-preset assignments.

This C#/Avalonia rewrite by **evildarkarchon** is a port of the original Java/JavaFX **jBS2BG** by **Totiman / asdasfa** (v1.1.2). All data formats and operational semantics are preserved from that version; see §2 and §4 for the parity contract. Credit to the original author is retained in the About dialog, the `README`, and the source headers.

The current Java 8 + JavaFX implementation works but has several friction points:

- JavaFX 8 bundled JRE, aging `dark.css`, Swing-era dialog patterns.
- Non-responsive two-tab layout with fixed `prefWidth`/`prefHeight` on every node.
- Business logic and UI state are coupled inside a single ~2,000-line `MainController`.
- File dialogs, threading, and progress are stitched together with ad-hoc `javafx.concurrent.Task` wrappers.
- No undo/redo, no search inside preset/target lists, limited keyboard-centric workflow.

Porting is an opportunity to (a) drop the JRE dependency, (b) cleanly separate model/view, and (c) modernize the UX without losing feature parity or changing the on-disk file formats.

## 2. Goals / Non-goals

### Goals

1. **Feature parity.** Every action reachable in v1.1.2 must be reachable in v2.0, including all menu items, popups, keybinds, and file format round-trips.
2. **Backward-compatible file formats.** `.jbs2bg` JSON, BodySlide `SliderPresets` XML input, BoS JSON output, and `templates.ini` / `morphs.ini` output must be byte-equivalent (modulo documented sort order) to the Java app's output for the same input. This is a hard requirement — modders share project files and downstream tools parse the INIs.
3. **Proper MVVM.** No code-behind business logic. ViewModels hold state, services handle I/O, views bind.
4. **Improved UI.** See §5 — a single-window workflow with better affordances for the new-user "I have a pile of XMLs, now what?" scenario.
5. **Self-contained single-file executable.** `dotnet publish -r win-x64 --self-contained` produces one `.exe`; no installer, no JRE.

### Non-goals

- **No new body-morph math.** CBBE/UUNP defaults, inverted sliders, multipliers, and the percent-range interpolation are ported verbatim from `Settings.java` and `SliderPreset.toLine` / `toBosJson`.
- **Not a BodySlide replacement.** We don't parse `.nif`, don't render 3D, don't write BodySlide XML back out.
- **No cloud sync, no account system, no telemetry.**
- **No mobile.**

## 3. Users & primary flows

**Audience:** Skyrim / Skyrim SE modders comfortable editing INIs. Typical session is 30–90 minutes of bulk data work.

**Flow A — First-time template generation:**
1. Open app → `File › New`.
2. "Add BodySlide XML Presets" → multi-select XMLs → app parses, shows presets in the left list.
3. (Optional) Select a preset → "Edit SetSliders" → tweak min/max/enabled → back out.
4. (Optional) Toggle UUNP per preset; toggle "Omit Redundant Sliders" globally.
5. "Generate Templates" → copy output or `File › Export BodyGen INIs`.

**Flow B — NPC assignment:**
1. Open a saved `.jbs2bg` project.
2. Morphs tab → "Add NPCs" → load a `Mod|Name|EditorID|Race|FormID` text dump (produced by xEdit scripts) → optionally "Assign Random".
3. For specific NPCs, select row → click presets from "Slider presets for: …" list (or use "Fill Empty" to bulk-assign).
4. "Generate Morphs" → export INIs.

**Flow C — Custom morph targets:**
1. Morphs tab → type `All|Female|NordRace` in the target textbox → "Add".
2. Assign presets as in Flow B.

## 4. Feature-by-feature parity spec

This is the authoritative checklist for the port. Each row must be implemented; the existing Java source is the canonical behavior spec when this document is ambiguous.

### 4.1 Menu bar

| Java item                              | Ported command                 | Keybind        | Notes |
| -------------------------------------- | ------------------------------ | -------------- | ----- |
| File › New                             | `NewProjectCommand`            | `Ctrl+N`       | Unsaved-changes confirm dialog when `IsDirty`. |
| File › Open…                           | `OpenProjectCommand`           | `Ctrl+O`       | Same confirm dialog. Load via `ProjectFileService`. |
| File › Save                            | `SaveProjectCommand`           | `Ctrl+S`       | Prompts for path if no current file. |
| File › Save As…                        | `SaveProjectAsCommand`         | `Ctrl+Alt+S`   | Java used `Ctrl+Shift+S` via `ALT_DOWN` — keep Shift. |
| File › Export Templates as BoS JSON    | `ExportBosJsonCommand`         | `Ctrl+B`       | One JSON file per preset in a chosen directory. |
| File › Export BodyGen INIs             | `ExportBodyGenInisCommand`     | `Ctrl+X`       | Writes `templates.ini` + `morphs.ini` with CRLF line endings. |
| Help › About Bodyslide to Bodygen      | `ShowAboutCommand`             | —              | About dialog must credit the original author (Totiman / asdasfa) for jBS2BG and list evildarkarchon as the port author. Window title: `Bodyslide to Bodygen (BS2BG)`. |

### 4.2 Templates tab

| Feature | Notes |
| --- | --- |
| Add BodySlide XML Presets | Multi-file picker, parse each `<Preset>` under `<SliderPresets>`, extract `<SetSlider>` children. Duplicate names update the existing preset (`clearAndCopySliders`). Run on background thread with busy overlay. |
| Preset list | Shows preset names, alphabetical, case-insensitive sort. Selecting binds `SelectedPreset`. |
| Rename | Popup; validates uniqueness. |
| Duplicate | Appends `(Dupe)` to name; error-notif if clash. |
| Remove | Confirms only if the preset is referenced by any target/NPC. |
| Clear | Confirms; cascades removal from all targets/NPCs. |
| UUNP checkbox | Per-preset flag; flipping it recomputes "missing default sliders" using `DEFAULTS_UUNP`. |
| Omit Redundant Sliders | Global toggle, persisted via user prefs. When on, excludes sliders whose small==big==0 (non-inverted) or ==100 (inverted). Toggling clears the generated-templates textarea. |
| Template preview textarea | Updates live on preset selection / UUNP toggle / omit-toggle. Shows `presetName=slider1@min:max, slider2@...`. Monospaced. |
| Generated templates textarea | Produced by `GenerateTemplatesCommand`; one line per preset. |
| Copy | Copies generated textarea to clipboard; notif on empty. |
| Edit SetSliders popup | Modal. Per-slider row with enabled checkbox, two sliders (min% / max%) and `tfBgFormat` live preview. Batch buttons: 0/50/100 All · Min · Max. |
| View BoS JSON popup | Resizable; shows `sliderPreset.toBosJson()` output with copy button. |

### 4.3 Morphs tab

| Feature | Notes |
| --- | --- |
| Custom morph targets list | Add by typing `Context|Gender[|Race[Variant]]` into the textbox. Tooltip must enumerate examples (`All|Female`, `All|Female|NordRace`, `All|Female|BretonRace`, `All|Female|NordRaceVampire`). Adding gives a random preset if any exist. |
| Remove / Clear custom target | Clear confirms. |
| Individual NPCs table | Columns: Name, Master, Race, EditorID, FormID, Slider Presets (joined by `\|`). Sort is preserved; selection scrolls to selected. |
| Column filter | Port the ControlsFX-style `TableFilter` behavior — per-column dropdown with value checklist and search. `DataGrid` in Avalonia doesn't have this out-of-box; plan to implement as a custom attached behavior (details in §7). |
| NPC count badge | `(n)` next to the "Individual NPCs:" label, live-updating. |
| Add NPCs popup | Loads a text file (`Mod|Name|EditorID|Race|FormID` per line; charset auto-detected in Java via juniversalchardet — port with `UTF8`/`Default` fallback + BOM detection). De-dupes by `(mod, editorId)` case-insensitive. Optional "Assign Random" checkbox. "View Image" opens the floating image window. |
| Slider Presets target panel | "Slider presets for: <selected target>" + preset count with color-coded thresholds (`<31` neutral, `<77` orange, else red — 77+ historically crashed the game's main menu, keep the warning). |
| Add / Add All / Remove / Clear | Unchanged semantics. |
| Fill Empty popup | Only shown when at least one visible (filtered) NPC has no presets. Offers to bulk-assign one or more presets randomly per empty NPC. |
| Clear Assignments | Confirms; clears presets from visible (filtered) NPCs. |
| Remove NPC | Remove-only, no confirm (parity). |
| Image view popup | Always-on-top floating window. Looks up `images/<Name> (<EditorId>).{jpg,jpeg,png,bmp}` then falls back to `images/<Name>.<ext>`. Empty / missing → blank. |
| Generate Morphs | Lines: `mod|formId=preset1|preset2|...` for NPCs (sorted by mod), `name=preset1|preset2|...` for custom targets. After run, pop "Targets without presets" notifier for any empty ones. |
| Copy morphs | Clipboard. |

### 4.4 Project file (`.jbs2bg`) format

**Unchanged.** JSON root with three objects: `SliderPresets`, `CustomMorphTargets`, `MorphedNPCs`. Pretty-printed (`WriterConfig.PRETTY_PRINT` → System.Text.Json `WriteIndented = true` with 2-space indent).

Serialization rules to preserve:

- Each SliderPreset has `isUUNP` (bool) and `SetSliders` (array of objects).
- A SetSlider with both `valueSmall` **and** `valueBig` set to JSON `null` is treated as a "missing default" slider (materialized on load from `DEFAULTS` / `DEFAULTS_UUNP`).
- On save, missing-default sliders are **omitted** if `pctMin == 100 && pctMax == 100 && enabled` (i.e., no user customization).
- NPC keys are the display name; body has `Mod`, `EditorId`, `Race`, `FormId`, `SliderPresets` (array of preset names).
- CustomMorphTarget keys are the target name; body has `SliderPresets` array.
- On load, preset references in targets/NPCs that don't match an existing SliderPreset are silently dropped (Java behavior).

### 4.5 Settings files (body/game profiles)

The v1 app ships two profile files next to the executable: `settings.json` (labeled "CBBE", implicitly Skyrim) and `settings_UUNP.json` (Skyrim UUNP). The in-app toggle is a single per-preset "UUNP" checkbox, which swaps which profile is used for default/multiplier/inverted lookup.

Schema (unchanged):

```json
{
  "Defaults":    { "<SliderName>": { "valueSmall": <0-1>, "valueBig": <0-1> }, ... },
  "Multipliers": { "<SliderName>": <float>, ... },
  "Inverted":    [ "<SliderName>", ... ]
}
```

v1 behavior, ported verbatim for backward compatibility:

- If either file is missing, it is written on first launch using the hardcoded defaults in `Settings.init()`.
- If **either** file is malformed JSON, app shows a modal error and exits (parity with `initSuccess` return codes 0 / -1 / -2).
- Values are read through `Settings.getDefaultValueSmall/Big`, `getMultiplier`, `isInverted` — port these as methods on a profile record owned by `ISettingsService`.

**v2 extension: generalize to N profiles (addresses the Fallout 4 gap).**

The hardcoded two-profile assumption is a v1 bug: the app will load and output nonsense for Fallout 4 CBBE XMLs because the SSE slider namespace (`Breasts`, `Butt`, `Legs`, `Arms`, ...) does not overlap with the FO4 CBBE namespace (`BreastCenterBig`, `ButtNew`, `ShoulderTweak`, `HipBack`, `ChubbyWaist`, ...). The port should replace "is UUNP: yes/no" with a named profile selector.

v2 settings layout on disk (alongside the executable, in a `profiles/` subfolder):

```
profiles/
  skyrim-cbbe.json           # renamed from settings.json; auto-migrated on first launch
  skyrim-uunp.json           # renamed from settings_UUNP.json
  fallout4-cbbe.json         # new, seeded with FO4-appropriate defaults (see §7.7)
  <user-custom>.json         # user-added profiles
```

Each profile file gains a top-level `"Name"` and `"Game"` field:

```json
{
  "Name": "Skyrim CBBE",
  "Game": "SkyrimSE",
  "Defaults":    { ... },
  "Multipliers": { ... },
  "Inverted":    [ ... ]
}
```

v1 files without those fields are accepted (Name defaults to the filename stem, Game defaults to `"SkyrimSE"`). On first launch, if `profiles/` does not exist, seed it from `settings.json`/`settings_UUNP.json` if present, or from hardcoded defaults otherwise, then optionally offer to delete the legacy files.

**Project-file impact.** The `.jbs2bg` JSON currently stores `"isUUNP": true/false` on each SliderPreset. Keep that field for round-trip compatibility, but also persist `"Profile": "<name>"` (new, optional). On load:

- If `Profile` is present, use it.
- Else if `isUUNP` is true, use `Skyrim UUNP`.
- Else use `Skyrim CBBE`.

On save, emit **both** for maximum compatibility: `isUUNP` mirrors whether the profile is the Skyrim UUNP one, and `Profile` carries the exact name. Old versions of v1 will ignore `Profile` and fall back to `isUUNP`.

**UI impact.** Replace the lone "UUNP" checkbox on the Templates tab with a profile dropdown per selected preset. Default selection for a newly-imported XML is whatever profile the user last picked (sticky in user prefs).

### 4.6 User preferences

Java stored last-used folders + `Omit Redundant Sliders` in `java.util.prefs.Preferences` under a path derived from the JAR location. Port to `%APPDATA%\BS2BG\user.json` (simple record serialized with `System.Text.Json`). Keys: `LastUsedFolder`, `LastUsedPresetFolder`, `LastUsedNpcFolder`, `LastUsedIniFolder`, `LastUsedJsonFolder`, `OmitRedundantSliders`.

### 4.7 Slider math — must match byte-for-byte

From `SliderPreset.SetSlider.toText()` and `toBosJson()`:

- Values are stored as integer percentages 0..100 and divided by 100 for math.
- If inverted: `small = 1 - small; big = 1 - big`.
- `diff = big - small; min = small + diff * pctMin/100; max = small + diff * pctMax/100`.
- Multiply by `multiplier` (default 1.0).
- Round to **2 decimal places, ROUND_HALF_UP** (use `Math.Round(x, 2, MidpointRounding.AwayFromZero)`; validate against Java `BigDecimal.ROUND_HALF_UP` with edge cases in tests).
- `toText()` formats as `name@max` when min==max, else `name@min:max`.
- BoS JSON emits `bodyname`, `slidernameN`, `highvalueN`, `lowvalueN`, `slidersnumber` keys. Order of entries matches insertion order (keep `Dictionary<string, T>` with deterministic ordering — Java uses `LinkedHashMap`).

### 4.8 Window/dialog parity

Port the following modals (all child windows, owner = main, modal where noted):

- About (modal, not resizable, 400×200).
- SetSliders (modal, 590×600, not resizable).
- BoS View (modal, resizable, 600×400).
- SliderPresets "Add" picker (modal, 410×460).
- SliderPresetsFill (modal, 410×460).
- NPC Database (modal, 600×400, resizable).
- Rename (modal, 400×150).
- ImageView (non-modal, always-on-top, resizable).
- "No Preset" notifier (non-modal, always-on-top).

Startup size 900×600, min same; resizable; dark-theme stylesheet.

## 5. UI improvements (v2 scope)

Goal: **keep every existing control**, but reorganize the flow so a newcomer knows what to do and so power users get better density/keyboarding. No game-logic changes.

### 5.1 Layout: single-window workspace instead of two disconnected tabs

Replace the JavaFX `TabPane` with a **three-pane main window**:

```
┌──────────────────────────────────────────────────────────────────────┐
│ Menu bar  │ New Open Save │ Export ▾ │               Search [ ____ ] │
├─────────────┬──────────────────────────────────┬─────────────────────┤
│             │                                  │                     │
│ PRESETS     │   WORKSPACE (context-sensitive)  │  INSPECTOR          │
│  + XML...   │                                  │                     │
│  [list]     │   • Preset selected → shows      │  SetSliders editor  │
│  ▸ preset A │     live template line + big     │  inline (no popup)  │
│  ▸ preset B │     "Generate & Copy" button     │                     │
│             │                                  │  OR                 │
│ TARGETS     │   • Target/NPC selected → shows  │                     │
│  NPCs       │     assigned presets, image,     │  NPC details +      │
│  [table]    │     quick-assign picker          │  image preview      │
│  Custom     │                                  │                     │
│  [list]     │   • Nothing selected → shows     │                     │
│             │     "Generated output" view      │                     │
│  + Add ▾    │     with templates + morphs      │                     │
│             │     side-by-side                 │                     │
├─────────────┴──────────────────────────────────┴─────────────────────┤
│ Status: 42 presets · 128 NPCs · 3 custom · ✎ Unsaved  │ UUNP  Omit   │
└──────────────────────────────────────────────────────────────────────┘
```

Rationale:
- The tab flip in v1 hides the preset list while editing NPCs, which is exactly when users want to glance at it. Collapsing both lists into a persistent left rail fixes that.
- The SetSliders editor is one of the most-used screens — elevate it from a modal popup to the inspector.
- The BoS JSON viewer becomes a tab inside the inspector (not a separate window).

### 5.2 Specific UX wins

- **Global search.** `Ctrl+F` focuses a search box that filters the currently visible list (presets / targets / NPCs) by name or — for NPCs — by mod/EditorID/FormID. Replaces the idiosyncratic type-ahead key-navigation code in `KeyNavigationListener`.
- **Drag-and-drop.** Drop `.xml` files on the preset list to import. Drop a `.jbs2bg` file anywhere in the window to open. Drop a text file on the NPC table to import NPCs.
- **Command palette.** `Ctrl+Shift+P` opens a palette listing every command in §4.1 and the context actions (`Rename`, `Duplicate`, etc.). Low cost with Avalonia; high payoff for modders with mouse-fatigue.
- **Inline validation.** The custom-target textbox currently only validates on Add. Port a live indicator (green check / red X) that parses `All|Female|NordRace` against a simple grammar as the user types, and replays the tooltip examples as inline placeholder text.
- **Multiselect in NPC table.** Java uses `SelectionMode.SINGLE`. Allow multi-select + "Assign preset(s) to selected" from the inspector. Also enables right-click → "Clear assignments on selected".
- **Undo/redo.** Implement a simple `ICommand`-based undo stack at the ViewModel level covering: add/remove/rename preset, add/remove custom target, NPC preset assignment changes. Bind to `Ctrl+Z` / `Ctrl+Y`. This is cheap if we commit to using `CommunityToolkit.Mvvm` relay commands from day 1.
- **Preset count warning becomes actionable.** v1 colors the count red at 77+. v2 shows a warning banner with a "Why?" tooltip linking to the BodyGen limit note, and offers a "Trim to 76" command that keeps the N most-used presets.
- **Dark/Light/System themes.** Avalonia's `FluentTheme` variants + a custom `SolidColorBrush` palette matching the original dark scheme. Pick up OS setting by default.
- **High-DPI.** Avalonia handles this natively; audit any hardcoded pixel offsets from the Java window-centering logic when porting.
- **Accessibility.** Tab order audit, `AutomationProperties.Name` on every interactive control, Enter/Space activation. The v1 controls are mostly keyboard-reachable but inconsistent; tighten in v2.

### 5.3 Visual design direction

- Fluent + mild neutrality: dark charcoal background, a single accent color, generous padding (12 px default, 8 px dense tables).
- Use icons for menu-bar actions (Fluent icons package). Text-only menus remain in the menu bar.
- Replace the v1 "color-coded text tint" on preset count with a pill-shaped badge (`neutral / warn / error`).
- Inline image preview (thumbnail) in the NPC table row when an image file is found, like tracker apps. Full preview stays in a dedicated inspector panel.
- Monospace font family: **JetBrains Mono** (bundled), fallback Consolas → Cascadia Mono. The v1 textareas pin to Consolas 12.

### 5.4 Out of scope for v2.0 (collect as follow-ups)

- In-editor XML diffing between two presets.
- Built-in xEdit integration / ESP/ESM parsing.
- 3D preview of slider output.
- Auto-download of defaults / community preset packs.
- Project auto-backup / revision history.

## 6. Architecture

### 6.1 Solution layout

```
/BS2BG.sln
  /src
    BS2BG.Core/           netstandard2.1   # pure domain + file formats, no UI deps
      Models/             SliderPreset, SetSlider, NPC, CustomMorphTarget, Project
      Services/           ISettingsService, IPreferencesService, IProjectFileService,
                          IBodySlideXmlParser, IBodyGenIniWriter, IBosJsonWriter
      Formatting/         SliderMathFormatter (the %/invert/multiplier/round code)
    BS2BG.App/            net9.0           # Avalonia, MVVM
      Views/              *.axaml
      ViewModels/         MainWindowViewModel, PresetListViewModel, ...
      Controls/           FilterableDataGrid (custom), PercentSlider, ...
      Resources/          themes, icons, fonts
    BS2BG.Tests/          net9.0           # xUnit + snapshot
      FormattingTests     # golden files from Java output for same XML inputs
      ProjectRoundTripTests
```

**Why split Core out?** The slider math is the load-bearing thing for modders. Keeping it in an assembly with zero UI dependency makes it trivial to snapshot-test against Java output and — later — to ship a headless CLI (see §10).

### 6.2 MVVM conventions

- `CommunityToolkit.Mvvm` `[ObservableProperty]` + `[RelayCommand]` source generators.
- `IMessenger` for cross-ViewModel notifications (e.g., "preset removed → NPC ViewModels drop it").
- All file I/O goes through services injected via `Microsoft.Extensions.DependencyInjection`.
- No `Dispatcher.UIThread.InvokeAsync` sprinkled in ViewModels: services return `Task<T>`, the command awaits, binding updates on the UI thread via the `SynchronizationContext` set by Avalonia.

### 6.3 Threading

Java uses `javafx.concurrent.Task` + a `mainPane.setDisable(true)` pattern for XML parsing, generate, save, export. Port as:

- `IAsyncRelayCommand` with `CanExecute = !IsBusy`.
- Root ViewModel exposes `IsBusy` + `BusyMessage` bound to a glass overlay.
- Progress for multi-file operations via `IProgress<T>` + determinate progress bar.

### 6.4 Data model mapping

| Java | C# | Notes |
| --- | --- | --- |
| `com.asdasfa.jbs2bg.data.Data` | `ProjectModel` (record class w/ `ObservableCollection<T>`) | Single source of truth. |
| `SliderPreset` | `SliderPreset` | `ObservableCollection<SetSlider>`; `IsUunp` triggers missing-default refresh. |
| `SliderPreset.SetSlider` (inner class) | `SetSlider` (top-level) | Moving it out simplifies binding. Carries a reference to `Parent.IsUunp` via weak ref or a getter on the parent; prefer passing `isUunp` into `ToText` / `ToBosJson` rather than storing state. |
| `MorphTarget` (abstract) | `MorphTargetBase` | Shared `Name`, `SliderPresets`, `ToLine()`. |
| `NPC` | `Npc : MorphTargetBase` | |
| `CustomMorphTarget` | `CustomMorphTarget : MorphTargetBase` | |
| `Settings` (static) | `SettingsService` (singleton DI) | |
| `javafx.util.prefs.Preferences` | `PreferencesService` (JSON @ AppData) | |

### 6.5 File-format libraries

- JSON: `System.Text.Json` with a custom `JsonSerializerOptions`:
  - `WriteIndented = true` and a 2-space `IndentCharacter`.
  - `DefaultIgnoreCondition = Never` (need to emit `"valueSmall": null`).
  - Property ordering via `JsonPropertyOrder` attributes so round-tripped files diff cleanly.
- XML: `XDocument`. BodySlide XMLs are small; DOM is fine.
- INI output: plain string builder, `\r\n` line endings (match Java's `System.lineSeparator()` on Windows).

## 7. Implementation risks & mitigations

1. **`ControlsFX` TableFilter has no Avalonia equivalent.** Mitigation: implement a `FilterableDataGrid` attached behavior — column header menu with a per-column search box and checklist sourced from distinct values. Track column predicates in an `ObservableCollection<ColumnPredicate>` and compose into a `CollectionView`-style filter. Budget 2–3 days; ship a minimal version first (search-only) and add checklist in a later patch if needed.
2. **Byte-identical file output — dual float formatting.** The Java code
   emits floats through **two different formatters depending on output
   context**, and a port that uses one helper for both will fail golden-file
   tests. Confirmed empirically via the fixture corpus:

   - **templates.ini, morphs.ini, preview textarea** → built via Java string
     concatenation (`name + "@" + floatValue`), which calls
     `Float.toString(f)`. **Keeps `.0` on integer-valued floats**:
     `0.0f → "0.0"`, `1.0f → "1.0"`, `0.6f → "0.6"`, `1.25f → "1.25"`.
   - **BoS JSON export** → goes through
     `com.eclipsesource.json.JsonObject.add(String, float)`, which invokes
     minimal-json 0.9.5's `cutOffPointZero` helper. **Strips `.0` from
     integer-valued floats**: `0.0f → 0`, `1.0f → 1`, `0.6f → 0.6`.

   Mitigation: `BS2BG.Core.Formatting` must ship two distinct formatters
   (`JavaFloatLikeString` for the text pipeline, `MinimalJsonLikeNumber`
   for BoS JSON). Neither `float.ToString("G9")` nor `System.Text.Json`
   matches the text-pipeline rule out of the box. Snapshot tests must
   cover `0.0`, `1.0`, `0.2`, `0.6`, `0.75`, `1.25`, and negatives on
   both formatters.
3. **Rounding parity.** `BigDecimal.ROUND_HALF_UP` ≠ default `Math.Round` (banker's rounding). Use `MidpointRounding.AwayFromZero`, add unit tests for `.005`, `-0.005`, etc.
4. **Character detection for NPC text dumps.** Java uses juniversalchardet. For .NET, use `UTF8` with `BOM detection → try UTF-8 → fallback to `Encoding.Default` (Windows-1252 on western Windows). Add a silent "fell back" indicator so power users can re-save as UTF-8.
5. **DataGrid performance at 5k NPCs.** Avalonia `DataGrid` virtualizes by default — confirm with a seed dataset early. If it struggles with filter toggling, cache filter results and swap the `ItemsSource` instead of re-filtering inline.
6. **Image file discovery.** v1 looks in `./images/` relative to CWD. Confirm modders actually use that convention — they do. Keep the same convention; don't migrate to AppData.
7. **Fallout 4 slider namespace seed.** Seed `fallout4-cbbe.json` with the slider names observed in `Fallout 4/Data/Tools/BodySlide/SliderPresets/CBBE.xml` that the Java app would have treated as "missing defaults." Concrete seeding plan: enumerate the union of slider names across every `<Preset>` in FO4 CBBE.xml (including `Ankles`, `AppleCheeks`, `Arms`, `BackArch`, `Belly`, `BreastCenter`, `BreastCenterBig`, `BreastGravity`, `BreastGravity2`, `BreastHeight`, `BreastTopSlope`, `Breasts`, `BreastsTogether`, `Butt`, `ButtNew`, `CalfSize`, `ChubbyWaist`, `CrotchBack`, `HipBack`, `LegShape`, `NippleDown`, `NipplePerkiness`, `ShoulderTweak`, `ShoulderWidth`, `Thighs`, `Waist`, `WaistHeight`, `BreastsCleavage`, `NipBGone`, `PushUp`, `BreastFlatness2`, ...). Default `valueSmall`/`valueBig` to `1.0`, multipliers to `1.0`, inverted list empty. Authoring the "correct" FO4 inverted/multiplier sets is out of v2.0 scope — ship the file empty and let users tune it. Document that the FO4 profile is "best effort."
8. **Negative slider values.** Real CBBE presets contain negative input values (`BreastHeight@-25`, `BreastWidth@-50`, `AreolaSize@-30`, `Belly@-25`). The Java math pipeline divides by 100, optionally inverts (`1 - x`), optionally multiplies, and rounds with `ROUND_HALF_UP`. Negative values flow through unchanged. Add explicit negative-value cases to the golden-file corpus (`CBBE Fetish`, `CBBE Chubby`, `CBBE Curvy (Outfit)` from the real CBBE.xml are good candidates).

## 8. Testing strategy

- **Golden-file tests (must-have).** Commit a `tests/fixtures/` directory with real BodySlide XMLs and the Java app's output for the same inputs:
  - **Skyrim CBBE** — `E:\Mod Organizer\SSE Mods\Caliente's Beautiful Bodies Enhancer -CBBE-\CalienteTools\BodySlide\SliderPresets\CBBE.xml` (14 presets, includes negative values, sparse sliders, the "- Zeroed Sliders -" edge case, and nested-name cases like `CBBE Curvy (Outfit)`).
  - **Fallout 4 CBBE** — `E:\SteamLibrary\steamapps\common\Fallout 4\Data\Tools\BodySlide\SliderPresets\CBBE.xml`. This input is an excellent stress test precisely because almost every slider name in it is *absent* from the Skyrim-CBBE defaults table, so it exercises the missing-default injection path end-to-end.
  - **Skyrim UUNP** — hunt down a UUNP-flavored `SliderPresets\*.xml` from the MO2 mods list (the CBBE-only directories won't have one). If none is available, synthesize a small fixture manually and document that it's hand-authored.
  - Output corpus per input: `.jbs2bg` save file, `templates.ini`, `morphs.ini` (requires dummy NPC data — include a `tests/fixtures/npcs.txt` dump of 3–5 synthetic NPCs), and BoS JSON files for each preset.
  - **Generate the expected output with the existing Java build before touching C#.** Run the Java app against each fixture, save/export, commit results. Ideally keep the Java build runnable in the repo so regenerating the corpus is one-command reproducible when a slider-math tweak lands.
  - Port tests assert byte-identical C# output. Failing diffs get reviewed case-by-case — some differences (e.g., float formatting minutiae) may require either matching Java exactly or documenting an intentional, tested divergence.
- **ViewModel unit tests.** Target `BS2BG.App.ViewModels` with xUnit; stub services.
- **UI smoke tests.** Avalonia.Headless + `UITest` to cover: open project, add XML, generate templates, assert preview text.
- **Manual QA matrix.** Windows 10 + 11, 100% / 125% / 150% DPI, dark + light theme. Linux/macOS are "builds and launches" only for v2.0.

## 9. Milestones

| # | Scope | Exit criteria | Notes |
| - | ----- | ------------- | ----- |
| M0 | Project scaffolding: solution, DI, theme, empty main window, `BS2BG.Core` with `SliderMathFormatter` ported + golden tests green. | `dotnet test` passes against Java output on reference fixtures. | Hardest risk retired first. |
| M1 | Project model + `.jbs2bg` round-trip. | Can open every v1-saved fixture and save it identically. | |
| M2 | BodySlide XML import + preset list + template preview + generate templates. | Flow A works end-to-end. | Uses v1 FXML layout as visual reference, not as final. |
| M3 | Morphs tab equivalents: custom targets, NPC table with basic filter, assignment flows, generate morphs. | Flow B + C work. | Basic filter = search-only. |
| M4 | SetSliders editor as inspector panel. BoS JSON view. Image view. "No preset" notifier. | Feature parity with v1 popups. | Pop the SetSliders into a secondary window if inspector space is too tight. |
| M5 | Export commands (BoS JSON, BodyGen INIs), unsaved-change prompts, keybinds. | Parity checklist (§4) 100% ticked. | |
| M6 | UX upgrades: command palette, DnD, multiselect, undo/redo, column-filter checklist, theming. | §5 items marked complete. | Anything unfinished rolls to v2.1. |
| M7 | Polish: accessibility pass, packaging (self-contained single-file), signed build, release notes. | Installer-less `.exe` under 80 MB; launches in < 1.5 s cold on a mid-range Windows 11 laptop. | |

Rough total: **~8–10 focused weeks for one developer** if Core math and file formats behave; allot +2 weeks buffer for filter behavior and cross-platform theme polish.

## 9a. Real-world input observations (from sampled XMLs)

Cross-cutting facts observed in the three BodySlide installations the user pointed to (`Fallout 4/Data/Tools/BodySlide/SliderPresets/CBBE.xml`, `Skyrim SE/Data/CalienteTools/BodySlide/SliderPresets/` — empty — and `Mod Organizer/SSE Mods/Caliente's Beautiful Bodies Enhancer -CBBE-/CalienteTools/BodySlide/SliderPresets/CBBE.xml`). These drive non-obvious port requirements:

1. **The `<?xml ... ?>` declaration is optional.** The Fallout 4 file opens directly with `<SliderPresets>`. `XDocument.Load` handles this, but tests must cover both cases.
2. **Ignore `<Preset set="...">` and the `<Group name="..."/>` child.** The Java parser reads only `name` and walks `<SetSlider>` children; unknown attributes/children are discarded. The port must do the same.
3. **Sliders are sparse per preset.** Presets routinely set only `big` or only `small` for a given slider. The missing half is resolved via profile defaults at render time, not at parse time. Don't pre-fill on load.
4. **Negative slider values are legal.** Observed examples: `BreastHeight@-25`, `BreastWidth@-50`, `AreolaSize@-30`, `Belly@-25`, `BreastPerkiness@-25`. These flow through the invert/multiplier/round pipeline unchanged; golden-file tests must cover them.
5. **Preset names contain parentheses and special characters.** `CBBE Curvy (Outfit)`, `- Zeroed Sliders -`, `CBBE Fetish v2`. The output file name for BoS JSON is `<presetName>.json`, written to a user-picked directory — sanitize against Windows path-reserved characters (`< > : " / \ | ? *`) on export, but keep the in-memory name verbatim for round-trips.
6. **FO4 and SSE share slider names by accident but semantically differ.** `Arms`, `Butt`, `Breasts`, `ShoulderWidth`, `WaistHeight`, `Thighs` exist in both, but the underlying morph targets are different bodies — the BodyGen output only makes sense if the selected profile matches the game the user is modding. Make the profile selector prominent.
7. **Mod Organizer virtual file layout matters.** The real-looking "SSE" XMLs live under MO2's per-mod directory, not Steam's `Data/`, because MO2 virtualizes the game data folder at launch. Don't assume `Data/CalienteTools/BodySlide/SliderPresets` is the authoritative path; the v1 behavior of letting the user pick via file dialog is correct.

## 10. Open questions

1. ~~Rename to BS2BG or keep jBS2BG?~~ **Resolved.** Full name is **Bodyslide to Bodygen**, short name **BS2BG**. Port author: evildarkarchon. Original author Totiman / asdasfa credited in About dialog, README, and source headers. File extension stays `.jbs2bg` indefinitely for backward compatibility; window title is `Bodyslide to Bodygen (BS2BG)` (plus the open project name when applicable).
2. **Headless CLI?** Since `BS2BG.Core` is standalone, a `bs2bg generate --project X.jbs2bg --out ./out` binary is maybe a day of work. Out of v2.0 scope but worth pre-announcing.
3. **Portable vs installed?** Default portable (zip with `.exe` + `profiles/` folder), no installer, to match modder expectations.
4. **Mod authoring sponsorship?** If the plan is to publish back to Nexus, confirm license compatibility with the original Java source (no explicit LICENSE file in the repo — needs to be established with the original author before release).
5. **Authoritative FO4 profile.** Seed file for `fallout4-cbbe.json` is "best effort" (defaults 1.0, no inverts, no multipliers). Does the user have a known-good FO4 BodyGen config to calibrate against, or should v2.0 ship with the empty-tune profile and iterate post-release? If the latter, add a "Report profile feedback" menu link.
6. **Ship all three profiles in `profiles/` on first launch, or gate FO4 behind a "Fallout 4 support (experimental)" toggle?** Leaning toward shipping all three unconditionally and marking FO4 experimental in a tooltip on the profile dropdown.

---

**Deliverable gate:** before coding starts, produce the golden-file fixture set (§8) from the existing Java build. That corpus is what the port is actually certified against.
