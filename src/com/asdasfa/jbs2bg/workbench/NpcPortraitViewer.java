package com.asdasfa.jbs2bg.workbench;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.workbench.morphs.NpcPortraitFiles;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The NPC portrait viewer shared by the Morphs and NPC Database inspectors. It owns the one owned viewer Stage, the
 * thumbnail loader each inspector uses ({@link #thumbnail}), and the stale-load guard that keeps an earlier
 * selection's background decode from replacing the current one.
 *
 * <p>Portraits are looked up in the image directory the {@link WorkbenchEnvironment} supplies. The viewer registers
 * its root with the shell's {@link WorkbenchSatellites} while it is open, which re-themes it and closes it when the
 * Workbench window is hidden. Confined to the JavaFX Application Thread.
 */
public final class NpcPortraitViewer {
    private static final double THUMBNAIL_WIDTH = 200.0;
    private static final double THUMBNAIL_HEIGHT = 180.0;

    private final Path imageDirectory;
    private final Stage owner;
    private final WorkbenchSatellites satellites;
    private final Consumer<Node> restoreLauncherFocus;
    private Stage viewer;
    private Path viewerPath;

    /**
     * Creates the viewer module; no window opens until a launcher asks for one.
     *
     * @param imageDirectory       directory holding {@code <name> (<editor ID>).<ext>} portrait files
     * @param owner                Workbench window that owns the viewer Stage
     * @param satellites           the shell's appearance, which re-themes and dismisses the open viewer
     * @param restoreLauncherFocus restores focus to the launching button, or to the shell's fallback when that
     *                             button can no longer take it; called when the viewer hides while the owner shows
     * @throws NullPointerException when an argument is null
     */
    public NpcPortraitViewer(Path imageDirectory, Stage owner, WorkbenchSatellites satellites,
                             Consumer<Node> restoreLauncherFocus) {
        this.imageDirectory = Objects.requireNonNull(imageDirectory, "imageDirectory");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.satellites = Objects.requireNonNull(satellites, "satellites");
        this.restoreLauncherFocus = Objects.requireNonNull(restoreLauncherFocus, "restoreLauncherFocus");
    }

    /**
     * Binds one inspector's thumbnail controls to this viewer and wires {@code launcher} to open it. The returned
     * thumbnail starts in no particular state; render it with {@link Thumbnail#show} or {@link Thumbnail#clear}.
     *
     * @param image           inspector thumbnail
     * @param status          inspector status label; its text is mirrored into accessible help
     * @param launcher        button that opens the viewer; enabled only while a thumbnail has decoded
     * @param noSelectionHelp accessible help shown when nothing is selected, worded for the inspector's Area
     * @return the inspector's thumbnail loader
     * @throws NullPointerException when an argument is null
     */
    public Thumbnail thumbnail(ImageView image, Label status, Button launcher, String noSelectionHelp) {
        Thumbnail thumbnail = new Thumbnail(image, status, launcher, noSelectionHelp);
        launcher.setOnAction(event -> thumbnail.open());
        return thumbnail;
    }

    /**
     * One inspector's thumbnail loader: it follows that inspector's stable selection and decodes the portrait away
     * from the JavaFX lane.
     */
    public final class Thumbnail {
        private final ImageView image;
        private final Label status;
        private final Button launcher;
        private final String noSelectionHelp;
        private NpcMorphAssignmentIdentity renderedIdentity;
        private String renderedDisplayName;
        private Path renderedPath;
        private Image renderedImage;

        /** Binds the inspector's controls; see {@link NpcPortraitViewer#thumbnail}. */
        private Thumbnail(ImageView image, Label status, Button launcher, String noSelectionHelp) {
            this.image = Objects.requireNonNull(image, "image");
            this.status = Objects.requireNonNull(status, "status");
            this.launcher = Objects.requireNonNull(launcher, "launcher");
            this.noSelectionHelp = Objects.requireNonNull(noSelectionHelp, "noSelectionHelp");
        }

        /**
         * Shows the portrait of the selected NPC. A repeat of the identity and display name already shown does
         * nothing, so an unrelated re-render does not restart the decode.
         *
         * @param identity    selected NPC; its editor ID is part of the portrait filename
         * @param displayName NPC display name used in the portrait filename
         * @throws NullPointerException when an argument is null
         */
        public void show(NpcMorphAssignmentIdentity identity, String displayName) {
            render(Objects.requireNonNull(identity, "identity"), Objects.requireNonNull(displayName, "displayName"));
        }

        /** Shows that nothing is selected. */
        public void clear() {
            render(null, null);
        }

        /**
         * Resolves the portrait for the stable selected NPC and starts decoding away from the JavaFX lane. Old load
         * callbacks compare image identity before touching the inspector so rapid selection cannot show a stale
         * portrait.
         */
        private void render(NpcMorphAssignmentIdentity identity, String displayName) {
            if (Objects.equals(identity, renderedIdentity) && Objects.equals(displayName, renderedDisplayName))
                return;
            renderedIdentity = identity;
            renderedDisplayName = displayName;
            renderedPath = null;
            renderedImage = null;
            image.setImage(null);
            launcher.setDisable(true);
            if (identity == null) {
                status.setText("No NPC portrait selected");
                status.setAccessibleHelp(noSelectionHelp);
                return;
            }
            // The selected NPC is already named by the catalog and inspector; only the dedicated viewer names its
            // image.
            Optional<Path> file = NpcPortraitFiles.find(imageDirectory, displayName, identity.getEditorId());
            if (file.isEmpty()) {
                String expected = "images/" + displayName + " (" + identity.getEditorId() + ").jpg";
                String missing = "No portrait found. Expected " + expected
                        + " (or .jpeg, .png, .bmp), then a display-name-only image.";
                status.setText(missing);
                status.setAccessibleHelp(missing);
                return;
            }
            renderedPath = file.orElseThrow();
            String filename = renderedPath.getFileName().toString();
            Image loading = new Image(renderedPath.toUri().toString(), THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT,
                    true, true, true);
            renderedImage = loading;
            image.setImage(loading);
            String loadingText = "Loading portrait: " + filename;
            status.setText(loadingText);
            status.setAccessibleHelp(loadingText);
            loading.progressProperty().addListener((observable, previous, current) -> finishLoad(loading, filename));
            loading.errorProperty().addListener((observable, previous, current) -> finishLoad(loading, filename));
            finishLoad(loading, filename);
        }

        /** Enables the viewer only after the current asynchronous thumbnail decoded successfully. */
        private void finishLoad(Image loaded, String filename) {
            // The stale-load guard: a decode started for an earlier selection must not touch this inspector.
            if (renderedImage != loaded)
                return;
            if (loaded.isError()) {
                image.setImage(null);
                String failed = "Could not load portrait: " + filename;
                status.setText(failed);
                status.setAccessibleHelp(failed);
            } else if (loaded.getProgress() >= 1.0 && loaded.getWidth() > 0.0) {
                String done = "Portrait file: " + filename;
                status.setText(done);
                status.setAccessibleHelp(done);
                launcher.setDisable(false);
            }
        }

        /**
         * Opens the selected portrait in a keyboard-closeable, zoomable owned viewer while a full-resolution image
         * loads in the background. The filename and dimensions remain available as text for assistive technology.
         */
        private void open() {
            if (renderedPath == null || renderedIdentity == null || renderedImage == null
                    || renderedImage.isError() || renderedImage.getProgress() < 1.0)
                return;
            openViewer(renderedPath, renderedDisplayName, renderedIdentity, launcher);
        }
    }

    /** Builds one owned portrait window and restores focus to the visible launching Area when it closes. */
    private void openViewer(Path file, String displayName, NpcMorphAssignmentIdentity identity, Button launcher) {
        if (viewer != null && viewer.isShowing()) {
            if (file.equals(viewerPath)) {
                // The one allowlisted window activation (ProductionPlatformBanGateTest rule 2, see
                // docs/build/workbench-platform.md "Production platform bans"): the user just commanded this
                // viewer open again, the application is already in the foreground, and the raise is synchronous,
                // so it cannot steal focus from another application or arrive after the user moved on.
                viewer.toFront();
                viewer.requestFocus();
                return;
            }
            // A different selected NPC needs its own image; the old viewer cannot be reused with stale content.
            viewer.close();
        }
        VBox content = new VBox(10.0);
        content.setId("workbenchRoot");
        content.getStyleClass().add("npc-portrait-viewer");
        content.setAccessibleText("NPC portrait viewer");
        Label title = new Label("NPC portrait: " + displayName);
        title.getStyleClass().add("title");
        Label filename = new Label("File: " + file.getFileName());
        filename.setWrapText(true);
        Label dimensions = new Label("Loading image dimensions…");
        dimensions.setAccessibleText("NPC portrait dimensions: loading");
        ImageView fullView = new ImageView();
        fullView.setAccessibleText("NPC portrait: " + displayName + ", plugin "
                + identity.getPluginName() + ", editor ID " + identity.getEditorId());
        fullView.setPreserveRatio(true);
        fullView.setSmooth(true);
        Image fullImage = new Image(file.toUri().toString(), true);
        fullView.setImage(fullImage);
        Runnable updateDimensions = () -> {
            if (fullImage.isError()) {
                dimensions.setText("Could not load portrait dimensions.");
                dimensions.setAccessibleText("NPC portrait dimensions unavailable");
            } else if (fullImage.getProgress() >= 1.0) {
                String size = (int) fullImage.getWidth() + " × " + (int) fullImage.getHeight() + " pixels";
                dimensions.setText(size);
                dimensions.setAccessibleText("NPC portrait dimensions: " + size);
            }
        };
        fullImage.progressProperty().addListener((observable, previous, current) -> updateDimensions.run());
        fullImage.errorProperty().addListener((observable, previous, current) -> updateDimensions.run());
        updateDimensions.run();
        ScrollPane imageScroll = new ScrollPane(fullView);
        imageScroll.setAccessibleText("NPC portrait image scroll area");
        imageScroll.setPannable(true);
        VBox.setVgrow(imageScroll, Priority.ALWAYS);
        Label zoomLabel = new Label("Zoom: 100%");
        Slider zoom = new Slider(0.25, 4.0, 1.0);
        zoom.setAccessibleText("NPC portrait zoom");
        zoom.setBlockIncrement(0.25);
        fullView.fitWidthProperty().bind(zoom.valueProperty().multiply(500.0));
        zoom.valueProperty().addListener((observable, previous, current) ->
                zoomLabel.setText("Zoom: " + Math.round(current.doubleValue() * 100.0) + "%"));
        Button close = new Button("Close");
        close.setAccessibleText("Close NPC portrait viewer");
        content.getChildren().setAll(title, filename, dimensions, imageScroll, zoomLabel, zoom, close);
        Stage opened = new Stage();
        opened.initOwner(owner);
        opened.getIcons().setAll(owner.getIcons());
        opened.setTitle("NPC Portrait — " + displayName);
        Scene scene = new Scene(content, 640.0, 560.0);
        scene.getStylesheets().add(NpcPortraitViewer.class
                .getResource("/com/asdasfa/jbs2bg/workbench.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                opened.close();
                event.consume();
            }
        });
        opened.setScene(scene);
        opened.setMinWidth(360.0);
        opened.setMinHeight(300.0);
        close.setOnAction(event -> opened.close());
        viewer = opened;
        viewerPath = file;
        WorkbenchSatellites.Registration registration = satellites.register(content, opened::close);
        opened.setOnHidden(event -> {
            registration.close();
            if (viewer == opened) {
                viewer = null;
                viewerPath = null;
            }
            if (owner.isShowing())
                restoreLauncherFocus.accept(launcher);
        });
        opened.show();
        close.requestFocus();
    }
}
