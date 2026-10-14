# How JavaFX 25 activates Windows UI Automation, and whether tests can pin it

Research date: 2026-10-13, for issue #122. The repository pins JavaFX `25.0.4` (`pom.xml` `javafx.version`). Source citations point to the `25.0.4+3` build tag of [openjdk/jfx25u] (commit `96aaf07`), which was the newest 25.0.4 tag when this was written; no `25.0.4-ga` tag existed. Where a claim was checked against other versions, the version is named.

## Answer in brief

- **Activation has two layers, and only one of them is global.** The first time any UI Automation (UIA) client asks a Glass window for its provider, the Scene creates its accessible root and sets `PlatformImpl.accessibilityActive` to true. That flag is JVM-wide and nothing ever sets it back to false. The objects that actually change control behaviour are the per-Node accessible peers. These are created lazily, Node by Node, when a client queries that Node, and are disposed when the Node leaves its Scene.
- **No screen reader is needed.** Any process that calls `ElementFromHandle`, `ElementFromPoint` or `GetFocusedElement` on the window, or subscribes to UIA events that reach it, triggers activation. JBS documents one case where a non-screen-reader component did this: the Tablet PC text-input service on a touch-screen system (JDK-8132897).
- **Tests can pin accessibility off, but not on.** `-Dglass.accessible.force=false` disables the provider for the whole JVM. It is an internal, unsupported property, present from 8u40 through current `jfx` master. `=true` does nothing extra on Windows 10/11, because the provider is already enabled there by default. No switch makes accessibility active. The only way to turn it on is for a UIA client to query the window, which is what the packaged smoke test already does.
- **Only two behaviours change while accessibility is active.** The global flag affects only `Chart`. Everything that matters for `ListView` happens per query. A client query can create hidden "private" cells inside `VirtualFlow`, and the Windows provider never raises UIA StructureChanged events. So the provider never tells clients that rows appeared or disappeared, for example after `setAll`, an empty-to-filled refill, `refresh()`, or a node swap. Clients only learn about the change by navigating again.

## 1. What activates accessibility

### Provider gate (`View.accessible`)

`com.sun.glass.ui.View` has a `static final boolean accessible`, computed once when the class initialises. If the system property `glass.accessible.force` is set, its value wins. Otherwise the value is true on Windows when `os.version` is 6.1 (Windows 7) or later ([View.java L46-61][view-accessible]). Windows 10 and 11 report `os.version` `10.0`, so the default there is true.

`View.getAccessible()` is the JNI upcall Glass makes when the platform asks for a provider ("On Windows it happens on WM_GETOBJECT"). It returns a native provider only if `accessible` is true. In that case it calls `eventHandler.getSceneAccessible()` ([View.java L1143-1162][view-getaccessible]), which routes through Quantum to `Scene.getAccessible()` ([GlassViewEventHandler.java L1243-1248][gveh], [Scene.java L3098-3100][scene-listener]).

### Win32 trigger (`WM_GETOBJECT`)

`GlassWindow` and `FullScreenWindow` both forward `WM_GETOBJECT` to `ViewContainer::HandleViewGetAccessible` ([GlassWindow.cpp L595-599][glasswindow], [ViewContainer.cpp L242-297][viewcontainer]). That function handles two cases:

- `lParam == UiaRootObjectId`: a UIA client is asking for the provider. Glass calls `View.getAccessible()` without further checks and returns the provider through `UiaReturnRawElementProvider`.
- `lParam == OBJID_CLIENT`: an MSAA request. Glass initialises accessibility only if `SPI_GETSCREENREADER` is set and `UiaClientsAreListening()` is true. Even then it deliberately returns no object, so that no UIA-to-MSAA bridge is created. The source comment says this check was added because "in some systems (i.e. touch monitors), OBJID_CLIENT are sent when no screen reader is active". This path came from the JAWS work in JDK-8096572.

