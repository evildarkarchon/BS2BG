package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.project.ProjectDiagnosticCodes;
import com.asdasfa.jbs2bg.project.ProjectLifecycleStatus;
import com.asdasfa.jbs2bg.project.ProjectSessions;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;

class WorkbenchProjectFlowTest {

    @TempDir
    Path temporaryDirectory;

    /** Save As adopts a normalized Project identity only after ProjectSession publishes a successful save. */
    @Test
    void saveAsAdoptsNormalizedIdentityAndLeavesProjectClean() {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        WorkbenchProjectFlow.Update requested = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS);
        WorkbenchProjectFlow.Effect chooser = requested.effect().orElseThrow();
        WorkbenchProjectFlow.Update saved = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selected(temporaryDirectory.resolve("saved-project")));

        Path expectedIdentity = temporaryDirectory.resolve("saved-project.jbs2bg").toAbsolutePath().normalize();
        assertTrue(saved.accepted());
        assertTrue(Files.isRegularFile(expectedIdentity));
        assertEquals(expectedIdentity, saved.frame().snapshot().getFileIdentity().orElseThrow());
        assertEquals(ProjectLifecycleStatus.FILE_BACKED, saved.frame().snapshot().getLifecycleStatus());
        assertFalse(saved.frame().snapshot().isDirty());
        assertEquals("BS2BG Preview - saved-project.jbs2bg", saved.frame().title());
    }

    /** A recovered Open publishes its adopted identity, dirty state, and ordered structured diagnostics together. */
    @Test
    void recoveredOpenPublishesOneCoherentDirtyFrame() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());

        WorkbenchProjectFlow.Update requested = flow.request(WorkbenchProjectFlow.Intent.OPEN);
        WorkbenchProjectFlow.Effect chooser = requested.effect().orElseThrow();
        WorkbenchProjectFlow.Update opened = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selected(source));

        assertTrue(opened.accepted());
        assertEquals(source.toAbsolutePath().normalize(), opened.frame().snapshot().getFileIdentity().orElseThrow());
        assertEquals(ProjectLifecycleStatus.RECOVERED, opened.frame().snapshot().getLifecycleStatus());
        assertTrue(opened.frame().snapshot().isDirty());
        assertEquals(2, opened.frame().diagnostics().size());
        assertTrue(opened.frame().diagnostics().stream()
                .allMatch(diagnostic -> ProjectDiagnosticCodes.SLIDER_PRESET_ASSIGNMENT_MISSING
                        .equals(diagnostic.getCode())));
        assertEquals("BS2BG Preview - *recovery-source.jbs2bg", opened.frame().title());
    }

    /** New keeps a dirty Project intact until the user explicitly confirms its replacement. */
    @Test
    void dirtyNewCanBeCancelledOrExplicitlyDiscarded() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame dirty = flow.respond(openChooser.token(),
                WorkbenchProjectFlow.Response.selected(source)).frame();

        WorkbenchProjectFlow.Effect firstConfirmation = flow.request(WorkbenchProjectFlow.Intent.NEW)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update cancelled = flow.respond(firstConfirmation.token(),
                WorkbenchProjectFlow.Response.cancelled());

        assertTrue(cancelled.accepted());
        assertEquals(dirty, cancelled.frame());
        assertTrue(cancelled.effect().isEmpty());

        WorkbenchProjectFlow.Effect secondConfirmation = flow.request(WorkbenchProjectFlow.Intent.NEW)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update replaced = flow.respond(secondConfirmation.token(),
                WorkbenchProjectFlow.Response.discard());

        assertTrue(replaced.accepted());
        assertEquals(ProjectLifecycleStatus.UNTITLED, replaced.frame().snapshot().getLifecycleStatus());
        assertTrue(replaced.frame().snapshot().getFileIdentity().isEmpty());
        assertFalse(replaced.frame().snapshot().isDirty());
        assertTrue(replaced.frame().diagnostics().isEmpty());
        assertEquals("BS2BG Preview", replaced.frame().title());
    }

    /** Save uses the adopted identity and clears recovery dirtiness without asking for another path. */
    @Test
    void saveUsesTheAdoptedIdentityOfARecoveredProject() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        assertTrue(flow.respond(openChooser.token(), WorkbenchProjectFlow.Response.selected(source))
                .frame().snapshot().isDirty());

        WorkbenchProjectFlow.Update saved = flow.request(WorkbenchProjectFlow.Intent.SAVE);

        assertTrue(saved.accepted());
        assertTrue(saved.effect().isEmpty());
        assertEquals(source.toAbsolutePath().normalize(), saved.frame().snapshot().getFileIdentity().orElseThrow());
        assertEquals(ProjectLifecycleStatus.FILE_BACKED, saved.frame().snapshot().getLifecycleStatus());
        assertFalse(saved.frame().snapshot().isDirty());
        assertTrue(saved.frame().diagnostics().isEmpty());
        assertEquals("BS2BG Preview - recovery-source.jbs2bg", saved.frame().title());
    }

    /** Dirty shutdown preserves the Project on Cancel and closes at most once after explicit Discard. */
    @Test
    void dirtyCloseCanBeCancelledBeforeDiscardClosesExactlyOnce() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame dirty = flow.respond(openChooser.token(),
                WorkbenchProjectFlow.Response.selected(source)).frame();

        WorkbenchProjectFlow.Effect firstConfirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update cancelled = flow.respond(firstConfirmation.token(),
                WorkbenchProjectFlow.Response.cancelled());

        assertTrue(cancelled.accepted());
        assertEquals(dirty, cancelled.frame());
        assertFalse(cancelled.frame().closed());

        WorkbenchProjectFlow.Effect secondConfirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update discarded = flow.respond(secondConfirmation.token(),
                WorkbenchProjectFlow.Response.discard());

        assertTrue(discarded.accepted());
        assertTrue(discarded.frame().closed());
        assertEquals(WorkbenchProjectFlow.EffectKind.CLOSE_WINDOW,
                discarded.effect().orElseThrow().kind());

        WorkbenchProjectFlow.Update repeated = flow.request(WorkbenchProjectFlow.Intent.CLOSE);
        assertFalse(repeated.accepted());
        assertTrue(repeated.effect().isEmpty());
    }

    /** Dirty shutdown can save through the adopted identity and closes only after the clean outcome publishes. */
    @Test
    void dirtyCloseSavesBeforePublishingTheCloseEffect() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path source = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        assertTrue(flow.respond(openChooser.token(), WorkbenchProjectFlow.Response.selected(source))
                .frame().snapshot().isDirty());

        WorkbenchProjectFlow.Effect confirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update saved = flow.respond(confirmation.token(),
                WorkbenchProjectFlow.Response.save());

        assertTrue(saved.accepted());
        assertFalse(saved.frame().snapshot().isDirty());
        assertEquals(ProjectLifecycleStatus.FILE_BACKED, saved.frame().snapshot().getLifecycleStatus());
        assertTrue(saved.frame().closed());
        assertEquals(WorkbenchProjectFlow.EffectKind.CLOSE_WINDOW, saved.effect().orElseThrow().kind());
    }

    /** A failed close-save keeps the window and exact dirty Project active with its failure diagnostics. */
    @Test
    void failedCloseSavePreservesUnsavedWorkAndDoesNotClose() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path parent = Files.createDirectory(temporaryDirectory.resolve("adopted-parent"));
        Path source = parent.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                source);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect openChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame dirty = flow.respond(openChooser.token(),
                WorkbenchProjectFlow.Response.selected(source)).frame();
        Files.delete(source);
        Files.delete(parent);

        WorkbenchProjectFlow.Effect confirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update failed = flow.respond(confirmation.token(),
                WorkbenchProjectFlow.Response.save());

        assertTrue(failed.accepted());
        assertFalse(failed.frame().closed());
        assertTrue(failed.frame().snapshot().isDirty());
        assertEquals(dirty.snapshot(), failed.frame().snapshot());
        assertEquals(dirty.title(), failed.frame().title());
        assertEquals(ProjectDiagnosticCodes.PROJECT_FILE_WRITE_FAILED,
                failed.frame().diagnostics().get(0).getCode());
        assertTrue(failed.effect().isEmpty());
    }

    /** A stale chooser callback cannot consume a newer effect or invoke a Project operation. */
    @Test
    void staleEffectResponseIsRejectedWithoutConsumingTheCurrentEffect() {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect stale = flow.request(WorkbenchProjectFlow.Intent.OPEN).effect().orElseThrow();
        assertTrue(flow.respond(stale.token(), WorkbenchProjectFlow.Response.cancelled()).accepted());
        WorkbenchProjectFlow.Effect current = flow.request(WorkbenchProjectFlow.Intent.SAVE_AS)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame beforeLateResponse = flow.frame();

        WorkbenchProjectFlow.Update rejected = flow.respond(stale.token(),
                WorkbenchProjectFlow.Response.selected(temporaryDirectory.resolve("stale-target")));

        assertFalse(rejected.accepted());
        assertEquals(beforeLateResponse, rejected.frame());
        assertFalse(Files.exists(temporaryDirectory.resolve("stale-target.jbs2bg")));

        WorkbenchProjectFlow.Update saved = flow.respond(current.token(),
                WorkbenchProjectFlow.Response.selected(temporaryDirectory.resolve("current-target")));
        assertTrue(saved.accepted());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("current-target.jbs2bg")));
    }

    /** Open replaces a dirty Project only after confirmation and then asks for the source path. */
    @Test
    void dirtyOpenConfirmsBeforeLaunchingTheChooser() throws Exception {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        Path recovery = temporaryDirectory.resolve("recovery-source.jbs2bg");
        Files.copy(Path.of("test-resources", "json-oracles", "project", "recovery-ordered-diagnostics.jbs2bg"),
                recovery);
        Path replacement = temporaryDirectory.resolve("replacement.jbs2bg");
        Files.copy(Path.of("test-resources", "projects", "legacy-project-semantics.jbs2bg"), replacement);
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        WorkbenchProjectFlow.Effect firstChooser = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Frame dirty = flow.respond(firstChooser.token(),
                WorkbenchProjectFlow.Response.selected(recovery)).frame();

        WorkbenchProjectFlow.Effect confirmation = flow.request(WorkbenchProjectFlow.Intent.OPEN)
                .effect().orElseThrow();
        assertEquals(WorkbenchProjectFlow.EffectKind.CONFIRM_OPEN, confirmation.kind());
        WorkbenchProjectFlow.Update confirmed = flow.respond(confirmation.token(),
                WorkbenchProjectFlow.Response.discard());

        assertTrue(confirmed.accepted());
        assertEquals(dirty, confirmed.frame());
        WorkbenchProjectFlow.Effect replacementChooser = confirmed.effect().orElseThrow();
        assertEquals(WorkbenchProjectFlow.EffectKind.CHOOSE_OPEN_PATH, replacementChooser.kind());

        WorkbenchProjectFlow.Update opened = flow.respond(replacementChooser.token(),
                WorkbenchProjectFlow.Response.selected(replacement));
        assertFalse(opened.frame().snapshot().isDirty());
        assertEquals(replacement.toAbsolutePath().normalize(),
                opened.frame().snapshot().getFileIdentity().orElseThrow());
    }

    /** Saving an untitled dirty Project during shutdown continues through Save As before the final close effect. */
    @Test
    void dirtyUntitledCloseChoosesAPathBeforeClosing() {
        assertTrue(Settings.initialize(temporaryDirectory).isSuccessful());
        WorkbenchProjectFlow flow = new WorkbenchProjectFlow("BS2BG Preview", ProjectSessions.create());
        flow.apply(SliderPresetEdits.create("Unsaved"));
        assertTrue(flow.frame().snapshot().isDirty());
        assertTrue(flow.frame().snapshot().getFileIdentity().isEmpty());

        WorkbenchProjectFlow.Effect confirmation = flow.request(WorkbenchProjectFlow.Intent.CLOSE)
                .effect().orElseThrow();
        WorkbenchProjectFlow.Update saveRequested = flow.respond(confirmation.token(),
                WorkbenchProjectFlow.Response.save());

        assertFalse(saveRequested.frame().closed());
        WorkbenchProjectFlow.Effect chooser = saveRequested.effect().orElseThrow();
        assertEquals(WorkbenchProjectFlow.EffectKind.CHOOSE_SAVE_PATH, chooser.kind());

        WorkbenchProjectFlow.Update saved = flow.respond(chooser.token(),
                WorkbenchProjectFlow.Response.selected(temporaryDirectory.resolve("saved-on-close")));
        assertFalse(saved.frame().snapshot().isDirty());
        assertTrue(saved.frame().closed());
        assertEquals(WorkbenchProjectFlow.EffectKind.CLOSE_WINDOW, saved.effect().orElseThrow().kind());
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("saved-on-close.jbs2bg")));
    }

}
