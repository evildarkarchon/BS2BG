# `minimal` fixture — hand-traced expected output

Every output line below is hand-computed from the Java source
(`SliderPreset.toLine`, `SetSlider.toText`, `SliderPreset.toBosJson`,
`Settings.getDefaultValueSmall/Big`, `Settings.isInverted`,
`Settings.getMultiplier`) against the **Skyrim-CBBE** profile
(`inputs/profiles/settings.json`).

These assertions are the load-bearing checkpoint for the C# port's
`SliderMathFormatter`. If a C# unit test here fails, either the math
pipeline diverged from Java or this document has an error — verify
against the Java source before changing either.

## Profile data (Skyrim-CBBE)

| Slider           | Default small | Default big | Inverted? | Multiplier |
| ---------------- | ------------: | ----------: | :-------: | ---------: |
| Breasts          | 20 (0.2×100)  | 100         | yes       | 1.0        |
| BreastsSmall     | 100           | 100         | yes       | 1.0        |
| NippleDistance   | 100           | 100         | yes       | 1.0        |
| NippleSize       | 100           | 100         | yes       | 1.0        |
| ButtCrack        | 100           | 100         | yes       | 1.0        |
| Butt             | 0             | 100         | yes       | 1.0        |
| ButtSmall        | 100           | 100         | yes       | 1.0        |
| Waist            | 0             | 100         | **no**    | 1.0        |
| Legs             | 0             | 100         | yes       | 1.0        |
| Ankles           | 100           | 100         | yes       | 1.0        |
| Arms             | 0             | 100         | yes       | 1.0        |
| ShoulderWidth    | 100           | 100         | yes       | 1.0        |

Defaults are stored as floats 0.0–1.0 and multiplied by 100 via
`(int) (v * 100)` — truncation, not rounding (see
`Settings.getDefaultValueSmall`).

## Math reference (per SetSlider)

Given:
- `pctMin = pctMax = 100` (default for newly-injected missing-default sliders
  and unedited user sliders),
- `small = getValueSmall() * 0.01f`
- `big   = getValueBig()   * 0.01f`

Steps:
1. If inverted: `small = 1 - small; big = 1 - big`.
2. `diff = big - small`
3. `min = small + diff * (pctMin * 0.01)` — with default pctMin=100, min == big.
4. `max = small + diff * (pctMax * 0.01)` — with default pctMax=100, max == big.
5. `min *= mult; max *= mult`
6. Round each to 2 decimals, `BigDecimal.ROUND_HALF_UP` → back to float.
7. If `min == max`: emit `name@max`. Else `name@min:max`.
   Float formatting: Java `Float.toString(f)` — shortest round-trip
   representation (e.g. `0.0f → "0.0"`, `1.25f → "1.25"`, never `"1.250"`).

## Exclusion rule for `omitRedundantSliders=true`

Checked on the **resolved** `getValueSmall/Big` (defaults applied):

- Non-inverted slider where `small == 0 && small == big` → excluded.
- Inverted slider where `small == 100 && small == big` → excluded.

Otherwise included.

---

## Preset 1: `AllCases`

### Raw user-defined SetSliders

| Slider   | size=big | size=small |
| -------- | -------: | ---------: |
| Arms     |     —    | 25         |
| Breasts  | 100      | 100        |
| Butt     |   0      |   0        |
| Waist    |  60      |  40        |

### Missing-default injection

All CBBE Defaults entries not in the user list are added with null/null
values (resolved via defaults at render time), enabled, pctMin=pctMax=100:
`Ankles, BreastsSmall, ButtCrack, ButtSmall, Legs, NippleDistance, NippleSize, ShoulderWidth`.

### Per-slider computation (alphabetical)

| Slider         | raw small | raw big | inv? | after invert (small, big) | result | excluded? |
| -------------- | --------: | ------: | :--: | ------------------------- | ------ | :-------: |
| Ankles         | 100 (def) | 100 (def) | yes  | (0.0, 0.0)                | `Ankles@0.0`         | **EX** |
| Arms           |  25       | 100 (def) | yes  | (0.75, 0.0)               | `Arms@0.0`           |        |
| Breasts        | 100       | 100       | yes  | (0.0, 0.0)                | `Breasts@0.0`        | **EX** |
| BreastsSmall   | 100 (def) | 100 (def) | yes  | (0.0, 0.0)                | `BreastsSmall@0.0`   | **EX** |
| Butt           |   0       |   0       | yes  | (1.0, 1.0)                | `Butt@1.0`           |        |
| ButtCrack      | 100 (def) | 100 (def) | yes  | (0.0, 0.0)                | `ButtCrack@0.0`      | **EX** |
| ButtSmall      | 100 (def) | 100 (def) | yes  | (0.0, 0.0)                | `ButtSmall@0.0`      | **EX** |
| Legs           |   0 (def) | 100 (def) | yes  | (1.0, 0.0)                | `Legs@0.0`           |        |
| NippleDistance | 100 (def) | 100 (def) | yes  | (0.0, 0.0)                | `NippleDistance@0.0` | **EX** |
| NippleSize     | 100 (def) | 100 (def) | yes  | (0.0, 0.0)                | `NippleSize@0.0`     | **EX** |
| ShoulderWidth  | 100 (def) | 100 (def) | yes  | (0.0, 0.0)                | `ShoulderWidth@0.0`  | **EX** |
| Waist          |  40       |  60       | no   | (0.4, 0.6)                | `Waist@0.6`          |        |

