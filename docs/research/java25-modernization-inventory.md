# Java 25 modernization and legacy-risk inventory

Research date: 2026-08-27

Question: Where can stable Java 25 language and library features materially clarify BS2BG or reduce infrastructure, which preview features should remain excluded, and which dependencies or internal APIs are obsolete or risky?

This is a decision input, not an adoption policy. It separates changes that can preserve current contracts mechanically from changes that alter APIs, concurrency ownership, UI behavior, or persistence semantics.

## Executive findings

1. **The full application is not yet a Java 25 build.** `pom.xml` resolves JavaFX 25.0.4, but sets `maven.compiler.release` to 8 and compiles only `data`, `project`, and `presentation`. `mvn test` on JDK 26.0.2 passes 104 tests, while a direct `javac --release 25` compile of every source file fails with 23 errors. The exclusions currently hide the UI port's hard failures.
2. **The hard failures are JavaFX 8 skin dependencies, not Java language incompatibilities.** `MyUtils` and the vendored ControlsFX table filter import five `com.sun.javafx.scene.control.skin` classes. JavaFX 25 exposes equivalents under `javafx.scene.control.skin`, but the methods used by this code, including `TableViewSkinBase.getTableHeaderRow()` and `VirtualContainerBase.getVirtualFlow()`, are protected. Renaming imports therefore does not complete the port. `CustomConfirm` also loads a Modena icon from a private `/com/sun/...` resource path.
3. **The core is already shaped well for selected modern Java features.** Immutable snapshots, typed outcomes, package-private edit request families, and one synchronized session boundary are natural candidates for records, sealed hierarchies, pattern matching, collection factories, and eventually virtual-thread-backed jobs. Only the smallest syntax/API substitutions are mechanical; records, sealing, and concurrency change observable contracts or ownership.
4. **One runtime dependency is explicitly unmaintained and another is old enough to be unnecessary in part.** The minimal-json maintainer labels 0.9.5 unmaintained; BS2BG uses it across project persistence, settings, and generated output. Commons IO 2.6 is from 2017 and many of its BS2BG uses now have JDK `Files` equivalents. Charset detection is different: juniversalchardet remains maintained and the JDK does not provide an equivalent detector.
5. **No JDK-internal `sun.*` or `jdk.internal.*` API was found.** The `javax.xml` imports are standard APIs in the `java.xml` module. The problem is specifically JavaFX-internal code/resources and vendored library drift.

## Build and source evidence

