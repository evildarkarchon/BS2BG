package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.testing.JavaFxFreeTypeGraph;

/**
 * Verifies the immutable input the shell fans out to every Area on each Project frame and each job frame, without a
 * JavaFX platform.
 */
class WorkbenchContextTest {

    @TempDir
    Path temporaryDirectory;

    /** Output resets its generated text whenever the Project frame resets either Templates or Morphs. */
    @Test
    void outputResetsWhenEitherTemplatesOrMorphsReset() {
        WorkbenchProjectFlow.Frame project = projectFrame();

        assertFalse(context(project, false, false).resetOutput());
        assertTrue(context(project, true, false).resetOutput());
        assertTrue(context(project, false, true).resetOutput());
        assertTrue(context(project, true, true).resetOutput());
    }

    /** A context always names its Project frame and its active-operation slot. */
    @Test
    void contextRequiresAProjectFrameAndAnOperationSlot() {
        WorkbenchProjectFlow.Frame project = projectFrame();

        assertThrows(NullPointerException.class, () -> new WorkbenchContext(null, false, false, false, false, true,
                Optional.empty()));
        assertThrows(NullPointerException.class, () -> new WorkbenchContext(project, false, false, false, false, true,
                null));
    }

    /** The Area-facing context is pure data with no JavaFX type behind it. */
    @Test
    void contextReachesNoJavaFxType() {
        JavaFxFreeTypeGraph.assertJavaFxFree(WorkbenchContext.class);
    }

    /** Builds one context that differs only in its reset-on-Open flags. */
    private static WorkbenchContext context(WorkbenchProjectFlow.Frame project, boolean resetTemplates,
                                            boolean resetMorphs) {
        return new WorkbenchContext(project, resetTemplates, resetMorphs, false, false, true, Optional.empty());
    }

    /** @return the first Project frame of a fresh untitled Project */
    private WorkbenchProjectFlow.Frame projectFrame() {
        return new WorkbenchProjectFlow("BS2BG Preview",
                WorkbenchTestEnvironment.initialized(temporaryDirectory).newSession()).frame();
    }
}
