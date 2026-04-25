## Why

The port is only ready for modders when it is accessible, packaged, documented, and fast on real Windows machines. M7 turns the finished app into a releaseable artifact.

## What Changes

- Perform accessibility audit and fix keyboard navigation, automation names, focus order, and contrast issues.
- Add self-contained single-file Windows publishing with a portable zip layout.
- Add signing support or documented unsigned-build handling for release artifacts.
- Add release notes, credits, and packaging documentation.
- Verify cold-launch performance and final manual QA matrix.

## Capabilities

### New Capabilities
- `release-polish`: Defines accessibility, packaging, signing, release notes, and launch-performance release gates.

### Modified Capabilities

## Impact

- Adds publish scripts/configuration and release documentation.
- Adds packaging validation and smoke checks.
- Adds final QA artifacts for Windows 10/11, DPI variants, themes, and cross-platform launch checks.
