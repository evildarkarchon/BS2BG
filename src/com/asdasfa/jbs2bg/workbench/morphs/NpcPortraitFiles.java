package com.asdasfa.jbs2bg.workbench.morphs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;

/** Resolves local NPC portrait files without loading images or depending on JavaFX. */
public final class NpcPortraitFiles {
    private static final List<String> EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".bmp");

    private NpcPortraitFiles() {
    }

    /**
     * Finds the first portrait for an immutable NPC Morph Assignment.
     *
     * @param imagesDirectory directory containing user-supplied portraits
     * @param npc selected NPC Morph Assignment
     * @return the first matching regular file, or empty when none exists
     * @throws NullPointerException when an argument is null
     */
    public static Optional<Path> find(Path imagesDirectory, NpcMorphAssignmentSnapshot npc) {
        Objects.requireNonNull(npc, "npc");
        return find(imagesDirectory, npc.getDisplayName(), npc.getEditorId());
    }

    /**
     * Finds a portrait by the accepted legacy filename patterns and extension order.
     * The editor-ID pattern takes priority over the display-name-only pattern.
     *
     * @param imagesDirectory directory containing user-supplied portraits
     * @param displayName NPC display name used in portrait filenames
     * @param editorId NPC editor ID used in the preferred filename pattern
     * @return the first matching regular file, or empty when none exists
     * @throws NullPointerException when an argument is null
     */
    public static Optional<Path> find(Path imagesDirectory, String displayName, String editorId) {
        Objects.requireNonNull(imagesDirectory, "imagesDirectory");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(editorId, "editorId");
        for (String baseName : List.of(displayName + " (" + editorId + ")", displayName)) {
            for (String extension : EXTENSIONS) {
                Path candidate = imagesDirectory.resolve(baseName + extension);
                if (Files.isRegularFile(candidate))
                    return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
