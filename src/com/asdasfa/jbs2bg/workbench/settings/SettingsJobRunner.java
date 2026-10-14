package com.asdasfa.jbs2bg.workbench.settings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.workbench.GenerationPreferencesStore;
import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

/**
 * Owns Settings persistence on the central job path: it admits Save, Reload, and generation-preference effects to the
 * shared coordinator, runs their blocking I/O on the worker against one explicit Settings store, recaptures the
 * current draft when the user retries a failed attempt, and settles the feature on the serialized publication lane.
 * It is JavaFX-free; the Workbench shell renders and continues through a {@link Listener}.
 */
public final class SettingsJobRunner {
    /** Coordinator operation name of a Settings Save, which the shell's close lifecycle recognizes. */
    public static final String SAVE_OPERATION = "Save Settings";

    private final SettingsFeature feature;
    private final Settings settings;
    private final JobCoordinator jobs;
    private final Listener listener;

    /**
     * Creates one window-scoped runner over the shared job coordinator.
     *
     * @param feature  Settings draft state that captured the effects and is settled by their completions
     * @param settings the one Settings store that Save persists to and Reload reloads
     * @param jobs     application-wide job coordinator
     * @param listener receives settled completions and retry recaptures on the publication lane
     */
    public SettingsJobRunner(SettingsFeature feature, Settings settings, JobCoordinator jobs, Listener listener) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    /**
     * Admits one immutable worker effect to the coordinator. When admission is refused the feature's pending effect
     * is cancelled, so the draft is free for the next intent; the caller re-renders the feature frame.
     *
     * @param effect captured Save, Reload, or generation-preference effect
     * @return whether the coordinator admitted the job
     * @throws IllegalArgumentException when the effect is a Reload confirmation, which must be answered first
     */
    public boolean submit(SettingsFeature.Effect effect) {
        if (Objects.requireNonNull(effect, "effect") instanceof SettingsFeature.ReloadConfirmationEffect)
            throw new IllegalArgumentException("Reload confirmation must complete before worker admission");
        JobCoordinator.Admission admission = jobs.submit(submission(effect));
        if (!admission.admitted())
            feature.cancel(effect.token());
        return admission.admitted();
    }

    /** Builds one retryable Settings submission whose retry recaptures current feature inputs. */
    private JobCoordinator.Submission<SettingsFeature.Completion> submission(SettingsFeature.Effect effect) {
        String operationName = switch (effect) {
            case SettingsFeature.SaveEffect ignored -> SAVE_OPERATION;
            case SettingsFeature.ReloadEffect ignored -> "Reload Settings";
            case SettingsFeature.PreferenceEffect ignored -> "Save Generation Preference";
            case SettingsFeature.ReloadConfirmationEffect ignored -> throw new IllegalArgumentException(
                    "Reload confirmation must complete before worker admission");
        };
        JobCoordinator.Operation operation = new JobCoordinator.Operation(
                operationName,
                effect instanceof SettingsFeature.ReloadEffect ? List.of(effect.directory().toString()) : List.of(),
                effect instanceof SettingsFeature.SaveEffect || effect instanceof SettingsFeature.PreferenceEffect
                        ? List.of(effect.directory().toString()) : List.of(),
                Optional.empty());
        return new JobCoordinator.Submission<>(operation,
                context -> run(settings, effect, context),
                (attempt, result) -> listener.completed(effect, applyCompletion(feature, effect, result)),
                Optional.of(retryFactory(effect)));
    }

    /** Builds dynamic retry availability and recapture around the current Settings draft. */
    private JobCoordinator.RetryFactory<SettingsFeature.Completion> retryFactory(SettingsFeature.Effect previous) {
        return new JobCoordinator.RetryFactory<>() {
            /** Recaptures current inputs and reports the replacement before it is admitted. */
            @Override
            public JobCoordinator.Submission<SettingsFeature.Completion> recapture() {
                return recaptureSubmission(previous);
            }

            /** Save needs a valid draft; dirty Reload must use the ordinary confirmation path. */
            @Override
            public Optional<String> unavailableReason() {
                if (previous instanceof SettingsFeature.SaveEffect)
                    return feature.saveRetryUnavailableReason();
                if (previous instanceof SettingsFeature.ReloadEffect)
                    return feature.reloadRetryUnavailableReason();
                return Optional.empty();
            }
        };
    }

