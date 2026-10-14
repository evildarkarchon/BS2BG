package com.asdasfa.jbs2bg.workbench;

import java.util.Objects;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.value.ObservableDoubleValue;
import javafx.scene.control.ListView;

/**
 * Sizes an Area's catalog ListView to the rows it holds. Each Area owns the binding for its own catalogs; the shell
 * hands the Area the Workbench Stage's render-scale property, so a catalog re-snaps when the window moves to a screen
 * with another scale. FX thread only.
 */
public final class CatalogHeight {

    /** Static helpers only. */
    private CatalogHeight() {
    }

    /**
     * Shows only the rows that exist (up to the accepted cap) so a short catalog does not expose a pointless inner
     * scrollbar; larger catalogs scroll within the list while the management pane handles minimum-height overflow.
     * Tracks CSS, scene attachment, and the owner's render scale for the lifetime of this adapter ListView.
     *
     * @param list        current Templates or Morphs catalog control
     * @param cellHeight  fixed logical cell height used by its ListView
     * @param maximumRows largest catalog viewport before scrolling is required
     * @param renderScale the owning Stage's vertical render scale, which changes the pixel snapping
     * @throws NullPointerException when list or renderScale is null
     */
    public static void bind(ListView<?> list, double cellHeight, int maximumRows, ObservableDoubleValue renderScale) {
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(renderScale, "renderScale");
        DoubleBinding height = Bindings.createDoubleBinding(() -> {
            int rows = Math.max(1, Math.min(maximumRows, list.getItems().size()));
            // Snap each CSS inset independently, then round the total outward. Otherwise subtracting fractional
            // insets can leave VirtualFlow a fraction of a pixel short and expose a redundant vertical scrollbar.
            return list.snapSizeY(Math.ceil(list.snapSizeY(rows * cellHeight)
                    + list.snappedTopInset() + list.snappedBottomInset()));
        }, list.getItems(), list.insetsProperty(), list.sceneProperty(), renderScale);
        list.minHeightProperty().bind(height);
        list.prefHeightProperty().bind(height);
        list.maxHeightProperty().bind(height);
    }
}