Microsoft documents when UIA sends `WM_GETOBJECT`: "when a client calls IUIAutomation::ElementFromHandle, ElementFromPoint, and GetFocusedElement, and when handling events for which the client has registered" ([Microsoft: Handling the WM_GETOBJECT Message][ms-getobject]).

### Accessible root creation, which sets the global flag

`Scene.getAccessible()` creates the Scene's root accessible on first call. Inside that creation it executes `PlatformImpl.accessibilityActiveProperty().set(true)` ([Scene.java L5915-5978][scene-getaccessible]). Searching all of `javafx.graphics` and `javafx.controls` in 25.0.4 finds no other writer of that property.

`PlatformImpl.accessibilityActive` is a `private static BooleanProperty` ([PlatformImpl.java L102, L602-604][platformimpl]). The public, read-only view of it is `Platform.accessibilityActiveProperty()` / `Platform.isAccessibilityActive()`, available since 8u40 and readable only on the FX thread. Its Javadoc says it is "typically set to true the first time an assistive technology, such as a screen reader, requests information about any JavaFX window or its children" ([Platform.java L422-450][platform]). The API was added by JDK-8094383.

### Is the state JVM-global and sticky?

- **`accessibilityActive`: yes to both.** It is a static field. One `WM_GETOBJECT(UiaRootObjectId)` on any Glass window in the JVM sets it, and no code path clears it. Within a Surefire fork, the flag stays true for every test that runs after the first UIA query.
- **`View.accessible`: global and fixed for the JVM's lifetime.** It is a `static final` field, read once when `com.sun.glass.ui.View` initialises. Changing `glass.accessible.force` after the toolkit has started has no effect.
- **Per-Scene and per-Node peers: not global.** Each Scene creates its own root accessible when it is first queried, and releases it when the Scene's peer goes away. Each Node gets a peer only when a client asks for that Node ([Node.java L10518-10549][node-getaccessible]). When a Node leaves its Scene, its peer is disposed; if the Node is detached to no Scene, disposal is deferred to the next pulse so the peer can be reused if the Node comes back ([Node.java L1194-1221][node-dispose]). `Node.notifyAccessibleAttributeChanged` does nothing for a Node that has no peer ([Node.java L10505-10515][node-notify]).

  So "accessibility active" in the global sense does **not** mean every control is now behaving differently. A control behaves differently only after a client has walked into it.

## 2. Can ordinary desktop software trigger it?

Yes. No screen reader is required. Anything that acts as a UIA client against the BS2BG window triggers the `UiaRootObjectId` path, and that path has no screen-reader check.

| Source | Evidence | Status |
| --- | --- | --- |
| Tablet PC text input / touch keyboard service (`tiptsf.dll`) on touch-screen hardware | JDK-8132897: on a touch-screen system with no screen reader, JavaFX accessibility initialised and hung `ComboBox` in a `Navigate()` loop. The native stack showed `tiptsf.dll` calling `UiaHasServerSideProvider` / `UiaRaiseAutomationPropertyChangedEvent`. The OpenJFX engineers wrote that "the a11y code in FX is initialized when running any JavaFX program if a touch screen is present … even when the screen reader is not running", and that "touch screen drivers use a11y interfaces and thus activate a11y support". The documented workaround was `-Dglass.accessible.force=false`. The bug was fixed in JavaFX 9 (with an 8u66 backport approved in the issue) by repairing `Navigate(Parent)`; activation itself was left alone. The engineers noted that a follow-up was needed on why the tree is created without a screen reader, but I found no such follow-up fix. An earlier JavaFX 2.x report, JDK-8095327, describes the same class of problem ("a11y code is activated by touch devices"). | **Confirmed** (JBS) |
| Touch keyboard / input pane in general | Microsoft documents that the touch keyboard appears for custom text controls only if they expose the right UIA control patterns, so the input stack reads UIA ([Microsoft: Respond to the presence of the touch keyboard][ms-touchkb]). | **Confirmed** that it is a UIA consumer. Whether it queries a window that has no touch input was **not confirmed**. |
| Magnifier | Microsoft names "screen readers and screen magnifiers" as the UIA event clients ([Microsoft: UI Automation Events Overview][ms-events]). Magnifier's "follow keyboard focus" and "follow text cursor" modes depend on those events. | **Inferred** from Microsoft's docs. Not tested against JavaFX. |
| Voice Access, Windows Speech Recognition | Microsoft describes speech/voice tools as needing UIA names to target controls ([Microsoft: Accessibility design basics][ms-design]). I found no first-party Voice Access implementation document. | **Not confirmed** from a primary source |
| Password managers, IME candidate UIs, clipboard/"text suggestions" tools, desktop automation, Accessibility Insights / Inspect | The mechanism applies to any of them: a single `ElementFromHandle`/`ElementFromPoint`/`GetFocusedElement` on the window, or a focus-event subscription that resolves to it, is enough. The Accessibility Insights for Windows FAQ says "Frameworks known to support UIA are JavaFX and QT" ([FAQ][ai-faq]). No JBS report names a specific password manager or IME. | Mechanism **confirmed**. Specific products **not confirmed**. |

