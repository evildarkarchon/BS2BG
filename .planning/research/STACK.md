# Technology Stack

**Project:** BS2BG (Bodyslide to Bodygen)
**Research dimension:** Stack guidance for future maintenance/extension phases
**Researched:** 2026-04-26
**Overall confidence:** HIGH for current repo posture; MEDIUM for third-party Avalonia ecosystem caveats

## Executive Recommendation

Keep the existing stack. BS2BG is no longer a greenfield port: the load-bearing work is preserving Java parity, byte-identical outputs, and the established Avalonia/ReactiveUI architecture. Future phases should optimize for low dependency churn, deterministic builds, and targeted additions only when they solve a real workflow gap.

The recommended posture is:

1. **Stay on .NET 10 / C# 14 for App and Tests** because .NET 10 is the current LTS line supported until November 2028, and the app ships self-contained to Windows users.
2. **Keep `BS2BG.Core` on `netstandard2.1` / C# 13** unless a future CLI or library consumer explicitly no longer needs portability. Core's value is being UI-free, deterministic, and easy to test against Java fixture output.
3. **Keep Avalonia 12.0.1 + ReactiveUI.Avalonia 12.0.1** for UI work. Do not migrate to WPF, WinUI, MAUI, CommunityToolkit.Mvvm, or a different MVVM stack during normal feature work; that would create churn without improving parity risk.
4. **Keep JSON/XML/file output dependencies boring**: `System.Text.Json`, `XDocument`, and explicit string writers are correct for this app. Avoid Newtonsoft.Json, generic INI libraries, or generic JSON DOM writers in byte-sensitive export paths.
5. **Tighten reproducibility** by adding NuGet lock files and locked-mode restore for release/CI. Central Package Management pins direct versions, but lock files protect the full transitive graph.

## Current Stack to Keep

### Core Runtime and Language

| Technology | Current Version/Posture | Recommendation | Confidence | Why |
|------------|-------------------------|----------------|------------|-----|
| .NET SDK/runtime | App/Tests target `net10.0` | **Keep** through the .NET 10 LTS lifecycle; install servicing SDKs regularly | HIGH | Microsoft documents .NET 10 as LTS, supported until November 2028. Self-contained publish fits Windows modder distribution. |
| C# | App/Tests `LangVersion` 14; Core 13 | **Keep split** | HIGH | App can use current language features; Core remains portable and avoids runtime/API surprises from newer TFM-only features. |
| `netstandard2.1` Core | `src/BS2BG.Core` | **Keep** | HIGH | Core contains parity-sensitive parsing, formatting, generation, export, and serialization. Keeping Avalonia/platform dependencies out preserves testability and future CLI reuse. |
| Windows-first portable distribution | Self-contained win-x64 zip | **Keep as primary release shape** | HIGH | Matches modder expectations: no installer, no Java, no global .NET runtime requirement. |

### UI Framework

| Technology | Current Version | Recommendation | Confidence | Why |
|------------|-----------------|----------------|------------|-----|
| Avalonia | 12.0.1 | **Keep and upgrade only deliberately within the 12.x line after test pass** | HIGH | Current UI is already implemented on Avalonia 12. Replatforming would add regression risk and provide little value. |
| Avalonia.Desktop | 12.0.1 | **Keep** | HIGH | Correct host package for desktop utility. |
| Avalonia.Themes.Fluent | 12.0.1 | **Keep** | HIGH | Maintains modern UI with minimal custom theme burden. |
| Avalonia.Fonts.Inter | 12.0.1 | **Keep** | HIGH | Existing bundled font is small and stable. Add JetBrains Mono only if future text-output UX work requires it. |
| Avalonia compiled bindings | Enabled with `AvaloniaUseCompiledBindingsByDefault=true` | **Keep mandatory** | HIGH | Official docs confirm compiled bindings require `x:DataType`, catch binding errors at build time, improve performance, and help AOT/trimming scenarios. |
| Plain `Window` | `MainWindow : Window` | **Keep by default** | HIGH | Project convention explicitly avoids `ReactiveWindow`; no need for reactive view lifecycle unless a specific new window needs it. |

### MVVM and Reactive Patterns

| Technology | Current Version | Recommendation | Confidence | Why |
|------------|-----------------|----------------|------------|-----|
| ReactiveUI.Avalonia | 12.0.1 | **Keep** | HIGH | Existing ViewModels and tests are aligned to ReactiveUI conventions. Migrating MVVM frameworks would be broad churn. |
| ReactiveUI.Avalonia.Microsoft.Extensions.DependencyInjection | 12.0.1 | **Keep** | HIGH | Existing composition root bridges ReactiveUI and Microsoft DI. |
| ReactiveUI.SourceGenerators | 2.6.1 | **Keep; prefer `[Reactive]` properties** | HIGH | Project specifically moved away from Fody and manual setter patterns. Source generators avoid Fody incompatibility with ReactiveUI 23.x. |
| ReactiveCommand | ReactiveUI command model | **Use for all new VM commands** | HIGH | Supports observable `canExecute`, async cancellation, `IsExecuting`, and derived busy state. |

