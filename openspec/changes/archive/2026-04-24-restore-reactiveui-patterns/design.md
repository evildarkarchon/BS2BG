## Context

PRD §6 (lines 315–329) lays out an explicit ReactiveUI architecture for the App layer: `ReactiveObject` ViewModels, `[Reactive]` properties, `ReactiveCommand<TParam,TResult>` with `WhenAnyValue`-derived `canExecute`, derived state via `ToProperty`, and scheduling via `RxApp.MainThreadScheduler` / `RxApp.TaskpoolScheduler`. Implementation diverged: ViewModels still inherit `ReactiveObject`, but every command is hand-rolled (`RelayCommand`, `RelayCommandOfT`, `AsyncRelayCommand` in `src/BS2BG.App/ViewModels/`), `canExecute` is a `Func<bool>` requiring manual `RaiseCanExecuteChanged()`, properties use manual `RaiseAndSetIfChanged` setters, and aggregate state (`IsBusy` across child VMs) is composed imperatively.

`AsyncRelayCommand` does carry one capability worth preserving: it threads a `CancellationToken` to the command body and cancels the previous run when re-invoked (see `f6b7d17` and `7ae8005`). Any migration must keep that behavior.

The Window base class (`MainWindow` was migrated from `ReactiveWindow<T>` to plain `Window` in `2a0fb46`) is intentionally out of scope — current Avalonia 12 guidance favors plain `Window`, and the user has explicitly asked to leave that decision alone.

The PRD names `ReactiveUI.Fody` as the source of `[Reactive]`. In practice, `ReactiveUI.Fody` was abandoned at the 19.x line and has a hard transitive dependency on `ReactiveUI` 19.5.41, while the project's existing `ReactiveUI.Avalonia 12.0.1` reference brings in `ReactiveUI` 23.2.1. There is no Fody release that targets ReactiveUI 23.x, so we substitute `ReactiveUI.SourceGenerators` (see Decision 3) — it provides the same `[Reactive]` attribute surface and has no transitive ReactiveUI version of its own, so it composes cleanly with whatever ReactiveUI is already present.

## Goals / Non-Goals

**Goals:**
- All App-layer ViewModel commands are `ReactiveCommand` instances created via the static `ReactiveCommand.Create*` factories.
- Async commands use `ReactiveCommand.CreateFromTask((CancellationToken) => Task)` and preserve the per-invocation cancellation behavior currently provided by `AsyncRelayCommand`.
- Notifying properties use `[Reactive]` from `ReactiveUI.Fody` instead of hand-written `RaiseAndSetIfChanged` setters.
- `canExecute` and derived state are expressed as `WhenAnyValue(...)` / `Observable.CombineLatest(...)` chains, fed into `ReactiveCommand`'s `canExecute` parameter or surfaced as `ObservableAsPropertyHelper<T>`-backed properties.
- Background work runs on `RxApp.TaskpoolScheduler`; UI marshalling uses `RxApp.MainThreadScheduler`.
- The custom command files (`RelayCommand.cs`, `RelayCommandOfT.cs`, `AsyncRelayCommand.cs`) are deleted with no remaining callers.
- `CLAUDE.md` is updated so its "Target stack — actual usage" section reflects the restored patterns.

**Non-Goals:**
- Migrating `MainWindow` (or any other Window) back to `ReactiveWindow<T>`. Plain `Avalonia.Controls.Window` stays.
- Touching `BS2BG.Core` — Core has no ReactiveUI dependency and none is being introduced.
- Adding new features, refactoring unrelated code, or changing user-visible behavior. Goldens, AXAML files, and command keybindings are unaffected.
- Re-organizing the ViewModel folder structure or renaming ViewModels.
- Changing tests for reasons other than adapting to the new command shape.

## Decisions

### 1. `ReactiveCommand` over the custom `RelayCommand` family

`ReactiveCommand<TParam, TResult>` is the PRD's mandated type, integrates natively with the rest of ReactiveUI (`IsExecuting`, `ThrownExceptions`, `CanExecute` observables), and removes the need for hand-written `RaiseCanExecuteChanged()` plumbing. Alternatives considered:

- **Keep custom `RelayCommand`s, just align them with PRD intent.** Rejected: the value of using ReactiveCommand is the rest of the reactive graph (`IsExecuting` composing into `IsBusy`, `WhenAnyValue` driving `canExecute`); a wrapper that doesn't expose those observables defeats the purpose.
- **Use CommunityToolkit.Mvvm `RelayCommand` + `[ObservableProperty]`.** Rejected: that pulls in a second MVVM framework alongside ReactiveUI and conflicts with PRD §6's choice. The repo has already committed to ReactiveUI; we should use it.

### 2. Async cancellation: `ReactiveCommand.CreateFromTask((CancellationToken) => Task)`

This overload provides the same per-invocation cancellation `AsyncRelayCommand` offers today: when the command's subscription is disposed (or the next invocation supersedes the running one via `outputScheduler` discipline), the `CancellationToken` is cancelled and the task body sees it. We preserve the "cancel previous run on re-invoke" behavior by ensuring `canExecute` for those commands gates on `!IsExecuting` (so a re-invoke isn't possible mid-flight); when explicit interruption is needed, callers expose a separate `CancelCommand` that signals a `Subject<Unit>` the command body observes.

Alternative considered: keep `AsyncRelayCommand` as a thin shim over `ReactiveCommand`. Rejected — it perpetuates the divergence and duplicates `IsExecuting`/`ThrownExceptions` observables that `ReactiveCommand` already exposes.

### 3. `[Reactive]` via `ReactiveUI.SourceGenerators`