Practical consequence: on a developer desktop, a test JVM may become accessibility-active because of software the test does not control, as soon as one of these clients touches a test window. The MSAA `OBJID_CLIENT` path is narrower: it requires `SPI_GETSCREENREADER`, which Microsoft says Narrator does not set ([Microsoft: Screen reader parameter][ms-spi]). The UIA path has no such gate.

## 3. Override switches

| Switch | Effect | Versions | Supported? |
| --- | --- | --- | --- |
| `-Dglass.accessible.force=false` | `View.accessible = false`, so `getAccessible()` returns 0 for every `WM_GETOBJECT`, `Scene.getAccessible()` is never reached, and `accessibilityActive` stays false. UIA clients see only the default HWND proxy, with no JavaFX children. | Present in 8u40 (`8u40-b17`), in 25.0.4+3, and on `jfx` master (27) as of 2026-10-13. Verified by reading `View.java` at each of those refs. | **No.** It is internal. When the property was named in JDK-8097517, Kevin Rushforth asked for a non-`javafx` prefix because "the 'force' flag isn't intended to be supported". It does not appear in the public docs or release notes. |
| `-Dglass.accessible.force=true` | Forces `View.accessible = true`. It does **not** set `accessibilityActive`; a client query is still required. It is a no-op on Windows 10/11, where the default is already true. | Same as above | No |
| `-Djavafx.accessible` / `-Djavafx.accessible.force` | Older opt-in switches. Accessibility was off by default before 8u40. | JavaFX 8u20 and earlier; removed by JDK-8097517 in 8u40 | Obsolete |
| `Platform.accessibilityActiveProperty()` | Read-only. Tests can **detect** activation with it, but cannot change it. | 8u40+ | Public API |
| `PlatformImpl.accessibilityActiveProperty().set(true)` through `--add-exports javafx.graphics/com.sun.javafx.application=…` | Flips only the flag, which affects only `Chart`. It creates no peers and changes no `ListView` behaviour. | 8u40+ | Internal; not a meaningful way to pin anything |

Requirements for pinning in a test JVM:

- **Pin off.** Add `-Dglass.accessible.force=false` to the Surefire `argLine`, or set it with `System.setProperty` before the toolkit starts and before `com.sun.glass.ui.View` initialises. The result is deterministic across the whole fork. But it also removes the code path the ListView workarounds exist for, so such a fork can never reproduce the UIA failures. It only isolates non-UIA tests from stray desktop clients. As an internal property, it could be renamed or removed in any JavaFX release without notice.
- **Pin on.** There is no in-process switch. A test has to become a UIA client itself: for example, call `IUIAutomation::ElementFromHandle` on the Stage HWND through a helper process (as `tools/java25/smoke-app-image.ps1` does with `System.Windows.Automation`), then walk into the controls under test. Activating the global flag alone is not enough. The specific `ListView` must be queried so its peers and private cells exist.
- **Detect.** Reading `Platform.isAccessibilityActive()` on the FX thread is a cheap, public way to check whether some outside client has already activated the fork.
- **Upstream outlook.** JDK-8391664 ("Add API to track Accessibility Events", opened 2026-09-02, still open) asks for a test hook because "the Accessibility notifications only reach the assistive technology bridge - which isn't present without a running screen reader". Reviewers prefer a non-public, `com.sun`-style shim, and note that "Accessible is only created when there is a need … so it is usually null". Nothing has shipped yet.

