package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.asdasfa.jbs2bg.testing.JavaFxFreeTypeGraph;

/**
 * Verifies the pure mapping the shell's Project effect loop uses to open its confirmation dialogs and read their
 * answers, without a JavaFX platform.
 */
class ProjectEffectDialogsTest {

    /** Each Project confirmation has its own dialog wording; choosers and the final close open no dialog. */
    @Test
    void confirmationEffectsOpenTheirDialogAndChoosersOpenNone() {
        WorkbenchFeedback.DialogSpec newProject = ProjectEffectDialogs.spec(
                WorkbenchProjectFlow.EffectKind.CONFIRM_NEW).orElseThrow();
        WorkbenchFeedback.DialogSpec openProject = ProjectEffectDialogs.spec(
                WorkbenchProjectFlow.EffectKind.CONFIRM_OPEN).orElseThrow();
        WorkbenchFeedback.DialogSpec closeProject = ProjectEffectDialogs.spec(
                WorkbenchProjectFlow.EffectKind.CONFIRM_CLOSE).orElseThrow();

        assertEquals("Create a new Project?", newProject.title());
        assertEquals(List.of(WorkbenchFeedback.DialogAction.DISCARD, WorkbenchFeedback.DialogAction.CANCEL),
                newProject.actions());
        assertEquals("Open another Project?", openProject.title());
        assertEquals("The current Project has unsaved changes.", openProject.message());
        assertEquals("Save changes before closing?", closeProject.title());
        assertEquals(List.of(WorkbenchFeedback.DialogAction.SAVE, WorkbenchFeedback.DialogAction.DISCARD,
                WorkbenchFeedback.DialogAction.CANCEL), closeProject.actions());
        for (WorkbenchProjectFlow.EffectKind kind : List.of(WorkbenchProjectFlow.EffectKind.CHOOSE_BODYSLIDE_SOURCES,
                WorkbenchProjectFlow.EffectKind.CHOOSE_OPEN_PATH, WorkbenchProjectFlow.EffectKind.CHOOSE_SAVE_PATH,
                WorkbenchProjectFlow.EffectKind.CLOSE_WINDOW))
            assertTrue(ProjectEffectDialogs.spec(kind).isEmpty(), kind.name());
    }

    /** A confirmation response reads back as the dialog action the user chose; a chooser path is not an answer. */
    @Test
    void confirmationResponsesReadBackAsTheChosenDialogAction() {
        assertEquals(WorkbenchFeedback.DialogAction.SAVE,
                ProjectEffectDialogs.answer(WorkbenchProjectFlow.Response.save()));
        assertEquals(WorkbenchFeedback.DialogAction.DISCARD,
                ProjectEffectDialogs.answer(WorkbenchProjectFlow.Response.discard()));
        assertEquals(WorkbenchFeedback.DialogAction.CANCEL,
                ProjectEffectDialogs.answer(WorkbenchProjectFlow.Response.cancelled()));
        assertThrows(IllegalArgumentException.class, () -> ProjectEffectDialogs.answer(
                WorkbenchProjectFlow.Response.selected(Path.of("project.jbs2bg").toAbsolutePath())));
    }

    /** The Project effect loop's dialog mapping is pure code with no JavaFX type behind it. */
    @Test
    void dialogMappingReachesNoJavaFxType() {
        JavaFxFreeTypeGraph.assertJavaFxFree(ProjectEffectDialogs.class);
    }
}
