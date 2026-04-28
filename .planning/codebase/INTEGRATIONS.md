# External Integrations

**Analysis Date:** 2026-04-28

## APIs & External Services

**Network APIs:**
- Not detected. Source search found no `HttpClient`, socket, cloud SDK, OAuth, webhook, or database client usage under `src/`.

**Desktop OS APIs:**
- Avalonia storage provider - File/folder open-save integration for projects, BodySlide XML, NPC text files, BodyGen export folders, BoS export folders, profile JSON, and bundle zips.
  - SDK/Client: `Avalonia.Platform.Storage` via `src/BS2BG.App/Services/WindowFileDialogService.cs`, `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs`, `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs`, and `src/BS2BG.App/Services/ProfileManagementDialogService.cs`.
  - Auth: Not applicable.
- Avalonia clipboard provider - Copy/export text from the UI.
  - SDK/Client: `Avalonia.Input.Platform` via `src/BS2BG.App/Services/WindowClipboardService.cs`.
  - Auth: Not applicable.
- Avalonia desktop lifetime/platform detection - Native desktop host startup.
  - SDK/Client: `Avalonia.AppBuilder` via `src/BS2BG.App/Program.cs`.
  - Auth: Not applicable.
- Windows/AppData filesystem - User preference and local custom-profile persistence.
  - SDK/Client: BCL `Environment.SpecialFolder.ApplicationData`, `File`, `Directory`, `Path` in `src/BS2BG.App/Services/UserPreferencesService.cs` and `src/BS2BG.App/Services/UserProfileStore.cs`.
  - Auth: Current OS user profile permissions.

**Build and Release Tooling:**
- .NET SDK / `dotnet` CLI - Build, test, publish, and self-contained release output.
  - SDK/Client: `dotnet publish` invoked by `tools/release/package-release.ps1`; projects defined by `BS2BG.sln` and `src/**/*.csproj`.
  - Auth: Not applicable.
- Windows SignTool - Optional Authenticode signing and verification for release executables.
  - SDK/Client: `signtool.exe` resolved in `tools/release/package-release.ps1`.
  - Auth: Certificate selected by script parameters `CertificateSubject`, `CertificatePath`, and optional password environment variable name `CertificatePasswordEnvVar`; no certificate files are committed or read by mapper.
- Java toolchain - Reference golden-file generation.
  - SDK/Client: `javac.exe` and `java.exe` resolved from `BS2BG_JDK8_HOME` or `PATH` in `tests/tools/generate-expected.ps1`.
  - Auth: Not applicable.

## Data Storage

**Databases:**
- Not detected. The application uses local files only; no SQL, document database, ORM, or remote persistence is configured in `src/` or project files.

**File Storage:**
- Local project files - `.jbs2bg` JSON projects loaded/saved by `src/BS2BG.Core/Serialization/ProjectFileService.cs` and selected through `src/BS2BG.App/Services/WindowFileDialogService.cs`.
- BodySlide input files - `.xml` files parsed by `src/BS2BG.Core/Import/BodySlideXmlParser.cs` and selected through `src/BS2BG.App/Services/WindowBodySlideXmlFilePicker.cs`.
- NPC input files - `.txt` files parsed by `src/BS2BG.Core/Import/NpcTextParser.cs` and selected through `src/BS2BG.App/Services/WindowNpcTextFilePicker.cs`.
- BodyGen output files - `templates.ini` and `morphs.ini` written by `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`.
- BoS output files - one JSON file per preset written by `src/BS2BG.Core/Export/BosJsonExportWriter.cs` with paths planned by `src/BS2BG.Core/Export/BosJsonExportPlanner.cs`.
- Portable bundles - `.zip` bundles created by `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` and `src/BS2BG.Cli/Program.cs`.
- User preferences - `%APPDATA%\jBS2BG\user-preferences.json` handled by `src/BS2BG.App/Services/UserPreferencesService.cs`.
- Local custom profiles - `%APPDATA%\jBS2BG\profiles\*.json` handled by `src/BS2BG.App/Services/UserProfileStore.cs`.
- NPC images - optional local `images/` directory scanned for `.jpg`, `.jpeg`, `.png`, and `.bmp` files by `src/BS2BG.App/Services/NpcImageLookupService.cs`.

**Caching:**
- Not detected. There is no external cache, in-memory distributed cache, Redis, or persistent cache layer. Runtime state is composed from files and in-memory models such as `src/BS2BG.Core/Models/ProjectModel.cs`.

## Authentication & Identity

**Auth Provider:**
- None. The app and CLI perform local file operations under the current OS user; no login, token, OAuth, or identity provider integration is present.
  - Implementation: Local desktop/CLI process with OS filesystem permissions only.

## Monitoring & Observability

**Error Tracking:**
- None. No telemetry, crash reporting, or external error-tracking SDK is detected.