The PRD originally named `ReactiveUI.Fody`, but Fody never tracked past ReactiveUI 19.x and is incompatible with the ReactiveUI 23.2.1 already pulled in transitively by `ReactiveUI.Avalonia 12.0.1` (Fody pins `ReactiveUI = 19.5.41`). `ReactiveUI.SourceGenerators` is the supported modern path: same `[Reactive]` attribute usage, no IL weaving (Roslyn source generator instead), and no transitive ReactiveUI version constraint.

- **Manual `RaiseAndSetIfChanged` everywhere.** Rejected: that's what we have today; the boilerplate is exactly what `[Reactive]` removes.
- **`ReactiveUI.Fody`.** Rejected by version conflict; see paragraph above.
- **`ReactiveUI.SourceGenerators`.** Accepted. The PRD's intent (eliminate INPC boilerplate via `[Reactive]`) is preserved; only the implementing package changes.

### 4. Derived state via `ToProperty` (`ObservableAsPropertyHelper<T>`)

`IsBusy` and any other "computed from inputs" property becomes:

```csharp
private readonly ObservableAsPropertyHelper<bool> _isBusy;
public bool IsBusy => _isBusy.Value;

// in ctor:
_isBusy = Observable.CombineLatest(
        TemplatesViewModel.WhenAnyValue(x => x.IsBusy),
        MorphsViewModel.WhenAnyValue(x => x.IsBusy),
        SomeAsyncCommand.IsExecuting,
        (a, b, c) => a || b || c)
    .ToProperty(this, x => x.IsBusy);
```

This replaces the imperative aggregation introduced in `f6b7d17`. The reactive graph is the source of truth; nothing else has to remember to push.

### 5. Scheduler discipline

`ReactiveCommand` runs the supplied `Func<Task>` on the calling scheduler and observes results on `outputScheduler` (defaults to `RxApp.MainThreadScheduler`). For long-running CPU-bound work, the body explicitly does `await Task.Run(..., ct).ConfigureAwait(false)` or wraps the work in `.SubscribeOn(RxApp.TaskpoolScheduler)` for observable-returning paths. ViewModels do not call `Dispatcher.UIThread.InvokeAsync` — that was a code smell PRD line 322 calls out by name.

### 6. Migration ordering: ViewModel-by-ViewModel, leaf-first

Order of conversion (each is its own commit, builds and passes tests at every step):

1. Add `ReactiveUI.SourceGenerators` to `Directory.Packages.props` and reference it from `BS2BG.App.csproj`.
2. `SetSliderInspectorRowViewModel` — leaf VM, simplest commands.
3. `MorphsViewModel`, `TemplatesViewModel` — child VMs with their own `IsBusy` and async commands.
4. `MainWindowViewModel` — composes child `IsBusy` into the aggregate; this is where `Observable.CombineLatest` replaces the imperative aggregation.
5. Delete `RelayCommand.cs`, `RelayCommandOfT.cs`, `AsyncRelayCommand.cs` once no callers remain.
6. Update `CLAUDE.md`.

Rejected alternative: big-bang migration. A leaf-first sequence keeps each step verifiable and avoids a long-lived broken state.

### 7. Test adaptation

Existing ViewModel tests invoke commands via `command.Execute(null)` / `await command.ExecuteAsync(null)`. With `ReactiveCommand`, the equivalents are `await command.Execute().ToTask()` (sync) or `await command.Execute()` if it's a `ReactiveCommand<Unit, T>` returning a Task. Tests that asserted on `RaiseCanExecuteChanged` ordering will switch to subscribing to `command.CanExecute` and asserting the emitted sequence. No test should need to change *what* it verifies, only *how* it talks to the command.

## Risks / Trade-offs

- **[Risk] `ReactiveUI.SourceGenerators` source-generator output diverges from `[Reactive]` semantics on some property pattern** → Mitigation: pin a known-good version in `Directory.Packages.props`, run the existing test suite (which exercises every notifying property indirectly via VM tests) on Windows after the migration, and spot-check generated code via the IDE's "Go to source generator output" if a property's notifications look wrong.
- **[Risk] Subtle behavior change in cancellation semantics** (the custom `AsyncRelayCommand` cancels the *previous* run when re-invoked; `ReactiveCommand` by default refuses re-invocation while `IsExecuting`) → Mitigation: per Decision 2, gate `canExecute` on `!IsExecuting`, and where the old behavior was load-bearing (project open/save, file dialogs that the user might re-trigger), preserve it explicitly via a `CancelCommand` and a `Subject<Unit>` cancellation signal. Verify the affected commands by walking each former `AsyncRelayCommand` site and noting which behavior it relied on.
- **[Risk] `ToProperty` derived properties read as default until first emission** → Mitigation: use the `initialValue` overload of `ToProperty` for any property the View binds to before the first reactive push.
- **[Risk] Test churn larger than expected** → Mitigation: leaf-first order surfaces test impact early; if a single test file blows up, that's a signal to re-evaluate, not push through.
- **[Trade-off] Source-generator runs add a small build-time cost.** Roslyn source generators are cheaper than Fody IL weaving and the cost is negligible relative to the boilerplate reduction across every VM property.
- **[Trade-off] One more thing for new contributors to learn (`[Reactive]`, `WhenAnyValue`, `ToProperty`).** Mitigated by the new `reactive-mvvm-conventions` spec and the `CLAUDE.md` update — both give a clear contract instead of "read the PRD and figure it out."

## Migration Plan

The change ships in one branch, but the commits inside it follow Decision 6's ordering so each commit builds and tests pass. No flag, no shim, no parallel old/new path — once a VM is migrated, it's migrated. There is no production deployment surface; the rollback strategy is `git revert` of the merged change.

## Open Questions

- None blocking. The package swap from Fody to SourceGenerators happened during apply (see Decision 3). The `[Reactive]` attribute usage is identical; only the package reference and version pin differ.
