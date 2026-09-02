package com.asdasfa.jbs2bg.workbench.morphs;

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
import java.util.random.RandomGenerator;

import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.FilterColumn;
import com.asdasfa.jbs2bg.filtering.FilteredView;
import com.asdasfa.jbs2bg.filtering.NameIdentity;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.filtering.SortKey;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.CustomMorphTargetEdits;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.project.UnchangedOutcome;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

/**
 * JavaFX-independent Morphs Area state machine. It renders immutable Custom Morph Target frames and submits
 * task-oriented intents only through the authoritative Workbench Project flow.
 */
public final class MorphsFeature {
    private static final String NAME_COLUMN = "name";
    private static final Duration TYPE_AHEAD_TIMEOUT = Duration.ofMillis(750);

    private final WorkbenchProjectFlow projectFlow;
    private final Clock clock;
    private final RandomGenerator random;
    private final Consumer<Throwable> observerFailureSink;
    private final Map<Long, Consumer<Frame>> observers = new LinkedHashMap<>();
    private final FilteredView<CustomMorphTargetSnapshot, NameIdentity> view = new FilteredView<>(
            List.of(FilterColumn.of(NAME_COLUMN, target -> target.getName().toLowerCase(Locale.ROOT))),
            ProjectIdentities::customMorphTarget);
    private List<CustomMorphTargetSnapshot> sourceTargets = List.of();
    private String filterText = "";
    private SortOrder sortOrder = SortOrder.NAME_ASCENDING;
    private Optional<NameIdentity> assignedSelection = Optional.empty();
    private String typeAheadPrefix = "";
    private Instant lastTypeAhead;
    private long revision;
    private long nextObserverId = 1;
    private long nextEffectToken = 1;
    private Frame frame;
    private Effect pendingEffect;
    private boolean publishing;

    /**
     * Creates the window-scoped Morphs feature from the latest coherent Project publication.
     *
     * @param projectFlow sole authoritative Project command path
     * @param clock       deterministic source for type-ahead timing
     */
    public MorphsFeature(WorkbenchProjectFlow projectFlow, Clock clock) {
        this(projectFlow, clock, RandomGenerator.getDefault(), failure -> {
            // Production adapters may supply technical diagnostics; the default keeps a renderer failure isolated.
        });
    }

    /**
     * Creates the feature with an explicit random source for deterministic eligibility tests.
     *
     * @param projectFlow sole authoritative Project command path
     * @param clock       deterministic source for type-ahead timing
     * @param random      source used to capture the legacy automatic initial relationship
     */
    MorphsFeature(WorkbenchProjectFlow projectFlow, Clock clock, RandomGenerator random) {
        this(projectFlow, clock, random, failure -> {
            // Deterministic randomness tests do not need a technical diagnostics adapter.
        });
    }

    /**
     * Creates the feature with an explicit observer-failure sink.
     *
     * @param projectFlow         sole authoritative Project command path
     * @param clock               deterministic source for type-ahead timing
     * @param observerFailureSink receives observer failures after frame state commits
     */
    public MorphsFeature(WorkbenchProjectFlow projectFlow, Clock clock,
                         Consumer<Throwable> observerFailureSink) {
        this(projectFlow, clock, RandomGenerator.getDefault(), observerFailureSink);
    }

    /** Creates the feature with every environmental source explicit. */
    private MorphsFeature(WorkbenchProjectFlow projectFlow, Clock clock, RandomGenerator random,
                          Consumer<Throwable> observerFailureSink) {
        this.projectFlow = Objects.requireNonNull(projectFlow, "projectFlow");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.observerFailureSink = Objects.requireNonNull(observerFailureSink, "observerFailureSink");
        reconcile(projectFlow.frame(), OutcomeKind.NONE, List.of());
    }

