# Issue tracker: GitHub

GitHub Issues in `evildarkarchon/BS2BG` are the source of truth for specs, tickets, status, and comments. Run every `gh` command outside the sandbox and use `--repo evildarkarchon/BS2BG` to identify the target.

## Conventions

- Create a spec or implementation ticket with `gh issue create --repo evildarkarchon/BS2BG --title "..." --body-file <path>`. Use one issue per ticket and link related issues.
- Read an issue and its discussion with `gh issue view <number> --repo evildarkarchon/BS2BG --comments`.
- List issues with `gh issue list --repo evildarkarchon/BS2BG --state open --json number,title,body,labels`; add `--label` or change `--state` as needed.
- Update a body with `gh issue edit <number> --repo evildarkarchon/BS2BG --body-file <path>`; add discussion with `gh issue comment <number> --repo evildarkarchon/BS2BG --body-file <path>`.
- Apply triage labels from `triage-labels.md` with `gh issue edit <number> --repo evildarkarchon/BS2BG --add-label <label>`; remove an old role with `--remove-label <label>`.
- Close a completed issue with `gh issue close <number> --repo evildarkarchon/BS2BG --comment "..."`.

A bare `#<number>` can identify an issue or a pull request because GitHub shares their number space. Resolve the type before acting.

## Pull requests as a triage surface

**PRs as a request surface: no.** Set this to `yes` only if external pull requests should enter the triage queue.

## When a skill says "publish to the issue tracker"

Create a GitHub issue and return its URL.

## When a skill says "fetch the relevant ticket"

Read the GitHub issue, including its labels and comments.

## Local archive

The [Java 25 modernization ticket index](../../.scratch/java25-javafx25-modernization/README.md) maps the former local tickets to GitHub issues and retains their history. Use GitHub for current status and updates; consult the local files for migration and completion evidence.

## Wayfinding operations

Used by `/wayfinder`. A **map** is one GitHub issue with **child** issues as tickets.

- **Map**: create one issue labelled `wayfinder:map` with Notes, Decisions-so-far, and Fog in its body.
- **Child ticket**: link an issue as a GitHub sub-issue. If sub-issues are unavailable, add it to a task list in the map and put `Part of #<map>` in the child body. Label the child `wayfinder:<type>` (`research`, `prototype`, `grilling`, or `task`).
- **Blocking**: use GitHub's native issue dependencies. The `blocked_by` API takes the blocker's database `id`, not its issue number. If dependencies are unavailable, use a `Blocked by: #<n>, #<n>` line in the child body.
- **Frontier**: inspect open children in map order; choose the first unassigned child with no open blockers.
- **Claim**: assign the selected child to yourself before starting work.
- **Resolve**: comment with the answer, close the child, then add a short context pointer and link to Decisions-so-far in the map.
