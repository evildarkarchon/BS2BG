package com.asdasfa.jbs2bg.workbench;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.asdasfa.jbs2bg.workbench.output.OutputDrawer;

import javafx.fxml.FXMLLoader;

/**
 * Looks controls up in a whole loaded Workbench: the shell's {@code workbench.fxml} namespace plus the namespace of
 * every Area FXML and the Output drawer FXML the attached shell loaded. Tests that drive cross-Area journeys through
 * the full Workbench use it for controls that moved into a module's own FXML.
 */
public final class LoadedWorkbench {

    /** Static helpers only. */
    private LoadedWorkbench() {
    }

    /**
     * Returns one node or control by {@code fx:id}, from the shell graph or any loaded Area graph.
     *
     * @param loader the loader of {@code workbench.fxml}, whose controller is attached
     * @param id     the {@code fx:id}
     * @return the node, or null when no graph declares it
     */
    public static Object node(FXMLLoader loader, String id) {
        return namespace(loader).get(id);
    }

    /**
     * Returns the controller of every Area FXML the attached shell loaded, in Area order.
     *
     * @param loader the loader of {@code workbench.fxml}, whose controller is attached
     * @return the loaded Area controllers
     */
    public static List<WorkbenchArea> areas(FXMLLoader loader) {
        WorkbenchController controller = loader.getController();
        List<WorkbenchArea> areas = new ArrayList<>();
        for (WorkbenchNavigation.Area area : WorkbenchNavigation.Area.values()) {
            Object module = controller.areaNamespace(area).get(FXMLLoader.CONTROLLER_KEYWORD);
            if (module != null)
                areas.add((WorkbenchArea) module);
        }
        return areas;
    }

    /**
     * Returns the Output drawer controller the attached shell loaded from its own FXML.
     *
     * @param loader the loader of {@code workbench.fxml}, whose controller is attached
     * @return the loaded drawer controller, or null before the shell is attached
     */
    public static OutputDrawer outputDrawer(FXMLLoader loader) {
        WorkbenchController controller = loader.getController();
        return (OutputDrawer) controller.outputDrawerNamespace().get(FXMLLoader.CONTROLLER_KEYWORD);
    }

    /**
     * Merges the shell namespace with every loaded Area namespace and the Output drawer's. Shell entries win a key
     * collision; only FXMLLoader's own keys (such as {@code controller}) and each module root's id ({@code panes},
     * {@code parts}) can collide.
     *
     * @param loader the loader of {@code workbench.fxml}, whose controller is attached
     * @return a new merged map
     */
    public static Map<String, Object> namespace(FXMLLoader loader) {
        Map<String, Object> merged = new LinkedHashMap<>(loader.getNamespace());
        WorkbenchController controller = loader.getController();
        for (WorkbenchNavigation.Area area : WorkbenchNavigation.Area.values())
            controller.areaNamespace(area).forEach(merged::putIfAbsent);
        controller.outputDrawerNamespace().forEach(merged::putIfAbsent);
        return merged;
    }
}
