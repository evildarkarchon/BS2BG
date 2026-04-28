---
phase: 07
slug: replay-saved-strategies-in-automation-outputs
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-28
---

# Phase 07 - Security

Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| saved `.jbs2bg` project -> Core replay service | User-controlled saved strategy data, NPC rows, and preset rows determine generated morph output. | Local project data; mod/NPC identity; preset names |
| replay working state -> output services | Request-scoped replay mutations feed byte-sensitive BodyGen generation after blocker checks. | In-memory project clone; generated assignment state |
| saved project file -> headless generation | CLI loads user-controlled project data and saved strategy configuration before filesystem writes. | Local project file contents |
| headless service -> filesystem | Generated `templates.ini`, `morphs.ini`, and BoS JSON files are written to caller-selected output directories. | Generated mod output files; filesystem paths |
| in-memory/shared project -> bundle zip | Bundle service serializes source project data and generated outputs into a shareable archive. | Project JSON, generated outputs, profile data |
| bundle report/manifest -> recipient | Bundle metadata and replay reports may be shared outside the developer's machine. | Manifest/checksum metadata; replay status; NPC identity needed for blockers |

---

## Threat Register

Threat IDs are scoped by plan because Phase 07 plan files reused `T-07-01` and `T-07-03` for different components.

| Threat ID | Category | Component | Disposition | Mitigation | Status | Evidence |
|-----------|----------|-----------|-------------|------------|--------|----------|
| 07-01/T-07-01 | Tampering | AssignmentStrategyReplayService | mitigate | Replay results expose `IsBlocked`; blocked working projects are documented as unsafe for generation. | closed | `AssignmentStrategyReplayContracts.cs:10-28`; `AssignmentStrategyReplayServiceTests.cs:73-98` |
| 07-01/T-07-02 | Tampering | AssignmentStrategyReplayService | mitigate | Replay delegates through `MorphAssignmentService.ApplyStrategy`; seeded strategies use deterministic provider while preserving `eligibleRows`. | closed | `AssignmentStrategyReplayService.cs:44-51`; `MorphAssignmentService.cs:168-172`; `AssignmentStrategyReplayServiceTests.cs:143-196` |
| 07-01/T-07-03 | Information Disclosure | AssignmentStrategyReplayService | accept | Blocker details expose only NPC identity already present in the local project and needed for remediation. | closed | Accepted risk AR-07-01; `AssignmentStrategyReplayContracts.cs:17`; `AssignmentStrategyReplayServiceTests.cs:91-97` |
| 07-02/T-07-01 | Tampering | HeadlessGenerationService | mitigate | Headless generation returns `ValidationBlocked` before target planning, overwrite preflight, directory creation, or writer calls when replay is blocked. | closed | `HeadlessGenerationService.cs:64-71`; `HeadlessGenerationService.cs:91-123`; `CliGenerationTests.cs:321-353` |
| 07-02/T-07-03 | Denial of Service | CLI output | accept | Replay work is bounded to existing in-memory NPC/preset collections; no external service calls or unbounded I/O loops were introduced. | closed | Accepted risk AR-07-02; `AssignmentStrategyReplayService.cs:40-51`; targeted Phase 7 tests passed |
| 07-02/T-07-05 | Information Disclosure | CLI blocked replay message | mitigate | Blocked CLI output includes NPC identity and rule reason, and tests assert project/output paths are not leaked. | closed | `HeadlessGenerationService.cs:216-235`; `CliGenerationTests.cs:334-343` |
| 07-03/T-07-01 | Tampering | PortableProjectBundleService BodyGen output | mitigate | Bundle planning uses a replay working project for generated output and returns `ValidationBlocked` before staging entries or zip creation when replay is blocked. | closed | `PortableProjectBundleService.cs:203-219`; `PortableProjectBundleService.cs:235-247`; `PortableBundleServiceTests.cs:588-616` |
| 07-03/T-07-04 | Information Disclosure | Bundle reports/manifests | mitigate | Replay report text flows through `BundlePathScrubber` and privacy findings include replay reports. | closed | `PortableProjectBundleService.cs:364-374`; `PortableProjectBundleService.cs:410`; `PortableProjectBundleService.cs:441-447`; `PortableBundleServiceTests.cs:588-616` |
| 07-03/T-07-06 | Repudiation | Bundle project entry vs generated output | mitigate | Bundle keeps `project/project.jbs2bg` as source state and adds explicit replay report/provenance text for generated output. | closed | `PortableProjectBundleService.cs:240-244`; `PortableProjectBundleContracts.cs:59-85`; `PortableBundleServiceTests.cs:494-523`; `PortableBundleServiceTests.cs:643-662` |

Status: open or closed. Disposition: mitigate, accept, or transfer.

---

## Summary Threat Flags

No additional threat flags were raised in Phase 07 summaries. `07-03-SUMMARY.md` explicitly records: "None - replay report/manifest sharing surface was already covered by the plan threat model and remains path-scrubbed."

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-07-01 | 07-01/T-07-03 | Replay blockers return local NPC identity already stored in the user's project and needed to repair saved strategy rules; no external service or private path surface is introduced. | GSD secure-phase workflow | 2026-04-28 |
| AR-07-02 | 07-02/T-07-03 | Additional replay work is limited to existing in-memory collections and introduces no external calls; this is acceptable for local desktop/CLI generation. | GSD secure-phase workflow | 2026-04-28 |

---

## Verification

| Check | Result |
|-------|--------|
| Threat model parsed from `07-01-PLAN.md`, `07-02-PLAN.md`, and `07-03-PLAN.md` | passed |
| Summary threat flags checked across `07-01-SUMMARY.md`, `07-02-SUMMARY.md`, and `07-03-SUMMARY.md` | passed |
| Mitigation evidence found in source and tests | passed |
| Targeted Phase 7 verification command | passed: 125 tests, 0 failures |

Command:

```powershell
dotnet test "tests/BS2BG.Tests/BS2BG.Tests.csproj" --filter "FullyQualifiedName~AssignmentStrategyReplayServiceTests|FullyQualifiedName~AssignmentStrategyServiceTests|FullyQualifiedName~MorphsViewModelStrategyTests|FullyQualifiedName~CliGenerationTests|FullyQualifiedName~PortableBundleServiceTests"
```

The test run emitted existing analyzer warnings but completed successfully.

---

## Security Audit 2026-04-28

| Metric | Count |
|--------|-------|
| Threats found | 9 |
| Closed | 9 |
| Open | 0 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-28 | 9 | 9 | 0 | GSD secure-phase workflow |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-28