    /** Recaptures Save drafts or the Reload directory when the user explicitly retries a failed Settings job. */
    private JobCoordinator.Submission<SettingsFeature.Completion> recaptureSubmission(
            SettingsFeature.Effect previous) {
        SettingsFeature.Update recaptured = feature.retry(previous);
        SettingsFeature.Effect replacement = recaptured.effect().orElseThrow(
                () -> new IllegalStateException("Settings retry could not recapture its persistence inputs"));
        listener.recaptured(previous, replacement);
        return submission(replacement);
    }

    /**
     * Executes blocking Settings I/O on the worker. Reload stays cancellable through lock acquisition and ordinary
     * pair reads; the Settings seam enters commit immediately before recovery or publication can mutate state.
     *
     * @param settings the Settings store that Save persists to and Reload reloads
     * @param effect   tokenized Save, Reload, or generation-preference effect captured on the JavaFX thread
     * @param context  coordinator context owning cancellation and the commit boundary
     * @return terminal coordinator result carrying the feature completion
     */
    static JobCoordinator.Result<SettingsFeature.Completion> run(
            Settings settings, SettingsFeature.Effect effect, JobCoordinator.Context context) {
        Objects.requireNonNull(settings, "settings");
        context.checkCancellation();
        String commitPhase = switch (effect) {
            case SettingsFeature.SaveEffect ignored -> "Saving Settings";
            case SettingsFeature.ReloadEffect ignored -> "Reloading Settings";
            case SettingsFeature.PreferenceEffect ignored -> "Saving generation preference";
            case SettingsFeature.ReloadConfirmationEffect ignored -> throw new IllegalArgumentException(
                    "Reload confirmation cannot execute on the worker");
        };
        boolean reloadEffect = effect instanceof SettingsFeature.ReloadEffect;
        if (!reloadEffect && !context.beginCommit(commitPhase))
            return JobCoordinator.Result.cancelled("Settings operation cancelled.", List.of(), List.of());
        SettingsFeature.Completion completion;
        boolean successful;
        List<JobCoordinator.Diagnostic> diagnostics;
        String successSummary;
        String failureSummary;
        switch (effect) {
            case SettingsFeature.SaveEffect save -> {
                Settings.PersistenceResult result = settings.persist(save.replacement());
                completion = new SettingsFeature.SaveCompletion(save.token(), result);
                successful = result.isSuccessful();
                diagnostics = settingsDiagnostics(result);
                successSummary = "Settings saved.";
                failureSummary = "Settings could not be saved.";
            }
            case SettingsFeature.ReloadEffect reload -> {
                Settings.InitializationResult result = settings.reload(() -> context.beginCommit(commitPhase));
                completion = new SettingsFeature.ReloadCompletion(reload.token(), result);
                successful = result.isSuccessful();
                diagnostics = settingsDiagnostics(result);
                boolean recovered = result.getDiagnostics().stream()
                        .anyMatch(value -> value.getCode().equals("SETTINGS_PUBLICATION_RECOVERED"));
                successSummary = recovered ? "Settings recovered and reloaded." : "Settings reloaded.";
                failureSummary = "Settings could not be reloaded.";
            }
            case SettingsFeature.PreferenceEffect preference -> {
                try {
                    new GenerationPreferencesStore(preference.directory()).save(preference.selected());
                    completion = SettingsFeature.PreferenceCompletion.successful(preference.token());
                    successful = true;
                    diagnostics = List.of();
                } catch (IOException failure) {
                    SettingsFeature.PreferenceCompletion failed =
                            SettingsFeature.PreferenceCompletion.failed(preference.token(), failure);
                    completion = failed;
                    successful = false;
                    diagnostics = List.of(generationPreferenceDiagnostic(preference.directory(),
                            failed.failureMessage().orElseThrow()));
                }
                successSummary = "Generation preference saved.";
                failureSummary = "Generation preference could not be saved.";
            }
            case SettingsFeature.ReloadConfirmationEffect ignored -> throw new IllegalArgumentException(
                    "Reload confirmation cannot execute on the worker");
        }
        if (!successful)
            return JobCoordinator.Result.failed(completion, failureSummary, diagnostics);
        List<String> effects = effect instanceof SettingsFeature.PreferenceEffect
                ? List.of("Published generated-output preference")
                : List.of("Published Standard and UUNP Settings");
        return diagnostics.isEmpty()
                ? JobCoordinator.Result.completed(completion, successSummary, effects, List.of())
                : JobCoordinator.Result.completedWithIssues(completion, successSummary, effects, diagnostics);
    }

