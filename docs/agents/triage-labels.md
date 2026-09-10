# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the `Status:` values used in this repo's local Markdown issue tracker.

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role or applies a triage label, set the ticket's `Status:` line to the corresponding value from this table.

Edit the right-hand column if the repository's triage vocabulary changes. Execution states such as `open`, `claimed`, and `resolved` are defined in `issue-tracker.md` and are not triage roles.