    /**
     * @return the latest completely reconciled immutable Morphs frame
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
            throw new IllegalStateException("Morphs intents cannot be dispatched during frame publication");
        if (pendingEffect != null)
            return new Update(false, frame, OutcomeKind.NONE);
        return switch (intent) {
            case Create create -> create(create.name());
            case Select select -> select(select.identity());
            case AssignSliderPreset assign -> assignSliderPreset(assign.identity());
            case AssignAllSliderPresets ignored -> assignAllSliderPresets();
            case SelectAssignedSliderPreset selectAssigned -> selectAssignedSliderPreset(selectAssigned.identity());
            case RemoveAssignedSliderPreset ignored -> removeAssignedSliderPreset();
            case RequestClearAssignments ignored -> requestClearAssignments();
            case ChangeFilter changeFilter -> changeFilter(changeFilter.text());
            case ChangeSort changeSort -> changeSort(changeSort.order());
            case TypeAhead typeAhead -> typeAhead(typeAhead.character());
            case RequestRemove ignored -> requestRemove();
            case RequestClearVisible ignored -> requestClearVisible();
            case ClearSelection ignored -> clearSelection();
            case DismissDiagnostics ignored -> dismissDiagnostics();
        };
    }

    /**
     * Completes a matching destructive confirmation against the identities captured when the effect was requested.
     *
     * @param token     pending effect token
     * @param confirmed whether the user accepted the destructive action
     * @return accepted response, or the unchanged frame for a stale token
     */
    public Update respond(long token, boolean confirmed) {
        if (pendingEffect == null || pendingEffect.token() != token)
            return new Update(false, frame, OutcomeKind.NONE);
        Effect effect = pendingEffect;
        pendingEffect = null;
        if (!confirmed)
            return new Update(true, frame, OutcomeKind.NONE);
        return switch (effect.kind()) {
            case CONFIRM_CLEAR_VISIBLE -> clearVisible(effect.identities());
            case CONFIRM_CLEAR_ASSIGNMENTS -> clearAssignments(effect.identities().getFirst());
            case CONFIRM_REMOVE -> remove(effect.identities().getFirst());
        };
    }

    /** Reconciles a later Project publication while retaining explicit filter and sort choices. */
    public Update acceptProjectFrame(WorkbenchProjectFlow.Frame projectFrame, boolean resetSelection) {
        return acceptProjectFrame(projectFrame, resetSelection,
                Objects.requireNonNull(projectFrame, "projectFrame").diagnostics());
    }

