## 1. Add ReactiveUI.SourceGenerators dependency

> Note: The proposal originally named `ReactiveUI.Fody`. During apply, Fody was found to be incompatible with `ReactiveUI.Avalonia 12.0.1` (which transitively requires ReactiveUI 23.x; Fody pins ReactiveUI 19.x). `ReactiveUI.SourceGenerators` is the modern substitute with the same `[Reactive]` attribute surface — see Decision 3 in `design.md`.

- [x] 1.1 Add `ReactiveUI.SourceGenerators` to `Directory.Packages.props` (pin to the latest stable, currently 2.6.1)
- [x] 1.2 Reference `ReactiveUI.SourceGenerators` from `src/BS2BG.App/BS2BG.App.csproj` (no `Version` attribute — central package management; mark with `PrivateAssets="all"` so the analyzer doesn't flow to consumers)
- [x] 1.3 No `FodyWeavers.xml` is needed — `ReactiveUI.SourceGenerators` is a Roslyn source generator, not an IL weaver
- [x] 1.4 Run `dotnet build BS2BG.sln` and confirm the App project builds cleanly with the source generator active

## 2. Migrate `SetSliderInspectorRowViewModel` (leaf VM)

- [x] 2.1 Convert all `RaiseAndSetIfChanged`-backed properties to `[Reactive]` auto-properties — no-op: `SetSliderInspectorRowViewModel` has zero `RaiseAndSetIfChanged` properties; it uses computed properties + a single `OnSliderPropertyChanged` handler that calls `RaisePropertyChanged` for each computed name. There is nothing to convert.
- [x] 2.2 Replace any `RelayCommand` / `AsyncRelayCommand` instances with `ReactiveCommand.Create*` factories; supply `canExecute` as `this.WhenAnyValue(...)` — no-op: this VM exposes zero `ICommand` properties.
- [x] 2.3 Update `SetSliderInspectorViewModelTests` to invoke commands via `await command.Execute()` and to assert on `command.CanExecute` observable emissions instead of `Func<bool>` returns — the only `Execute(null)` calls in `SetSliderInspectorViewModelTests.cs` (`SetAllSliderPercentsTo50Command`, `SetAllMinPercentsTo0Command`, `SetAllMaxPercentsTo100Command`) are against `TemplatesViewModel` properties, so they are addressed in section 4 alongside that VM's migration.
- [x] 2.4 Run `dotnet test --filter SetSliderInspector` and confirm green — baseline 5/5 pass; will re-run after §4.

## 3. Migrate `MorphsViewModel`

- [x] 3.1 Convert notifying properties to `[Reactive]`
- [x] 3.2 Replace each command with the appropriate `ReactiveCommand.Create*` factory; for the async commands previously using `AsyncRelayCommand`, use `ReactiveCommand.CreateFromTask((CancellationToken ct) => ...)` and gate `canExecute` on `!IsExecuting` per Decision 2 in `design.md`
- [x] 3.3 Replace the imperative `IsBusy` flag with an `ObservableAsPropertyHelper<bool>` driven by `Observable.CombineLatest(...)` over the relevant command `IsExecuting` streams
- [x] 3.4 Update `MorphsViewModelTests` to the new command invocation style
- [x] 3.5 Run `dotnet test --filter Morphs` and confirm green

## 4. Migrate `TemplatesViewModel`

- [x] 4.1 Convert notifying properties to `[Reactive]`
- [x] 4.2 Replace commands with `ReactiveCommand` equivalents; preserve cancellation behavior for any former `AsyncRelayCommand` usages by following Decision 2
- [x] 4.3 Replace imperative `IsBusy` aggregation with a `ToProperty`-backed observable
- [x] 4.4 Update `TemplatesViewModelTests` to the new command invocation style
- [x] 4.5 Run `dotnet test --filter Templates` and confirm green

## 5. Migrate `MainWindowViewModel`

- [x] 5.1 Convert notifying properties to `[Reactive]`
- [x] 5.2 Replace all commands with `ReactiveCommand` equivalents
- [x] 5.3 Replace the aggregate `IsBusy` (currently composed imperatively from `TemplatesViewModel.IsBusy` and `MorphsViewModel.IsBusy` per `f6b7d17`) with `Observable.CombineLatest(TemplatesViewModel.WhenAnyValue(x => x.IsBusy), MorphsViewModel.WhenAnyValue(x => x.IsBusy), <main commands>.IsExecuting, ...).ToProperty(...)` — implemented as the `IsAnyBusy` aggregate (the existing public name); the previous local-only `IsBusy` flag was retired.
- [x] 5.4 For the "disable main commands while any child view is busy" behavior (commit `7ae8005`), express the gate as `this.WhenAnyValue(x => x.IsBusy, busy => !busy)` fed into each affected command's `canExecute` — wired via the `notBusy` observable derived from the aggregate `IsAnyBusy` Subject.
- [x] 5.5 Update `MainWindowViewModelTests` to the new command invocation style; confirm the busy-aggregation scenarios still pass
- [x] 5.6 Run `dotnet test --filter MainWindow` and confirm green

## 6. Audit ViewModels for scheduler discipline

- [x] 6.1 Search `src/BS2BG.App/ViewModels/` for `Dispatcher.UIThread.InvokeAsync` and `Dispatcher.UIThread.Post`; remove each occurrence by routing the underlying logic through a `ReactiveCommand` body or an observable that uses `RxApp.MainThreadScheduler` / `RxApp.TaskpoolScheduler` — verified zero matches.
- [x] 6.2 Confirm no remaining `Task.Run` calls bypass the reactive scheduler chain in command bodies; where CPU-bound work is required, use `await Task.Run(..., ct).ConfigureAwait(false)` from inside the `ReactiveCommand.CreateFromTask` body so the cancellation token still flows — all 5 `Task.Run` call sites are inside `CreateFromTask` bodies and pass the cancellation token.
- [x] 6.3 Run a repo-wide grep for `Dispatcher.UIThread` under `src/BS2BG.App/ViewModels/` to confirm zero matches (spec scenario "ViewModels do not invoke Dispatcher.UIThread directly") — confirmed.

## 7. Delete the custom command infrastructure

- [x] 7.1 Delete `src/BS2BG.App/ViewModels/RelayCommand.cs`
- [x] 7.2 Delete `src/BS2BG.App/ViewModels/RelayCommandOfT.cs`
- [x] 7.3 Delete `src/BS2BG.App/ViewModels/AsyncRelayCommand.cs`
- [x] 7.4 Run `dotnet build BS2BG.sln` and confirm zero references to the deleted types remain (the build should succeed without warnings about missing types)
- [x] 7.5 Flip `src/BS2BG.App/Views/MainWindow.axaml.cs` to inherit `Avalonia.Controls.Window` (commit `2a0fb46` updated only the `.axaml` root tag; the code-behind still inherited `ReactiveWindow<MainWindowViewModel>`, which made the spec scenario "MainWindow inherits from plain Avalonia Window" fail). Replaced `ReactiveWindow` base with `Window`, dropped the `using ReactiveUI.Avalonia;` import, and exposed a typed `ViewModel` getter for existing test/view callers.

## 8. Verify the full suite and update CLAUDE.md

- [x] 8.1 Run `dotnet build BS2BG.sln` — must succeed with no warnings introduced by the migration
- [x] 8.2 Run `dotnet test` — full suite must pass, including golden-file tests in `BS2BG.Tests` (golden outputs are unaffected by this change; any regression is a real bug) — 151/151 pass; the previous 153 included two `AsyncRelayCommand` tests that were deleted with the type itself.
- [ ] 8.3 Smoke-test the App: `dotnet run --project src/BS2BG.App/BS2BG.App.csproj`. Verify (a) main window opens, (b) busy-state disables main commands during a long-running operation, (c) async command cancellation still works (e.g. cancel a project open mid-flight) — left for the user to run manually before archiving (requires interactive UI).
- [x] 8.4 Update `CLAUDE.md` "Target stack — actual usage": replace the "ReactiveUI usage is minimal in practice" paragraph with the restored guidance — `ReactiveCommand`, `[Reactive]` (via `ReactiveUI.SourceGenerators`), `WhenAnyValue`, `ToProperty`, scheduler usage. Keep the "plain Window, not ReactiveWindow" note and update it to reflect that the code-behind now actually matches.

## 9. Spec verification

- [x] 9.1 Re-read `openspec/changes/restore-reactiveui-patterns/specs/reactive-mvvm-conventions/spec.md` and walk each scenario against the implementation; all SHALL/SHALL NOT statements must hold — verified each scenario:
    - "No custom command types remain in the App ViewModels" — `class RelayCommand` / `class AsyncRelayCommand` grep returns zero matches under `src/BS2BG.App/`.
    - "ViewModel commands resolve to ReactiveCommand at runtime" — every command property in the four ViewModels is typed `ReactiveCommand<TParam, TResult>`.
    - "Disposing an in-flight async command invocation cancels its task" — async commands use `ReactiveCommand.CreateFromTask((CancellationToken) => Task)` with the token threaded into the body.
    - "Property change updates CanExecute without manual re-raise" — every `canExecute` is an `IObservable<bool>` derived from `this.WhenAnyValue(...)` / `Observable.CombineLatest(...)`.
    - "ReactiveUI.SourceGenerators is referenced from BS2BG.App" — `Directory.Packages.props` pins `2.6.1`; `BS2BG.App.csproj` references it with `PrivateAssets="all"`.
    - "Assigning a [Reactive] property raises PropertyChanged" — `[Reactive]` field-syntax generates standard ReactiveUI INPC setters; verified by the existing tests that subscribe to PropertyChanged.
    - "Aggregate IsBusy reflects child busy state without imperative refresh" — `MainWindowViewModel.IsAnyBusy` is `[ObservableAsProperty]` driven by `Observable.CombineLatest` over `Templates.IsBusy`, `Morphs.IsBusy`, and the six main async commands' `IsExecuting`. No manual `RaisePropertyChanged(nameof(IsAnyBusy))` calls remain.
    - "ViewModels do not invoke Dispatcher.UIThread directly" — repo-wide grep under `src/BS2BG.App/ViewModels/` returns zero matches.
    - "MainWindow inherits from plain Avalonia Window" — `MainWindow.axaml.cs` declares `partial class MainWindow : Window`.
- [x] 9.2 Run `openspec verify --change restore-reactiveui-patterns` and address any reported gaps before archiving — `openspec validate restore-reactiveui-patterns` reports the change as valid.
