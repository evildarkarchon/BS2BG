# Workbench appearance, accessibility, and feedback checkpoint

Issue #100 delivers the application-owned Workbench platform layer on standard JavaFX 25 controls and Modena.
`WorkbenchAppearance` owns the persisted System/Light/Dark choice, effective theme, reduced-motion state, and
semantic tokens without depending on JavaFX. `JavaFxWorkbenchAppearance` is the production adapter: it observes
the public [`Platform.Preferences`](https://openjfx.io/javadoc/25/javafx.graphics/javafx/application/Platform.Preferences.html)
interface, including the documented Windows `Windows.SPI.HighContrast` and system-color keys, and applies one
coherent frame to the loaded Workbench root. High Contrast always overrides the manual choice and ending High
Contrast restores that unchanged choice.

The Workbench uses one application-owned stylesheet, `workbench.css`. Color is never the only status cue: InfoBar,
Activity, status, selection, focus, and dialog states retain text, semantic vectors, and boundaries. Focus styling
is at least two logical pixels. Reduced motion is live and all platform-slice drawer, overlay, selection, and reflow
changes are already immediate; the centralized job slice (#101) owns later progress animation behavior.

## Selected semantic icons

The predetermined bundled-vector fallback is the selected and only shipping implementation. The conditional
Ikonli candidate could not satisfy the complete adoption gate in this checkpoint because its packaged font loading,
UIA, DPI/theme, licensing, and targeted Narrator evidence were not all established. `SemanticIcons.IconKey` is the
feature interface and fresh public-JavaFX `SVGPath` nodes are the sole adapter. The POM bans
`org.kordamp.ikonli:*` transitively so no dormant alternative can enter the image. `SemanticIconsTest`, the source
gate, dependency convergence, and the packaged smoke continuously verify that choice.

## Feedback and dialogs

`WorkbenchFeedback` publishes immutable InfoBar, durable session Activity, status, and pending-dialog state before
any platform effect. Validation, success, warning, and failure share the same severity, explicit text cue, semantic
icon key, and terminal disposition across those projections. Dismissing an InfoBar never deletes Activity or the
terminal summary. Tokenized answers reject stale dialogs. Standard `Dialog`/`DialogPane` rendering provides an
explicit owner, one modal at a time, safe Enter/Escape defaults, typed actions, and launcher focus restoration.

## Packaged verification

`smoke-app-image.ps1` drives the real packaged launcher by accessible role, stable name, state, and semantic tree
relationship. It exercises Light/Dark/System selection, High Contrast precedence/restoration, reduced motion,
keyboard landmarks including Activity, disabled Cancel state, InfoBar help text, durable Activity, and destructive
typed dialogs. It captures screenshots for High Contrast and reduced motion. The harness uses the documented
[`SPI_GETHIGHCONTRAST` / `SPI_SETHIGHCONTRAST`](https://learn.microsoft.com/en-us/windows/win32/winauto/high-contrast-parameter)
and [`SPI_GETCLIENTAREAANIMATION` / `SPI_SETCLIENTAREAANIMATION`](https://learn.microsoft.com/en-us/windows/win32/winauto/client-area-animation)
contracts. The original flags, High Contrast scheme, and animation preference are captured before launch and
restored both inside the successful test step and unconditionally in `finally`; restoration failure fails the gate.

The screenshot artifacts support visual review of contrast, clipping, and fidelity that UIA cannot prove. Targeted
human Narrator and cross-monitor inspection remain release evidence rather than claims inferred from automation.