    /** Reconciles Project content while preserving kernel-selected ownership of operation diagnostics. */
    public Update acceptProjectFrame(WorkbenchProjectFlow.Frame projectFrame, boolean resetSelection,
                                     List<ProjectDiagnostic> diagnostics) {
        Objects.requireNonNull(projectFrame, "projectFrame");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (projectFrame.sequence() == frame.projectSequence())
            return new Update(true, frame, OutcomeKind.NONE);
        if (resetSelection) {
            view.clearSelection();
            assignedSelection = Optional.empty();
        }
        reconcile(projectFrame, OutcomeKind.NONE, diagnostics);
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Selects only a Custom Morph Target present in the latest immutable Project frame. */
    private Update select(NameIdentity identity) {
        Optional<NameIdentity> previous = view.getSelection();
        if (!view.select(Objects.requireNonNull(identity, "identity")))
            return new Update(false, frame, OutcomeKind.NONE);
        if (!previous.equals(view.getSelection()))
            assignedSelection = Optional.empty();
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Applies case-insensitive contains filtering and permanently drops a hidden selection. */
    private Update changeFilter(String text) {
        filterText = Objects.requireNonNull(text, "text");
        applyFilter();
        publish(frame.projectSequence(), OutcomeKind.NONE, List.of());
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Changes presentation order without changing membership or logical selection. */
    private Update changeSort(SortOrder order) {
        sortOrder = Objects.requireNonNull(order, "order");
        applySort();
        publish(frame.projectSequence(), OutcomeKind.NONE, List.of());
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Selects by a timed visible-order prefix; repeated identical characters cycle matching rows. */
    private Update typeAhead(char character) {
        if (Character.isISOControl(character))
            return new Update(false, frame, OutcomeKind.NONE);
        Instant now = clock.instant();
        String typed = String.valueOf(character).toLowerCase(Locale.ROOT);
        boolean expired = lastTypeAhead == null || now.isBefore(lastTypeAhead)
                || Duration.between(lastTypeAhead, now).compareTo(TYPE_AHEAD_TIMEOUT) > 0;
        boolean repeated = !expired && typeAheadPrefix.length() == 1 && typeAheadPrefix.equals(typed);
        typeAheadPrefix = expired || repeated ? typed : typeAheadPrefix + typed;
        lastTypeAhead = now;

        List<CustomMorphTargetSnapshot> visible = view.visibleSet().getRows();
        int start = repeated ? selectedIndex(visible) + 1 : 0;
        for (int offset = 0; offset < visible.size(); offset++) {
            CustomMorphTargetSnapshot candidate = visible.get((start + offset) % visible.size());
            if (candidate.getName().toLowerCase(Locale.ROOT).startsWith(typeAheadPrefix)) {
                Optional<NameIdentity> previous = view.getSelection();
                view.select(ProjectIdentities.customMorphTarget(candidate));
                if (!previous.equals(view.getSelection()))
                    assignedSelection = Optional.empty();
                publish(frame.projectSequence(), OutcomeKind.NONE, List.of());
                return new Update(true, frame, OutcomeKind.NONE);
            }
        }
        publish(frame.projectSequence(), OutcomeKind.NONE, List.of());
        return new Update(false, frame, OutcomeKind.NONE);
    }

    /** Resolves the current selection inside one visible frame without retaining its row index. */
    private int selectedIndex(List<CustomMorphTargetSnapshot> visible) {
        Optional<NameIdentity> selected = view.getSelection();
        if (selected.isEmpty())
            return -1;
        for (int index = 0; index < visible.size(); index++)
            if (ProjectIdentities.customMorphTarget(visible.get(index)).equals(selected.orElseThrow()))
                return index;
        return -1;
    }

    /** Captures the exact accepted visible identity set before requesting destructive confirmation. */
    private Update requestClearVisible() {
        List<NameIdentity> identities = view.visibleSet().getIdentities();
        if (identities.isEmpty())
            return new Update(false, frame, OutcomeKind.NONE);
        pendingEffect = new Effect(nextEffectToken++, EffectKind.CONFIRM_CLEAR_VISIBLE, identities,
                "Clear Custom Morph Targets", "Remove the visible Custom Morph Targets from the Project?");
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, Optional.of(pendingEffect), OutcomeKind.NONE);
    }

    /** Captures the selected logical target before requesting destructive removal confirmation. */
    private Update requestRemove() {
        CustomMorphTargetSnapshot target = selectedTarget();
        if (target == null)
            return new Update(false, frame, OutcomeKind.NONE);
        NameIdentity identity = NameIdentity.of(target.getName());
        pendingEffect = new Effect(nextEffectToken++, EffectKind.CONFIRM_REMOVE, List.of(identity),
                "Remove Custom Morph Target", "Remove " + target.getName() + " from the Project?");
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, Optional.of(pendingEffect), OutcomeKind.NONE);
    }

    /** Removes the target identity captured before confirmation and never chooses a replacement selection. */
    private Update remove(NameIdentity identity) {
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.delete(identity.getName()));
        return reconcileOutcome(outcome);
    }

    /** Clears target and assigned-preset selection without writing Project state. */
    private Update clearSelection() {
        view.clearSelection();
        assignedSelection = Optional.empty();
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Clears pane-local validation without changing Project or selection state. */
    private Update dismissDiagnostics() {
        publish(frame.projectSequence(), OutcomeKind.NONE, List.of());
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Removes one frozen visible identity set through one authoritative atomic Project edit. */
    private Update clearVisible(List<NameIdentity> identities) {
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.deleteAll(
                identities.stream().map(NameIdentity::getName).toList()));
        return reconcileOutcome(outcome);
    }

    /** Submits one raw condition-bearing name through ProjectSession validation. */
    private Update create(String name) {
        List<SliderPresetSnapshot> presets = projectFlow.frame().snapshot().getSliderPresets();
        // Capture the random relationship before apply so retrying or rendering cannot change the edit's meaning.
        List<String> initialAssignments = presets.isEmpty() ? List.of()
                : List.of(presets.get(random.nextInt(presets.size())).getName());
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.create(name, initialAssignments));
        sourceTargets = outcome.getSnapshot().getCustomMorphTargets();
        OutcomeKind kind = outcomeKind(outcome);
        if (kind == OutcomeKind.CHANGED || kind == OutcomeKind.UNCHANGED) {
            NameIdentity requested = NameIdentity.of(Objects.requireNonNull(name, "name").trim());
            view.setRows(sourceTargets);
            applyFilter();
            view.select(requested);
            assignedSelection = Optional.empty();
            applySort();
        }
        publish(projectFlow.frame().sequence(), kind, outcome.getDiagnostics());
        return new Update(kind == OutcomeKind.CHANGED || kind == OutcomeKind.UNCHANGED, frame, kind);
    }

    /** Adds one relationship by stable identities and renders only the value returned by ProjectSession. */
    private Update assignSliderPreset(NameIdentity presetIdentity) {
        CustomMorphTargetSnapshot target = selectedTarget();
        if (target == null)
            return new Update(false, frame, OutcomeKind.NONE);
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.addSliderPreset(
                target.getName(), Objects.requireNonNull(presetIdentity, "presetIdentity").getName()));
        return reconcileOutcome(outcome);
    }

