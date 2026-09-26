# Establish permanent JSON compatibility oracles and the Jackson adapter

Status: resolved
Source: [GitHub #82](https://github.com/evildarkarchon/BS2BG/issues/82)
GitHub state at migration: closed (completed)
Created: 2026-08-28T12:06:43Z
Updated: 2026-08-29T04:01:35Z
Closed: 2026-08-29T04:01:35Z
Labels: ready-for-agent
Assignees: none
Parent: [spec](../spec.md)
Blocked by: 01

## Parent

- [#80](../spec.md)
- Source plan: [#62](https://github.com/evildarkarchon/BS2BG/issues/62)
- Accepted migration order: [#76](https://github.com/evildarkarchon/BS2BG/issues/76)

## What to build

Establish the permanent semantic and byte-level compatibility evidence for Project, Settings, and BoS JSON, and introduce the repository-owned Jackson streaming implementation without changing any production route.

## Acceptance criteria

- [ ] Pin Jackson Core 3.1.5 and keep Jackson types inside repository-owned JSON internals and the three format adapters.
- [ ] Provide owned strict-reader and canonical-writer profiles with path/location diagnostics, stream constraints, and stable exception translation.
- [ ] Check in permanent Project semantic, recovery, diagnostic, malformed-input, Unicode, null/omission, duplicate, lexical-number, and resource-limit fixtures.
- [ ] Check in permanent paired-Settings semantic and atomic-publication fixtures and exact BoS byte goldens.
- [ ] A temporary differential seam may compare the old and new adapters, but all production routing remains unchanged and no public generic codec abstraction is introduced.
- [ ] The full-source deterministic suite and a clean Windows x64 app-image verify the new dependency and fixtures without compatibility drift.
- [ ] The issue is independently revertible without changing user-visible production behavior.

## Blocked by

- [#81](01-establish-the-full-source-java-25-packaged-baseline.md)

## Comments

No comments at migration.
