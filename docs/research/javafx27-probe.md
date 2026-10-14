# BS2BG suite on JavaFX 27: Windows glass and built-in headless (issue #132)

Probe date: 2026-10-13. Tier 1 (`.\mvnw.cmd clean test`) was run on this machine against the pinned Temurin
`25.0.4.1+1` toolchain, with `javafx.version` changed from `25.0.4` to `27` in `pom.xml`. That one-line POM change is
the only edit, and it is committed only on this `research/javafx27-probe` branch. It is not for `main`. Sources are
the Surefire reports, the OpenJFX source at tag `27-ga`, the JavaFX 26 and 27 release notes, and JBS.

## Answer

**Nothing breaks.** On JavaFX 27 (`javafx.runtime.version=27+30`), the whole suite is green on both platforms.
Compilation is clean under `-Xlint:all -Werror`. Every enforcer rule passes. No FX-thread exception appears in any
run.

| Run | Platform properties | Tests | Fail | Error | Skip | FX-thread exceptions | Wall time |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Control: JavaFX 25.0.4 | none (Windows glass, D3D) | 568 | 0 | 0 | 0 | 0 | 40 s |
| JavaFX 27, Windows glass | none (Windows glass, D3D) | 568 | 0 | 0 | 0 | 0 | 34 s |
| JavaFX 27, built-in headless | `-Dglass.platform=headless -Dprism.order=sw` | 568 | 0 | 0 | 0 | 0 | 31 s |
| JavaFX 27, built-in headless (repeat) | same | 568 | 0 | 0 | 0 | 0 | 35 s |

