package com.asdasfa.jbs2bg.fx;

import java.util.Objects;

import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * Application-owned semantic graphics for dialogs: drawn from public JavaFX
 * shapes and rasterized through {@link Node#snapshot}, so the same image
 * serves as a stage icon and as dialog content without depending on any
 * JavaFX theme resource. Must be called on the JavaFX Application Thread.
 */
public final class DialogGraphics {

    /**
     * Edge length, in pixels, of the graphic every dialog shows and uses as its window icon.
     */
    public static final int ICON_SIZE = 64;
    private static final Color INFORMATION_FILL = Color.web("#2f7fd6");
    private static final Color ERROR_FILL = Color.web("#d32f2f");
    private static final Color WARNING_FILL = Color.web("#f2a900");
    private static final Color CONFIRMATION_FILL = Color.web("#3c8f5b");
    private static final Color GLYPH = Color.WHITE;
    private static final Color WARNING_GLYPH = Color.web("#2b2b2b");
    private DialogGraphics() {
    }

    /**
     * Renders one semantic graphic.
     *
     * @param semantic the dialog meaning to depict
     * @param size     width and height in pixels
     * @return a fresh image with a transparent background
     * @throws NullPointerException     when semantic is null
     * @throws IllegalArgumentException when size is not positive
     */
    public static Image image(Semantic semantic, int size) {
        Objects.requireNonNull(semantic, "semantic");
        if (size <= 0)
            throw new IllegalArgumentException("size must be positive: " + size);
        Node graphic = node(semantic, size);
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        // The viewport pins the raster to exactly size x size regardless of stroke overhang.
        parameters.setViewport(new Rectangle2D(0, 0, size, size));
        WritableImage target = new WritableImage(size, size);
        return graphic.snapshot(parameters, target);
    }

    /**
     * Builds the vector graphic for one semantic.
     *
     * @param semantic the dialog meaning to depict
     * @param size     width and height in pixels
     * @return a node whose layout bounds are the {@code size} square
     */
    public static Node node(Semantic semantic, double size) {
        Objects.requireNonNull(semantic, "semantic");
        double centre = size / 2;
        double radius = size * 0.46;
        switch (semantic) {
            case INFORMATION:
                return new Group(disc(centre, radius, INFORMATION_FILL), glyph("i", size, GLYPH));
            case ERROR:
                return new Group(disc(centre, radius, ERROR_FILL), cross(size));
            case WARNING:
                return new Group(triangle(size), glyph("!", size * 0.9, WARNING_GLYPH));
            case CONFIRMATION:
                return new Group(disc(centre, radius, CONFIRMATION_FILL), glyph("?", size, GLYPH));
            default:
                throw new IllegalArgumentException("unknown semantic " + semantic);
        }
    }

    private static Circle disc(double centre, double radius, Color fill) {
        Circle circle = new Circle(centre, centre, radius, fill);
        circle.setStroke(fill.darker());
        circle.setStrokeWidth(radius * 0.06);
        return circle;
    }

    private static Polygon triangle(double size) {
        double inset = size * 0.06;
        Polygon triangle = new Polygon(size / 2, inset, size - inset, size - inset, inset, size - inset);
        triangle.setFill(WARNING_FILL);
        triangle.setStroke(WARNING_FILL.darker());
        triangle.setStrokeWidth(size * 0.03);
        return triangle;
    }

    private static Group cross(double size) {
        double from = size * 0.32;
        double to = size * 0.68;
        Line down = new Line(from, from, to, to);
        Line up = new Line(from, to, to, from);
        for (Line line : new Line[]{down, up}) {
            line.setStroke(GLYPH);
            line.setStrokeWidth(size * 0.09);
            line.setStrokeLineCap(StrokeLineCap.ROUND);
        }
        return new Group(down, up);
    }

    /**
     * A bold glyph centred on the {@code size} square.
     */
    private static Node glyph(String text, double size, Color fill) {
        Text glyph = new Text(text);
        glyph.setFont(Font.font("System", FontWeight.BOLD, size * 0.62));
        glyph.setFill(fill);
        glyph.setTextAlignment(TextAlignment.CENTER);
        // Text positions by its baseline by default; a CENTER origin makes y the vertical middle.
        glyph.setTextOrigin(VPos.CENTER);
        glyph.setX(size / 2 - glyph.getLayoutBounds().getWidth() / 2);
        glyph.setY(size / 2);
        return glyph;
    }

    /**
     * The meaning a dialog conveys; each has one graphic.
     */
    public enum Semantic {
        INFORMATION, ERROR, WARNING, CONFIRMATION
    }
}
