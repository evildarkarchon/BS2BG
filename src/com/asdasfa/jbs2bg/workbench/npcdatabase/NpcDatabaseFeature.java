package com.asdasfa.jbs2bg.workbench.npcdatabase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.filtering.ColumnCriterion;
import com.asdasfa.jbs2bg.filtering.FilteredView;
import com.asdasfa.jbs2bg.filtering.NpcTableColumns;
import com.asdasfa.jbs2bg.filtering.ProjectIdentities;
import com.asdasfa.jbs2bg.filtering.SortKey;
import com.asdasfa.jbs2bg.filtering.VisibleSet;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;

/**
 * Window-scoped NPC Database catalog. All methods run on the serialized presentation lane;
 * source files are staged separately before a complete result is committed here.
 */
public final class NpcDatabaseFeature {
    private static final long TYPE_AHEAD_TIMEOUT_NANOS = 750_000_000L;

    private final LinkedHashMap<Path, List<NPC>> sourceRows = new LinkedHashMap<>();
    private final FilteredView<NPC, NpcMorphAssignmentIdentity> view = new FilteredView<>(
            NpcTableColumns.npcDatabase(), ProjectIdentities::npcDatabaseEntry);
    private final List<NpcDatabaseSourceReader.Diagnostic> diagnostics = new ArrayList<>();
    private Optional<Path> selectedSource = Optional.empty();
    private String typeAheadPrefix = "";
    private Long lastTypeAheadAt;
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
                        Map<NpcMorphAssignmentIdentity, Path> rowSources) {
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

    /** Starts with an empty source catalog and no selected or filtered row. */
    public NpcDatabaseFeature() {
        publish();
    }

    /** @return the latest immutable catalog frame */
    public Frame frame() {
        return frame;
    }

    /**
     * Commits an entire accepted source, retaining its original position on a later reimport.
     * A rejected result publishes diagnostics but cannot change source rows.
     *
     * @param result detached source reader result
     * @return the new catalog frame
     */
    public Frame commitSource(NpcDatabaseSourceReader.ReadResult result) {
        NpcDatabaseSourceReader.ReadResult required = Objects.requireNonNull(result, "result");
        Path source = normalize(required.source());
        if (required.status() == NpcDatabaseSourceReader.Status.ACCEPTED)
            sourceRows.put(source, required.rows());
        diagnostics.addAll(required.diagnostics());
        publish();
        return frame;
    }

    /**
     * Removes one source and recomputes first-wins identity ownership from the remaining import order.
     *
     * @param source source path to remove
     * @return the updated frame, or the current frame when the source was absent
     */
    public Frame removeSource(Path source) {
        Path normalized = normalize(source);
        if (sourceRows.remove(normalized) == null)
            return frame;
        if (selectedSource.filter(normalized::equals).isPresent())
            selectedSource = Optional.empty();
        publish();
        return frame;
    }

    /**
     * Removes frozen NPC identities from every source so shadowed duplicates cannot reappear.
     *
     * @param identities identities captured when the clear action was requested
     * @return the resulting catalog frame
     */
    public Frame clearEntries(List<NpcMorphAssignmentIdentity> identities) {
        Set<NpcMorphAssignmentIdentity> frozen = new HashSet<>(List.copyOf(
                Objects.requireNonNull(identities, "identities")));
        if (frozen.isEmpty())
            return frame;
        for (Map.Entry<Path, List<NPC>> source : sourceRows.entrySet())
            source.setValue(source.getValue().stream()
                    .filter(row -> !frozen.contains(ProjectIdentities.npcDatabaseEntry(row))).toList());
        publish();
        return frame;
    }

    /** @return the current immutable visible scope for a later confirmed bulk action */
    public VisibleSet<NPC, NpcMorphAssignmentIdentity> visibleSet() {
        return view.visibleSet();
    }

    /**
     * Selects an imported source for inspection without changing catalog membership.
     *
     * @param source source path, or empty to clear source selection
     * @return whether the requested source exists or selection was cleared
     */
    public boolean selectSource(Optional<Path> source) {
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
    public boolean selectRow(NpcMorphAssignmentIdentity identity) {
        if (!view.select(Objects.requireNonNull(identity, "identity")))
            return false;
        publish();
        return true;
    }

    /** Clears the selected NPC row while preserving source selection and view criteria. */
    public void clearSelection() {
        view.clearSelection();
        publish();
    }

    /** Clears source and row selection together after a Project New or Open lifecycle change. */
    public void clearAllSelections() {
        selectedSource = Optional.empty();
        view.clearSelection();
        typeAheadPrefix = "";
        lastTypeAheadAt = null;
        publish();
    }

    /**
     * Selects by a timed display-name prefix in the current visible sort order. Repeated
     * matching characters cycle rows; a gap over 750 ms starts a new prefix.
     *
     * @param typed one printable non-whitespace character from the catalog table
     * @param atNanos monotonic event time, such as {@link System#nanoTime()}
     * @return the latest frame, unchanged when no visible name matches
     */
    public Frame typeAhead(char typed, long atNanos) {
        if (Character.isISOControl(typed) || Character.isWhitespace(typed))
            return frame;
        String character = Character.toString(typed).toLowerCase(Locale.ROOT);
        boolean expired = lastTypeAheadAt == null || atNanos < lastTypeAheadAt
                || atNanos - lastTypeAheadAt > TYPE_AHEAD_TIMEOUT_NANOS;
        boolean repeated = !expired && typeAheadPrefix.equals(character);
        typeAheadPrefix = expired || repeated ? character : typeAheadPrefix + character;
        lastTypeAheadAt = atNanos;
        if (!selectByPrefix(typeAheadPrefix, repeated)) {
            typeAheadPrefix = character;
            selectByPrefix(character, false);
        }
        return frame;
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
    public Frame setCriterion(ColumnCriterion criterion) {
        view.setCriterion(Objects.requireNonNull(criterion, "criterion"));
        publish();
        return frame;
    }

    /** @return the updated frame after showing every column value */
    public Frame clearAllCriteria() {
        view.clearAllCriteria();
        publish();
        return frame;
    }

    /**
     * Sets ordered column sort keys without changing the source import order.
     *
     * @param sortOrder canonical NPC Database column sort keys
     * @return the updated catalog frame
     */
    public Frame setSortOrder(List<SortKey> sortOrder) {
        view.setSortOrder(Objects.requireNonNull(sortOrder, "sortOrder"));
        publish();
        return frame;
    }

    /** Replaces prior attempt diagnostics before a newly completed import batch is published. */
    public void clearDiagnostics() {
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
                selected, diagnostics, view.getCriteria(), view.getSortOrder(), owners);
    }

    /** Canonicalizes a local source identity for selection and removal. */
    private static Path normalize(Path source) {
        return Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
    }
}
