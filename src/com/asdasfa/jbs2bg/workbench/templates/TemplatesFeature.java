package com.asdasfa.jbs2bg.workbench.templates;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.FilterColumn;
import com.asdasfa.jbs2bg.filtering.FilteredView;
import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.filtering.SortKey;
import com.asdasfa.jbs2bg.project.CancelledOutcome;
import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

/**
 * JavaFX-independent Templates Area state machine. It renders immutable Slider Preset frames and translates
 * task-oriented intents into edits submitted only through the authoritative Workbench Project flow.
 */
public final class TemplatesFeature {
    private static final String NAME_COLUMN = "name";
    private static final Duration TYPE_AHEAD_TIMEOUT = Duration.ofMillis(750);

    private final WorkbenchProjectFlow projectFlow;
    private final Clock clock;
    private final Consumer<Throwable> observerFailureSink;
    private final Map<Long, Consumer<Frame>> observers = new LinkedHashMap<>();
    private final FilteredView<SliderPresetSnapshot, NameIdentity> view = new FilteredView<>(
            List.of(FilterColumn.of(NAME_COLUMN, preset -> preset.getName().toLowerCase(Locale.ROOT))),
            ProjectIdentities::sliderPreset);
    private List<SliderPresetSnapshot> sourcePresets = List.of();
    private String filterText = "";
    private SortOrder sortOrder = SortOrder.NAME_ASCENDING;
    private String typeAheadPrefix = "";
    private Instant lastTypeAhead;
    private RenameState rename;
    private long revision;
    private long nextObserverId = 1;
    private long nextEffectToken = 1;
    private Frame frame;
    private Effect pendingEffect;
    private boolean publishing;

    /**
     * Creates the window-scoped Templates feature from the latest coherent Project publication.
     *
     * @param projectFlow sole authoritative Project command path
     * @param clock       deterministic source for later type-ahead timing
     */
    public TemplatesFeature(WorkbenchProjectFlow projectFlow, Clock clock) {
        this(projectFlow, clock, failure -> {
            // Production adapters may supply technical diagnostics; the default keeps a renderer failure isolated.
        });
    }

    /**
     * Creates the Templates feature with an explicit technical-diagnostics sink for isolated observer failures.
     *
     * @param projectFlow         sole authoritative Project command path
     * @param clock               deterministic type-ahead clock
     * @param observerFailureSink receives observer failures after frame state commits
     */
    public TemplatesFeature(WorkbenchProjectFlow projectFlow, Clock clock,
                            Consumer<Throwable> observerFailureSink) {
        this.projectFlow = Objects.requireNonNull(projectFlow, "projectFlow");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.observerFailureSink = Objects.requireNonNull(observerFailureSink, "observerFailureSink");
        reconcile(projectFlow.frame(), List.of());
    }

    /**
     * @return the latest completely reconciled immutable Templates frame
     */
    public Frame frame() {
        return frame;
    }

    /**
     * Observes subsequent immutable frame publications until the returned subscription is closed.
     *
     * @param observer frame callback invoked after state commit
     * @return idempotent subscription handle
     */
    public Subscription observe(Consumer<Frame> observer) {
        long id = nextObserverId++;
        observers.put(id, Objects.requireNonNull(observer, "observer"));
        return () -> observers.remove(id);
    }

    /**
     * Applies one feature-specific task intent on the serialized presentation lane.
     *
     * @param intent immutable user task
     * @return whether the task was accepted and the resulting immutable frame
     */
    public Update dispatch(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (publishing)
            throw new IllegalStateException("Templates intents cannot be dispatched during frame publication");
        if (pendingEffect != null)
            return new Update(false, frame);
        return switch (intent) {
            case Select select -> select(select.identity());
            case Create create -> create(create.name());
            case ChangeFilter changeFilter -> changeFilter(changeFilter.text());
            case ChangeSort changeSort -> changeSort(changeSort.order());
            case TypeAhead typeAhead -> typeAhead(typeAhead.character());
            case Duplicate duplicate -> duplicate(duplicate.name());
            case BeginRename ignored -> beginRename();
            case ChangeRename changeRename -> changeRename(changeRename.draft());
            case CommitRename ignored -> commitRename();
            case RequestRemove ignored -> requestRemove();
            case RequestClearVisible ignored -> requestClearVisible();
            case CancelRename ignored -> cancelRename();
            case ClearSelection ignored -> clearSelection();
            case DismissDiagnostics ignored -> dismissDiagnostics();
        };
    }

