# Technology Stack

**Analysis Date:** 2026-04-28

## Languages

**Primary:**
- C# 14 - Avalonia desktop app, automation CLI, and test project in `src/BS2BG.App/BS2BG.App.csproj`, `src/BS2BG.Cli/BS2BG.Cli.csproj`, and `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- C# 13 - Portable core library in `src/BS2BG.Core/BS2BG.Core.csproj` targeting `netstandard2.1`.

**Secondary:**
- Java 8 - Authoritative legacy reference implementation under `src/com/asdasfa/jbs2bg/`; `tests/tools/generate-expected.ps1` compiles/runs `src/com/asdasfa/jbs2bg/testharness/FixtureDriver.java` for golden-file regeneration.
- PowerShell - Release packaging and golden fixture tooling in `tools/release/package-release.ps1` and `tests/tools/generate-expected.ps1`.
- AXAML/XAML - Avalonia UI markup in `src/BS2BG.App/App.axaml`, `src/BS2BG.App/Views/MainWindow.axaml`, and `src/BS2BG.App/Themes/ThemeResources.axaml`.
- JSON - Bundled slider/profile data in `settings.json`, `settings_UUNP.json`, `settings_FO4_CBBE.json`, test fixture profiles in `tests/fixtures/inputs/profiles/`, and local/project serialization handled by `src/BS2BG.Core/Serialization/ProjectFileService.cs`.

## Runtime

**Environment:**
- .NET 10 - App, CLI, and tests target `net10.0` in `src/BS2BG.App/BS2BG.App.csproj`, `src/BS2BG.Cli/BS2BG.Cli.csproj`, and `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- .NET Standard 2.1 - Core library target in `src/BS2BG.Core/BS2BG.Core.csproj`.
- Java 8 with JavaFX 8 - Required only for reference golden-file regeneration; configured via `BS2BG_JDK8_HOME` in `tests/tools/generate-expected.ps1`.

**Package Manager:**
- NuGet with Central Package Management - `Directory.Packages.props` sets `<ManagePackageVersionsCentrally>true</ManagePackageVersionsCentrally>` and owns package versions.
- Lockfile: Not detected for NuGet packages.
- npm package manifests exist only for local AI/agent tooling in `.opencode/package.json`, `.kilo/package.json`, and `.kilocode/package.json`; they are not part of the BS2BG build.

## Frameworks

**Core:**
- Avalonia 12.0.1 - Cross-platform desktop UI; configured in `src/BS2BG.App/BS2BG.App.csproj`, bootstrapped in `src/BS2BG.App/Program.cs`, styled with `Avalonia.Themes.Fluent` in `src/BS2BG.App/App.axaml`.
- ReactiveUI.Avalonia 12.0.1 + ReactiveUI.SourceGenerators 2.6.1 - MVVM notifications and commands in App ViewModels such as `src/BS2BG.App/ViewModels/MainWindowViewModel.cs`, `src/BS2BG.App/ViewModels/TemplatesViewModel.cs`, and `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- Microsoft.Extensions.DependencyInjection 10.0.7 - Application composition in `src/BS2BG.App/AppBootstrapper.cs`.
- System.CommandLine 2.0.7 - Automation CLI command tree in `src/BS2BG.Cli/Program.cs`.

**Testing:**
- xUnit v3 3.2.2 - Test runner and attributes in `tests/BS2BG.Tests/BS2BG.Tests.csproj` and test files under `tests/BS2BG.Tests/`.
- Avalonia.Headless.XUnit 12.0.1 - Headless UI tests such as `tests/BS2BG.Tests/MainWindowHeadlessTests.cs` and `tests/BS2BG.Tests/AppShellTests.cs`.
- FluentAssertions 8.9.0 - Assertion style in tests such as `tests/BS2BG.Tests/AssignmentStrategyServiceTests.cs` and `tests/BS2BG.Tests/ReleaseTrustTests.cs`.
- Microsoft.NET.Test.Sdk 18.4.0 + xunit.runner.visualstudio 3.1.5 - Test execution packages in `tests/BS2BG.Tests/BS2BG.Tests.csproj`.

**Build/Dev:**
- MSBuild / `dotnet` CLI - Solution and project build driven by `BS2BG.sln`, `Directory.Build.props`, and project files under `src/` and `tests/`.
- Microsoft.CodeAnalysis.NetAnalyzers 10.0.203 - Enabled for every C# project via `Directory.Build.props`.
- PowerShell release pipeline - `tools/release/package-release.ps1` publishes self-contained `win-x64` single-file App and CLI binaries, creates normalized zip packages, SHA-256 manifests, and optional Authenticode signatures.
- Java golden fixture pipeline - `tests/tools/generate-expected.ps1` compiles and runs the Java `FixtureDriver` against fixture inputs.

## Key Dependencies

