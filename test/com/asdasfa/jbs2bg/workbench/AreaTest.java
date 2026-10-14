package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.workbench.jobs.JobCoordinator;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

/**
 * The shared fixture for every {@code <Area>AreaTest}: it loads one Area's FXML alone, through the same
 * {@link AreaLoader} the shell uses, and hosts its three panes in a minimal Scene. The Area drives a real feature
 * over a real {@link com.asdasfa.jbs2bg.project.ProjectSession} built from an explicit Settings store, with a
 * {@link RecordingPlatform} behind the pipeline's dialogs and a {@link RecordingShellPort} in place of the shell.
 * <p>
 * The fixture plays the shell's part of the Area contract and nothing more: it builds the
 * {@link WorkbenchContext}, renders it into the Area once on load and again on every job frame, and feeds the job
 * frames to the pipeline's gate. Tests assert through the Area's controls and the recorded port requests. The Stage
 * is never shown unless a test asks for it with {@link Hosted#show()}, which is only for focus assertions.
 * <p>
 * Subclasses implement {@link #load(Collaborators)} to build the Area's feature and call the Area's production
 * loader. Every method that touches the hosted Area runs on the JavaFX Application Thread.
 *
 * @param <A> the Area under test
 */
public abstract class AreaTest<A extends WorkbenchArea> {
    /** Fixed clock for the feedback model and for Areas whose behaviour reads time. */
    protected static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T20:00:00Z"), ZoneOffset.UTC);

    @TempDir
    protected Path temporaryDirectory;
    /** Scripted dialogs and native effects; queue responses before the gesture that needs them. */
    protected final RecordingPlatform platform = new RecordingPlatform();
    /** Records every request the Area makes of the shell. */
    protected final RecordingShellPort shell = new RecordingShellPort();
    // Memoized on first use in the test's temporary directory; a test seeding custom Settings assigns it first.
    private WorkbenchTestEnvironment fixture;
    // The Area hosted by this test, kept so later FX bodies of the same test can reach it.
    private volatile Hosted<A> hosted;

    /**
     * Everything the shell would hand an Area's controller factory, for {@link #load(Collaborators)} to pick from.
     *
     * @param environment the Workbench environment built from the fixture's Settings store
     * @param flow        the Project flow over a real session and the hosted job coordinator
     * @param pipeline    the dispatch pipeline whose gate follows the hosted job frames
     * @param platform    the recording platform
     * @param shell       the recording shell port
     * @param clock       the fixed clock
     */
    public record Collaborators(WorkbenchEnvironment environment, WorkbenchProjectFlow flow,
                                DispatchPipeline pipeline, WorkbenchPlatform platform, ShellPort shell, Clock clock) {
    }

    /**
     * Builds the Area's feature and loads the Area through its production loader.
     *
     * @param collaborators the shell-side collaborators
     * @return the loaded Area and its FXML namespace
     */
    protected abstract AreaLoader.Loaded<A> load(Collaborators collaborators);