    /**
     * Reconciles a later kernel Project publication while retaining filter and sort state.
     *
     * @param projectFrame   latest coherent Project frame
     * @param resetSelection whether lifecycle navigation requires drafts and selection to be cleared
     * @return accepted immutable feature update
     */
    public Update acceptProjectFrame(WorkbenchProjectFlow.Frame projectFrame, boolean resetSelection) {
        Objects.requireNonNull(projectFrame, "projectFrame");
        if (projectFrame.sequence() == frame.projectSequence())
            return new Update(true, frame);
        if (resetSelection) {
            rename = null;
            view.clearSelection();
        }
        reconcile(projectFrame, projectFrame.diagnostics());
        return new Update(true, frame);
    }

    /**
     * Completes a matching tokenized destructive confirmation before any captured identity can be edited.
     *
     * @param token     pending effect token
     * @param confirmed whether the user chose the destructive action
     * @return accepted response and resulting frame, or the unchanged frame for a stale token
     */
    public Update respond(long token, boolean confirmed) {
        if (pendingEffect == null || pendingEffect.token() != token)
            return new Update(false, frame);
        Effect effect = pendingEffect;
        pendingEffect = null;
        if (!confirmed)
            return new Update(true, frame);
        return switch (effect.kind()) {
            case CONFIRM_REMOVE -> remove(effect.identities().get(0));
            case CONFIRM_CLEAR_VISIBLE -> clearVisible(effect.identities());
        };
    }

    /**
     * Applies case-insensitive contains filtering while letting FilteredView enforce selection visibility.
     */
    private Update changeFilter(String text) {
        filterText = Objects.requireNonNull(text, "text");
        applyFilter();
        if (view.getSelection().isEmpty())
            rename = null;
        publish(frame.projectSequence(), List.of());
        return new Update(true, frame);
    }

    /**
     * Changes presentation order without changing membership or logical selection.
     */
    private Update changeSort(SortOrder order) {
        sortOrder = Objects.requireNonNull(order, "order");
        applySort();
        publish(frame.projectSequence(), List.of());
        return new Update(true, frame);
    }

    /**
     * Selects by a timed visible-order prefix; repeated identical characters cycle matching rows.
     */
    private Update typeAhead(char character) {
        if (Character.isISOControl(character))
            return new Update(false, frame);
        Instant now = clock.instant();
        String typed = String.valueOf(character).toLowerCase(Locale.ROOT);
        boolean expired = lastTypeAhead == null || now.isBefore(lastTypeAhead)
                || Duration.between(lastTypeAhead, now).compareTo(TYPE_AHEAD_TIMEOUT) > 0;
        boolean repeated = !expired && typeAheadPrefix.length() == 1 && typeAheadPrefix.equals(typed);
        typeAheadPrefix = expired || repeated ? typed : typeAheadPrefix + typed;
        lastTypeAhead = now;

        List<SliderPresetSnapshot> visible = view.visibleSet().getRows();
        int start = repeated ? selectedIndex(visible) + 1 : 0;
        for (int offset = 0; offset < visible.size(); offset++) {
            SliderPresetSnapshot candidate = visible.get((start + offset) % visible.size());
            if (candidate.getName().toLowerCase(Locale.ROOT).startsWith(typeAheadPrefix)) {
                view.select(ProjectIdentities.sliderPreset(candidate));
                publish(frame.projectSequence(), List.of());
                return new Update(true, frame);
            }
        }
        publish(frame.projectSequence(), List.of());
        return new Update(false, frame);
    }

    /**
     * Resolves the current selection inside one visible frame without retaining a row index as identity.
     */
    private int selectedIndex(List<SliderPresetSnapshot> visible) {
        Optional<NameIdentity> selected = view.getSelection();
        if (selected.isEmpty())
            return -1;
        for (int index = 0; index < visible.size(); index++)
            if (ProjectIdentities.sliderPreset(visible.get(index)).equals(selected.orElseThrow()))
                return index;
        return -1;
    }

