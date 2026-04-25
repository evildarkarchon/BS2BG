## Context

M7 prepares the completed app for users. The milestone focuses on accessibility, packaging, signing, release notes, and performance rather than new product features.

## Goals / Non-Goals

**Goals:**
- Complete accessibility audit and fixes.
- Produce a portable Windows release package with self-contained single-file executable.
- Add signing or documented unsigned-release handling.
- Ship release notes, credits, known limitations, and QA evidence.
- Validate launch performance and manual QA matrix.

**Non-Goals:**
- No new v2.1 feature work.
- No installer unless a later decision changes the portable distribution goal.

## Decisions

- Use `dotnet publish -r win-x64 --self-contained` as the release basis and package the executable with required profile/assets folders.
- Keep the default distribution installer-less to match modder expectations.
- Treat accessibility and performance checks as release gates, not best-effort cleanup.
- Keep signing configuration optional until certificate availability is known, but require explicit release documentation either way.

## Risks / Trade-offs

- Single-file Avalonia packaging may need asset inclusion tweaks -> validate the published artifact from a clean extraction directory.
- Code signing may be unavailable -> document unsigned warnings clearly and provide checksums.
- Cold-start target may be affected by bundled assets -> measure with release build and trim only after correctness is verified.

## Migration Plan

No user-data migration. Release packaging must include default profiles and preserve portable first-run behavior.

## Open Questions

- Confirm whether a signing certificate is available for v2.0 or whether checksums plus unsigned-warning docs are the accepted release path.