    /**
     * Applies a Settings worker value only when its coordinator lifecycle accepted it. A cancellation won before
     * commit may still carry a failed I/O value, which must not replace the retained draft with failure feedback.
     *
     * @param feature Settings draft state awaiting the effect
     * @param effect  tokenized worker effect being settled
     * @param result  authoritative coordinator result after cancellation classification
     * @return the completed feature frame, or the unchanged frame after cancellation
     */
    static SettingsFeature.Update applyCompletion(SettingsFeature feature, SettingsFeature.Effect effect,
                                                  JobCoordinator.Result<SettingsFeature.Completion> result) {
        Objects.requireNonNull(feature, "feature");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(result, "result");
        if (result.lifecycle() == JobCoordinator.Lifecycle.CANCELLED || result.value().isEmpty())
            return feature.cancel(effect.token());
        return feature.complete(result.value().orElseThrow());
    }

    /** Preserves a generation-preference failure's stable code and profile-local path in terminal job evidence. */
    private static JobCoordinator.Diagnostic generationPreferenceDiagnostic(Path directory, String message) {
        return new JobCoordinator.Diagnostic("GENERATION_PREFERENCES_IO_FAILED", message,
                Optional.of(directory + " /omitRedundantSliders"));
    }

    /** Converts one Save failure or warning set to coordinator-owned structured diagnostics. */
    private static List<JobCoordinator.Diagnostic> settingsDiagnostics(Settings.PersistenceResult result) {
        if (!result.isSuccessful())
            return List.of(settingsDiagnostic(result.getFailure().orElseThrow()));
        return result.getDiagnostics().stream().map(SettingsJobRunner::settingsDiagnostic).toList();
    }

    /** Converts one Reload failure or warning set to coordinator-owned structured diagnostics. */
    private static List<JobCoordinator.Diagnostic> settingsDiagnostics(Settings.InitializationResult result) {
        if (!result.isSuccessful())
            return List.of(settingsDiagnostic(result.getFailure().orElseThrow()));
        return result.getDiagnostics().stream().map(SettingsJobRunner::settingsDiagnostic).toList();
    }

    /** Preserves a Settings failure's stable code and source path in terminal job evidence. */
    private static JobCoordinator.Diagnostic settingsDiagnostic(Settings.Failure failure) {
        return new JobCoordinator.Diagnostic(failure.getCode(), failure.getMessage(),
                Optional.of(failure.getSource() + " " + failure.getPath()));
    }

    /** Preserves a non-blocking Settings diagnostic in terminal job evidence. */
    private static JobCoordinator.Diagnostic settingsDiagnostic(Settings.Diagnostic diagnostic) {
        return new JobCoordinator.Diagnostic(diagnostic.getCode(), diagnostic.getMessage(),
                Optional.of(diagnostic.getSource() + " " + diagnostic.getPath()));
    }

    /**
     * Receives Settings job outcomes on the coordinator's serialized publication lane.
     */
    @FunctionalInterface
    public interface Listener {
        /**
         * Delivers one settled worker result. The update may carry a follow-up effect (the original Reload after a
         * dirty Reload answered Save); the listener realizes it, normally by {@link SettingsJobRunner#submit submitting} it back.
         *
         * @param effect the worker effect that settled
         * @param update the feature update after completion, or after cancellation when the job was cancelled
         */
        void completed(SettingsFeature.Effect effect, SettingsFeature.Update update);

        /**
         * Reports that an explicit retry recaptured the current inputs of a failed effect under a fresh token, just
         * before the replacement is admitted. The shell uses it to move a pending close onto the new token.
         *
         * @param previous    the failed effect being retried
         * @param replacement the recaptured effect that will run instead
         */
        default void recaptured(SettingsFeature.Effect previous, SettingsFeature.Effect replacement) {
            // Listeners without token-keyed continuations have nothing to transfer.
        }
    }
}
