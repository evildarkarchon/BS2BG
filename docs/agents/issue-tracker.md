# Issue tracker: Local Markdown

Issues and specs for this repo live as Markdown files in `.scratch/`.

## Conventions

- One feature per directory: `.scratch/<feature-slug>/`.
- The spec is `.scratch/<feature-slug>/spec.md`.
- Implementation issues are one file per ticket at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01`. Allocate the next unused number within the feature; preserve existing numbers and files.
- Record triage state as a `Status:` line near the top of each issue file, using the role strings in `triage-labels.md`. New untriaged issues start with `Status: needs-triage`.
- Append comments and conversation history at the bottom of the file under a `## Comments` heading.
- Complete a ticket by recording the result and setting `Status: resolved`. Retain the file as history. Use `Status: wontfix` for work that will not be actioned.
- Reference tickets by repository-relative path, since numbers are local to each feature.

## When a skill says "publish to the issue tracker"

Create the spec or individual issue files at the paths above, creating directories as needed. Return the paths to the created files.

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path, including its comments. Resolve a bare ticket number within the named feature; if multiple features match and the context does not identify one, ask which feature the user means.

## Existing GitHub references

Existing GitHub issues remain historical references; this configuration change does not migrate or close them. Fetch explicitly referenced GitHub issues with `gh issue view <number> --repo evildarkarchon/BS2BG --comments`, running `gh` outside the sandbox. New tracker work uses local Markdown. External pull requests are not a triage request surface.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a file with one **child** file per ticket.

- **Map**: `.scratch/<effort>/map.md` (the Notes / Decisions-so-far / Fog body).
- **Child ticket**: `.scratch/<effort>/issues/<NN>-<slug>.md`, numbered from `01`, with the question in the body. A `Type:` line records `research`, `prototype`, `grilling`, or `task`. Wayfinding tickets use `Status: open`, `Status: claimed`, or `Status: resolved` for execution state; these are separate from the triage roles.
- **Blocking**: a `Blocked by: NN, NN` line near the top refers to tickets in the same effort. A ticket is unblocked when every referenced ticket is `resolved`; a missing blocker remains unresolved.
- **Frontier**: scan the effort's `issues/` directory for tickets with `Status: open` and no unresolved blockers; select the lowest number.
- **Claim**: set `Status: claimed` and save before any work.
- **Resolve**: append the answer under an `## Answer` heading, set `Status: resolved`, then append a context pointer (gist + relative link) to Decisions-so-far in `map.md`.
