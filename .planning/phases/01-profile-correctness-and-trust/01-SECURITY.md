---
phase: 01
slug: profile-correctness-and-trust
status: verified
threats_open: 0
asvs_level: 1
created: 2026-04-26
---

# Phase 01 - Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| Bundled profile JSON -> Core/App profile catalog | Root-level profile JSON files are copied as app content and loaded into the default catalog. | Slider defaults, multipliers, and inverted slider metadata. |
| `.jbs2bg` project JSON -> Core model | User-controlled project files can contain arbitrary saved `Profile` strings. | Project profile names and preset metadata. |
| Core catalog -> App ViewModel | Catalog lookup results drive fallback information and generation profile calculation. | Bundled profile names and fallback profile metadata. |
| User-selected XML files -> Templates workflow | Imported presets and slider names are untrusted local file input. | BodySlide preset profile/slider data. |
| ViewModel profile state -> AXAML display | Saved profile names and fallback copy are displayed to the local user. | Local project profile text. |
| Project model -> export files | Generated template and morph strings are persisted to INI/JSON files. | BodyGen templates, morph assignments, and BoS JSON. |
| Planning source contracts -> future agents | ROADMAP and REQUIREMENTS constrain future implementation and verification behavior. | Phase requirements, accepted decisions, and verification wording. |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-01-01 | Tampering | `settings_FO4_CBBE.json` | mitigate | `BS2BG.App.csproj:31-33` registers `settings_FO4_CBBE.json`; `TemplateProfileCatalogFactory.cs:28-29` loads it; `TemplateProfileCatalogFactoryTests.cs:28-62` verifies FO4-only defaults, neutral multipliers, and no inversion. | closed |
| T-01-02 | Information Disclosure | Profile catalog | accept | Accepted risk AR-01: bundled profile JSON contains deterministic slider metadata only. | closed |
| T-01-03 | Denial of Service | `TemplateProfileCatalogFactory.CreateDefault` | accept | Accepted risk AR-02: missing required bundled profiles fail closed as local startup/configuration errors; failure path exists at `TemplateProfileCatalogFactory.cs:33-39`. | closed |
| T-01-04 | Tampering | `ProjectFileService.LoadFromString` | mitigate | `ProjectFileService.cs:88-92,157-163` preserves profile strings; `ProjectFileServiceTests.cs:122-148` proves unbundled names load/save unchanged. | closed |
| T-01-05 | Spoofing | `TemplateProfileCatalog.ContainsProfile` | mitigate | `TemplateProfileCatalog.cs:20-24,34-41` uses exact profile-name matching with `StringComparison.OrdinalIgnoreCase`; `TemplateProfileCatalogTests.cs:21-33` proves unknown names remain distinguishable. | closed |
| T-01-06 | Denial of Service | Unknown project profile names | accept | Accepted risk AR-03: unknown profile names remain non-blocking; fallback remains non-throwing at `TemplateProfileCatalog.cs:26-32`. | closed |
| T-01-07 | Tampering | `TemplatesViewModel.ImportPresetFilesCoreAsync` | mitigate | `TemplatesViewModel.cs:444-460` assigns imported presets from `SelectedProfileName`; `TemplatesViewModelTests.cs:510-532` proves path/slider inference does not override selected profile. | closed |
| T-01-08 | Spoofing | Fallback profile display | mitigate | `TemplatesViewModel.cs:699-713` builds fallback copy from exact saved profile and catalog fallback name; `TemplatesViewModelTests.cs:534-546` asserts exact text. | closed |
| T-01-09 | Denial of Service | Unprofiled/custom sliders | accept | Accepted risk AR-04: unknown/custom sliders remain non-blocking and warning-free; `TemplatesViewModelTests.cs:548-566` covers this behavior. | closed |
| T-01-10 | Spoofing | `ProfileFallbackInformationPanel` | mitigate | `MainWindow.axaml:319-330` binds directly to `Templates.IsProfileFallbackInformationVisible` and `Templates.ProfileFallbackInformationText`; ViewModel source text is at `TemplatesViewModel.cs:709-713`. | closed |
| T-01-11 | Information Disclosure | Fallback panel | accept | Accepted risk AR-05: fallback panel shows only local project profile names already visible to the user. | closed |
| T-01-12 | Repudiation | Release profile note | mitigate | `docs/release/README.md:27-32` documents Fallout 4 CBBE seed assumptions outside the in-app workflow. | closed |
| T-01-13 | Tampering | `TemplatesViewModel.SetSelectedProfileNameFromPreset` | mitigate | `TemplatesViewModel.cs:666-693` keeps unbundled saved names inert and resolves calculation profile without adoption. | closed |
| T-01-14 | Repudiation | Profile selector adoption path | mitigate | `TemplatesViewModel.cs:610-638` writes `SelectedPreset.ProfileName = resolvedName` only on explicit selector changes; `TemplatesViewModelTests.cs:568-605` proves explicit adoption is required. | closed |
| T-01-15 | Information Disclosure | Fallback information text | accept | Accepted risk AR-06: fallback text repeats local saved profile metadata only. | closed |
| T-01-16 | Tampering | Bundled profile tests | mitigate | `SliderMathFormatterTests.cs:160-202` loads root bundled profile files and asserts FO4-only names are absent from Skyrim outputs. | closed |
| T-01-17 | Repudiation | Morph profile independence | mitigate | `MorphCoreTests.cs:167-180,202-220` proves morph output is independent of `Skyrim CBBE`, `Skyrim UUNP`, and `Fallout 4 CBBE` profile names. | closed |
| T-01-18 | Tampering | Golden expected fixtures | mitigate | `git diff -- tests/fixtures/expected` produced no output during audit; `01-06-SUMMARY.md:104-110` also records no golden fixture changes. | closed |
| T-01-19 | Repudiation | ROADMAP/REQUIREMENTS | mitigate | `.planning/ROADMAP.md:30-32` and `.planning/REQUIREMENTS.md:14-18` cite the D-05 through D-08/D-12/D-16 accepted override. | closed |
| T-01-20 | Tampering | Phase scope | mitigate | `.planning/ROADMAP.md:23-44` and `.planning/REQUIREMENTS.md:10-20` confine source-contract edits to Phase 1 profile-correctness wording. | closed |
| T-01-21 | Information Quality | Future verification | mitigate | `.planning/ROADMAP.md:23-32` and `.planning/REQUIREMENTS.md:14-18` replace contradictory warning/experimental wording with neutral fallback and release-doc contracts matching implementation. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-01-02 | Profile JSON files are bundled deterministic slider metadata and contain no secrets, credentials, telemetry, account identifiers, or PII. | Phase 1 security audit | 2026-04-26 |
| AR-02 | T-01-03 | Missing bundled profile files cause a startup/configuration failure in a local desktop app; this is preferable to silent profile substitution. | Phase 1 security audit | 2026-04-26 |
| AR-03 | T-01-06 | Unknown project profile names remain non-blocking so legacy/custom projects can load and generate through visible fallback behavior. | Phase 1 security audit | 2026-04-26 |
| AR-04 | T-01-09 | Unknown/custom slider names are common in body-mod workflows and must not block generation or create mismatch warnings in Phase 1. | Phase 1 security audit | 2026-04-26 |
| AR-05 | T-01-11 | The fallback panel displays the saved project profile name, which is local project data already visible to the local user. | Phase 1 security audit | 2026-04-26 |
| AR-06 | T-01-15 | Fallback information text repeats local saved profile metadata only and introduces no new sensitive data source. | Phase 1 security audit | 2026-04-26 |

---

## Threat Flags

None. Summaries `01-03` through `01-07` explicitly report `Threat Flags: None`; summaries `01-01` and `01-02` report no authentication gates, no known stubs, successful verification, and no user setup required.

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-04-26 | 21 | 21 | 0 | gsd-security-auditor |

## Security Audit 2026-04-26

| Metric | Count |
|--------|-------|
| Threats found | 21 |
| Closed | 21 |
| Open | 0 |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-04-26
