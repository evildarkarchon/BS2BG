# Third-party content attribution

The test corpus bundles redistributed input files. This file documents
their provenance.

## `inputs/skyrim-cbbe/CBBE.xml`

- **Origin:** The *Caliente's Beautiful Bodies Enhancer -CBBE-* Skyrim
  Special Edition mod by **Caliente** and **Ousnius**.
- **Source path on contributor's system:**
  `E:\Mod Organizer\SSE Mods\Caliente's Beautiful Bodies Enhancer -CBBE-\CalienteTools\BodySlide\SliderPresets\CBBE.xml`
- **Content:** 14 default CBBE slider presets shipped with the mod.
- **Purpose in this repo:** test input only. BS2BG reads this file,
  generates BodyGen templates from it, and the generated output is
  compared against the reference Java build's output for the same input.
- **Redistribution status:** CBBE's public license terms allow
  redistribution of the SliderPresets XML for tooling interoperability
  purposes; the mod is installable and freely downloadable from
  Nexus Mods. **If the CBBE authors object, remove this file and replace
  with a synthetic fixture.**

## `inputs/fallout4-cbbe/CBBE.xml`

- **Origin:** The *Caliente's Beautiful Bodies Enhancer -CBBE-* Fallout 4
  mod by **Caliente** and **Ousnius**.
- **Source path on contributor's system:**
  `E:\SteamLibrary\steamapps\common\Fallout 4\Data\Tools\BodySlide\SliderPresets\CBBE.xml`
- Same status and caveats as the Skyrim CBBE.xml above.

## `inputs/minimal/minimal.xml`

- Hand-authored by the BS2BG project (evildarkarchon) for test purposes.
- Uses real slider names from the Skyrim-CBBE Defaults list but the
  values/combinations are entirely synthetic.

## `inputs/skyrim-uunp/UUNP-synthetic.xml`

- Hand-authored by the BS2BG project (evildarkarchon) for test purposes.
- Uses real slider names from the Skyrim-UUNP Defaults list but the
  presets themselves are entirely synthetic.

## `inputs/npcs/sample-npcs.txt`

- Hand-authored by the BS2BG project (evildarkarchon) for test purposes.
- Values reference vanilla Skyrim NPCs (Lydia, Aela, Serana, etc.) for
  authenticity. No proprietary mod data is included.

## `inputs/profiles/settings.json` and `settings_UUNP.json`

- Copied verbatim from the jBS2BG v1.1.2 repository root
  (`J:\jBS2BG\settings.json` and `settings_UUNP.json`). These are the
  Skyrim-CBBE and Skyrim-UUNP profile definitions consumed by the
  reference Java build.
- Original authorship: **Totiman / asdasfa** (jBS2BG project).