    /**
     * Selects only an identity that is present in the accepted visible frame.
     */
    private Update select(NameIdentity identity) {
        if (!view.select(Objects.requireNonNull(identity, "identity")))
            return new Update(false, frame);
        publish(frame.projectSequence(), frame.diagnostics());
        return new Update(true, frame);
    }

    /**
     * Submits one raw name through ProjectSession validation, then selects the canonical returned identity.
     */
    private Update create(String name) {
        ProjectOutcome outcome = projectFlow.apply(SliderPresetEdits.create(name));
        boolean accepted = accepted(outcome);
        reconcile(projectFlow.frame(), outcome.getDiagnostics());
        if (accepted) {
            String requestedIdentity = Objects.requireNonNull(name, "name").trim();
            view.select(NameIdentity.of(requestedIdentity));
            publish(projectFlow.frame().sequence(), outcome.getDiagnostics());
        }
        return new Update(accepted, frame);
    }

    /**
     * Duplicates the selected logical identity and selects the canonical returned copy when validation accepts it.
     */
    private Update duplicate(String name) {
        Optional<NameIdentity> source = view.getSelection();
        if (source.isEmpty())
            return new Update(false, frame);
        ProjectOutcome outcome = projectFlow.apply(SliderPresetEdits.duplicate(
                source.orElseThrow().getName(), name));
        boolean accepted = accepted(outcome);
        reconcile(projectFlow.frame(), outcome.getDiagnostics());
        if (accepted) {
            view.select(NameIdentity.of(Objects.requireNonNull(name, "name").trim()));
            publish(projectFlow.frame().sequence(), outcome.getDiagnostics());
        }
        return new Update(accepted, frame);
    }

    /**
     * Opens inline rename for the selected identity using its latest display casing.
     */
    private Update beginRename() {
        Optional<NameIdentity> selected = view.getSelection();
        if (selected.isEmpty())
            return new Update(false, frame);
        SliderPresetSnapshot preset = sourcePresets.stream()
                .filter(candidate -> ProjectIdentities.sliderPreset(candidate).equals(selected.orElseThrow()))
                .findFirst().orElse(null);
        if (preset == null)
            return new Update(false, frame);
        rename = new RenameState(ProjectIdentities.sliderPreset(preset), preset.getName(), List.of());
        publish(frame.projectSequence(), List.of());
        return new Update(true, frame);
    }

    /**
     * Replaces only the inline draft; Project validation remains deferred until commit.
     */
    private Update changeRename(String draft) {
        if (rename == null)
            return new Update(false, frame);
        rename = new RenameState(rename.identity(), Objects.requireNonNull(draft, "draft"), List.of());
        publish(frame.projectSequence(), List.of());
        return new Update(true, frame);
    }

    /**
     * Commits the raw draft through the Project flow, retaining rejected validation beside the same inline editor.
     */
    private Update commitRename() {
        if (rename == null)
            return new Update(false, frame);
        RenameState pending = rename;
        ProjectOutcome outcome = projectFlow.apply(SliderPresetEdits.rename(
                pending.identity().getName(), pending.draft()));
        boolean accepted = accepted(outcome);
        if (accepted)
            rename = null;
        else
            rename = new RenameState(pending.identity(), pending.draft(), outcome.getDiagnostics());
        reconcile(projectFlow.frame(), outcome.getDiagnostics());
        if (accepted) {
            view.select(NameIdentity.of(pending.draft().trim()));
            publish(projectFlow.frame().sequence(), outcome.getDiagnostics());
        }
        return new Update(accepted, frame);
    }

    /**
     * Cancels inline rename without changing the Project and keeps the selected identity focused by the adapter.
     */
    private Update cancelRename() {
        if (rename == null)
            return new Update(false, frame);
        rename = null;
        publish(frame.projectSequence(), List.of());
        return new Update(true, frame);
    }

