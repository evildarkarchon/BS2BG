---
phase: 04
slug: profile-extensibility-and-controlled-customization
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-27
---

# Phase 04 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| profile JSON file → Core/App validation | User-selected or AppData custom profile JSON enters profile validation and catalog inclusion paths. | Local JSON, profile identity, slider numeric tables |
| project JSON → project/profile services | Shared `.jbs2bg` project JSON can contain embedded custom profiles and profile references. | Project model, embedded profile DTOs, preset profile names |
| UI commands → profile/project state | Profiles, Diagnostics, and Templates actions can import, save, export, remap, or activate overlays. | User intent, local files, runtime catalog, undo/dirty state |
| search/filter text → editor projections | Local search text controls displayed profile rows without changing source rows. | In-memory editor row projections |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status | Evidence |
|-----------|----------|-----------|-------------|------------|--------|----------|
| T-04-01-01 | Tampering | ProfileDefinitionService.ValidateProfileJson | mitigate | Parse with System.Text.Json and reject malformed schema, nonnumeric values, duplicate names, and ambiguous identity before returning a valid profile. | closed | `src/BS2BG.Core/Generation/ProfileDefinitionService.cs:29`, `:77-84`, `:178-218`, `:264-279`, `:461-464` |
| T-04-01-02 | Spoofing | ProfileValidationContext duplicate-name checks | mitigate | Use case-insensitive existing-name set so custom profiles cannot shadow bundled names. | closed | `src/BS2BG.Core/Generation/ProfileDefinitionService.cs:82-84`; `src/BS2BG.App/Services/UserProfileStore.cs:101`, `:130-132` |
| T-04-01-03 | DoS | ProfileDefinitionService | accept | Local user-selected JSON could be large; standard JsonDocument parsing is acceptable for desktop local files in Phase 4. | closed | Accepted risk AR-04-01-03 |
| T-04-02-01 | Tampering | UserProfileStore.DiscoverProfiles | mitigate | Validate entire JSON with ProfileDefinitionService before returning a catalog candidate. | closed | `src/BS2BG.App/Services/UserProfileStore.cs:117-138` |
| T-04-02-02 | Spoofing | TemplateProfileCatalog constructor | mitigate | Reject case-insensitive duplicate names across bundled and custom entries. | closed | `src/BS2BG.Core/Generation/TemplateProfileCatalog.cs:86-102` |
| T-04-02-03 | Tampering/DoS | UserProfileStore.SaveProfile | mitigate | Use sanitized filenames and AtomicFileWriter so partial local profile writes do not corrupt existing files. | closed | `src/BS2BG.App/Services/UserProfileStore.cs:152-163`, `:211-267` |
| T-04-03-01 | Tampering | ProjectFileService.LoadFromString | mitigate | Validate embedded profile DTOs before hydrating them; malformed profiles do not mutate legacy preset profile strings. | closed | `src/BS2BG.Core/Serialization/ProjectFileService.cs:69-92`, `:331-360` |
| T-04-03-02 | Information Disclosure | ProjectFileService.SaveToString | mitigate | Serialize only custom profiles referenced by project presets; do not include unrelated local profiles. | closed | `src/BS2BG.Core/Serialization/ProjectFileService.cs:209-243` |
| T-04-03-03 | Tampering | ProjectFileDto field order/names | mitigate | Preserve legacy property names/order and add only optional CustomProfiles section after existing root fields. | closed | `src/BS2BG.Core/Serialization/ProjectFileService.cs:413-429` |
| T-04-04-01 | Information Disclosure/Integrity | ProfileRecoveryDiagnosticsService.Analyze | mitigate | Always surface neutral missing-profile diagnostics when fallback is used. | closed | `src/BS2BG.Core/Diagnostics/ProfileRecoveryDiagnosticsService.cs:84-100`, `:106-115` |
| T-04-04-02 | Spoofing | CanResolveMissingReference | mitigate | Use internal profile display name exact match ignoring case; ignore filename/path. | closed | `src/BS2BG.Core/Diagnostics/ProfileRecoveryDiagnosticsService.cs:59-64`; `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:230-233` |
| T-04-04-03 | Tampering | Recovery diagnostics | mitigate | Keep diagnostics read-only; mutation happens only through explicit App commands. | closed | `src/BS2BG.Core/Diagnostics/ProfileRecoveryDiagnosticsService.cs:73-103`; `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:275-289` |
| T-04-05-01 | Tampering | ProfileManagerViewModel.ImportProfileCommand | mitigate | Validate imported JSON with Core service before store write or catalog refresh. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:321-364` |
| T-04-05-02 | Elevation of Privilege | ProfileManagerViewModel bundled rows | mitigate | Disable edit/delete/save for bundled rows; only copy-as-custom is allowed. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:72-90`, `:417-425`; `src/BS2BG.App/Views/MainWindow.axaml:1563-1590` |
| T-04-05-03 | Tampering | ProfileEditorViewModel.SaveProfileCommand | mitigate | Gate save on validation blockers and route writes through UserProfileStore, not direct ViewModel file I/O. | closed | `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:150-169`, `:345-363` |
| T-04-06-01 | Tampering | MainWindowViewModel project open | mitigate | Snapshot local profiles, collect decisions before mutation, and abort/rollback on cancel or store-write failure. | closed | `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:476-512`, `:603-627` |
| T-04-06-02 | Spoofing | TemplateProfileCatalogService overlay | mitigate | Allow project-scoped local-custom override only for active project custom names; reject bundled-name collisions. | closed | `src/BS2BG.App/Services/TemplateProfileCatalogService.cs:168-188` |
| T-04-06-03 | Repudiation | Rename Project Copy during open | mitigate | Mark loaded project dirty after open when profile references are renamed. | closed | `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:647-668`, `:523-524` |
| T-04-07-01 | Spoofing | Import Matching Profile | mitigate | Use internal display-name exact match ignoring case; filenames do not resolve references. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:190-233` |
| T-04-07-02 | Repudiation | Remap to Installed Profile | mitigate | Record undo/redo and dirty state for profile reference changes. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:291-312`; `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:384-404` |
| T-04-07-03 | Information Disclosure | Export Profile JSON | mitigate | Enable export only for selected custom/embedded profile data. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:428-459` |
| T-04-08-01 | Tampering | Profiles tab action buttons | mitigate | Bind buttons to validated ViewModel commands; UI does not write files directly. | closed | `src/BS2BG.App/Views/MainWindow.axaml:1410`, `:1570-1590` |
| T-04-08-02 | Spoofing | Source badges | mitigate | Display explicit text badges for Bundled/Custom/Embedded/Missing. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:648-650`, `:658`; `src/BS2BG.App/Views/MainWindow.axaml:1563` |
| T-04-08-03 | Repudiation | Remap/recovery UI | mitigate | Recovery/remap controls route through undo-aware commands and visible neutral copy. | closed | `src/BS2BG.App/ViewModels/DiagnosticsViewModel.cs:195-234`; `src/BS2BG.App/ViewModels/TemplatesViewModel.cs:397-404` |
| T-04-09-01 | Tampering | CopyBundledProfile | mitigate | Capture selected row before clearing selection. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:374-389` |
| T-04-09-02 | Repudiation | Profile row actions | mitigate | Bind row selection/action state explicitly and cover deterministic source rows. | closed | `src/BS2BG.App/Views/MainWindow.axaml:1435`, `:1461`, `:1484`, `:1507`; `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:146-162` |
| T-04-09-03 | Information Disclosure | Profile JSON export | accept | Export writes only the user-selected local/embedded profile through existing picker paths; no unrelated profiles are exported by this plan. | closed | Accepted risk AR-04-09-03; implementation evidence `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:432-446` |
| T-04-10-01 | Tampering | ProfileEditorViewModel | mitigate | Re-run validation on every metadata/table row mutation before enabling save. | closed | `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:190-210`, `:237-295` |
| T-04-10-02 | Denial of Service | Row subscriptions | mitigate | Detach subscriptions for removed rows/current editor replacement. | closed | `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:176-187`, `:212-235`, `:252-303` |
| T-04-10-03 | Tampering | Numeric validation | mitigate | Reject malformed/nonnumeric values but preserve broad finite numeric acceptance. | closed | `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:391-471` |
| T-04-11-01 | Information Disclosure | ProjectSaveContext construction | mitigate | Include available custom profiles in context but rely on Core referenced-name filtering. | closed | `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:928-941`; `src/BS2BG.Core/Serialization/ProjectFileService.cs:211-243` |
| T-04-11-02 | Tampering | GUI project save path | mitigate | Use ProjectFileService.SaveToString(project, context). | closed | `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:903-909` |
| T-04-11-03 | Spoofing/Tampering | Conflict rename validation | mitigate | Validate names against separate local/bundled occupancy. | closed | `src/BS2BG.App/ViewModels/MainWindowViewModel.cs:671-707` |
| T-04-12-01 | Tampering | SaveSelectedProfileAsync | mitigate | Save only Editor.BuildProfile(LocalCustom, filePath) after validation; reuse local file path only for LocalCustom rows. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:392-407`, `:417-425` |
| T-04-12-02 | Repudiation | TrySelectProfileAsync | mitigate | Track committed selection and restore it when discard is declined. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:146-157`, `:461-525` |
| T-04-12-03 | Denial of Service | ImportProfilesAsync / ExportSelectedProfileAsync | mitigate | Catch expected IOException/UnauthorizedAccessException and report StatusMessage text. | closed | `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:321-364`, `:444-457` |
| T-04-12-04 | Information Disclosure | ExportSelectedProfileAsync | accept | Export remains limited to explicit LocalCustom/EmbeddedProject selections; failure handling does not broaden export scope. | closed | Accepted risk AR-04-12-04; implementation evidence `src/BS2BG.App/ViewModels/ProfileManagerViewModel.cs:432-446` |
| T-04-13-01 | Tampering | RefreshVisibleRows | mitigate | Populate visible projections only; keep build paths reading source collections. | closed | `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:391-430`, `:473-485`; `src/BS2BG.App/Views/MainWindow.axaml:1677`, `:1702` |
| T-04-13-02 | Denial of Service | Profile editor filtering | accept | Filtering is in-memory over existing ObservableCollections; no file, network, or unbounded background work is added. | closed | Accepted risk AR-04-13-02; implementation evidence `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:82`, `:473-485` |
| T-04-13-03 | Information Disclosure | Filtered profile rows | accept | Search only narrows local visible rows already loaded into the editor; it does not expose external data. | closed | Accepted risk AR-04-13-03; implementation evidence `src/BS2BG.App/ViewModels/ProfileEditorViewModel.cs:473-485` |

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-04-01-03 | T-04-01-03 | Local user-selected JSON could be large; standard `JsonDocument` parsing is acceptable for desktop local files in Phase 4. | phase plan | 2026-04-27 |
| AR-04-09-03 | T-04-09-03 | Export writes only the user-selected local/embedded profile through existing picker paths; no private unrelated profiles are exported by this plan. | phase plan | 2026-04-27 |
| AR-04-12-04 | T-04-12-04 | Export remains limited to explicit LocalCustom/EmbeddedProject selections from prior plans; this plan only changes failure handling and does not broaden export scope. | phase plan | 2026-04-27 |
| AR-04-13-02 | T-04-13-02 | Filtering is in-memory over existing ObservableCollections; no file, network, or unbounded background work is added. | phase plan | 2026-04-27 |
| AR-04-13-03 | T-04-13-03 | Search only narrows local visible rows already loaded into the editor; it does not expose external data. | phase plan | 2026-04-27 |

---

## Unregistered Flags

None. All 13 summaries reported no additional threat flags.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-27 | 40 | 40 | 0 | gsd-security-auditor |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-27