### Expected `toLine(false)` — omit=false

```
AllCases = Ankles@0.0, Arms@0.0, Breasts@0.0, BreastsSmall@0.0, Butt@1.0, ButtCrack@0.0, ButtSmall@0.0, Legs@0.0, NippleDistance@0.0, NippleSize@0.0, ShoulderWidth@0.0, Waist@0.6
```

### Expected `toLine(true)` — omit=true

```
AllCases = Arms@0.0, Butt@1.0, Legs@0.0, Waist@0.6
```

---

## Preset 2: `Negatives`

### Raw

| Slider   | size=big | size=small |
| -------- | -------: | ---------: |
| Breasts  | −25      | −50        |
| Legs     |  80      |     —      |

Missing defaults: all non-`Breasts`, non-`Legs` CBBE sliders.

### Per-slider (alphabetical)

| Slider         | raw small | raw big | inv? | after invert | result | excluded? |
| -------------- | --------: | ------: | :--: | ------------ | ------ | :-------: |
| Ankles         | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `Ankles@0.0`         | **EX** |
| Arms           |   0 (def) | 100 (def) | yes | (1.0, 0.0)   | `Arms@0.0`           |        |
| Breasts        | −50       | −25       | yes | (1.5, 1.25)  | `Breasts@1.25`       |        |
| BreastsSmall   | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `BreastsSmall@0.0`   | **EX** |
| Butt           |   0 (def) | 100 (def) | yes | (1.0, 0.0)   | `Butt@0.0`           |        |
| ButtCrack      | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `ButtCrack@0.0`      | **EX** |
| ButtSmall      | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `ButtSmall@0.0`      | **EX** |
| Legs           |   0 (def) |  80       | yes | (1.0, 0.2)   | `Legs@0.2`           |        |
| NippleDistance | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `NippleDistance@0.0` | **EX** |
| NippleSize     | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `NippleSize@0.0`     | **EX** |
| ShoulderWidth  | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `ShoulderWidth@0.0`  | **EX** |
| Waist          |   0 (def) | 100 (def) | no  | (0.0, 1.0)   | `Waist@1.0`          |        |

Worked example for `Breasts`:
- raw small = −50, raw big = −25
- small_f = −0.5, big_f = −0.25
- inverted: small_f = 1 − (−0.5) = 1.5; big_f = 1 − (−0.25) = 1.25
- diff = 1.25 − 1.5 = −0.25
- min = 1.5 + (−0.25) × 1.0 = 1.25
- max = 1.5 + (−0.25) × 1.0 = 1.25
- min == max → `Breasts@1.25`

### Expected `toLine(false)`

```
Negatives = Ankles@0.0, Arms@0.0, Breasts@1.25, BreastsSmall@0.0, Butt@0.0, ButtCrack@0.0, ButtSmall@0.0, Legs@0.2, NippleDistance@0.0, NippleSize@0.0, ShoulderWidth@0.0, Waist@1.0
```

### Expected `toLine(true)`

```
Negatives = Arms@0.0, Breasts@1.25, Butt@0.0, Legs@0.2, Waist@1.0
```

---

## Preset 3: `MissingDef`

### Raw

| Slider    | size=big | size=small |
| --------- | -------: | ---------: |
| NipBGone  | 100      |     —      |

`NipBGone` is not in the Skyrim-CBBE Defaults table, so
`Settings.getDefaultValueSmall("NipBGone")` returns 0 (the "not defined"
else-branch). `NipBGone` is also not in the Inverted list, and has no
multiplier entry (defaults to 1.0).

Missing defaults: all 12 CBBE default sliders.

### Per-slider (alphabetical)