## 4. Which control behaviours differ while accessibility is active

### Behaviours tied to the global flag

In 25.0.4, the only consumer of `Platform.accessibilityActiveProperty()` in `javafx.controls` is `Chart`, which makes chart symbols focus-traversable when the flag is true ([Chart.java L530-575][chart]; JDK-8349091). Neither `ListView`, `VirtualFlow`, nor any skin reads the flag.

### Behaviours tied to per-query peers

The following changes happen only after a UIA client has queried the specific `ListView`:

1. **Hidden private cells are added to the scene graph.** `ListViewSkin.queryAccessibleAttribute` answers `FOCUS_ITEM`, `ITEM_AT_INDEX` and `SELECTED_ITEMS` through `VirtualFlow.getPrivateCell(index)` ([ListViewSkin.java L350-401][lvs-query]). `getPrivateCell` works in three steps ([VirtualFlow.java L2785-2833][vf-private]):
   - It returns a visible cell for that index if one exists, after forcing `cell.layout()`.
   - Otherwise it scans `sheetChildren` for any cell whose index matches.
   - Otherwise it builds a new cell from the cell factory, sets it `visible=false`, and adds it to `sheetChildren` and `privateCells`.

   Private cells are released only on a rebuild or recreate pass ([VirtualFlow.java L1080-1101][vf-rebuild]; the leak fixed in JDK-8203345 in jfx11). They are real `ListCell` instances. They run `updateItem` and therefore the controller's cell factory, which in BS2BG sets accessible text/help.

   **This path never runs without a UIA client.** JDK-8203345 confirms that `getPrivateCell` "is only called via … queryAccessibleAttribute".
2. **UIA child order comes from the item index, not the scene graph.** For `LIST_VIEW`, `Navigate(FirstChild/LastChild)` first probes `ITEM_AT_INDEX(0)`. It then looks at the visible scene-graph children of the ListView, and switches to index-based navigation (`NavigateListView`) once it reaches a `LIST_ITEM` ([WinAccessible.java L1019-1042, L1133-1170][wa-nav]). Children are filtered by `Node::isVisible` ([WinAccessible.java L513-522][wa-children]).

   While the list is empty, `ListViewSkin` hides the `VirtualFlow` and shows the placeholder ([ListViewSkin.java L475-500][lvs-placeholder]). So an empty list's only UIA child is the placeholder, and a refill has to flip that visibility before rows can be found.
3. **No StructureChanged events.** `WinAccessible.sendNotification` raises only focus, selection, toggle, value, text and similar events ([WinAccessible.java L270-…][wa-notify]). Neither 25.0.4 nor `jfx` master calls `UiaRaiseStructureChangedEvent` anywhere. When rows are added, removed or rebuilt, or a whole `ListView` is swapped out, UIA clients are not told, and must navigate again from an ancestor.
4. **New peers on every swap and every dispose.** Each `WinAccessible` gets a new `RuntimeId` (`UiaAppendRuntimeId`, `idCount++`, [WinAccessible.java L247][wa-id]). Disposing a peer destroys the native object ([WinAccessible.java L251-266][wa-dispose]), after which its methods return null or 0. A replacement `ListView` node, or a cell that left the Scene, is a different UIA element from the client's point of view. Element references the client already holds go stale.
5. **Focus events depend on peers existing.** `Scene` raises UIA focus-changed events only once its root accessible exists (Scene.java L2184, L2347). `ListView` focus-model changes call `notifyAccessibleAttributeChanged(FOCUS_ITEM)`, which is silent until the ListView has a peer. `ListCell.layoutChildren` posts a `runLater` FOCUS_ITEM notification for the focused cell whether or not accessibility is active (JDK-8309374; [ListCell.java L352-378][listcell]); without a peer, it does nothing.

