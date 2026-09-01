package com.asdasfa.jbs2bg.workbench.output;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.presentation.BosJsonArtifact;
import com.asdasfa.jbs2bg.presentation.BosOutputDiagnostic;
import com.asdasfa.jbs2bg.presentation.BosOutputException;
import com.asdasfa.jbs2bg.presentation.OutputArtifact;
import com.asdasfa.jbs2bg.presentation.OutputArtifactPublisher;
import com.asdasfa.jbs2bg.presentation.ProjectGeneratedOutput;
import com.asdasfa.jbs2bg.presentation.ProjectOutputFormatter;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.workbench.WorkbenchProjectFlow;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

/**
 * JavaFX-independent Output state machine. It captures one immutable output basis per attempt and publishes
 * generated artifacts only through the application-wide job coordinator's serialized completion lane.
 */
public final class OutputFeature {
    private static final String STALE_MESSAGE = "Project changed—Generate again.";

    private final WorkbenchProjectFlow projectFlow;
    private final GenerationSettingsSource generationSettingsSource;
    private final Consumer<Throwable> observerFailureSink;
    private final Map<Long, Consumer<Update>> observers = new LinkedHashMap<>();
    private long revision;
    private long nextGenerationToken = 1;
    private long nextObserverId = 1;
    private long nextEffectToken = 1;
    private long currentGenerationToken;
    private PendingExport pendingExport;
    private PendingSelectedExport pendingSelectedExport;
    private Tab selectedTab = Tab.TEMPLATES;
    private String selectedBosArtifact;
    private Frame frame;

    /**
     * Creates a window-scoped Output feature over the authoritative Project and generation-settings sources.
     *
     * @param projectFlow authoritative Project publication and central-job path
     * @param generationSettingsSource immutable settings and command-option capture source
     */
    public OutputFeature(WorkbenchProjectFlow projectFlow, GenerationSettingsSource generationSettingsSource) {
        this(projectFlow, generationSettingsSource, failure -> {
            // Production adapters may provide technical diagnostics; the default keeps observers isolated.
        });
    }

    /**
     * Creates an Output feature with an explicit sink for observer/rendering failures.
     *
     * @param projectFlow authoritative Project publication and central-job path
     * @param generationSettingsSource immutable settings and command-option capture source
     * @param observerFailureSink isolated observer-failure receiver
     */
    public OutputFeature(WorkbenchProjectFlow projectFlow, GenerationSettingsSource generationSettingsSource,
                         Consumer<Throwable> observerFailureSink) {
        this.projectFlow = Objects.requireNonNull(projectFlow, "projectFlow");
        this.generationSettingsSource = Objects.requireNonNull(generationSettingsSource,
                "generationSettingsSource");
        this.observerFailureSink = Objects.requireNonNull(observerFailureSink, "observerFailureSink");
        commit(Optional.empty(), Optional.empty(), Freshness.EMPTY, Optional.empty());
    }

    /** @return latest completely committed immutable Output frame */
    public Frame frame() {
        return frame;
    }

    /**
     * Observes later committed Output updates; one observer failure cannot block state or another adapter.
     *
     * @param observer update consumer
     * @return idempotent subscription handle
     */
    public Subscription observe(Consumer<Update> observer) {
        long id = nextObserverId++;
        observers.put(id, Objects.requireNonNull(observer, "observer"));
        return () -> observers.remove(id);
    }

    /**
     * Reconciles a coherent Project publication and invalidates only when semantic Project content changed.
     *
     * @param projectFrame latest authoritative Project frame
     * @return accepted Output update with no navigation effect
     */
    public Update acceptProjectFrame(WorkbenchProjectFlow.Frame projectFrame) {
        return acceptProjectFrame(projectFrame, false);
    }

    /**
     * Reconciles Project content and optionally clears all session Output state for successful New/Open lifecycle
     * replacement, including an equal-content pristine replacement.
     *
     * @param projectFrame latest authoritative Project frame
     * @param resetOutput  whether New/Open semantics require an unconditional empty Output state
     * @return accepted Output update with no navigation effect
     */
    public Update acceptProjectFrame(WorkbenchProjectFlow.Frame projectFrame, boolean resetOutput) {
        Objects.requireNonNull(projectFrame, "projectFrame");
        if (resetOutput) {
            selectedTab = Tab.TEMPLATES;
            selectedBosArtifact = null;
            commit(Optional.empty(), Optional.empty(), Freshness.EMPTY, Optional.empty());
            return publish(true, Optional.empty());
        }
        if (frame.basis().stream().noneMatch(basis -> !basis.projectSnapshot().getContentVersion()
                .equals(projectFrame.snapshot().getContentVersion())))
            return update(true, Optional.empty());
        selectedBosArtifact = null;
        commit(Optional.empty(), Optional.empty(), Freshness.INVALIDATED, Optional.empty());
        return publish(true, Optional.empty());
    }