`WorkbenchControllerTest` took 6.8 s on 25 glass, 6.5 s on 27 glass, and 6.4 s and 7.1 s on 27 headless. The
headless runs need **no screen-geometry setting** (see [Screen geometry](#screen-geometry)). They also need no extra
jar and no `--add-exports`.

## Compile and lint

- `compile` and `testCompile` both pass with `-Xlint:all -Werror` on JavaFX 27. No new deprecation or removal
  warning fires at any BS2BG use site.
- **`FXPermission`**: JavaFX 26 removed it (JDK-8359759, "Removed Features and Options" in the 26 release notes).
  Neither `src/` nor `test/` references it, so the removal has no effect here.
- No other removal in the 26 or 27 release notes touches an API that BS2BG uses.

## Enforcer rules

All seven rules in `enforce-pinned-build-inputs` passed with `org.openjfx:*:27`: `requireMavenVersion`,
`requireJavaVersion`, `requireOS`, `requirePluginVersions`, `banDynamicVersions`, `dependencyConvergence` and
`bannedDependencies`.

- The JavaFX 27 POMs pull in only `javafx-base`, `-graphics`, `-controls` and `-fxml` (plain and `win` jars), so
  there is nothing to converge.
- No `javafx-incubator-*` artifact arrives transitively, so `bannedDependencies` stays satisfied.

## Failures per run

None. There is no failing or erroring test to attribute to a JavaFX 27 behaviour change, a headless-platform gap, or
a desktop dependency. The desktop dependencies already known to the map are still present under Windows glass:
focus checks on `isFocused()`, real Stages and host preferences. They did not fail in this run because nobody used
the desktop during it. Under headless they cannot fail, because windows never lose focus.

## Uncaught FX-thread exceptions

None, in any run. The only stderr output in any run is the existing
`WARNING: Unsupported JavaFX configuration: classes were loaded from 'unnamed module ...'`, which JavaFX 25 also
prints.

This is a change from the Monocle probe in [#121](https://github.com/evildarkarchon/BS2BG/blob/research/headless-javafx25/docs/research/headless-javafx25.md).
There, `shortCatalogsFitAllRowsAtFractionalRenderScales` left 16 `BufferOverflowException`s at Monocle's default
geometry. The JavaFX 27 headless platform does not reproduce them. Two caveats follow:

- **Why it does not overflow.** `UploadingPainter` builds the upload `Pixels` at the view's *output* size
  (`getOutputWidth()`, `UploadingPainter.java:146-157`). The headless screen's output scale is fixed at `1.0`, so a
  render scale of `1.75` does not enlarge the upload. The 1300x800 test scene uploads 1300x800 pixels.
- **Failures are silent.** A window that lies partly off the fake screen loses its pixels silently, and nothing is
  thrown. In a throwaway probe, a 1300x1100 scene was centred at `y=-33`. The probe read back black pixels and saw no
  exception on stderr or through an uncaught-exception handler. Any rendering exception that does occur is printed by
  `UploadingPainter`'s `catch (Throwable) { printStackTrace }` (`:185-187`) and never reaches an uncaught-exception
  handler. A harness that fails tests on FX-thread exceptions therefore will not catch blit faults. No BS2BG test
  reads screen pixels (no `Robot` pixel reads, `snapshot` or screen capture), so this does not affect the current
  suite.

## Screen geometry

JavaFX 27 has no headless screen setting. `HeadlessApplication.staticScreen_getScreens()` hard-codes one 1000x1000
screen at 100 DPI and scale 1.0, and its framebuffer is sized to match (`HeadlessApplication.java:166-178` at
`27-ga`). Configuration is JDK-8364201, which is still open. Monocle's `headless.geometry` does not apply to this
platform.

- The suite's largest scene is 1300x800. It passes on the 1000x1000 screen; the window is centred at `x=-150`.
- `Window.renderScaleX/Y` overrides are honoured, so the fractional render-scale tests exercise their snapping
  logic. They do not exercise real per-monitor DPI, which stays a Tier 3 concern.

## Confirmed platform state in the forked test JVM (probe)

A throwaway test, run once under headless and deleted afterwards, reported the following:

- `glass.platform=headless`, `prism.order=sw` and `javafx.runtime.version=27+30`.
- One screen at 1000x1000, 100 DPI, output scale 1.0.
- `Platform.getPreferences()` was empty, with colour scheme `LIGHT`.
- `accessibilityActive` was `false`.
- A shown Stage reported `isFocused()=true`.

This matches the platform behaviour table in the #121 research. Maven user properties (`-D...` on the `mvnw`
command line) reach the Surefire fork as system properties before `FxTestToolkit.ensureStarted()` starts the toolkit,
so no `argLine` change was needed.

## JavaFX 26/27 changes relevant to the UIA ListView workarounds

The workarounds are in `WorkbenchController`:

- **Node replacement on refill**: `reconcileCustomMorphTargetItems`, `replaceEmptySliderPresetList`, and the other
  `replaceEmpty*` methods.
- **No-op `setAll` avoidance**: `reconcileSliderPresetItems`.
- **Edit lifecycle instead of `refresh()`**: `synchronizeRenameEditor`.

Their effect is visible only to a UIA client, so Tier 1 can neither confirm nor refute them on 27. A commit search
of `openjdk/jfx` at `27-ga` since 2025-06-01 found:

- **`WinAccessible.java`: no commits.** The Windows UIA provider that the workarounds target is unchanged since
  JavaFX 25. The "empty virtualized subtree kept for the control's lifetime" and "no-op empty/refill drops every
  cell" behaviours therefore have no visible fix. Expect the node-replacement and `setAll`-avoidance workarounds to
  still be needed **(unverified on 27: needs Tier 3)**.
- **`VirtualFlow.java`, `ListCell.java`: no commits.**
- **`ListView`, `ListViewSkin` and `VirtualContainerBase`: one behavioural commit,
  [JDK-8359599](https://bugs.openjdk.org/browse/JDK-8359599)** "Calling refresh() for all virtualized controls
  recreates all cells instead of refreshing the cells" (jfx26). Since 26, `refresh()` calls `rebuildCells()`, which
  re-indexes the existing cells, instead of `recreateCells()`, which discards them. The `synchronizeRenameEditor`
  workaround exists because `refresh()` "drops JavaFX 25 UIA children after rejected edits". Discarding cells is
  the most likely cause of that drop. On 27, `refresh()` may therefore no longer drop UIA children, and the
  workaround's stated reason may no longer hold.
  - Keeping the workaround is harmless, since the edit lifecycle is correct on any version.
  - Removing it needs a Tier 3 check **(unverified)**.
- [JDK-8385533](https://bugs.openjdk.org/browse/JDK-8385533) (jfx27) removes `Cell`'s redundant listener for the
  `focused` pseudo-class. The pseudo-class is still set by `Node`, so behaviour is unchanged.
- [JDK-8202066](https://bugs.openjdk.org/browse/JDK-8202066) (jfx27) speeds up ListView multiple-selection changes.
  BS2BG's catalogs are small, so there is no expected effect.
- The accessibility entries in both release notes are macOS (NSAccessibility) or documentation fixes. None touches
  the Windows provider.

## Other headless-platform gaps checked against the code

- **JDK-8391883** (`KeyCharacterCombination` throws headless, fixed in jfx28): not reachable. Production uses only
  `KeyCodeCombination` accelerators (`WorkbenchController.java:671, 816-820`), and `KeyCodeCombination.match` never
  calls `_getKeyCodeForChar`. No test workaround is needed today. Any future `KeyCharacterCombination` would need one
  until jfx28.
- **File and directory choosers throw headless**: not reached. `JavaFxWorkbenchPlatformTest` covers only the
  extension policy around the chooser, and the workbench tests inject `RecordingPlatform`.
- **System clipboard** (JDK-8364204, open): not reached. Only `JavaFxWorkbenchPlatform` and `JavaFxWorkbenchDialogs`
  touch `Clipboard.getSystemClipboard()`, and no test calls those paths.
- **Preferences**: `JavaFxWorkbenchAppearance` reads `Platform.getPreferences()`. Headless always reports an empty
  map and `LIGHT`, so the appearance tests become host-independent. The High Contrast–sensitive behaviour noted on
  the map cannot be exercised headless, except through `Scene.getPreferences()` overrides or the
  `com.sun.javafx.highContrastTheme` property.

## What the upgrade issue must also touch (outside Tier 1)

Tier 1 needs only the `javafx.version` change. Tier 2 and Tier 3 will fail closed until the following are updated:

- `tools/java25/toolchain-lock.json` `javafx` block: id, version, URL, SHA-256, source URL and revision, and
  `archiveRootDirectory`.
  - The Gluon URL `https://download2.gluonhq.com/openjfx/27/openjfx-27_windows-x64_bin-jmods.zip` resolves (HTTP 200,
    46,337,288 bytes).
  - Its SHA-256 was not recorded here.
- `tools/java25/Java25Toolchain.Tests.ps1:39` asserts `$lock.javafx.version` is `'25.0.4'`. Its fixture strings
  (`javafx.base@25.0.4`, lines 155-175) are only examples and can stay. The fixture name in
  `WindowsAppImage.Tests.ps1:66` is cosmetic.
- Prose that names "JavaFX 25":
  - `pom.xml:13, 229`
  - `docs/build/java25-verification.md:4, 21, 73, 86-87`
  - `docs/build/windows-app-image.md:114`
  - `docs/build/workbench-platform.md:3`
  - `ProductionSourceGateTest` Javadoc
  - `verify-java25.ps1` and `package-java25.ps1` headers
  - the UIA workaround comments in `WorkbenchController` and `tools/java25/{smoke-app-image.ps1,UiaAutomation.psm1}`

  The workaround comments should say "JavaFX 25+" or name the version they were last confirmed on, rather than
  silently become wrong.
- Neither Tier 2 nor Tier 3 was run in this probe.

## Reproduce

From a checkout of this branch, in Git Bash:

```sh
export BS2BG_JDK25_HOME="$LOCALAPPDATA/BS2BG/build-toolchains/temurin-25.0.4.1+1-windows-x64"
export JAVA_HOME="$BS2BG_JDK25_HOME"
./mvnw.cmd -B clean test                                             # JavaFX 27, Windows glass
./mvnw.cmd -B clean test -Dglass.platform=headless -Dprism.order=sw  # JavaFX 27, built-in headless
```

The Windows-glass run shows real Stages on the desktop. The headless run shows none.