**Project-specific override:** Some generic Avalonia guidance now recommends CommunityToolkit.Mvvm and warns against ReactiveUI. Do **not** apply that to BS2BG. This repository has an explicit, tested ReactiveUI convention in `AGENTS.md` and `openspec/specs/reactive-mvvm-conventions/spec.md`; consistency is more valuable than a greenfield preference.

### Data and File Formats

| Technology | Current Version/Posture | Recommendation | Confidence | Why |
|------------|-------------------------|----------------|------------|-----|
| `System.Text.Json` | 10.0.7 package, used in Core/App | **Keep** | HIGH | Sufficient for project/profile/preferences serialization. Avoids dependency bloat. Preserve custom BoS/minimal-json-like number formatting outside normal serializer paths. |
| `System.Xml.Linq.XDocument` | BCL | **Keep** | HIGH | BodySlide XMLs are small; DOM parsing is simple and deterministic. |
| Explicit INI string writers | Custom code | **Keep** | HIGH | Generic INI libraries are likely to normalize ordering, escaping, or line endings and break golden tests. |
| `System.Text.Encoding.CodePages` | 10.0.7 | **Keep** | HIGH | Needed for NPC text fallback encoding support on Windows-style dumps. |
| Java reference harness | Java 8 + JavaFX + fixture JARs | **Keep as test infrastructure only** | HIGH | Required to regenerate expected fixture outputs; never ship Java with the app. |

### Testing Stack

| Technology | Current Version | Recommendation | Confidence | Why |
|------------|-----------------|----------------|------------|-----|
| xUnit v3 | 3.2.2 | **Keep** | HIGH | Required by current Avalonia.Headless.XUnit stack and already adopted. |
| Avalonia.Headless.XUnit | 12.0.1 | **Keep for UI/control tests** | HIGH | Official Avalonia docs position headless tests as fast, in-process tests for controls, binding, layout, and input. |
| FluentAssertions | 8.9.0 | **Keep** | HIGH | Existing migration is archived; use fluent assertions for new tests. |
| Golden-file tests | Existing fixture corpus | **Treat as release gate** | HIGH | Byte-identical output is the trust contract. Expand fixtures for new profile/export/import behavior. |
| Appium | Not installed | **Defer unless accessibility/end-to-end platform tests become a phase goal** | MEDIUM | Avalonia docs recommend Appium for real-window E2E/accessibility tests, but it adds setup friction and is not needed for most parity work. |

### Build, Packaging, and Dependency Management

| Technology/Practice | Current State | Recommendation | Confidence | Why |
|---------------------|---------------|----------------|------------|-----|
| Central Package Management | Enabled in `Directory.Packages.props` | **Keep** | HIGH | Versions are explicit and centralized. |
| NuGet lock files | Not present | **Add for app/test restore reproducibility** | HIGH | NuGet docs recommend checking lock files into source for executable/application dependency chains and using locked mode in CI/release. |
| Analyzer package | Microsoft.CodeAnalysis.NetAnalyzers 10.0.203 | **Keep** | HIGH | Existing `AnalysisLevel=latest`, `AnalysisMode=Recommended` is appropriate. Consider stricter rules only phase-by-phase. |
| PowerShell release scripts | Existing `tools/release/package-release.ps1` | **Keep Windows-first** | HIGH | Project/environment are Windows-first; PowerShell avoids shell portability traps. |
| Single-file self-contained publish | Existing release goal | **Keep** | HIGH | Best distribution model for non-developer modders. |

## Dependency Posture for Future Phases

### Prefer These Additions When Needed