    /** Invalidates accepted artifacts after one output-affecting Settings/options publication. */
    public Update refreshGenerationSettings() {
        if (frame.basis().stream().noneMatch(basis ->
                !basis.generationSettings().equals(generationSettingsSource.capture())))
            return update(true, Optional.empty());
        selectedBosArtifact = null;
        commit(Optional.empty(), Optional.empty(), Freshness.INVALIDATED, Optional.empty());
        return publish(true, Optional.empty());
    }

    /**
     * Applies one task-oriented Output intent on the serialized presentation lane.
     *
     * @param intent immutable user task
     * @return whether the task was accepted and the resulting immutable frame
     */
    public Update dispatch(Intent intent) {
        Objects.requireNonNull(intent, "intent");
        return switch (intent) {
            case Generate ignored -> generate();
            case SelectTab selectTab -> selectTab(selectTab.tab());
            case SelectBosArtifact selectBosArtifact -> selectBosArtifact(selectBosArtifact.sliderPresetName());
            case Copy ignored -> copy();
            case Export ignored -> requestExport();
            case ExportSelected ignored -> requestSelectedExport();
        };
    }

    /** Captures the selected accepted artifact as a tokenized clipboard effect. */
    private Update copy() {
        Optional<OutputArtifact> artifact = frame.displayedArtifact();
        if (frame.freshness() != Freshness.FRESH || artifact.isEmpty())
            return update(false, Optional.empty());
        OutputArtifact accepted = artifact.orElseThrow();
        return update(true, Optional.of(new CopyToClipboard(nextEffectToken++, accepted.getFileName(),
                accepted.getText())));
    }

    /** Captures the complete accepted batch before asking the platform for one destination directory. */
    private Update requestExport() {
        if (frame.freshness() != Freshness.FRESH || frame.generatedOutput().isEmpty()
                || projectFlow.jobs().frame().active())
            return update(false, Optional.empty());
        long token = nextEffectToken++;
        pendingExport = new PendingExport(token, frame.generatedOutput().orElseThrow(), frame.basis().orElseThrow());
        return update(true, Optional.of(new ChooseExportDirectory(token)));
    }

    /**
     * Completes the latest tokenized directory chooser and admits one captured export job when a path was selected.
     *
     * @param effectToken     chooser effect identity
     * @param targetDirectory selected existing directory, or empty when the chooser was cancelled
     * @return whether this response matched the pending effect and any resulting job was admitted
     */
    public Update completeExport(long effectToken, Optional<Path> targetDirectory) {
        Objects.requireNonNull(targetDirectory, "targetDirectory");
        PendingExport pending = pendingExport;
        if (pending == null || pending.token() != effectToken)
            return update(false, Optional.empty());
        pendingExport = null;
        if (targetDirectory.isEmpty())
            return update(true, Optional.empty());
        ProjectGeneratedOutput output = pending.output();
        ExportCapture capture = new ExportCapture(output.getArtifacts(), relationshipDiagnostics(output),
                pending.basis(), targetDirectory.orElseThrow().toAbsolutePath().normalize(), Optional.empty(),
                ExportScope.COMPLETE);
        JobCoordinator.Admission admission = projectFlow.jobs().submit(exportSubmission(capture));
        return update(admission.admitted(), Optional.empty());
    }

    /** Captures the selected accepted BoS artifact before asking the platform for one file destination. */
    private Update requestSelectedExport() {
        Optional<OutputArtifact> artifact = frame.displayedArtifact();
        if (frame.freshness() != Freshness.FRESH || selectedTab != Tab.BOS_JSON || artifact.isEmpty()
                || projectFlow.jobs().frame().active())
            return update(false, Optional.empty());
        long token = nextEffectToken++;
        OutputArtifact accepted = artifact.orElseThrow();
        pendingSelectedExport = new PendingSelectedExport(token, accepted, frame.basis().orElseThrow());
        return update(true, Optional.of(new ChooseExportFile(token, accepted.getFileName())));
    }

    /**
     * Completes the latest selected-BoS chooser and admits a one-artifact transactional export.
     *
     * @param effectToken chooser effect identity
     * @param destination selected file, or empty when the chooser was cancelled
     * @return whether this response matched the pending effect and any resulting job was admitted
     */
    public Update completeSelectedExport(long effectToken, Optional<Path> destination) {
        Objects.requireNonNull(destination, "destination");
        PendingSelectedExport pending = pendingSelectedExport;
        if (pending == null || pending.token() != effectToken)
            return update(false, Optional.empty());
        pendingSelectedExport = null;
        if (destination.isEmpty())
            return update(true, Optional.empty());
        Path selected = jsonDestination(destination.orElseThrow());
        Path directory = selected.getParent();
        if (directory == null)
            return update(false, Optional.empty());
        OutputArtifact targeted = new SelectedOutputArtifact(pending.artifact(),
                selected.getFileName().toString());
        ExportCapture capture = new ExportCapture(List.of(targeted), List.of(), pending.basis(), directory,
                Optional.of(selected), ExportScope.SELECTED_BOS);
        JobCoordinator.Admission admission = projectFlow.jobs().submit(exportSubmission(capture));
        return update(admission.admitted(), Optional.empty());
    }