| Slider         | raw small | raw big | inv? | after invert | result | excluded? |
| -------------- | --------: | ------: | :--: | ------------ | ------ | :-------: |
| Ankles         | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `Ankles@0.0`         | **EX** |
| Arms           |   0 (def) | 100 (def) | yes | (1.0, 0.0)   | `Arms@0.0`           |        |
| Breasts        |  20 (def) | 100 (def) | yes | (0.8, 0.0)   | `Breasts@0.0`        |        |
| BreastsSmall   | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `BreastsSmall@0.0`   | **EX** |
| Butt           |   0 (def) | 100 (def) | yes | (1.0, 0.0)   | `Butt@0.0`           |        |
| ButtCrack      | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `ButtCrack@0.0`      | **EX** |
| ButtSmall      | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `ButtSmall@0.0`      | **EX** |
| Legs           |   0 (def) | 100 (def) | yes | (1.0, 0.0)   | `Legs@0.0`           |        |
| NipBGone       |   0 (fallback, not in defaults) | 100 | no | (0.0, 1.0) | `NipBGone@1.0`     |        |
| NippleDistance | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `NippleDistance@0.0` | **EX** |
| NippleSize     | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `NippleSize@0.0`     | **EX** |
| ShoulderWidth  | 100 (def) | 100 (def) | yes | (0.0, 0.0)   | `ShoulderWidth@0.0`  | **EX** |
| Waist          |   0 (def) | 100 (def) | no  | (0.0, 1.0)   | `Waist@1.0`          |        |

### Expected `toLine(false)`

```
MissingDef = Ankles@0.0, Arms@0.0, Breasts@0.0, BreastsSmall@0.0, Butt@0.0, ButtCrack@0.0, ButtSmall@0.0, Legs@0.0, NipBGone@1.0, NippleDistance@0.0, NippleSize@0.0, ShoulderWidth@0.0, Waist@1.0
```

### Expected `toLine(true)`

```
MissingDef = Arms@0.0, Breasts@0.0, Butt@0.0, Legs@0.0, NipBGone@1.0, Waist@1.0
```

---

## Expected `templates.ini` (full file, `omit=false`)

Presets appear alphabetically (`Data.sortPresets`) — `AllCases`,
`MissingDef`, `Negatives` — separated by the platform line separator
(`\r\n` on Windows when `System.getProperty("line.separator")` is used).

```
AllCases = Ankles@0.0, Arms@0.0, Breasts@0.0, BreastsSmall@0.0, Butt@1.0, ButtCrack@0.0, ButtSmall@0.0, Legs@0.0, NippleDistance@0.0, NippleSize@0.0, ShoulderWidth@0.0, Waist@0.6
MissingDef = Ankles@0.0, Arms@0.0, Breasts@0.0, BreastsSmall@0.0, Butt@0.0, ButtCrack@0.0, ButtSmall@0.0, Legs@0.0, NipBGone@1.0, NippleDistance@0.0, NippleSize@0.0, ShoulderWidth@0.0, Waist@1.0
Negatives = Ankles@0.0, Arms@0.0, Breasts@1.25, BreastsSmall@0.0, Butt@0.0, ButtCrack@0.0, ButtSmall@0.0, Legs@0.2, NippleDistance@0.0, NippleSize@0.0, ShoulderWidth@0.0, Waist@1.0
```

## Expected `templates-omit.ini` (`omit=true`)

```
AllCases = Arms@0.0, Butt@1.0, Legs@0.0, Waist@0.6
MissingDef = Arms@0.0, Breasts@0.0, Butt@0.0, Legs@0.0, NipBGone@1.0, Waist@1.0
Negatives = Arms@0.0, Breasts@1.25, Butt@0.0, Legs@0.2, Waist@1.0
```

---

## Expected `morphs.ini` (FixtureDriver with `--npcs`)

The driver assigns one preset to each NPC via deterministic round-robin
over the alphabetically-sorted preset list (`[AllCases, MissingDef,
Negatives]`) and one custom morph target `All|Female` with the first two
presets. Assignment order follows the NPC file's line order:

| Line idx | Mod           | Name     | Preset      |
| -------: | ------------- | -------- | ----------- |
| 0        | Skyrim.esm    | Lydia    | AllCases    |
| 1        | Skyrim.esm    | Aela     | MissingDef  |
| 2        | Skyrim.esm    | Serana   | Negatives   |
| 3        | Dawnguard.esm | Valerica | AllCases    |
| 4        | Skyrim.esm    | Mjoll    | MissingDef  |
| 5        | Skyrim.esm    | Jenassa  | Negatives   |
| 6        | Skyrim.esm    | Ysolda   | AllCases    |

Output format (from `MainController.doGenerateMorphs`): custom morph
targets first in insertion order, then NPCs sorted by mod
(`String.CASE_INSENSITIVE_ORDER`, stable sort preserves file order within
same-mod tie).

FormID trim (`NPC.trimFormId`): strip chars beyond the rightmost 6, then
strip leading zeros (keep at least one char). So `000A2C94` → `A2C94`,
`0001A697` → `1A697`, `02002B74` → `2B74`, `02002B6C` → `2B6C`.