| Need | Preferred Tool/Library | Confidence | Guidance |
|------|------------------------|------------|----------|
| New file/folder pickers | Avalonia `StorageProvider` APIs | HIGH | Use `TopLevel`/`Window.StorageProvider` with `OpenFilePickerAsync`, `SaveFilePickerAsync`, and `OpenFolderPickerAsync`. Do not resurrect old `OpenFileDialog` patterns. |
| New clipboard flows | Existing app clipboard service over Avalonia clipboard APIs | MEDIUM | Keep clipboard behind an app service so Avalonia API churn stays localized. Verify exact Avalonia 12 clipboard APIs before touching. |
| New custom controls | Avalonia controls with `StyledProperty`/`DirectProperty` as appropriate | HIGH | Use Avalonia idioms, not WPF `DependencyProperty`, triggers, or `Visibility` enum patterns. |
| Filterable NPC/grid work | Existing/custom Avalonia behavior or custom control | MEDIUM | TableFilter has no Avalonia equivalent in repo guidance. Build narrowly: search/filter predicates over existing models before adding heavy grid libraries. |
| Large flat tabular data | Avalonia `DataGrid` first; consider `TreeDataGrid` only after measurement | MEDIUM | Do not replace the grid stack preemptively. Profile with 5k+ NPC datasets before adding a new grid dependency. |
| Logging diagnostics | Existing Avalonia `.LogToTrace(...)`; optional app-level logging only if a phase needs persisted diagnostics | MEDIUM | Avoid heavy logging frameworks unless there is a user-facing troubleshooting requirement. |
| Release checks | `dotnet build`, `dotnet test`, publish script, hash generation | HIGH | Keep release process simple and reproducible. |

### Avoid These Migrations or Libraries

| Avoid | Confidence | Why |
|-------|------------|-----|
| Migrating from Avalonia to WPF/WinUI/MAUI | HIGH | No product value, high regression risk, and contrary to implemented UI. |
| Migrating MVVM from ReactiveUI to CommunityToolkit.Mvvm | HIGH | Existing ReactiveUI source-generator patterns, scheduler setup, and command semantics are now canonical. |
| ReactiveUI.Fody | HIGH | Project guidance says Fody is incompatible with the ReactiveUI 23.x line used through ReactiveUI.Avalonia 12.0.1. Use `ReactiveUI.SourceGenerators`. |
| Reintroducing custom `RelayCommand` / `AsyncRelayCommand` | HIGH | Retired by project convention. Use `ReactiveCommand.Create*` / `CreateFromTask`. |
| Calling `Dispatcher.UIThread.InvokeAsync` directly from ViewModels | HIGH | Violates project threading convention. Use `ReactiveCommand`, schedulers, services, and observable derived state. |
| Generic JSON/INI writers for export parity paths | HIGH | They can reorder, normalize line endings, alter trailing newlines, or change float formatting. |
| Newtonsoft.Json | HIGH | Unneeded; adds dependency surface and risks serialization differences. |
| Dynamic/reflection bindings by default | HIGH | Compiled bindings are mandatory. Use `ReflectionBinding` only for explicitly dynamic cases and document why. |
| `Avalonia.Diagnostics` package | HIGH | Avalonia docs mark legacy diagnostics package as deprecated; current diagnostics posture requires separate consideration. Do not casually add it. |
| NativeAOT as a near-term goal | MEDIUM | Compiled bindings help, but current Avalonia/ReactiveUI/reflection/packaging posture should not be disrupted unless startup/package size becomes a measured problem. |
| Built-in ESP/ESM/NIF parsing libraries | MEDIUM | Out of scope for this conversion utility unless a future OpenSpec explicitly adds game-file integration. |

## Version-Sensitive Caveats

### .NET 10

- .NET 10 is LTS and currently supported until November 2028.
- Keep release machines on current .NET 10 servicing SDKs. Servicing updates are the supported state.
- Do not downgrade App/Tests to .NET 8 unless a dependency blocks .NET 10; current repo is already .NET 10 and benefits from the LTS runway.
- Do not move Core to `net10.0` just to match the app. Core's `netstandard2.1` target is an architectural boundary, not technical debt.

### Avalonia 12

- Compiled bindings are project-wide. Every `Window`, `UserControl`, and `DataTemplate` needs `x:DataType`.
- File picker work should use `StorageProvider`; official docs describe `OpenFilePickerAsync`, `SaveFilePickerAsync`, and `OpenFolderPickerAsync` as the central file/folder APIs.
- Avalonia is not WPF: do not use WPF `DependencyProperty`, `Style.Triggers`, `DataTrigger`, `Visibility`, `pack://` URIs, or resource-based WPF `DataTemplate` assumptions.
- Old tutorials and StackOverflow answers are likely to be v11/v0.10-era. Verify against current Avalonia 12 docs before copying UI/file/dialog/devtools code.

### ReactiveUI 23.x via ReactiveUI.Avalonia 12.0.1

- Initialize ReactiveUI explicitly in tests; the existing module initializer already does this.
- For async commands, prefer `ReactiveCommand.CreateFromTask((CancellationToken) => Task)` to preserve cancellation semantics.
- `canExecute` should be observable-based, not a synchronous predicate callback.
- Derived busy/error/count state should flow through `WhenAnyValue(...).ToProperty(...)` or `Observable.CombineLatest(...).ToProperty(...)`.

### NuGet / Restore