### What this means for BS2BG's workarounds

The workarounds are in `src/com/asdasfa/jbs2bg/workbench/WorkbenchController.java` (the issue cites L3257-3510).

- **Empty-to-refill node replacement** (`reconcileSliderPresetItems` / `replaceEmptySliderPresetList` and the Custom Morph Target, NPC Morph Assignment and assigned-preset equivalents, L3323-3485). The source shows that the `ListView` itself handles an empty-to-filled change normally:
  - `updateItemCount` with `oldCount == 0` requests a full cell rebuild and flips placeholder/flow visibility (ListViewSkin.java L331-346, L475-500).
  - No accessibility code runs unless a client has queried the list.
  - The provider never raises StructureChanged, so a client that cached or held the empty list's subtree is never told rows appeared.

  Swapping in a fresh `ListView` gives the client a new element with a new RuntimeId and no stale peers or private cells. That is consistent with the workaround's observed effect. **The exact mechanism that keeps the old control's subtree empty "for the lifetime of that control" was not confirmed from source.** Two candidates fit what I read: the missing StructureChanged event, combined with client-side caching in the managed `System.Windows.Automation` client the smoke test uses; and stale private or placeholder state left over from queries made while the list was empty. Separating them needs an instrumented run (Accessibility Insights event log, or a UIA client that re-walks with no cache). The workaround only matters on a code path reached when a client has walked into the list; in a fork where no client ever does, it is inert apart from the extra node allocation and focus handoff.
- **"Avoid no-op `setAll`" guard** (`!List.copyOf(items).equals(visible)`, L3331/3342/3353/3412). A `setAll` with equal contents is reported as a replace. `ListViewSkin` then marks cells dirty and reconfigures them (L130-160, L331-346). Without accessibility, that only costs extra layout. With a client attached, any private cells it had materialised can be rebuilt and their peers disposed, and no StructureChanged tells the client. The guard avoids creating churn for UIA that it cannot see, and is harmless when accessibility is inactive.
- **Rename via `edit()` instead of `refresh()`** (`synchronizeRenameEditor`, L3509-3534). In 25.0.4, `ListView.refresh()` puts `Properties.RECREATE` into the control's properties. The skin turns that into `needCellsRebuilt`, then `VirtualFlow.rebuildCells()`, which moves every cell to the pile, resets indices and calls `releaseAllPrivateCells()` ([ListView.java L1043-1045][lv-refresh]; ListViewSkin.java L118-125, L280-286; VirtualFlow.java L1092-1100). The released private cells leave the Scene, so their peers are disposed and become stale for any client holding them, and again no StructureChanged event is raised. Without accessibility, `refresh()` is visually harmless. That fits the controller's comment that `refresh()` "drops JavaFX 25 UIA children after rejected edits". Using the editing lifecycle keeps the existing cells and peers.
- **Slider choice row reuse** (`reconcileSliderChoiceRows`, L3256-3285) is the same idea outside `ListView`. Reusing nodes keeps their peers and RuntimeIds; replacing them in `getChildren().setAll(...)` would dispose the old peers without a StructureChanged event.

Net: every workaround addresses UIA-client-visible churn. None corrects ordinary JavaFX rendering. Each is a no-op or near-no-op in a JVM where no UIA client queries the affected control, so pinning accessibility off would make them untestable rather than wrong.

## Not confirmed

- That Magnifier, Voice Access, a particular IME, or a particular password manager queries a JavaFX window. The mechanism is confirmed; the product-specific behaviour is not.
- The precise root cause of the "empty list stays empty in UIA" symptom. Candidate mechanisms are listed above.
- Whether `glass.accessible.force` will survive future JavaFX releases. It is internal and undocumented.

