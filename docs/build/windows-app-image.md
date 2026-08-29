# Windows app-image packaging checkpoint

Status: packaging checkpoint on top of the complete application gate (issue #97, parent #81). A green run proves
that the complete Java 25 build packages into a self-contained, non-modular Windows x64 application image that
starts from a clean extracted location without any system Java, exercises Project New/Save/Save As/recovery and
failed-overwrite preservation plus the representative BoS, Templates, and Morphs workflows, and exits cleanly.
ADR-0003 records the Java 25 baseline this checkpoint ships.

## One command

```powershell
.\tools\java25\package-java25.ps1
```

The script:

1. Runs `tools/java25/verify-java25.ps1` first (the complete application gate; see
   [java25-verification.md](java25-verification.md)). A packaging run never starts from a red or partial gate.
2. Reads the payload the Maven Wrapper staged in `target/app-image-input/`: the application jar (main class
   `com.asdasfa.jbs2bg.Launcher`) beside `lib/` with every runtime-scoped dependency. JavaFX is a `provided`
   dependency and is excluded from staging twice (`copy-dependencies` `excludeGroupIds`), so it can never be on
   the launcher classpath; `Get-StagedApplication` fails closed if a JavaFX jar appears anywhere in the tree.
3. Measures the runtime module closure with `jdeps --print-module-deps` over the staged jars against the pinned
   JavaFX 25 JMODs. `jdeps` reads jars and exploded modules but not JMOD archives, so the pinned JMODs are first
   extracted with `jmod extract` into `target/app-image-measure/`. The measured closure is widened only by the
   explicit additions listed in the script, each with a recorded reason (currently `jdk.charsets`: the extended
   charsets that `ProjectFileLoader` may resolve at runtime from a juniversalchardet detection).
4. Links the runtime with `jlink` from the pinned Temurin 25 `jmods/` and the pinned JavaFX JMODs with the same
   four options jpackage applies by default (`--strip-debug --no-header-files --no-man-pages
   --strip-native-commands`), then verifies its `release` file (`JAVA_VERSION` = the lock's, every requested
   module present), that `bin\java.exe` is absent, and that every JavaFX module's `legal/` directory was linked
   in. The JVM's own resolution of that module set (`--show-module-resolution`, including service bindings) is
   recorded from the toolchain JVM against the same pinned inputs.
5. Assembles `THIRD-PARTY-NOTICES.txt` and `notices/<jar>/` from the staged jars' own metadata (`META-INF`
   license and notice files, the embedded Maven pom `<licenses>`), listing a jar without metadata explicitly
   rather than omitting it, and points at the runtime's `runtime/legal/<module>/` directories for the JDK and
   JavaFX licenses (GPLv2 with the Classpath Exception).
