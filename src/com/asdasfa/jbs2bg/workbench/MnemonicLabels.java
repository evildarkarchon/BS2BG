package com.asdasfa.jbs2bg.workbench;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;

/**
 * Keeps a mnemonic label's Alt+letter mnemonic registered while its {@code labelFor} control leaves and re-enters
 * the scene (#155).
 *
 * <p>JavaFX registers a label's mnemonic against its control only when the label itself enters a scene, and only if
 * the control already has one at that moment; it also drops the control's mnemonics when the control leaves. Every
 * navigation render re-parents the Area panes, and inside a pane a label precedes its control, so on re-entry the
 * label registers before its control has a scene and the mnemonic is lost for good. Re-pointing {@code labelFor} is
 * the one public trigger that makes the label's skin register again, so this helper does that when the control
 * re-enters the label's scene.</p>
 */
final class MnemonicLabels {

    private MnemonicLabels() {
    }

    /**
     * Installs the re-registration on every mnemonic label under {@code root}, following ScrollPane content but not
     * descending into other controls' skins. A label's later {@code labelFor} re-pointing (such as an Area's list node
     * replacement) is followed automatically. Call once per subtree, on the JavaFX application thread.
     *
     * @param root an Area pane or other authored subtree
     */
    static void keepRegistered(Node root) {
        if (root instanceof Label label && label.isMnemonicParsing())
            keepRegistered(label);
        if (root instanceof ScrollPane scroll) {
            if (scroll.getContent() != null)
                keepRegistered(scroll.getContent());
        } else if (root instanceof Parent parent && !(root instanceof Control)) {
            for (Node child : parent.getChildrenUnmodifiable())
                keepRegistered(child);
        }
    }

    /** Watches whichever control {@code label} currently names and re-points the label when it re-enters the scene. */
    private static void keepRegistered(Label label) {
        ChangeListener<Scene> reRegister = (observable, previous, scene) -> {
            Node target = label.getLabelFor();
            // A label that enters the scene after its control registers by itself; only the reverse order is lost.
            if (scene != null && target != null && label.getScene() == scene) {
                label.setLabelFor(null);
                label.setLabelFor(target);
            }
        };
        label.labelForProperty().addListener((observable, previous, next) -> {
            if (previous != null)
                previous.sceneProperty().removeListener(reRegister);
            if (next != null)
                next.sceneProperty().addListener(reRegister);
        });
        if (label.getLabelFor() != null)
            label.getLabelFor().sceneProperty().addListener(reRegister);
    }
}