- Central direct versions are already pinned, but transitive resolution can still drift.
- Add `RestorePackagesWithLockFile=true` and commit generated lock files for the executable/test dependency graph, then use `dotnet restore --locked-mode` in CI/release.
- Be aware that .NET 10 package pruning can change lock file diffs; regenerate lock files intentionally when package references change.

## Recommended Package Policy

### Short Term

1. **No broad dependency upgrades during feature phases.** Upgrade packages in their own maintenance changes so regressions are attributable.
2. **Pin all new direct packages in `Directory.Packages.props`.** Do not use floating versions.
3. **Prefer BCL APIs over NuGet packages** for parsing, formatting, path handling, JSON, XML, hashing, and archive work unless a clear gap exists.
4. **Add lock files** before the next release-polish/release-maintenance phase.
5. **Run golden tests after every package upgrade** touching Avalonia, ReactiveUI, System.Text.Json, or .NET SDK feature bands.

### Medium Term

1. **Stay within Avalonia 12.x unless a scoped migration proposal exists.** Avalonia major upgrades should be treated as roadmap items with UI smoke tests and manual QA.
2. **Review .NET 10 SDK feature bands quarterly**, not per feature. Prefer stable release tooling over newest SDK churn.
3. **Document any new UI dependency with an exit strategy.** For a small utility, custom simple code is often safer than a large control library.
4. **Consider Appium only after accessibility automation becomes valuable enough to justify setup.** Headless tests remain the default.

## Installation / Restore Guidance

```powershell
# Normal developer restore/build/test
 dotnet restore BS2BG.sln
 dotnet build BS2BG.sln
 dotnet test

# Recommended future CI/release restore after lock files are committed
 dotnet restore BS2BG.sln --locked-mode
 dotnet test

# Release packaging remains PowerShell/Windows-first
 .\tools\release\package-release.ps1
```

## Roadmap Implications

1. **Maintenance phases should start with dependency discipline**, not framework migration. Add lock files, confirm package centralization, and keep package upgrades isolated.
2. **UI enhancement phases should budget Avalonia 12 API verification** for file dialogs, clipboard, data templates, compiled bindings, and any custom control work.
3. **Profile/export/import phases should avoid new serializers or formatters** unless they are wrapped in parity tests and proven byte-identical where required.
4. **Testing phases should expand golden and ViewModel coverage first**, then add headless UI tests for binding/control behavior. Appium is optional and phase-specific.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| .NET 10 / C# 14 posture | HIGH | Repo config and Microsoft support docs agree. |
| `netstandard2.1` Core posture | HIGH | Strongly supported by repo architecture and portability constraints. |
| Avalonia 12 posture | HIGH | Existing implementation, official compiled-binding/file-picker docs, and project guidance agree. |
| ReactiveUI posture | HIGH | Project-specific guidance is explicit and overrides generic Avalonia greenfield guidance. |
| Testing posture | HIGH | Current packages and Avalonia testing docs align. |
| NuGet lock-file recommendation | HIGH | Official NuGet docs recommend checked-in lock files for executable/app dependency chains. |
| Future custom grid/control library choices | MEDIUM | Requires phase-specific measurement and UX requirements. |
| DevTools/diagnostics package choice | MEDIUM | Avalonia docs identify deprecated diagnostics package and a replacement path, but project has not committed to a diagnostics dependency. |

## Sources

- Repository: `.planning/PROJECT.md`, `PRD.md`, `AGENTS.md`, `Directory.Packages.props`, `Directory.Build.props`, project `.csproj` files, `.planning/codebase/STACK.md`.
- Microsoft Learn: `.NET releases and support` — .NET 10 LTS supported until November 2028; servicing and support guidance. https://learn.microsoft.com/dotnet/core/releases-and-support
- Microsoft Learn / NuGet: `PackageReference in project files` — lock files, checked-in application lock files, and `--locked-mode`. https://learn.microsoft.com/nuget/consume-packages/package-references-in-project-files#locking-dependencies
- Avalonia Docs: `XAML compilation` / compiled bindings — project-wide compiled bindings and `x:DataType`. https://docs.avaloniaui.net/docs/xaml/compilation
- Avalonia Docs: `x: directives` — `x:DataType` and `x:CompileBindings`. https://docs.avaloniaui.net/docs/xaml/directives
- Avalonia Docs: `Storage Provider` — `OpenFilePickerAsync`, `SaveFilePickerAsync`, `OpenFolderPickerAsync`. https://docs.avaloniaui.net/docs/services/storage/storage-provider
- Avalonia Docs: `Testing` — unit, headless, visual regression, and Appium testing tradeoffs. https://docs.avaloniaui.net/docs/testing/
