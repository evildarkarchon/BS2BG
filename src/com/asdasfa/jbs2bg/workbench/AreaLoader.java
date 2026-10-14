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
 * FXML can also be loaded alone by its {@code AreaTest}. The Output drawer, which is not an Area, loads through the
 * same mechanism with its own non-Node root ({@link #loadGraph}). Must be called on the JavaFX Application Thread.
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
     * One loaded module graph: its constructor-injected controller, its non-Node root holder, and its namespace.
     *
     * @param controller the module controller, with every {@code @FXML} field injected
     * @param root       the FXML root, of the root type the caller required
     * @param namespace  the FXML namespace (every {@code fx:id}), read-only
     * @param <C>        the controller type
     * @param <R>        the root holder type
     */
    public record Graph<C, R>(C controller, R root, Map<String, Object> namespace) {
        /** Requires every value. */
        public Graph {
            Objects.requireNonNull(controller, "controller");
            Objects.requireNonNull(root, "root");
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
        Graph<A, AreaPanes> graph = loadGraph(type, resource, controller, AreaPanes.class);
        if (graph.controller().panes() != graph.root())
            throw new IllegalStateException(type.getSimpleName() + ".panes() must return its FXML root");
        return new Loaded<>(graph.controller(), graph.namespace());
    }

    /**
     * Loads one module FXML whose root is a non-Node holder of the given type, through the same constructor-injecting
     * controller factory {@link #load} uses for Areas. The caller checks its own root contract.
     *
     * @param type       the controller class; the FXML resource is resolved relative to it
     * @param resource   the FXML resource name, beside the controller class
     * @param controller builds the controller with its injected collaborators; called once
     * @param rootType   the holder type the FXML root must have
     * @param <C>        the controller type
     * @param <R>        the root holder type
     * @return the loaded controller, root and namespace
     * @throws IllegalStateException when the FXML is missing, names another controller, declares none, or has a root
     *                               of another type
     * @throws UncheckedIOException  when FXMLLoader fails to read or build the graph
     */
    public static <C, R> Graph<C, R> loadGraph(Class<C> type, String resource, Supplier<? extends C> controller,
                                               Class<R> rootType) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(rootType, "rootType");
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
        if (!rootType.isInstance(root))
            throw new IllegalStateException(resource + " root must be a " + rootType.getSimpleName() + " holder");
        C loaded = type.cast(loader.getController());
        if (loaded == null)
            throw new IllegalStateException(resource + " declares no fx:controller");
        // FXMLLoader's namespace is a live, mutable map; the copy keeps lookups stable after loading.
        return new Graph<>(loaded, rootType.cast(root),
                Collections.unmodifiableMap(new LinkedHashMap<>(loader.getNamespace())));
    }
}
