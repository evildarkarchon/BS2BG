---
phase: 05
slug: automation-sharing-and-release-trust
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-27
verified: 2026-04-27
---

# Phase 05 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| CLI args→Core request | Untrusted command-line paths and intent values become filesystem write requests. | Project paths, output paths, output intent, overwrite flags. |
| Project file→Core model | User-controlled `.jbs2bg` JSON drives validation and output content. | Project JSON, preset/profile/NPC/strategy data. |
| CLI output path→filesystem | User-selected directory receives generated artifacts. | `templates.ini`, `morphs.ini`, BoS JSON files. |
| Shared `.jbs2bg`→strategy model | Collaborator-provided JSON controls assignment eligibility. | Assignment strategy schema, rules, weights, seed, race strings. |
| Strategy config→NPC assignment mutation | Shared project data controls project assignment changes. | NPC assignment state and preset references. |
| GUI inputs→ProjectModel strategy | User-entered seed/rule text changes persisted project state and assignments. | Strategy editor fields, undo snapshots, project dirty state. |
| Project/profile data→zip | Shared project data becomes an archive sent to another person. | Project JSON, generated outputs, custom profile JSON, reports. |
| Filesystem path→manifest/report | Local absolute paths may accidentally enter support artifacts. | Source filenames, private roots, validation report text. |
| GUI/CLI bundle target→filesystem | User-selected zip path is created or overwritten. | Bundle destination path and overwrite flag. |
| Release script→distributed zip | Local build artifacts become user-downloaded files. | App/CLI binaries, docs, profile assets, checksums. |
| Optional signing config→executable | Optional certificate/signing inputs affect trust metadata. | SignTool path, certificate subject/path, certificate password environment value. |
| Documentation→user filesystem actions | Users may follow setup docs to place generated files. | BodyGen/BodySlide/BoS setup guidance. |
| User filesystem→bundle zip target | Existing user-created bundle may be replaced by a new archive. | Existing bundle bytes and replacement zip temp file. |
| Shared project/custom profile data→generated bundle outputs | Embedded/local custom profile JSON influences generated BodyGen/BoS bytes. | Custom profile definitions, generation catalog, bundled outputs. |
| CLI user input→process exit contract | Missing/malformed project paths and filesystem failures cross into automation scripts. | Expected exceptions, stderr text, stable exit codes. |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status | Evidence |
|-----------|----------|-----------|-------------|------------|--------|----------|
| T-05-01-01 | Tampering | Program.cs parser | mitigate | System.CommandLine typed options and accepted intent values before request creation. | closed | `src/BS2BG.Cli/Program.cs:25-42`, `Program.cs:95-103`; tests `CliGenerationTests.cs:53-79`. |
| T-05-01-02 | Elevation of Privilege | BS2BG.Cli project references | mitigate | CLI references Core only, not App/Avalonia. | closed | `Program.cs:1-9` has Core-only usings; test asserts no `BS2BG.App`/`Avalonia` in CLI project at `CliGenerationTests.cs:40-46`. |
| T-05-02-01 | Tampering | HeadlessGenerationService | mitigate | Project validation runs before writes and blockers return exit code 2. | closed | `HeadlessGenerationService.cs:61-68`; test `CliGenerationTests.cs:143-159`. |
| T-05-02-02 | Tampering | Output preflight | mitigate | Existing selected targets are checked and require overwrite before writes. | closed | `HeadlessGenerationService.cs:69-80`; test `CliGenerationTests.cs:210-229`. |
| T-05-02-03 | Information Disclosure | CLI messages | accept | CLI generation intentionally prints selected user paths for automation feedback; bundle/report scrubbing is separately enforced. | closed | Accepted risk AR-05-01 below; generation output writer prints `WrittenFiles`/ledger paths at `Program.cs:293-301`. |
| T-05-02-04 | Tampering | BoS filename preflight | mitigate | Overwrite preflight uses writer-owned BoS JSON planner. | closed | `HeadlessGenerationService.cs:20-21`, `HeadlessGenerationService.cs:130-132`; test wiring includes `BosJsonExportPlanner` at `CliGenerationTests.cs:501-503`. |
| T-05-03-01 | Tampering | ProjectFileService strategy DTO | mitigate | Nullable-safe strategy hydration, schema/version validation, recoverable diagnostics. | closed | `ProjectFileService.cs:363-407`, validation at `ProjectFileService.cs:431-495`; persisted field at `ProjectFileService.cs:641-644`. |
| T-05-03-02 | Information Disclosure | Race rules | mitigate | Store imported race strings/preset names only; no game/plugin lookup. | closed | `AssignmentStrategyService.cs:118-120`, `AssignmentStrategyService.cs:167-173`; test rejects Plugin/Game/xEdit references at `AssignmentStrategyServiceTests.cs:286-302`. |
| T-05-04-01 | Tampering | AssignmentStrategyService | mitigate | Stable ordering, provider seam, pinned deterministic provider, exact-sequence tests. | closed | Stable ordering `AssignmentStrategyService.cs:228-235`; provider seam `AssignmentStrategyService.cs:11-19`, `105-108`; Mulberry32 `DeterministicAssignmentRandomProvider.cs:3-9`, `36-45`; tests `AssignmentStrategyServiceTests.cs:239-263`. |
| T-05-04-02 | Tampering | No eligible rules | mitigate | Block and diagnose per-NPC gaps instead of fallback to all presets. | closed | Blocking in `AssignmentStrategyService.cs:45-49`, no mutation on empty eligibility `AssignmentStrategyService.cs:84-87`; tests `AssignmentStrategyServiceTests.cs:325-358`, diagnostics `AssignmentStrategyServiceTests.cs:360-427`. |
| T-05-05-01 | Tampering | MorphsViewModel ApplyStrategyCommand | mitigate | Validate strategy input, capture undo snapshots, block no-eligible rows visibly. | closed | Commands/canExecute `MorphsViewModel.cs:348-353`, validation `MorphsViewModel.cs:1450-1555`, undo/apply/no-eligible copy `MorphsViewModel.cs:1361-1404`; tests `MorphsViewModelStrategyTests.cs:81-82`. |
| T-05-05-02 | Repudiation | Strategy UI | mitigate | Persist strategy config and expose reproducibility summary text. | closed | Save persists `project.AssignmentStrategy` and status copy `MorphsViewModel.cs:1336-1354`; summary copy `MorphsViewModel.cs:1441-1447`. |
| T-05-06-01 | Tampering | Zip entries | mitigate | Normalize relative entry paths and reject rooted/traversal paths. | closed | `BundlePathScrubber.cs:19-33`; tests `PortableBundleServiceTests.cs:195-214`. |
| T-05-06-02 | Information Disclosure | manifest/report | mitigate | Central scrubber detects drive roots, UNC, backslashes, usernames, private roots. | closed | `BundlePathScrubber.cs:40-75`; privacy tests `PortableBundleServiceTests.cs:216-235`, manifest/report no path tests `PortableBundleServiceTests.cs:331-360`. |
| T-05-06-03 | Tampering | bundle target | mitigate | Existing zip target refused unless overwrite is true. | closed | `PortableProjectBundleService.cs:111-122`; test `PortableBundleServiceTests.cs:362-375`. |
| T-05-06-04 | Tampering | generated bundle outputs | mitigate | Stage outputs through existing BodyGen/BoS writers and zip exact bytes. | closed | `PortableProjectBundleService.cs:254-276`; byte parity tests `PortableBundleServiceTests.cs:427-445`. |
| T-05-06-05 | Information Disclosure | diagnostics formatter | mitigate | Core report formatter plus BundlePathScrubber, no App formatter/raw path dependency in bundle service. | closed | `PortableProjectBundleService.cs:28-29`, `183-199`, `349-365`; privacy scan `PortableProjectBundleService.cs:395-399`. |
| T-05-07-01 | Tampering | CLI/GUI bundle target | mitigate | CLI `--overwrite` and GUI explicit overwrite flag required for existing zip targets. | closed | CLI option `Program.cs:82-93`, outcome mapping `Program.cs:155-163`; GUI check `MainWindowViewModel.cs:927-955`; test `PortableBundleServiceTests.cs:49-60`, `362-375`. |
| T-05-07-02 | Information Disclosure | Bundle preview | mitigate | Preview uses Core scrubber before write and exposes privacy status. | closed | Preview `MainWindowViewModel.cs:912-920`, privacy summary `MainWindowViewModel.cs:1016-1033`; Core preview privacy `PortableProjectBundleService.cs:89-100`, `395-399`. |
| T-05-08-01 | Spoofing | Release artifacts | mitigate | Generate SHA-256 sidecars/SHA256SUMS and optional signing metadata. | closed | `package-release.ps1:319-340`, signing metadata `package-release.ps1:113-176`; docs `README.md:41-50`; tests `ReleaseTrustTests.cs:24-58`. |
| T-05-08-02 | Tampering | package-release.ps1 | mitigate | Required-file/checksum/package assertions and ReleaseTrustTests. | closed | Required assertions `package-release.ps1:305-317`, checksum `package-release.ps1:319-334`; tests `ReleaseTrustTests.cs:12-22`, `99-128`. |
| T-05-08-03 | Denial of Service | SignTool dependency | mitigate | SignTool optional; unsigned checksum-backed path remains valid. | closed | Optional SignTool handling `package-release.ps1:98-142`; docs `UNSIGNED-BUILD.md:3-6`, `15-20`; test `ReleaseTrustTests.cs:39-58`. |
| T-05-08-04 | Information Disclosure | signing config | mitigate | Password env-var values are not logged; certificate paths redacted to filenames. | closed | Password only in local arg array `package-release.ps1:122-155`, output suppressed `package-release.ps1:158-175`, filename redaction `package-release.ps1:121`, `135`, `174`; test `ReleaseTrustTests.cs:60-74`. |
| T-05-08-05 | Tampering | release composition | mitigate | Package smoke/zip inspection covers CLI, checksums, docs, profile assets, path-safe entries. | closed | Normalized zip and path safety `package-release.ps1:198-264`, `336-337`; ReleaseSmoke inspection `ReleaseTrustTests.cs:99-128`, helper `ReleaseTrustTests.cs:158-182`. |
| T-05-09-01 | Tampering | Setup docs | mitigate | Docs state BS2BG does not edit plugins and guide verification before copying. | closed | `BODYGEN-BODYSLIDE-BOS-SETUP.md:5`, `41-60`; tests `ReleaseDocsTests.cs:19-31`. |
| T-05-09-02 | Information Disclosure | Setup docs | accept | Static packaged docs contain no user-specific paths; tests assert packaged docs inclusion rather than local path capture. | closed | Accepted risk AR-05-02 below; static docs use generic MO2/Vortex/manual guidance at `BODYGEN-BODYSLIDE-BOS-SETUP.md:17-23`, packaging inclusion tests `ReleaseDocsTests.cs:33-48`. |
| T-05-10-01 | Tampering | PortableProjectBundleService.Create | mitigate | Same-directory temp zip and only final commit with File.Replace/File.Move after archive close; prior final path not deleted. | closed | Temp path and archive close before commit `PortableProjectBundleService.cs:136-160`; catch deletes only temp `PortableProjectBundleService.cs:170-180`; commit `PortableProjectBundleService.cs:450-456`; test `PortableBundleServiceTests.cs:378-390`. |
| T-05-10-02 | Information Disclosure | CLI bundle failure messages | mitigate | Expected exceptions map to concise stderr without stack traces/type names/private roots. | closed | Project load mapping `Program.cs:123-127`, `243-268`; I/O catch `Program.cs:137-141`; review confirms no path leak at `05-REVIEW.md:70-78`. |
| T-05-10-03 | Tampering | Bundle generation catalog | mitigate | Build request-scoped catalog from bundled profiles plus referenced custom profiles before validation/generation. | closed | `BuildRequestProfileCatalog` and `ResolveBundleProfileSet` at `PortableProjectBundleService.cs:186-193`, `288-336`; generation uses request catalog `PortableProjectBundleService.cs:254-276`; tests `PortableBundleServiceTests.cs:447-484`. |
| T-05-10-04 | Denial of Service | Temp bundle artifacts | accept | Abrupt process termination can leave temp files; managed failures best-effort cleanup and final bundle preservation. | closed | Accepted risk AR-05-03 below; managed cleanup code `PortableProjectBundleService.cs:248-251`, `426-448`; preservation test `PortableBundleServiceTests.cs:378-390`. |

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-05-01 | T-05-02-03 | CLI generation prints selected user paths as part of script-friendly automation feedback. This is consistent with the plan; bundle/report privacy controls are separate and verified. | Phase 05 plan disposition | 2026-04-27 |
| AR-05-02 | T-05-09-02 | Static packaged docs are accepted because they do not capture runtime user paths; release tests assert package inclusion and no in-app local-path discovery/wizard. | Phase 05 plan disposition | 2026-04-27 |
| AR-05-03 | T-05-10-04 | Abrupt process termination can leave temp bundle files; normal managed failures perform best-effort cleanup and preserve the final bundle. | Phase 05 plan disposition | 2026-04-27 |

---

## Threat Flags

| Flag | Source | Mapping | Status |
|------|--------|---------|--------|
| threat_flag: cli-args-to-filesystem | `05-01-SUMMARY.md` | Maps to T-05-01-01 and T-05-02-02. | registered |
| threat_flag: cli-output-filesystem | `05-02-SUMMARY.md` | Maps to T-05-02-01, T-05-02-02, and T-05-02-03. | registered |

No unregistered flags.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-27 | 30 | 30 | 0 | Kilo security auditor |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-27
