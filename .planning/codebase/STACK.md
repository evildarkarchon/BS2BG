# Technology Stack

**Analysis Date:** 2026-04-26

## Languages

**Primary:**
- C# 14 - Avalonia desktop app and test project in `src/BS2BG.App/BS2BG.App.csproj` and `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- C# 13 - portable core library in `src/BS2BG.Core/BS2BG.Core.csproj` targeting `netstandard2.1`.

**Secondary:**
- AXAML/XAML - Avalonia UI markup in `src/BS2BG.App/App.axaml`, `src/BS2BG.App/Views/MainWindow.axaml`, and `src/BS2BG.App/Themes/ThemeResources.axaml`.
- PowerShell - release packaging and fixture regeneration scripts in `tools/release/package-release.ps1` and `tests/tools/generate-expected.ps1`.
- Java 8 - legacy/reference implementation and fixture harness under `src/com/asdasfa/jbs2bg/`, configured by `.classpath` and invoked by `tests/tools/generate-expected.ps1`.
- JSON - slider profile data in `settings.json`, `settings_UUNP.json`, and test/project fixtures under `tests/fixtures/`.

## Runtime

**Environment:**
- .NET 10 - app and tests target `net10.0` in `src/BS2BG.App/BS2BG.App.csproj` and `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- .NET Standard 2.1 - core library target in `src/BS2BG.Core/BS2BG.Core.csproj` to keep domain/I/O code portable.
- Windows desktop is the production package target; `tools/release/package-release.ps1` validates `Runtime` as `win-x64` and publishes self-contained.
- Java 8 with bundled JavaFX is required only for reference fixture regeneration via `tests/tools/generate-expected.ps1` and `src/com/asdasfa/jbs2bg/testharness/FixtureDriver.java`.

**Package Manager:**
- NuGet / .NET SDK package restore - central package management enabled in `Directory.Packages.props` with `ManagePackageVersionsCentrally=true`.
- Lockfile: missing for NuGet (`packages.lock.json` not detected); package versions are pinned centrally in `Directory.Packages.props`.
- npm - project-local Kilo plugin dependency in `.kilo/package.json`; lockfile present at `.kilo/package-lock.json`.

## Frameworks

**Core:**
- Avalonia 12.0.1 - desktop UI framework referenced by `src/BS2BG.App/BS2BG.App.csproj`; bootstrapped in `src/BS2BG.App/Program.cs` with `UsePlatformDetect()`, `WithInterFont()`, `FluentTheme`, and trace logging.
- ReactiveUI.Avalonia 12.0.1 - MVVM/reactive command integration referenced by `src/BS2BG.App/BS2BG.App.csproj` and registered in `src/BS2BG.App/Program.cs` through `UseReactiveUIWithMicrosoftDependencyResolver(...)`.
- Microsoft.Extensions.DependencyInjection 10.0.7 - DI container for app services, view models, core services, and views in `src/BS2BG.App/AppBootstrapper.cs`.
- System.Text.Json 10.0.7 - JSON parsing/serialization for slider profiles, project files, user preferences, and BoS JSON-related output in `src/BS2BG.Core/Generation/SliderProfileJsonService.cs`, `src/BS2BG.Core/Serialization/ProjectFileService.cs`, and `src/BS2BG.App/Services/UserPreferencesService.cs`.
- System.Xml.Linq (`XDocument`) - BodySlide XML import in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`.

