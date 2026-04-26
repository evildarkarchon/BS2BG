# External Integrations

**Analysis Date:** 2026-04-26

## APIs & External Services

**Desktop OS services:**
- Avalonia StorageProvider - user-selected file/folder access for opening `.jbs2bg` project files, saving project files, importing XML/text files, and choosing export folders.
  - SDK/Client: `Avalonia.Platform.Storage` from Avalonia 12.0.1.
  - Auth: Not applicable; implemented through local desktop permission/UI flows in `src/BS2BG.App/Services/WindowFileDialogService.cs`, `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs`, and `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs`.
- Avalonia clipboard - copies generated templates/morphs/BoS JSON text to the system clipboard.
  - SDK/Client: `Avalonia.Input.Platform` via `TopLevel.Clipboard`.
  - Auth: Not applicable; implementation is `src/BS2BG.App/Services/WindowClipboardService.cs`.
- Avalonia desktop/windowing - main app window, image preview window, dialogs, and platform detection.
  - SDK/Client: `Avalonia`, `Avalonia.Desktop`, and `Avalonia.Controls`.
  - Auth: Not applicable; bootstrapped in `src/BS2BG.App/Program.cs` and wired in `src/BS2BG.App/Views/MainWindow.axaml.cs`.

**Modding file formats:**
- BodySlide XML presets - imported from local `.xml` files.
  - SDK/Client: `System.Xml.Linq.XDocument` in `src/BS2BG.Core/Import/BodySlideXmlParser.cs`.
  - Auth: Not applicable; files are selected by the user through `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs`.
- BodyGen INI exports - generated as local `templates.ini` and `morphs.ini` files.
  - SDK/Client: local filesystem APIs and `BS2BG.Core.IO.AtomicFileWriter` in `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`.
  - Auth: Not applicable; export folder is user-selected through `src/BS2BG.App/Services/WindowFileDialogService.cs`.
- BoS JSON exports - generated as local per-preset `.json` files.
  - SDK/Client: local filesystem APIs and `TemplateGenerationService` through `src/BS2BG.Core/Export/BosJsonExportWriter.cs`.
  - Auth: Not applicable; export folder is user-selected through `src/BS2BG.App/Services/WindowFileDialogService.cs`.
- NPC text dumps - imported from local pipe-delimited `.txt` files.
  - SDK/Client: `System.Text.Encoding` and `System.Text.Encoding.CodePages` in `src/BS2BG.Core/Import/NpcTextParser.cs`.
  - Auth: Not applicable; files are selected through `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs`.

**Build and verification tooling:**
- NuGet package feeds - dependency restore for packages declared in `src/BS2BG.App/BS2BG.App.csproj`, `src/BS2BG.Core/BS2BG.Core.csproj`, `tests/BS2BG.Tests/BS2BG.Tests.csproj`, and versions in `Directory.Packages.props`.
  - SDK/Client: .NET SDK / NuGet.
  - Auth: Not detected; no `NuGet.config` or `.npmrc` file detected.
- Java reference fixture harness - regenerates golden fixture outputs from the legacy Java implementation.
  - SDK/Client: Java/Javac launched by `tests/tools/generate-expected.ps1`, with JARs in `tests/tools/lib/`.
  - Auth: Not applicable; optional `BS2BG_JDK8_HOME` environment variable selects a local JDK.

## Data Storage

**Databases:**
- Not detected.
  - Connection: Not applicable; no SQL/NoSQL client packages or connection strings detected in `Directory.Packages.props` or C# source under `src/`.
  - Client: Not applicable.

**File Storage:**
- Local filesystem only.
  - Project files: `.jbs2bg` JSON documents loaded/saved by `src/BS2BG.Core/Serialization/ProjectFileService.cs` using atomic writes through `src/BS2BG.Core/IO/AtomicFileWriter.cs`.
  - Runtime profile files: `settings.json` and `settings_UUNP.json` copied by `src/BS2BG.App/BS2BG.App.csproj` and loaded by `src/BS2BG.App/Services/TemplateProfileCatalogFactory.cs`.
  - User preferences: `%APPDATA%/jBS2BG/user-preferences.json` loaded/saved by `src/BS2BG.App/Services/UserPreferencesService.cs`.
  - NPC images: local `images/` directory relative to the working directory, with `.jpg`, `.jpeg`, `.png`, and `.bmp` lookup in `src/BS2BG.App/Services/NpcImageLookupService.cs`; image decoding in `src/BS2BG.App/Services/WindowImageViewService.cs` also accepts GIF and WebP signatures before constructing an Avalonia `Bitmap`.
  - Export output: `templates.ini`, `morphs.ini`, and BoS `.json` files are written to user-selected folders by `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs` and `src/BS2BG.Core/Export/BosJsonExportWriter.cs`.
  - Release artifacts: generated under `artifacts/release` by `tools/release/package-release.ps1`; `artifacts/` is ignored by `.gitignore`.

**Caching:**
- None detected.
- In-memory application state is kept in singleton services and models registered in `src/BS2BG.App/AppBootstrapper.cs`, including `ProjectModel`, `TemplateProfileCatalog`, and ViewModels.

## Authentication & Identity

**Auth Provider:**
- None.
  - Implementation: The app is an offline desktop utility. No login, OAuth, API keys, bearer tokens, cookies, or identity providers were detected in `src/`, `Directory.Packages.props`, or repository configuration.

## Monitoring & Observability

**Error Tracking:**
- None.
- No Sentry/Application Insights/OpenTelemetry/log aggregation dependency detected in `Directory.Packages.props` or `src/`.

**Logs:**
- Avalonia trace logging is enabled through `.LogToTrace()` in `src/BS2BG.App/Program.cs`.
- User-visible failures are generally returned as diagnostics/status messages rather than remote logs, for example `src/BS2BG.Core/Import/BodySlideXmlParser.cs`, `src/BS2BG.Core/Import/NpcTextParser.cs`, and ViewModels under `src/BS2BG.App/ViewModels/`.
- Release/package scripts write console output through PowerShell host output in `tools/release/package-release.ps1` and `tests/tools/generate-expected.ps1`.

## CI/CD & Deployment

**Hosting:**
- None for the app runtime; BS2BG runs locally as a desktop executable.
- Production distribution target is a portable Windows x64 zip containing a self-contained `BS2BG.App.exe`, described in `docs/release/README.md` and created by `tools/release/package-release.ps1`.

**CI Pipeline:**
- None detected.
- No `.github/workflows/`, Azure Pipelines, GitLab CI, or other CI configuration files were detected in the repository.

## Environment Configuration

**Required env vars:**
- None required for normal app runtime.
- `BS2BG_JDK8_HOME` is optional for fixture regeneration in `tests/tools/generate-expected.ps1`; when absent, the script falls back to `java` and `javac` on `PATH` and warns.

**Secrets location:**
- Not detected.
- `.env` files are ignored by `.gitignore`, and no `.env` files were found.
- No credential or secret configuration files were detected during the integration scan.

## Webhooks & Callbacks

**Incoming:**
- None.
- No HTTP server, webhook endpoint, controller, route, or listener implementation detected under `src/`.

**Outgoing:**
- None.
- No HTTP clients, REST SDKs, cloud SDKs, sockets, SMTP clients, or outgoing webhook calls detected under `src/`.

---

*Integration audit: 2026-04-26*
