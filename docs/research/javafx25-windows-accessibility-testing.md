# JavaFX 25 accessibility and UI testing on Windows

Research date: 2026-08-27

## Question and scope

This note establishes what JavaFX 25 exposes on Windows for accessibility, keyboard and focus behavior, system appearance, DPI scaling, UI Automation, and test automation. It also records the limits that later planning must account for. It intentionally does not define final acceptance criteria.

The baseline is JavaFX 25 on JDK 25, packaged as a self-contained Windows application with `jpackage`. Sources are limited to JavaFX/JDK documentation and source, OpenJDK project artifacts, Microsoft accessibility and automation documentation, and the first-party documentation of the automation projects discussed below.

## Findings at a glance

- JavaFX 25 has a real Windows UI Automation (UIA) provider, not merely Java Access Bridge compatibility. Its native provider implements the core raw-element interfaces and common patterns for invoking, selecting, editing, toggling, expanding, scrolling, ranges, grids, tables, and text. Standard JavaFX controls therefore provide a viable accessibility and external-automation foundation.
- JavaFX's public accessibility model is deliberately finite. A node can expose a JavaFX `AccessibleRole`, accessible text, help, role description, role-dependent attributes/actions, and change notifications. Custom workbench controls must actively supply those semantics; an attractive scene graph is not automatically an accessible control.
- The Windows provider has material limitations for tests: JavaFX generates its own `AutomationId` (`JavaFX` plus a runtime counter) instead of exposing `Node.id`/FXML `fx:id`; it currently reports every exposed element as keyboard-focusable; and it does not expose UIA live-region or notification APIs. UIA inspection and manual keyboard/screen-reader testing cannot be replaced by a green in-process JavaFX test suite.
- JavaFX 25 exposes observable Windows dark/light, accent, reduced-motion, reduced-transparency, persistent-scrollbar, high-contrast, and system-color data. A custom BS2BG theme must consume those values itself. In particular, the built-in Modena high-contrast fallback recognizes the old named Windows schemes, while Windows 11 supports new and user-defined contrast themes.
- JavaFX is per-monitor-DPI-aware by default and exposes screen/window output scales. JavaFX 25 nevertheless lacks the Windows fix for changing display scale while the app is already running; that fix is listed in JavaFX 26. Scale-at-launch, mixed-DPI movement, and live scale changes are distinct scenarios.
- There is no single mature, maintained JavaFX-25-specific end-to-end framework. TestFX remains useful for in-process scene-graph tests but describes its current support as legacy. Microsoft's newer `winapp ui` can exercise the packaged executable through UIA but is still public preview. Accessibility Insights and Narrator remain essential manual/assisted tools.

## Accessible metadata, roles, actions, and events

### Public JavaFX surface

Every `Node` exposes the following public accessibility properties and hooks:

