package com.asdasfa.jbs2bg.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkbenchNavigationTest {

    /**
     * A typed Area destination changes the active Area and requests its first semantic content target.
     */
    @Test
    void typedAreaNavigationEntersTheDestinationPredictably() {
        WorkbenchNavigation navigation = new WorkbenchNavigation();

        WorkbenchNavigation.Transition transition = navigation.navigate(
                WorkbenchNavigation.Destination.MORPHS,
                new WorkbenchNavigation.FocusTarget(
                        WorkbenchNavigation.Area.TEMPLATES,
                        WorkbenchNavigation.Landmark.RAIL));

        assertEquals(WorkbenchNavigation.Area.MORPHS, transition.frame().activeArea());
        assertFalse(transition.frame().outputDrawerVisible());
        assertEquals(new WorkbenchNavigation.FocusTarget(
                WorkbenchNavigation.Area.MORPHS,
                WorkbenchNavigation.Landmark.PRIMARY_CONTENT), transition.focusTarget().orElseThrow());
    }

    /**
     * User-invoked Output toggles the drawer without replacing the active Area and restores prior focus.
     */
    @Test
    void outputDestinationPreservesTheAreaAndRestoresItsLauncherFocus() {
        WorkbenchNavigation navigation = new WorkbenchNavigation();
        WorkbenchNavigation.FocusTarget editor = new WorkbenchNavigation.FocusTarget(
                WorkbenchNavigation.Area.TEMPLATES,
                WorkbenchNavigation.Landmark.EDITOR);

        WorkbenchNavigation.Transition opened = navigation.navigate(
                WorkbenchNavigation.Destination.OUTPUT, editor);

        assertEquals(WorkbenchNavigation.Area.TEMPLATES, opened.frame().activeArea());
        assertTrue(opened.frame().outputDrawerVisible());
        assertEquals(new WorkbenchNavigation.FocusTarget(
                WorkbenchNavigation.Area.TEMPLATES,
                WorkbenchNavigation.Landmark.OUTPUT), opened.focusTarget().orElseThrow());

        WorkbenchNavigation.Transition closed = navigation.navigate(
                WorkbenchNavigation.Destination.OUTPUT,
                opened.focusTarget().orElseThrow());

        assertEquals(WorkbenchNavigation.Area.TEMPLATES, closed.frame().activeArea());
        assertFalse(closed.frame().outputDrawerVisible());
        assertEquals(editor, closed.focusTarget().orElseThrow());
    }

    /**
     * Rail-opened Output restores the distinct drawer launcher rather than the active Area's rail button.
     */
    @Test
    void railOpenedOutputRestoresTheOutputLauncher() {
        WorkbenchNavigation navigation = new WorkbenchNavigation();
        WorkbenchNavigation.FocusTarget outputLauncher = new WorkbenchNavigation.FocusTarget(
                WorkbenchNavigation.Area.TEMPLATES,
                WorkbenchNavigation.Landmark.OUTPUT_LAUNCHER);

        navigation.navigate(WorkbenchNavigation.Destination.OUTPUT, outputLauncher);
        WorkbenchNavigation.Transition closed = navigation.dismiss();

        assertEquals(outputLauncher, closed.focusTarget().orElseThrow());
    }

    /**
     * Automatic completion reveal makes Output visible without changing Area or stealing keyboard focus.
     */
    @Test
    void automaticOutputRevealHasNoFocusEffect() {
        WorkbenchNavigation navigation = new WorkbenchNavigation();
        navigation.navigate(WorkbenchNavigation.Destination.MORPHS,
                new WorkbenchNavigation.FocusTarget(
                        WorkbenchNavigation.Area.TEMPLATES,
                        WorkbenchNavigation.Landmark.RAIL));

        WorkbenchNavigation.Transition revealed = navigation.revealOutput();

        assertEquals(WorkbenchNavigation.Area.MORPHS, revealed.frame().activeArea());
        assertTrue(revealed.frame().outputDrawerVisible());
        assertTrue(revealed.focusTarget().isEmpty());
    }

    /** Closing automatically revealed Output leaves the still-focused control untouched. */
    @Test
    void dismissingAutomaticOutputRevealHasNoFocusEffect() {
        WorkbenchNavigation navigation = new WorkbenchNavigation();
        navigation.revealOutput();

        WorkbenchNavigation.Transition dismissed = navigation.dismiss();

        assertFalse(dismissed.frame().outputDrawerVisible());
        assertTrue(dismissed.focusTarget().isEmpty());
    }

    /**
     * F6 follows semantic landmark order through durable Activity and loops across the wide Workbench.
     */
    @Test
    void f6CyclesTheOpenWideWorkbenchLandmarksCoherently() {
        WorkbenchNavigation navigation = new WorkbenchNavigation();
        WorkbenchNavigation.FocusTarget current = new WorkbenchNavigation.FocusTarget(
                WorkbenchNavigation.Area.TEMPLATES,
                WorkbenchNavigation.Landmark.RAIL);
        navigation.navigate(WorkbenchNavigation.Destination.OUTPUT, current);

        for (WorkbenchNavigation.Landmark expected : new WorkbenchNavigation.Landmark[]{
                WorkbenchNavigation.Landmark.PRIMARY_CONTENT,
                WorkbenchNavigation.Landmark.EDITOR,
                WorkbenchNavigation.Landmark.INSPECTOR,
                WorkbenchNavigation.Landmark.OUTPUT,
                WorkbenchNavigation.Landmark.ACTIVITY,
                WorkbenchNavigation.Landmark.STATUS,
                WorkbenchNavigation.Landmark.RAIL}) {
            current = navigation.cycleFocus(current).focusTarget().orElseThrow();
            assertEquals(expected, current.landmark());
            assertEquals(WorkbenchNavigation.Area.TEMPLATES, current.area());
        }
    }

    /**
     * Below 1200 logical pixels side content becomes dismissible overlays over the still-inline editor.
     */
    @Test
    void narrowModeUsesSideOverlaysAndReturnsFocusToTheirSemanticLauncher() {
        WorkbenchNavigation navigation = new WorkbenchNavigation();
        WorkbenchNavigation.FocusTarget inspector = new WorkbenchNavigation.FocusTarget(
                WorkbenchNavigation.Area.TEMPLATES,
                WorkbenchNavigation.Landmark.INSPECTOR);

        WorkbenchNavigation.Transition narrowed = navigation.resize(1199, inspector);

        assertTrue(narrowed.frame().narrowMode());
        assertEquals(WorkbenchNavigation.Overlay.NONE, narrowed.frame().overlay());
        assertEquals(WorkbenchNavigation.Landmark.EDITOR, narrowed.focusTarget().orElseThrow().landmark());

        WorkbenchNavigation.FocusTarget editor = narrowed.focusTarget().orElseThrow();
        WorkbenchNavigation.Transition opened = navigation.openInspector(editor);
        assertEquals(WorkbenchNavigation.Overlay.INSPECTOR, opened.frame().overlay());
        assertEquals(WorkbenchNavigation.Landmark.INSPECTOR, opened.focusTarget().orElseThrow().landmark());

        WorkbenchNavigation.Transition dismissed = navigation.dismiss();
        assertEquals(WorkbenchNavigation.Overlay.NONE, dismissed.frame().overlay());
        assertEquals(editor, dismissed.focusTarget().orElseThrow());

        WorkbenchNavigation.Transition widened = navigation.resize(1200, editor);
        assertFalse(widened.frame().narrowMode());
        assertTrue(widened.focusTarget().isEmpty());
    }

    /**
     * Narrow Area navigation opens primary content as the predictable entry surface.
     */
    @Test
    void narrowAreaNavigationOpensPrimaryContentFromItsRailLauncher() {
        WorkbenchNavigation navigation = new WorkbenchNavigation();
        WorkbenchNavigation.FocusTarget editor = new WorkbenchNavigation.FocusTarget(
                WorkbenchNavigation.Area.TEMPLATES,
                WorkbenchNavigation.Landmark.EDITOR);
        navigation.resize(1199, editor);

        WorkbenchNavigation.Transition entered = navigation.navigate(
                WorkbenchNavigation.Destination.NPC_DATABASE, editor);

        assertEquals(WorkbenchNavigation.Area.NPC_DATABASE, entered.frame().activeArea());
        assertEquals(WorkbenchNavigation.Overlay.PRIMARY_CONTENT, entered.frame().overlay());
        assertEquals(WorkbenchNavigation.Landmark.PRIMARY_CONTENT,
                entered.focusTarget().orElseThrow().landmark());

        WorkbenchNavigation.Transition dismissed = navigation.dismiss();
        assertEquals(new WorkbenchNavigation.FocusTarget(
                WorkbenchNavigation.Area.NPC_DATABASE,
                WorkbenchNavigation.Landmark.RAIL), dismissed.focusTarget().orElseThrow());
    }
}
