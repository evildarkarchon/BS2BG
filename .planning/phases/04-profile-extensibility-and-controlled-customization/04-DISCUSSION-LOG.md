# Phase 4: Profile Extensibility and Controlled Customization - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-26
**Phase:** 04-profile-extensibility-and-controlled-customization
**Areas discussed:** Profile storage, Authoring rules, Missing recovery, Sharing profiles

---

## Profile Storage

| Question | Selected | Alternatives considered |
|----------|----------|-------------------------|
| Where should custom profile JSON files live at runtime? | User profiles folder | App profiles folder; Project-local only; You decide |
| How should bundled profiles be treated once custom editing exists? | Read-only with copy | Editable with reset; Hide bundled files; You decide |
| What happens for duplicate profile display names? | Reject duplicate names | Allow with file ID; Custom overrides bundled; You decide |
| Should profile discovery be automatic, manual, or both? | Both, local-first | Manual import only; Automatic folder scan only; You decide |

**Notes:** User accepted the recommended storage path: user-local custom profile folder, protected bundled profiles, unique names, and local folder discovery plus explicit import/copy.

---

## Authoring Rules

| Question | Selected | Alternatives considered |
|----------|----------|-------------------------|
| What should be editable in a custom profile? | Metadata and tables | Tables only; Metadata only; You decide |
| How strict should profile JSON validation be? | Strict reject | Import with cautions; Loose runtime fallback; You decide |
| What numeric ranges should profile table values allow? | Permit broad floats | Clamp to normal ranges; No range checks; You decide |
| How should users make a new custom profile? | Blank profile allowed | Copy/import first; Wizard from preset; You decide |

**Notes:** User accepted strict validation but explicitly chose to allow blank custom profiles and broad numeric values for modder flexibility.

---

## Missing Recovery

| Question | Selected | Alternatives considered |
|----------|----------|-------------------------|
| What should users see when a project references a missing custom profile? | Resolvable diagnostic | Blocking open dialog; Templates-only notice; You decide |
| Should generation/export be allowed while unresolved? | Allow with visible fallback | Block until resolved; Ask at export time; You decide |
| Which recovery actions should be available? | Import or remap | Import only; Remap only; You decide |
| How should an imported profile match unresolved references? | Exact name match | Prompt on mismatch; Filename can match; You decide |

**Notes:** User kept Phase 1's non-blocking fallback behavior but required the missing custom-profile state to be visible and actionable.

---

## Sharing Profiles

| Question | Selected | Alternatives considered |
|----------|----------|-------------------------|
| Should custom profile data be embedded or copied as sidecar JSON? | Embed in project | Sidecar JSON files; Both formats; You decide |
| How should older-reader compatibility be handled if embedded? | Add optional section | Profile sidecar fallback; New project version; You decide |
| Which custom profiles should be included? | Referenced only | All custom profiles; User chooses each time; You decide |
| What if an embedded profile conflicts with a local profile of the same name? | Prompt to import or keep local | Project copy wins; Local copy wins; You decide |
| Should Phase 4 include separate export-profile action? | Yes, profile export | No, project embed only; Copy folder path only; You decide |

**Notes:** User overrode the recommended sidecar-first approach and chose embedded referenced custom profiles in `.jbs2bg`, with optional-section compatibility and explicit conflict prompts.

---

## the agent's Discretion

- Exact user profiles folder path and filename convention.
- Exact Profile Manager/Diagnostics/Templates UI placement.
- Exact optional `.jbs2bg` embedded custom-profile DTO shape.
- Exact validation and diagnostic wording.

## Deferred Ideas

None.