6. Runs `jpackage --type app-image` with `--runtime-image`, `--main-jar`, `--main-class com.asdasfa.jbs2bg.Launcher`,
   `--app-version` from the pom's `bs2bg.app.version`, `--app-content` for the notices, and one JVM option,
   `--enable-native-access=javafx.graphics` (JavaFX loads its native libraries through a restricted method; JDK 25
   warns without it and a later release will refuse). Because JDK 25 has no jpackage CLI switch for its Windows
   no-restart mode, the script then adds `win.norestart=true` under `[Application]` in the generated launcher
   configuration. It verifies the image: layout (`Assert-AppImageLayout`: launcher, exact payload, runtime, JavaFX
   native libraries, notices, `runtime/legal/java.base/LICENSE`), launcher configuration (`Assert-LauncherConfig`:
   single-process mode, main class, `$APPDIR`-relative classpath covering the jar and every lib, nothing absolute,
   no JavaFX jar, stamped version, required JVM option), jpackage's own state record (`Assert-JpackageState`), the
   bundled runtime's release, and hashes every file into one image digest. The image is archived as
   `target/BS2BG-<version>-windows-x64.zip` (top-level `BS2BG\` preserved).
7. Runs `tools/java25/smoke-app-image.ps1` against that archive (below) and writes
   `target/reproducibility/windows-app-image.json`.

`-SkipVerify` reuses the previous gate run and `-SkipSmoke` skips the launch; both are developer loops and the
evidence records `checkpointResult: false` for them.

## Launcher

`com.asdasfa.jbs2bg.Launcher` is a `final` class with a plain `public static void main(String[])` that calls
`Main.main`. It does not extend `javafx.application.Application`: the JDK launcher special-cases a main class that
does (it reroutes startup through its FX helper, which must resolve `javafx.graphics` as a module before any
application code runs), and a plain `main` keeps that detour out of the packaged startup sequence. `LauncherTest`
pins the shape: `Launcher` is not an `Application`, and `Main` remains the sole `Application` subclass among the
production classes (every emitted class is loaded without initialization and checked), so no second Project flow
can appear. The jar manifest names `Launcher` as well, so the staged jar also runs with `java -jar` plus a JavaFX
module path in a developer loop.

The pom pins the application version once (`bs2bg.app.version`, 1.1.2); `WindowsAppImageGateTest` fails if it
differs from `Main.APP_VERSION`, the value the About dialog renders, so the stamped image and the running
application cannot disagree.

## Packaged smoke run

`smoke-app-image.ps1` extracts the archive to a fresh `%TEMP%` location and starts `BS2BG\BS2BG.exe` from an
empty working directory with the environment scrubbed of every host-Java discovery path (`JAVA_HOME`,
`JDK_JAVA_OPTIONS`, `JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`, `CLASSPATH`, and any PATH entry that looks like a Java
installation; `Get-ScrubbedEnvironment`). The archive must contain exactly one `win.norestart=true` setting, and the
original `BS2BG.exe` must remain the only BS2BG process under the extracted image while it hosts the JVM. The harness
requires `jvm.dll` to be loaded into that process and rejects any observed JVM/JavaFX launcher library whose path is
outside the extracted image. Opening and driving the JavaFX UI then proves the bundled toolkit is operational, so
both "single-process launch" and "without a system Java installation" are observed conditions of the run.

The application is then driven through Windows UI Automation the way an assistive technology sees it:

| Locator kind | Examples |
| --- | --- |
| Accessible role + accessible name | `Button 'Generate Templates'`, `MenuItem 'Open…'`, `TabItem 'Morphs'`, `CheckBox 'Omit Redundant Sliders'`, `Edit 'File name:'` |
| Accessible names declared in `main.fxml` for the Project collections | `List 'Slider Presets'`, `List 'Custom Morph Targets'`, `List 'Target Slider Presets'`, `Table 'NPC Morph Assignments'` (CONTEXT.md vocabulary) |
| Project-domain identity | `ListItem 'CBBE Curvy'`, `ListItem 'All|Female|NordRace'`, the NPC row exposing `HousecarlWhiterun` and `Skyrim.esm` |
| Semantic relationships | the field that follows the `Custom Target:` label, the `Add` button that follows that field, the output area that precedes the `Omit Redundant Sliders` option, the counter that follows `Count:` |

Nothing is located by coordinates, row index, generated automation id, CSS, or JavaFX internals. Text areas are
deliberately not given `accessibleText`: on a JavaFX text input it replaces the spoken *content*, so those are
located by relationship instead.

Workflow steps (each recorded with duration and observations in the evidence):

1. `extract-clean-image` — extract the archive; verify the launcher, its stamped version, and its single-process
   setting; copy the representative and recoverable checked-in Project fixtures into the working directory.
2. `launch-packaged-launcher-without-system-java` — start the launcher, wait for the `jBS2BG` window, require it to
   be the image's only BS2BG process, check the loaded runtime libraries, and note the settings files created in its
   working directory.
3. `verify-first-run-canonical-settings-pair` — require both Settings filenames, canonical UTF-8 bytes without a
   BOM and with a final LF, the accepted built-in Standard/UUNP defaults and inversion families, and no transaction
   directory.
4. `open-recoverable-project` — open the checked-in missing-relationship oracle and require both ordered
   `SLIDER_PRESET_ASSIGNMENT_MISSING` diagnostics plus the dirty/recovered title.
5. `save-recovered-project` — invoke ordinary Save through the adopted identity, require a clean title, and parse
   the canonical file to prove that Alpha/Beta survive while both missing relationships are omitted.
6. `exit-after-project-recovery` — close the first window; require exit code 0 within the bound.
7. `install-legacy-settings-edit` — replace both files with the checked-in legacy Settings oracles and retain their
   exact SHA-256 values as the prior pair for later recovery.
8. `launch-after-legacy-settings-edit` — relaunch the package and require the edited pair to remain byte-identical.
9. `open-representative-project` — File › Open…, the native dialog, the file path, Open; the title becomes
   `jBS2BG - representative.jbs2bg` and `Slider Presets` lists `CBBE Curvy` and `UUNP Athletic`.
10. `generate-preview-copy-and-export-bos-artifact` — select `CBBE Curvy`, open `View BoS JSON`, parse the
   preview, require its Waist values to reflect the imported inversion, require clipboard content to match it after
   normalizing Windows `CF_UNICODETEXT` CRLF line endings, export through the native save dialog, and require the
   exported UTF-8 bytes to equal that same preview exactly without a BOM or final newline.
11. `generate-templates-output` — require the exact Settings-dependent legacy lines
   `CBBE Curvy=Waist@0.74:0.26, Ångström/形@0.0` and `UUNP Athletic=Arms@0.25:0.75`.
12. `load-representative-morph-content` — the Morphs tab lists `All|Female` and the NPC row for
   `Skyrim.esm / HousecarlWhiterun`.
13. `create-custom-morph-target` — `All|Female|NordRace` is added; the title shows the unsaved marker.
14. `assign-slider-presets-to-target` — Add All; `Target Slider Presets` lists both presets and the counter reads 2.
15. `generate-morphs-output` — the Morphs output names both Custom Morph Targets and the NPC.
16. `save-project-as` — File › Save As… to `smoke-output.jbs2bg`; the title is clean; the file is parsed and must
   contain the new target with both presets and the NPC.
17. `prepare-project-save-overwrite` — add `All|Female|SaveRetry` after Save As and require the dirty title.
18. `failed-project-save-preserves-destination-and-lifecycle` — hold the destination open without write/delete
    sharing, invoke Save, require `PROJECT_FILE_WRITE_FAILED`, exact prior bytes and dirty title, and no sibling
    staging file; dismiss the error and release the lock.
19. `retry-project-save-after-overwrite-failure` — invoke ordinary Save again, require the new target in the
    canonical file, a changed hash, and the clean title.
20. `prepare-unsaved-project-for-new` — add `All|Female|Discarded` without saving and require the dirty title.
21. `new-project-discards-confirmed-changes` — invoke New, confirm the discard through the owned dialog, require
    an empty untitled Project, and prove the discarded target was not written into the prior destination.
22. `exit-after-new` — close the second window; the launcher process must exit with code 0 within the bound.
23. `prepare-interrupted-settings-publication` — move the edited pair into the transaction's backups, install only
    a replacement Standard member, and leave the staged UUNP member to model interruption between installs.
24. `recover-settings-relaunch-and-reopen-saved-project` — relaunch, require the exact prior Settings hashes and no
    remaining transaction state, reopen the saved Project, regenerate the exact Settings-dependent Templates lines,
    and verify the Slider Presets, all three Custom Morph Targets, the NPC row, and the assigned target's presets.
25. `close-and-exit` — close the third window; require exit code 0 within the bound.
26. `verify-settings-recovery-diagnostic` — require the stable `SETTINGS_PUBLICATION_RECOVERED` diagnostic in the
    packaged launcher's captured stderr.

Two behaviours of the platform shaped the harness and are worth knowing before changing it:

- A UIA `Invoke` of a JavaFX menu item runs the command inside the UIA callback on the application thread. When
  the command opens a modal `FileChooser`, the dialog's nested event loop runs inside that pending callback and the
  application serves no UIA request for any of its windows until the dialog closes (a `FromHandle` on the dialog
  times out, from any client process). File commands are therefore triggered through the accelerators declared in
  `MainController.setupKeyCombinations` (New `Ctrl+N`, Open `Ctrl+O`, Save `Ctrl+S`, Save As `Ctrl+Alt+S`) after
  each menu item has been verified to exist by role and name; keyboard focus is
  confirmed to be in the application process before the keys are sent, and one bounded re-send is allowed. This
  is a limitation, not an equivalent: the accelerator is the app's own published binding and no locator rule is
  broken, but activation does not go through the located menu item, so a broken menu-to-command wiring would not
  be caught by this run.
- The BoS Copy and Export buttons also enter modal loops (the application notification and the native save
  dialog). They are located by role and accessible name, focused, verified as the focused element, and activated
  with Enter so JavaFX is not held inside a synchronous UIA callback while the harness drives the modal window.
- The native file dialog is owned by the JavaFX window and is listed neither by the UIA desktop root nor by
  JavaFX's own provider; it is located by title through `EnumWindows` and attached with `FromHandle`, accepting
  only a fully formed window. Its Open/Save control is exposed as a bare `Pane` without patterns, so after being
  located by name it is activated with the Win32 `BM_CLICK` message on its own window handle.
- A JavaFX `ListView` that is emptied (New) and refilled (Open) within one process renders its items but no longer
  publishes its cells to UI Automation. New therefore ends the second lifecycle, and the saved Project's lists are
  verified after a fresh-process reopen, which is also stronger proof that it survives exit/relaunch.

Every wait is bounded (`-StartupTimeoutSeconds 90`, `-StepTimeoutSeconds 30`, `-ExitTimeoutSeconds 30`). The
first failing step captures the process's window list, the UIA tree of every visible window, a screenshot, and
the launcher's stdout/stderr into `target/reproducibility/smoke-diagnostics/` before the processes are terminated.
The run must not be interacted with while it drives the application: the accelerators are real keystrokes to the
focused window.

## Evidence

`target/reproducibility/windows-app-image.json` (schema `bs2bg.windows-app-image/2`) records:

- `application`: name, version, main class, main jar, description, vendor.
- `gate`: how the gate was obtained (this run or reused), its test count, Maven version and `--version` output.
- `toolchain`: Temurin implementor/full build/`JAVA_VERSION`/`JAVA_RUNTIME_VERSION`, JavaFX patch, archive
  hashes, architecture (`PROCESSOR_ARCHITECTURE`, JDK `OS_ARCH`, `os.arch`), and `jdeps`/`jlink`/`jpackage`
  versions.
- `payload`: every staged artifact with SHA-256 and size; pointer to `dependency-tree.txt`.
- `runtime`: measured modules, explicit additions with reasons, requested modules, the image's `MODULES` list,
  the service closure (`serviceBindings`), the jlink options, and the runtime `release` values. The service
  closure is the ` binds ` lines of `java --show-module-resolution` run with `--limit-modules` set to the
  image's exact module set (`app-image-module-resolution.txt` holds the whole resolution): every `uses`/
  `provides` binding the JVM actually makes among the modules that ship. `jlink --suggest-providers` is not
  used for this because it lists candidate providers across the whole module path, not what the image resolves.
- `image`: file count, size, the image digest (SHA-256 over every file's path and hash;
  `app-image-sha256.txt` lists them), the archive name and hash, the parsed launcher configuration, the
  jpackage state (tool version, platform), the JVM options, and the notices components.
- `smoke`: the complete smoke evidence (schema `bs2bg.windows-app-image-smoke/4`; steps with durations; Project
  New, recovered Save, Save As, failed-overwrite preservation, retry, and reopen observations; first-run/edited/
  recovered Settings hashes and exact output; expected and observed process models; the three sequential process
  lifecycles with exit codes and exit wait times; environment scrubbing; diagnostics summary including any
  native-access warning lines from stderr, which must be none).

Beside it: `app-image-jdeps-output.txt`, `app-image-jlink-output.txt`, `app-image-jpackage-output.txt`
(`--verbose`), `app-image-module-resolution.txt`, `app-image-sha256.txt`, `windows-app-image-smoke.json`, and
`smoke-diagnostics/` (launcher stdout/stderr, the BoS preview UIA tree, and the UIA tree after the reopen).

`target/` is not versioned, so the checkpoint's evidence is retained verbatim under
`docs/build/evidence/windows-app-image-<date>/`: the two JSON evidence files, the gate's
`java25-verification.json`, the jdeps/jlink/jpackage/module-resolution logs, the per-file image hashes, the
dependency tree and classpath hashes, and the smoke diagnostics (launcher stderr, BoS preview UIA tree, and UIA
tree after the reopen).
Paths inside the evidence are the machine-local paths of the run that produced it. The `gitCommit` in both JSON
files is the commit the run was made *from* — the preceding verified state, not the checkpoint commit — because
the evidence is captured before the commit that retains it exists; the checkpoint commit is the one that added the
`docs/build/evidence/windows-app-image-<date>/` directory. Note that the Maven artifact
is still `jbs2bg-1.0-SNAPSHOT.jar` while the application version is 1.1.2: `project.version` predates the
modernization and is not what the image stamps; aligning it is a separate decision.

The issue #83 BoS writer checkpoint is retained separately under
`docs/build/evidence/windows-app-image-2026-08-28-bos-cutover/`. Its smoke evidence records the canonical BoS
artifact byte count and SHA-256, clipboard parity after the Windows text-format boundary, exact preview/export
byte parity, the BoS popup UIA tree, and both clean launcher lifecycles.

The issue #84 Settings cutover checkpoint is retained separately under
`docs/build/evidence/windows-app-image-2026-08-28-settings-cutover/`. Its smoke evidence records first-run canonical
pair creation, byte-identical legacy editing, exact Standard/UUNP output consumption, interrupted-publication
recovery, the stable recovery diagnostic, and all three clean launcher lifecycles.

The issue #85 Project writer cutover checkpoint is retained separately under
`docs/build/evidence/windows-app-image-2026-08-29-project-writer-cutover/`. Its smoke evidence records New,
recovered-Project Save, canonical Save As, locked overwrite failure with destination/lifecycle preservation,
successful Save retry, fresh-process reopen, and all three clean launcher lifecycles.

## Reverting the Project writer cutover

The production Project writer route, permanent corpus routing evidence, packaged smoke, documentation, and
retained evidence land together in the single issue #85 commit. Reverting that commit restores the prior
minimal-json Project writer and the previous packaging checkpoint. Saved Projects remain semantically compatible
JSON and need no data migration or external-state cleanup.

## Reverting the Settings persistence cutover

The production Settings adapter route, paired publisher/recovery protocol, immutable live publication, consumer
test migration, packaged smoke, documentation, and retained evidence land together in the single issue #84 commit.
Reverting that commit restores the prior independent minimal-json Settings loader/writer and the previous packaging
checkpoint. The generated Settings files remain backward-compatible JSON and need no data migration or external
state cleanup.

## Reverting the BoS writer cutover

The production writer route, filename policy, publisher, UI wiring, packaged smoke, documentation, and retained
evidence land together in the single issue #83 commit. Reverting that commit restores the prior formatter/String
route and its previous packaging checkpoint without a data migration or any external-state cleanup.

## Reverting this checkpoint

The checkpoint is one commit on top of the verified #96 state and reverts independently: `git revert` restores
the previous pom (jar in `target/`, JavaFX at compile scope, no staging execution), removes `Launcher`, the
packaging scripts, the two gate tests, ADR-0003, and this document, and restores ADR-0001's status note. Nothing
outside the repository needs undoing: the toolchain cache is the same content-addressed cache #94/#96 use, the
smoke run's extracted image and working directory live under a fresh `%TEMP%` path that is removed after the run,
and the only production behaviour changes are the `Data` preference-node fallback (a per-user Preferences node
keyed by class name instead of by install directory; an existing per-directory node is simply no longer read)
and four accessible names in `main.fxml`.

## Out of scope for this checkpoint

Traditional installers (MSI/EXE), code signing, publication, automatic updates, file associations, shortcuts, and
the release accessibility/DPI/theme matrix are not part of this checkpoint; see
[windows-portable-release-policy.md](../research/windows-portable-release-policy.md).

## Tests

`Invoke-Pester -Path tools/java25` covers the deterministic parts (`WindowsAppImage.Tests.ps1`: staged payload,
jdeps parsing, module resolution, single-process launcher configuration and mutation, jpackage state, image layout,
tree digest, runtime release, environment scrubbing, notices). `LauncherTest` and `WindowsAppImageGateTest` run in
the Maven build. The launcher and the image are exercised only by the packaging run itself.
