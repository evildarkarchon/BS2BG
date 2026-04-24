## 1. Command Shell

- [ ] 1.1 Add File and Help menus with PRD command labels and keybindings
- [ ] 1.2 Wire New, Open, Save, Save As, Export BoS JSON, Export BodyGen INIs, and About to shared ReactiveCommands
- [ ] 1.3 Add command availability rules based on project state and generated output state

## 2. Prompts And Dialog Services

- [ ] 2.1 Add Avalonia 12 storage-provider abstraction for files and folders
- [ ] 2.2 Add unsaved-change confirmation before New and Open
- [ ] 2.3 Add About dialog with required credits and app name

## 3. Export Services

- [ ] 3.1 Implement BodyGen INI export to `templates.ini` and `morphs.ini` with CRLF line endings
- [ ] 3.2 Implement BoS JSON folder export with sanitized filenames and original in-memory names
- [ ] 3.3 Add user notifications for success, empty output, and export failures

## 4. Parity Closure

- [ ] 4.1 Review PRD section 4 parity checklist and close any remaining M0-M5 gaps
- [ ] 4.2 Add integration tests for save/open/export and keybinding paths
- [ ] 4.3 Run `dotnet test`
