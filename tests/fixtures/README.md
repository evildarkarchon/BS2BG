# BS2BG test fixtures

Golden-file corpus for the C#/Avalonia port. Each subdirectory under `inputs/`
is a distinct scenario; the corresponding subdirectory under `expected/` holds
the output produced by running the reference Java build (`jBS2BG` v1.1.2)
against those inputs.

## Layout

```
tests/
  fixtures/
    README.md                 # this file
    NOTICE.md                 # third-party content attribution
    inputs/
      profiles/
        settings.json         # Skyrim-CBBE profile (copied from project root)
        settings_UUNP.json    # Skyrim-UUNP profile (copied from project root)
      skyrim-cbbe/
        CBBE.xml              # real CBBE preset file (see NOTICE.md)
      fallout4-cbbe/
        CBBE.xml              # real FO4 CBBE preset file (see NOTICE.md)
      skyrim-uunp/
        UUNP-synthetic.xml    # hand-authored, NOT a real preset file
      minimal/
        minimal.xml           # hand-authored; every slider chosen to hit a
                              # specific code path in SliderPreset.toLine
                              # and SetSlider.toText
      npcs/
        sample-npcs.txt       # 7 synthetic NPCs in jBS2BG's
                              # Mod|Name|EditorID|Race|FormID format
    expected/
      {scenario}/
        templates.ini         # output of Generate Templates + Export
        morphs.ini            # output of Generate Morphs + Export
        project.jbs2bg        # saved project file
        bos-json/             # one JSON file per SliderPreset
  tools/
    generate-expected.ps1     # regenerates expected/ from inputs/
```

## Fixture descriptions

### minimal

Three presets hand-crafted to exercise specific code paths:

| Preset       | Purpose |
| ------------ | ------- |
| `AllCases`   | Inverted-both-100 slider (must be excluded when `omitRedundantSliders=true`), non-inverted-both-0, a regular big/small pair, and a slider with only `small=` set (big falls back to profile default). |
| `Negatives`  | Negative input values (`-25`, `-50`). Verifies that the invert → diff → multiply → round pipeline handles negatives correctly. |
| `MissingDef` | Single slider, not in the profile's Defaults list. Forces the full missing-default injection (all 12 CBBE default sliders) to appear in output. |

Assumes the Skyrim-CBBE profile. Use this fixture for ViewModel-independent
unit tests of `BS2BG.Core.Formatting.SliderMathFormatter`.

### skyrim-cbbe

Real-world CBBE.xml from the Skyrim SE CBBE mod (see NOTICE.md). 14 presets,
hundreds of sliders. Includes presets with negatives (`CBBE Fetish`,
`CBBE Chubby`), sparse sliders (`- Zeroed Sliders -` has one slider),
parenthesized names (`CBBE Curvy (Outfit)`), and a preset where every
SetSlider has both big and small set (`CBBE Slim`).

### fallout4-cbbe

Real-world CBBE.xml from Fallout 4 (see NOTICE.md). The slider namespace
differs from Skyrim-CBBE; under the Skyrim-CBBE profile, almost every
slider in this file is treated as "not in Defaults" and the missing-default
injection fires in full. Useful for testing the planned `fallout4-cbbe.json`
profile once it ships.

### skyrim-uunp

Three hand-authored presets using UUNP slider names from
`settings_UUNP.json`'s Defaults table. UUNP is not installed on the author's
machine (it conflicts with CBBE) and no UUNP preset XML was available from
any local mod directory, so this file is clearly marked synthetic in its
own header. Purpose: verify the profile-switching mechanism (Skyrim-CBBE →
Skyrim-UUNP) selects the correct Defaults / Multipliers / Inverted tables.

### npcs/sample-npcs.txt

Seven Skyrim NPCs in the jBS2BG NPC-import format
(`Mod|Name|EditorID|Race|FormID`). Used to exercise the "Add NPCs from
File" import path, the dedup-by-(mod, editorId) logic, and the morph-line
generator. Values are pulled from vanilla Skyrim so the test dump is stable
and legal to redistribute.

## Regenerating expected/

The `expected/` directory is produced by running the reference Java build
against each input. `tools/generate-expected.ps1` documents the exact steps
and, given a working Java 8 + JavaFX 8 environment, automates them.

**Why this matters:** the port's slider math is byte-compared against the
Java output. Any tweak to `SliderPreset.toLine` / `SetSlider.toText` /
`SliderPreset.toBosJson` in the Java source requires regenerating this
corpus, recommitting the diffs, and re-running the C# tests.

**Current state:**

- **Dependency JARs**: pre-fetched in `tests/tools/lib/`, versions matched
  to the original jBS2BG `.classpath`:
  commons-io 2.6, juniversalchardet 2.1.0 (from the
  `com.github.albfernandez` fork — `com.googlecode` 1.0.3 predates
  `UniversalDetector.detectCharset(File)`), and minimal-json 0.9.5.
- **Headless harness**: `src/com/asdasfa/jbs2bg/testharness/FixtureDriver.java`
  is written. It calls the same `Data.parseXmlPreset` / `saveToFile` /
  `SliderPreset.toLine` / `toBosJson` paths the GUI uses, with zero JavaFX
  Stage dependencies. Its logic parses cleanly under JDK 26's `javac` when
  JavaFX is available — only the `javafx.collections.ObservableList` import
  (transitive from `Data`/`NPC`) prevents local compilation.
- **Remaining blocker**: a JDK 8 toolchain (or JDK 11+ plus OpenJFX 8 SDK
  on `--module-path`). Once installed, run
  `tests/tools/generate-expected.ps1` to populate `expected/`.
- **Hand-verified assertions**: for the `minimal` fixture,
  `inputs/minimal/MATH-WALKTHROUGH.md` contains the exact expected
  `templates.ini`, `templates-omit.ini`, `morphs.ini`, and a worked-out
  `AllCases.json` BoS output, each traced from the Java source
  line-by-line. The first C# unit tests for
  `BS2BG.Core.Formatting.SliderMathFormatter` should assert against these
  strings directly. Larger fixtures (skyrim-cbbe, fallout4-cbbe,
  skyrim-uunp) get populated via the harness once the JDK 8 environment
  is stood up.

## Contributing new fixtures

1. Add inputs under `inputs/<name>/`. Keep real-world XMLs provenance-
   documented in `NOTICE.md`; mark hand-authored XMLs as synthetic in the
   file's own XML comment header.
2. If the fixture exercises a new code path, explain what in this README.
3. Regenerate `expected/<name>/` via the Java build, or open a PR that
   only adds inputs with a note that expected-output will follow once
   the reference build can produce it.