    /**
     * Supplies the context's live-Settings flag. The default follows the fixture's startup result; an Area that owns
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
     * Hosts the Area over a Project flow with its own default job coordinator.
     *
     * @return the hosted Area
     */
    protected final Hosted<A> host() {
        return host(new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession()));
    }

    /**
     * Hosts the Area over a Project flow driven by the given coordinator, so a test controls the worker and
     * publication lanes. Like the shell, the fixture observes the coordinator; the first job frame is published on
     * the coordinator's publication lane.
     *
     * @param jobs the coordinator the Area's jobs run on
     * @return the hosted Area
     */
    protected final Hosted<A> host(JobCoordinator jobs) {
        return host(new WorkbenchProjectFlow("BS2BG Preview", fixture().newSession(), jobs));
    }

    /** @return the Area this test hosted */
    protected final Hosted<A> hosted() {
        return Objects.requireNonNull(hosted, "no Area is hosted yet");
    }

    /** Loads the Area, mounts its panes, renders the first context, and starts the job-frame fan-out. */
    private Hosted<A> host(WorkbenchProjectFlow flow) {
        if (hosted != null)
            throw new IllegalStateException("This test already hosts its Area");
        Stage stage = new Stage();
        long[] renderedSequence = {flow.frame().sequence()};
        // The chrome re-renders the Hosted that is created only after the pipeline it is passed to.
        AtomicReference<Hosted<A>> self = new AtomicReference<>();
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
            // The shell renders round-trip feedback frames; an Area test asserts the Area, not the shell's InfoBar.
        }, new DispatchPipeline.ProjectChrome() {
            /** @return the Project sequence the fixture last rendered */
            @Override
            public long renderedSequence() {
                return renderedSequence[0];
            }

            /** Records the sequence and re-renders the Area against a fresh context, as the shell's chrome does. */
            @Override
            public void render(long sequence) {
                renderedSequence[0] = sequence;
                self.get().renderContext();
            }
        });
        AreaLoader.Loaded<A> loaded = load(new Collaborators(fixture().environment(), flow, pipeline, platform,
                shell, CLOCK));
        AreaPanes panes = loaded.area().panes();
        for (Node pane : new Node[] {panes.getPrimary(), panes.getEditor(), panes.getInspector()}) {
            // The shell shows only the active Area's panes; the hosted Area is always the active one.
            pane.setManaged(true);
            pane.setVisible(true);
        }
        stage.setScene(new Scene(new HBox(12.0, panes.getPrimary(), panes.getEditor(), panes.getInspector()),
                1000.0, 720.0));
        Hosted<A> created = new Hosted<>(this, loaded, flow, pipeline, stage);
        self.set(created);
        hosted = created;
        created.renderContext();
        flow.jobs().observe(frame -> {
            pipeline.acceptJobFrame(frame);
            created.renderContext();
        });
        return created;
    }

    /**
     * One Area hosted in a minimal Scene.
     *
     * @param <A> the Area under test
     */
    public static final class Hosted<A extends WorkbenchArea> {
        private final AreaTest<A> test;
        private final AreaLoader.Loaded<A> loaded;
        private final WorkbenchProjectFlow flow;
        private final DispatchPipeline pipeline;
        private final Stage stage;
        private WorkbenchContext context;

        /** Wraps one loaded Area and its host. */
        private Hosted(AreaTest<A> test, AreaLoader.Loaded<A> loaded, WorkbenchProjectFlow flow,
                       DispatchPipeline pipeline, Stage stage) {
            this.test = test;
            this.loaded = loaded;
            this.flow = flow;
            this.pipeline = pipeline;
            this.stage = stage;
        }

        /** @return the Area controller */
        public A area() {
            return loaded.area();
        }

        /**
         * Looks up one node or control the Area FXML declares.
         *
         * @param id the {@code fx:id}
         * @param <T> the expected type
         * @return the node
         * @throws AssertionError when the Area FXML declares no such id
         */
        @SuppressWarnings("unchecked")
        public <T> T node(String id) {
            Object value = loaded.namespace().get(id);
            if (value == null)
                throw new AssertionError("The Area FXML declares no fx:id " + id);
            return (T) value;
        }

        /** @return the Area FXML's namespace */
        public Map<String, Object> namespace() {
            return loaded.namespace();
        }

        /** @return the Project flow the Area runs over */
        public WorkbenchProjectFlow flow() {
            return flow;
        }

        /** @return the Stage hosting the panes; not shown unless {@link #show()} was called */
        public Stage stage() {
            return stage;
        }

        /** @return the context the fixture last rendered into the Area */
        public WorkbenchContext context() {
            return context;
        }

        /** Shows the Stage, for tests that assert real focus. */
        public void show() {
            stage.show();
        }

        /** Builds a fresh context from the live sources and renders it into the Area, as the shell's fan-out does. */
        public void renderContext() {
            JobCoordinator.Frame jobs = flow.jobs().frame();
            context = new WorkbenchContext(flow.frame(), false, false, pipeline.mutationsBlocked(),
                    jobs.active() || jobs.shutdownRequested(), test.settingsLiveAvailable(), Optional.empty());
            loaded.area().render(context);
        }
    }
}
