package com.asdasfa.jbs2bg.project;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.WriterConfig;

/**
 * Serializes immutable Project state with the established legacy field semantics
 * and replaces a local file only after a complete sibling temporary file exists.
 */
final class ProjectFileWriter {

    private ProjectFileWriter() {
    }

    /**
     * Persists one coherent snapshot through a sibling temporary file. The
     * temporary file is flushed before replacement and removed after a failed
     * replacement attempt.
     *
     * @param snapshot immutable Project content to persist
     * @param target requested destination file
     * @throws IOException when the temporary file cannot be written or installed
     */
    static void write(ProjectSnapshot snapshot, Path target) throws IOException {
        byte[] content = serialize(snapshot).getBytes(StandardCharsets.UTF_8);
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        if (parent == null)
            throw new IOException("The Project target has no parent directory: " + normalizedTarget);

        String targetName = normalizedTarget.getFileName() == null
                ? "project"
                : normalizedTarget.getFileName().toString();
        String prefix = "." + targetName + "-";
        if (prefix.length() < 3)
            prefix = ".project-";

        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        try {
            writeAndFlush(temporary, content);
            replace(temporary, normalizedTarget);
        } catch (IOException failure) {
            cleanupTemporary(temporary, failure);
            throw failure;
        } catch (RuntimeException failure) {
            cleanupTemporary(temporary, failure);
            throw failure;
        }
    }

    /**
     * Removes only the detached staging file after a failed write or replacement,
     * preserving the operation failure as the primary diagnostic cause.
     *
     * @param temporary staging file to remove
     * @param failure primary persistence failure
     */
    private static void cleanupTemporary(Path temporary, Throwable failure) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            // Preserve the persistence failure as primary while retaining cleanup context.
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * Writes every byte and asks the filesystem to flush file content before the
     * destination path can be replaced.
     *
     * @param temporary sibling temporary file
     * @param content serialized UTF-8 Project bytes
     * @throws IOException when writing or flushing fails
     */
    private static void writeAndFlush(Path temporary, byte[] content) throws IOException {
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(content);
            while (buffer.hasRemaining())
                channel.write(buffer);
            channel.force(true);
        }
    }

    /**
     * Installs the completed temporary file as the single atomic replacement step.
     * Unsupported providers fail truthfully instead of risking a non-atomic move.
     *
     * @param temporary completed sibling temporary file
     * @param target normalized destination path
     * @throws IOException when replacement fails
     */
    private static void replace(Path temporary, Path target) throws IOException {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Builds the established `.jbs2bg` JSON document without making whitespace or
     * member ordering part of the persistence contract.
     *
     * @param snapshot immutable Project content
     * @return pretty-printed legacy Project JSON
     */
    private static String serialize(ProjectSnapshot snapshot) {
        JsonObject root = new JsonObject();
        root.add("SliderPresets", serializeSliderPresets(snapshot));
        root.add("CustomMorphTargets", serializeCustomMorphTargets(snapshot));
        root.add("MorphedNPCs", serializeNpcMorphAssignments(snapshot));
        return root.toString(WriterConfig.PRETTY_PRINT);
    }

    /**
     * Serializes the Slider Preset catalog, retaining the legacy omission rule for
     * unchanged synthesized defaults.
     *
     * @param snapshot Project content containing the catalog
     * @return legacy Slider Preset object
     */
    private static JsonObject serializeSliderPresets(ProjectSnapshot snapshot) {
        JsonObject presets = new JsonObject();
        for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
            JsonObject serializedPreset = new JsonObject();
            serializedPreset.add("isUUNP", preset.isUunp());
            JsonArray choices = new JsonArray();
            for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
                if (!isOmittedMissingDefault(choice))
                    choices.add(serializeSliderChoice(choice));
            }
            serializedPreset.add("SetSliders", choices);
            presets.add(preset.getName(), serializedPreset);
        }
        return presets;
    }

    /**
     * Reports whether a synthesized choice remains in the legacy all-default state
     * that is intentionally absent from persisted files.
     *
     * @param choice slider choice to classify
     * @return true when the choice must be omitted
     */
    private static boolean isOmittedMissingDefault(SliderChoiceSnapshot choice) {
        return choice.isMissingDefault() && choice.isEnabled()
                && choice.getPercentageMinimum() == 100 && choice.getPercentageMaximum() == 100;
    }

    /**
     * Serializes one explicit slider choice, including nullable stored values.
     *
     * @param choice immutable slider choice
     * @return legacy slider-choice object
     */
    private static JsonObject serializeSliderChoice(SliderChoiceSnapshot choice) {
        JsonObject serialized = new JsonObject();
        serialized.add("name", choice.getName());
        serialized.add("enabled", choice.isEnabled());
        if (choice.getStoredSmallValue().isPresent())
            serialized.add("valueSmall", choice.getStoredSmallValue().getAsInt());
        else
            serialized.add("valueSmall", Json.NULL);
        if (choice.getStoredBigValue().isPresent())
            serialized.add("valueBig", choice.getStoredBigValue().getAsInt());
        else
            serialized.add("valueBig", Json.NULL);
        serialized.add("pctMin", choice.getPercentageMinimum());
        serialized.add("pctMax", choice.getPercentageMaximum());
        return serialized;
    }

    /**
     * Serializes Custom Morph Targets and their Slider Preset references.
     *
     * @param snapshot Project content containing Custom Morph Targets
     * @return legacy Custom Morph Target object
     */
    private static JsonObject serializeCustomMorphTargets(ProjectSnapshot snapshot) {
        JsonObject targets = new JsonObject();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            JsonObject serialized = new JsonObject();
            serialized.add("SliderPresets", serializeAssignments(target.getSliderPresetNames()));
            targets.add(target.getName(), serialized);
        }
        return targets;
    }

    /**
     * Serializes NPC Morph Assignments with the established display-name keys and
     * field names.
     *
     * @param snapshot Project content containing NPC Morph Assignments
     * @return legacy NPC Morph Assignment object
     */
    private static JsonObject serializeNpcMorphAssignments(ProjectSnapshot snapshot) {
        JsonObject assignments = new JsonObject();
        for (NpcMorphAssignmentSnapshot npc : snapshot.getNpcMorphAssignments()) {
            JsonObject serialized = new JsonObject();
            serialized.add("Mod", npc.getPluginName());
            serialized.add("EditorId", npc.getEditorId());
            serialized.add("Race", npc.getRace());
            serialized.add("FormId", npc.getFormId());
            serialized.add("SliderPresets", serializeAssignments(npc.getSliderPresetNames()));
            assignments.add(npc.getDisplayName(), serialized);
        }
        return assignments;
    }

    /**
     * Copies canonical Slider Preset references into a JSON string array.
     *
     * @param names immutable canonical reference names
     * @return serialized assignment array
     */
    private static JsonArray serializeAssignments(Iterable<String> names) {
        JsonArray assignments = new JsonArray();
        for (String name : names)
            assignments.add(name);
        return assignments;
    }
}
