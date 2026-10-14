package com.asdasfa.jbs2bg.workbench;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import javafx.fxml.FXMLLoader;

/**
 * Loads one Area's FXML in code, through a controller factory that hands FXMLLoader the Area controller the caller
 * built with constructor injection. The shell loads every Area this way instead of {@code fx:include}, so each Area
 * FXML can also be loaded alone by its {@code AreaTest}. Must be called on the JavaFX Application Thread.
 */
public final class AreaLoader {

    /** Static loader only. */
    private AreaLoader() {
    }

    /**
     * One loaded Area.
     *
     * @param area      the Area controller, with every {@code @FXML} field injected
     * @param namespace the FXML namespace (every {@code fx:id}), read-only; the shell and tests look controls up in it
     */
    public record Loaded<A extends WorkbenchArea>(A area, Map<String, Object> namespace) {
        /** Requires both values. */
        public Loaded {
            Objects.requireNonNull(area, "area");
            Objects.requireNonNull(namespace, "namespace");
        }
    }

    /**
     * Loads one Area FXML. The FXML's {@code fx:controller} must name {@code type}; the factory supplies the one
     * controller instance, so the Area's injected collaborators can be final constructor fields. The FXML root must be
     * an {@link AreaPanes} holder, and the Area must report that same holder as its {@link WorkbenchArea#panes()}.
     *
     * @param type       the Area controller class; the FXML resource is resolved relative to it
     * @param resource   the FXML resource name, beside the controller class
     * @param controller builds the controller with its injected collaborators; called once
     * @param <A>        the Area type
     * @return the loaded Area and its namespace
     * @throws IllegalStateException when the FXML is missing, names another controller, or breaks the pane contract
     * @throws UncheckedIOException  when FXMLLoader fails to read or build the graph
     */
    public static <A extends WorkbenchArea> Loaded<A> load(Class<A> type, String resource,
                                                           Supplier<? extends A> controller) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(controller, "controller");
        URL location = type.getResource(Objects.requireNonNull(resource, "resource"));
        if (location == null)
            throw new IllegalStateException(type.getSimpleName() + " has no FXML resource " + resource);
        FXMLLoader loader = new FXMLLoader(location);
        loader.setControllerFactory(requested -> {
            if (requested != type)
                throw new IllegalStateException(resource + " declares controller " + requested.getName()
                        + ", expected " + type.getName());
            return Objects.requireNonNull(controller.get(), "controller");
        });
        Object root;
        try {
            root = loader.load();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not load " + resource, exception);
        }
        if (!(root instanceof AreaPanes panes))
            throw new IllegalStateException(resource + " root must be an AreaPanes holder");
        A area = type.cast(loader.getController());
        if (area == null)
            throw new IllegalStateException(resource + " declares no fx:controller");
        if (area.panes() != panes)
            throw new IllegalStateException(type.getSimpleName() + ".panes() must return its FXML root");
        // FXMLLoader's namespace is a live, mutable map; the copy keeps lookups stable after loading.
        return new Loaded<>(area, Collections.unmodifiableMap(new LinkedHashMap<>(loader.getNamespace())));
    }
}