    /**
     * Clears the stable logical selection and any draft targeting it.
     */
    private Update clearSelection() {
        if (view.getSelection().isEmpty() && rename == null)
            return new Update(false, frame);
        rename = null;
        view.clearSelection();
        publish(frame.projectSequence(), List.of());
        return new Update(true, frame);
    }

    /**
     * Dismisses only pane-local validation while retaining Project state, selection, and inline rename diagnostics.
     */
    private Update dismissDiagnostics() {
        if (frame.diagnostics().isEmpty())
            return new Update(false, frame);
        publish(frame.projectSequence(), List.of());
        return new Update(true, frame);
    }

    /**
     * Freezes the selected identity and its relationship impact before requesting a destructive confirmation.
     */
    private Update requestRemove() {
        Optional<NameIdentity> selected = view.getSelection();
        if (selected.isEmpty())
            return new Update(false, frame);
        NameIdentity identity = selected.orElseThrow();
        int references = relationshipCount(identity);
        Effect effect = new Effect(nextEffectToken++, EffectKind.CONFIRM_REMOVE, List.of(identity),
                "Remove Slider Preset " + identity.getName() + "?",
                "This also removes " + references + (references == 1 ? " Project reference." : " Project references."));
        pendingEffect = effect;
        return new Update(true, frame, Optional.of(effect));
    }

    /**
     * Freezes the entire accepted visible identity set before requesting filtered destructive confirmation.
     */
    private Update requestClearVisible() {
        List<NameIdentity> identities = view.visibleSet().getIdentities();
        if (identities.isEmpty())
            return new Update(false, frame);
        int references = identities.stream().mapToInt(this::relationshipCount).sum();
        Effect effect = new Effect(nextEffectToken++, EffectKind.CONFIRM_CLEAR_VISIBLE, identities,
                "Clear visible Slider Presets?",
                "This removes " + identities.size() + (identities.size() == 1 ? " visible Slider Preset and "
                        : " visible Slider Presets and ") + references
                        + (references == 1 ? " Project reference." : " Project references."));
        pendingEffect = effect;
        return new Update(true, frame, Optional.of(effect));
    }

    /**
     * Removes one previously captured identity and relies on the immutable Project aggregate for relationship cascade.
     */
    private Update remove(NameIdentity identity) {
        ProjectOutcome outcome = projectFlow.apply(SliderPresetEdits.delete(identity.getName()));
        boolean accepted = accepted(outcome);
        reconcile(projectFlow.frame(), outcome.getDiagnostics());
        return new Update(accepted, frame);
    }

    /**
     * Removes the frozen visible identity set through one authoritative bulk edit.
     */
    private Update clearVisible(List<NameIdentity> identities) {
        ProjectOutcome outcome = projectFlow.apply(SliderPresetEdits.deleteAll(
                identities.stream().map(NameIdentity::getName).toList()));
        boolean accepted = accepted(outcome);
        reconcile(projectFlow.frame(), outcome.getDiagnostics());
        return new Update(accepted, frame);
    }

    /**
     * Counts relationships affected by one destructive operation from the same immutable snapshot used for capture.
     */
    private int relationshipCount(NameIdentity identity) {
        return (int) java.util.stream.Stream.concat(
                projectFlow.frame().snapshot().getCustomMorphTargets().stream()
                        .flatMap(target -> target.getSliderPresetNames().stream()),
                projectFlow.frame().snapshot().getNpcMorphAssignments().stream()
                        .flatMap(npc -> npc.getSliderPresetNames().stream()))
                .filter(name -> NameIdentity.of(name).equals(identity))
                .count();
    }

    /**
     * Replaces rows from one coherent Project publication before exposing the next feature frame.
     */
    private void reconcile(WorkbenchProjectFlow.Frame projectFrame, List<ProjectDiagnostic> diagnostics) {
        sourcePresets = Objects.requireNonNull(projectFrame, "projectFrame").snapshot().getSliderPresets();
        view.setRows(sourcePresets);
        applyFilter();
        if (view.getSelection().isEmpty())
            rename = null;
        applySort();
        publish(projectFrame.sequence(), diagnostics);
    }