- `pom.xml:13-16` pins JavaFX 25.0.4 but release 8; `pom.xml:74-89` limits compilation to three packages and documents the JavaFX 8 skin exclusion.
- `mvn dependency:tree -Dscope=runtime` resolves only minimal-json 0.9.5, Commons IO 2.6, juniversalchardet 2.1.0, and the JavaFX 25.0.4 modules/classifiers. There are no unexpected non-JavaFX transitives.
- `mvn test` reports `Tests run: 104, Failures: 0, Errors: 0, Skipped: 0` on JDK 26.0.2.
- A full `javac --release 25 -Xlint:all` compile reports 23 errors from missing `com.sun.javafx.scene.control.skin` types, plus 25 warnings. The warnings include raw/unchecked vendored ControlsFX code and deprecated `minimal-json` `ParseException.getLine()` / `getColumn()` calls.
- `src/com/asdasfa/jbs2bg/etc/MyUtils.java:5-6`, `controlsfx/table/FilterPanel.java:29-31`, and `CustomConfirm.java:40` are all current JavaFX-internal touchpoints.
- The official JavaFX 25 API documents public skin types, but keeps the two accessors used here protected: [`TableViewSkinBase.getTableHeaderRow()`](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/skin/TableViewSkinBase.html#getTableHeaderRow()) and [`VirtualContainerBase.getVirtualFlow()`](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/skin/VirtualContainerBase.html#getVirtualFlow()). The public `ListView.scrollTo` contract already promises to make an item visible, so the private-skin visibility probe should be reconsidered at the interaction level rather than ported mechanically ([JavaFX 25 `ListView`](https://openjfx.io/javadoc/25/javafx.controls/javafx/scene/control/ListView.html#scrollTo(int))).

## Safe mechanical candidates

These candidates do not require a new architecture decision if their existing tests are retained. “Mechanical” still means reviewing exception messages, ordering, and exact output where compatibility tests cover them.

| Candidate | Current locations | Why it is bounded |
|---|---|---|
| Raise the compiler release and forbid preview flags once the full source compiles | `pom.xml` | This activates the already accepted Java 25 baseline. It should be coupled to removal of the compiler include list so regressions cannot hide in excluded UI packages. |
| Use pattern variables for existing `instanceof`-then-cast chains | `DefaultProjectSession.apply`, import outcome classification | Pattern matching for `instanceof` has been permanent since Java 16. Keeping the same ordered chain preserves the current unsupported-edit fallback; it does not require sealing. ([Oracle Java 25 language summary](https://docs.oracle.com/en/java/javase/25/language/java-language-changes-summary.html)) |
| Use a switch expression for closed enum-to-value mappings | `DefaultProjectSession.outcomeAtSnapshot` and similarly total enum switches | Switch expressions are permanent and can make exhaustiveness compiler-checked without changing the domain hierarchy. Avoid bulk conversion of UI event switches where fall-through or side effects require individual review. |
| Replace the implementation of `ImmutableValues.copyOf` with `List.copyOf` | `project/ImmutableValues.java` | The helper already copies, preserves iteration order, rejects null elements, and returns an unmodifiable list—the specified behavior of `List.copyOf`. Its custom argument text would change, so tests must not depend on NPE messages. ([Java 25 `List.copyOf`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))) |
| Use `Stream.toList()` when the consumer requires an encounter-ordered unmodifiable result | Selected collection pipelines, after checking each consumer | `Stream.toList()` preserves encounter order and returns an unmodifiable list. It is not a blanket replacement where a mutable result, a specific implementation, or null rejection is part of the contract. ([Java 25 `Stream.toList`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/stream/Stream.html#toList())) |
| Use `Files.readString` / `Files.writeString` with `Charset` or `StandardCharsets.UTF_8` for simple complete-text operations | `Settings`; project loader's `readAllBytes` + `new String`; simple controller exports | These APIs have existed since Java 11 and close resources themselves. With no options, `writeString` creates or truncates the file. Parent-directory creation and current quiet-delete behavior must be checked per call. ([Java 25 `Files`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html#writeString(java.nio.file.Path,java.lang.CharSequence,java.nio.charset.Charset,java.nio.file.OpenOption...))) |
| Use `ParseException.getLocation()` rather than its deprecated line/column forwarding methods | `DefaultProjectSession.java:110-111` | minimal-json 0.9.5 already exposes `Location`; this removes a compiler warning without choosing the parser's future. |
| Replace anonymous comparators with comparator factories/lambdas where the comparator is purely declarative | `Project`, `ProjectFileLoader`, `SliderChoiceDefaults`, `ProjectOutputFormatter` | This is mostly Java 8-era cleanup, but it is behavior-preserving when the same fields, case handling, and tie-breaking are retained. Canonical ordering tests are the gate. |
| Replace proven `Optional.get()` calls with `orElseThrow()` or local extraction | `ProjectPresentation`, `DefaultProjectSession` | This can make the required invariant explicit. It must remain after the same presence/lifecycle check; this is not permission to spread `Optional` into fields or parameters. |

Two tempting substitutions are **not** mechanical:

- `Map.copyOf` must not replace `Collections.unmodifiableMap(new LinkedHashMap<>(...))` in `ProjectGeneratedOutput`. Java 25 specifies the factory map's iteration order as unspecified, while the output maps intentionally retain canonical encounter order. A `LinkedHashMap`-backed unmodifiable copy—or an explicit `SequencedMap` decision—remains necessary. ([Java 25 `Map`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Map.html#unmodifiable))
- `List.of` / `List.copyOf` reject nulls immediately. Several public edit factories intentionally carry null into `ProjectSession` so it becomes a structured rejection. Replacing their collection construction can move validation across the session boundary.

## Architecture-sensitive opportunities

### Records and record patterns

Good structural candidates are `SourceLocation`, diagnostics, identity and choice values, snapshots, generated-output/update values, parser holders, and the many package-private edit request classes. Records are permanent since Java 16 and record patterns since Java 21 ([Oracle Java 25 language summary](https://docs.oracle.com/en/java/javase/25/language/java-language-changes-summary.html)).

This is not a bulk conversion:

- Public snapshots currently expose JavaBean-style `get...` methods and deliberately do not define value equality. A record changes accessors, superclass constraints, generated `equals`/`hashCode`/`toString`, reflection shape, and construction rules. Alias getters can preserve source calls but not the complete class contract.
- Package-private edit requests are lower risk, but generated value equality could become observable to callers holding them as `ProjectEdit`.
- `Project` uses **instance identity** to mean “unchanged.” Record/value equality must not replace that invariant.
- Compact constructors are useful for centralized normalization/validation, but changing when null or malformed values are rejected can violate the structured diagnostic boundary.

The decision required is which types are transparent values in the supported API and which must retain their current class shape.

### Sealed outcomes/edits and pattern switches

`ProjectOutcome` plus its four final subclasses is effectively closed to external packages because its constructors are package-private. Sealing it would document that fact and allow an exhaustive pattern switch for import classification. The family interfaces inside the three `*Edits` factories are similarly strong internal candidates.

The public `ProjectEdit` interface is different. It is currently open, and `DefaultProjectSession.apply` returns a structured `EDIT_UNSUPPORTED` rejection for an unknown implementation. Sealing it would remove that behavior and is an API/extension-policy decision. Pattern matching for switch is permanent since Java 21, but exhaustiveness should follow—not force—the hierarchy decision.

### Background jobs and virtual threads

`MainController`, `PopupBosViewController`, and `PopupNpcDatabaseController` each start raw platform threads around JavaFX `Task`; only `MainController` partially centralizes handlers. The accepted UI direction calls for centralized cancellable jobs with scoped disabling and progress. Virtual threads are permanent since Java 21 and suit blocking file I/O, but OpenJDK explicitly positions them as a throughput tool rather than a new concurrency model ([JEP 444](https://openjdk.org/jeps/444)).

A job service decision must come first because it owns:

- executor lifetime and application shutdown;
- cancellation/interruption and whether a file operation is safely interruptible;
- single-flight or conflict rules around `ProjectSession` and output destinations;
- delivery back to the JavaFX application thread;
- busy state scoped to a workspace/pane rather than whole-window disabling;
- exception propagation and user notifications.

`Data.parseNpcFile` currently mutates a JavaFX `ObservableList` from a worker thread. Modernization should separate plain parsed results from JavaFX-thread publication without moving the NPC Database into `ProjectSession`; virtual threads alone do not make that mutation safe.

### NIO and Commons IO removal

Most Commons IO calls are complete-string reads/writes or quiet deletion and can be expressed with `Files`. `Data.parseNpcFile` still uses `LineIterator`, and the JavaFX controllers rely on the failure-swallowing semantics of `deleteQuietly`. Removing the dependency therefore requires explicit choices about streaming, parent creation, overwrite atomicity, charset fallback, and truthful errors. Updating 2.6 independently is a separate, smaller option; this inventory does not choose between update and removal.

### Table filtering and skin access

The six files under `controlsfx/table` are a modified, vendored copy of 2014-2016 ControlsFX source. Current upstream ControlsFX still offers `TableFilter`, with 11.2.4 the latest listed release as of this research ([ControlsFX releases](https://github.com/controlsfx/controlsfx/releases), [TableFilter API](https://controlsfx.github.io/javadoc/11.2.2/org.controlsfx.controls/org/controlsfx/control/table/TableFilter.html)). Three routes remain open:

1. adopt and compatibility-test a maintained upstream dependency;
2. implement the needed filters using only public JavaFX APIs and the new workbench's visible filter controls;
3. remove column-header filtering if the modern information architecture makes it redundant.

Merely changing `com.sun` imports is not a route: the protected header access still fails. This decision is coupled to the modern UI prototype, not the Java syntax sweep.

## Dependency and toolchain risk register

| Component | Current state and use | Risk / unresolved question |
|---|---|---|
| `minimal-json` 0.9.5 | Loader, writer, settings, generated BoS JSON, and compatibility tests | The maintainer explicitly labels it **UNMAINTAINED**, and Maven Central's 0.9.5 artifact dates to 2017 ([project README](https://github.com/ralfstx/minimal-json#minimal-json-unmaintained), [Central artifact](https://repo1.maven.org/maven2/com/eclipsesource/minimal-json/minimal-json/0.9.5/)). Replacement is desirable but high-risk: exact number handling, object member behavior, diagnostics, pretty output, and `.jbs2bg` compatibility must be characterized before selecting a parser. |
| Commons IO 2.6 | File line iteration, complete-text settings/export I/O, quiet deletes | Apache records 2.6 as a 2017 release; current 2.22.0 was released in 2026 ([release history](https://commons.apache.org/proper/commons-io/changes.html)). Decide update versus JDK removal. Do not preserve `deleteQuietly` accidentally if truthful write failures are required. |
| juniversalchardet 2.1.0 | Charset detection for Project and NPC inputs | Maintained rather than obsolete. Upstream documents 2.5.0 and the same `detectCharset` API; it also documents that detection may return null ([official README](https://github.com/albfernandez/juniversalchardet#readme)). Decide the fallback charset and upgrade verification; do not remove it on the assumption Java 25 detects arbitrary encodings. |
| JavaFX 25.0.4 | Controls and FXML | The chosen baseline resolves successfully. The risk is excluded JavaFX 8-era application code, not this dependency. Add a full-source compile gate before considering the port complete. Avoid `jfx.incubator.*` APIs unless a separate decision accepts their modification/removal risk; the JavaFX 25 overview labels those modules experimental ([JavaFX 25 API](https://openjfx.io/javadoc/25/)). |
| Vendored ControlsFX table filter | Six copied source files, no dependency coordinate | It has upstream drift, unchecked/raw warnings, and causes most Java 25 UI compile errors. Resolve through the UI/filter decision above. Preserve its license notices until the copied code is actually removed. |
| JUnit 5.11.4 | Tests only | The suite is green. The Maven versions check reports a newer major (6.1.3), so this is routine build maintenance with its own release-notes review, not a runtime modernization blocker. |
| Maven compiler/surefire configuration | Compiler 3.13.0, Surefire 3.5.2, no minimum Maven enforcement | The more important risk is the intentional source exclusion. Maven's versions check also reports compiler 3.15.0 and no declared minimum Maven version. Plugin updates should follow the full-build change rather than mask it. |

The repository has no `module-info.java`. This matches the accepted packaging direction to defer JPMS unless it has a demonstrated benefit; modernization should not use modules merely to work around internal JavaFX access.

## Preview and experimental features to exclude

The build should not add `--enable-preview`. Oracle notes that preview code requires matching compiler/runtime flags and may not compile or run on a later JDK ([Java 25 preview guide](https://docs.oracle.com/en/java/javase/25/language/preview-language-vm-features.html)). For Java 25 specifically:

- **Primitive types in patterns, `instanceof`, and `switch`** are a third preview. Ordinary reference-type pattern matching and record patterns are permanent and sufficient here.
- **Structured Concurrency (`StructuredTaskScope`)** is a fifth-preview API. It is attractive for jobs, but the Java 25 API still marks it preview; use stable executor/future/thread APIs for the production design.
- **Stable Values** and **PEM encodings** are preview APIs in Java 25 and have no demonstrated BS2BG need.
- **String Templates** are not a Java 25 feature at all; they were withdrawn after Java 22/23 previews. Do not plan output formatting around them.
- JavaFX `jfx.incubator.*` modules are experimental, even though they use JavaFX's incubator designation rather than the JDK preview flag.

Oracle's Java 25 lists module import declarations, compact source files/instance main methods, and flexible constructor bodies as permanent. They do not materially improve this packaged JavaFX application: compact source files target small programs, module imports obscure dependencies in normal production classes, and the current constructors have no compelling pre-super initialization problem. “Stable” is not by itself a reason to adopt a feature.

## Interaction with accepted ADRs

- [Deepen Project state behind ProjectSession](../adr/0001-deepen-project-state-behind-project-session.md) remains the primary boundary. Modern jobs may schedule its synchronous, thread-safe operations and render returned snapshots, but must not move random selection, the NPC Database, preferences, slider configuration, or generated-output caches into the session.
- That ADR's Java 8 source-compatibility statement is superseded by the accepted Java 25 baseline, but its `.jbs2bg` and semantic compatibility requirement is not. NIO, records, or parser changes do not authorize format drift.
- [Project aggregate is an internal seam of ProjectSession](../adr/0002-project-aggregate-internal-seam.md) requires the external `ProjectSession` interface and `ProjectSnapshot` contract to stay stable during aggregate work. Record/sealed conversions of public types therefore require a new explicit decision or compatible bridge, not incidental cleanup.
- The immutable `Project` and same-instance no-op convention remain. Modern collection factories may simplify construction but must preserve canonical case-insensitive order, referential cascades, and identity-based “unchanged.”
- Each Templates, Morphs, BoS, or export workflow must still capture exactly one immutable Project snapshot before background work. A centralized job service and virtual threads should reinforce this rule.
- JPMS remains deferred under the accepted packaging direction; do not create module descriptors as part of language cleanup.

## Decisions surfaced, not made

1. **Choose the future JSON boundary:** keep and isolate minimal-json temporarily, adopt a maintained parser, or write a narrow format codec; define compatibility fixtures and diagnostic requirements before choosing.
2. **Choose the modern table-filter interaction:** maintained ControlsFX, public-JavaFX implementation, or removal in favor of persistent workbench filters.
3. **Define the supported Java-facing model contract:** which public snapshots/outcomes must retain class/getter behavior, and which internal values may become records or sealed families.
4. **Define the centralized job service:** executor type (including whether virtual threads help), ownership/shutdown, cancellation semantics, conflict policy, and JavaFX-thread publication.
5. **Choose Commons IO update versus removal** and separately verify juniversalchardet 2.5.0 plus the undetected-charset fallback.
6. **Make full-source compilation a gate:** remove the package include workaround only when the UI slice compiles, then keep preview disabled and run `-Xlint`/UI tests in CI.

Fog that depends on those decisions: the exact record conversion set cannot be specified until the public API boundary is chosen; the ControlsFX route may disappear after the modern workbench prototype; and the executor/cancellation shape depends on which operations remain independently runnable in that UI.

## Reproduction commands

```powershell
mvn dependency:tree -Dscope=runtime -Dverbose
mvn test
mvn versions:display-dependency-updates -DallowSnapshots=false

mvn -q dependency:build-classpath "-Dmdep.outputFile=target/full-classpath.txt"
$cp = Get-Content -Raw target/full-classpath.txt
$sources = Get-ChildItem src -Filter *.java -Recurse | ForEach-Object FullName
javac --release 25 -Xlint:all -cp $cp -d target/full-classes $sources
```
