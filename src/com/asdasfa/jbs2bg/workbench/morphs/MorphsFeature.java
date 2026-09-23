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
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.asdasfa.jbs2bg.project.UnchangedOutcome;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

/**
 * JavaFX-independent Morphs Area state machine. It renders immutable Custom Morph Target and NPC Morph Assignment
 * frames and submits task-oriented intents only through the authoritative Workbench Project flow.
 */
public final class MorphsFeature {
    private static final String NAME_COLUMN = "name";
    private static final String NPC_SEARCH_COLUMN = "search";
    private static final String NPC_DISPLAY_NAME_COLUMN = "displayName";
    private static final String NPC_PLUGIN_COLUMN = "plugin";
    private static final Duration TYPE_AHEAD_TIMEOUT = Duration.ofMillis(750);

    private final WorkbenchProjectFlow projectFlow;
    private final Clock clock;
    private final RandomGenerator random;
    private final Consumer<Throwable> observerFailureSink;
    private final Map<Long, Consumer<Frame>> observers = new LinkedHashMap<>();
    private final FilteredView<CustomMorphTargetSnapshot, NameIdentity> view = new FilteredView<>(
            List.of(FilterColumn.of(NAME_COLUMN, target -> target.getName().toLowerCase(Locale.ROOT))),
            ProjectIdentities::customMorphTarget);
    private final FilteredView<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> npcView = new FilteredView<>(
            List.of(FilterColumn.of(NPC_SEARCH_COLUMN, MorphsFeature::npcSearchText),
                    FilterColumn.of(NPC_DISPLAY_NAME_COLUMN,
                            npc -> npc.getDisplayName().toLowerCase(Locale.ROOT)),
                    FilterColumn.of(NPC_PLUGIN_COLUMN, npc -> npc.getPluginName().toLowerCase(Locale.ROOT))),
            ProjectIdentities::npcMorphAssignment);
    private List<CustomMorphTargetSnapshot> sourceTargets = List.of();
    private List<NpcMorphAssignmentSnapshot> sourceNpcs = List.of();
    private String filterText = "";
    private String npcFilterText = "";
    private SortOrder sortOrder = SortOrder.NAME_ASCENDING;
    private NpcSortOrder npcSortOrder = NpcSortOrder.DISPLAY_NAME_ASCENDING;
    private Optional<NameIdentity> assignedSelection = Optional.empty();
    private String typeAheadPrefix = "";
    private Instant lastTypeAhead;
    private String npcTypeAheadPrefix = "";
    private Instant lastNpcTypeAhead;
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
     * @throws NullPointerException  when intent is null
     * @throws IllegalStateException when an observer attempts reentrant dispatch during frame publication
     */
    public Update dispatch(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (publishing)
            throw new IllegalStateException("Morphs intents cannot be dispatched during frame publication");
        if (pendingEffect != null)
            return new Update(false, frame, OutcomeKind.NONE);
        return switch (intent) {
            case Create create -> create(create.name());
            case CreateNpc createNpc -> createNpc(createNpc);
            case Select select -> select(select.identity());
            case SelectNpc selectNpc -> selectNpc(selectNpc.identity());
            case AssignSliderPreset assign -> assignSliderPreset(assign.identity());
            case AssignAllSliderPresets ignored -> assignAllSliderPresets();
            case SelectAssignedSliderPreset selectAssigned -> selectAssignedSliderPreset(selectAssigned.identity());
            case ClearAssignedSliderPresetSelection ignored -> clearAssignedSliderPresetSelection();
            case RemoveAssignedSliderPreset ignored -> requestRemoveAssignedSliderPreset();
            case RequestClearAssignments ignored -> requestClearAssignments();
            case ChangeFilter changeFilter -> changeFilter(changeFilter.text());
            case ChangeNpcFilter changeNpcFilter -> changeNpcFilter(changeNpcFilter.text());
            case ChangeSort changeSort -> changeSort(changeSort.order());
            case ChangeNpcSort changeNpcSort -> changeNpcSort(changeNpcSort.order());
            case TypeAhead typeAhead -> typeAhead(typeAhead.character());
            case NpcTypeAhead npcTypeAhead -> npcTypeAhead(npcTypeAhead.character());
            case RequestRemove ignored -> requestRemove();
            case RequestRemoveNpc ignored -> requestRemoveNpc();
            case RequestClearVisible ignored -> requestClearVisible();
            case RequestClearVisibleNpcs ignored -> requestClearVisibleNpcs();
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
            case CONFIRM_CLEAR_VISIBLE_NPCS -> clearVisibleNpcs(effect.npcIdentities());
            case CONFIRM_CLEAR_ASSIGNMENTS -> effect.npcIdentities().isEmpty()
                    ? clearAssignments(effect.identities().getFirst())
                    : clearNpcAssignments(effect.npcIdentities().getFirst());
            case CONFIRM_REMOVE -> remove(effect.identities().getFirst());
            case CONFIRM_REMOVE_NPC -> removeNpc(effect.npcIdentities().getFirst());
            case CONFIRM_REMOVE_ASSIGNMENT -> effect.npcIdentities().isEmpty()
                    ? removeAssignedSliderPreset(effect.identities().getFirst(), effect.identities().get(1))
                    : removeNpcAssignedSliderPreset(effect.npcIdentities().getFirst(), effect.identities().getFirst());
        };
    }

