# JavaFX 25 controls and Fluent-inspired theming research

Research date: 2026-08-27  
Issue: [Research maintained JavaFX controls and Fluent theming options](https://github.com/evildarkarchon/BS2BG/issues/64)

## Question and scope

Which JavaFX 25-compatible building blocks can support a Fluent-inspired BS2BG workbench, table filtering, icons, dialogs, and notifications without retaining the application's vendored JavaFX 8 ControlsFX sources or its direct use of `com.sun.javafx` internals?

This note records facts, compatibility evidence, risks, and decisions that still need to be made. It does not select a final UI stack.

## Local baseline

- The Maven build declares JavaFX `25.0.4`, but still compiles with `maven.compiler.release` 8 and deliberately excludes the JavaFX presentation classes from compilation. A successful current build therefore does **not** establish that the UI works on JavaFX 25. See [`pom.xml`](../../pom.xml).
- Three screens instantiate a vendored `com.asdasfa.jbs2bg.controlsfx.table.TableFilter`: the main NPC Morph Assignment table, NPC Database, and no-preset notification table. Callers also read its `FilteredList` for bulk operations, so replacement must preserve both visible filtering and access to the effective filtered rows. See [`MainController.java`](../../src/com/asdasfa/jbs2bg/MainController.java), [`PopupNpcDatabaseController.java`](../../src/com/asdasfa/jbs2bg/PopupNpcDatabaseController.java), and [`PopupNoPresetNotifController.java`](../../src/com/asdasfa/jbs2bg/PopupNoPresetNotifController.java).
- The repository carries eight old ControlsFX-derived files under [`src/com/asdasfa/jbs2bg/controlsfx/table`](../../src/com/asdasfa/jbs2bg/controlsfx/table). Its `FilterPanel` imports JavaFX 8 `com.sun.javafx.scene.control.skin` header classes.
- [`MyUtils.isIndexVisible`](../../src/com/asdasfa/jbs2bg/etc/MyUtils.java) reaches through `ListViewSkin.getChildren().get(0)` to a `VirtualFlow`. Eight call sites use that check to avoid scrolling an already-visible list item.
- [`CustomConfirm`](../../src/com/asdasfa/jbs2bg/CustomConfirm.java) and [`CustomNotif`](../../src/com/asdasfa/jbs2bg/CustomNotif.java) load dialog images from `/com/sun/javafx/scene/control/skin/modena/`. Those are implementation resources, not application assets or public API.

## JavaFX 25 platform facts

- JavaFX 25 requires JDK 23 or later and recommends JDK 25. It is therefore a valid runtime pair with the chosen JDK 25 baseline. [JavaFX 25 release notes](https://github.com/openjdk/jfx/blob/jfx25/doc-files/release-notes-25.md)
- JavaFX 25 provides public `TableView`, `TableColumn`, `FilteredList`, and `SortedList` APIs. `TableColumnBase` exposes public `contextMenu` and `graphic` properties, so an application-owned filter affordance does not inherently require skin-header access. [TableColumnBase API](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/TableColumnBase.html), [FilteredList API](https://openjfx.io/javadoc/25/javafx.base/javafx/collections/transformation/FilteredList.html), [SortedList API](https://openjfx.io/javadoc/25/javafx.base/javafx/collections/transformation/SortedList.html)
- The skin classes used by the old code now have public types in `javafx.scene.control.skin`, including `ListViewSkin`, `TableViewSkin`, `TableColumnHeader`, `NestedTableColumnHeader`, and `VirtualFlow`. Public type visibility does not make all of their internals public: for example, `VirtualContainerBase.getVirtualFlow()` and `TableViewSkinBase.getTableHeaderRow()` are protected. [ListViewSkin API](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/skin/ListViewSkin.html), [TableViewSkinBase API](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/skin/TableViewSkinBase.html), [VirtualFlow API](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/skin/VirtualFlow.html)
- JavaFX CSS supports live, inherited looked-up colors. These are sufficient to express application-owned semantic tokens such as surface, text, border, accent, focus, success, warning, and error colors. [JavaFX 25 CSS reference: looked-up colors](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/doc-files/cssref.html#looked-up-colors)
- JavaFX 25 CSS media queries can react to the platform's light/dark scheme, reduced motion, reduced transparency, and persistent-scrollbar preference. The corresponding observable values and Windows-specific high-contrast/accent settings are available through `Platform.getPreferences()`. [JavaFX 25 CSS `@media` reference](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/doc-files/cssref.html#media-queries), [`Platform.Preferences` API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/application/Platform.Preferences.html)
- Standard nodes expose accessible text, help, role, and role-description properties. Standard controls also implement their established accessible roles and actions; custom icon nodes and custom controls must supply equivalent semantics themselves. [`Node` accessibility properties](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/Node.html#accessibleTextProperty()), [`AccessibleRole` API](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/AccessibleRole.html)
- JavaFX provides `Alert`, `Dialog`, `DialogPane`, `ChoiceDialog`, and `TextInputDialog` for modal interaction. It does not expose a dedicated toast/snackbar API in the JavaFX 25 control set, so transient in-workbench notifications require an application-owned component or a third-party library. [`Alert` API](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/Alert.html), [`Dialog` API](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/Dialog.html)

## Candidate compatibility snapshot

“Candidate on JavaFX 25” below means that the published bytecode/API baseline does not itself exclude JDK 25/JavaFX 25. It does not mean that the project's maintainers test that exact pair. Any selected third-party candidate needs a Windows JavaFX 25 runtime spike covering CSS, control skins, keyboard use, screen-reader exposure, and packaging.

| Candidate | Relevant capability | Published compatibility evidence | Maintenance and JavaFX 25 constraint |
| --- | --- | --- | --- |
| Standard JavaFX 25 controls plus application CSS | Exact runtime baseline; standard tables, dialogs, controls, CSS, platform preferences, accessibility | The APIs and CSS features are part of JavaFX 25 itself. | Lowest external dependency and private-API exposure. BS2BG owns the Fluent-inspired theme, filter UI, notification component, visual regression tests, and accessibility behavior. |
| ControlsFX 11.2.4 | Drop-in-style `TableFilter`, richer `FilteredTableView`/`FilteredTableColumn`, `Notifications`, `ExceptionDialog`, validation and utility controls | 11.2.4 was released 2026-07-28, targets Java 11 bytecode, and builds against JavaFX 17. [Release](https://github.com/controlsfx/controlsfx/releases/tag/11.2.4), [build baseline](https://github.com/controlsfx/controlsfx/blob/11.2.4/build.gradle), [JavaFX version](https://github.com/controlsfx/controlsfx/blob/11.2.4/gradle.properties) | Actively released, but not maintainer-tested on JavaFX 25 in the published build. The full module compiles with several `--add-exports`, and some features use reflection/private implementation details. Adoption needs a feature-scoped runtime test and a decision about accepting that maintenance boundary. |
| AtlantaFX 2.1.0 | Modern light/dark CSS themes, semantic palettes/looked-up colors, SASS custom-theme sources, some additional controls | The project requires JavaFX 17+, compiles release 2.1.0 with Java 21 and OpenJFX 23, and was released 2025-07-11. [README](https://github.com/mkpaz/atlantafx/blob/v2.1.0/README.md), [build properties](https://github.com/mkpaz/atlantafx/blob/v2.1.0/pom.xml), [release](https://github.com/mkpaz/atlantafx/releases/tag/v2.1.0) | Current repository activity and a relatively recent release make it a maintained theme candidate, but its supplied visual languages are Primer/Cupertino-style rather than Fluent. Using it for BS2BG's chosen language means an override layer or a custom SASS theme, plus JavaFX 25 screenshot and accessibility validation. |
| JMetro 11.6.16 | A theme explicitly inspired by Microsoft Fluent Design and applied to standard JavaFX controls through CSS/skins | Its documentation targets and tests JavaFX 11 while stating it may run on later releases. The latest release is dated 2022-09-06. [Project README](https://github.com/JFXtras/jfxtras-styles/blob/11.6.16/README.md), [JMetro design documentation](https://www.pixelduke.com/java-javafx-theme-jmetro/), [release](https://github.com/JFXtras/jfxtras-styles/releases/tag/11.6.16) | This is the closest prebuilt visual match but the weakest current JavaFX 25 maintenance signal. It changes skins as well as CSS, increasing sensitivity to JavaFX control internals. Treat compatibility as unproven until a time-boxed JavaFX 25 prototype passes. |
| Ikonli 12.4.0 | JavaFX `FontIcon` plus selectable icon packs, including a Fluent UI pack | 12.4.0 was released 2025-04-18; its published build targets Java 11/JavaFX 11 and includes `fluentuiVersion = 1.1.74`. [release](https://github.com/kordamp/ikonli/releases/tag/v12.4.0), [build properties](https://github.com/kordamp/ikonli/blob/v12.4.0/gradle.properties), [`FontIcon` source](https://github.com/kordamp/ikonli/blob/v12.4.0/core/ikonli-javafx/src/main/java/org/kordamp/ikonli/javafx/FontIcon.java) | Low control-skin coupling, but the release is not explicitly tested on JavaFX 25. The selected pack's age, glyph coverage, redistribution license, font loading in the packaged image, and accessible labels on every icon-only action need verification. |

MaterialFX was also inspected because it includes tables, dialogs, and notifications. Its declared design language is Material rather than Fluent and it replaces many standard controls with library-specific controls, so it is not a neutral theming layer for the already-chosen Fluent-inspired direction. This is a scope/fit fact, not a recommendation against using individual ideas from it. [MaterialFX project](https://github.com/palexdev/MaterialFX)

## Standard controls versus library controls

| Surface | Standard JavaFX 25 route | Maintained-library route | Decision input |
| --- | --- | --- | --- |
| General controls and navigation | Use `Button`, `ToggleButton`, `ListView`, `TableView`, `SplitPane`, `ToolBar`, `MenuButton`, and layout panes; restyle with semantic CSS tokens. | AtlantaFX restyles standard controls; JMetro restyles and may replace their skins. | Standard control types preserve portability, FXML familiarity, built-in keyboard behavior, and the ability to change themes later. A skin-changing theme can deliver more behavior but adds upgrade coupling. |
| Table filtering | Keep the model as `ObservableList -> FilteredList -> SortedList -> TableView`; own filter predicates and a public-control filter editor. | ControlsFX `TableFilter` is closest to the vendored Excel-style checklist. `FilteredTableView`/`FilteredTableColumn` offers typed predicates and popup/south-header editors but replaces `TableView` with a richer subclass. | Decide whether exact Excel-style distinct-value filtering is required on every table, and whether keeping a standard `TableView` matters more than receiving a ready-made editor. |
| Modal dialogs | Use `Alert` and typed `Dialog<R>`/`DialogPane`; style the `DialogPane` with the same tokens. | ControlsFX adds specialized dialogs such as `ExceptionDialog`; theme libraries can style standard dialogs. | Standard dialogs directly replace the custom modal window plumbing and private Modena image loads. Specialized dialogs should be added only where their behavior is actually needed. |
| Transient notifications | Build an in-scene banner/toast from standard labeled controls, owned by the workbench's notification service. | ControlsFX `Notifications` supplies positioned, auto-hiding popup notifications. | In-scene notifications are easier to associate with the active workbench, retain in history, and test for focus/accessibility. Popup notifications reduce implementation work but require explicit owner/multi-monitor, timing, keyboard, and screen-reader testing. |
| Icons | Use application-owned raster images or `SVGPath` nodes and style their fill through tokens. | Use Ikonli `FontIcon` and one pinned icon pack. | App-owned shapes avoid a runtime dependency but require an asset pipeline. Ikonli supplies broad, CSS-styleable coverage but adds font/resource packaging and pack-version constraints. |

## Data-table filtering details

### Standard JavaFX pipeline

The public, version-stable data path is:

```text
ObservableList<T> source
    -> FilteredList<T> effectiveRows
    -> SortedList<T> displayedRows (comparator bound to TableView.comparatorProperty)
    -> TableView<T>
```

The application owns a predicate model separate from the filter editor. That preserves programmatic access to `effectiveRows`, which current bulk operations require, without exposing the editor widget to unrelated controllers. A column can use its public `graphic` and `contextMenu` properties for an active-filter indicator and editor. This route requires BS2BG to implement distinct-value enumeration, predicate composition, reset/apply semantics, and keyboard-accessible editor behavior.

### ControlsFX `TableFilter`

ControlsFX 11.2.4 still publishes `org.controlsfx.control.table.TableFilter` with the same builder shape, lazy initialization, and `getFilteredList()` concept used by the vendored fork. Its source installs a `FilteredList`/`SortedList`, binds sorting to the table comparator, and attaches a checklist to each column. [ControlsFX 11.2.4 `TableFilter`](https://github.com/controlsfx/controlsfx/blob/11.2.4/controlsfx/src/main/java/org/controlsfx/control/table/TableFilter.java)

It is not purely public-API implementation. The current `FilterPanel` uses public skin class names but calls `ReflectionUtils` to access protected header methods; `ReflectionUtils` uses `setAccessible(true)` on skin methods and fields. [ControlsFX `FilterPanel`](https://github.com/controlsfx/controlsfx/blob/11.2.4/controlsfx/src/main/java/org/controlsfx/control/table/FilterPanel.java), [ControlsFX `ReflectionUtils`](https://github.com/controlsfx/controlsfx/blob/11.2.4/controlsfx/src/main/java/impl/org/controlsfx/ReflectionUtils.java)

Therefore replacing the vendored fork with the official dependency would remove local ownership of stale JavaFX 8 source and direct `com.sun` imports, but it would not eliminate all skin/reflection risk. The relevant compatibility spike must exercise right-click/filter-popup placement, nested and reordered columns, sorting, lazy initialization, live row updates, reset, and packaging on JavaFX 25.

### ControlsFX `FilteredTableView`

`FilteredTableView` represents per-column predicates directly and documents the same `FilteredList`/`SortedList` binding pattern. It provides popup and south-header filter editors through `FilteredTableColumn`, but is a `TableView2` subclass with its own skin and additional behavior. [ControlsFX `FilteredTableView` API](https://controlsfx.github.io/javadoc/11.2.2/org.controlsfx.controls/org/controlsfx/control/tableview2/FilteredTableView.html)

This is not a drop-in choice for the current FXML. It changes the control type and creates a larger ControlsFX surface, so it should be evaluated separately from simply replacing the old `TableFilter` package.

## Icons, dialogs, and notifications

### Icons

- No code should continue loading `/com/sun/javafx/.../modena/*.png`. Standard `Alert.AlertType` installs its own default graphic, or BS2BG can set a public, application-owned graphic.
- A Fluent-inspired icon set needs consistent stroke/fill weight and sizes, not a mixture of Modena raster assets and arbitrary glyphs. Both an app-owned asset set and a pinned Ikonli pack can meet that visual requirement.
- `ImageView` documentation explicitly expects a text description through accessible text or a label relationship. The same semantic requirement applies to an icon used as a button graphic: the containing button needs a meaningful text/accessibility name and tooltip; the glyph itself should not become a duplicate focus target. [`AccessibleRole.IMAGE_VIEW`](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/AccessibleRole.html#IMAGE_VIEW)

### Modal dialogs

- `Alert(AlertType.CONFIRMATION, ...)` can replace confirmation-only `CustomConfirm` instances; `Dialog<R>` is available for typed custom workflows. Owners, button types, cancel/close semantics, and result conversion are public APIs.
- `Alert` supports blocking `showAndWait()` and non-blocking `show()`. The UI architecture still needs one policy for when modal interruption is appropriate; a maintained API does not by itself justify preserving every existing popup.
- Standard `DialogPane` is a `Node` with the accessible properties and `DIALOG` role available in JavaFX 25. Custom dialog content must retain focus order, an Escape/cancel route, a default action only when safe, and visible focus styling.

### Notifications

- The existing `CustomNotif` is a blocking application-modal dialog despite its name. Replacing it requires deciding whether each call is actually an acknowledgement/error dialog, a transient success message, or a durable workbench status entry.
- ControlsFX `Notifications` can show positioned, auto-hiding popup notifications with actions and owner selection. [ControlsFX 11.2.4 `Notifications` source](https://github.com/controlsfx/controlsfx/blob/11.2.4/controlsfx/src/main/java/org/controlsfx/control/Notifications.java)
- JavaFX 25 has no `ALERT` or `NOTIFICATION` accessible role; it does have `DIALOG`. A transient toast must not be the only place that critical information or recovery actions exist. If auto-dismiss is used, timing, pause/dismiss controls, keyboard reachability, and screen-reader behavior need explicit testing.

## Replacing vendored/private JavaFX 8 mechanisms

| Current mechanism | JavaFX 25-safe replacement inputs | Constraint to carry forward |
| --- | --- | --- |
| Vendored ControlsFX 8 `TableFilter` package | Official ControlsFX 11.2.4 `TableFilter`; ControlsFX `FilteredTableView`; or an application-owned public-API filter model/editor using `FilteredList`, `SortedList`, `TableColumn.graphic`, and `TableColumn.contextMenu` | Preserve access to the effective filtered rows and multi-column filter semantics. Delete the vendored package only when every caller has moved. Official ControlsFX still has reflection/skin coupling that must be accepted or avoided deliberately. |
| `com.sun.javafx.scene.control.skin` imports in the vendored `FilterPanel` | Do not port the old source by changing import names alone. Use an official maintained control or attach an editor through public column properties. | The old code depends on table-header implementation structure. The current official ControlsFX implementation demonstrates that even newer public skin types do not expose every needed header method publicly. |
| `MyUtils.isIndexVisible` casts a skin child to `VirtualFlow` | Prefer behavior that calls public `ListView.scrollTo(index)` when selection/navigation requires visibility. If exact visibility is still a product requirement, prototype either an owned skin subclass exposing its protected flow or a CSS lookup, understanding that both couple to skin structure. | Public `VirtualFlow.getFirstVisibleCell()`/`getLastVisibleCell()` exist, but obtaining the flow from a standard `ListViewSkin` is protected. Exact visibility detection is not available as a direct `ListView` API. |
| Private Modena dialog images | Let public `Alert` supply its semantic graphic or use application-owned image/SVG/icon-pack assets. | Do not depend on resource paths inside JavaFX modules; app assets must be packaged and licensed explicitly. |
| Popup classes reaching across controllers to a `TableFilter` field | Expose effective rows and filter state through a presentation/filter model or service rather than the table widget. | Current bulk actions depend on the filtered result, not on the filter control itself. Separating those concepts keeps later control-library changes local. |

## Accessibility and maintenance gates

These gates apply whichever candidates are selected:

1. Every action must remain keyboard reachable with a visible focus indication. Context-menu-only table filters need an explicit keyboard/open-button path.
2. Icon-only actions need a stable accessible name and tooltip; decorative glyph nodes should not become duplicate accessible/focus targets.
3. Light, dark, Windows high-contrast, reduced-motion, reduced-transparency, and persistent-scrollbar preferences need coverage. JavaFX 25 exposes the necessary preferences, but third-party themes may not honor them automatically.
4. Do not encode status only by color. Active table filters, validation, job state, and severity also need text, icon shape, or another programmatic state.
5. Critical errors and required decisions cannot be auto-dismiss-only notifications. Transient status must have a durable destination such as the workbench status/history area when users may need to revisit it.
6. Run screen-reader and keyboard smoke tests on Windows against standard controls and every selected third-party control. A JavaFX `AccessibleRole` property on the outer node is not proof that a custom skin exposes useful children/actions.
7. Pin exact library and icon-pack versions. Record licenses/notices and verify that `jpackage` includes CSS, fonts, and images.
8. Add JavaFX 25 CSS/screenshot tests for every supported theme and runtime tests for control skins. Theme/control updates must be treated as UI changes, not dependency-only upgrades.
9. Reject any option that requires BS2BG itself to import `com.sun.*`, add broad `--add-opens`, or load resources from another module's implementation package. A library's own narrow internal dependency is a separate, explicit acceptance decision.

## Decisions this research makes ready

The following questions are now sharp enough for separate decision tickets; this note does not answer them:

1. Should the Fluent-inspired visual foundation be application-owned JavaFX 25 CSS tokens, an AtlantaFX-derived custom theme, or a time-boxed JMetro compatibility prototype?
2. Should the NPC tables keep standard `TableView` with an application-owned predicate/editor, adopt official ControlsFX `TableFilter`, or migrate to `FilteredTableView`?
3. Is a dependency that internally uses JavaFX exports/reflection acceptable when BS2BG itself remains free of private API, and what runtime test/update policy would govern it?
4. Should icons be application-owned assets or an Ikonli Fluent UI pack, and which exact glyph/license set covers the workbench actions?
5. Which current `CustomNotif` call sites are modal acknowledgement/errors, which are transient status, and which require a durable notification/history surface?
6. Should theme selection always follow Windows, allow an application override, or support both? How should Windows high-contrast override branded tokens?
7. Can all `isIndexVisible` behavior be replaced by unconditional public scrolling, or does any workflow require exact visible-range detection?

## Primary sources

- [OpenJFX JavaFX 25 release notes](https://github.com/openjdk/jfx/blob/jfx25/doc-files/release-notes-25.md)
- [JavaFX 25 API](https://openjfx.io/javadoc/25/)
- [JavaFX 25 CSS reference](https://openjfx.io/javadoc/25/javafx.graphics/javafx/scene/doc-files/cssref.html)
- [ControlsFX 11.2.4 release and source](https://github.com/controlsfx/controlsfx/tree/11.2.4)
- [AtlantaFX 2.1.0 release and source](https://github.com/mkpaz/atlantafx/tree/v2.1.0)
- [JMetro 11.6.16 release and source](https://github.com/JFXtras/jfxtras-styles/tree/11.6.16)
- [Ikonli 12.4.0 release and source](https://github.com/kordamp/ikonli/tree/v12.4.0)

