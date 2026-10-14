package com.asdasfa.jbs2bg.workbench.npcdatabase;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.FilteredView;
import com.asdasfa.jbs2bg.filtering.NpcTableColumns;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.filtering.SortKey;
import com.asdasfa.jbs2bg.project.ChangedOutcome;
import com.asdasfa.jbs2bg.project.FailedOutcome;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.NpcPromotionOutcome;
import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.ProjectDiagnosticCodes;
import com.asdasfa.jbs2bg.project.ProjectOutcome;
import com.asdasfa.jbs2bg.project.RejectedOutcome;
import com.asdasfa.jbs2bg.workbench.WorkbenchFeature;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;

/**
 * Window-scoped NPC Database catalog and promotion requests. All methods run on the serialized presentation lane;
 * source files are staged separately before a complete result is committed here. Project outcomes and assignment
 * ownership remain with the Workbench Project flow. User tasks arrive as {@link Intent}s through
 * {@link #dispatch(Intent)}; only the {@link NpcDatabaseImporter} commits staged sources, from its job completion.
 */
public final class NpcDatabaseFeature
        implements WorkbenchFeature<NpcDatabaseFeature.Intent, NpcDatabaseFeature.Update, NpcDatabaseFeature.Effect> {
    private static final Duration TYPE_AHEAD_TIMEOUT = Duration.ofMillis(750);

    private final LinkedHashMap<Path, List<NPC>> sourceRows = new LinkedHashMap<>();
    private final FilteredView<NPC, NpcMorphAssignmentIdentity> view = new FilteredView<>(
            NpcTableColumns.npcDatabase(), ProjectIdentities::npcDatabaseEntry);
    private final List<NpcDatabaseSourceReader.Diagnostic> diagnostics = new ArrayList<>();
    private final WorkbenchProjectFlow projectFlow;
    private final Clock clock;
    private final RandomGenerator random;
    private Optional<Path> selectedSource = Optional.empty();
    private Optional<PromotionReport> promotionReport = Optional.empty();
    private Optional<NpcMorphAssignmentIdentity> returnAssignment = Optional.empty();
    private boolean assignRandom;
    private String typeAheadPrefix = "";
    private Instant lastTypeAheadAt;
    private Effect pendingConfirmation;
    private long nextToken = 1;
    private long revision;
    private Frame frame;

    /** Describes one imported source and its currently retained row count. */
    public record Source(Path path, int rowCount) {
        /** Requires a source path and a nonnegative parsed row count. */
        public Source {
            Objects.requireNonNull(path, "path");
            if (rowCount < 0)
                throw new IllegalArgumentException("rowCount must be nonnegative");
        }
    }

    /** Immutable catalog state for one complete presentation publication. */
    public record Frame(long revision, List<Source> sources, List<NPC> rows, List<NPC> visibleRows,
                        Optional<Path> selectedSource, Optional<NPC> selectedRow,
                        List<NpcDatabaseSourceReader.Diagnostic> diagnostics,
                        List<ColumnCriterion> criteria, List<SortKey> sortOrder,
                        Map<NpcMorphAssignmentIdentity, Path> rowSources, boolean assignRandom,
                        Optional<PromotionReport> promotionReport) {
        /** Owns every collection so earlier frames cannot change after later imports. */
        public Frame {
            if (revision <= 0)
                throw new IllegalArgumentException("revision must be positive");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
            visibleRows = List.copyOf(Objects.requireNonNull(visibleRows, "visibleRows"));
            Objects.requireNonNull(selectedSource, "selectedSource");
            Objects.requireNonNull(selectedRow, "selectedRow");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
            sortOrder = List.copyOf(Objects.requireNonNull(sortOrder, "sortOrder"));
            rowSources = Map.copyOf(Objects.requireNonNull(rowSources, "rowSources"));
            Objects.requireNonNull(promotionReport, "promotionReport");
        }

        /**
         * Returns the first imported source currently contributing an NPC identity.
         *
         * @param row catalog row to inspect
         * @return the contributing source, or empty when the row is no longer in this frame
         */
        public Optional<Path> sourceOf(NPC row) {
            return Optional.ofNullable(rowSources.get(ProjectIdentities.npcDatabaseEntry(row)));
        }
    }

    /**
     * Starts with an empty source catalog and no selected or filtered row.
     *
     * @param projectFlow authoritative Project operation boundary that Add and Add All promote through
     * @param clock       deterministic source for type-ahead timing; only gaps between its instants are read, so
     *                    production passes a monotonic {@link com.asdasfa.jbs2bg.workbench.MonotonicClock}
     * @throws NullPointerException when an argument is null
     */
    public NpcDatabaseFeature(WorkbenchProjectFlow projectFlow, Clock clock) {
        this(projectFlow, clock, RandomGenerator.getDefault());
    }

    /**
     * Starts a catalog with a controlled random source for optional initial preset assignment.
     *
     * @param projectFlow authoritative Project operation boundary that Add and Add All promote through
     * @param clock       deterministic source for type-ahead timing
     * @param random      source of one index per promoted NPC when Assign random is enabled
     */
    NpcDatabaseFeature(WorkbenchProjectFlow projectFlow, Clock clock, RandomGenerator random) {
        this.projectFlow = Objects.requireNonNull(projectFlow, "projectFlow");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        publish();
    }

    /** @return the latest immutable catalog frame */
    public Frame frame() {
        return frame;
    }

    /** One Project-owned outcome for an NPC identity captured from the visible catalog. */
    public record PromotionEntry(NpcMorphAssignmentIdentity identity, String displayName,
                                 PromotionStatus status, List<ProjectDiagnostic> diagnostics) {
        /** Defensively owns diagnostics so a later Project edit cannot change this report. */
        public PromotionEntry {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    /** Result category for one requested promotion. */
    public enum PromotionStatus {
        ADDED, DUPLICATE, REJECTED, FAILED, UNCHANGED
    }

    /** Complete ordered result of Add or Add All; an empty result means no row was eligible. */
    public record PromotionReport(boolean bulk, List<PromotionEntry> entries) {
        /** Retains every attempted row and its Project diagnostics in request order. */
        public PromotionReport {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }

        /** @return number of new NPC Morph Assignments created */
        public long addedCount() {
            return entries.stream().filter(entry -> entry.status() == PromotionStatus.ADDED).count();
        }

        /** @return number of identities already present in the Project */
        public long duplicateCount() {
            return entries.stream().filter(entry -> entry.status() == PromotionStatus.DUPLICATE).count();
        }

        /** @return number of requested rows rejected by Project validation or failed */
        public long rejectedCount() {
            return entries.stream().filter(entry -> entry.status() == PromotionStatus.REJECTED
                    || entry.status() == PromotionStatus.FAILED).count();
        }

        /** @return the last newly promoted identity, when one can be selected on return to Morphs */
        public Optional<NpcMorphAssignmentIdentity> lastAddedIdentity() {
            for (int index = entries.size() - 1; index >= 0; index--) {
                PromotionEntry entry = entries.get(index);
                if (entry.status() == PromotionStatus.ADDED)
                    return Optional.of(entry.identity());
            }
            return Optional.empty();
        }
    }

    /**
     * Applies one NPC Database task intent on the serialized presentation lane.
     *
     * @param intent immutable user task
     * @return whether the task was accepted, the resulting frame, and any effect the caller must realize
     * @throws NullPointerException when intent is null
     */
    @Override
    public Update dispatch(Intent intent) {
        return switch (Objects.requireNonNull(intent, "intent")) {
            case SelectSource select -> new Update(selectSource(select.source()), frame);
            case SelectRow select -> new Update(selectRow(select.identity()), frame);
            case ClearSelection ignored -> clearSelection();
            case ClearAllSelections ignored -> clearAllSelections();
            case TypeAhead typeAhead -> typeAhead(typeAhead.character());
            case SetCriterion set -> setCriterion(set.criterion());
            case ClearAllCriteria ignored -> clearAllCriteria();
            case ChangeSort sort -> changeSort(sort.sortOrder());
            case SetAssignRandom set -> setAssignRandom(set.selected());
            case DismissPromotionReport ignored -> dismissPromotionReport();
            case LeaveArea ignored -> leaveArea();
            case BackToMorphs ignored -> backToMorphs();
            case AddSelected ignored -> promoted(promoteSelected());
            case AddAllVisible ignored -> promoted(promoteVisible());
            case RequestRemoveSource ignored -> requestRemoveSource();
            case RequestClearVisible ignored -> requestClearVisible();
        };
    }

    /**
     * Distinguishes Project promotion and catalog removal from local browsing, so an active job blocks writes
     * without freezing the catalog. Removing a source or clearing entries discards imported work, so they wait for a
     * blocking job like the Project edits do; Assign random only records a choice for a later Add.
     *
     * @param intent intent about to be dispatched
     * @return true for Add, Add All, Remove source and Clear visible entries
     */
    @Override
    public boolean isMutation(Intent intent) {
        return switch (Objects.requireNonNull(intent, "intent")) {
            case AddSelected ignored -> true;
            case AddAllVisible ignored -> true;
            case RequestRemoveSource ignored -> true;
            case RequestClearVisible ignored -> true;
            case SelectSource ignored -> false;
            case SelectRow ignored -> false;
            case ClearSelection ignored -> false;
            case ClearAllSelections ignored -> false;
            case TypeAhead ignored -> false;
            case SetCriterion ignored -> false;
            case ClearAllCriteria ignored -> false;
            case ChangeSort ignored -> false;
            case SetAssignRandom ignored -> false;
            case DismissPromotionReport ignored -> false;
            case LeaveArea ignored -> false;
            case BackToMorphs ignored -> false;
        };
    }

    /**
     * Completes the pending removal or clear confirmation against the operands captured when it was requested.
     *
     * @param token     token of the confirmation effect being answered
     * @param confirmed whether the user accepted the destructive action
     * @return an accepted update when the token matched the pending confirmation, otherwise an unaccepted update
     *         with the unchanged frame
     */
    public Update respond(long token, boolean confirmed) {
        Effect pending = pendingConfirmation;
        long pendingToken = switch (pending) {
            case ConfirmRemoveSource remove -> remove.token();
            case ConfirmClearVisible clear -> clear.token();
            case null, default -> 0L;
        };
        if (pendingToken == 0L || pendingToken != token)
            return new Update(false, frame);
        pendingConfirmation = null;
        if (confirmed) {
            switch (pending) {
                case ConfirmRemoveSource remove -> removeSource(remove.source());
                case ConfirmClearVisible clear -> clearEntries(clear.identities());
                default -> throw new IllegalStateException("Unexpected pending confirmation " + pending);
            }
        }
        return new Update(true, frame);
    }

    /**
     * Updates the catalog-owned Assign random choice without changing source rows or selection.
     *
     * @param selected whether newly promoted NPCs receive one randomly chosen Project Slider Preset
     */
    private Update setAssignRandom(boolean selected) {
        if (assignRandom != selected) {
            assignRandom = selected;
            publish();
        }
        return new Update(true, frame);
    }

    /** Dismisses the latest inline promotion result while preserving catalog selection and choices. */
    private Update dismissPromotionReport() {
        if (promotionReport.isPresent()) {
            promotionReport = Optional.empty();
            publish();
        }
        return new Update(true, frame);
    }

    /**
     * Consumes the most recently added NPC identity for one Back-to-Morphs selection effect.
     * Duplicate or rejected attempts leave a prior successful return target intact.
     *
     * @return an update whose effect carries the newly promoted identity, if one has not yet been returned to Morphs
     */
    private Update backToMorphs() {
        Optional<NpcMorphAssignmentIdentity> identity = returnAssignment;
        returnAssignment = Optional.empty();
        return new Update(true, frame, Optional.of(new ReturnToMorphs(identity)), Optional.empty());
    }

    /**
     * Ends one database visit without changing its independent source catalog or logical selection.
     * Inline feedback and an unconsumed promoted return identity belong only to the visit that created them.
     */
    private Update leaveArea() {
        returnAssignment = Optional.empty();
        if (promotionReport.isPresent()) {
            promotionReport = Optional.empty();
            publish();
        }
        return new Update(true, frame);
    }

    /** Wraps one promotion report as the update that produced it. */
    private Update promoted(PromotionReport report) {
        return new Update(true, frame, Optional.empty(), Optional.of(report));
    }

    /**
     * Promotes the selected database entry through the sole Project flow. The catalog row remains selected.
     *
     * @return the exact Project outcome for the selected row, or an empty report when nothing is selected
     */
    private PromotionReport promoteSelected() {
        PromotionReport report = new PromotionReport(false,
                frame.selectedRow().map(row -> List.of(promote(row)))
                .orElseGet(List::of));
        promotionReport = Optional.of(report);
        report.lastAddedIdentity().ifPresent(identity -> returnAssignment = Optional.of(identity));
        publish();
        return report;
    }

    /**
     * Promotes every row in one frozen filtered view, reporting each duplicate or rejection separately.
     * No catalog membership, filtering, or selection changes as Project outcomes arrive.
     *
     * @return one ordered result per visible database identity
     */
    private PromotionReport promoteVisible() {
        List<NPC> captured = List.copyOf(view.visibleSet().getRows());
        List<PromotionEntry> entries = new ArrayList<>(captured.size());
        if (!captured.isEmpty()) {
            List<NpcMorphAssignmentSnapshot> sources = new ArrayList<>(captured.size());
            for (NPC row : captured)
                sources.add(sourceFor(row));
            // ProjectSession classifies every row before one sorted aggregate commit, retaining
            // row diagnostics without publishing a Project snapshot for each visible identity.
            NpcPromotionOutcome outcome = projectFlow.promoteNpcs(sources);
            for (int index = 0; index < captured.size(); index++)
                entries.add(promotionEntry(captured.get(index), outcome.getRowOutcomes().get(index)));
        }
        PromotionReport report = new PromotionReport(true, entries);
        promotionReport = Optional.of(report);
        report.lastAddedIdentity().ifPresent(identity -> returnAssignment = Optional.of(identity));
        publish();
        return report;
    }

    /** Copies one source row into a Project edit so the catalog never owns the resulting assignment. */
    private PromotionEntry promote(NPC row) {
        ProjectOutcome outcome = projectFlow.apply(NpcMorphAssignmentEdits.addNpc(sourceFor(row)));
        return promotionEntry(row, outcome);
    }

    /**
     * Captures source values and the caller's optional random preset choice before
     * Project validation, including for rows that will later be rejected.
     */
    private NpcMorphAssignmentSnapshot sourceFor(NPC row) {
        List<String> presets = List.of();
        if (assignRandom) {
            var available = projectFlow.frame().snapshot().getSliderPresets();
            if (!available.isEmpty())
                presets = List.of(available.get(random.nextInt(available.size())).getName());
        }
        return new NpcMorphAssignmentSnapshot(row.getName(), row.getMod(),
                row.getEditorId(), row.getRace(), row.getSourceFormId(), presets);
    }

    /** Maps one Project-owned row outcome back to the captured database identity. */
    private static PromotionEntry promotionEntry(NPC row, ProjectOutcome outcome) {
        PromotionStatus status;
        if (outcome instanceof ChangedOutcome)
            status = PromotionStatus.ADDED;
        else if (outcome instanceof FailedOutcome)
            status = PromotionStatus.FAILED;
        else if (outcome instanceof RejectedOutcome && outcome.getDiagnostics().stream()
                .anyMatch(diagnostic -> ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE
                        .equals(diagnostic.getCode())))
            status = PromotionStatus.DUPLICATE;
        else if (outcome instanceof RejectedOutcome)
            status = PromotionStatus.REJECTED;
        else
            status = PromotionStatus.UNCHANGED;
        return new PromotionEntry(ProjectIdentities.npcDatabaseEntry(row), row.getName(), status,
                outcome.getDiagnostics());
    }

    /**
     * Commits an entire accepted source, retaining its original position on a later reimport.
     * A rejected result publishes diagnostics but cannot change source rows. Only the importer's job completion
     * commits sources, so this is not an intent.
     *
     * @param result detached source reader result
     * @return the new catalog frame
     */
    Frame commitSource(NpcDatabaseSourceReader.ReadResult result) {
        NpcDatabaseSourceReader.ReadResult required = Objects.requireNonNull(result, "result");
        Path source = normalize(required.source());
        if (required.status() == NpcDatabaseSourceReader.Status.ACCEPTED)
            sourceRows.put(source, required.rows());
        diagnostics.addAll(required.diagnostics());
        publish();
        return frame;
    }

    /**
     * Captures the selected source as a tokenized removal confirmation.
     *
     * @return an update carrying the confirmation, or an unaccepted update when no source is selected
     */
    private Update requestRemoveSource() {
        if (selectedSource.isEmpty())
            return new Update(false, frame);
        return confirmation(new ConfirmRemoveSource(nextToken++, selectedSource.orElseThrow()));
    }

    /**
     * Freezes the visible identities as a tokenized clear confirmation, so a later filter change cannot widen it.
     *
     * @return an update carrying the confirmation, or an unaccepted update when no entry is visible
     */
    private Update requestClearVisible() {
        List<NpcMorphAssignmentIdentity> identities = view.visibleSet().getIdentities();
        if (identities.isEmpty())
            return new Update(false, frame);
        return confirmation(new ConfirmClearVisible(nextToken++, identities));
    }

    /** Records one confirmation as pending and returns it as the update's effect. */
    private Update confirmation(Effect effect) {
        pendingConfirmation = effect;
        return new Update(true, frame, Optional.of(effect), Optional.empty());
    }

    /**
     * Removes one source and recomputes first-wins identity ownership from the remaining import order.
     *
     * @param source source path to remove
     */
    private void removeSource(Path source) {
        Path normalized = normalize(source);
        if (sourceRows.remove(normalized) == null)
            return;
        if (selectedSource.filter(normalized::equals).isPresent())
            selectedSource = Optional.empty();
        publish();
    }

    /**
     * Removes frozen NPC identities from every source so shadowed duplicates cannot reappear.
     *
     * @param identities identities captured when the clear action was requested
     */
    private void clearEntries(List<NpcMorphAssignmentIdentity> identities) {
        Set<NpcMorphAssignmentIdentity> frozen = new HashSet<>(List.copyOf(
                Objects.requireNonNull(identities, "identities")));
        if (frozen.isEmpty())
            return;
        for (Map.Entry<Path, List<NPC>> source : sourceRows.entrySet())
            source.setValue(source.getValue().stream()
                    .filter(row -> !frozen.contains(ProjectIdentities.npcDatabaseEntry(row))).toList());
        publish();
    }

    /**
     * Selects an imported source for inspection without changing catalog membership.
     *
     * @param source source path, or empty to clear source selection
     * @return whether the requested source exists or selection was cleared
     */
    private boolean selectSource(Optional<Path> source) {
        Optional<Path> normalized = Objects.requireNonNull(source, "source").map(NpcDatabaseFeature::normalize);
        if (normalized.isPresent() && !sourceRows.containsKey(normalized.orElseThrow()))
            return false;
        selectedSource = normalized;
        publish();
        return true;
    }

    /**
     * Selects one visible NPC by logical plugin and editor ID identity.
     *
     * @param identity NPC identity
     * @return whether a matching row is visible and was selected
     */
    private boolean selectRow(NpcMorphAssignmentIdentity identity) {
        if (!view.select(Objects.requireNonNull(identity, "identity")))
            return false;
        publish();
        return true;
    }

    /** Clears the selected NPC row while preserving source selection and view criteria. */
    private Update clearSelection() {
        view.clearSelection();
        publish();
        return new Update(true, frame);
    }

    /** Clears source and row selection together after a Project New or Open lifecycle change. */
    private Update clearAllSelections() {
        selectedSource = Optional.empty();
        promotionReport = Optional.empty();
        returnAssignment = Optional.empty();
        view.clearSelection();
        typeAheadPrefix = "";
        lastTypeAheadAt = null;
        publish();
        return new Update(true, frame);
    }

    /**
     * Selects by a timed display-name prefix in the current visible sort order. Repeated
     * matching characters cycle rows; a gap over 750 ms on the injected clock starts a new prefix.
     *
     * @param typed one printable non-whitespace character from the catalog table
     * @return the latest frame, unchanged when no visible name matches
     */
    private Update typeAhead(char typed) {
        if (Character.isISOControl(typed) || Character.isWhitespace(typed))
            return new Update(true, frame);
        String character = Character.toString(typed).toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        boolean expired = lastTypeAheadAt == null || now.isBefore(lastTypeAheadAt)
                || Duration.between(lastTypeAheadAt, now).compareTo(TYPE_AHEAD_TIMEOUT) > 0;
        boolean repeated = !expired && typeAheadPrefix.equals(character);
        typeAheadPrefix = expired || repeated ? character : typeAheadPrefix + character;
        lastTypeAheadAt = now;
        if (!selectByPrefix(typeAheadPrefix, repeated)) {
            typeAheadPrefix = character;
            selectByPrefix(character, false);
        }
        return new Update(true, frame);
    }

    /** Resolves the next prefix match by logical identity without changing filter or sort state. */
    private boolean selectByPrefix(String prefix, boolean cycle) {
        List<NPC> visible = view.visibleSet().getRows();
        if (visible.isEmpty())
            return false;
        int start = 0;
        if (cycle && view.getSelection().isPresent()) {
            NpcMorphAssignmentIdentity selected = view.getSelection().orElseThrow();
            for (int index = 0; index < visible.size(); index++) {
                if (ProjectIdentities.npcDatabaseEntry(visible.get(index)).equals(selected)) {
                    start = index + 1;
                    break;
                }
            }
        }
        for (int offset = 0; offset < visible.size(); offset++) {
            NPC row = visible.get((start + offset) % visible.size());
            if (row.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                view.select(ProjectIdentities.npcDatabaseEntry(row));
                publish();
                return true;
            }
        }
        return false;
    }

    /**
     * Sets one exact-cell column criterion; active criteria combine with AND semantics.
     *
     * @param criterion criterion for a canonical NPC Database column
     * @return the updated catalog frame
     */
    private Update setCriterion(ColumnCriterion criterion) {
        view.setCriterion(Objects.requireNonNull(criterion, "criterion"));
        publish();
        return new Update(true, frame);
    }

    /** @return the updated frame after showing every column value */
    private Update clearAllCriteria() {
        view.clearAllCriteria();
        publish();
        return new Update(true, frame);
    }

    /**
     * Sets ordered column sort keys without changing the source import order.
     *
     * @param sortOrder canonical NPC Database column sort keys
     * @return the updated catalog frame
     */
    private Update changeSort(List<SortKey> sortOrder) {
        view.setSortOrder(Objects.requireNonNull(sortOrder, "sortOrder"));
        publish();
        return new Update(true, frame);
    }

    /** Replaces prior attempt diagnostics before a newly completed import batch is published. */
    void clearDiagnostics() {
        diagnostics.clear();
        publish();
    }

    /** Rebuilds source attribution and one coherent immutable view publication. */
    private void publish() {
        LinkedHashMap<NpcMorphAssignmentIdentity, NPC> winners = new LinkedHashMap<>();
        LinkedHashMap<NpcMorphAssignmentIdentity, Path> owners = new LinkedHashMap<>();
        List<Source> sources = new ArrayList<>();
        for (Map.Entry<Path, List<NPC>> source : sourceRows.entrySet()) {
            sources.add(new Source(source.getKey(), source.getValue().size()));
            for (NPC row : source.getValue()) {
                NpcMorphAssignmentIdentity identity = ProjectIdentities.npcDatabaseEntry(row);
                if (winners.putIfAbsent(identity, row) == null)
                    owners.put(identity, source.getKey());
            }
        }
        List<NPC> rows = List.copyOf(winners.values());
        view.setRows(rows);
        Optional<NPC> selected = view.getSelection().map(winners::get);
        frame = new Frame(++revision, sources, rows, view.visibleSet().getRows(), selectedSource,
                selected, diagnostics, view.getCriteria(), view.getSortOrder(), owners, assignRandom,
                promotionReport);
    }

    /** Canonicalizes a local source identity for selection and removal. */
    private static Path normalize(Path source) {
        return Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
    }

    /** One NPC Database user task. {@link #isMutation} classifies every intent for the job gate. */
    public sealed interface Intent permits SelectSource, SelectRow, ClearSelection, ClearAllSelections, TypeAhead,
            SetCriterion, ClearAllCriteria, ChangeSort, SetAssignRandom, DismissPromotionReport, LeaveArea,
            BackToMorphs, AddSelected, AddAllVisible, RequestRemoveSource, RequestClearVisible {
    }

    /** Selects an imported source for inspection, or clears source selection when empty. */
    public record SelectSource(Optional<Path> source) implements Intent {
        /** Requires an explicit optional source. */
        public SelectSource {
            Objects.requireNonNull(source, "source");
        }
    }

    /** Selects one visible catalog row by its logical NPC identity. */
    public record SelectRow(NpcMorphAssignmentIdentity identity) implements Intent {
        /** Requires an NPC identity. */
        public SelectRow {
            Objects.requireNonNull(identity, "identity");
        }
    }

    /** Clears the selected catalog row, keeping source selection and view criteria. */
    public record ClearSelection() implements Intent {
    }

    /** Clears source and row selection, the inline promotion result and type-ahead after Project New or Open. */
    public record ClearAllSelections() implements Intent {
    }

    /** Selects the next visible row whose display name starts with the timed type-ahead prefix. */
    public record TypeAhead(char character) implements Intent {
    }

    /** Sets one exact-cell column criterion. */
    public record SetCriterion(ColumnCriterion criterion) implements Intent {
        /** Requires a criterion. */
        public SetCriterion {
            Objects.requireNonNull(criterion, "criterion");
        }
    }

    /** Shows every column value again. */
    public record ClearAllCriteria() implements Intent {
    }

    /** Replaces the ordered column sort keys; an empty list restores source order. */
    public record ChangeSort(List<SortKey> sortOrder) implements Intent {
        /** Owns the sort keys. */
        public ChangeSort {
            sortOrder = List.copyOf(Objects.requireNonNull(sortOrder, "sortOrder"));
        }
    }

    /** Records whether later promotions assign one random Project Slider Preset. */
    public record SetAssignRandom(boolean selected) implements Intent {
    }

    /** Dismisses the inline promotion result. */
    public record DismissPromotionReport() implements Intent {
    }

    /** Ends one database visit, discarding its inline result and unconsumed return identity. */
    public record LeaveArea() implements Intent {
    }

    /** Requests the return to Morphs; the update's {@link ReturnToMorphs} effect carries the identity to select. */
    public record BackToMorphs() implements Intent {
    }

    /** Promotes the selected catalog row into the Project. */
    public record AddSelected() implements Intent {
    }

    /** Promotes every visible catalog row into the Project in one aggregate commit. */
    public record AddAllVisible() implements Intent {
    }

    /** Requests removal of the selected source; the update carries a {@link ConfirmRemoveSource} effect. */
    public record RequestRemoveSource() implements Intent {
    }

    /** Requests clearing every visible entry; the update carries a {@link ConfirmClearVisible} effect. */
    public record RequestClearVisible() implements Intent {
    }

    /** An effect the caller realizes before the dispatch settles. */
    public sealed interface Effect permits ConfirmRemoveSource, ConfirmClearVisible,
            ReturnToMorphs {
    }

    /**
     * Tokenized confirmation to remove one captured source; answer it with {@link #respond(long, boolean)}.
     *
     * @param token  token identifying this confirmation
     * @param source normalized source captured when removal was requested
     */
    public record ConfirmRemoveSource(long token, Path source) implements Effect {
        /** Requires a positive token and a source. */
        public ConfirmRemoveSource {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(source, "source");
        }
    }

    /**
     * Tokenized confirmation to clear identities frozen from the visible view; answer it with
     * {@link #respond(long, boolean)}.
     *
     * @param token      token identifying this confirmation
     * @param identities visible identities captured when the clear was requested
     */
    public record ConfirmClearVisible(long token, List<NpcMorphAssignmentIdentity> identities) implements Effect {
        /** Requires a positive token and owns the frozen identities. */
        public ConfirmClearVisible {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            identities = List.copyOf(Objects.requireNonNull(identities, "identities"));
        }
    }

    /**
     * Return to Morphs, selecting the most recently promoted assignment when this visit added one.
     *
     * @param assignment newly promoted identity to select in Morphs, if any
     */
    public record ReturnToMorphs(Optional<NpcMorphAssignmentIdentity> assignment) implements Effect {
        /** Requires an explicit optional assignment. */
        public ReturnToMorphs {
            Objects.requireNonNull(assignment, "assignment");
        }
    }

    /**
     * Result of one NPC Database intent or confirmation response.
     *
     * @param accepted  whether the intent applied (false for an invisible row, an unknown source, a request with
     *                  nothing to act on, or a stale confirmation token)
     * @param frame     catalog frame after the intent
     * @param effect    effect the caller must realize, if any
     * @param promotion promotion report this dispatch produced, present only for Add and Add All
     */
    public record Update(boolean accepted, Frame frame, Optional<Effect> effect, Optional<PromotionReport> promotion)
            implements WorkbenchFeature.Update<Effect> {
        /** Validates the immutable result payload. */
        public Update {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(promotion, "promotion");
        }

        /** Creates an update with no effect and no promotion report. */
        public Update(boolean accepted, Frame frame) {
            this(accepted, frame, Optional.empty(), Optional.empty());
        }
    }
}