    /**
     * Reconciles a later Project publication while retaining explicit filter and sort choices.
     *
     * @param projectFrame   latest coherent Project frame
     * @param resetSelection whether lifecycle navigation clears target and relationship selections
     * @return accepted immutable update, or the current frame when the sequence was already reconciled
     */
    public Update acceptProjectFrame(WorkbenchProjectFlow.Frame projectFrame, boolean resetSelection) {
        return acceptProjectFrame(projectFrame, resetSelection,
                Objects.requireNonNull(projectFrame, "projectFrame").diagnostics());
    }

    /**
     * Reconciles Project content while preserving kernel-selected ownership of operation diagnostics.
     *
     * @param projectFrame   latest coherent Project frame
     * @param resetSelection whether lifecycle navigation clears target and relationship selections
     * @param diagnostics    structured diagnostics owned by Morphs for this publication
     * @return accepted immutable update, or the current frame when the sequence was already reconciled
     */
    public Update acceptProjectFrame(WorkbenchProjectFlow.Frame projectFrame, boolean resetSelection,
                                     List<ProjectDiagnostic> diagnostics) {
        Objects.requireNonNull(projectFrame, "projectFrame");
        Objects.requireNonNull(diagnostics, "diagnostics");
        if (projectFrame.sequence() == frame.projectSequence())
            return new Update(true, frame, OutcomeKind.NONE);
        if (resetSelection) {
            view.clearSelection();
            npcView.clearSelection();
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
        boolean switched = npcView.getSelection().isPresent();
        npcView.clearSelection();
        if (switched || !previous.equals(view.getSelection()))
            assignedSelection = Optional.empty();
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Selects one visible NPC by plugin/editor identity and clears the Custom Morph Target selection. */
    private Update selectNpc(NpcMorphAssignmentIdentity identity) {
        Optional<NpcMorphAssignmentIdentity> previous = npcView.getSelection();
        if (!npcView.select(Objects.requireNonNull(identity, "identity")))
            return new Update(false, frame, OutcomeKind.NONE);
        boolean switched = view.getSelection().isPresent();
        view.clearSelection();
        if (switched || !previous.equals(npcView.getSelection()))
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

    /** Filters NPCs across the five visible identity and metadata fields, dropping hidden selection. */
    private Update changeNpcFilter(String text) {
        npcFilterText = Objects.requireNonNull(text, "text");
        applyNpcFilter();
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

    /** Changes NPC presentation order while retaining membership and logical selection. */
    private Update changeNpcSort(NpcSortOrder order) {
        npcSortOrder = Objects.requireNonNull(order, "order");
        applyNpcSort();
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
                boolean switched = npcView.getSelection().isPresent();
                npcView.clearSelection();
                if (switched || !previous.equals(view.getSelection()))
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

    /** Selects an NPC by timed visible-order display-name prefix, cycling repeated characters. */
    private Update npcTypeAhead(char character) {
        if (Character.isISOControl(character))
            return new Update(false, frame, OutcomeKind.NONE);
        Instant now = clock.instant();
        String typed = String.valueOf(character).toLowerCase(Locale.ROOT);
        boolean expired = lastNpcTypeAhead == null || now.isBefore(lastNpcTypeAhead)
                || Duration.between(lastNpcTypeAhead, now).compareTo(TYPE_AHEAD_TIMEOUT) > 0;
        boolean repeated = !expired && npcTypeAheadPrefix.length() == 1 && npcTypeAheadPrefix.equals(typed);
        npcTypeAheadPrefix = expired || repeated ? typed : npcTypeAheadPrefix + typed;
        lastNpcTypeAhead = now;

        List<NpcMorphAssignmentSnapshot> visible = npcView.visibleSet().getRows();
        int start = repeated ? selectedNpcIndex(visible) + 1 : 0;
        for (int offset = 0; offset < visible.size(); offset++) {
            NpcMorphAssignmentSnapshot candidate = visible.get((start + offset) % visible.size());
            if (candidate.getDisplayName().toLowerCase(Locale.ROOT).startsWith(npcTypeAheadPrefix)) {
                selectNpc(ProjectIdentities.npcMorphAssignment(candidate));
                return new Update(true, frame, OutcomeKind.NONE);
            }
        }
        publish(frame.projectSequence(), OutcomeKind.NONE, List.of());
        return new Update(false, frame, OutcomeKind.NONE);
    }

    /** Resolves current NPC selection in the visible order without retaining a row index. */
    private int selectedNpcIndex(List<NpcMorphAssignmentSnapshot> visible) {
        Optional<NpcMorphAssignmentIdentity> selected = npcView.getSelection();
        if (selected.isEmpty())
            return -1;
        for (int index = 0; index < visible.size(); index++)
            if (ProjectIdentities.npcMorphAssignment(visible.get(index)).equals(selected.orElseThrow()))
                return index;
        return -1;
    }

    /** Captures the exact accepted visible identity set before requesting destructive confirmation. */
    private Update requestClearVisible() {
        List<NameIdentity> identities = view.visibleSet().getIdentities();
        if (identities.isEmpty())
            return new Update(false, frame, OutcomeKind.NONE);
        pendingEffect = new Effect(nextEffectToken++, EffectKind.CONFIRM_CLEAR_VISIBLE, identities,
                List.of(), "Clear Custom Morph Targets", "Remove the visible Custom Morph Targets from the Project?");
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
                List.of(), "Remove Custom Morph Target", "Remove " + target.getName() + " from the Project?");
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, Optional.of(pendingEffect), OutcomeKind.NONE);
    }

    /** Removes the target identity captured before confirmation and never chooses a replacement selection. */
    private Update remove(NameIdentity identity) {
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.delete(identity.getName()));
        return reconcileOutcome(outcome);
    }

    /** Freezes the visible NPC identity set before requesting destructive confirmation. */
    private Update requestClearVisibleNpcs() {
        List<NpcMorphAssignmentIdentity> identities = npcView.visibleSet().getIdentities();
        if (identities.isEmpty())
            return new Update(false, frame, OutcomeKind.NONE);
        pendingEffect = new Effect(nextEffectToken++, EffectKind.CONFIRM_CLEAR_VISIBLE_NPCS, List.of(), identities,
                "Clear NPC Morph Assignments", "Remove the visible NPC Morph Assignments from the Project?");
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, Optional.of(pendingEffect), OutcomeKind.NONE);
    }

    /** Freezes the selected NPC's complete identity before removal confirmation. */
    private Update requestRemoveNpc() {
        NpcMorphAssignmentSnapshot npc = selectedNpc();
        if (npc == null)
            return new Update(false, frame, OutcomeKind.NONE);
        NpcMorphAssignmentIdentity identity = ProjectIdentities.npcMorphAssignment(npc);
        pendingEffect = new Effect(nextEffectToken++, EffectKind.CONFIRM_REMOVE_NPC, List.of(), List.of(identity),
                "Remove NPC Morph Assignment", "Remove " + npc.getDisplayName() + " ("
                        + npc.getPluginName() + "/" + npc.getEditorId() + ") from the Project?");
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, Optional.of(pendingEffect), OutcomeKind.NONE);
    }