**Critical:**
- `Avalonia` / `Avalonia.Desktop` 12.0.1 - UI runtime; `src/BS2BG.App/Program.cs` uses `UsePlatformDetect()` and `StartWithClassicDesktopLifetime(args)`.
- `Avalonia.Themes.Fluent` 12.0.1 - App theme in `src/BS2BG.App/App.axaml`.
- `Avalonia.Fonts.Inter` 12.0.1 - Inter font registration in `src/BS2BG.App/Program.cs`.
- `ReactiveUI.Avalonia` 12.0.1 - Reactive MVVM integration through `UseReactiveUIWithMicrosoftDependencyResolver(...)` in `src/BS2BG.App/Program.cs`.
- `ReactiveUI.SourceGenerators` 2.6.1 - `[Reactive]` property generation in ViewModels such as `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- `System.Text.Json` 10.0.7 - Project files, profile JSON, BoS JSON, bundle manifests, and CLI output in `src/BS2BG.Core/Serialization/ProjectFileService.cs`, `src/BS2BG.Core/Generation/ProfileDefinitionService.cs`, `src/BS2BG.Core/Bundling/PortableProjectBundleContracts.cs`, and `src/BS2BG.Cli/Program.cs`.
- `System.Text.Encoding.CodePages` 10.0.7 - NPC import fallback encodings; `src/BS2BG.Core/Import/NpcTextParser.cs` registers `CodePagesEncodingProvider.Instance`.
- `System.CommandLine` 2.0.7 - `generate` and `bundle` CLI commands in `src/BS2BG.Cli/Program.cs`.

**Infrastructure:**
- `DynamicData` 9.4.31 - Reactive collection helpers in `src/BS2BG.App/ViewModels/MorphsViewModel.cs`.
- `Microsoft.Extensions.DependencyInjection` 10.0.7 - Service lifetimes and singleton ViewModel/service graph in `src/BS2BG.App/AppBootstrapper.cs`.
- `System.IO.Compression` from the BCL - Portable project bundles in `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` and release zip packaging in `tools/release/package-release.ps1`.
- `System.Security.Cryptography` from the BCL - SHA-256 profile filename hashes in `src/BS2BG.App/Services/UserProfileStore.cs` and release checksums in `tools/release/package-release.ps1`.
- Java fixture JARs - `tests/tools/lib/commons-io-2.6.jar`, `tests/tools/lib/juniversalchardet-2.1.0.jar`, and `tests/tools/lib/minimal-json-0.9.5.jar` are used only by the Java reference fixture generator.
- Agent tooling packages - `.opencode/package.json` references `@kilocode/plugin` 7.2.22 and `@opencode-ai/plugin` 1.14.19; `.kilo/package.json` and `.kilocode/package.json` reference `@kilocode/plugin` 7.2.22.

## Configuration

**Environment:**
- No `.env` files detected in the repo root.
- Core profile assets are install-relative JSON files copied to App and CLI output: `settings.json`, `settings_UUNP.json`, and `settings_FO4_CBBE.json` via `src/BS2BG.App/BS2BG.App.csproj` and `src/BS2BG.Cli/BS2BG.Cli.csproj`.
- Runtime profile lookup starts from `AppContext.BaseDirectory` in `src/BS2BG.Core/Generation/TemplateProfileCatalogFactory.cs`.
- User preferences are stored under `%APPDATA%\jBS2BG\user-preferences.json` by `src/BS2BG.App/Services/UserPreferencesService.cs`.
- Local custom profiles are stored under `%APPDATA%\jBS2BG\profiles` by `src/BS2BG.App/Services/UserProfileStore.cs`.
- Golden fixture regeneration optionally uses `BS2BG_JDK8_HOME` in `tests/tools/generate-expected.ps1`.

**Build:**
- Solution: `BS2BG.sln` contains `src/BS2BG.Core/BS2BG.Core.csproj`, `src/BS2BG.App/BS2BG.App.csproj`, `src/BS2BG.Cli/BS2BG.Cli.csproj`, and `tests/BS2BG.Tests/BS2BG.Tests.csproj`.
- Shared build properties: `Directory.Build.props` enables nullable reference types, implicit usings, .NET analyzers, latest analysis level, and recommended analysis mode.
- Central package versions: `Directory.Packages.props` pins all NuGet package versions.
- App project config: `src/BS2BG.App/BS2BG.App.csproj` sets `OutputType=WinExe`, `TargetFramework=net10.0`, `LangVersion=14`, and `AvaloniaUseCompiledBindingsByDefault=true`.
- CLI project config: `src/BS2BG.Cli/BS2BG.Cli.csproj` sets `OutputType=Exe`, `TargetFramework=net10.0`, `LangVersion=14`, `PublishSingleFile=true`, `IncludeNativeLibrariesForSelfExtract=true`, and `EnableCompressionInSingleFile=true`.
- Core project config: `src/BS2BG.Core/BS2BG.Core.csproj` sets `TargetFramework=netstandard2.1` and `LangVersion=13`.
- Test project config: `tests/BS2BG.Tests/BS2BG.Tests.csproj` sets `IsTestProject=true`, references App/CLI/Core, and copies golden expected files from `tests/fixtures/expected/**`.

## Platform Requirements

**Development:**
- Windows is the primary development platform per `AGENTS.md`; use PowerShell commands for local workflows.
- .NET 10 SDK is required for `dotnet build BS2BG.sln`, `dotnet test`, and `dotnet run --project src/BS2BG.App/BS2BG.App.csproj`.
- Visual Studio 17 solution metadata exists in `BS2BG.sln`; `dotnet` CLI remains sufficient for build/test.
- JDK 8 with JavaFX 8 is required only when regenerating Java golden outputs through `tests/tools/generate-expected.ps1`.
- Optional Windows SignTool is used by `tools/release/package-release.ps1` when signing inputs are configured.

**Production:**
- Desktop App target: self-contained Windows `win-x64` single-file executable from `src/BS2BG.App/BS2BG.App.csproj`, produced by `tools/release/package-release.ps1`.
- Automation CLI target: self-contained Windows `win-x64` single-file executable from `src/BS2BG.Cli/BS2BG.Cli.csproj`, produced by `tools/release/package-release.ps1`.
- Release artifact target: zip package plus `.sha256` checksum in `artifacts/release/` from `tools/release/package-release.ps1`.

---

*Stack analysis: 2026-04-28*
