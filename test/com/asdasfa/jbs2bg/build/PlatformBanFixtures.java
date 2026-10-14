package com.asdasfa.jbs2bg.build;

import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * Planted violations of the production platform bans, compiled into test classes so
 * {@link ProductionPlatformBanGateTest} can prove that each rule fails on real javac output. Nothing here is ever
 * invoked; only the emitted class files are read.
 */
final class PlatformBanFixtures {

    private PlatformBanFixtures() {
    }

    /** Rule 1: reads window activation through a JavaFX node, window, and property. */
    static final class ReadsWindowActivation {
        /** Direct {@code Node.isFocused()} read. */
        boolean nodeFocus(Node node) {
            return node.isFocused();
        }

        /** {@code Window.focusedProperty()} read. */
        ReadOnlyBooleanProperty windowFocus(Window window) {
            return window.focusedProperty();
        }
    }

    /**
     * Rule 1 through inheritance: javac names this subclass, not {@code javafx/scene/Node}, as the call owner, so
     * the gate must resolve the hierarchy rather than match the owner's package.
     */
    static final class InheritedFocusRead extends Region {
        /** Inherited {@code isFocused()} call whose owner is this production-style subclass. */
        boolean ownFocus() {
            return isFocused();
        }
    }

    /** Rule 2: activates windows directly and through a method reference. */
    static final class ActivatesWindow {
        /** {@code Stage.toFront()} and {@code Stage.requestFocus()} calls. */
        void raise(Stage stage) {
            stage.toFront();
            stage.requestFocus();
        }

        /** A {@code PopupWindow} is a {@code javafx/stage} type too. */
        void focusPopup(Popup popup) {
            popup.requestFocus();
        }

        /** A bound method reference to window activation. */
        Runnable deferredRaise(Stage stage) {
            return stage::toFront;
        }
    }

    /** Rule 3: reads the live OS appearance preferences directly and through a method reference. */
    static final class ReadsAppearancePreferences {
        /** Direct {@code Platform.getPreferences()} read. */
        Platform.Preferences readNow() {
            return Platform.getPreferences();
        }

        /** A method reference to the same read. */
        Supplier<Platform.Preferences> readLater() {
            return Platform::getPreferences;
        }
    }

    /** Rule 4: defers focus with a method reference and with a lambda that requests focus directly. */
    static final class DefersFocus {
        /** {@code Platform.runLater(node::requestFocus)}. */
        void deferReference(Node node) {
            Platform.runLater(node::requestFocus);
        }

        /** {@code Platform.runLater(() -> node.requestFocus())}. */
        void deferLambda(Node node) {
            Platform.runLater(() -> {
                node.setVisible(true);
                node.requestFocus();
            });
        }
    }

    /** Calls every rule must leave alone. */
    static final class Permitted {
        /** {@code Node.toFront()} changes stacking order inside the scene; it does not activate a window. */
        void stackAndFocus(Node node) {
            node.toFront();
            node.requestFocus();
        }

        /** A deferral that does not request focus. */
        void deferOther(Node node) {
            Platform.runLater(() -> node.setVisible(true));
        }

        /**
         * A deferral through a helper is not caught: the check is not transitive, so the docs rule covers it.
         */
        void deferThroughHelper(Node node) {
            Platform.runLater(() -> helper(node));
        }

        /** Requests focus on behalf of {@link #deferThroughHelper(Node)}. */
        private static void helper(Node node) {
            node.requestFocus();
        }
    }
}