    /** Assigns every Project Slider Preset through one atomic relationship edit. */
    private Update assignAllSliderPresets() {
        CustomMorphTargetSnapshot target = selectedTarget();
        if (target == null)
            return new Update(false, frame, OutcomeKind.NONE);
        List<String> presetNames = projectFlow.frame().snapshot().getSliderPresets().stream()
                .map(SliderPresetSnapshot::getName)
                .toList();
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.addSliderPresets(
                target.getName(), presetNames));
        return reconcileOutcome(outcome);
    }

    /** Selects one relationship only when it remains assigned in the latest immutable frame. */
    private Update selectAssignedSliderPreset(NameIdentity identity) {
        NameIdentity requested = Objects.requireNonNull(identity, "identity");
        Optional<EditorFrame> editor = editorFrame();
        if (editor.isEmpty() || editor.orElseThrow().assignedPresets().stream()
                .map(preset -> NameIdentity.of(preset.getName()))
                .noneMatch(requested::equals))
            return new Update(false, frame, OutcomeKind.NONE);
        assignedSelection = Optional.of(requested);
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Removes the currently selected relationship through ProjectSession and clears that consumed selection. */
    private Update removeAssignedSliderPreset() {
        CustomMorphTargetSnapshot target = selectedTarget();
        if (target == null || assignedSelection.isEmpty())
            return new Update(false, frame, OutcomeKind.NONE);
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.removeSliderPreset(
                target.getName(), assignedSelection.orElseThrow().getName()));
        return reconcileOutcome(outcome);
    }

    /** Captures the selected target identity before requesting relationship-clear confirmation. */
    private Update requestClearAssignments() {
        CustomMorphTargetSnapshot target = selectedTarget();
        if (target == null || target.getSliderPresetNames().isEmpty())
            return new Update(false, frame, OutcomeKind.NONE);
        NameIdentity identity = NameIdentity.of(target.getName());
        pendingEffect = new Effect(nextEffectToken++, EffectKind.CONFIRM_CLEAR_ASSIGNMENTS, List.of(identity),
                "Clear Slider Presets", "Remove every Slider Preset from " + target.getName() + "?");
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, Optional.of(pendingEffect), OutcomeKind.NONE);
    }

    /** Clears every relationship for the target captured before confirmation. */
    private Update clearAssignments(NameIdentity targetIdentity) {
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.clearSliderPresets(
                targetIdentity.getName()));
        return reconcileOutcome(outcome);
    }

    /** Reconciles one Project outcome through the common immutable feature-publication path. */
    private Update reconcileOutcome(ProjectOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        sourceTargets = outcome.getSnapshot().getCustomMorphTargets();
        view.setRows(sourceTargets);
        applyFilter();
        applySort();
        OutcomeKind kind = outcomeKind(outcome);
        publish(projectFlow.frame().sequence(), kind, outcome.getDiagnostics());
        return new Update(kind == OutcomeKind.CHANGED || kind == OutcomeKind.UNCHANGED, frame, kind);
    }

    /** Resolves the current selection without retaining a row index or mutable model reference. */
    private CustomMorphTargetSnapshot selectedTarget() {
        if (view.getSelection().isEmpty())
            return null;
        NameIdentity selected = view.getSelection().orElseThrow();
        return sourceTargets.stream()
                .filter(target -> NameIdentity.of(target.getName()).equals(selected))
                .findFirst().orElse(null);
    }

    /** Builds relationship choices from the same immutable Project snapshot as the selected target. */
    private Optional<EditorFrame> editorFrame() {
        CustomMorphTargetSnapshot target = selectedTarget();
        if (target == null)
            return Optional.empty();
        List<SliderPresetSnapshot> presets = projectFlow.frame().snapshot().getSliderPresets();
        List<SliderPresetSnapshot> assigned = presets.stream()
                .filter(preset -> target.getSliderPresetNames().stream()
                        .anyMatch(name -> name.equalsIgnoreCase(preset.getName())))
                .toList();
        List<SliderPresetSnapshot> available = presets.stream()
                .filter(preset -> target.getSliderPresetNames().stream()
                        .noneMatch(name -> name.equalsIgnoreCase(preset.getName())))
                .toList();
        return Optional.of(new EditorFrame(target, assigned, available, assignedSelection));
    }

    /** Replaces rows from one coherent Project publication before exposing the next feature frame. */
    private void reconcile(WorkbenchProjectFlow.Frame projectFrame, OutcomeKind outcomeKind,
                           List<ProjectDiagnostic> diagnostics) {
        sourceTargets = Objects.requireNonNull(projectFrame, "projectFrame").snapshot().getCustomMorphTargets();
        view.setRows(sourceTargets);
        applyFilter();
        applySort();
        publish(projectFrame.sequence(), outcomeKind, diagnostics);
    }

    /** Rebuilds the exact-value exclusion criterion whenever rows or retained query changes. */
    private void applyFilter() {
        String query = filterText.toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            view.clearCriterion(NAME_COLUMN);
            return;
        }
        List<String> hidden = sourceTargets.stream()
                .map(CustomMorphTargetSnapshot::getName)
                .filter(name -> !name.toLowerCase(Locale.ROOT).contains(query))
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
        view.setCriterion(ColumnCriterion.hiding(NAME_COLUMN, hidden));
    }

    /** Applies the retained single-column order to the logical view. */
    private void applySort() {
        view.setSortOrder(List.of(sortOrder == SortOrder.NAME_ASCENDING
                ? SortKey.ascending(NAME_COLUMN)
                : SortKey.descending(NAME_COLUMN)));
    }

    /** Commits one defensively owned immutable feature frame. */
    private void publish(long projectSequence, OutcomeKind outcomeKind, List<ProjectDiagnostic> diagnostics) {
        reconcileAssignedSelection();
        frame = new Frame(++revision, projectSequence, view.visibleSet().getRows(), view.getSelection(), filterText,
                sortOrder, editorFrame(), outcomeKind, diagnostics);
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

    /** Keeps both an observer and its optional diagnostics sink from breaking publication. */
    private void reportObserverFailure(RuntimeException failure) {
        try {
            observerFailureSink.accept(failure);
        } catch (RuntimeException sinkFailure) {
            // A technical-diagnostics sink is observational and must never become a second publication failure.
        }
    }

    /** Permanently drops an assigned-preset selection when its target or relationship is no longer visible. */
    private void reconcileAssignedSelection() {
        if (assignedSelection.isEmpty())
            return;
        CustomMorphTargetSnapshot target = selectedTarget();
        NameIdentity selected = assignedSelection.orElseThrow();
        if (target == null || target.getSliderPresetNames().stream()
                .map(NameIdentity::of)
                .noneMatch(selected::equals))
            assignedSelection = Optional.empty();
    }

    /** Maps the closed Project outcome family into presentation state. */
    private static OutcomeKind outcomeKind(ProjectOutcome outcome) {
        if (outcome instanceof ChangedOutcome)
            return OutcomeKind.CHANGED;
        if (outcome instanceof UnchangedOutcome)
            return OutcomeKind.UNCHANGED;
        if (outcome instanceof RejectedOutcome)
            return OutcomeKind.REJECTED;
        if (outcome instanceof FailedOutcome)
            return OutcomeKind.FAILED;
        return OutcomeKind.CANCELLED;
    }

    /** Closed family of task-oriented Morphs intents. */
    public sealed interface Intent permits Create, Select, AssignSliderPreset, AssignAllSliderPresets,
            SelectAssignedSliderPreset, RemoveAssignedSliderPreset, RequestClearAssignments, ChangeFilter,
            ChangeSort, TypeAhead, RequestRemove, RequestClearVisible, ClearSelection, DismissDiagnostics {
    }

    /** Requests one validated Custom Morph Target creation. */
    public record Create(String name) implements Intent {
        /** Captures the raw name so ProjectSession remains the validation authority. */
        public Create {
        }
    }

    /** Selects one visible Custom Morph Target by stable logical identity. */
    public record Select(NameIdentity identity) implements Intent {
        /** Validates the immutable selection request. */
        public Select {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /** Assigns one existing Slider Preset to the selected Custom Morph Target. */
    public record AssignSliderPreset(NameIdentity identity) implements Intent {
        /** Validates the immutable relationship endpoint. */
        public AssignSliderPreset {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /** Assigns every currently available Slider Preset to the selected target atomically. */
    public record AssignAllSliderPresets() implements Intent {
    }

    /** Selects one assigned Slider Preset by stable logical identity. */
    public record SelectAssignedSliderPreset(NameIdentity identity) implements Intent {
        /** Validates the immutable relationship-selection request. */
        public SelectAssignedSliderPreset {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /** Removes the selected assigned Slider Preset from the selected target. */
    public record RemoveAssignedSliderPreset() implements Intent {
    }

    /** Requests confirmation before clearing every relationship from the selected target. */
    public record RequestClearAssignments() implements Intent {
    }

    /** Changes the case-insensitive Custom Morph Target contains filter. */
    public record ChangeFilter(String text) implements Intent {
        /** Validates the immutable filter text. */
        public ChangeFilter {
            Objects.requireNonNull(text, "text");
        }
    }

    /** Changes the Custom Morph Target presentation order. */
    public record ChangeSort(SortOrder order) implements Intent {
        /** Validates the immutable sort request. */
        public ChangeSort {
            Objects.requireNonNull(order, "order");
        }
    }

    /** Selects a Custom Morph Target by one type-ahead character. */
    public record TypeAhead(char character) implements Intent {
    }

    /** Requests confirmation before deleting the exact currently visible target set. */
    public record RequestClearVisible() implements Intent {
    }

    /** Requests confirmation before deleting the selected Custom Morph Target. */
    public record RequestRemove() implements Intent {
    }

    /** Clears current target and relationship selection. */
    public record ClearSelection() implements Intent {
    }

    /** Dismisses current inline Morphs diagnostics without changing Project state. */
    public record DismissDiagnostics() implements Intent {
    }

    /** Supported Custom Morph Target name presentation orders. */
    public enum SortOrder {
        NAME_ASCENDING("Name (A–Z)"),
        NAME_DESCENDING("Name (Z–A)");

        private final String displayName;

        SortOrder(String displayName) {
            this.displayName = displayName;
        }

        /** @return localized-ready label used by the JavaFX adapter */
        @Override
        public String toString() {
            return displayName;
        }
    }

    /** Observable classification of the most recent Project operation. */
    public enum OutcomeKind {
        NONE,
        CHANGED,
        UNCHANGED,
        REJECTED,
        FAILED,
        CANCELLED
    }

    /** Tokenized destructive confirmation requested by the feature after capturing its operand. */
    public record Effect(long token, EffectKind kind, List<NameIdentity> identities, String title, String message) {
        /** Defensively owns all effect values before the platform adapter runs. */
        public Effect {
            kind = Objects.requireNonNull(kind, "kind");
            identities = List.copyOf(identities);
            title = Objects.requireNonNull(title, "title");
            message = Objects.requireNonNull(message, "message");
        }
    }

    /** Closed family of platform confirmations requested by Morphs. */
    public enum EffectKind {
        CONFIRM_CLEAR_VISIBLE,
        CONFIRM_CLEAR_ASSIGNMENTS,
        CONFIRM_REMOVE
    }

    /** Immutable render input for the Morphs Area. */
    public record EditorFrame(CustomMorphTargetSnapshot target, List<SliderPresetSnapshot> assignedPresets,
                              List<SliderPresetSnapshot> availablePresets,
                              Optional<NameIdentity> assignedSelection) {
        /** Defensively owns every relationship list crossing the feature boundary. */
        public EditorFrame {
            Objects.requireNonNull(target, "target");
            assignedPresets = List.copyOf(assignedPresets);
            availablePresets = List.copyOf(availablePresets);
            assignedSelection = Objects.requireNonNull(assignedSelection, "assignedSelection");
        }
    }

    /** Immutable render input for the Morphs Area. */
    public record Frame(long revision, long projectSequence, List<CustomMorphTargetSnapshot> visibleTargets,
                        Optional<NameIdentity> selection, String filterText, SortOrder sortOrder,
                        Optional<EditorFrame> editor, OutcomeKind outcomeKind,
                        List<ProjectDiagnostic> diagnostics) {
        /** Defensively owns all collections and optional values crossing the feature boundary. */
        public Frame {
            visibleTargets = List.copyOf(visibleTargets);
            selection = Objects.requireNonNull(selection, "selection");
            filterText = Objects.requireNonNull(filterText, "filterText");
            sortOrder = Objects.requireNonNull(sortOrder, "sortOrder");
            editor = Objects.requireNonNull(editor, "editor");
            outcomeKind = Objects.requireNonNull(outcomeKind, "outcomeKind");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /** Result of one serialized feature intent. */
    public record Update(boolean accepted, Frame frame, Optional<Effect> effect, OutcomeKind outcomeKind) {
        /** Creates an update without a platform effect. */
        public Update(boolean accepted, Frame frame, OutcomeKind outcomeKind) {
            this(accepted, frame, Optional.empty(), outcomeKind);
        }

        /** Validates the immutable result payload. */
        public Update {
            Objects.requireNonNull(frame, "frame");
            effect = Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(outcomeKind, "outcomeKind");
        }
    }

    /** Idempotent frame-observer lifetime handle. */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        /** Stops later frame publications from reaching this observer. */
        @Override
        void close();
    }
}