    /** Deletes the exact confirmed NPC identity without selecting another row. */
    private Update removeNpc(NpcMorphAssignmentIdentity identity) {
        return reconcileOutcome(projectFlow.apply(NpcMorphAssignmentEdits.removeNpc(identity)));
    }

    /** Clears target and assigned-preset selection without writing Project state. */
    private Update clearSelection() {
        view.clearSelection();
        npcView.clearSelection();
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

    /** Removes one frozen visible NPC identity set in a single atomic Project edit. */
    private Update clearVisibleNpcs(List<NpcMorphAssignmentIdentity> identities) {
        return reconcileOutcome(projectFlow.apply(NpcMorphAssignmentEdits.removeNpcs(identities)));
    }

    /** Submits one raw condition-bearing name through ProjectSession validation. */
    private Update create(String name) {
        List<SliderPresetSnapshot> presets = projectFlow.frame().snapshot().getSliderPresets();
        // Capture the random relationship before apply so retrying or rendering cannot change the edit's meaning.
        List<String> initialAssignments = presets.isEmpty() ? List.of()
                : List.of(presets.get(random.nextInt(presets.size())).getName());
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.create(name, initialAssignments));
        sourceTargets = outcome.getSnapshot().getCustomMorphTargets();
        sourceNpcs = outcome.getSnapshot().getNpcMorphAssignments();
        OutcomeKind kind = outcomeKind(outcome);
        if (kind == OutcomeKind.CHANGED || kind == OutcomeKind.UNCHANGED) {
            NameIdentity requested = NameIdentity.of(Objects.requireNonNull(name, "name").trim());
            view.setRows(sourceTargets);
            npcView.setRows(sourceNpcs);
            applyFilter();
            applyNpcFilter();
            npcView.clearSelection();
            view.select(requested);
            assignedSelection = Optional.empty();
            applySort();
        }
        publish(projectFlow.frame().sequence(), kind, outcome.getDiagnostics());
        return new Update(kind == OutcomeKind.CHANGED || kind == OutcomeKind.UNCHANGED, frame, kind);
    }

