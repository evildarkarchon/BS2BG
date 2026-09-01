package com.asdasfa.jbs2bg.workbench.output;

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
        Objects.requireNonNull(projectFrame, "projectFrame");
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
        };
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
            return JobCoordinator.Result.completed(candidate, "Output generated.", List.of(), List.of());
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
                || (result.lifecycle() != JobCoordinator.Lifecycle.COMPLETED
                && result.lifecycle() != JobCoordinator.Lifecycle.COMPLETED_WITH_ISSUES))
            return;
        Optional<GeneratedCandidate> value = result.value();
        if (value.isEmpty() || !result.effectsCommitted().contains("Generated Output published"))
            return;
        GeneratedCandidate candidate = value.orElseThrow();
        Optional<String> selected = retainedBosSelection(candidate.output());
        selectedBosArtifact = selected.orElse(null);
        commit(Optional.of(candidate.output()), Optional.of(candidate.basis()), Freshness.FRESH, selected);
        publish(true, Optional.of(new Effect(nextEffectToken++, EffectKind.REVEAL_DRAWER)));
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
        return JobCoordinator.Result.completed(candidate, "Output generated.",
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

        /**
         * @return read-only text belonging to the selected tab/artifact, or the current empty/freshness guidance
         */
        public String displayedText() {
            if (generatedOutput.isEmpty())
                return freshness == Freshness.INVALIDATED
                        ? STALE_MESSAGE : "Generate Project output to inspect it here.";
            ProjectGeneratedOutput output = generatedOutput.orElseThrow();
            return switch (selectedTab) {
                case TEMPLATES -> output.getTemplatesText();
                case MORPHS -> output.getMorphsText();
                case BOS_JSON -> output.getBosJsonArtifacts().stream()
                        .filter(artifact -> selectedBosArtifact.stream().anyMatch(name ->
                                name.equalsIgnoreCase(artifact.getSliderPresetName())))
                        .findFirst().map(BosJsonArtifact::getText)
                        .orElse("No BoS JSON artifacts were generated.");
            };
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
    public record Effect(long token, EffectKind kind) {
        /** Requires a positive effect token and semantic effect kind. */
        public Effect {
            if (token <= 0)
                throw new IllegalArgumentException("token must be positive");
            Objects.requireNonNull(kind, "kind");
        }
    }

    /** Supplies one detached settings/options value at admission and freshness boundaries. */
    @FunctionalInterface
    public interface GenerationSettingsSource {
        /** @return current immutable generation settings and options */
        GenerationSettings capture();
    }

    /** Task family owned by Output. */
    public sealed interface Intent permits Generate, SelectTab, SelectBosArtifact {
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
        REVEAL_DRAWER
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
}
