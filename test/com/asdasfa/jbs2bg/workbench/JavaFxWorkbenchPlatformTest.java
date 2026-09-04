package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaFxWorkbenchPlatformTest {

    @TempDir
    Path temporaryDirectory;

    /** Save chooser selections without the canonical extension gain it before entering the Project flow. */
    @Test
    void saveChooserAppendsProjectExtensionWhenAbsent() {
        Path selected = temporaryDirectory.resolve("project");

        WorkbenchProjectFlow.Response response = JavaFxWorkbenchPlatform.projectChooserResponse(
                selected.toFile(), true);

        assertEquals(temporaryDirectory.resolve("project.jbs2bg"), response.selectedPath());
    }

    /** Save chooser selections retain an existing canonical extension regardless of its case. */
    @Test
    void saveChooserPreservesCaseInsensitiveProjectExtension() {
        Path selected = temporaryDirectory.resolve("project.JBS2BG");

        WorkbenchProjectFlow.Response response = JavaFxWorkbenchPlatform.projectChooserResponse(
                selected.toFile(), true);

        assertEquals(selected, response.selectedPath());
    }

    /** Open chooser selections remain exact because extension completion belongs only to Save As. */
    @Test
    void openChooserDoesNotAppendProjectExtension() {
        Path selected = temporaryDirectory.resolve("extensionless-project");

        WorkbenchProjectFlow.Response response = JavaFxWorkbenchPlatform.projectChooserResponse(
                selected.toFile(), false);

        assertEquals(selected, response.selectedPath());
    }

    /** A dismissed native chooser remains an ordinary cancelled Project-flow response. */
    @Test
    void cancelledChooserRemainsCancelled() {
        WorkbenchProjectFlow.Response response = JavaFxWorkbenchPlatform.projectChooserResponse(null, true);

        assertEquals(WorkbenchProjectFlow.ResponseKind.CANCELLED, response.kind());
    }
}