**Logs:**
- Avalonia trace logging is enabled through `.LogToTrace()` in `src/BS2BG.App/Program.cs`.
- CLI writes expected user-facing failures to standard error in `src/BS2BG.Cli/Program.cs`.
- Diagnostics are represented as domain data and formatted locally by `src/BS2BG.Core/Diagnostics/DiagnosticReportTextFormatter.cs`, `src/BS2BG.App/Services/DiagnosticsReportFormatter.cs`, and `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs`.

## CI/CD & Deployment

**Hosting:**
- Local desktop distribution only. Release packaging emits self-contained Windows `win-x64` App and CLI binaries in a zip under `artifacts/release/` via `tools/release/package-release.ps1`.

**CI Pipeline:**
- Not detected. No `.github/workflows/` files or other CI configuration were found.

## Environment Configuration

**Required env vars:**
- None for normal App/CLI runtime.
- `BS2BG_JDK8_HOME` is optional but recommended for fixture regeneration in `tests/tools/generate-expected.ps1`; if unset, the script uses `java` and `javac` from `PATH` and warns.
- `CertificatePasswordEnvVar` is a release script parameter naming an environment variable that contains a certificate password for optional signing in `tools/release/package-release.ps1`; no value is stored in the repo.

**Secrets location:**
- No `.env` files detected.
- No committed credentials detected in mapped integration files.
- Optional signing secrets are expected outside the repo and accessed indirectly by `tools/release/package-release.ps1` through a caller-provided environment variable name.

## Webhooks & Callbacks

**Incoming:**
- None. No HTTP server, route handlers, or webhook endpoints are present.

**Outgoing:**
- None. No outbound webhooks, HTTP API calls, or messaging integrations are present.

## File Formats & Protocols

**Input formats:**
- BodySlide XML - Parsed from `<SliderPresets>` / `<Preset>` / `<SetSlider>` documents by `src/BS2BG.Core/Import/BodySlideXmlParser.cs` using `System.Xml.Linq.XDocument`.
- NPC pipe-delimited text - `Mod|Name|EditorID|Race|FormID` rows parsed by `src/BS2BG.Core/Import/NpcTextParser.cs` with BOM detection, strict UTF-8, Windows code page fallback, and CP-1252 fallback on non-Windows.
- Slider profile JSON - Bundled and custom profile definitions loaded from `settings.json`, `settings_UUNP.json`, `settings_FO4_CBBE.json`, and `%APPDATA%\jBS2BG\profiles\*.json` by `src/BS2BG.Core/Generation/SliderProfileJsonService.cs`, `src/BS2BG.Core/Generation/ProfileDefinitionService.cs`, and `src/BS2BG.App/Services/UserProfileStore.cs`.
- Project JSON - `.jbs2bg` files loaded by `src/BS2BG.Core/Serialization/ProjectFileService.cs` with comment/trailing-comma tolerance.

**Output formats:**
- BodyGen INI - `templates.ini` and `morphs.ini` written by `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs` with CRLF normalization.
- BoS JSON - Preset JSON files generated by `src/BS2BG.Core/Export/BosJsonExportWriter.cs` and formatting logic in `src/BS2BG.Core/Generation/TemplateGenerationService.cs`.
- Portable bundle ZIP - Bundle manifests and generated outputs packed by `src/BS2BG.Core/Bundling/PortableProjectBundleService.cs` using `System.IO.Compression.ZipArchive`.
- Release ZIP + checksums - Release packages and SHA-256 files produced by `tools/release/package-release.ps1`.

## OS/Tooling Integrations

**Windows filesystem and profile folders:**
- Use `Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData)` in `src/BS2BG.App/Services/UserPreferencesService.cs` and `src/BS2BG.App/Services/UserProfileStore.cs`.
- Use platform-aware path comparison for NPC images in `src/BS2BG.App/Services/NpcImageLookupService.cs`.

**Atomic file writes:**
- Core exports and project/profile saves use atomic writer utilities in `src/BS2BG.Core/IO/AtomicFileWriter.cs`, called by `src/BS2BG.Core/Serialization/ProjectFileService.cs`, `src/BS2BG.Core/Export/BodyGenIniExportWriter.cs`, `src/BS2BG.Core/Export/BosJsonExportWriter.cs`, and `src/BS2BG.App/Services/UserProfileStore.cs`.

**Java reference parity tooling:**
- Golden-file expected outputs are produced from Java reference code under `src/com/asdasfa/jbs2bg/` using `tests/tools/generate-expected.ps1` and dependency JARs in `tests/tools/lib/`.
- Project skill `java-ref` documents authoritative Java reference mapping in `.claude/skills/java-ref/SKILL.md`.
- Project skill `parity-check` documents golden-file validation workflow in `.claude/skills/parity-check/SKILL.md`.

---

*Integration audit: 2026-04-28*
