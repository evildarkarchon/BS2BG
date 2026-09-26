---
status: accepted
date: 2026-09-26
supersedes: ADR-0001 only for the ProjectSession entry used by partial-success NPC Database promotion
---

# Promote NPC Database rows through one ProjectSession batch

ADR-0001 places ordinary atomic Project edits behind `apply(ProjectEdit)`. NPC Database Add All has different
outcomes: valid rows must become independent NPC Morph Assignments while every duplicate and rejection remains
visible in the captured row order. Applying one edit per row repeatedly copies, sorts, and publishes a growing
Project. The existing atomic `addNpcs` edit instead rejects an invalid batch as a whole.

`ProjectSession.promoteNpcs` is a narrow synchronous batch operation for this partial-success workflow. It validates
and classifies the rows under the session lock, commits accepted assignments through one immutable Project build and
sort, and returns ordered row outcomes bound to the one final snapshot. This supersedes ADR-0001's single-`apply`
entry only for this workflow. Ordinary edits still use `apply(ProjectEdit)`, and the NPC Database remains independent
of Project state.

## Considered options

- Encoding row positions into `ProjectDiagnostic` locations through an `apply` edit would keep one entry point but
  make the caller reconstruct typed row outcomes from diagnostics and overload source-location meaning.
- Moving per-row edits to the job coordinator would free the JavaFX thread during the work but retain repeated
  Project copying, sorting, and publication. The bounded batch removes that cost at its source.