- `accessibleRole`: one of the finite `AccessibleRole` values. JavaFX 25 includes roles for the standard desktop vocabulary: buttons and toggles, menus, tabs, lists, trees, tables, text and edit controls, sliders/spinners/progress indicators, images, dialogs, toolbars, scroll controls, and related item/cell roles. There is no arbitrary string role. ([`AccessibleRole` API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/AccessibleRole.html))
- `accessibleText`: overrides the text a screen reader speaks for a node. `accessibleHelp` adds a longer description and defaults to tooltip text when a tooltip exists. `accessibleRoleDescription` supplies a localized role description. ([`Node` accessibility properties](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/Node.html#setAccessibleText(java.lang.String)))
- `queryAccessibleAttribute` and `executeAccessibleAction`: extension points for role-specific state and behavior. `AccessibleAttribute` includes names/labels, values and ranges, selection, focus, children, table/grid indices and counts, text/caret/range geometry, expansion, visibility, disabled/editable state, and related metadata. When a value changes, a node must call `notifyAccessibleAttributeChanged`; JavaFX explicitly says this is how assistive technology is told about the change. ([`AccessibleAttribute` API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/AccessibleAttribute.html), [`AccessibleAction` API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/AccessibleAction.html))
- `Label.labelFor` associates a label with another node and is also the target for mnemonic focus transfer. The Windows bridge uses the corresponding `LABELED_BY` accessible attribute as a fallback for UIA Name. ([`Label` API](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/Label.html#labelForProperty()), [`WinAccessible` Name mapping](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L3579-L3673))

`Platform.accessibilityActiveProperty()` becomes true when assistive technology first asks about a JavaFX window. It is an activation signal, not a conformance result, and in JavaFX 25 it may be accessed only on the JavaFX Application Thread. ([`Platform` API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/application/Platform.html#accessibilityActiveProperty()))

### Windows UI Automation bridge

The JavaFX 25 Windows implementation is a native UIA provider. `GlassAccessible` implements `IRawElementProviderSimple`, fragment/root navigation, event advice, and provider interfaces for Invoke, Selection/SelectionItem, RangeValue/Value, Text, Grid/GridItem, Table/TableItem, Toggle, ExpandCollapse, Transform, Scroll, and ScrollItem. ([JavaFX 25 native provider](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/native-glass/win/GlassAccessible.cpp#L2670-L2755))

`WinAccessible` maps JavaFX roles to Windows control types and patterns. Examples include Button/Invoke, CheckBox/Toggle, TextField and TextArea/Edit with Text and Value, Table/Grid/Selection/Scroll, list and tree selection/scroll/expansion, ComboBox/ExpandCollapse and Value, and Slider/ScrollBar/ProgressIndicator with RangeValue. ([control-type mapping](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L3012-L3105), [pattern mapping](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L3255-L3492))

JavaFX notifications are translated to Windows events for focus, selection, range/value, text, toggle, and expansion changes. The source includes Narrator-specific handling for traversal, empty text controls, and list position/count reporting, which is evidence of direct Narrator integration but not a guarantee that every composed control behaves correctly. ([Windows notification mapping](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L2654-L2942))

### Provider limitations that affect design and testing

1. **No stable application-controlled UIA AutomationId.** The provider returns `JavaFX` plus an internal counter for UIA `AutomationId`; it does not use `Node.id` or FXML `fx:id`. The counter is assigned as accessible peers are created. Microsoft's UIA guidance says AutomationId is the preferred test locator when stable, while Name can be localized and is not unique, and RuntimeId is opaque and reusable. External automation therefore cannot assume a JavaFX CSS ID is a Windows accessibility ID, nor should it persist the generated `JavaFX<n>` value across runs. ([JavaFX provider source](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L3905-L3916), [Microsoft UIA locator guidance](https://learn.microsoft.com/en-us/windows/win32/winauto/uiauto-usefortesting#key-properties-for-test-automation))

2. **Focusable metadata is over-broad.** JavaFX 25's Windows provider returns `true` for `UIA_IsKeyboardFocusablePropertyId` with a source TODO to return `focusTraversable`. Automated accessibility scans of this property can therefore produce misleading results; actual Tab/Shift+Tab and composite-control traversal must be exercised. ([provider source](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L3880-L3888))

3. **Control/content-tree filtering is coarse.** The provider uses the same test for UIA content and control elements and retains a TODO about distinguishing them. Custom decorative nodes and composite-control internals need inspection for redundant or noisy UIA elements. ([provider source](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L3852-L3863))

4. **Not every JavaFX role becomes a distinct Windows control type.** The mapping is explicit and finite. For example, a `DATE_PICKER` maps to a Pane and roles not present in the mapping fall through without a control type; `DIALOG` is separately exposed through the UIA IsDialog property and localized role description. The closest JavaFX role plus its actual patterns and names must be verified for custom workbench constructs. ([control-type mapping](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L3012-L3105), [dialog properties](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L3694-L3728))

5. **No first-class UIA live region or notification event.** JavaFX 25 has no `AccessibleAttribute` for live settings, and the Windows bridge contains neither `UIA_LiveSettingPropertyId` nor `UiaRaiseNotificationEvent`. It can raise text/value/property events when the corresponding accessible attribute changes, but a toast or background-job status should not be assumed to be announced as a Windows live region. The announcement behavior of the planned notification system needs a focused prototype and Narrator validation. ([accessible attribute set](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/AccessibleAttribute.html), [Windows notification mapping](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L2654-L2942))

## Keyboard navigation and visible focus

`Node.focusTraversable` determines membership in the regular focus traversal cycle; desktop traversal is normally Tab and Shift+Tab. Most JavaFX `Control` subclasses default it to true, while read-only controls and some containers—such as Label, ProgressIndicator, ScrollPane, and ToolBar—do not. JavaFX 24 added `requestFocusTraversal(TraversalDirection)` for custom traversal behavior. ([`Node` focus API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/Node.html#focusTraversableProperty()), [`Control` defaults](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/Control.html))

JavaFX CSS 25 exposes `:focused`, `:focus-visible`, and `:focus-within`; `:focus-visible` tracks focus obtained through keyboard traversal. A tokenized theme can therefore provide keyboard-specific focus rings without showing the same affordance for every pointer click. ([JavaFX 25 CSS reference](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/doc-files/cssref.html#node))

Standard controls supply their normal keyboard behavior, but the planned navigation rail, split workspaces, list/editor/inspector composition, drawers, and any custom cells still determine the practical order, escape path, arrow-key behavior, default actions, and focus restoration. Microsoft recommends a real keyboard pass for complete logical Tab order, arrow-key behavior in composite controls, keyboard invocation, visible focus, and focus traps. ([Microsoft accessibility testing](https://learn.microsoft.com/en-us/windows/apps/design/accessibility/accessibility-testing#test-keyboard-accessibility))

## System theme, contrast, and motion preferences

JavaFX 25's `Platform.getPreferences()` returns an unmodifiable but observable map and convenience properties. Values update when the operating system reports changes, but JavaFX warns that platform-specific mappings depend on OS version/configuration and may be absent. The API is JavaFX-thread-confined in JavaFX 25. ([`Platform.Preferences` API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/application/Platform.Preferences.html), [`Platform.getPreferences`](https://openjfx.io/javadoc/25/javafx.graphics/javafx/application/Platform.html#getPreferences()))

On Windows, the exposed values include:

- `ColorScheme` (light/dark), foreground, background, and accent convenience properties;
- `Windows.SPI.HighContrast` and `Windows.SPI.HighContrastColorScheme`;
- Windows system colors for window/background text, buttons, disabled text, links, selected text, and highlights;
- reduced motion (inverse of client-area animation), reduced transparency (inverse of advanced effects), persistent scrollbars (inverse of auto-hide), and related preferences.

The Windows implementation listens for high-contrast, client-animation, and immersive-color changes and republishes the preferences. ([JavaFX 25 Windows preference source](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/native-glass/win/PlatformSupport.cpp#L184-L288), [Windows-to-generic mappings](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinApplication.java#L335-L380))

Detection does not automatically make a custom stylesheet adaptive. JavaFX's built-in Modena code conditionally adds one of three high-contrast stylesheets only when the Windows theme name matches its old `High Contrast White`, `High Contrast Black`, `High Contrast #1`, or `High Contrast #2` resource entries. Windows 11 instead offers the Aquatic, Desert, Dusk, and Night sky contrast themes and permits user-defined colors. A modern BS2BG theme should therefore treat the boolean high-contrast signal and current system colors as the contract, rather than depending on a recognized theme name or treating dark mode as high contrast. ([JavaFX Modena high-contrast selection](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/javafx/application/PlatformImpl.java#L621-L680), [recognized Windows names](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/resources/com/sun/glass/ui/win/themes.properties), [Windows 11 contrast themes](https://support.microsoft.com/en-au/windows/make-windows-easier-to-see-c97c2b0d-cadb-93f0-5fd1-59ccfe19345d))

Decision inputs:

- whether system light/dark is the default with an in-app override, and whether that override is persisted;
- whether high contrast switches to a dedicated token set based on Windows system colors or merely suppresses nonessential effects—source evidence favors a dedicated path;
- which animations/transparency effects respond to `reducedMotion` and `reducedTransparency`;
- whether always-visible scrollbars are honored structurally or only cosmetically.

## DPI and scaling

JavaFX 25 is per-monitor-DPI-aware by default on Windows. Its Glass startup requests `Process_Per_Monitor_DPI_Aware` unless HiDPI is disabled or an internal override is supplied. `Screen` reports DPI and recommended output scales, while `Window.outputScaleX/Y` are system-updated and `renderScaleX/Y` normally track them. ([Windows startup source](https://github.com/openjdk/jfx/blob/jfx25/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinApplication.java#L118-L160), [`Screen` scaling API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/stage/Screen.html#getOutputScaleX()), [`Window` scaling API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/stage/Window.html#outputScaleXProperty()))

Coordinates are logical screen units, while captures can contain a different number of physical pixels. JavaFX `Robot.getScreenCapture(..., scaleToFit=false)` explicitly returns physical-pixel-dependent dimensions, and its pixel sampling behavior is scale-aware. Screenshot comparisons must record whether images are normalized to logical size or intentionally retain native pixels. ([`Robot` capture API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/robot/Robot.html#getScreenCapture(javafx.scene.image.WritableImage,double,double,double,double,boolean)))

There is a JavaFX-25-specific limitation: the fix for `JDK-8346281`, "[Windows] RenderScale doesn't update to HiDPI changes," is listed among JavaFX 26 fixes and is absent from the current JavaFX 25 update source. JavaFX 25 can start correctly at a configured scale and react to moving between monitors, but changing Windows display scale while the application remains on the same display must be treated separately and empirically. ([JavaFX 26 release notes](https://github.com/openjdk/jfx/blob/master/doc-files/release-notes-26.md), [JavaFX 25 update source without the new handler](https://github.com/openjdk/jfx25u/blob/master/modules/javafx.graphics/src/main/native-glass/win/GlassWindow.cpp))

Decision inputs:

- startup coverage at common Windows scales such as 100%, 125%, 150%, and 200%;
- a mixed-DPI, multi-monitor movement case independent of changing scale in Settings;
- whether live display-scale changes are supported, documented as requiring restart on JavaFX 25, or important enough to revisit the JavaFX baseline/backport;
- whether visual baselines are per-scale, normalized, or limited to geometry/state assertions plus targeted screenshots.

## Screen readers and accessibility inspection

Because JavaFX exposes a native UIA provider, Windows UIA clients can inspect it. Accessibility Insights explicitly states that it can scan a Java application when the Java framework supplies UIA provider interfaces. Its Live Inspect view shows names, control types, properties, patterns, and events; its automated checks cover dozens of rules; and FastPass adds an assisted manual tab-stop pass. The project also warns that automated checks find only some common issues and provide partial coverage. ([Accessibility Insights Java FAQ](https://accessibilityinsights.io/docs/windows/reference/faq/#can-i-use-accessibility-insights-for-windows-on-a-windows-app-written-with-java), [Windows overview](https://accessibilityinsights.io/docs/windows/overview/), [FastPass](https://accessibilityinsights.io/docs/windows/getstarted/fastpass/))

Microsoft now recommends Accessibility Insights over legacy SDK tools such as Inspect and AccScope. Inspect remains useful for raw properties/patterns, while AccScope can visualize the Narrator item-navigation order and spoken text. ([Microsoft accessibility testing tools](https://learn.microsoft.com/en-us/windows/apps/design/accessibility/accessibility-testing#accessibility-testing-tools), [AccScope Narrator mode](https://learn.microsoft.com/en-us/windows/win32/winauto/accscope#testing-the-narrator-scenario))

Narrator testing remains manual and scenario-based: Microsoft directs testers to navigate with Tab/arrow/Narrator commands, verify name/state/control type and invocability, inspect tables, search for all controls, and complete primary workflows without sight. The OpenJFX source has Narrator-specific accommodations, but neither OpenJFX nor Microsoft publishes a JavaFX-25 conformance claim. A plan must choose the manual screen-reader matrix (at least Narrator as the Windows-owned baseline, and optionally NVDA based on target-user needs) rather than infer coverage from UIA tree visibility. ([Microsoft Narrator procedure](https://learn.microsoft.com/en-us/windows/apps/design/accessibility/accessibility-testing#verify-main-app-scenarios-by-using-narrator))

## JavaFX and Windows test tooling

### In-process JavaFX tests

**TestFX 4** provides JUnit 4/5 fixtures, scene-graph node lookup, input robots, matchers/assertions, failure screenshots, and optional headless execution through Monocle. It can use JavaFX CSS IDs such as `#myButton`, so `Node.id` is useful here even though it is not a Windows UIA AutomationId. Its current README names version 4.0.18, advertises Java 8/11/17+, and also states that TestFX 4 "has only legacy support." JavaFX 25/JDK 25 compatibility should be established by a small dependency/build spike before it is made the central harness. ([TestFX project README](https://github.com/TestFX/TestFX/blob/master/README.md))

TestFX headless mode is not a Windows accessibility test. It substitutes Monocle/Glass and software rendering, so it does not exercise the Windows `WinAccessible` provider, Narrator, platform preferences, real monitor DPI, window manager behavior, or the `jpackage` launcher. Headless tests can still cover view wiring and deterministic control behavior.

**JavaFX `Robot`** is a supported JavaFX 25 API for key and mouse input, pixel reads, and screen capture. It must be created and used on the JavaFX Application Thread and supplies no fixture lifecycle, semantic node finder, waits, or assertions. A project-owned JUnit harness around it avoids TestFX maintenance risk but would need to build those facilities. ([JavaFX `Robot` API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/robot/Robot.html))

**JemmyFX is not a current alternative.** The OpenJDK `jfx-tests` repository describes itself as JavaFX Jemmy-based tests but was archived and made read-only on 2025-11-03. ([OpenJDK `jfx-tests`](https://github.com/openjdk/jfx-tests))

### External Windows UIA automation

**Microsoft `winapp ui`** is an active, framework-independent UIA command set. It can inspect/search a tree, read properties and values, invoke/toggle/expand through UIA patterns, wait for state, and capture screenshots. Its documentation says UIA-pattern operations can run in a locked session, while real mouse/keyboard injection needs an unlocked interactive desktop and foreground target. The CLI supports selecting an app by process name, title, PID, or HWND and documents CI smoke-test patterns. It is a strong candidate for packaged-app smoke and UIA-contract tests, but the parent WinApp CLI is explicitly public preview and in active development. JavaFX's generated AutomationIds mean selectors would generally be accessible Name plus ControlType/tree context or runtime-discovered selectors, not persistent `fx:id` values. ([`winapp ui` documentation](https://github.com/microsoft/winappCli/blob/main/docs/ui-automation.md), [WinApp CLI status](https://github.com/microsoft/winappCli))

**Appium Windows Driver / WinAppDriver** can launch a classic app by executable path and expose WebDriver-style UIA locators, but it proxies Microsoft's WinAppDriver. The Appium project warns that WinAppDriver has not been maintained by Microsoft for years; Microsoft's latest WinAppDriver release shown in its repository is 1.2.1 from 2020. Introducing it now carries a larger maintenance risk than its mature protocol initially suggests. ([Appium Windows Driver README](https://github.com/appium/appium-windows-driver/blob/master/README.md), [Microsoft WinAppDriver](https://github.com/microsoft/WinAppDriver))

No option covers everything. The practical boundary is:

- ordinary JUnit tests for JavaFX-independent presentation/domain logic;
- a limited in-process JavaFX layer (TestFX after a compatibility spike, or a small Robot-based harness) for scene wiring and workflows;
- external UIA tests on a real Windows desktop for the accessibility tree and packaged executable;
- assisted/manual Accessibility Insights, keyboard, Narrator, high-contrast, and DPI checks for behavior that automation cannot judge.

This is a decision input for the test architecture, not a settled test-count or gate.

## Packaged-application smoke-test boundary

JDK 25 `jpackage` creates a self-contained application image or native Windows EXE/MSI. The Windows app image contains the native application launcher, application files/configuration, and bundled runtime. Oracle's packaging guide explicitly recommends creating `--type app-image` to test the application before producing an installer, and Windows packages must be built on Windows. ([JDK 25 `jpackage` command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html), [JDK 25 packaging overview](https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html))

An in-process test launched by Maven does not establish that the bundled runtime contains the right modules, the generated launcher starts, working-directory/file-dialog assumptions hold, or the installed artifact exposes the same UIA tree. Packaged smoke tests need to start the generated BS2BG executable as an external process and attach by PID/HWND/title. `winapp ui` can attach to an already-running process; Appium/WinAppDriver can alternatively launch a classic executable directly, subject to the maintenance concerns above.

Two artifact levels expose different facts:

1. **Application-image smoke** is fast and isolates launcher/runtime/image composition without installer state.
2. **Installer smoke** additionally covers installation location, shortcuts/file associations if selected, launch after install, upgrade/uninstall behavior, and user-versus-machine installation. It is slower and mutates the runner, so it normally belongs in a dedicated disposable Windows environment.

The plan still needs to decide which workflows are proven at each level. The research only establishes that the packaged executable—not a Maven-launched substitute—is required for launcher/runtime and external-UIA evidence.

## Decisions and fog surfaced

1. **In-process harness:** validate TestFX 4.0.18 against JDK/JavaFX 25 and Maven, then choose it or a project-owned JUnit/Robot harness. The current documentation does not justify adopting TestFX without that spike.
2. **External Windows harness:** decide whether public-preview `winapp ui` is acceptable for UIA/package smoke scripts. WinAppDriver should be considered only with an explicit tolerance for its unmaintained server.
3. **Locator contract:** choose how external tests locate controls when JavaFX cannot expose stable application-defined AutomationIds. Accessible Name + ControlType makes the tests enforce user-facing semantics but is localization-sensitive; generated `JavaFX<n>` IDs are unsuitable as durable selectors.
4. **Dynamic announcements:** prototype the centralized job/notification experience with Narrator because JavaFX 25 has no UIA live-region/notification bridge. Decide what focus, persistent status, or dialog behavior is acceptable for important errors and completions.
5. **Theme contract:** define explicit application behavior for system light/dark, Windows high contrast/system colors, reduced motion/transparency, and persistent scrollbars. Do not rely on Modena's legacy named high-contrast themes for Windows 11.
6. **DPI support boundary:** decide whether changing Windows scale while BS2BG is running must work on the JavaFX 25 baseline, can require restart, or warrants revisiting/backporting the JavaFX 26 fix. Keep startup scale and mixed-monitor movement as separate cases.
7. **Assistive-technology matrix:** decide which workflows are manually exercised with Narrator and whether NVDA is included based on users. UIA inspection alone is insufficient evidence of spoken order and operability.
8. **CI desktop model:** UIA pattern calls can run without injected input, but real keyboard/mouse, Narrator, DPI, and contrast scenarios require a real interactive Windows desktop. Decide which checks run per change, on a scheduled interactive runner, and before release.
9. **Package levels:** decide the division between app-image smoke and disposable-environment installer smoke, including whether file associations, upgrades, and uninstall are in the modernization destination.
