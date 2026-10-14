package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * The shared fixture for every Workbench module test: an {@code <Area>AreaTest} through {@link AreaTest}, and the
 * Output drawer's test. It loads one module's FXML alone, through the same production loader the shell uses, and
 * hosts the module's nodes in a minimal Scene. The module drives a real feature over a real
 * {@link com.asdasfa.jbs2bg.project.ProjectSession} built from an explicit Settings store, with a
 * {@link RecordingPlatform} behind the pipeline's dialogs and a {@link RecordingShellPort} in place of the shell.
 * <p>
 * The fixture plays the shell's part of the module contract and nothing more: it builds the
 * {@link WorkbenchContext}, renders it into the module once on load and again on every job frame, and feeds the job
 * frames to the pipeline's gate. Tests assert through the module's controls and the recorded port requests. The Stage
 * is never shown unless a test asks for it with {@link Hosted#show()}, which is only for focus assertions.
 * <p>
 * Subclasses implement {@link #mount(Collaborators)} to build the module's feature, call its production loader and
 * say how to host and render it. Every method that touches the hosted module runs on the JavaFX Application Thread.
 *
 * @param <M> the module under test
 */
public abstract class ModuleTest<M> {
    /** Fixed clock for the feedback model and for modules whose behaviour reads time. */
    protected static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC);

    @TempDir
    protected Path temporaryDirectory;
    /** Scripted dialogs and native effects; queue responses before the gesture that needs them. */
    protected final RecordingPlatform platform = new RecordingPlatform();
    /** Records every request the module makes of the shell. */
    protected final RecordingShellPort shell = new RecordingShellPort();
    // Memoized on first use in the test's temporary directory; a test seeding custom Settings assigns it first.
    private WorkbenchTestEnvironment fixture;
    // The module hosted by this test, kept so later FX bodies of the same test can reach it.
    private volatile Hosted<M> hosted;

    /**
     * Everything the shell would hand a module's controller factory, for {@link #mount(Collaborators)} to pick from.
     *
     * @param environment the Workbench environment built from the fixture's Settings store
     * @param flow        the Project flow over a real session and the hosted job coordinator
     * @param pipeline    the dispatch pipeline whose gate follows the hosted job frames
     * @param platform    the recording platform
     * @param shell       the recording shell port
     * @param clock       the fixed clock
     * @param stage       the Stage that will host the module, which owns its native choosers and dialogs
     */
    public record Collaborators(WorkbenchEnvironment environment, WorkbenchProjectFlow flow,
                                DispatchPipeline pipeline, WorkbenchPlatform platform, ShellPort shell, Clock clock,
                                Stage stage) {
    }

    /**
     * One loaded module, ready for the fixture to host.
     *
     * @param module    the module controller
     * @param namespace the module FXML's {@code fx:id} namespace
     * @param root      the Scene root that holds every node the shell would place, made visible as the shell would
     *                  show the module
     * @param render    renders one context into the module, as the shell's fan-out does
     * @param <M>       the module type
     */
    public record Mount<M>(M module, Map<String, Object> namespace, Parent root,
                              Consumer<WorkbenchContext> render) {
        /** Requires every value. */
        public Mount {
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(render, "render");
        }
    }

    /**
     * Builds the module's feature, loads the module through its production loader, and says how to host it.
     *
     * @param collaborators the shell-side collaborators
     * @return the loaded module, its Scene root and its render
     */
    protected abstract Mount<M> mount(Collaborators collaborators);

    /**
     * Supplies the context's live-Settings flag. The default follows the fixture's startup result; a module that owns
     * the Settings feature overrides it to read the feature, as the shell does.
     *
     * @return whether Settings are live
     */
    protected boolean settingsLiveAvailable() {
        return fixture().startup().isSuccessful();
    }

    /** @return the test's Settings fixture, created in the temporary directory on first use */
    protected final WorkbenchTestEnvironment fixture() {
        if (fixture == null)
            fixture = WorkbenchTestEnvironment.initialized(temporaryDirectory);
        return fixture;
    }

    /**
     * Replaces the Settings fixture, for tests that seed custom Settings; call it before {@link #host}.
     *
     * @param replacement the fixture to use
     */
    protected final void useFixture(WorkbenchTestEnvironment replacement) {
        fixture = Objects.requireNonNull(replacement, "replacement");
    }

    /**
     * Hosts the module over a Project flow with its own default job coordinator.
     *
     * @return the hosted module
     */
    protected final Hosted<M> host() {
        return host(new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession()));
    }

    /**
     * Hosts the module over a Project flow driven by the given coordinator, so a test controls the worker and
     * publication lanes. Like the shell, the fixture observes the coordinator; the first job frame is published on
     * the coordinator's publication lane.
     *
     * @param jobs the coordinator the module's jobs run on
     * @return the hosted module
     */
    protected final Hosted<M> host(JobCoordinator jobs) {
        return host(new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs));
    }

    /**
     * Hosts the module over a Project flow the test built, for tests that edit the Project before the module loads.
     *
     * @param flow the Project flow the module runs over
     * @return the hosted module
     */
    protected final Hosted<M> host(WorkbenchProjectFlow flow) {
        if (hosted != null)
            throw new IllegalStateException("This test already hosts its module");
        Stage stage = new Stage();
        long[] renderedSequence = {flow.frame().sequence()};
        // The chrome re-renders the Hosted that is created only after the pipeline it is passed to.
        AtomicReference<Hosted<M>> self = new AtomicReference<>();
        DispatchPipeline pipeline = new DispatchPipeline(shell.feedback(), new DispatchPipeline.DialogPort() {
            /** Completes a scripted confirmation. */
            @Override
            public WorkbenchFeedback.DialogAction confirm(WorkbenchFeedback.DialogSpec spec) {
                return platform.completeConfirmation(spec, stage);
            }

            /** Completes a scripted failure dialog. */
            @Override
            public WorkbenchFeedback.DialogAction fail(WorkbenchFeedback.DialogSpec spec) {
                return platform.completeFailure(spec, stage);
            }
        }, frame -> {
            // The shell renders round-trip feedback frames; a module test asserts the module, not the shell's InfoBar.
        }, new DispatchPipeline.ProjectChrome() {
            /** @return the Project sequence the fixture last rendered */
            @Override
            public long renderedSequence() {
                return renderedSequence[0];
            }

            /** Records the sequence and re-renders the module against a fresh context, as the shell's chrome does. */
            @Override
            public void render(long sequence) {
                renderedSequence[0] = sequence;
                self.get().renderContext();
            }
        });
        Mount<M> mounted = mount(new Collaborators(fixture().environment(), flow, pipeline, platform, shell, CLOCK,
                stage));
        stage.setScene(new Scene(mounted.root(), 1000.0, 720.0));
        Hosted<M> created = new Hosted<>(this, mounted, flow, pipeline, stage);
        self.set(created);
        hosted = created;
        created.renderContext();
        flow.jobs().observe(frame -> {
            pipeline.acceptJobFrame(frame);
            created.renderContext();
        });
        return created;
    }

    /** @return the module this test hosted */
    protected final Hosted<M> hosted() {
        return Objects.requireNonNull(hosted, "no module is hosted yet");
    }

    /**
     * One module hosted in a minimal Scene.
     *
     * @param <M> the module under test
     */
    public static final class Hosted<M> {
        private final ModuleTest<M> test;
        private final Mount<M> mounted;
        private final WorkbenchProjectFlow flow;
        private final DispatchPipeline pipeline;
        private final Stage stage;
        private WorkbenchContext context;

        /** Wraps one loaded module and its host. */
        private Hosted(ModuleTest<M> test, Mount<M> mounted, WorkbenchProjectFlow flow, DispatchPipeline pipeline,
                       Stage stage) {
            this.test = test;
            this.mounted = mounted;
            this.flow = flow;
            this.pipeline = pipeline;
            this.stage = stage;
        }

        /** @return the module controller */
        public M module() {
            return mounted.module();
        }

        /**
         * Looks up one node or control the module FXML declares.
         *
         * @param id the {@code fx:id}
         * @param <T> the expected type
         * @return the node
         * @throws AssertionError when the module FXML declares no such id
         */
        @SuppressWarnings("unchecked")
        public <T> T node(String id) {
            Object value = mounted.namespace().get(id);
            if (value == null)
                throw new AssertionError("The module FXML declares no fx:id " + id);
            return (T) value;
        }

        /** @return the module FXML's namespace */
        public Map<String, Object> namespace() {
            return mounted.namespace();
        }

        /** @return the Project flow the module runs over */
        public WorkbenchProjectFlow flow() {
            return flow;
        }

        /** @return the Stage hosting the module; not shown unless {@link #show()} was called */
        public Stage stage() {
            return stage;
        }

        /** @return the context the fixture last rendered into the module */
        public WorkbenchContext context() {
            return context;
        }

        /** Shows the Stage, for tests that assert real focus. */
        public void show() {
            stage.show();
        }

        /**
         * Builds a fresh context from the live sources and renders it into the module, as the shell's fan-out does.
         */
        public void renderContext() {
            JobCoordinator.Frame jobs = flow.jobs().frame();
            context = new WorkbenchContext(flow.frame(), false, false, pipeline.mutationsBlocked(),
                    jobs.active() || jobs.shutdownRequested(), test.settingsLiveAvailable(), Optional.empty());
            mounted.render().accept(context);
        }
    }
}
