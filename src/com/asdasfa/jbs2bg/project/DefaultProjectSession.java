package com.asdasfa.jbs2bg.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.eclipsesource.json.ParseException;

/**
 * Serializes Project operations and atomically publishes immutable snapshots.
 */
final class DefaultProjectSession implements ProjectSession {
    private static final Comparator<SliderPresetSnapshot> SLIDER_PRESET_NAME_ORDER =
            new Comparator<SliderPresetSnapshot>() {
                @Override
                public int compare(SliderPresetSnapshot left, SliderPresetSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    private static final Comparator<SliderChoiceSnapshot> SLIDER_CHOICE_NAME_ORDER =
            new Comparator<SliderChoiceSnapshot>() {
                @Override
                public int compare(SliderChoiceSnapshot left, SliderChoiceSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    private static final Comparator<CustomMorphTargetSnapshot> CUSTOM_MORPH_TARGET_NAME_ORDER =
            new Comparator<CustomMorphTargetSnapshot>() {
                @Override
                public int compare(CustomMorphTargetSnapshot left, CustomMorphTargetSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };
    private static final Comparator<NpcMorphAssignmentSnapshot> NPC_MORPH_ASSIGNMENT_IDENTITY_ORDER =
            new Comparator<NpcMorphAssignmentSnapshot>() {
                /** Orders NPC Morph Assignments by plugin name and then editor ID. */
                @Override
                public int compare(NpcMorphAssignmentSnapshot left, NpcMorphAssignmentSnapshot right) {
                    int pluginOrder = left.getPluginName().compareToIgnoreCase(right.getPluginName());
                    return pluginOrder != 0 ? pluginOrder
                            : left.getEditorId().compareToIgnoreCase(right.getEditorId());
                }
            };
    private static final Comparator<String> CASE_INSENSITIVE_NAME_ORDER = new Comparator<String>() {
        @Override
        public int compare(String left, String right) {
            return left.compareToIgnoreCase(right);
        }
    };

    private final Object operationLock = new Object();
    private volatile ProjectSnapshot snapshot = ProjectSnapshot.noProject();

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome newProject() {
        synchronized (operationLock) {
            ProjectSnapshot emptyProject = ProjectSnapshot.empty();
            if (snapshot == emptyProject)
                return new UnchangedOutcome(snapshot);
            snapshot = emptyProject;
            return new ChangedOutcome(snapshot);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome open(Path source) {
        Objects.requireNonNull(source, "source");
        synchronized (operationLock) {
            try {
                ProjectFileLoader.LoadedProject loaded = ProjectFileLoader.load(source);
                snapshot = loaded.getSnapshot();
                return new ChangedOutcome(snapshot, loaded.getDiagnostics());
            } catch (IOException exception) {
                return failedOpen(ProjectDiagnosticCodes.PROJECT_FILE_READ_FAILED, source,
                        "The Project file could not be read: " + exception.getMessage());
            } catch (ParseException exception) {
                return rejectedMalformedJson(source, exception);
            } catch (ProjectFileLoader.InvalidProjectFileException exception) {
                return rejectedInvalidProject(source, exception);
            } catch (RuntimeException exception) {
                // Known document failures are handled above; this boundary keeps an
                // unexpected loader failure from escaping after callers entrusted state to Open.
                return failedOpen(ProjectDiagnosticCodes.PROJECT_OPEN_FAILED, source,
                        "The Project could not be opened: " + exception.getMessage());
            }
        }
    }

    /**
     * Reports JSON parser coordinates while preserving the active Project.
     *
     * @param source malformed Project file
     * @param exception parser failure with one-based coordinates
     * @return structured rejection carrying the unchanged snapshot
     */
    private RejectedOutcome rejectedMalformedJson(Path source, ParseException exception) {
        SourceLocation location = new SourceLocation(Optional.of(source), Optional.of("/"),
                OptionalInt.of(exception.getLine()), OptionalInt.of(exception.getColumn()));
        return rejected(ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED, location,
                "The Project file contains malformed JSON: " + exception.getMessage());
    }

    /**
     * Reports a stable schema or domain validation location while preserving the
     * active Project.
     *
     * @param source invalid Project file
     * @param exception structured candidate validation failure
     * @return structured rejection carrying the unchanged snapshot
     */
    private RejectedOutcome rejectedInvalidProject(Path source,
            ProjectFileLoader.InvalidProjectFileException exception) {
        SourceLocation location = new SourceLocation(Optional.of(source), Optional.of(exception.getElement()),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(exception.getCode(), location, exception.getMessage());
    }

    /**
     * Builds a failed open outcome without replacing the currently published
     * Project snapshot.
     *
     * @param code stable diagnostic code
     * @param source requested source file
     * @param message human-readable failure message
     * @return failure carrying the unchanged snapshot
     */
    private FailedOutcome failedOpen(String code, Path source, String message) {
        return failedOperation(code, Optional.of(source), "/", message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome save() {
        synchronized (operationLock) {
            if (snapshot.getFileIdentity().isPresent())
                return persist(snapshot.getFileIdentity().get());
            SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("project-file"),
                    OptionalInt.empty(), OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.FILE_IDENTITY_REQUIRED, location,
                    "The Project has no file identity; choose a target before saving.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome saveAs(Path target) {
        Objects.requireNonNull(target, "target");
        synchronized (operationLock) {
            if (snapshot.getLifecycleStatus() == ProjectLifecycleStatus.NO_PROJECT)
                return rejectedActiveProjectRequired();
            return persist(target);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SliderPresetImportOutcome importSliderPresets(List<Path> sources) {
        List<Path> selectedSources = ImmutableValues.copyOf(sources, "sources");
        synchronized (operationLock) {
            if (snapshot.getLifecycleStatus() == ProjectLifecycleStatus.NO_PROJECT) {
                List<ProjectOutcome> rejectedSources = new ArrayList<>();
                for (int index = 0; index < selectedSources.size(); index++)
                    rejectedSources.add(rejectedActiveProjectRequired());
                return new SliderPresetImportOutcome(rejectedActiveProjectRequired(), rejectedSources);
            }

            List<ProjectOutcome> sourceOutcomes = new ArrayList<>();
            List<ProjectDiagnostic> diagnostics = new ArrayList<>();
            boolean changed = false;
            boolean rejected = false;
            boolean failed = false;
            for (Path source : selectedSources) {
                ProjectOutcome sourceOutcome = importSliderPresetSource(source);
                sourceOutcomes.add(sourceOutcome);
                diagnostics.addAll(sourceOutcome.getDiagnostics());
                ImportOutcomeKind outcomeKind = importOutcomeKind(sourceOutcome);
                changed |= outcomeKind == ImportOutcomeKind.CHANGED;
                rejected |= outcomeKind == ImportOutcomeKind.REJECTED;
                failed |= outcomeKind == ImportOutcomeKind.FAILED;
            }

            List<ProjectOutcome> finalSourceOutcomes = new ArrayList<>();
            for (ProjectOutcome sourceOutcome : sourceOutcomes)
                finalSourceOutcomes.add(outcomeAtSnapshot(sourceOutcome, snapshot));

            ProjectOutcome projectOutcome;
            // A published source must make the batch Changed so dirty handling stays
            // truthful. Without a change, rejection distinguishes invalid content from
            // a batch made solely of environmental failures; exact kinds remain per file.
            if (changed)
                projectOutcome = new ChangedOutcome(snapshot, diagnostics);
            else if (rejected)
                projectOutcome = new RejectedOutcome(snapshot, diagnostics);
            else if (failed)
                projectOutcome = new FailedOutcome(snapshot, diagnostics);
            else
                projectOutcome = new UnchangedOutcome(snapshot, diagnostics);
            return new SliderPresetImportOutcome(projectOutcome, finalSourceOutcomes);
        }
    }

    /**
     * Retypes one source result against the final batch snapshot while preserving
     * its classification and diagnostics.
     *
     * @param outcome source result captured during ordered processing
     * @param finalSnapshot latest snapshot after every selected source
     * @return equivalent typed outcome carrying the final snapshot
     */
    private static ProjectOutcome outcomeAtSnapshot(ProjectOutcome outcome, ProjectSnapshot finalSnapshot) {
        switch (importOutcomeKind(outcome)) {
        case CHANGED:
            return new ChangedOutcome(finalSnapshot, outcome.getDiagnostics());
        case REJECTED:
            return new RejectedOutcome(finalSnapshot, outcome.getDiagnostics());
        case FAILED:
            return new FailedOutcome(finalSnapshot, outcome.getDiagnostics());
        case UNCHANGED:
            return new UnchangedOutcome(finalSnapshot, outcome.getDiagnostics());
        default:
            throw new IllegalStateException("Unsupported import outcome kind.");
        }
    }

    /**
     * Classifies the established typed Project outcomes once for import aggregation
     * and final-snapshot rebinding.
     *
     * @param outcome typed source outcome
     * @return corresponding import aggregation kind
     */
    private static ImportOutcomeKind importOutcomeKind(ProjectOutcome outcome) {
        if (outcome instanceof ChangedOutcome)
            return ImportOutcomeKind.CHANGED;
        if (outcome instanceof RejectedOutcome)
            return ImportOutcomeKind.REJECTED;
        if (outcome instanceof FailedOutcome)
            return ImportOutcomeKind.FAILED;
        return ImportOutcomeKind.UNCHANGED;
    }

    /** Internal classification for the four public typed Project outcomes. */
    private enum ImportOutcomeKind {
        CHANGED,
        UNCHANGED,
        REJECTED,
        FAILED
    }

    /**
     * Parses and commits one source without allowing its failure to escape or
     * discard state committed by another selected file.
     *
     * @param source selected BodySlide XML source
     * @return one typed source outcome at the latest coherent snapshot
     */
    private ProjectOutcome importSliderPresetSource(Path source) {
        Path normalizedSource;
        try {
            normalizedSource = source.toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            // An unresolvable path cannot safely be sent to the filesystem parser.
            return failedOperation(ProjectDiagnosticCodes.SLIDER_PRESET_XML_IMPORT_FAILED,
                    Optional.of(source), "/",
                    "The BodySlide XML source could not be resolved: " + exception.getMessage());
        }
        try {
            List<BodySlidePresetFileParser.ParsedPreset> imported = BodySlidePresetFileParser.parse(normalizedSource);
            return upsertImportedSliderPresets(imported, normalizedSource);
        } catch (SAXParseException exception) {
            return rejectedMalformedXml(normalizedSource, exception,
                    optionalPositive(exception.getLineNumber()), optionalPositive(exception.getColumnNumber()));
        } catch (SAXException exception) {
            return rejectedMalformedXml(normalizedSource, exception, OptionalInt.empty(), OptionalInt.empty());
        } catch (BodySlidePresetFileParser.InvalidBodySlidePresetException exception) {
            SourceLocation location = new SourceLocation(Optional.of(normalizedSource),
                    Optional.of(exception.getElement()), OptionalInt.empty(), OptionalInt.empty());
            return rejected(exception.getCode(), location, exception.getMessage());
        } catch (IOException exception) {
            return failedOperation(ProjectDiagnosticCodes.SLIDER_PRESET_XML_READ_FAILED,
                    Optional.of(normalizedSource), "/",
                    "The BodySlide XML source could not be read: " + exception.getMessage());
        } catch (ParserConfigurationException exception) {
            return failedOperation(ProjectDiagnosticCodes.SLIDER_PRESET_XML_IMPORT_FAILED,
                    Optional.of(normalizedSource), "/",
                    "Secure BodySlide XML parsing is unavailable: " + exception.getMessage());
        } catch (RuntimeException exception) {
            // Keep an unexpected parser/provider failure scoped to this source so the
            // batch can report it without losing earlier independent commits.
            return failedOperation(ProjectDiagnosticCodes.SLIDER_PRESET_XML_IMPORT_FAILED,
                    Optional.of(normalizedSource), "/",
                    "The BodySlide XML source could not be imported: " + exception.getMessage());
        }
    }

    /**
     * Builds one malformed-XML rejection with optional parser coordinates.
     *
     * @param source normalized XML source
     * @param exception parser failure
     * @param line optional one-based line
     * @param column optional one-based column
     * @return structured rejection carrying the current coherent snapshot
     */
    private RejectedOutcome rejectedMalformedXml(Path source, SAXException exception, OptionalInt line,
            OptionalInt column) {
        SourceLocation location = new SourceLocation(Optional.of(source), Optional.of("/"), line, column);
        return rejected(ProjectDiagnosticCodes.SLIDER_PRESET_XML_MALFORMED, location,
                "The BodySlide source contains malformed XML: " + exception.getMessage());
    }

    /**
     * Converts positive SAX coordinates to the optional source-location form.
     *
     * @param value parser coordinate, or a non-positive unknown marker
     * @return present one-based coordinate when known
     */
    private static OptionalInt optionalPositive(int value) {
        return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
    }

    /**
     * Upserts every detached preset from one valid source and publishes at most
     * one dirty snapshot. Existing display identity and relationships are retained.
     *
     * @param imported complete detached source payload with XML name locations
     * @param source normalized XML source for validation diagnostics
     * @return changed, unchanged, or rejected source outcome
     */
    private ProjectOutcome upsertImportedSliderPresets(List<BodySlidePresetFileParser.ParsedPreset> imported,
            Path source) {
        List<SliderPresetSnapshot> presets = new ArrayList<>(snapshot.getSliderPresets());
        boolean changed = false;
        for (BodySlidePresetFileParser.ParsedPreset parsedPreset : imported) {
            SliderPresetSnapshot candidate = parsedPreset.getPreset();
            int index = findSliderPreset(presets, candidate.getName());
            SliderPresetNameProblem nameProblem = findSliderPresetNameProblem(candidate.getName(), presets,
                    index < 0 ? OptionalInt.empty() : OptionalInt.of(index));
            if (nameProblem != null) {
                SourceLocation location = new SourceLocation(Optional.of(source),
                        Optional.of(parsedPreset.getNameElement()), OptionalInt.empty(), OptionalInt.empty());
                return rejected(nameProblem.code, location, nameProblem.message);
            }
            if (index < 0) {
                presets.add(candidate);
                changed = true;
                continue;
            }

            SliderPresetSnapshot current = presets.get(index);
            SliderPresetSnapshot replacement = new SliderPresetSnapshot(current.getName(), candidate.isUunp(),
                    candidate.getSliderChoices());
            if (!sameSliderPreset(current, replacement)) {
                presets.set(index, replacement);
                changed = true;
            }
        }
        return changed ? publishChangedPresets(presets) : new UnchangedOutcome(snapshot);
    }

    /**
     * Persists the pinned Project snapshot and publishes its clean file identity
     * only after the completed replacement succeeds.
     *
     * @param target requested Project destination
     * @return changed, unchanged, or failed outcome at the operation boundary
     */
    private ProjectOutcome persist(Path target) {
        Path normalizedTarget;
        try {
            normalizedTarget = target.toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            // A path that cannot be normalized also cannot safely identify a filesystem source.
            return failedOperation(ProjectDiagnosticCodes.PROJECT_SAVE_FAILED, Optional.<Path>empty(),
                    "project-file", "The Project target could not be resolved: " + exception.getMessage());
        }
        try {
            ProjectFileWriter.write(snapshot, normalizedTarget);
        } catch (IOException exception) {
            return failedOperation(ProjectDiagnosticCodes.PROJECT_FILE_WRITE_FAILED, Optional.of(normalizedTarget),
                    "/", "The Project file could not be written: " + exception.getMessage());
        } catch (RuntimeException exception) {
            // Filesystem providers may surface environmental failures as unchecked exceptions.
            return failedOperation(ProjectDiagnosticCodes.PROJECT_SAVE_FAILED, Optional.of(normalizedTarget), "/",
                    "The Project could not be saved: " + exception.getMessage());
        }

        boolean changed = snapshot.isDirty() || !snapshot.getFileIdentity().isPresent()
                || !normalizedTarget.equals(snapshot.getFileIdentity().get());
        if (!changed)
            return new UnchangedOutcome(snapshot);
        ProjectSnapshot saved = new ProjectSnapshot(snapshot.getSliderPresets(), snapshot.getCustomMorphTargets(),
                snapshot.getNpcMorphAssignments(), Optional.of(normalizedTarget), false,
                ProjectLifecycleStatus.FILE_BACKED);
        snapshot = saved;
        return new ChangedOutcome(snapshot);
    }

    /**
     * Reports an operation failure without replacing or partially updating the
     * currently published Project snapshot.
     *
     * @param code stable diagnostic code
     * @param path requested filesystem source, or empty when no identity is available
     * @param element stable logical location within the operation
     * @param message human-readable failure message
     * @return failed outcome carrying the unchanged snapshot
     * @throws NullPointerException when an argument is null
     */
    private FailedOutcome failedOperation(String code, Optional<Path> path, String element, String message) {
        SourceLocation location = new SourceLocation(path, Optional.of(element), OptionalInt.empty(),
                OptionalInt.empty());
        ProjectDiagnostic diagnostic = new ProjectDiagnostic(code, DiagnosticSeverity.ERROR, location, message);
        return new FailedOutcome(snapshot, Collections.singletonList(diagnostic));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ProjectOutcome apply(ProjectEdit edit) {
        Objects.requireNonNull(edit, "edit");
        synchronized (operationLock) {
            if ((edit instanceof SliderPresetEdits.SliderPresetEdit
                    || edit instanceof CustomMorphTargetEdits.CustomMorphTargetEdit
                    || edit instanceof NpcMorphAssignmentEdits.NpcMorphAssignmentEdit)
                    && snapshot.getLifecycleStatus() == ProjectLifecycleStatus.NO_PROJECT)
                return rejectedActiveProjectRequired();
            if (edit instanceof NpcMorphAssignmentEdits.AddNpc)
                return addNpc((NpcMorphAssignmentEdits.AddNpc) edit);
            if (edit instanceof NpcMorphAssignmentEdits.AddNpcs)
                return addNpcs((NpcMorphAssignmentEdits.AddNpcs) edit);
            if (edit instanceof NpcMorphAssignmentEdits.AddSliderPreset)
                return addNpcSliderPreset((NpcMorphAssignmentEdits.AddSliderPreset) edit);
            if (edit instanceof NpcMorphAssignmentEdits.RemoveSliderPreset)
                return removeNpcSliderPreset((NpcMorphAssignmentEdits.RemoveSliderPreset) edit);
            if (edit instanceof NpcMorphAssignmentEdits.ClearSliderPresets)
                return clearNpcSliderPresets((NpcMorphAssignmentEdits.ClearSliderPresets) edit);
            if (edit instanceof NpcMorphAssignmentEdits.ClearSliderPresetsForNpcs)
                return clearNpcSliderPresetsForNpcs((NpcMorphAssignmentEdits.ClearSliderPresetsForNpcs) edit);
            if (edit instanceof NpcMorphAssignmentEdits.RemoveNpc)
                return removeNpc((NpcMorphAssignmentEdits.RemoveNpc) edit);
            if (edit instanceof NpcMorphAssignmentEdits.RemoveNpcs)
                return removeNpcs((NpcMorphAssignmentEdits.RemoveNpcs) edit);
            if (edit instanceof NpcMorphAssignmentEdits.ClearNpcs)
                return clearNpcs();
            if (edit instanceof NpcMorphAssignmentEdits.FillEmpty)
                return fillEmpty((NpcMorphAssignmentEdits.FillEmpty) edit);
            if (edit instanceof CustomMorphTargetEdits.Create)
                return createCustomMorphTarget((CustomMorphTargetEdits.Create) edit);
            if (edit instanceof CustomMorphTargetEdits.AddSliderPreset)
                return addCustomMorphTargetSliderPreset((CustomMorphTargetEdits.AddSliderPreset) edit);
            if (edit instanceof CustomMorphTargetEdits.RemoveSliderPreset)
                return removeCustomMorphTargetSliderPreset((CustomMorphTargetEdits.RemoveSliderPreset) edit);
            if (edit instanceof CustomMorphTargetEdits.ClearSliderPresets)
                return clearCustomMorphTargetSliderPresets((CustomMorphTargetEdits.ClearSliderPresets) edit);
            if (edit instanceof CustomMorphTargetEdits.Delete)
                return deleteCustomMorphTarget((CustomMorphTargetEdits.Delete) edit);
            if (edit instanceof CustomMorphTargetEdits.Clear)
                return clearCustomMorphTargets();
            if (edit instanceof SliderPresetEdits.Create)
                return createSliderPreset((SliderPresetEdits.Create) edit);
            if (edit instanceof SliderPresetEdits.Duplicate)
                return duplicateSliderPreset((SliderPresetEdits.Duplicate) edit);
            if (edit instanceof SliderPresetEdits.Update)
                return updateSliderPreset((SliderPresetEdits.Update) edit);
            if (edit instanceof SliderPresetEdits.Rename)
                return renameSliderPreset((SliderPresetEdits.Rename) edit);
            if (edit instanceof SliderPresetEdits.Delete)
                return deleteSliderPreset((SliderPresetEdits.Delete) edit);
            if (edit instanceof SliderPresetEdits.Clear)
                return clearSliderPresets();
            if (edit instanceof SliderPresetEdits.SetUunp)
                return setSliderPresetUunp((SliderPresetEdits.SetUunp) edit);
            if (edit instanceof SliderPresetEdits.SetSliderChoice)
                return setSliderChoice((SliderPresetEdits.SetSliderChoice) edit);
            SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("project-edit"),
                    OptionalInt.empty(), OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.EDIT_UNSUPPORTED, location,
                    "The Project edit request type is not supported.");
        }
    }

    /**
     * Rejects a known edit until a lifecycle operation establishes active state.
     *
     * @return a structured rejection carrying the pre-lifecycle snapshot
     */
    private RejectedOutcome rejectedActiveProjectRequired() {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("project"), OptionalInt.empty(),
                OptionalInt.empty());
        return rejected(ProjectDiagnosticCodes.ACTIVE_PROJECT_REQUIRED, location,
                "Create or open a Project before applying edits.");
    }

    /**
     * Copies one NPC source value into an independent, canonically assigned NPC
     * Morph Assignment. Existing case-insensitive identity is rejected without
     * changing the Project.
     *
     * @param edit immutable NPC-add request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome addNpc(NpcMorphAssignmentEdits.AddNpc edit) {
        NpcMorphAssignmentSnapshot source = edit.getSource();
        if (source == null) {
            SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("npc-morph-assignment"),
                    OptionalInt.empty(), OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_REQUIRED, location,
                    "Adding an NPC requires copied source values.");
        }
        for (NpcMorphAssignmentSnapshot assignment : snapshot.getNpcMorphAssignments()) {
            if (sameNpcIdentity(assignment, source))
                return rejectedNpc(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE,
                        "An NPC Morph Assignment with this plugin name and editor ID already exists.");
        }

        List<String> canonicalAssignments = canonicalNpcSliderPresetNames(source.getSliderPresetNames());
        if (canonicalAssignments == null)
            return rejectedSliderPresetNotFound();
        NpcMorphAssignmentSnapshot copied = copyNpc(source, canonicalAssignments);
        List<NpcMorphAssignmentSnapshot> assignments = new ArrayList<>(snapshot.getNpcMorphAssignments());
        assignments.add(copied);
        return publishChangedNpcMorphAssignments(assignments);
    }

    /**
     * Builds and validates a complete filtered bulk-add candidate before publishing
     * one atomic Project snapshot. Existing identities are expected no-ops.
     *
     * @param edit immutable caller-filtered bulk-add request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome addNpcs(NpcMorphAssignmentEdits.AddNpcs edit) {
        if (edit.getSources() == null)
            return rejectedNpc(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_REQUIRED,
                    "Filtered NPC promotion requires copied source values.");
        if (edit.getSources().isEmpty())
            return new UnchangedOutcome(snapshot);

        List<NpcMorphAssignmentSnapshot> candidate = new ArrayList<>(snapshot.getNpcMorphAssignments());
        for (NpcMorphAssignmentSnapshot source : edit.getSources()) {
            if (containsNpcIdentity(snapshot.getNpcMorphAssignments(), source))
                continue;
            if (containsNpcIdentity(candidate, source))
                return rejectedNpc(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE,
                        "A filtered NPC batch contains duplicate plugin-name/editor-ID identity.");
            List<String> canonicalAssignments = canonicalNpcSliderPresetNames(source.getSliderPresetNames());
            if (canonicalAssignments == null)
                return rejectedSliderPresetNotFound();
            candidate.add(copyNpc(source, canonicalAssignments));
        }
        if (candidate.size() == snapshot.getNpcMorphAssignments().size())
            return new UnchangedOutcome(snapshot);
        return publishChangedNpcMorphAssignments(candidate);
    }

    /**
     * Searches an arbitrary immutable candidate collection by NPC Project identity.
     *
     * @param assignments candidate NPC Morph Assignments
     * @param requested requested source value
     * @return true when plugin name and editor ID already occur in the candidate
     */
    private static boolean containsNpcIdentity(List<NpcMorphAssignmentSnapshot> assignments,
            NpcMorphAssignmentSnapshot requested) {
        for (NpcMorphAssignmentSnapshot assignment : assignments) {
            if (sameNpcIdentity(assignment, requested))
                return true;
        }
        return false;
    }

    /**
     * Adds one validated canonical Slider Preset relationship to an NPC Morph Assignment.
     *
     * @param edit immutable relationship-add request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome addNpcSliderPreset(NpcMorphAssignmentEdits.AddSliderPreset edit) {
        int npcIndex = findNpcMorphAssignment(edit.getIdentity());
        if (npcIndex < 0)
            return rejectedNpcMorphAssignmentNotFound();
        int presetIndex = findSliderPreset(edit.getSliderPresetName());
        if (presetIndex < 0)
            return rejectedSliderPresetNotFound();

        NpcMorphAssignmentSnapshot current = snapshot.getNpcMorphAssignments().get(npcIndex);
        String presetName = snapshot.getSliderPresets().get(presetIndex).getName();
        List<String> assignments = new ArrayList<>(current.getSliderPresetNames());
        for (String assignment : assignments) {
            if (assignment.equalsIgnoreCase(presetName))
                return new UnchangedOutcome(snapshot);
        }
        assignments.add(presetName);
        Collections.sort(assignments, CASE_INSENSITIVE_NAME_ORDER);
        return replaceNpcMorphAssignment(npcIndex, copyNpc(current, assignments));
    }

    /**
     * Removes one case-insensitive Slider Preset relationship from an NPC Morph Assignment.
     *
     * @param edit immutable relationship-remove request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome removeNpcSliderPreset(NpcMorphAssignmentEdits.RemoveSliderPreset edit) {
        int npcIndex = findNpcMorphAssignment(edit.getIdentity());
        if (npcIndex < 0)
            return rejectedNpcMorphAssignmentNotFound();
        NpcMorphAssignmentSnapshot current = snapshot.getNpcMorphAssignments().get(npcIndex);
        String requestedName = edit.getSliderPresetName() == null ? "" : edit.getSliderPresetName().trim();
        List<String> assignments = new ArrayList<>(current.getSliderPresetNames());
        for (int index = 0; index < assignments.size(); index++) {
            if (assignments.get(index).equalsIgnoreCase(requestedName)) {
                assignments.remove(index);
                return replaceNpcMorphAssignment(npcIndex, copyNpc(current, assignments));
            }
        }
        return new UnchangedOutcome(snapshot);
    }

    /**
     * Clears every Slider Preset relationship from one NPC Morph Assignment.
     *
     * @param edit immutable relationship-clear request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome clearNpcSliderPresets(NpcMorphAssignmentEdits.ClearSliderPresets edit) {
        int npcIndex = findNpcMorphAssignment(edit.getIdentity());
        if (npcIndex < 0)
            return rejectedNpcMorphAssignmentNotFound();
        NpcMorphAssignmentSnapshot current = snapshot.getNpcMorphAssignments().get(npcIndex);
        if (current.getSliderPresetNames().isEmpty())
            return new UnchangedOutcome(snapshot);
        return replaceNpcMorphAssignment(npcIndex, copyNpc(current, Collections.<String>emptyList()));
    }

    /**
     * Validates a filtered identity selection before atomically clearing every
     * selected NPC's Slider Preset relationships.
     *
     * @param edit immutable filtered assignment-clear request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome clearNpcSliderPresetsForNpcs(
            NpcMorphAssignmentEdits.ClearSliderPresetsForNpcs edit) {
        if (edit.getIdentities() == null)
            return rejectedNpc(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_REQUIRED,
                    "Filtered assignment clearing requires an NPC identity selection.");
        if (edit.getIdentities().isEmpty())
            return new UnchangedOutcome(snapshot);

        for (NpcMorphAssignmentIdentity identity : edit.getIdentities()) {
            if (findNpcMorphAssignment(identity) < 0)
                return rejectedNpcMorphAssignmentNotFound();
        }
        List<NpcMorphAssignmentSnapshot> candidate = new ArrayList<>(snapshot.getNpcMorphAssignments());
        boolean changed = false;
        for (NpcMorphAssignmentIdentity identity : edit.getIdentities()) {
            int npcIndex = findNpcMorphAssignment(identity);
            NpcMorphAssignmentSnapshot current = candidate.get(npcIndex);
            if (!current.getSliderPresetNames().isEmpty()) {
                candidate.set(npcIndex, copyNpc(current, Collections.<String>emptyList()));
                changed = true;
            }
        }
        if (!changed)
            return new UnchangedOutcome(snapshot);
        return publishChangedNpcMorphAssignments(candidate);
    }

    /**
     * Removes one NPC Morph Assignment selected by case-insensitive identity.
     *
     * @param edit immutable NPC-remove request
     * @return a changed or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome removeNpc(NpcMorphAssignmentEdits.RemoveNpc edit) {
        int npcIndex = findNpcMorphAssignment(edit.getIdentity());
        if (npcIndex < 0)
            return rejectedNpcMorphAssignmentNotFound();
        List<NpcMorphAssignmentSnapshot> assignments = new ArrayList<>(snapshot.getNpcMorphAssignments());
        assignments.remove(npcIndex);
        return publishChangedNpcMorphAssignments(assignments);
    }

    /**
     * Atomically removes the NPC Morph Assignments selected by a caller-owned
     * filtered identity list, preserving state when none match.
     *
     * @param edit immutable filtered removal request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome removeNpcs(NpcMorphAssignmentEdits.RemoveNpcs edit) {
        if (edit.getIdentities() == null)
            return rejectedNpc(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_REQUIRED,
                    "Filtered NPC removal requires an identity selection.");
        if (edit.getIdentities().isEmpty())
            return new UnchangedOutcome(snapshot);

        List<NpcMorphAssignmentSnapshot> remaining = new ArrayList<>();
        for (NpcMorphAssignmentSnapshot assignment : snapshot.getNpcMorphAssignments()) {
            boolean selected = false;
            for (NpcMorphAssignmentIdentity identity : edit.getIdentities()) {
                if (identity.equals(identityOf(assignment))) {
                    selected = true;
                    break;
                }
            }
            if (!selected)
                remaining.add(assignment);
        }
        if (remaining.size() == snapshot.getNpcMorphAssignments().size())
            return new UnchangedOutcome(snapshot);
        return publishChangedNpcMorphAssignments(remaining);
    }

    /**
     * Clears every NPC Morph Assignment without dirtying an already-empty Project.
     *
     * @return changed or unchanged outcome at the pinned snapshot
     */
    private ProjectOutcome clearNpcs() {
        if (snapshot.getNpcMorphAssignments().isEmpty())
            return new UnchangedOutcome(snapshot);
        return publishChangedNpcMorphAssignments(new ArrayList<NpcMorphAssignmentSnapshot>());
    }

    /**
     * Validates every explicit caller-owned choice and stages all eligible NPC
     * replacements before a single publish, so rejection cannot expose a partial fill.
     *
     * @param edit immutable fill-empty request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome fillEmpty(NpcMorphAssignmentEdits.FillEmpty edit) {
        if (edit.getChoices() == null)
            return rejectedNpc(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_REQUIRED,
                    "Fill-empty requires explicit NPC and Slider Preset choices.");
        if (edit.getChoices().isEmpty())
            return new UnchangedOutcome(snapshot);

        List<NpcMorphAssignmentIdentity> seen = new ArrayList<>();
        List<NpcMorphAssignmentSnapshot> candidate = new ArrayList<>(snapshot.getNpcMorphAssignments());
        boolean changed = false;
        for (NpcSliderPresetChoice choice : edit.getChoices()) {
            if (seen.contains(choice.getIdentity()))
                return rejectedNpc(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE,
                        "Fill-empty contains more than one choice for the same NPC identity.");
            seen.add(choice.getIdentity());
            int npcIndex = findNpcMorphAssignment(choice.getIdentity());
            if (npcIndex < 0)
                return rejectedNpcMorphAssignmentNotFound();
            int presetIndex = findSliderPreset(choice.getSliderPresetName());
            if (presetIndex < 0)
                return rejectedSliderPresetNotFound();

            NpcMorphAssignmentSnapshot current = snapshot.getNpcMorphAssignments().get(npcIndex);
            if (current.getSliderPresetNames().isEmpty()) {
                String canonicalName = snapshot.getSliderPresets().get(presetIndex).getName();
                candidate.set(npcIndex, copyNpc(current, Collections.singletonList(canonicalName)));
                changed = true;
            }
        }
        if (!changed)
            return new UnchangedOutcome(snapshot);
        return publishChangedNpcMorphAssignments(candidate);
    }

    /**
     * Finds an NPC Morph Assignment by its complete case-insensitive Project identity.
     *
     * @param identity requested plugin-name/editor-ID identity
     * @return assignment index, or -1 when omitted or absent
     */
    private int findNpcMorphAssignment(NpcMorphAssignmentIdentity identity) {
        if (identity == null)
            return -1;
        for (int index = 0; index < snapshot.getNpcMorphAssignments().size(); index++) {
            NpcMorphAssignmentSnapshot assignment = snapshot.getNpcMorphAssignments().get(index);
            if (identity.equals(identityOf(assignment)))
                return index;
        }
        return -1;
    }

    /**
     * Replaces one NPC Morph Assignment and publishes the canonical Project collection.
     *
     * @param index collection index to replace
     * @param changed replacement immutable value
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome replaceNpcMorphAssignment(int index, NpcMorphAssignmentSnapshot changed) {
        List<NpcMorphAssignmentSnapshot> assignments = new ArrayList<>(snapshot.getNpcMorphAssignments());
        assignments.set(index, changed);
        return publishChangedNpcMorphAssignments(assignments);
    }

    /**
     * Rejects an edit whose NPC identity cannot be resolved.
     *
     * @return a structured rejection carrying the current snapshot
     */
    private RejectedOutcome rejectedNpcMorphAssignmentNotFound() {
        return rejectedNpc(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_NOT_FOUND,
                "The requested NPC Morph Assignment does not exist.");
    }

    /**
     * Builds an NPC edit rejection at the stable identity location.
     *
     * @param code stable diagnostic code
     * @param message human-readable validation failure
     * @return a rejection carrying the unchanged current snapshot
     */
    private RejectedOutcome rejectedNpc(String code, String message) {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("npc-morph-assignment.identity"),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(code, location, message);
    }

    /**
     * Reports whether two NPC values have the same Project identity.
     *
     * @param left first NPC value
     * @param right second NPC value
     * @return true when plugin name and editor ID match without regard to case
     */
    private static boolean sameNpcIdentity(NpcMorphAssignmentSnapshot left, NpcMorphAssignmentSnapshot right) {
        return identityOf(left).equals(identityOf(right));
    }

    /**
     * Extracts the complete logical identity from an immutable NPC Morph Assignment.
     *
     * @param assignment immutable NPC Morph Assignment value
     * @return plugin-name/editor-ID identity with case-insensitive value semantics
     */
    private static NpcMorphAssignmentIdentity identityOf(NpcMorphAssignmentSnapshot assignment) {
        return new NpcMorphAssignmentIdentity(assignment.getPluginName(), assignment.getEditorId());
    }

    /**
     * Resolves caller-supplied assignment names to canonical Project display names
     * while removing case-insensitive duplicates.
     *
     * @param requestedNames explicit caller-owned Slider Preset choices
     * @return canonical ordered names, or null when any requested preset is absent
     */
    private List<String> canonicalNpcSliderPresetNames(List<String> requestedNames) {
        List<String> canonicalNames = new ArrayList<>();
        for (String requestedName : requestedNames) {
            int presetIndex = findSliderPreset(requestedName);
            if (presetIndex < 0)
                return null;
            String canonicalName = snapshot.getSliderPresets().get(presetIndex).getName();
            boolean duplicate = false;
            for (String existingName : canonicalNames) {
                if (existingName.equalsIgnoreCase(canonicalName)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate)
                canonicalNames.add(canonicalName);
        }
        Collections.sort(canonicalNames, CASE_INSENSITIVE_NAME_ORDER);
        return canonicalNames;
    }

    /**
     * Creates a distinct NPC Morph Assignment value from copied source fields.
     *
     * @param source source NPC value
     * @param canonicalAssignments validated canonical Slider Preset names
     * @return a new independent immutable NPC Morph Assignment
     */
    private static NpcMorphAssignmentSnapshot copyNpc(NpcMorphAssignmentSnapshot source,
            List<String> canonicalAssignments) {
        return new NpcMorphAssignmentSnapshot(source.getDisplayName(), source.getPluginName(), source.getEditorId(),
                source.getRace(), source.getFormId(), canonicalAssignments);
    }

    /**
     * Creates an empty Custom Morph Target and publishes the collection in
     * canonical order.
     *
     * @param edit immutable creation request
     * @return a changed outcome carrying the new dirty snapshot
     */
    private ProjectOutcome createCustomMorphTarget(CustomMorphTargetEdits.Create edit) {
        RejectedOutcome rejection = validateCustomMorphTargetName(edit.getName());
        if (rejection != null)
            return rejection;
        String name = edit.getName().trim();
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>(snapshot.getCustomMorphTargets());
        targets.add(new CustomMorphTargetSnapshot(name, Collections.<String>emptyList()));
        return publishChangedCustomMorphTargets(targets);
    }

    /**
     * Adds one canonical Slider Preset relationship to a Custom Morph Target.
     * Duplicate logical relationships preserve the exact current snapshot.
     *
     * @param edit immutable assignment-add request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome addCustomMorphTargetSliderPreset(CustomMorphTargetEdits.AddSliderPreset edit) {
        int targetIndex = findCustomMorphTarget(edit.getTargetName());
        if (targetIndex < 0)
            return rejectedCustomMorphTargetNotFound();
        int presetIndex = findSliderPreset(edit.getSliderPresetName());
        if (presetIndex < 0)
            return rejectedSliderPresetNotFound();

        CustomMorphTargetSnapshot current = snapshot.getCustomMorphTargets().get(targetIndex);
        String presetName = snapshot.getSliderPresets().get(presetIndex).getName();
        List<String> assignments = new ArrayList<>(current.getSliderPresetNames());
        for (String assignment : assignments) {
            if (assignment.equalsIgnoreCase(presetName))
                return new UnchangedOutcome(snapshot);
        }
        assignments.add(presetName);
        Collections.sort(assignments, CASE_INSENSITIVE_NAME_ORDER);
        CustomMorphTargetSnapshot changed = new CustomMorphTargetSnapshot(current.getName(), assignments);
        return replaceCustomMorphTarget(targetIndex, changed);
    }

    /**
     * Removes one case-insensitive Slider Preset relationship from a Custom Morph
     * Target, preserving the current snapshot when it is already absent.
     *
     * @param edit immutable assignment-remove request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome removeCustomMorphTargetSliderPreset(CustomMorphTargetEdits.RemoveSliderPreset edit) {
        int targetIndex = findCustomMorphTarget(edit.getTargetName());
        if (targetIndex < 0)
            return rejectedCustomMorphTargetNotFound();

        CustomMorphTargetSnapshot current = snapshot.getCustomMorphTargets().get(targetIndex);
        String requestedName = edit.getSliderPresetName() == null ? "" : edit.getSliderPresetName().trim();
        List<String> assignments = new ArrayList<>(current.getSliderPresetNames());
        int assignmentIndex = -1;
        for (int index = 0; index < assignments.size(); index++) {
            if (assignments.get(index).equalsIgnoreCase(requestedName)) {
                assignmentIndex = index;
                break;
            }
        }
        if (assignmentIndex < 0)
            return new UnchangedOutcome(snapshot);
        assignments.remove(assignmentIndex);
        CustomMorphTargetSnapshot changed = new CustomMorphTargetSnapshot(current.getName(), assignments);
        return replaceCustomMorphTarget(targetIndex, changed);
    }

    /**
     * Clears every Slider Preset relationship from a Custom Morph Target while
     * preserving the current snapshot when the relationship collection is empty.
     *
     * @param edit immutable assignment-clear request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome clearCustomMorphTargetSliderPresets(CustomMorphTargetEdits.ClearSliderPresets edit) {
        int targetIndex = findCustomMorphTarget(edit.getTargetName());
        if (targetIndex < 0)
            return rejectedCustomMorphTargetNotFound();

        CustomMorphTargetSnapshot current = snapshot.getCustomMorphTargets().get(targetIndex);
        if (current.getSliderPresetNames().isEmpty())
            return new UnchangedOutcome(snapshot);
        CustomMorphTargetSnapshot changed = new CustomMorphTargetSnapshot(current.getName(),
                Collections.<String>emptyList());
        return replaceCustomMorphTarget(targetIndex, changed);
    }

    /**
     * Replaces one Custom Morph Target and publishes the dirty canonical collection.
     *
     * @param targetIndex collection index to replace
     * @param changed replacement immutable value
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome replaceCustomMorphTarget(int targetIndex, CustomMorphTargetSnapshot changed) {
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>(snapshot.getCustomMorphTargets());
        targets.set(targetIndex, changed);
        return publishChangedCustomMorphTargets(targets);
    }

    /**
     * Removes one existing Custom Morph Target selected by case-insensitive identity.
     *
     * @param edit immutable deletion request
     * @return a changed or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome deleteCustomMorphTarget(CustomMorphTargetEdits.Delete edit) {
        int targetIndex = findCustomMorphTarget(edit.getName());
        if (targetIndex < 0)
            return rejectedCustomMorphTargetNotFound();
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>(snapshot.getCustomMorphTargets());
        targets.remove(targetIndex);
        return publishChangedCustomMorphTargets(targets);
    }

    /**
     * Clears a non-empty Custom Morph Target collection without manufacturing a
     * dirty transition for an already-empty Project.
     *
     * @return changed or unchanged outcome at the pinned snapshot
     */
    private ProjectOutcome clearCustomMorphTargets() {
        if (snapshot.getCustomMorphTargets().isEmpty())
            return new UnchangedOutcome(snapshot);
        return publishChangedCustomMorphTargets(new ArrayList<CustomMorphTargetSnapshot>());
    }

    /**
     * Finds a Custom Morph Target using its case-insensitive Project identity.
     *
     * @param name requested name, optionally surrounded by whitespace
     * @return the collection index, or -1 when no logical target matches
     */
    private int findCustomMorphTarget(String name) {
        if (name == null)
            return -1;
        String normalizedName = name.trim();
        for (int index = 0; index < snapshot.getCustomMorphTargets().size(); index++) {
            if (snapshot.getCustomMorphTargets().get(index).getName().equalsIgnoreCase(normalizedName))
                return index;
        }
        return -1;
    }

    /**
     * Rejects an edit whose Custom Morph Target cannot be resolved.
     *
     * @return a structured rejection carrying the current snapshot
     */
    private RejectedOutcome rejectedCustomMorphTargetNotFound() {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("custom-morph-target.name"),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NOT_FOUND, location,
                "The requested Custom Morph Target does not exist.");
    }

    /**
     * Validates a requested Custom Morph Target name without restricting BodyGen
     * condition syntax.
     *
     * @param requestedName caller-supplied name before normalization
     * @return a structured rejection, or null when the trimmed name is valid
     */
    private RejectedOutcome validateCustomMorphTargetName(String requestedName) {
        if (requestedName == null || requestedName.trim().isEmpty())
            return rejectedCustomMorphTargetName(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_REQUIRED,
                    "A Custom Morph Target name must not be empty.");
        String normalizedName = requestedName.trim();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            if (target.getName().equalsIgnoreCase(normalizedName))
                return rejectedCustomMorphTargetName(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_DUPLICATE,
                        "A Custom Morph Target with this name already exists.");
        }
        return null;
    }

    /**
     * Builds a naming rejection at the stable Custom Morph Target name location.
     *
     * @param code stable diagnostic code
     * @param message human-readable validation failure
     * @return a rejection carrying the unchanged current snapshot
     */
    private RejectedOutcome rejectedCustomMorphTargetName(String code, String message) {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("custom-morph-target.name"),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(code, location, message);
    }

    /**
     * Creates an empty Slider Preset and publishes the catalog in canonical order.
     *
     * @param edit immutable creation request
     * @return a changed outcome carrying the new dirty snapshot
     */
    private ProjectOutcome createSliderPreset(SliderPresetEdits.Create edit) {
        RejectedOutcome rejection = validateSliderPresetName(edit.getName(), OptionalInt.empty());
        if (rejection != null)
            return rejection;
        String name = edit.getName().trim();
        List<SliderPresetSnapshot> presets = new ArrayList<>(snapshot.getSliderPresets());
        presets.add(new SliderPresetSnapshot(name, false, Collections.<SliderChoiceSnapshot>emptyList()));
        return publishChangedPresets(presets);
    }

    /**
     * Duplicates the complete immutable Slider Preset value under a validated name.
     *
     * @param edit immutable duplication request
     * @return a changed or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome duplicateSliderPreset(SliderPresetEdits.Duplicate edit) {
        int sourceIndex = findSliderPreset(edit.getSourceName());
        if (sourceIndex < 0)
            return rejectedSliderPresetNotFound();
        RejectedOutcome rejection = validateSliderPresetName(edit.getDuplicateName(), OptionalInt.empty());
        if (rejection != null)
            return rejection;

        SliderPresetSnapshot source = snapshot.getSliderPresets().get(sourceIndex);
        SliderPresetSnapshot duplicate = new SliderPresetSnapshot(edit.getDuplicateName().trim(), source.isUunp(),
                source.getSliderChoices());
        List<SliderPresetSnapshot> presets = new ArrayList<>(snapshot.getSliderPresets());
        presets.add(duplicate);
        return publishChangedPresets(presets);
    }

    /**
     * Replaces the complete Slider Preset value after normalizing its name and
     * slider-choice order.
     *
     * @param edit immutable full-update request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome updateSliderPreset(SliderPresetEdits.Update edit) {
        int index = findSliderPreset(edit.getCurrentName());
        if (index < 0)
            return rejectedSliderPresetNotFound();
        if (edit.getReplacement() == null) {
            SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("slider-preset"),
                    OptionalInt.empty(), OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.SLIDER_PRESET_VALUE_REQUIRED, location,
                    "A Slider Preset update requires a replacement value.");
        }
        RejectedOutcome rejection = validateSliderPresetName(edit.getReplacement().getName(), OptionalInt.of(index));
        if (rejection != null)
            return rejection;

        SliderPresetSnapshot replacement = canonicalSliderPreset(edit.getReplacement(),
                edit.getReplacement().getName().trim());
        if (sameSliderPreset(snapshot.getSliderPresets().get(index), replacement))
            return new UnchangedOutcome(snapshot);
        return replaceSliderPreset(index, replacement);
    }

    /**
     * Changes display casing or spelling while retaining the Slider Preset's
     * complete payload and case-insensitive logical identity.
     *
     * @param edit immutable rename request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome renameSliderPreset(SliderPresetEdits.Rename edit) {
        int index = findSliderPreset(edit.getCurrentName());
        if (index < 0)
            return rejectedSliderPresetNotFound();
        RejectedOutcome rejection = validateSliderPresetName(edit.getNewName(), OptionalInt.of(index));
        if (rejection != null)
            return rejection;

        SliderPresetSnapshot current = snapshot.getSliderPresets().get(index);
        String normalizedName = edit.getNewName().trim();
        if (current.getName().equals(normalizedName))
            return new UnchangedOutcome(snapshot);
        SliderPresetSnapshot renamed = new SliderPresetSnapshot(normalizedName, current.isUunp(),
                current.getSliderChoices());
        return replaceSliderPreset(index, renamed);
    }

    /**
     * Removes one existing Slider Preset and every affected Custom Morph Target
     * relationship in one atomically published snapshot.
     *
     * @param edit immutable deletion request
     * @return a changed or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome deleteSliderPreset(SliderPresetEdits.Delete edit) {
        int index = findSliderPreset(edit.getName());
        if (index < 0)
            return rejectedSliderPresetNotFound();
        String removedName = snapshot.getSliderPresets().get(index).getName();
        List<SliderPresetSnapshot> presets = new ArrayList<>(snapshot.getSliderPresets());
        presets.remove(index);
        List<CustomMorphTargetSnapshot> targets = removeCustomMorphTargetAssignments(removedName);
        List<NpcMorphAssignmentSnapshot> npcs = removeNpcAssignments(removedName);
        return publishChangedProjectState(presets, targets, npcs);
    }

    /**
     * Clears a non-empty Slider Preset catalog and every Custom Morph Target
     * relationship atomically, without manufacturing a dirty transition for an
     * already-empty Project.
     *
     * @return changed or unchanged outcome at the pinned snapshot
     */
    private ProjectOutcome clearSliderPresets() {
        if (snapshot.getSliderPresets().isEmpty())
            return new UnchangedOutcome(snapshot);
        List<CustomMorphTargetSnapshot> targets = clearAllCustomMorphTargetAssignments();
        List<NpcMorphAssignmentSnapshot> npcs = clearAllNpcAssignments();
        return publishChangedProjectState(new ArrayList<SliderPresetSnapshot>(), targets, npcs);
    }

    /**
     * Changes the UUNP flag without flattening the Slider Preset's immutable choices.
     *
     * @param edit immutable UUNP request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome setSliderPresetUunp(SliderPresetEdits.SetUunp edit) {
        int index = findSliderPreset(edit.getName());
        if (index < 0)
            return rejectedSliderPresetNotFound();
        SliderPresetSnapshot current = snapshot.getSliderPresets().get(index);
        if (current.isUunp() == edit.isUunp())
            return new UnchangedOutcome(snapshot);
        SliderPresetSnapshot changed = new SliderPresetSnapshot(current.getName(), edit.isUunp(),
                current.getSliderChoices());
        return replaceSliderPreset(index, changed);
    }

    /**
     * Upserts one slider choice while preserving nullable stored values, effective
     * values, percentages, and missing-default identity.
     *
     * @param edit immutable slider-choice request
     * @return changed, unchanged, or rejected outcome at the pinned snapshot
     */
    private ProjectOutcome setSliderChoice(SliderPresetEdits.SetSliderChoice edit) {
        int presetIndex = findSliderPreset(edit.getPresetName());
        if (presetIndex < 0)
            return rejectedSliderPresetNotFound();
        if (edit.getChoice() == null) {
            SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("slider-preset.slider-choice"),
                    OptionalInt.empty(), OptionalInt.empty());
            return rejected(ProjectDiagnosticCodes.SLIDER_CHOICE_REQUIRED, location,
                    "A slider-choice edit requires a value.");
        }

        SliderPresetSnapshot current = snapshot.getSliderPresets().get(presetIndex);
        List<SliderChoiceSnapshot> choices = new ArrayList<>(current.getSliderChoices());
        int choiceIndex = findSliderChoice(choices, edit.getChoice().getName());
        if (choiceIndex >= 0 && sameSliderChoice(choices.get(choiceIndex), edit.getChoice()))
            return new UnchangedOutcome(snapshot);
        if (choiceIndex >= 0)
            choices.set(choiceIndex, edit.getChoice());
        else
            choices.add(edit.getChoice());
        Collections.sort(choices, SLIDER_CHOICE_NAME_ORDER);
        SliderPresetSnapshot changed = new SliderPresetSnapshot(current.getName(), current.isUunp(), choices);
        return replaceSliderPreset(presetIndex, changed);
    }

    /**
     * Finds a Slider Preset using its case-insensitive Project identity.
     *
     * @param name requested name, optionally surrounded by whitespace
     * @return the catalog index, or -1 when no logical Slider Preset matches
     */
    private int findSliderPreset(String name) {
        return findSliderPreset(snapshot.getSliderPresets(), name);
    }

    /**
     * Finds a Slider Preset inside a detached working catalog by logical identity.
     *
     * @param presets working catalog
     * @param name requested name
     * @return matching index, or -1 when absent
     */
    private static int findSliderPreset(List<SliderPresetSnapshot> presets, String name) {
        if (name == null)
            return -1;
        String normalizedName = name.trim();
        for (int index = 0; index < presets.size(); index++) {
            if (presets.get(index).getName().equalsIgnoreCase(normalizedName))
                return index;
        }
        return -1;
    }

    /**
     * Finds a slider choice without making display casing part of its identity.
     *
     * @param choices current immutable choice values
     * @param name requested slider name
     * @return the choice index, or -1 when absent
     */
    private static int findSliderChoice(List<SliderChoiceSnapshot> choices, String name) {
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).getName().equalsIgnoreCase(name))
                return index;
        }
        return -1;
    }

    /**
     * Compares all observable slider-choice values so only real persisted-state
     * changes dirty the Project.
     *
     * @param left current choice
     * @param right requested choice
     * @return true when every exposed value is equal
     */
    private static boolean sameSliderChoice(SliderChoiceSnapshot left, SliderChoiceSnapshot right) {
        return left.getName().equals(right.getName()) && left.isEnabled() == right.isEnabled()
                && left.getStoredSmallValue().equals(right.getStoredSmallValue())
                && left.getStoredBigValue().equals(right.getStoredBigValue())
                && left.getEffectiveSmallValue() == right.getEffectiveSmallValue()
                && left.getEffectiveBigValue() == right.getEffectiveBigValue()
                && left.getPercentageMinimum() == right.getPercentageMinimum()
                && left.getPercentageMaximum() == right.getPercentageMaximum()
                && left.isMissingDefault() == right.isMissingDefault();
    }

    /**
     * Produces a Slider Preset whose choices are in stable case-insensitive order.
     *
     * @param source immutable source value
     * @param normalizedName validated display name
     * @return a canonically ordered immutable value
     */
    private static SliderPresetSnapshot canonicalSliderPreset(SliderPresetSnapshot source, String normalizedName) {
        List<SliderChoiceSnapshot> choices = new ArrayList<>(source.getSliderChoices());
        Collections.sort(choices, SLIDER_CHOICE_NAME_ORDER);
        return new SliderPresetSnapshot(normalizedName, source.isUunp(), choices);
    }

    /**
     * Compares complete immutable Slider Preset values after canonicalization.
     *
     * @param left current value
     * @param right requested canonical value
     * @return true when name, UUNP, and every slider choice are equal
     */
    private static boolean sameSliderPreset(SliderPresetSnapshot left, SliderPresetSnapshot right) {
        if (!left.getName().equals(right.getName()) || left.isUunp() != right.isUunp()
                || left.getSliderChoices().size() != right.getSliderChoices().size())
            return false;
        for (int index = 0; index < left.getSliderChoices().size(); index++) {
            if (!sameSliderChoice(left.getSliderChoices().get(index), right.getSliderChoices().get(index)))
                return false;
        }
        return true;
    }

    /**
     * Replaces one Slider Preset and, when its display name changes, rewrites every
     * affected Custom Morph Target relationship in the same published snapshot.
     *
     * @param index catalog index to replace
     * @param changed replacement immutable value
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome replaceSliderPreset(int index, SliderPresetSnapshot changed) {
        String previousName = snapshot.getSliderPresets().get(index).getName();
        List<SliderPresetSnapshot> presets = new ArrayList<>(snapshot.getSliderPresets());
        presets.set(index, changed);
        if (!previousName.equals(changed.getName())) {
            List<CustomMorphTargetSnapshot> targets = renameCustomMorphTargetAssignments(previousName,
                    changed.getName());
            List<NpcMorphAssignmentSnapshot> npcs = renameNpcAssignments(previousName, changed.getName());
            return publishChangedProjectState(presets, targets, npcs);
        }
        return publishChangedPresets(presets);
    }

    /**
     * Rewrites every relationship to a renamed Slider Preset while retaining
     * canonical assignment order and unaffected immutable target values.
     *
     * @param previousName prior Slider Preset display name
     * @param changedName replacement canonical display name
     * @return Custom Morph Targets with every matching relationship updated
     */
    private List<CustomMorphTargetSnapshot> renameCustomMorphTargetAssignments(String previousName,
            String changedName) {
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            List<String> assignments = new ArrayList<>(target.getSliderPresetNames());
            boolean changed = false;
            for (int index = 0; index < assignments.size(); index++) {
                if (assignments.get(index).equalsIgnoreCase(previousName)) {
                    assignments.set(index, changedName);
                    changed = true;
                }
            }
            if (changed) {
                Collections.sort(assignments, CASE_INSENSITIVE_NAME_ORDER);
                targets.add(new CustomMorphTargetSnapshot(target.getName(), assignments));
            } else {
                targets.add(target);
            }
        }
        return targets;
    }

    /**
     * Removes every relationship to a deleted Slider Preset without changing
     * unrelated Custom Morph Target values.
     *
     * @param removedName deleted Slider Preset display name
     * @return Custom Morph Targets with matching relationships removed
     */
    private List<CustomMorphTargetSnapshot> removeCustomMorphTargetAssignments(String removedName) {
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            List<String> assignments = new ArrayList<>(target.getSliderPresetNames());
            boolean changed = false;
            for (int index = assignments.size() - 1; index >= 0; index--) {
                if (assignments.get(index).equalsIgnoreCase(removedName)) {
                    assignments.remove(index);
                    changed = true;
                }
            }
            targets.add(changed ? new CustomMorphTargetSnapshot(target.getName(), assignments) : target);
        }
        return targets;
    }

    /**
     * Clears every Custom Morph Target relationship when the Slider Preset catalog
     * is cleared, retaining already-empty immutable target values.
     *
     * @return Custom Morph Targets with empty Slider Preset relationships
     */
    private List<CustomMorphTargetSnapshot> clearAllCustomMorphTargetAssignments() {
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            if (target.getSliderPresetNames().isEmpty())
                targets.add(target);
            else
                targets.add(new CustomMorphTargetSnapshot(target.getName(), Collections.<String>emptyList()));
        }
        return targets;
    }

    /**
     * Rewrites every NPC relationship to a renamed Slider Preset without mutating
     * caller-held NPC Database source values.
     *
     * @param previousName prior Slider Preset display name
     * @param changedName replacement canonical display name
     * @return copied NPC Morph Assignments with repaired relationships
     */
    private List<NpcMorphAssignmentSnapshot> renameNpcAssignments(String previousName, String changedName) {
        List<NpcMorphAssignmentSnapshot> assignments = new ArrayList<>();
        for (NpcMorphAssignmentSnapshot npc : snapshot.getNpcMorphAssignments()) {
            List<String> presetNames = new ArrayList<>(npc.getSliderPresetNames());
            boolean changed = false;
            for (int index = 0; index < presetNames.size(); index++) {
                if (presetNames.get(index).equalsIgnoreCase(previousName)) {
                    presetNames.set(index, changedName);
                    changed = true;
                }
            }
            if (changed) {
                Collections.sort(presetNames, CASE_INSENSITIVE_NAME_ORDER);
                assignments.add(copyNpc(npc, presetNames));
            } else {
                assignments.add(npc);
            }
        }
        return assignments;
    }

    /**
     * Removes every NPC relationship to a deleted Slider Preset while retaining
     * independent NPC values and unrelated assignments.
     *
     * @param removedName deleted Slider Preset display name
     * @return copied NPC Morph Assignments with matching relationships removed
     */
    private List<NpcMorphAssignmentSnapshot> removeNpcAssignments(String removedName) {
        List<NpcMorphAssignmentSnapshot> assignments = new ArrayList<>();
        for (NpcMorphAssignmentSnapshot npc : snapshot.getNpcMorphAssignments()) {
            List<String> presetNames = new ArrayList<>(npc.getSliderPresetNames());
            boolean changed = false;
            for (int index = presetNames.size() - 1; index >= 0; index--) {
                if (presetNames.get(index).equalsIgnoreCase(removedName)) {
                    presetNames.remove(index);
                    changed = true;
                }
            }
            assignments.add(changed ? copyNpc(npc, presetNames) : npc);
        }
        return assignments;
    }

    /**
     * Clears every Project NPC relationship when the Slider Preset catalog is
     * cleared, without changing or retaining a reference to NPC Database entries.
     *
     * @return NPC Morph Assignments with empty Slider Preset relationships
     */
    private List<NpcMorphAssignmentSnapshot> clearAllNpcAssignments() {
        List<NpcMorphAssignmentSnapshot> assignments = new ArrayList<>();
        for (NpcMorphAssignmentSnapshot npc : snapshot.getNpcMorphAssignments()) {
            assignments.add(npc.getSliderPresetNames().isEmpty() ? npc
                    : copyNpc(npc, Collections.<String>emptyList()));
        }
        return assignments;
    }

    /**
     * Rejects an edit whose target cannot be resolved without changing dirty state.
     *
     * @return a structured rejection carrying the current snapshot
     */
    private RejectedOutcome rejectedSliderPresetNotFound() {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("slider-preset.name"),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(ProjectDiagnosticCodes.SLIDER_PRESET_NOT_FOUND, location,
                "The requested Slider Preset does not exist.");
    }

    /**
     * Validates a requested Slider Preset name against Project naming invariants.
     *
     * @param requestedName caller-supplied name before normalization
     * @param exemptIndex existing catalog index allowed to retain its logical name,
     *        or empty when creating a new Slider Preset
     * @return a structured rejection, or null when the trimmed name is valid
     */
    private RejectedOutcome validateSliderPresetName(String requestedName, OptionalInt exemptIndex) {
        SliderPresetNameProblem problem = findSliderPresetNameProblem(requestedName,
                snapshot.getSliderPresets(), exemptIndex);
        return problem == null ? null : rejectedSliderPresetName(problem.code, problem.message);
    }

    /**
     * Applies the single Project name-validation implementation to any current or
     * detached Slider Preset catalog.
     *
     * @param requestedName name before trimming
     * @param presets catalog whose logical identities must remain unique
     * @param exemptIndex existing identity allowed to retain or replace its name
     * @return validation problem, or null when the name satisfies every invariant
     */
    private static SliderPresetNameProblem findSliderPresetNameProblem(String requestedName,
            List<SliderPresetSnapshot> presets, OptionalInt exemptIndex) {
        if (requestedName == null || requestedName.trim().isEmpty())
            return new SliderPresetNameProblem(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_REQUIRED,
                    "A Slider Preset name must not be empty.");
        String normalizedName = requestedName.trim();
        if (normalizedName.indexOf('.') >= 0)
            return new SliderPresetNameProblem(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_CONTAINS_DOT,
                    "A Slider Preset name must not contain dots.");
        for (int index = 0; index < presets.size(); index++) {
            if ((!exemptIndex.isPresent() || index != exemptIndex.getAsInt())
                    && presets.get(index).getName().equalsIgnoreCase(normalizedName))
                return new SliderPresetNameProblem(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE,
                        "A Slider Preset with this name already exists.");
        }
        return null;
    }

    /** Immutable validation detail shared by edit and XML-import callers. */
    private static final class SliderPresetNameProblem {
        private final String code;
        private final String message;

        /** Creates one stable name-validation problem. */
        private SliderPresetNameProblem(String code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    /**
     * Builds a naming rejection at the stable Slider Preset name location.
     *
     * @param code stable diagnostic code
     * @param message human-readable validation failure
     * @return a rejection carrying the unchanged current snapshot
     */
    private RejectedOutcome rejectedSliderPresetName(String code, String message) {
        SourceLocation location = new SourceLocation(Optional.empty(), Optional.of("slider-preset.name"),
                OptionalInt.empty(), OptionalInt.empty());
        return rejected(code, location, message);
    }

    /**
     * Sorts changed Slider Presets and atomically publishes a dirty Project snapshot.
     *
     * @param presets changed catalog values
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome publishChangedPresets(List<SliderPresetSnapshot> presets) {
        return publishChangedProjectState(presets,
                new ArrayList<>(snapshot.getCustomMorphTargets()));
    }

    /**
     * Sorts changed Custom Morph Targets and atomically publishes a dirty Project
     * snapshot.
     *
     * @param targets changed Custom Morph Target values
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome publishChangedCustomMorphTargets(List<CustomMorphTargetSnapshot> targets) {
        return publishChangedProjectState(new ArrayList<>(snapshot.getSliderPresets()), targets);
    }

    /**
     * Canonically orders and atomically publishes changed NPC Morph Assignments.
     *
     * @param assignments changed NPC Morph Assignment values
     * @return a changed outcome carrying the published snapshot
     */
    private ChangedOutcome publishChangedNpcMorphAssignments(List<NpcMorphAssignmentSnapshot> assignments) {
        Collections.sort(assignments, NPC_MORPH_ASSIGNMENT_IDENTITY_ORDER);
        snapshot = new ProjectSnapshot(snapshot.getSliderPresets(), snapshot.getCustomMorphTargets(), assignments,
                snapshot.getFileIdentity(), true, snapshot.getLifecycleStatus());
        return new ChangedOutcome(snapshot);
    }

    /**
     * Canonically orders and atomically publishes changed Slider Presets together
     * with their repaired Custom Morph Target relationships.
     *
     * @param presets changed Slider Preset catalog
     * @param targets repaired Custom Morph Target values
     * @return a changed outcome carrying the single coherent snapshot
     */
    private ChangedOutcome publishChangedProjectState(List<SliderPresetSnapshot> presets,
            List<CustomMorphTargetSnapshot> targets) {
        return publishChangedProjectState(presets, targets,
                new ArrayList<>(snapshot.getNpcMorphAssignments()));
    }

    /**
     * Canonically orders and atomically publishes every changed Project collection.
     *
     * @param presets changed Slider Preset catalog
     * @param targets repaired Custom Morph Target values
     * @param npcs repaired NPC Morph Assignment values
     * @return a changed outcome carrying the single coherent snapshot
     */
    private ChangedOutcome publishChangedProjectState(List<SliderPresetSnapshot> presets,
            List<CustomMorphTargetSnapshot> targets, List<NpcMorphAssignmentSnapshot> npcs) {
        Collections.sort(presets, SLIDER_PRESET_NAME_ORDER);
        Collections.sort(targets, CUSTOM_MORPH_TARGET_NAME_ORDER);
        Collections.sort(npcs, NPC_MORPH_ASSIGNMENT_IDENTITY_ORDER);
        snapshot = new ProjectSnapshot(presets, targets, npcs,
                snapshot.getFileIdentity(), true, snapshot.getLifecycleStatus());
        return new ChangedOutcome(snapshot);
    }

    /**
     * Builds a validation rejection while the operation lock pins the snapshot used
     * by both the outcome and concurrent callers.
     *
     * @param code stable diagnostic code
     * @param location structured source location
     * @param message human-readable diagnostic message
     * @return a rejection carrying the pinned snapshot
     */
    private RejectedOutcome rejected(String code, SourceLocation location, String message) {
        ProjectDiagnostic diagnostic = new ProjectDiagnostic(code, DiagnosticSeverity.ERROR, location, message);
        return new RejectedOutcome(snapshot, Collections.singletonList(diagnostic));
    }
}
