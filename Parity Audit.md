I audited this as strict Java v1.1.2 parity. Short version: the core math, import/export formats, project serialization, profiles, main NPC filters, image lookup, release/CLI work, and most template/morph workflows are already in good shape. The remaining gaps are mostly UI/workflow parity where the C# app modernized behavior instead of matching Java one-for-one.

**Highest Priority Gaps**
1. Preset duplicate behavior is not Java-parity.
Java duplicates as `<name>(Dupe)` automatically and only errors on clash: [MainController.java](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/MainController.java:1087). C# requires the user-provided `PresetNameInput`, so the visible Duplicate button can fail unless the user edits the name first: [TemplatesViewModel.cs](D:/repos/jBS2BG/src/BS2BG.App/ViewModels/TemplatesViewModel.cs:159).

2. Java destructive confirmations are mostly missing.
Java confirms clear presets, removing assigned presets, clearing target presets, clearing custom targets, clearing NPCs, and clearing assignments: [MainController.java](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/MainController.java:569). C# makes many of these direct undoable commands, with confirmation only for all-scope NPC operations: [TemplatesViewModel.cs](D:/repos/jBS2BG/src/BS2BG.App/ViewModels/TemplatesViewModel.cs:340), [MorphsViewModel.cs](D:/repos/jBS2BG/src/BS2BG.App/ViewModels/MorphsViewModel.cs:1619).

3. Fill Empty lost the Java multi-select preset chooser.
Java lets users pick multiple presets, Select All, Invert Selection, then randomly fill visible empty NPCs from that subset: [popup_sliderpresetsfill.fxml](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/popup_sliderpresetsfill.fxml:16). C# fills from the selected preset, or all presets when none is selected: [MorphsViewModel.cs](D:/repos/jBS2BG/src/BS2BG.App/ViewModels/MorphsViewModel.cs:1611).

4. Imported NPC database is less capable.
Java’s NPC database popup has a full filterable table plus add selected, add all filtered, clear filtered database, assign-random, and image preview: [PopupNpcDatabaseController.java](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/PopupNpcDatabaseController.java:231). C# has import/search/add/add-all, but no clear imported-database action and a simpler imported NPC presentation: [MainWindow.axaml](D:/repos/jBS2BG/src/BS2BG.App/Views/MainWindow.axaml:607).

5. Selected-preset BoS JSON single-file export is missing.
Java’s BoS popup has both Copy and Export for the current preset: [popup_bosview.fxml](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/popup_bosview.fxml:16). C# only exposes Copy for the selected preset; export is bulk all-presets: [MainWindow.axaml](D:/repos/jBS2BG/src/BS2BG.App/Views/MainWindow.axaml:516).

**Medium Priority**
6. Dirty window close confirmation is missing.
Java prompts on dirty close and blocks close while busy: [MainController.java](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/MainController.java:298). C# confirms New/Open discard, but the window code-behind has no close hook: [MainWindow.axaml.cs](D:/repos/jBS2BG/src/BS2BG.App/Views/MainWindow.axaml.cs:30).

7. Open-project behavior differs.
Java regenerates templates/morphs and can show no-preset warnings after open: [MainController.java](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/MainController.java:1716). C# opens and updates state/status, but does not regenerate generated output or raise the no-preset notification: [MainWindowViewModel.cs](D:/repos/jBS2BG/src/BS2BG.App/ViewModels/MainWindowViewModel.cs:551).

8. SetSliders bulk controls are narrower.
Java has arbitrary-percent All / All Min / All Max sliders with checkboxes: [popup_setsliders.fxml](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/popup_setsliders.fxml:87). C# has per-row sliders and bulk 0/50/100 buttons only: [MainWindow.axaml](D:/repos/jBS2BG/src/BS2BG.App/Views/MainWindow.axaml:388).

**Lower Priority / Edge Parity**
9. Settings startup self-healing differs.
Java creates missing `settings.json` / `settings_UUNP.json` and shows modal errors for invalid files: [Settings.java](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/data/Settings.java:41). C# expects bundled profile files and throws when required files are absent: [TemplateProfileCatalogFactory.cs](D:/repos/jBS2BG/src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs:119).

10. No-preset notification is simpler.
Java shows custom targets plus a filterable NPC table: [PopupNoPresetNotifController.java](D:/repos/jBS2BG/src/com/asdasfa/jbs2bg/PopupNoPresetNotifController.java:99). C# shows plain list strings: [WindowNoPresetNotificationService.cs](D:/repos/jBS2BG/src/BS2BG.App/Services/WindowNoPresetNotificationService.cs:21).

I did this as a read-only audit and did not run tests. The clean next move would be an OpenSpec parity-closure change focused on the first five items, since those are the strongest “Java action exists, C# action is absent or behaviorally different” gaps.