## Sources

Primary source code (openjdk/jfx25u `25.0.4+3`, commit `96aaf07f6404c4d7ccad1aa75498ec7d2196047a`) is linked inline. JBS issues: [JDK-8132897], [JDK-8095327], [JDK-8096572], [JDK-8097517], [JDK-8094383], [JDK-8203345], [JDK-8349091], [JDK-8391664]. Microsoft documentation is linked inline.

[openjdk/jfx25u]: https://github.com/openjdk/jfx25u
[view-accessible]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/com/sun/glass/ui/View.java#L46-L61
[view-getaccessible]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/com/sun/glass/ui/View.java#L1143-L1162
[gveh]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/com/sun/javafx/tk/quantum/GlassViewEventHandler.java#L1243-L1248
[scene-listener]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/javafx/scene/Scene.java#L3098-L3100
[glasswindow]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/native-glass/win/GlassWindow.cpp#L595-L599
[viewcontainer]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/native-glass/win/ViewContainer.cpp#L242-L297
[scene-getaccessible]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/javafx/scene/Scene.java#L5915-L5978
[platformimpl]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/com/sun/javafx/application/PlatformImpl.java#L602-L604
[platform]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/javafx/application/Platform.java#L422-L450
[node-getaccessible]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/javafx/scene/Node.java#L10518-L10549
[node-notify]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/javafx/scene/Node.java#L10505-L10515
[node-dispose]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/javafx/scene/Node.java#L1194-L1221
[chart]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.controls/src/main/java/javafx/scene/chart/Chart.java#L530-L575
[lvs-query]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.controls/src/main/java/javafx/scene/control/skin/ListViewSkin.java#L350-L401
[lvs-placeholder]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.controls/src/main/java/javafx/scene/control/skin/ListViewSkin.java#L475-L500
[vf-private]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.controls/src/main/java/javafx/scene/control/skin/VirtualFlow.java#L2785-L2833
[vf-rebuild]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.controls/src/main/java/javafx/scene/control/skin/VirtualFlow.java#L1080-L1101
[lv-refresh]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.controls/src/main/java/javafx/scene/control/ListView.java#L1043-L1045
[listcell]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.controls/src/main/java/javafx/scene/control/ListCell.java#L352-L378
[wa-nav]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L1019-L1170
[wa-children]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L513-L522
[wa-notify]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L270-L303
[wa-id]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L247
[wa-dispose]: https://github.com/openjdk/jfx25u/blob/96aaf07f6404c4d7ccad1aa75498ec7d2196047a/modules/javafx.graphics/src/main/java/com/sun/glass/ui/win/WinAccessible.java#L251-L266
[JDK-8132897]: https://bugs.openjdk.org/browse/JDK-8132897
[JDK-8095327]: https://bugs.openjdk.org/browse/JDK-8095327
[JDK-8096572]: https://bugs.openjdk.org/browse/JDK-8096572
[JDK-8097517]: https://bugs.openjdk.org/browse/JDK-8097517
[JDK-8094383]: https://bugs.openjdk.org/browse/JDK-8094383
[JDK-8203345]: https://bugs.openjdk.org/browse/JDK-8203345
[JDK-8349091]: https://bugs.openjdk.org/browse/JDK-8349091
[JDK-8391664]: https://bugs.openjdk.org/browse/JDK-8391664
[ms-getobject]: https://learn.microsoft.com/windows/win32/winauto/handling-the-wm-getobject-message
[ms-touchkb]: https://learn.microsoft.com/windows/apps/develop/input/respond-to-the-presence-of-the-touch-keyboard
[ms-events]: https://learn.microsoft.com/windows/win32/winauto/uiauto-eventsoverview
[ms-design]: https://learn.microsoft.com/windows/win32/uxguide/inter-accessibility
[ms-spi]: https://learn.microsoft.com/windows/win32/winauto/screen-reader-parameter
[ai-faq]: https://accessibilityinsights.io/docs/windows/reference/faq/
