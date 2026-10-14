package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import javafx.stage.Stage;

/**
 * Test adapter for modal platform effects; responses are consumed in user-interaction order. Shared by the whole
 * Workbench tests and the {@link ModuleTest} fixture, so its scripting methods are public.
 */
public final class RecordingPlatform implements WorkbenchPlatform {
    private final Deque<WorkbenchProjectFlow.Response> responses = new ArrayDeque<>();
    private final Deque<WorkbenchFeedback.DialogAction> confirmationResponses = new ArrayDeque<>();
    private final Deque<WorkbenchFeedback.DialogAction> failureResponses = new ArrayDeque<>();
    private final Deque<Runnable> failureHooks = new ArrayDeque<>();
    private final Deque<Optional<Path>> outputDirectoryResponses = new ArrayDeque<>();
    private final Deque<Optional<Path>> outputFileResponses = new ArrayDeque<>();
    private final Deque<Optional<List<Path>>> npcSourceResponses = new ArrayDeque<>();
    // The recorded effects below are read directly by same-package Workbench test assertions.
    final List<String> clipboardTexts = new java.util.ArrayList<>();
    WorkbenchFeedback.DialogSpec failureSpec;
    int closeCount;

    /**
     * Adds the next chooser or confirmation result.
     */
    public void respondWith(WorkbenchProjectFlow.Response response) {
        responses.addLast(response);
    }

    /**
     * Adds the next destructive feature confirmation action.
     */
    public void respondConfirmationWith(WorkbenchFeedback.DialogAction action) {
        confirmationResponses.addLast(action);
    }

    /** Adds the next failure-dialog action and a hook run immediately before that response is returned. */
    public void respondFailureWith(WorkbenchFeedback.DialogAction action, Runnable beforeResponse) {
        failureResponses.addLast(action);
        failureHooks.addLast(beforeResponse);
    }

    /** Adds the next Output directory-chooser result. */
    public void respondOutputDirectoryWith(Optional<Path> response) {
        outputDirectoryResponses.addLast(response);
    }

    /** Adds the next selected-BoS save-chooser result. */
    public void respondOutputFileWith(Optional<Path> response) {
        outputFileResponses.addLast(response);
    }

    /** Adds the next NPC Database multi-file chooser result. */
    public void respondNpcSourcesWith(Optional<List<Path>> response) {
        npcSourceResponses.addLast(response);
    }

    /** Returns the next scripted NPC Database source selection. */
    @Override
    public Optional<List<Path>> chooseNpcSources(Stage owner) {
        return npcSourceResponses.removeFirst();
    }

    /**
     * Returns the next scripted real-platform result.
     */
    @Override
    public WorkbenchProjectFlow.Response complete(WorkbenchProjectFlow.Effect effect, Stage owner) {
        return responses.removeFirst();
    }

    /**
     * Returns the next scripted destructive feature confirmation.
     */
    @Override
    public WorkbenchFeedback.DialogAction completeConfirmation(WorkbenchFeedback.DialogSpec spec, Stage owner) {
        return confirmationResponses.removeFirst();
    }

    /** Records the offered failure actions and runs the scripted hook before returning a response. */
    @Override
    public WorkbenchFeedback.DialogAction completeFailure(WorkbenchFeedback.DialogSpec spec, Stage owner) {
        failureSpec = spec;
        if (failureResponses.isEmpty())
            return WorkbenchFeedback.DialogAction.CLOSE;
        failureHooks.removeFirst().run();
        return failureResponses.removeFirst();
    }

    /** Records accepted clipboard text without consulting a JavaFX TextArea. */
    @Override
    public boolean copyOutputText(String text) {
        clipboardTexts.add(text);
        return true;
    }

    /**
     * Lists the clipboard copies for module tests outside this package; same-package tests read the field.
     *
     * @return every copied text so far, oldest first
     */
    public List<String> clipboardTexts() {
        return List.copyOf(clipboardTexts);
    }

    /** Returns the next scripted complete-batch directory. */
    @Override
    public Optional<Path> chooseOutputDirectory(Stage owner) {
        return outputDirectoryResponses.removeFirst();
    }

    /** Returns the next scripted selected-BoS destination. */
    @Override
    public Optional<Path> chooseOutputFile(String suggestedFileName, Stage owner) {
        return outputFileResponses.removeFirst();
    }

    /**
     * Records the at-most-once final close effect without closing the test Stage.
     */
    @Override
    public void closeWindow(Stage owner) {
        closeCount++;
    }
}
