Last verified: 2026-04-28

# BodyGen, BodySlide, and BodyTypes of Skyrim Setup

This packaged guide explains how to use BS2BG outputs with common Skyrim SE and Fallout 4 modding layouts. BS2BG generates files and setup guidance; it does not edit game plugins.

## BodySlide preset XML inputs

BS2BG imports BodySlide preset XML files that already exist on your machine. Export or locate presets from BodySlide, then add those XML files in BS2BG before generating templates, morph assignments, or BodyTypes of Skyrim JSON.

Before generating, confirm the selected profile matches the preset family you intend to use, such as Skyrim CBBE, Skyrim UUNP, or Fallout 4 CBBE. Profile selection controls slider defaults, multipliers, and inverted sliders.

## BodyGen templates.ini and morphs.ini placement

BodyGen reads `templates.ini` and `morphs.ini` from the mod that supplies BodyGen configuration for your game setup. The final output location depends on how your mod manager maps files into the virtual game `Data` folder.

Use generic placement rules instead of assuming one manager-specific folder:

- MO2: place generated INI files inside a dedicated mod folder whose paths appear in the virtual `Data` tree where BodyGen expects configuration files.
- Vortex: place generated INI files in a managed mod/staging folder or package them as a mod, then deploy so the files appear under the intended game `Data` path.
- Manual layouts: place generated INI files under the matching game `Data` subfolder used by your BodyGen/RaceMenu or LooksMenu setup.

Keep a backup of any existing BodyGen files before replacing them. BS2BG will generate the file contents, but you decide when and where to copy them into your mod setup.

## BodyTypes of Skyrim JSON output

BodyTypes of Skyrim/BoS JSON output is separate from BodyGen INI output. Generate the BoS JSON only when your workflow uses BodyTypes of Skyrim and place it where that mod's documentation expects JSON configuration.

If you use a mod manager, prefer adding the JSON to a dedicated mod package so deployment and rollback are explicit. If you manage files manually, copy the JSON only after checking the generated content and confirming the target folder matches your BoS installation instructions.

## Common output-location mistakes

Most setup problems come from generated files being valid but copied to the wrong output location. Check these issues before changing presets or regenerating data:

- The files were written to a temporary export folder but never copied into a mod-manager mod.
- The files were copied into the real game folder while MO2 or Vortex is using a different staged/virtual folder.
- `templates.ini` and `morphs.ini` were split between different mods, leaving BodyGen to read only one of them.
- BoS JSON was copied to a BodyGen INI folder, or BodyGen INI files were copied to a BoS JSON folder.
- A higher-priority mod overwrote the generated files after deployment.

## Verify generated files before copying

Open the generated files in a text editor before copying them into a mod manager or manual game layout. Verify the preset names, NPC assignment names, and expected target output type match the workflow you intended to run.

For BodyGen INI files, confirm both `templates.ini` and `morphs.ini` exist when your workflow needs both files. For BoS JSON, confirm the JSON file contains the expected BodyTypes of Skyrim assignments and is not an old export from a previous project.

## Troubleshooting checklist

1. Confirm the BodySlide preset XML imported into BS2BG is the preset you intended to convert.
2. Confirm the BS2BG profile family matches the body mod or game family for the preset.
3. Generate to an easy-to-inspect folder first, not directly into a live mod folder.
4. Open the generated files and verify they contain the expected presets, morphs, and assignments.
5. Copy files into a dedicated MO2/Vortex/manual mod location only after inspection.
6. Deploy or enable the mod and verify the files appear in the effective game `Data` view.
7. If the game does not show changes, check mod priority and whether another mod overwrote the same output location.
8. Re-run BS2BG only after confirming the copied files are the files the game or downstream mod is actually reading.

## Boundary

BS2BG generates files and setup guidance; it does not edit game plugins. It does not patch ESP/ESM/ESL files, discover game folders automatically, upload support data, or change external mod-manager configuration.