    /**
     * Rebuilds the exact-value exclusion criterion whenever either the immutable Project rows or query changes.
     * New Project values therefore cannot bypass a retained text filter.
     */
    private void applyFilter() {
        String query = filterText.toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            view.clearCriterion(NAME_COLUMN);
            return;
        }
        List<String> hidden = sourcePresets.stream()
                .map(SliderPresetSnapshot::getName)
                .filter(name -> !name.toLowerCase(Locale.ROOT).contains(query))
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
        view.setCriterion(ColumnCriterion.hiding(NAME_COLUMN, hidden));
    }

    /**
     * Applies the retained single-column order to the logical view.
     */
    private void applySort() {
        view.setSortOrder(List.of(sortOrder == SortOrder.NAME_ASCENDING
                ? SortKey.ascending(NAME_COLUMN)
                : SortKey.descending(NAME_COLUMN)));
    }

    /**
     * Commits frame state before any caller can realize later focus or dialog effects.
     */
    private void publish(long projectSequence, List<ProjectDiagnostic> diagnostics) {
        frame = new Frame(++revision, projectSequence, view.visibleSet().getRows(), view.getSelection(), filterText,
                sortOrder, Optional.ofNullable(rename), diagnostics);
        publishing = true;
        try {
            for (Consumer<Frame> observer : List.copyOf(observers.values())) {
                try {
                    observer.accept(frame);
                } catch (RuntimeException failure) {
                    reportObserverFailure(failure);
                }
            }
        } finally {
            publishing = false;
        }
    }

    /**
     * Keeps both an observer and an optional technical-diagnostics adapter from breaking publication.
     */
    private void reportObserverFailure(RuntimeException failure) {
        try {
            observerFailureSink.accept(failure);
        } catch (RuntimeException sinkFailure) {
            // A technical-diagnostics sink is observational and must never become a second publication failure.
        }
    }

    /**
     * Distinguishes usable domain outcomes from typed cancellation, failure, and validation rejection.
     */
    private static boolean accepted(ProjectOutcome outcome) {
        return !(outcome instanceof CancelledOutcome) && !(outcome instanceof FailedOutcome)
                && !(outcome instanceof RejectedOutcome);
    }

    /**
     * Closed family of task-oriented Templates intents.
     */
    public sealed interface Intent permits Select, Create, ChangeFilter, ChangeSort, TypeAhead, Duplicate,
            BeginRename, ChangeRename, CommitRename, RequestRemove, RequestClearVisible, CancelRename,
            ClearSelection, DismissDiagnostics {
    }

    /**
     * Selects one case-insensitive Slider Preset identity when it is visible.
     *
     * @param identity logical identity, independent of list index
     */
    public record Select(NameIdentity identity) implements Intent {
        /** Requires a complete logical identity. */
        public Select {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /**
     * Creates one Slider Preset through authoritative Project validation.
     *
     * @param name raw user-entered display name
     */
    public record Create(String name) implements Intent {
    }

    /**
     * Duplicates the selected Slider Preset under a requested display name.
     *
     * @param name raw user-entered duplicate name
     */
    public record Duplicate(String name) implements Intent {
    }

    /**
     * Replaces the case-insensitive contains filter for Slider Preset display names.
     *
     * @param text raw filter field contents
     */
    public record ChangeFilter(String text) implements Intent {
    }

    /**
     * Replaces the Slider Preset name sort order.
     *
     * @param order requested presentation order
     */
    public record ChangeSort(SortOrder order) implements Intent {
        /** Requires a complete sort choice. */
        public ChangeSort {
            Objects.requireNonNull(order, "order");
        }
    }

    /**
     * Adds one character to the deterministic type-ahead interaction.
     *
     * @param character typed character
     */
    public record TypeAhead(char character) implements Intent {
    }

    /**
     * Begins inline rename for the currently selected Slider Preset.
     */
    public record BeginRename() implements Intent {
    }

    /**
     * Replaces the inline rename draft without mutating the Project.
     *
     * @param draft raw text field contents
     */
    public record ChangeRename(String draft) implements Intent {
    }

    /**
     * Commits the current inline rename draft through authoritative validation.
     */
    public record CommitRename() implements Intent {
    }

    /**
     * Requests confirmation to remove the selected Slider Preset and every Project relationship to it.
     */
    public record RequestRemove() implements Intent {
    }

    /**
     * Requests confirmation to remove exactly the current visible Slider Preset identity set.
     */
    public record RequestClearVisible() implements Intent {
    }

    /**
     * Cancels the active inline rename draft.
     */
    public record CancelRename() implements Intent {
    }

    /**
     * Clears Templates selection after the Esc transient-surface cascade is exhausted.
     */
    public record ClearSelection() implements Intent {
    }

    /**
     * Dismisses the current Templates pane validation message.
     */
    public record DismissDiagnostics() implements Intent {
    }

    /**
     * User-selectable Slider Preset presentation orders.
     */
    public enum SortOrder {
        NAME_ASCENDING("Name (A to Z)"),
        NAME_DESCENDING("Name (Z to A)");

        private final String displayName;

        SortOrder(String displayName) {
            this.displayName = displayName;
        }

        /**
         * @return stable user-facing sort description
         */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Durable inline rename state rendered beneath exactly one logical Slider Preset row.
     *
     * @param identity    stable identity being renamed
     * @param draft       current raw field contents
     * @param diagnostics structured validation from the last rejected commit
     */
    public record RenameState(NameIdentity identity, String draft, List<ProjectDiagnostic> diagnostics) {
        /** Defensively owns the inline validation collection. */
        public RenameState {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(draft, "draft");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /**
     * Tokenized destructive effect realized only after its immutable feature frame has committed.
     *
     * @param token      positive response token
     * @param kind       destructive operation kind
     * @param identities frozen logical operands
     * @param title      accessible confirmation title
     * @param message    non-color description of the destructive consequence
     */
    public record Effect(long token, EffectKind kind, List<NameIdentity> identities, String title, String message) {
        /** Defensively owns every captured confirmation value. */
        public Effect {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(kind, "kind");
            identities = List.copyOf(Objects.requireNonNull(identities, "identities"));
            if (identities.isEmpty())
                throw new IllegalArgumentException("identities must not be empty");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * Destructive confirmation families owned by Templates.
     */
    public enum EffectKind {
        CONFIRM_REMOVE,
        CONFIRM_CLEAR_VISIBLE
    }

    /**
     * Immutable render input for the Templates JavaFX adapter.
     *
     * @param revision       monotonic feature publication sequence
     * @param projectSequence Project frame sequence used to derive this feature frame
     * @param visiblePresets Slider Presets in current visible presentation order
     * @param selection      selected stable logical identity, when still visible
     * @param filterText     retained filter field contents
     * @param sortOrder      retained name presentation order
     * @param rename         inline rename draft and validation, when active
     * @param diagnostics    structured diagnostics from the most recent feature edit
     */
    public record Frame(long revision, long projectSequence, List<SliderPresetSnapshot> visiblePresets,
                        Optional<NameIdentity> selection, String filterText, SortOrder sortOrder,
                        Optional<RenameState> rename, List<ProjectDiagnostic> diagnostics) {
        /** Defensively owns all immutable frame collections and optionals. */
        public Frame {
            if (revision <= 0)
                throw new IllegalArgumentException("revision must be positive");
            if (projectSequence <= 0)
                throw new IllegalArgumentException("projectSequence must be positive");
            visiblePresets = List.copyOf(Objects.requireNonNull(visiblePresets, "visiblePresets"));
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(filterText, "filterText");
            Objects.requireNonNull(sortOrder, "sortOrder");
            Objects.requireNonNull(rename, "rename");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /**
     * Result of dispatching one Templates intent.
     *
     * @param accepted whether the intent was accepted
     * @param frame    current immutable frame, changed or unchanged
     */
    public record Update(boolean accepted, Frame frame, Optional<Effect> effect) {
        /** Creates an update without a platform effect. */
        private Update(boolean accepted, Frame frame) {
            this(accepted, frame, Optional.empty());
        }

        /** Requires a coherent current frame for both accepted and rejected tasks. */
        public Update {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(effect, "effect");
        }
    }

    /**
     * Idempotent lifetime handle for one Templates frame observer.
     */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        /** Stops later callbacks; repeated closure is harmless. */
        @Override
        void close();
    }
}
