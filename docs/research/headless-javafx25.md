# Headless JavaFX 25 for the workbench tests on Windows (issue #121)

Research date: 2026-10-13. Sources are OpenJFX source (`openjdk/jfx`, `openjdk/jfx25u`), the OpenJDK bug system (JBS), OpenJFX release notes, and the published Maven artifacts. Behaviour claims marked **(probe)** come from a throwaway experiment run on this machine against the pinned Temurin 25.0.4.1+1 and JavaFX 25.0.4 jars (see [Experiment](#experiment)). Claims marked **(unverified)** were not confirmed.

## Answer

**Yes, but only through Monocle.** JavaFX 25 does not ship a built-in headless Glass platform. The OpenJFX `Headless` platform is JDK-8324941, not JDK-8324658, and it first shipped in JavaFX 26. On the repo's JavaFX 25.0.4, the only headless option is Monocle's `Headless` sub-platform. Monocle's source is in the JavaFX 25 tree but is left out of the Windows jars, so it has to come from a separately built jar. The only published build is TestFX's `org.testfx:openjfx-monocle:21.0.2`, which targets JavaFX 21.

With that jar on the test classpath and `-Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw`, the **whole BS2BG suite (568 tests) passes** on JavaFX 25.0.4 **(probe)**. Two caveats apply. First, the default 1280x800 headless screen is too small for the fractional render-scale tests. They pass, but they leave uncaught `BufferOverflowException`s on the FX thread. Adding `-Dheadless.geometry=3840x2400-32` removes them. Second, the JavaFX 21 build of Monocle has version skew against JavaFX 25. It works only on the software pipeline, and with D3D it throws `AbstractMethodError`.

Recommended candidate: **TestFX Monocle 21.0.2 + `prism.order=sw` + an enlarged `headless.geometry`**. This is a stopgap until the project moves to JavaFX 26+ and can switch to the built-in `-Dglass.platform=headless`. Both platforms behave the same for focus, accessibility and preferences (see the comparison below), so tests written against one should carry over to the other.

## Candidates

### Built-in Glass `Headless` platform: not available in JavaFX 25

- The issue is **JDK-8324941 "POC for Headless platform for JavaFX"**, Fix Version **jfx26**, label `javafx-rn-feature`. It proposes "a headless platform that can be set via `-Dglass.platform=Headless`". ([JDK-8324941](https://bugs.openjdk.org/browse/JDK-8324941))
- JDK-8324658 is unrelated. It is "Allow animation play/start/stop/pause methods to be called on any thread", jfx23. ([JDK-8324658](https://bugs.openjdk.org/browse/JDK-8324658))
- The code landed in `openjdk/jfx` on 2025-07-28 in [commit 3c4d290](https://github.com/openjdk/jfx/commit/3c4d29057999af89f3264810cb48a2819fdecf8e) as package `com.sun.glass.ui.headless`. That package is present at tag `26-ga` and absent at `25-ga`. It is also absent from the `openjdk/jfx25u` update tag `25.0.4+3`, which has only `android, delegate, gtk, ios, mac, monocle, win` under `com/sun/glass/ui`. ([jfx 26-ga headless package](https://github.com/openjdk/jfx/tree/26-ga/modules/javafx.graphics/src/main/java/com/sun/glass/ui/headless), [jfx25u 25.0.4+3 glass/ui](https://github.com/openjdk/jfx25u/tree/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/glass/ui))
- No backport exists. A JBS search for JavaFX issues with "headless" in the summary shows only jfx26/jfx28/tbd fix versions for this work. ([JBS search](https://bugs.openjdk.org/issues/?jql=project%3DJDK%20AND%20component%3Djavafx%20AND%20summary~%22headless%22))
- The JavaFX 26 release notes list JDK-8324941 under "New Features", plus JDK-8364687 "Enable headless with -Dglass.platform=headless" and JDK-8369306 "Implement invokeAndWait and finishTerminating on headless platform". The JavaFX 25 release notes do not mention headless. ([release-notes-26.md](https://github.com/openjdk/jfx/blob/27-ga/doc-files/release-notes-26.md), [release-notes-25.md](https://github.com/openjdk/jfx/blob/27-ga/doc-files/release-notes-25.md))
- Trying it on 25.0.4 fails at startup with `ClassNotFoundException: com.sun.glass.ui.headless.HeadlessPlatformFactory`, followed by an NPE from `PlatformFactory.createApplication()` **(probe)**. `Platform.determinePlatform()` in 25 passes unknown `glass.platform` values through verbatim, and `PlatformFactory` builds the class name `com.sun.glass.ui.<lower>.<Value>PlatformFactory` from them. ([Platform.java @25.0.4+3](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/glass/ui/Platform.java), [PlatformFactory.java @25.0.4+3](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/glass/ui/PlatformFactory.java))
- The repo pins `javafx.version` 25.0.4 (`pom.xml:25`). JavaFX 26, 26.0.1, 26.0.2 and 27 are on Maven Central. ([javafx-graphics metadata](https://repo1.maven.org/maven2/org/openjfx/javafx-graphics/maven-metadata.xml)) `javafx-graphics-27-win.jar` contains the `com/sun/glass/ui/headless/` classes **(probe)**.
- Its maturity is still "POC". Follow-ups remain open: JDK-8364201 "Configuration for headless platform", JDK-8364209 "add implementations for unimplemented headless platform methods", JDK-8364211 "Allow multiple windows to be active in headless mode" and JDK-8364204 "Clipboardtest fails on headless". JDK-8391883 (KeyCharacterCombination exception) is fixed only in jfx28. ([JDK-8324941 links](https://bugs.openjdk.org/browse/JDK-8324941))

### Monocle: viable on JavaFX 25, but only from a JavaFX 21 build

- Monocle's source, including `HeadlessPlatform`, `HeadlessPlatformFactory` and `HeadlessScreen`, is in the JavaFX 25 tree under `com.sun.glass.ui.monocle`. ([monocle @25.0.4+3](https://github.com/openjdk/jfx25u/tree/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/glass/ui/monocle))
- It is **not** in the Windows desktop binaries. `javafx-graphics-25.0.4-win.jar` has 0 `monocle` entries and its only glass sub-package is `com/sun/glass/ui/win/`. The pinned Gluon `javafx.graphics.jmod` (25.0.4) also has 0 **(probe)**. TestFX's README says the same: Monocle is "not included in builds for desktop platforms (Windows, Linux, Mac)". ([TestFX/Monocle README](https://github.com/TestFX/Monocle))
- `github.com/openjfx/monocle` does not exist (GitHub API 404). TestFX's README says the official Monocle source is `openjdk/jfx`.
- Published builds: `org.testfx:openjfx-monocle` has `21.0.2` (released 2024-02) as its latest version, with no 22–27 builds. ([maven-metadata.xml](https://repo1.maven.org/maven2/org/testfx/openjfx-monocle/maven-metadata.xml)) TestFX/Monocle has branches `jdk-11`, `jdk-17` and `jdk-21` only, and its last push was 2024-02-27 (GitHub API). The artifact is GPLv2 with the Classpath Exception (its POM). Its JavaFX dependencies are `provided`, so it adds no transitive JavaFX 21 jars and should not trip the `dependencyConvergence` enforcer rule **(unverified: not added to the POM)**.
- **Version skew, confirmed.** JavaFX 25 changed `View._getNativeFrameBuffer` from `abstract int` (21-ga, 22-ga, 23-ga, 24-ga) to `abstract long` (25.0.4+3, `View.java:773`). The 21.0.2 jar's `MonocleView` still declares `int _getNativeFrameBuffer(long)` (javap). On the D3D pipeline, every present therefore throws `AbstractMethodError: ... MonocleView does not define ... 'abstract long _getNativeFrameBuffer(long)'` **(probe)**. The software pipeline never calls it, so `prism.order=sw` is required, not optional. The JavaFX 25 in-tree `MonocleView` does implement the `long` form (`MonocleView.java:180`). Building Monocle from the `jfx25u` tag the project pins would remove the skew (**unverified**: not built).

## Enabling it

Enabling properties (JavaFX 25.0.4 + Monocle 21.0.2):

| Property | Value | Why |
| --- | --- | --- |
| `glass.platform` | `Monocle` (case-sensitive) | `PlatformFactory` loads `com.sun.glass.ui.monocle.MonoclePlatformFactory` |
| `monocle.platform` | `Headless` | Default cascade is `MX6,OMAP,Dispman,Android,X11,Linux,Headless` ([NativePlatformFactory.java](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/glass/ui/monocle/NativePlatformFactory.java)); naming `Headless` skips the probing |
| `prism.order` | `sw` | Required because of the version skew above; also keeps the run GPU-free |
| `headless.geometry` | e.g. `3840x2400-32` (`WxH-depth`) | Default screen is 1280x800x32 ([HeadlessScreen.java:42-71](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/glass/ui/monocle/HeadlessScreen.java)); see the overflow caveat below |

**Surefire / module-path compatibility.**

- The repo has no `module-info.java` in `src/` or `test/`, so Surefire runs the tests on the **classpath**. JavaFX already loads from the unnamed module there, which is why the existing "Unsupported JavaFX configuration: classes were loaded from 'unnamed module'" warning appears. On the classpath, the Monocle jar simply adds `com.sun.glass.ui.monocle` to the unnamed module, and no `--add-exports` or `--add-opens` flags are needed **(probe)**.
- The existing `argLine` (`--enable-native-access=ALL-UNNAMED`, `pom.xml:306`) can stay as it is. `Java25ToolchainGuardTest` only forbids `--enable-preview`.
- The properties can be supplied as forked-JVM system properties. JavaFX reads them at toolkit startup, and `FxTestToolkit.ensureStarted()` is lazy, so they are in place before the toolkit starts. Two routes were tried:
  - JVM flags through `JDK_JAVA_OPTIONS`, plus the jar through Surefire's `maven.test.additionalClasspath`: full suite green.
  - Maven user properties (`-Dglass.platform=Monocle ...`), which Surefire's booter sets in the fork: `WorkbenchControllerTest` green on Monocle, confirmed by Monocle frames in the stack traces.

  Both routes passed **(probe)**. A literal `<systemPropertyVariables>` block was not tried. It uses the same booter mechanism and is expected to behave identically **(unverified)**.
- Module path: the jar's manifest declares `Automatic-Module-Name: org.testfx.monocle`. A module-path run would need `--add-modules` and `--add-exports javafx.graphics/com.sun.glass.ui…=org.testfx.monocle` style flags **(unverified; not needed for this repo)**.

**JavaFX 26+ built-in alternative.** Use `-Dglass.platform=headless` (lowercase is mapped by JDK-8364687, `Platform.java:57` at 27-ga) or `Headless`, plus `-Dprism.order=sw`, with no extra jar. In the probe, JavaFX 27 also ran on D3D without errors, but `sw` is the documented and GPU-free choice.

## Behaviour (JavaFX 25.0.4 + Monocle Headless, compared with the Windows glass control run)

| Question | Monocle (25.0.4) | Built-in Headless (27, for comparison) | Windows glass (control) |
| --- | --- | --- | --- |
| `Stage.show()` | Shows; `isShowing()` true; bounds = requested 400x300 (no decorations) | Same | 416x339 (OS frame included) |
| `Screen` | 1 screen, 1280x800 (or `headless.geometry`), visual = full bounds, 96 DPI, output scale 1.0 | 1 screen, 1000x1000, 100 DPI, scale 1.0, hard-coded (`HeadlessApplication.java:167`) | Real monitors (here 2 screens, 2560x1440, 110 DPI) |
| `localToScreen` | Deterministic (window x/y + scene offset, no frame insets) | Same | Includes OS frame insets |
| `Popup` show / position | Shows at the requested coordinates | Same | Same |
| `Popup` autoHide on click outside | Hides (Robot press outside, event consumed) | Same | Not probed (would move the real mouse) |
| `Popup` autoHide when the owner loses focus | **Never fires**: owner never loses focus | **Never fires** | Hides |
| `ContextMenu.show` | Works | Works | Works |
| `Platform.enterNestedEventLoop` / `showAndWait` | Works (`RunnableProcessor`) | Works (`NestedRunnableProcessor`) | Works |
| `Window.renderScaleX/Y` override | Honoured (1.5 sticks; `outputScale` stays 1.0) | Honoured | Honoured |
| Shown Stage `isFocused()` | `true` immediately | `true` | `true` (if the OS grants activation) |
| First Stage after a second Stage is shown | **Still `true`** (both report focused) | **Still `true`** | `false` |
| `Node.isFocused()` / `Scene.getFocusOwner()` | Deterministic: `requestFocus()` moves both synchronously | Same | Same while the window holds OS focus; `Node.isFocused()` drops if the OS deactivates the window |
| `Platform.isAccessibilityActive()` | `false`, and cannot become true | `false`, cannot become true | `false` until a UIA client connects |
| `Application.createAccessible()` | `null` | `null` | `WinAccessible` |
| `Platform.getPreferences()` | Empty map; `LIGHT`, background `#FFFFFF`, foreground `#000000`, accent `#157EFB`, no `Windows.SPI.HighContrast` key | Same | 22 `Windows.*` keys, host-dependent (this host: `DARK`, accent `#007AD6`, HighContrast `false`) |
| `FileChooser` / `DirectoryChooser` | Throw `UnsupportedOperationException` | Throw | Native dialogs |

Source anchors for the table:

- **Focus.** `MonocleWindowManager.requestFocus` sets `focusedWindow` and sends `FOCUS_GAINED` to the new window only. It never sends `FOCUS_LOST` to the previous one, and `grabFocus`/`ungrabFocus` are no-ops (`MonocleWindowManager.java:125-145`). JavaFX 27's `HeadlessWindow._requestFocus` likewise only calls `notifyFocus(event)` (`HeadlessWindow.java:178`). `Node.focused` is derived from the Scene's `windowFocused` flag (`Scene.java:2173-2180` @25.0.4+3). That is why focus-owner state is deterministic headless: windows never lose focus. ([MonocleWindowManager.java](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/glass/ui/monocle/MonocleWindowManager.java), [HeadlessWindow.java @27-ga](https://github.com/openjdk/jfx/blob/27-ga/modules/javafx.graphics/src/main/java/com/sun/glass/ui/headless/HeadlessWindow.java), [Scene.java](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/javafx/scene/Scene.java))
- **AutoHide.** `PopupWindow` auto-hides on `MOUSE_PRESSED` outside the popup or on a `FocusUngrabEvent` (`PopupWindow.java:1022-1029`). Headless platforms never generate the ungrab, so owner-deactivation hiding cannot occur. ([PopupWindow.java](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/javafx/stage/PopupWindow.java))
- **Accessibility.** The base `Application.createAccessible()` returns `null` (`Application.java:662`). `MonocleApplication` and JavaFX 27's `HeadlessApplication` do not override it, while `WinApplication` does (`WinApplication.java:285`). `Platform.accessibilityActive` is set true only when `Scene` lazily builds its platform accessible (`Scene.java:5925-5975`), and only a native UIA request triggers that. Node-level `queryAccessibleAttribute` still works, because it never touches the platform accessible. `WorkbenchControllerTest`'s `VERTICAL_SCROLLBAR` query passes under Monocle **(probe)**. A forced `Node.getAccessible()` would NPE headless (`Node.java:10533-10535`), but only glass calls it. ([Application.java](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/glass/ui/Application.java), [Node.java](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/javafx/scene/Node.java))
- **Preferences.** The base `Application.getPlatformPreferences()` returns `Map.of()` (`Application.java:774-776`). The platform-independent defaults are in `PreferenceProperties.java:57-64` (white, black, `rgb(21,126,251)`, reduced-* `false`). `WinApplication.getPlatformPreferences()` is native (`WinApplication.java:400`). The high-contrast stylesheet is reachable headless only through the `com.sun.javafx.highContrastTheme` system property (`PlatformImpl.java:696`). JavaFX 25 also adds per-scene overrides (`Scene.getPreferences()`, `@since 25`), which apply on any platform. ([PreferenceProperties.java](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/javafx/application/preferences/PreferenceProperties.java), [PlatformImpl.java](https://github.com/openjdk/jfx25u/blob/25.0.4%2B3/modules/javafx.graphics/src/main/java/com/sun/javafx/application/PlatformImpl.java))
- **Screen and scale.** `HeadlessScreen.getScale()` is fixed at `1.0f` and `getDPI()` at `96` (`HeadlessScreen.java:94-105`). Windows-only knobs such as `glass.win.uiScale` (`WinApplication.java:91`) have no Monocle equivalent. To simulate HiDPI, set `Window.renderScaleX/Y` explicitly, as `WorkbenchControllerTest.shortCatalogsFitAllRowsAtFractionalRenderScales` already does.
- **Dialogs.** `MonocleApplication.staticCommonDialogs_showFileChooser` and `showFolderChooser` throw (`MonocleApplication.java:263-277`). JavaFX 27 `HeadlessApplication` also throws, and its `_getKeyCodeForChar` throws too (`HeadlessApplication.java:196, 242`), which is the JDK-8391883 KeyCharacterCombination failure fixed in jfx28.

## Caveats found running the real suite

1. **Framebuffer overflow at the default geometry.** With the default 1280x800 screen, `shortCatalogsFitAllRowsAtFractionalRenderScales` produced 16 uncaught `java.nio.BufferOverflowException`s. They come from `Framebuffer.composePixels` ← `HeadlessScreen.uploadPixels`, on the FX thread inside `settleCatalogLayout`'s nested loop (`WorkbenchControllerTest.java:757, 778`). The cause is a render-scaled window larger than the fake screen. The tests still **passed**, because `FxTestToolkit` does not fail on uncaught FX-thread exceptions. `-Dheadless.geometry=3840x2400-32` removed every occurrence **(probe)**. A headless harness should also fail tests on uncaught FX-thread exceptions; otherwise a rendering fault stays silent.
2. **Focus assertions become weaker, not flaky.** Every shown window reports focused, so assertions that focus returns to an owner after a dialog closes, or that a popup closes when its owner deactivates, pass vacuously or cannot be expressed. In-scene focus-owner assertions (`scene.getFocusOwner()`, the bulk of `WorkbenchControllerTest`) remain meaningful and become independent of whether the developer clicks elsewhere during the run.
3. **Preferences become host-independent.** This is a fidelity loss and a determinism gain. Under Windows glass, `Platform.getPreferences()` follows the developer's OS theme: this host reported `DARK`. Production `WorkbenchController` feeds `Platform.getPreferences()` into the appearance (`WorkbenchController.java:3671`), so today's results can depend on the host theme. Headless always reports `LIGHT` with no high-contrast key.
4. **Version skew is a standing risk.** The 21.0.2 Monocle jar is binary-coupled to internal `com.sun.glass.ui` classes that changed in JavaFX 25. It works on the sw path today, but any later 25.0.x internal change can break it without a compile error. Pinning its checksum and adding a startup guard (for example, asserting `Platform.getPreferences().isEmpty()` or the configured screen bounds, both public API) would turn that into a fast, explicit failure.

## Fidelity lost compared with Windows glass

- No HWND, DWM, window frame, or insets. No taskbar-reduced visual bounds. One fake screen at scale 1.0. No real per-monitor DPI or monitor-change events.
- Software rendering only (Prism `SWPipeline`) instead of D3D, so pixel output can differ. Fonts still come from the host's DirectWrite: 272 families on both platforms **(probe)**.
- No OS activation model. Windows never lose focus, there is no `FOCUS_LOST`, and there are no focus-ungrab–driven popup closes.
- No accessibility provider at all. UIA, Narrator and `Platform.accessibilityActive` cannot be exercised (issue #122's subject). Node-level accessible attributes remain testable.
- No platform preferences: no dark mode, accent colour or `Windows.SPI.HighContrast*` keys.
- No native file and folder choosers (they throw). Input is synthetic (Monocle Robot / `LinuxInputDeviceRegistry(true)`). IME, real keyboard layouts and the Windows clipboard are not exercised. Monocle has its own `MonocleSystemClipboard` (semantics not probed).
- None of the above affects the packaged smoke run (`package-java25.ps1`), which stays the real-desktop check.

## Experiment

Everything ran outside the repo except the Maven runs, which used this research worktree with no source or POM edits.

- **Probe.** `Probe.java`, a single-file program, was run with `java.exe` from Temurin 25.0.4.1+1 and JavaFX 25.0.4 `-win` jars on the classpath. It covered five variants: Windows glass; Monocle 21.0.2 with sw; Monocle with default D3D; `glass.platform=Headless` on 25.0.4; and JavaFX 27 `headless` with sw and with D3D. It prints the glass `Application` class, Prism pipeline, `Screen` geometry, preferences, Stage, Node and Scene focus across two stages, a `Popup` (autoHide, Robot click outside, second stage shown), a `ContextMenu`, a nested event loop, `showAndWait`, a render-scale override, and a scene snapshot.
- **Suite.**
  - `./mvnw.cmd test` with `BS2BG_JDK25_HOME` set to the pinned JDK, `JDK_JAVA_OPTIONS="-Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw"` and `-Dmaven.test.additionalClasspath=<openjfx-monocle-21.0.2.jar>`: **568 tests, 0 failures, 0 errors, 0 skipped**, 32.7 s total, with the overflow traces described above. Surefire's report recorded `glass.platform=Monocle`, `prism.order=sw`, `monocle.platform=Headless` and the Monocle jar on the classpath.
  - Re-running `WorkbenchControllerTest` with `headless.geometry=3840x2400-32`: 73/73, no overflow.
  - Re-running it with the properties as Maven user properties instead of JVM flags: 73/73, Monocle frames present.
- **Not done.** A Windows-glass baseline suite run in this session. A Monocle build from `jfx25u` sources. Running the BS2BG suite on JavaFX 26/27.