    /** Submits raw NPC authoring fields to ProjectSession and selects only its accepted logical identity. */
    private Update createNpc(CreateNpc create) {
        ProjectOutcome outcome = projectFlow.apply(NpcMorphAssignmentEdits.create(create.displayName(),
                create.pluginName(), create.editorId(), create.race(), create.formId()));
        sourceTargets = outcome.getSnapshot().getCustomMorphTargets();
        sourceNpcs = outcome.getSnapshot().getNpcMorphAssignments();
        OutcomeKind kind = outcomeKind(outcome);
        if (kind == OutcomeKind.CHANGED || kind == OutcomeKind.UNCHANGED) {
            view.setRows(sourceTargets);
            applyFilter();
            applySort();
            npcView.setRows(sourceNpcs);
            applyNpcFilter();
            applyNpcSort();
            NpcMorphAssignmentIdentity requested = new NpcMorphAssignmentIdentity(create.pluginName().trim(),
                    create.editorId().trim());
            view.clearSelection();
            npcView.select(requested);
            assignedSelection = Optional.empty();
        }
        publish(projectFlow.frame().sequence(), kind, outcome.getDiagnostics());
        return new Update(kind == OutcomeKind.CHANGED || kind == OutcomeKind.UNCHANGED, frame, kind);
    }

    /** Adds one relationship by stable identities and renders only the value returned by ProjectSession. */
    private Update assignSliderPreset(NameIdentity presetIdentity) {
        CustomMorphTargetSnapshot target = selectedTarget();
        NpcMorphAssignmentSnapshot npc = selectedNpc();
        if (target == null && npc == null)
            return new Update(false, frame, OutcomeKind.NONE);
        String presetName = Objects.requireNonNull(presetIdentity, "presetIdentity").getName();
        ProjectOutcome outcome = target != null
                ? projectFlow.apply(CustomMorphTargetEdits.addSliderPreset(target.getName(), presetName))
                : projectFlow.apply(NpcMorphAssignmentEdits.addSliderPreset(
                        ProjectIdentities.npcMorphAssignment(npc), presetName));
        return reconcileOutcome(outcome);
    }

    /** Assigns every Project Slider Preset through one atomic relationship edit. */
    private Update assignAllSliderPresets() {
        CustomMorphTargetSnapshot target = selectedTarget();
        NpcMorphAssignmentSnapshot npc = selectedNpc();
        if (target == null && npc == null)
            return new Update(false, frame, OutcomeKind.NONE);
        List<String> presetNames = projectFlow.frame().snapshot().getSliderPresets().stream()
                .map(SliderPresetSnapshot::getName)
                .toList();
        ProjectOutcome outcome = target != null
                ? projectFlow.apply(CustomMorphTargetEdits.addSliderPresets(target.getName(), presetNames))
                : projectFlow.apply(NpcMorphAssignmentEdits.addSliderPresets(
                        ProjectIdentities.npcMorphAssignment(npc), presetNames));
        return reconcileOutcome(outcome);
    }

