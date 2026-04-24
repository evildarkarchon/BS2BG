## Context

M1 builds on the M0 Core project and fixture corpus. The app cannot safely import, edit, or export user data until `.jbs2bg` load/save behavior is compatible with v1 project files.

## Goals / Non-Goals

**Goals:**
- Add Core models for presets, set sliders, profiles, custom targets, NPCs, and project state.
- Implement `ProjectFileService` with v1-compatible JSON shape and deterministic property ordering.
- Preserve legacy `isUUNP` behavior while adding the optional `Profile` field.
- Add fixture-based load/save tests.

**Non-Goals:**
- No XML import workflow, morph UI, or file export commands beyond project save/load.
- No migration that rewrites or deletes legacy settings files.

## Decisions

- Use `System.Text.Json` with explicit DTOs and property-order attributes rather than serializing observable UI models directly.
- Convert DTOs into domain models after load so Java compatibility rules stay isolated from ViewModel state.
- Emit both `isUUNP` and `Profile` on save; older versions ignore `Profile`, and the port gets named profile fidelity.
- Keep missing-default slider omission in the serializer layer, because it is an on-disk compatibility rule rather than a UI concern.

## Risks / Trade-offs

- Byte-identical pretty printing may differ from Java minimal-json -> use the PRD-compatible 2-space shape and fixture expectations for accepted output.
- Dropping stale assignment references can hide bad input -> match Java silently but cover the behavior with tests.
- Profile names may drift -> centralize legacy name mapping in settings/profile services.

## Migration Plan

Existing `.jbs2bg` files load as-is. Saving from the port adds `Profile` while retaining `isUUNP`, preserving backward compatibility for v1 consumers.

## Open Questions

- Decide whether the first save should preserve exact original project property ordering if a fixture differs from the normalized port order.