    /** Preserves any case-insensitive JSON extension and appends the canonical extension only when absent. */
    private static Path jsonDestination(Path selected) {
        Path normalized = Objects.requireNonNull(selected, "selected").toAbsolutePath().normalize();
        String name = normalized.getFileName().toString();
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".json")
                ? normalized : normalized.resolveSibling(name + ".json");
    }

    /** Selects one stable artifact-family tab without changing accepted generated bytes. */
    private Update selectTab(Tab tab) {
        selectedTab = Objects.requireNonNull(tab, "tab");
        commit(frame.generatedOutput(), frame.basis(), frame.freshness(), frame.selectedBosArtifact());
        return publish(true, Optional.empty());
    }

    /** Selects a BoS artifact by case-insensitive Slider Preset identity without retargeting a missing name. */
    private Update selectBosArtifact(String sliderPresetName) {
        String requested = Objects.requireNonNull(sliderPresetName, "sliderPresetName");
        Optional<String> canonical = frame.generatedOutput().stream()
                .flatMap(output -> output.getBosJsonArtifacts().stream())
                .map(BosJsonArtifact::getSliderPresetName)
                .filter(name -> name.equalsIgnoreCase(requested))
                .findFirst();
        if (canonical.isEmpty())
            return update(false, Optional.empty());
        selectedBosArtifact = canonical.orElseThrow();
        commit(frame.generatedOutput(), frame.basis(), frame.freshness(), canonical);
        return publish(true, Optional.empty());
    }

    /** Captures one coherent basis and submits it without queueing behind another application job. */
    private Update generate() {
        OutputBasis basis = new OutputBasis(projectFlow.frame().snapshot(), generationSettingsSource.capture());
        long generationToken = nextGenerationToken++;
        long previousGenerationToken = currentGenerationToken;
        currentGenerationToken = generationToken;
        JobCoordinator.Admission admission = projectFlow.jobs().submit(generationSubmission(
                generationToken, basis));
        if (!admission.admitted()) {
            currentGenerationToken = previousGenerationToken;
            return update(false, Optional.empty());
        }
        return update(true, Optional.empty());
    }

    /** Creates a retryable central-job request whose worker retains no mutable Project or Settings state. */
    private JobCoordinator.Submission<GeneratedCandidate> generationSubmission(long generationToken,
                                                                                OutputBasis basis) {
        JobCoordinator.Operation operation = new JobCoordinator.Operation("Generate Output", List.of(), List.of(),
                Optional.of(basis.describe()), JobCoordinator.ConsistencyClass.SNAPSHOT_DERIVED);
        return new JobCoordinator.Submission<>(operation, context -> {
            context.report(JobCoordinator.Progress.indeterminate("Generating Project output", true));
            context.checkCancellation();
            ProjectGeneratedOutput generated;
            try {
                generated = ProjectOutputFormatter.generate(basis.projectSnapshot(), basis.settings(),
                        basis.omitRedundantSliders(), new ProjectOutputFormatter.GenerationContext() {
                            @Override
                            public void checkCancellation() {
                                context.checkCancellation();
                            }

                            @Override
                            public void report(long completedUnits, long totalUnits) {
                                context.report(JobCoordinator.Progress.determinate("Generating Project output",
                                        completedUnits, totalUnits));
                            }
                        });
            } catch (BosOutputException exception) {
                return JobCoordinator.Result.failed("Generate Output failed with "
                                + diagnosticSummary(exception.getDiagnostics().size()) + ".",
                        exception.getDiagnostics().stream().map(OutputFeature::jobDiagnostic).toList());
            }
            context.checkCancellation();
            GeneratedCandidate candidate = new GeneratedCandidate(generationToken, basis, generated);
            List<JobCoordinator.Diagnostic> warnings = relationshipDiagnostics(generated);
            return warnings.isEmpty()
                    ? JobCoordinator.Result.completed(candidate, "Output generated.", List.of(), List.of())
                    : JobCoordinator.Result.completedWithIssues(candidate,
                    unassignedSummary("Output generated", warnings.size()), List.of(), warnings);
        }, (attempt, result) -> acceptCompletion(generationToken, result),
                Optional.of(this::recaptureGenerationSubmission), this::resolveCompletion);
    }

    /** Recaptures every Project and Settings input for a linked user-requested retry. */
    private JobCoordinator.Submission<GeneratedCandidate> recaptureGenerationSubmission() {
        long generationToken = nextGenerationToken++;
        currentGenerationToken = generationToken;
        return generationSubmission(generationToken,
                new OutputBasis(projectFlow.frame().snapshot(), generationSettingsSource.capture()));
    }

    /** Accepts only the latest fresh usable completion, leaving prior accepted artifacts untouched otherwise. */
    private void acceptCompletion(long generationToken, JobCoordinator.Result<GeneratedCandidate> result) {
        if (generationToken != currentGenerationToken
                || result.lifecycle() != JobCoordinator.Lifecycle.COMPLETED
                && result.lifecycle() != JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES
                || !result.effectsCommitted().contains("Generated Output published"))
            return;
        Optional<GeneratedCandidate> value = result.value();
        if (value.isEmpty())
            return;
        GeneratedCandidate candidate = value.orElseThrow();
        Optional<String> selected = retainedBosSelection(candidate.output());
        selectedBosArtifact = selected.orElse(null);
        commit(Optional.of(candidate.output()), Optional.of(candidate.basis()), Freshness.FRESH, selected);
        publish(true, Optional.of(new RevealDrawer(nextEffectToken++)));
    }

    /** Creates one retryable central-job request over already accepted, defensively owned Output bytes. */
    private JobCoordinator.Submission<ExportCapture> exportSubmission(ExportCapture capture) {
        List<String> artifactLabels = capture.artifacts().stream()
                .map(OutputFeature::artifactLabel).toList();
        JobCoordinator.Operation operation = new JobCoordinator.Operation("Export Output", artifactLabels,
                List.of(capture.destinationLabel()), Optional.of(capture.basis().describe()),
                JobCoordinator.ConsistencyClass.SNAPSHOT_DERIVED);
        return new JobCoordinator.Submission<>(operation, context -> {
            context.report(JobCoordinator.Progress.indeterminate("Preflighting Output destinations", true));
            try {
                OutputArtifactPublisher.publishAll(capture.targetDirectory(), capture.artifacts(),
                        new OutputArtifactPublisher.PublicationContext() {
                            @Override
                            public void checkCancellation() {
                                context.checkCancellation();
                            }

                            @Override
                            public void reportStaged(long completedArtifacts, long totalArtifacts) {
                                context.report(JobCoordinator.Progress.determinate("Staging Output artifacts",
                                        completedArtifacts, totalArtifacts));
                            }

                            @Override
                            public boolean beginCommit() {
                                return context.beginCommit("Publishing Output artifacts");
                            }
                        });
            } catch (IOException exception) {
                return JobCoordinator.Result.failed("Export Output failed.", List.of(
                        new JobCoordinator.Diagnostic("OUTPUT_EXPORT_FAILED",
                                readableMessage(exception), Optional.of(exception.getClass().getName()))));
            }
            int count = capture.artifacts().size();
            List<String> effects = List.of("Published " + count + " Output artifacts");
            return capture.warnings().isEmpty()
                    ? JobCoordinator.Result.completed(capture, "Output exported.", effects, List.of())
                    : JobCoordinator.Result.completedWithIssues(capture,
                    unassignedSummary("Output exported", capture.warnings().size()), effects, capture.warnings());
        }, (attempt, result) -> {
            // Filesystem effects and Activity are already committed by the worker/coordinator paths.
        }, Optional.of(() -> recaptureExportSubmission(capture)));
    }

    /** Recaptures current accepted Output bytes while retaining the user-selected retry destination and scope. */
    private JobCoordinator.Submission<ExportCapture> recaptureExportSubmission(ExportCapture previous) {
        if (frame.freshness() == Freshness.FRESH && frame.generatedOutput().isPresent()) {
            ProjectGeneratedOutput output = frame.generatedOutput().orElseThrow();
            if (previous.scope() == ExportScope.COMPLETE)
                return exportSubmission(new ExportCapture(output.getArtifacts(), relationshipDiagnostics(output),
                        frame.basis().orElseThrow(), previous.targetDirectory(), Optional.empty(),
                        ExportScope.COMPLETE));
            Optional<OutputArtifact> selected = selectedTab == Tab.BOS_JSON ? frame.displayedArtifact()
                    : Optional.empty();
            if (selected.isPresent()) {
                Path destination = previous.selectedDestination().orElseThrow();
                OutputArtifact targeted = new SelectedOutputArtifact(selected.orElseThrow(),
                        destination.getFileName().toString());
                return exportSubmission(new ExportCapture(List.of(targeted), List.of(), frame.basis().orElseThrow(),
                        previous.targetDirectory(), Optional.of(destination), ExportScope.SELECTED_BOS));
            }
        }
        JobCoordinator.Operation operation = new JobCoordinator.Operation("Export Output", List.of("Output"),
                List.of(previous.destinationLabel()), Optional.of("No accepted Output is available"),
                JobCoordinator.ConsistencyClass.SNAPSHOT_DERIVED);
        return new JobCoordinator.Submission<>(operation, context -> JobCoordinator.Result.failed(
                "Generate Output before retrying export.", List.of(new JobCoordinator.Diagnostic(
                        "OUTPUT_NOT_AVAILABLE", "No accepted Output is available for export.", Optional.empty()))),
                (attempt, result) -> {
                    // The coordinator publishes the structured retry failure through the ordinary Activity path.
                }, Optional.of(() -> recaptureExportSubmission(previous)));
    }

    /** Returns stable nonblank I/O diagnostic text without assuming providers supply a message. */
    private static String readableMessage(IOException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    /** Reports every BoS source-to-filename mapping while keeping fixed INI labels concise. */
    private static String artifactLabel(OutputArtifact artifact) {
        if (artifact instanceof BosJsonArtifact bosArtifact)
            return bosArtifact.getFileNameMapping().formatForDisplay();
        if (artifact instanceof SelectedOutputArtifact selected
                && selected.source() instanceof BosJsonArtifact bosArtifact)
            return bosArtifact.getFileNameMapping().formatForDisplay();
        return artifact.getFileName();
    }

    /** Preserves the selected BoS Slider Preset when present, otherwise selects the first generated artifact. */
    private Optional<String> retainedBosSelection(ProjectGeneratedOutput generated) {
        if (selectedBosArtifact != null) {
            Optional<String> retained = generated.getBosJsonArtifacts().stream()
                    .map(BosJsonArtifact::getSliderPresetName)
                    .filter(name -> name.equalsIgnoreCase(selectedBosArtifact))
                    .findFirst();
            if (retained.isPresent())
                return retained;
        }
        return generated.getBosJsonArtifacts().stream().findFirst().map(BosJsonArtifact::getSliderPresetName);
    }

    /** Resolves supersession and freshness atomically on the coordinator's serialized publication lane. */
    private JobCoordinator.Result<GeneratedCandidate> resolveCompletion(JobCoordinator.AttemptId attempt,
                                                                         JobCoordinator.Result<GeneratedCandidate> result) {
        Objects.requireNonNull(attempt, "attempt");
        if (result.lifecycle() != JobCoordinator.Lifecycle.COMPLETED
                && result.lifecycle() != JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES)
            return result;
        Optional<GeneratedCandidate> value = result.value();
        if (value.isEmpty())
            return result;
        GeneratedCandidate candidate = value.orElseThrow();
        if (candidate.generationToken() != currentGenerationToken) {
            String message = "A newer Generate attempt superseded this result.";
            return JobCoordinator.Result.completedWithIssues(candidate, message, List.of(),
                    List.of(new JobCoordinator.Diagnostic("SUPERSEDED_RESULT", message, Optional.empty())));
        }
        if (!isCurrent(candidate.basis()))
            return JobCoordinator.Result.completedWithIssues(candidate, STALE_MESSAGE, List.of(),
                    List.of(new JobCoordinator.Diagnostic("STALE_RESULT", STALE_MESSAGE, Optional.empty())));
        if (result.diagnostics().isEmpty())
            return JobCoordinator.Result.completed(candidate, "Output generated.",
                    List.of("Generated Output published"), List.of());
        return JobCoordinator.Result.completedWithIssues(candidate,
                unassignedSummary("Output generated", result.diagnostics().size()),
                List.of("Generated Output published"), result.diagnostics());
    }

    /** Compares semantic content/settings identity rather than Project presentation sequence. */
    private boolean isCurrent(OutputBasis basis) {
        ProjectSnapshot currentProject = projectFlow.frame().snapshot();
        if (!basis.projectSnapshot().getContentVersion().equals(currentProject.getContentVersion()))
            return false;
        return basis.generationSettings().equals(generationSettingsSource.capture());
    }

    /** Converts one structured BoS validation failure into central Activity evidence. */
    private static JobCoordinator.Diagnostic jobDiagnostic(BosOutputDiagnostic diagnostic) {
        String message = diagnostic.getSliderPresetName().isEmpty()
                ? diagnostic.getMessage()
                : diagnostic.getSliderPresetName() + ": " + diagnostic.getMessage();
        return new JobCoordinator.Diagnostic(diagnostic.getCode(), message, Optional.empty());
    }

    /** Converts accepted unassigned-target metadata into non-fatal Output Activity diagnostics. */
    private static List<JobCoordinator.Diagnostic> relationshipDiagnostics(ProjectGeneratedOutput output) {
        List<JobCoordinator.Diagnostic> diagnostics = new java.util.ArrayList<>();
        output.getCustomMorphTargetsWithoutPresets().forEach(target -> diagnostics.add(
                new JobCoordinator.Diagnostic("CUSTOM_MORPH_TARGET_WITHOUT_PRESET",
                        "Custom Morph Target " + target.getName() + " has no assigned Slider Preset.",
                        Optional.empty())));
        output.getNpcMorphAssignmentsWithoutPresets().forEach(npc -> diagnostics.add(
                new JobCoordinator.Diagnostic("NPC_MORPH_ASSIGNMENT_WITHOUT_PRESET",
                        "NPC Morph Assignment " + npc.getPluginName() + "|" + npc.getFormId()
                                + " has no assigned Slider Preset.", Optional.empty())));
        return List.copyOf(diagnostics);
    }

    /** Formats one usable Output warning summary without misclassifying it as a failure. */
    private static String unassignedSummary(String completedText, int count) {
        return completedText + " with " + count + (count == 1 ? " unassigned target." : " unassigned targets.");
    }

    /** Pluralizes the stable failure count used in terminal job summaries. */
    private static String diagnosticSummary(int count) {
        return count + (count == 1 ? " diagnostic" : " diagnostics");
    }

    /** Commits one immutable Output frame before any later adapter observes it. */
    private void commit(Optional<ProjectGeneratedOutput> generatedOutput, Optional<OutputBasis> basis,
                        Freshness freshness, Optional<String> bosArtifactSelection) {
        frame = new Frame(++revision, generatedOutput, basis, freshness, selectedTab, bosArtifactSelection);
    }

    /** Notifies every attached adapter after state commit while isolating renderer failures. */
    private Update publish(boolean accepted, Optional<Effect> effect) {
        Update update = update(accepted, effect);
        for (Consumer<Update> observer : List.copyOf(observers.values())) {
            try {
                observer.accept(update);
            } catch (RuntimeException | LinkageError failure) {
                observerFailureSink.accept(failure);
            }
        }
        return update;
    }

    /** Creates one immutable update without notifying observers. */
    private Update update(boolean accepted, Optional<Effect> effect) {
        return new Update(accepted, frame, effect);
    }

    /** Immutable generation-settings and command-option capture. */
    public record GenerationSettings(Settings.Snapshot settings, boolean omitRedundantSliders) {
        /** Requires the complete paired Settings snapshot. */
        public GenerationSettings {
            Objects.requireNonNull(settings, "settings");
        }
    }

    /** One coherent Project and generation-settings basis retained by a Generate attempt and accepted result. */
    public record OutputBasis(ProjectSnapshot projectSnapshot, GenerationSettings generationSettings) {
        /** Requires both immutable input families. */
        public OutputBasis {
            Objects.requireNonNull(projectSnapshot, "projectSnapshot");
            Objects.requireNonNull(generationSettings, "generationSettings");
        }

        /** @return captured paired Settings value */
        public Settings.Snapshot settings() {
            return generationSettings.settings();
        }

        /** @return captured redundant-slider omission option */
        public boolean omitRedundantSliders() {
            return generationSettings.omitRedundantSliders();
        }

        /** @return concise immutable-basis evidence retained in Activity */
        public String describe() {
            return projectSnapshot.getContentVersion() + "; Settings "
                    + Integer.toUnsignedString(settings().hashCode(), 16)
                    + "; omit redundant sliders: " + omitRedundantSliders();
        }
    }

    /** Latest immutable Output state; absent output is intentionally distinct from empty generated text. */
    public record Frame(long revision, Optional<ProjectGeneratedOutput> generatedOutput,
                        Optional<OutputBasis> basis, Freshness freshness, Tab selectedTab,
                        Optional<String> selectedBosArtifact) {
        /** Requires coherent absent/present output and basis state. */
        public Frame {
            if (revision <= 0)
                throw new IllegalArgumentException("revision must be positive");
            Objects.requireNonNull(generatedOutput, "generatedOutput");
            Objects.requireNonNull(basis, "basis");
            Objects.requireNonNull(freshness, "freshness");
            Objects.requireNonNull(selectedTab, "selectedTab");
            Objects.requireNonNull(selectedBosArtifact, "selectedBosArtifact");
            if (generatedOutput.isPresent() != basis.isPresent())
                throw new IllegalArgumentException("generated output and basis must be present together");
            if ((freshness == Freshness.FRESH) != generatedOutput.isPresent())
                throw new IllegalArgumentException("only fresh frames may carry generated output");
            if (selectedBosArtifact.isPresent() && generatedOutput.stream()
                    .flatMap(output -> output.getBosJsonArtifacts().stream())
                    .noneMatch(artifact -> artifact.getSliderPresetName()
                            .equalsIgnoreCase(selectedBosArtifact.orElseThrow())))
                throw new IllegalArgumentException("selected BoS artifact must exist in generated output");
        }

        /** @return generated BoS Slider Preset identities in canonical artifact order */
        public List<String> bosArtifactNames() {
            return generatedOutput.stream().flatMap(output -> output.getBosJsonArtifacts().stream())
                    .map(BosJsonArtifact::getSliderPresetName).toList();
        }

        /** @return canonical text of the selected BoS artifact, or explicit empty-artifact guidance */
        public String selectedBosText() {
            return generatedOutput.stream().flatMap(output -> output.getBosJsonArtifacts().stream())
                    .filter(artifact -> selectedBosArtifact.stream().anyMatch(name ->
                            name.equalsIgnoreCase(artifact.getSliderPresetName())))
                    .findFirst().map(BosJsonArtifact::getText)
                    .orElse("No BoS JSON artifacts were generated.");
        }

        /** @return accepted artifact selected by the current Output tab and BoS identity */
        public Optional<OutputArtifact> displayedArtifact() {
            return generatedOutput.flatMap(output -> switch (selectedTab) {
                case TEMPLATES -> Optional.of(output.getTemplatesArtifact());
                case MORPHS -> Optional.of(output.getMorphsArtifact());
                case BOS_JSON -> output.getBosJsonArtifacts().stream()
                        .filter(artifact -> selectedBosArtifact.stream().anyMatch(name ->
                                name.equalsIgnoreCase(artifact.getSliderPresetName())))
                        .findFirst().map(artifact -> (OutputArtifact) artifact);
            });
        }

        /**
         * @return read-only text belonging to the selected tab/artifact, or the current empty/freshness guidance
         */
        public String displayedText() {
            if (generatedOutput.isEmpty())
                return freshness == Freshness.INVALIDATED
                        ? STALE_MESSAGE : "Generate Project output to inspect it here.";
            return displayedArtifact().map(OutputArtifact::getText)
                    .orElse("No BoS JSON artifacts were generated.");
        }
    }

    /** Result of one Output intent. */
    public record Update(boolean accepted, Frame frame, Optional<Effect> effect) {
        /** Requires the latest coherent Output frame. */
        public Update {
            Objects.requireNonNull(frame, "frame");
            Objects.requireNonNull(effect, "effect");
        }
    }

    /** Tokenized at-most-once effect emitted only by a freshly accepted Generate result. */
    public sealed interface Effect permits RevealDrawer, CopyToClipboard, ChooseExportDirectory, ChooseExportFile {
        /** @return monotonically increasing at-most-once platform-effect identity */
        long token();

        /** @return semantic effect family used by adapters that do not need the typed payload */
        EffectKind kind();
    }

    /** Fresh Generate completion requests the sole accepted Output navigation side effect. */
    public record RevealDrawer(long token) implements Effect {
        /** Requires a positive effect identity. */
        public RevealDrawer {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
        }

        /** {@inheritDoc} */
        @Override
        public EffectKind kind() {
            return EffectKind.REVEAL_DRAWER;
        }
    }

    /** Clipboard effect carrying text decoded from one accepted artifact's owned bytes. */
    public record CopyToClipboard(long token, String artifactName, String text) implements Effect {
        /** Requires complete immutable clipboard context. */
        public CopyToClipboard {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(artifactName, "artifactName");
            Objects.requireNonNull(text, "text");
        }

        /** {@inheritDoc} */
        @Override
        public EffectKind kind() {
            return EffectKind.COPY_TO_CLIPBOARD;
        }
    }

    /** Native directory-chooser effect whose response returns through {@link #completeExport(long, Optional)}. */
    public record ChooseExportDirectory(long token) implements Effect {
        /** Requires a positive effect identity. */
        public ChooseExportDirectory {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
        }

        /** {@inheritDoc} */
        @Override
        public EffectKind kind() {
            return EffectKind.CHOOSE_EXPORT_DIRECTORY;
        }
    }

    /** Native save-file chooser for one selected accepted BoS artifact. */
    public record ChooseExportFile(long token, String suggestedFileName) implements Effect {
        /** Requires a positive effect identity and the accepted artifact's canonical filename. */
        public ChooseExportFile {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(suggestedFileName, "suggestedFileName");
        }

        /** {@inheritDoc} */
        @Override
        public EffectKind kind() {
            return EffectKind.CHOOSE_EXPORT_FILE;
        }
    }

    /** Supplies one detached settings/options value at admission and freshness boundaries. */
    @FunctionalInterface
    public interface GenerationSettingsSource {
        /** @return current immutable generation settings and options */
        GenerationSettings capture();
    }

    /** Task family owned by Output. */
    public sealed interface Intent permits Generate, SelectTab, SelectBosArtifact, Copy, Export, ExportSelected {
    }

    /** Requests generation of all Output artifact families from one captured basis. */
    public record Generate() implements Intent {
    }

    /** Selects one Output artifact-family tab. */
    public record SelectTab(Tab tab) implements Intent {
        /** Requires a semantic tab identity. */
        public SelectTab {
            Objects.requireNonNull(tab, "tab");
        }
    }

    /** Selects one generated BoS artifact by Slider Preset identity. */
    public record SelectBosArtifact(String sliderPresetName) implements Intent {
        /** Requires a non-null identity; membership is validated by dispatch. */
        public SelectBosArtifact {
            Objects.requireNonNull(sliderPresetName, "sliderPresetName");
        }
    }

    /** Copies the artifact currently selected in the accepted Output drawer. */
    public record Copy() implements Intent {
    }

    /** Requests complete-batch export of the currently accepted Output value. */
    public record Export() implements Intent {
    }

    /** Requests one-file export of the selected accepted BoS artifact. */
    public record ExportSelected() implements Intent {
    }

    /** Whether the feature currently retains accepted output for the active Project/settings basis. */
    public enum Freshness {
        EMPTY,
        FRESH,
        INVALIDATED
    }

    /** Output artifact families exposed by the drawer's tabbed region. */
    public enum Tab {
        TEMPLATES,
        MORPHS,
        BOS_JSON
    }

    /** Kernel-owned effect request family produced by Output. */
    public enum EffectKind {
        REVEAL_DRAWER,
        COPY_TO_CLIPBOARD,
        CHOOSE_EXPORT_DIRECTORY,
        CHOOSE_EXPORT_FILE
    }

    /** Idempotent Output observer handle. */
    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        /** Detaches the observer; repeated calls have no additional effect. */
        @Override
        void close();
    }

    /** Worker-to-publication value that keeps attempt identity, basis, and output inseparable. */
    private record GeneratedCandidate(long generationToken, OutputBasis basis, ProjectGeneratedOutput output) {
        /** Requires a positive generation identity and complete immutable values. */
        private GeneratedCandidate {
            if (generationToken <= 0)
                throw new IllegalArgumentException("generationToken must be positive");
            Objects.requireNonNull(basis, "basis");
            Objects.requireNonNull(output, "output");
        }
    }

    /** Accepted bytes and basis retained while the native export-directory chooser is open. */
    private record PendingExport(long token, ProjectGeneratedOutput output, OutputBasis basis) {
        /** Requires one positive effect identity and complete accepted Output state. */
        private PendingExport {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(output, "output");
            Objects.requireNonNull(basis, "basis");
        }
    }

    /** Selected accepted artifact and basis retained while its native save chooser is open. */
    private record PendingSelectedExport(long token, OutputArtifact artifact, OutputBasis basis) {
        /** Requires one positive effect identity and complete accepted selected Output state. */
        private PendingSelectedExport {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(basis, "basis");
        }
    }

    /** Accepted artifacts, warnings, basis, scope, and normalized destination captured by one export attempt. */
    private record ExportCapture(List<OutputArtifact> artifacts, List<JobCoordinator.Diagnostic> warnings,
                                 OutputBasis basis, Path targetDirectory, Optional<Path> selectedDestination,
                                 ExportScope scope) {
        /** Requires all immutable export inputs before central-job admission. */
        private ExportCapture {
            artifacts = List.copyOf(Objects.requireNonNull(artifacts, "artifacts"));
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
            Objects.requireNonNull(basis, "basis");
            Objects.requireNonNull(targetDirectory, "targetDirectory");
            Objects.requireNonNull(selectedDestination, "selectedDestination");
            Objects.requireNonNull(scope, "scope");
            if (artifacts.isEmpty())
                throw new IllegalArgumentException("artifacts must not be empty");
            if ((scope == ExportScope.SELECTED_BOS) != selectedDestination.isPresent())
                throw new IllegalArgumentException("selected destination must match selected BoS scope");
        }

        /** @return exact destination shown in Activity for this export scope */
        private String destinationLabel() {
            return selectedDestination.map(Path::toString).orElseGet(targetDirectory::toString);
        }
    }

    /** Complete-directory and selected-BoS retry recapture policies. */
    private enum ExportScope {
        COMPLETE,
        SELECTED_BOS
    }

    /** One selected accepted artifact retargeted to the user's safe chosen filename without changing its bytes. */
    private record SelectedOutputArtifact(OutputArtifact source, String fileName) implements OutputArtifact {
        /** Requires the immutable accepted source and selected leaf name. */
        private SelectedOutputArtifact {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(fileName, "fileName");
        }

        /** {@inheritDoc} */
        @Override
        public String getFileName() {
            return fileName;
        }

        /** {@inheritDoc} */
        @Override
        public byte[] getBytes() {
            return source.getBytes();
        }

        /** {@inheritDoc} */
        @Override
        public String getText() {
            return source.getText();
        }
    }
}
