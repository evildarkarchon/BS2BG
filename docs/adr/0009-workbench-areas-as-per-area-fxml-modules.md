---
status: accepted
date: 2026-10-14
---

# Workbench Areas as per-Area FXML modules under a shell

`WorkbenchController` grew into one controller over one FXML: about 4,300 lines and 205 `@FXML` fields covering the
chrome, all four Areas and the Output drawer. No Area could be loaded or tested without the whole Workbench, and
cross-Area inputs were written by whichever render ran last. The decomposition shape was decided in
[WorkbenchController decomposition shape](https://github.com/evildarkarchon/BS2BG/issues/127). The Settings Area
proved the pattern first ([#149](https://github.com/evildarkarchon/BS2BG/issues/149)), so it is recorded here.

## Decision

Each Area is **one FXML with a non-Node three-pane root plus one constructor-injected controller**, loaded by the
shell in code.

- **The FXML root is `AreaPanes`**, a small holder that is not a Node. Its `primary`, `editor` and `inspector`
  properties hold the Area's three panes. The FXML has no layout of its own.
- **The shell loads each Area through `AreaLoader`**, not `fx:include`. A controller factory hands FXMLLoader the
  Area controller the shell built, so the Area's collaborators are final constructor fields: its feature, the
  `DispatchPipeline`, the `ShellPort`, and only what else it uses of `WorkbenchPlatform`, a `Clock` and the
  `WorkbenchEnvironment`.
- **The shell places the panes into its own landmark containers** (`primaryPane`, `editorPane`, `inspectorPane`) at
  the positions they held in the single FXML, and shows only the active Area's panes. `workbench.fxml` keeps the
  chrome and the landmarks. The shell keeps the width listener, the reparenting into `overlayLayer` and the overlay
  clip; an Area owns any catalog-height binding.
- **The shell's `attach` stays the only composition root.** It builds every feature first, because the shell itself
  needs some of them (for example `SettingsFeature` for the dirty-Settings close gate), then loads each Area.
- **Packaging.** Each Area class and its FXML live beside its feature, for example `workbench.settings.SettingsArea`
  and `settings-area.fxml`. The contract types are public in `workbench`. `workbench.css` stays one scene
  stylesheet.

The modules are:

- the shell (`WorkbenchController`)
- the four Areas: Templates, Morphs, NPC Database and Settings
- the Output drawer, which is not an Area, matching the `Area` enum
- the shared modules: the dispatch pipeline, the focus policy, the NPC portrait viewer and the UIA list helper

## Interfaces

- **`WorkbenchArea`**: `panes()`; `render(WorkbenchContext)`, the single fan-out; `focusNode(Landmark)` with
  state-dependent per-Area answers and a `defaultFocusNode()` fallback; `focusSearch()` for Ctrl+K; `escape()`, which
  returns whether the Area consumed it; `leave()` when navigation leaves the Area; and `resetInspectorScroll()` when
  the narrow inspector overlay opens.
- **`WorkbenchContext`** is the one immutable record the shell builds on every Project frame and every job frame and
  renders into every Area. An Area keeps the last context and renders its own updates against it.
- **Single-writer controls.** Each control is written only by the module that owns it. Cross-Area inputs arrive as
  `WorkbenchContext` data.
- **The shell port (`ShellPort`)** carries every request an Area makes of the shell or of another Area. The shell
  does the shell-owned work, or calls the target Area's typed method. **Areas never reference each other.** The port
  grows one request at a time, as Areas are extracted.

## Considered options

- **Nested controllers via `fx:include`.** Rejected. The landmark-grouped FXML would force three fragments and three
  controllers per Area, or a layout restructure that breaks the shell-owned landmarks and the responsive
  reparenting.
- **Plain-Java binders over the single FXML.** Rejected. The shell would keep all 205 `@FXML` fields, or look nodes
  up by string id, and no Area could be loaded on its own.
- **A separate Project-lifecycle module.** Rejected. It would move the hub without deepening it, so the Project
  lifecycle, including close, stays in the shell.

## Consequences

- **Areas are testable alone.** Each `<Area>AreaTest` extends the shared `AreaTest` fixture. The fixture loads only
  that Area's FXML through `AreaLoader` and hosts the panes in a minimal Scene. It drives a real feature over a real
  `ProjectSession` with an explicit Settings store, a `RecordingPlatform` and a `RecordingShellPort`. Cross-Area
  journeys stay in `WorkbenchControllerTest`, through the whole loaded Workbench.
- **Tier 3 depends on accessible names, not AutomationIds.** The packaged smoke run finds controls by accessible
  name, role and parent region. Moving a control into an Area FXML must keep its accessible text, its visible text
  and its region nesting exactly.
- **One stylesheet.** The Area panes join the shell's scene, so `workbench.css` styles them as before.
- **`FxmlGraphLoadingTest`** checks that every `@FXML` field is injected for the root graph and for every Area graph
  the shell loads. **`WorkbenchFocusPolicyTest`** classifies the Area controls into the shell's landmark containers.
