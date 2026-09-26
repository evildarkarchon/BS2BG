---
status: accepted
date: 2026-08-29
supersedes: ADR-0002 only where it freezes the external ProjectSession and ProjectSnapshot surface
---

# Long Project operations expose context and opaque content versions

ADR-0002 deliberately kept `ProjectSession` and `ProjectSnapshot` unchanged while extracting the internal
`Project` aggregate. The accepted background-job decision in issue #69 later requires a narrow reopening of those
external seams: synchronous long-running Project operations must accept a JavaFX-independent cancellation,
progress, and commit context, and immutable snapshots must carry an opaque, session-scoped content version for
freshness checks. This decision resolves that conflict explicitly. It does not reopen the aggregate boundary or
expose mutable Project state.

`ProjectSession` retains its original Open, Save, Save As, and BodySlide-import methods as convenience defaults.
Context-bearing overloads are the implementation boundary used by the application-wide job coordinator. New and
ordinary `apply` edits remain synchronous and unchanged. `ProjectSnapshot` adds only `ProjectContentVersion`, an
equality token with no public counter or mutation API. The version advances for New, successful Open, and actual
Project-content changes, but not for save-only lifecycle changes or unchanged, rejected, failed, or cancelled work.

Open reads, parses, and validates a detached candidate without holding the session publication lock. It then takes
that lock for the freshness/cancellation decision and atomic Project swap. The Workbench also captures lightweight
filesystem identity for the selected source; if the source or Project content version changes before publication,
the candidate is stale and commits no effect. Retry recaptures both inputs as a new linked attempt.

## Considered options

- Keeping ADR-0002's external surface frozen was rejected because cancellation safe points hidden inside a
  presentation wrapper cannot protect the session's filesystem commit boundaries.
- Exposing a numeric revision was rejected because callers need only equality and must not infer ordering across
  independent sessions.
- Holding the session lock during Open I/O was rejected because it prevents safe immediate work and makes the
  freshness check vacuous; detached construction plus one atomic publication boundary preserves thread safety.
- Hashing the selected source on the JavaFX lane was rejected because it would move file I/O back onto the UI
  thread. Basic filesystem identity is captured cheaply, while the worker remains the sole owner of document bytes.

## Consequences

- `ProjectSession` remains synchronous, thread-safe, and JavaFX-independent, but long operations now cooperate with
  one application-level coordinator.
- Existing callers retain non-cancellable behavior without constructing a context.
- Content freshness is explicit and testable without exposing the internal aggregate or sharing mutable state.
- ADR-0002 remains accepted for `Project` ownership, integrity, ordering, and package visibility; only its statement
  that the external session/snapshot interfaces do not change is superseded.