**Testing:**
- xUnit v3 3.2.2 - test framework for `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- FluentAssertions 8.9.0 - assertion library referenced by `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- Avalonia.Headless.XUnit 12.0.1 - headless UI tests referenced by `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- Microsoft.NET.Test.Sdk 18.4.0 and xunit.runner.visualstudio 3.1.5 - test discovery/execution packages in `Directory.Packages.props`.
- ReactiveUI test initialization - `tests/BS2BG.Tests/TestModuleInitializer.cs` initializes ReactiveUI and pins schedulers to `ImmediateScheduler.Instance`.

**Build/Dev:**
- MSBuild / .NET SDK - solution entry point is `BS2BG.sln`; shared build settings live in `Directory.Build.props`.
- Microsoft.CodeAnalysis.NetAnalyzers 10.0.203 - recommended analyzer set enabled in `Directory.Build.props` with `EnableNETAnalyzers=true`, `AnalysisLevel=latest`, and `AnalysisMode=Recommended`.
- ReactiveUI.SourceGenerators 2.6.1 - source generator package referenced as private assets in `src/BS2BG.App/BS2BG.App.csproj`.
- Avalonia compiled bindings - enabled globally for the app by `AvaloniaUseCompiledBindingsByDefault=true` in `src/BS2BG.App/BS2BG.App.csproj`; `src/BS2BG.App/Views/MainWindow.axaml` declares `x:DataType="vm:MainWindowViewModel"`.
- PowerShell packaging - `tools/release/package-release.ps1` runs `dotnet publish`, copies required profile/assets/docs, zips output, and writes SHA-256 sidecars.
- Java fixture harness - `tests/tools/generate-expected.ps1` compiles/runs `src/com/asdasfa/jbs2bg/testharness/FixtureDriver.java` using JARs in `tests/tools/lib/`.

## Key Dependencies

**Critical:**
- Avalonia / Avalonia.Desktop / Avalonia.Themes.Fluent / Avalonia.Fonts.Inter 12.0.1 - desktop UI, platform integration, Fluent theme, and bundled Inter font for `src/BS2BG.App/`.
- ReactiveUI.Avalonia 12.0.1 and ReactiveUI.Avalonia.Microsoft.Extensions.DependencyInjection 12.0.1 - MVVM command/property patterns and service resolver bridge used by `src/BS2BG.App/Program.cs` and `src/BS2BG.App/AppBootstrapper.cs`.
- System.Text.Json 10.0.7 - load-bearing project/profile serialization in `src/BS2BG.Core/Serialization/ProjectFileService.cs` and `src/BS2BG.Core/Generation/SliderProfileJsonService.cs`.
- System.Text.Encoding.CodePages 10.0.7 - NPC text fallback encoding support; `src/BS2BG.Core/Import/NpcTextParser.cs` registers `CodePagesEncodingProvider.Instance` and falls back to Windows/default code pages.
- Microsoft.Extensions.DependencyInjection 10.0.7 - application composition root and service lifetimes in `src/BS2BG.App/AppBootstrapper.cs`.

**Infrastructure:**
- Microsoft.CodeAnalysis.NetAnalyzers 10.0.203 - analyzer enforcement across all `.csproj` projects via `Directory.Build.props`.
- Microsoft.NET.Test.Sdk 18.4.0 - test runner infrastructure for `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- Avalonia.Headless.XUnit 12.0.1 - headless Avalonia test execution for UI/service/view model tests.
- FluentAssertions 8.9.0 - fluent assertions in test files under `tests/BS2BG.Tests/`.
- Java reference JARs - `tests/tools/lib/commons-io-2.6.jar`, `tests/tools/lib/juniversalchardet-2.1.0.jar`, and `tests/tools/lib/minimal-json-0.9.5.jar` support legacy fixture generation.
- @kilocode/plugin 7.2.22 - Kilo local tooling dependency in `.kilo/package.json`.

## Configuration

**Environment:**
- Build/test commands are `dotnet build BS2BG.sln`, `dotnet test`, and `dotnet run --project src/BS2BG.App/BS2BG.App.csproj` as documented in `AGENTS.md`.
- App profile JSON files are copied to app output from `settings.json` and `settings_UUNP.json` via content entries in `src/BS2BG.App/BS2BG.App.csproj`.
- `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs` loads required profile files from `AppContext.BaseDirectory` at startup.
- User preferences are stored locally at `%APPDATA%/jBS2BG/user-preferences.json` by `src/BS2BG.App/Services/UserPreferencesService.cs`.
- NPC image lookup uses an `images/` folder relative to the current working directory in `src/BS2BG.App/Services/NpcImageLookupService.cs`.
- Java fixture generation optionally uses `BS2BG_JDK8_HOME` in `tests/tools/generate-expected.ps1`; if unset, the script falls back to `java` and `javac` on `PATH`.
- `.env` files are ignored by `.gitignore`; no `.env` files were detected in the repository.

**Build:**
- Solution: `BS2BG.sln` includes `src/BS2BG.Core/BS2BG.Core.csproj`, `src/BS2BG.App/BS2BG.App.csproj`, and `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- Central versions: `Directory.Packages.props`.
- Shared analyzer/nullable/implicit using settings: `Directory.Build.props`.
- App UI/theme config: `src/BS2BG.App/App.axaml`, `src/BS2BG.App/Views/MainWindow.axaml`, `src/BS2BG.App/Themes/ThemeResources.axaml`.
- Release package script: `tools/release/package-release.ps1`.
- Legacy Java Eclipse config: `.classpath`.

## Platform Requirements

**Development:**
- .NET SDK capable of `net10.0`, C# 14, and C# 13 builds for `BS2BG.sln`.
- Windows/PowerShell is the primary development and packaging environment; `tools/release/package-release.ps1` validates `win-x64` and uses PowerShell cmdlets such as `Compress-Archive` and `Get-FileHash`.
- Java fixture regeneration requires JDK 8 with JavaFX plus `tests/tools/lib/commons-io-2.6.jar`, `tests/tools/lib/juniversalchardet-2.1.0.jar`, and `tests/tools/lib/minimal-json-0.9.5.jar`.
- Avalonia headless tests run through `Avalonia.Headless.XUnit` in `tests/BS2BG.Tests/BS2BG.Tests.csproj`.

**Production:**
- Deployment target is a portable Windows x64 zip containing a self-contained single-file `BS2BG.App.exe`; implemented by `tools/release/package-release.ps1` and documented in `docs/release/README.md`.
- Packaged build does not require an installed .NET runtime or Java runtime according to `docs/release/README.md`.
- Runtime data files `settings.json`, `settings_UUNP.json`, `assets/res/icon.png`, and release docs are copied into the release package by `tools/release/package-release.ps1`.

---

*Stack analysis: 2026-04-26*