```
All|Female=AllCases|MissingDef
Dawnguard.esm|2B6C=AllCases
Skyrim.esm|A2C94=AllCases
Skyrim.esm|1A697=MissingDef
Skyrim.esm|2B74=Negatives
Skyrim.esm|1B07A=MissingDef
Skyrim.esm|1348E=Negatives
Skyrim.esm|1A691=AllCases
```

---

## BoS JSON sample — `AllCases`

Emits the same `getEnabledAndDefaultSetSliders` set as templates.ini but
with the always-excluded sliders from the omit-redundant rule applied
**unconditionally** (BoS JSON always trims them — see
`SliderPreset.toBosJson`). So for `AllCases` the kept sliders are:
`Arms, Butt, Legs, Waist`.

BoS high/low values use the `big`/`small` values directly (post-invert,
post-multiplier), not the percent-interpolated min/max:

| Slider | raw small | raw big | inv? | low  | high |
| ------ | --------: | ------: | :--: | ---: | ---: |
| Arms   | 25        | 100     | yes  | 0.75 | 0.0  |
| Butt   |  0        |   0     | yes  | 1.0  | 1.0  |
| Legs   |  0 (def)  | 100 (def) | yes | 1.0 | 0.0  |
| Waist  | 40        |  60     | no   | 0.4  | 0.6  |

BoS JSON iteration uses `LinkedHashMap` insertion order from
`getEnabledAndDefaultSetSliders` (which sorts alphabetically).
`slidersnumber` counts the kept sliders.

Expected `AllCases.json` (pretty-printed with
`com.eclipsesource.json.WriterConfig.PRETTY_PRINT`, 2-space indent).
**Confirmed byte-exact against the reference Java build's output** via
`tests/tools/generate-expected.ps1`:

```json
{
  "string": {
    "bodyname": "AllCases",
    "slidername1": "Arms",
    "slidername2": "Butt",
    "slidername3": "Legs",
    "slidername4": "Waist"
  },
  "int": {
    "slidersnumber": 4
  },
  "float": {
    "highvalue1": 0,
    "highvalue2": 1,
    "highvalue3": 0,
    "highvalue4": 0.6,
    "lowvalue1": 0.75,
    "lowvalue2": 1,
    "lowvalue3": 1,
    "lowvalue4": 0.4
  }
}
```

Note the ordering: `string` block first, then `int`, then `float`; within
each, insertion order (highvalues before lowvalues because the Java code
adds to `highValues` map first, then `lowValues`).

## Float formatting — two rules, two contexts

This is the single most important subtlety to get right in the C# port.

**Context A: `SetSlider.toText()` → templates.ini / preview**

The slider string is built via Java string concatenation
`name + "@" + floatValue`. Java's autoboxing path to `String` calls
`Float.toString(f)`, which emits shortest-round-trip form with
`.0` preserved for integer-valued floats:

- `0.0f` → `"0.0"`
- `1.0f` → `"1.0"`
- `0.6f` → `"0.6"` (not `"0.60"`)
- `1.25f` → `"1.25"`
- `0.75f` → `"0.75"`
- `0.2f` → `"0.2"` (not `"0.20"`)

**Context B: `SliderPreset.toBosJson()` → BoS JSON**

Values go through `com.eclipsesource.json`'s
`JsonObject.add(String, float)`. minimal-json 0.9.5 applies a
`cutOffPointZero` helper that **strips trailing `.0`** for
integer-valued floats before emitting the JSON number. So:

- `0.0f` → `0` (integer-style, no decimal)
- `1.0f` → `1` (integer-style, no decimal)
- `0.6f` → `0.6` (unchanged — no trailing `.0` to strip)
- `1.25f` → `1.25`

The two contexts **disagree** on integer-valued output. A port that
uses one helper for both will fail byte-for-byte tests on at least
one of templates.ini / bos-json.

**C# implications.** Neither
`float.ToString(CultureInfo.InvariantCulture)` nor `System.Text.Json`'s
number writer match either Java rule by default:

- `System.Text.Json` writes `0` for `0.0f` (matches context B, NOT A).
- `float.ToString("G9", CultureInfo.InvariantCulture)` writes `"0"` for
  `0f` and `"0.5"` for `0.5f` (matches context B, NOT A).

Two helpers are required in `BS2BG.Core.Formatting`:

```csharp
// Context A: templates.ini. Always appends .0 for integer-valued floats.
string JavaFloatLikeString(float f);

// Context B: BoS JSON. Strips trailing .0 (matches minimal-json).
string MinimalJsonLikeNumber(float f);
```

Add regression tests for both helpers with these cases: `0.0f`, `1.0f`,
`0.2f`, `0.6f`, `0.75f`, `1.25f`, and negatives (`-0.25f`).