    /** Selects one relationship only when it remains assigned in the latest immutable frame. */
    private Update selectAssignedSliderPreset(NameIdentity identity) {
        NameIdentity requested = Objects.requireNonNull(identity, "identity");
        List<SliderPresetSnapshot> assigned = editorFrame().map(EditorFrame::assignedPresets)
                .orElseGet(() -> npcEditorFrame().map(NpcEditorFrame::assignedPresets).orElse(List.of()));
        if (assigned.stream()
                .map(preset -> NameIdentity.of(preset.getName()))
                .noneMatch(requested::equals))
            return new Update(false, frame, OutcomeKind.NONE);
        assignedSelection = Optional.of(requested);
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Clears only assigned-preset selection without changing the selected target or Project. */
    private Update clearAssignedSliderPresetSelection() {
        if (assignedSelection.isEmpty())
            return new Update(false, frame, OutcomeKind.NONE);
        assignedSelection = Optional.empty();
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, OutcomeKind.NONE);
    }

    /** Captures both relationship endpoints before requesting destructive removal confirmation. */
    private Update requestRemoveAssignedSliderPreset() {
        CustomMorphTargetSnapshot target = selectedTarget();
        NpcMorphAssignmentSnapshot npc = selectedNpc();
        if ((target == null && npc == null) || assignedSelection.isEmpty())
            return new Update(false, frame, OutcomeKind.NONE);
        NameIdentity presetIdentity = assignedSelection.orElseThrow();
        List<NameIdentity> identities = target != null
                ? List.of(NameIdentity.of(target.getName()), presetIdentity) : List.of(presetIdentity);
        List<NpcMorphAssignmentIdentity> npcIdentities = npc == null ? List.of()
                : List.of(ProjectIdentities.npcMorphAssignment(npc));
        String owner = target != null ? target.getName() : npc.getDisplayName() + " ("
                + npc.getPluginName() + "/" + npc.getEditorId() + ")";
        pendingEffect = new Effect(nextEffectToken++, EffectKind.CONFIRM_REMOVE_ASSIGNMENT,
                identities, npcIdentities, "Remove Slider Preset relationship",
                "Remove " + presetIdentity.getName() + " from " + owner + "?");
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, Optional.of(pendingEffect), OutcomeKind.NONE);
    }

    /** Removes one captured relationship through ProjectSession after confirmation. */
    private Update removeAssignedSliderPreset(NameIdentity targetIdentity, NameIdentity presetIdentity) {
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.removeSliderPreset(
                targetIdentity.getName(), presetIdentity.getName()));
        return reconcileOutcome(outcome);
    }

    /** Removes a confirmed NPC relationship by the captured plugin/editor and preset identities. */
    private Update removeNpcAssignedSliderPreset(NpcMorphAssignmentIdentity npcIdentity,
                                                 NameIdentity presetIdentity) {
        return reconcileOutcome(projectFlow.apply(NpcMorphAssignmentEdits.removeSliderPreset(
                npcIdentity, presetIdentity.getName())));
    }

    /** Captures the selected target identity before requesting relationship-clear confirmation. */
    private Update requestClearAssignments() {
        CustomMorphTargetSnapshot target = selectedTarget();
        NpcMorphAssignmentSnapshot npc = selectedNpc();
        if (target == null && npc == null)
            return new Update(false, frame, OutcomeKind.NONE);
        List<String> names = target != null ? target.getSliderPresetNames() : npc.getSliderPresetNames();
        if (names.isEmpty())
            return new Update(false, frame, OutcomeKind.NONE);
        List<NameIdentity> identities = target == null ? List.of() : List.of(NameIdentity.of(target.getName()));
        List<NpcMorphAssignmentIdentity> npcIdentities = npc == null ? List.of()
                : List.of(ProjectIdentities.npcMorphAssignment(npc));
        String owner = target != null ? target.getName() : npc.getDisplayName() + " ("
                + npc.getPluginName() + "/" + npc.getEditorId() + ")";
        pendingEffect = new Effect(nextEffectToken++, EffectKind.CONFIRM_CLEAR_ASSIGNMENTS, identities, npcIdentities,
                "Clear Slider Presets", "Remove every Slider Preset from " + owner + "?");
        publish(frame.projectSequence(), OutcomeKind.NONE, frame.diagnostics());
        return new Update(true, frame, Optional.of(pendingEffect), OutcomeKind.NONE);
    }

    /** Clears every relationship for the target captured before confirmation. */
    private Update clearAssignments(NameIdentity targetIdentity) {
        ProjectOutcome outcome = projectFlow.apply(CustomMorphTargetEdits.clearSliderPresets(
                targetIdentity.getName()));
        return reconcileOutcome(outcome);
    }

    /** Clears every relationship for the captured NPC identity after confirmation. */
    private Update clearNpcAssignments(NpcMorphAssignmentIdentity npcIdentity) {
        return reconcileOutcome(projectFlow.apply(NpcMorphAssignmentEdits.clearSliderPresets(npcIdentity)));
    }

    /** Reconciles one Project outcome through the common immutable feature-publication path. */
    private Update reconcileOutcome(ProjectOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        sourceTargets = outcome.getSnapshot().getCustomMorphTargets();
        sourceNpcs = outcome.getSnapshot().getNpcMorphAssignments();
        view.setRows(sourceTargets);
        npcView.setRows(sourceNpcs);
        applyFilter();
        applyNpcFilter();
        applySort();
        applyNpcSort();
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

    /** Resolves current NPC selection by complete identity in the latest immutable source. */
    private NpcMorphAssignmentSnapshot selectedNpc() {
        if (npcView.getSelection().isEmpty())
            return null;
        NpcMorphAssignmentIdentity selected = npcView.getSelection().orElseThrow();
        return sourceNpcs.stream()
                .filter(npc -> ProjectIdentities.npcMorphAssignment(npc).equals(selected))
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

    /** Builds NPC relationship choices from the same immutable Project snapshot as its selected value. */
    private Optional<NpcEditorFrame> npcEditorFrame() {
        NpcMorphAssignmentSnapshot npc = selectedNpc();
        if (npc == null)
            return Optional.empty();
        List<SliderPresetSnapshot> presets = projectFlow.frame().snapshot().getSliderPresets();
        List<SliderPresetSnapshot> assigned = presets.stream()
                .filter(preset -> npc.getSliderPresetNames().stream()
                        .anyMatch(name -> name.equalsIgnoreCase(preset.getName())))
                .toList();
        List<SliderPresetSnapshot> available = presets.stream()
                .filter(preset -> npc.getSliderPresetNames().stream()
                        .noneMatch(name -> name.equalsIgnoreCase(preset.getName())))
                .toList();
        return Optional.of(new NpcEditorFrame(npc, assigned, available, assignedSelection));
    }

    /** Replaces rows from one coherent Project publication before exposing the next feature frame. */
    private void reconcile(WorkbenchProjectFlow.Frame projectFrame, OutcomeKind outcomeKind,
                           List<ProjectDiagnostic> diagnostics) {
        sourceTargets = Objects.requireNonNull(projectFrame, "projectFrame").snapshot().getCustomMorphTargets();
        sourceNpcs = projectFrame.snapshot().getNpcMorphAssignments();
        view.setRows(sourceTargets);
        npcView.setRows(sourceNpcs);
        applyFilter();
        applyNpcFilter();
        applySort();
        applyNpcSort();
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

    /** Applies one case-insensitive query to all NPC identity and metadata fields. */
    private void applyNpcFilter() {
        String query = npcFilterText.toLowerCase(Locale.ROOT);
        if (query.isEmpty()) {
            npcView.clearCriterion(NPC_SEARCH_COLUMN);
            return;
        }
        List<String> hidden = sourceNpcs.stream()
                .map(MorphsFeature::npcSearchText)
                .filter(value -> !value.contains(query))
                .toList();
        npcView.setCriterion(ColumnCriterion.hiding(NPC_SEARCH_COLUMN, hidden));
    }

    /** @return lower-cased searchable values for one NPC without relying on its display name for identity */
    private static String npcSearchText(NpcMorphAssignmentSnapshot npc) {
        return String.join(" ", npc.getDisplayName(), npc.getPluginName(), npc.getEditorId(), npc.getRace(),
                npc.getFormId()).toLowerCase(Locale.ROOT);
    }

    /** Applies the retained single-column order to the logical view. */
    private void applySort() {
        view.setSortOrder(List.of(sortOrder == SortOrder.NAME_ASCENDING
                ? SortKey.ascending(NAME_COLUMN)
                : SortKey.descending(NAME_COLUMN)));
    }

    /** Applies the retained NPC display-name or plugin order to the logical view. */
    private void applyNpcSort() {
        npcView.setSortOrder(List.of(switch (npcSortOrder) {
            case DISPLAY_NAME_ASCENDING -> SortKey.ascending(NPC_DISPLAY_NAME_COLUMN);
            case DISPLAY_NAME_DESCENDING -> SortKey.descending(NPC_DISPLAY_NAME_COLUMN);
            case PLUGIN_ASCENDING -> SortKey.ascending(NPC_PLUGIN_COLUMN);
            case PLUGIN_DESCENDING -> SortKey.descending(NPC_PLUGIN_COLUMN);
        }));
    }

    /** Commits one defensively owned immutable feature frame. */
    private void publish(long projectSequence, OutcomeKind outcomeKind, List<ProjectDiagnostic> diagnostics) {
        reconcileAssignedSelection();
        frame = new Frame(++revision, projectSequence, view.visibleSet().getRows(), view.getSelection(), filterText,
                sortOrder, editorFrame(), outcomeKind, diagnostics, npcView.visibleSet().getRows(),
                npcView.getSelection(), npcFilterText, npcSortOrder, npcEditorFrame());
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
        NpcMorphAssignmentSnapshot npc = selectedNpc();
        NameIdentity selected = assignedSelection.orElseThrow();
        List<String> names = target != null ? target.getSliderPresetNames()
                : npc != null ? npc.getSliderPresetNames() : List.of();
        if (names.stream()
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
    public sealed interface Intent permits Create, CreateNpc, Select, SelectNpc,
            AssignSliderPreset, AssignAllSliderPresets,
            SelectAssignedSliderPreset, ClearAssignedSliderPresetSelection, RemoveAssignedSliderPreset,
            RequestClearAssignments, ChangeFilter, ChangeNpcFilter,
            ChangeSort, ChangeNpcSort, TypeAhead, NpcTypeAhead, RequestRemove, RequestRemoveNpc,
            RequestClearVisible, RequestClearVisibleNpcs, ClearSelection, DismissDiagnostics {
    }

    /** Requests one validated Custom Morph Target creation. */
    public record Create(String name) implements Intent {
        /** Captures the raw name so ProjectSession remains the validation authority. */
        public Create {
        }
    }

    /** Requests validated manual NPC Morph Assignment creation. */
    public record CreateNpc(String displayName, String pluginName, String editorId, String race,
                            String formId) implements Intent {
    }

    /** Selects one visible Custom Morph Target by stable logical identity. */
    public record Select(NameIdentity identity) implements Intent {
        /** Validates the immutable selection request. */
        public Select {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /** Selects one visible NPC by its complete plugin/editor identity. */
    public record SelectNpc(NpcMorphAssignmentIdentity identity) implements Intent {
        /** Validates the immutable selection request. */
        public SelectNpc {
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

    /** Clears assigned Slider Preset selection while preserving the selected target. */
    public record ClearAssignedSliderPresetSelection() implements Intent {
    }

    /** Requests confirmation before removing the selected Slider Preset relationship. */
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

    /** Changes the case-insensitive NPC contains filter across identity and metadata. */
    public record ChangeNpcFilter(String text) implements Intent {
        /** Validates the immutable filter request. */
        public ChangeNpcFilter {
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

    /** Changes the NPC catalog presentation order. */
    public record ChangeNpcSort(NpcSortOrder order) implements Intent {
        /** Validates the immutable sort request. */
        public ChangeNpcSort {
            Objects.requireNonNull(order, "order");
        }
    }

    /** Selects a Custom Morph Target by one type-ahead character. */
    public record TypeAhead(char character) implements Intent {
    }

    /** Selects an NPC by one visible-order display-name type-ahead character. */
    public record NpcTypeAhead(char character) implements Intent {
    }

    /** Requests confirmation before deleting the exact currently visible target set. */
    public record RequestClearVisible() implements Intent {
    }

    /** Requests confirmation before deleting the frozen visible NPC set. */
    public record RequestClearVisibleNpcs() implements Intent {
    }

    /** Requests confirmation before deleting the selected Custom Morph Target. */
    public record RequestRemove() implements Intent {
    }

    /** Requests confirmation before deleting the selected NPC Morph Assignment. */
    public record RequestRemoveNpc() implements Intent {
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

    /** Supported NPC catalog orders; ties retain canonical Project identity order. */
    public enum NpcSortOrder {
        DISPLAY_NAME_ASCENDING("Display name (A–Z)"),
        DISPLAY_NAME_DESCENDING("Display name (Z–A)"),
        PLUGIN_ASCENDING("Plugin (A–Z)"),
        PLUGIN_DESCENDING("Plugin (Z–A)");

        private final String displayName;

        NpcSortOrder(String displayName) {
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
    public record Effect(long token, EffectKind kind, List<NameIdentity> identities,
                         List<NpcMorphAssignmentIdentity> npcIdentities, String title, String message) {
        /** Defensively owns all effect values before the platform adapter runs. */
        public Effect {
            kind = Objects.requireNonNull(kind, "kind");
            identities = List.copyOf(identities);
            npcIdentities = List.copyOf(npcIdentities);
            title = Objects.requireNonNull(title, "title");
            message = Objects.requireNonNull(message, "message");
        }
    }

    /** Closed family of platform confirmations requested by Morphs. */
    public enum EffectKind {
        CONFIRM_CLEAR_VISIBLE,
        CONFIRM_CLEAR_VISIBLE_NPCS,
        CONFIRM_CLEAR_ASSIGNMENTS,
        CONFIRM_REMOVE,
        CONFIRM_REMOVE_NPC,
        CONFIRM_REMOVE_ASSIGNMENT
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

    /** Immutable NPC metadata and Slider Preset relationship render input. */
    public record NpcEditorFrame(NpcMorphAssignmentSnapshot npc, List<SliderPresetSnapshot> assignedPresets,
                                 List<SliderPresetSnapshot> availablePresets,
                                 Optional<NameIdentity> assignedSelection) {
        /** Defensively owns every relationship list crossing the feature boundary. */
        public NpcEditorFrame {
            Objects.requireNonNull(npc, "npc");
            assignedPresets = List.copyOf(assignedPresets);
            availablePresets = List.copyOf(availablePresets);
            assignedSelection = Objects.requireNonNull(assignedSelection, "assignedSelection");
        }
    }

    /** Immutable render input for the Morphs Area. */
    public record Frame(long revision, long projectSequence, List<CustomMorphTargetSnapshot> visibleTargets,
                        Optional<NameIdentity> selection, String filterText, SortOrder sortOrder,
                        Optional<EditorFrame> editor, OutcomeKind outcomeKind,
                        List<ProjectDiagnostic> diagnostics,
                        List<NpcMorphAssignmentSnapshot> visibleNpcs,
                        Optional<NpcMorphAssignmentIdentity> npcSelection, String npcFilterText,
                        NpcSortOrder npcSortOrder, Optional<NpcEditorFrame> npcEditor) {
        /** Defensively owns all collections and optional values crossing the feature boundary. */
        public Frame {
            visibleTargets = List.copyOf(visibleTargets);
            selection = Objects.requireNonNull(selection, "selection");
            filterText = Objects.requireNonNull(filterText, "filterText");
            sortOrder = Objects.requireNonNull(sortOrder, "sortOrder");
            editor = Objects.requireNonNull(editor, "editor");
            outcomeKind = Objects.requireNonNull(outcomeKind, "outcomeKind");
            diagnostics = List.copyOf(diagnostics);
            visibleNpcs = List.copyOf(visibleNpcs);
            npcSelection = Objects.requireNonNull(npcSelection, "npcSelection");
            npcFilterText = Objects.requireNonNull(npcFilterText, "npcFilterText");
            npcSortOrder = Objects.requireNonNull(npcSortOrder, "npcSortOrder");
            npcEditor = Objects.requireNonNull(npcEditor, "npcEditor");
            if (selection.isPresent() && npcSelection.isPresent())
                throw new IllegalArgumentException("Morphs selections must be mutually exclusive");
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
