package com.asdasfa.jbs2bg.project;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.mozilla.universalchardet.UniversalDetector;

import com.asdasfa.jbs2bg.json.JacksonJson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.TokenStreamLocation;

/**
 * Owned Jackson streaming adapter for Project persistence. Writing is the sole
 * production route through {@link ProjectFileWriter}; reading remains a
 * compatibility oracle until its dedicated cutover slice.
 */
final class ProjectJacksonAdapter {

    private static final String RESOURCE_LIMIT_CODE = "PROJECT_JSON_RESOURCE_LIMIT";
    private static final String TRAILING_DATA_CODE = "PROJECT_JSON_TRAILING_DATA";
    private static final String WRITE_FAILED_CODE = "PROJECT_JSON_WRITE_FAILED";
    private static final Comparator<String> CASE_INSENSITIVE_ORDER = String::compareToIgnoreCase;
    private static final Set<String> ROOT_FIELDS = Set.of("SliderPresets", "CustomMorphTargets", "MorphedNPCs");
    private static final Set<String> PRESET_FIELDS = Set.of("isUUNP", "SetSliders");
    private static final Set<String> CHOICE_FIELDS = Set.of("name", "enabled", "valueSmall", "valueBig", "pctMin",
            "pctMax");
    private static final Set<String> TARGET_FIELDS = Set.of("SliderPresets");
    private static final Set<String> NPC_FIELDS = Set.of("Mod", "EditorId", "Race", "FormId", "SliderPresets");
    private ProjectJacksonAdapter() {
    }

    /**
     * Detects the legacy charset, stream-parses one complete Project, and returns a
     * detached candidate after the final {@link Project#from(ProjectSnapshot)}
     * integrity check.
     *
     * @param source Project fixture or source file
     * @return immutable candidate and ordered recovery diagnostics
     * @throws ProjectFormatException for I/O, syntax, schema, domain, or limit failure
     */
    static Candidate read(Path source) {
        Objects.requireNonNull(source, "source");
        Path normalizedSource = source.toAbsolutePath().normalize();
        long sourceSize;
        try {
            sourceSize = Files.size(normalizedSource);
        } catch (IOException | RuntimeException exception) {
            throw failure(ProjectDiagnosticCodes.PROJECT_FILE_READ_FAILED, normalizedSource.toString(), "/", 0, 0,
                    readableMessage(exception, "The Project source could not be inspected."));
        }
        // Refuse an oversized artifact before readAllBytes can allocate storage for it;
        // the post-read check below still closes a concurrent file-growth race.
        if (sourceSize > JacksonJson.projectMaximumDocumentBytes()) {
            throw failure(RESOURCE_LIMIT_CODE, normalizedSource.toString(), "/", 1, 1,
                    "Project input exceeds the 64 MiB document limit.");
        }
        byte[] sourceBytes;
        Charset charset;
        try {
            charset = detectCharset(normalizedSource);
            sourceBytes = Files.readAllBytes(normalizedSource);
        } catch (IOException | RuntimeException exception) {
            throw failure(ProjectDiagnosticCodes.PROJECT_FILE_READ_FAILED, normalizedSource.toString(), "/", 0, 0,
                    readableMessage(exception, "The Project source could not be read."));
        }
        if (sourceBytes.length > JacksonJson.projectMaximumDocumentBytes()) {
            throw failure(RESOURCE_LIMIT_CODE, normalizedSource.toString(), "/", 1, 1,
                    "Project input exceeds the 64 MiB document limit.");
        }

        String decoded = new String(sourceBytes, charset);
        Reader reader = null;
        // Parse detected text directly so the 64 MiB source limit is not applied again after UTF-8 expansion.
        try (JsonParser parser = JacksonJson.projectReaderFactory()
                .createParser(ObjectReadContext.empty(), decoded)) {
            reader = new Reader(parser, normalizedSource.toString());
            ParsedProject parsed = readDocument(reader);
            return finishCandidate(normalizedSource, parsed);
        } catch (ProjectFormatException exception) {
            throw exception;
        } catch (JacksonException exception) {
            String path = reader == null ? "/" : reader.failurePath();
            TokenStreamLocation location = exception.getLocation();
            if (location == null && reader != null)
                location = reader.location();
            String code = JacksonJson.isConstraintFailure(exception)
                    ? RESOURCE_LIMIT_CODE : ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED;
            throw failure(code, normalizedSource.toString(), path, location, exception.getOriginalMessage());
        }
    }

    /**
     * Writes deterministic UTF-8 Project bytes in canonical domain order. The
     * method only returns after the snapshot passes the aggregate integrity check.
     *
     * @param snapshot detached Project content
     * @return newly owned canonical bytes without a BOM or final newline
     * @throws ProjectFormatException when the snapshot or stream cannot be represented
     */
    static byte[] write(ProjectSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        Project project;
        try {
            project = Project.from(snapshot);
        } catch (IllegalArgumentException exception) {
            throw failure(ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, "<memory>", "/", 0, 0,
                    exception.getMessage());
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = JacksonJson.canonicalWriterFactory()
                .createGenerator(ObjectWriteContext.empty(), output)) {
            writeProject(generator, project);
        } catch (JacksonException exception) {
            throw failure(WRITE_FAILED_CODE, "<memory>", "/", 0, 0,
                    readableMessage(exception, "Project JSON could not be represented."));
        }
        return output.toByteArray();
    }

    /** Detects the same source charset as the legacy loader, retaining UTF-8 fallback for ASCII. */
    private static Charset detectCharset(Path source) throws IOException {
        String detected = UniversalDetector.detectCharset(source.toFile());
        return detected == null ? StandardCharsets.UTF_8 : Charset.forName(detected);
    }

    /** Parses the root fields without assuming their encounter order. */
    private static ParsedProject readDocument(Reader reader) {
        require(reader.next("/"), JsonToken.START_OBJECT, ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, reader,
                "/", "The Project document root must be an object.");
        List<SliderPresetSnapshot> presets = null;
        List<RawTarget> targets = null;
        List<RawNpcMorphAssignment> npcMorphAssignments = null;
        Set<String> seen = new LinkedHashSet<>();
        JsonToken token;
        while ((token = reader.next("/")) != JsonToken.END_OBJECT) {
            require(token, JsonToken.PROPERTY_NAME, ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED, reader, "/",
                    "The Project root object is incomplete.");
            String name = fixedMember(reader, seen, ROOT_FIELDS, "/");
            String path = child("/", name);
            JsonToken value = reader.next(path);
            switch (name) {
                case "SliderPresets" -> presets = readSliderPresets(reader, value, path);
                case "CustomMorphTargets" -> targets = readCustomMorphTargets(reader, value, path);
                case "MorphedNPCs" -> npcMorphAssignments = readNpcMorphAssignments(reader, value, path);
                default -> throw new AssertionError("Validated Project root field was not dispatched: " + name);
            }
        }
        requirePresent(presets, reader, "/SliderPresets", "SliderPresets");
        requirePresent(targets, reader, "/CustomMorphTargets", "CustomMorphTargets");
        requirePresent(npcMorphAssignments, reader, "/MorphedNPCs", "MorphedNPCs");
        if (reader.next("/") != null) {
            throw reader.failure(TRAILING_DATA_CODE, "/",
                    "Project input contains data after the first complete document.");
        }
        return new ParsedProject(presets, targets, npcMorphAssignments);
    }

    /** Parses the dynamic Slider Preset catalog and applies legacy name normalization. */
    private static List<SliderPresetSnapshot> readSliderPresets(Reader reader, JsonToken token, String path) {
        require(token, JsonToken.START_OBJECT, ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, reader, path,
                "Project member 'SliderPresets' must be an object.");
        List<SliderPresetSnapshot> presets = new ArrayList<>();
        Map<String, String> identities = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        while ((token = reader.next(path)) != JsonToken.END_OBJECT) {
            require(token, JsonToken.PROPERTY_NAME, ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED, reader, path,
                    "The Slider Preset catalog is incomplete.");
            String persistedName = reader.propertyName(path);
            String presetPath = child(path, persistedName);
            String name = persistedName.replace('.', ' ').trim();
            if (name.isEmpty()) {
                throw reader.failure(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_REQUIRED, presetPath,
                        "A Slider Preset name must not be empty.");
            }
            if (identities.containsKey(name)) {
                throw reader.failure(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE, presetPath,
                        "A Slider Preset with this name already exists in the Project file.");
            }
            identities.put(name, name);
            presets.add(readSliderPreset(reader, reader.next(presetPath), presetPath, name));
        }
        presets.sort(Comparator.comparing(SliderPresetSnapshot::getName, CASE_INSENSITIVE_ORDER));
        return presets;
    }

    /** Parses one fixed-schema Slider Preset object. */
    private static SliderPresetSnapshot readSliderPreset(Reader reader, JsonToken token, String path, String name) {
        require(token, JsonToken.START_OBJECT, ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, reader, path,
                "A Slider Preset must be an object.");
        Boolean uunp = null;
        ExplicitChoices explicitChoices = null;
        Set<String> seen = new LinkedHashSet<>();
        while ((token = reader.next(path)) != JsonToken.END_OBJECT) {
            require(token, JsonToken.PROPERTY_NAME, ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED, reader, path,
                    "The Slider Preset object is incomplete.");
            String field = fixedMember(reader, seen, PRESET_FIELDS, path);
            String fieldPath = child(path, field);
            JsonToken value = reader.next(fieldPath);
            if ("isUUNP".equals(field))
                uunp = Boolean.valueOf(readBoolean(reader, value, fieldPath));
            else
                explicitChoices = readSliderChoices(reader, value, fieldPath);
        }
        requirePresent(uunp, reader, child(path, "isUUNP"), "isUUNP");
        requirePresent(explicitChoices, reader, child(path, "SetSliders"), "SetSliders");
        return new SliderPresetSnapshot(name, uunp.booleanValue(), explicitChoices.withDefaults(uunp.booleanValue()));
    }

    /**
     * Parses explicit slider choices. If SetSliders precedes isUUNP, synthesis is
     * deferred by re-applying the final profile after the object closes.
     */
    private static ExplicitChoices readSliderChoices(Reader reader, JsonToken token, String path) {
        require(token, JsonToken.START_ARRAY, ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, reader, path,
                "Project member 'SetSliders' must be an array.");
        List<RawChoice> choices = new ArrayList<>();
        Set<String> representedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        int index = 0;
        while ((token = reader.next(path + "/" + index)) != JsonToken.END_ARRAY) {
            String choicePath = path + "/" + index++;
            RawChoice choice = readSliderChoice(reader, token, choicePath);
            if (!representedNames.add(choice.name())) {
                throw reader.failure(ProjectDiagnosticCodes.SLIDER_CHOICE_NAME_DUPLICATE,
                        child(choicePath, "name"),
                        "A Slider Preset cannot contain duplicate slider-choice names.");
            }
            choices.add(choice);
        }
        // Both explicit effective values and omitted defaults depend on isUUNP, which
        // may legally follow SetSliders in the object, so adaptation is deferred.
        return new ExplicitChoices(choices, representedNames);
    }

    /** Parses one fixed-schema explicit slider choice with exact integer lexemes. */
    private static RawChoice readSliderChoice(Reader reader, JsonToken token, String path) {
        require(token, JsonToken.START_OBJECT, ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, reader, path,
                "A slider choice must be an object.");
        String name = null;
        Boolean enabled = null;
        Integer small = null;
        Integer big = null;
        Integer minimum = null;
        Integer maximum = null;
        boolean smallSeen = false;
        boolean bigSeen = false;
        Set<String> seen = new LinkedHashSet<>();
        while ((token = reader.next(path)) != JsonToken.END_OBJECT) {
            require(token, JsonToken.PROPERTY_NAME, ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED, reader, path,
                    "The slider-choice object is incomplete.");
            String field = fixedMember(reader, seen, CHOICE_FIELDS, path);
            String fieldPath = child(path, field);
            JsonToken value = reader.next(fieldPath);
            switch (field) {
                case "name" -> name = readString(reader, value, fieldPath);
                case "enabled" -> enabled = Boolean.valueOf(readBoolean(reader, value, fieldPath));
                case "valueSmall" -> {
                    smallSeen = true;
                    small = readNullableInteger(reader, value, fieldPath);
                }
                case "valueBig" -> {
                    bigSeen = true;
                    big = readNullableInteger(reader, value, fieldPath);
                }
                case "pctMin" -> minimum = Integer.valueOf(readInteger(reader, value, fieldPath));
                case "pctMax" -> maximum = Integer.valueOf(readInteger(reader, value, fieldPath));
                default -> throw new AssertionError("Validated slider-choice field was not dispatched: " + field);
            }
        }
        requirePresent(name, reader, child(path, "name"), "name");
        if (name.trim().isEmpty()) {
            throw reader.failure(ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, child(path, "name"),
                    "A slider-choice name must not be empty.");
        }
        requirePresent(enabled, reader, child(path, "enabled"), "enabled");
        requireSeen(smallSeen, reader, child(path, "valueSmall"), "valueSmall");
        requireSeen(bigSeen, reader, child(path, "valueBig"), "valueBig");
        requirePresent(minimum, reader, child(path, "pctMin"), "pctMin");
        requirePresent(maximum, reader, child(path, "pctMax"), "pctMax");
        return new RawChoice(name, enabled.booleanValue(), small, big, minimum.intValue(), maximum.intValue());
    }

    /** Parses the dynamic Custom Morph Target catalog without resolving references yet. */
    private static List<RawTarget> readCustomMorphTargets(Reader reader, JsonToken token, String path) {
        require(token, JsonToken.START_OBJECT, ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, reader, path,
                "Project member 'CustomMorphTargets' must be an object.");
        List<RawTarget> targets = new ArrayList<>();
        Set<String> identities = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        while ((token = reader.next(path)) != JsonToken.END_OBJECT) {
            require(token, JsonToken.PROPERTY_NAME, ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED, reader, path,
                    "The Custom Morph Target catalog is incomplete.");
            String persistedName = reader.propertyName(path);
            String targetPath = child(path, persistedName);
            String name = persistedName.trim();
            if (name.isEmpty()) {
                throw reader.failure(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_REQUIRED, targetPath,
                        "A Custom Morph Target name must not be empty.");
            }
            if (!identities.add(name)) {
                throw reader.failure(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_DUPLICATE, targetPath,
                        "A Custom Morph Target with this name already exists in the Project file.");
            }
            targets.add(readCustomMorphTarget(reader, reader.next(targetPath), targetPath, name));
        }
        return targets;
    }

    /** Parses one fixed-schema Custom Morph Target object. */
    private static RawTarget readCustomMorphTarget(Reader reader, JsonToken token, String path, String name) {
        require(token, JsonToken.START_OBJECT, ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, reader, path,
                "A Custom Morph Target must be an object.");
        List<AssignmentReference> assignments = null;
        Set<String> seen = new LinkedHashSet<>();
        while ((token = reader.next(path)) != JsonToken.END_OBJECT) {
            require(token, JsonToken.PROPERTY_NAME, ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED, reader, path,
                    "The Custom Morph Target object is incomplete.");
            String field = fixedMember(reader, seen, TARGET_FIELDS, path);
            String fieldPath = child(path, field);
            assignments = readAssignments(reader, reader.next(fieldPath), fieldPath);
        }
        requirePresent(assignments, reader, child(path, "SliderPresets"), "SliderPresets");
        return new RawTarget(name, assignments);
    }

    /** Parses the NPC Morph Assignment catalog while permitting repeated display-name members. */
    private static List<RawNpcMorphAssignment> readNpcMorphAssignments(Reader reader, JsonToken token, String path) {
        require(token, JsonToken.START_OBJECT, ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, reader, path,
                "Project member 'MorphedNPCs' must be an object.");
        List<RawNpcMorphAssignment> assignments = new ArrayList<>();
        Set<NpcMorphAssignmentIdentity> identities = new HashSet<>();
        while ((token = reader.next(path)) != JsonToken.END_OBJECT) {
            require(token, JsonToken.PROPERTY_NAME, ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED, reader, path,
                    "The NPC Morph Assignment catalog is incomplete.");
            String displayName = reader.propertyName(path);
            String npcPath = child(path, displayName);
            RawNpcMorphAssignment assignment = readNpcMorphAssignment(
                    reader, reader.next(npcPath), npcPath, displayName);
            NpcMorphAssignmentIdentity identity = new NpcMorphAssignmentIdentity(
                    assignment.pluginName(), assignment.editorId());
            if (!identities.add(identity)) {
                throw reader.failure(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE, npcPath,
                        "An NPC Morph Assignment with this plugin and editor ID already exists.");
            }
            assignments.add(assignment);
        }
        return assignments;
    }

    /** Parses one fixed-schema NPC Morph Assignment object. */
    private static RawNpcMorphAssignment readNpcMorphAssignment(
            Reader reader, JsonToken token, String path, String displayName) {
        require(token, JsonToken.START_OBJECT, ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, reader, path,
                "An NPC Morph Assignment must be an object.");
        String pluginName = null;
        String editorId = null;
        String race = null;
        String formId = null;
        List<AssignmentReference> assignments = null;
        Set<String> seen = new LinkedHashSet<>();
        while ((token = reader.next(path)) != JsonToken.END_OBJECT) {
            require(token, JsonToken.PROPERTY_NAME, ProjectDiagnosticCodes.PROJECT_JSON_MALFORMED, reader, path,
                    "The NPC Morph Assignment object is incomplete.");
            String field = fixedMember(reader, seen, NPC_FIELDS, path);
            String fieldPath = child(path, field);
            JsonToken value = reader.next(fieldPath);
            switch (field) {
                case "Mod" -> pluginName = readString(reader, value, fieldPath);
                case "EditorId" -> editorId = readString(reader, value, fieldPath);
                case "Race" -> race = readString(reader, value, fieldPath);
                case "FormId" -> formId = normalizeFormId(readString(reader, value, fieldPath));
                case "SliderPresets" -> assignments = readAssignments(reader, value, fieldPath);
                default -> throw new AssertionError("Validated NPC field was not dispatched: " + field);
            }
        }
        requirePresent(pluginName, reader, child(path, "Mod"), "Mod");
        requirePresent(editorId, reader, child(path, "EditorId"), "EditorId");
        requirePresent(race, reader, child(path, "Race"), "Race");
        requirePresent(formId, reader, child(path, "FormId"), "FormId");
        requirePresent(assignments, reader, child(path, "SliderPresets"), "SliderPresets");
        return new RawNpcMorphAssignment(displayName, pluginName, editorId, race, formId, assignments);
    }

    /** Parses one assignment array and rejects case-insensitive repetitions in encounter order. */
    private static List<AssignmentReference> readAssignments(Reader reader, JsonToken token, String path) {
        require(token, JsonToken.START_ARRAY, ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, reader, path,
                "A Slider Preset assignment collection must be an array.");
        List<AssignmentReference> assignments = new ArrayList<>();
        Set<String> represented = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        int index = 0;
        while ((token = reader.next(path + "/" + index)) != JsonToken.END_ARRAY) {
            String itemPath = path + "/" + index++;
            String name = readString(reader, token, itemPath);
            String normalized = name.trim();
            if (!represented.add(normalized)) {
                throw reader.failure(ProjectDiagnosticCodes.SLIDER_PRESET_ASSIGNMENT_DUPLICATE, itemPath,
                        "A Slider Preset assignment occurs more than once.");
            }
            TokenStreamLocation location = reader.location();
            assignments.add(new AssignmentReference(name, itemPath, line(location), column(location)));
        }
        return assignments;
    }

    /** Resolves references, creates ordered diagnostics, and performs the final aggregate validation. */
    private static Candidate finishCandidate(Path source, ParsedProject parsed) {
        Map<String, String> presetNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SliderPresetSnapshot preset : parsed.presets())
            presetNames.put(preset.getName(), preset.getName());

        List<ProjectDiagnostic> diagnostics = new ArrayList<>();
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>();
        for (RawTarget raw : parsed.targets()) {
            targets.add(new CustomMorphTargetSnapshot(raw.name(),
                    resolveAssignments(source, raw.assignments(), presetNames, diagnostics)));
        }
        targets.sort(Comparator.comparing(CustomMorphTargetSnapshot::getName, CASE_INSENSITIVE_ORDER));

        List<NpcMorphAssignmentSnapshot> npcMorphAssignments = new ArrayList<>();
        for (RawNpcMorphAssignment raw : parsed.npcMorphAssignments()) {
            npcMorphAssignments.add(new NpcMorphAssignmentSnapshot(
                    raw.displayName(), raw.pluginName(), raw.editorId(), raw.race(),
                    raw.formId(), resolveAssignments(source, raw.assignments(), presetNames, diagnostics)));
        }
        npcMorphAssignments.sort((left, right) -> {
            int pluginOrder = left.getPluginName().compareToIgnoreCase(right.getPluginName());
            return pluginOrder != 0 ? pluginOrder : left.getEditorId().compareToIgnoreCase(right.getEditorId());
        });

        boolean recovered = !diagnostics.isEmpty();
        ProjectSnapshot assembled = new ProjectSnapshot(
                parsed.presets(), targets, npcMorphAssignments, Optional.of(source), recovered,
                recovered ? ProjectLifecycleStatus.RECOVERED : ProjectLifecycleStatus.FILE_BACKED);
        try {
            Project validated = Project.from(assembled);
            ProjectSnapshot canonical = validated.toSnapshot(Optional.of(source), recovered,
                    recovered ? ProjectLifecycleStatus.RECOVERED : ProjectLifecycleStatus.FILE_BACKED);
            return new Candidate(canonical, diagnostics);
        } catch (IllegalArgumentException exception) {
            throw failure(ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, source.toString(), "/", 0, 0,
                    exception.getMessage());
        }
    }

    /** Resolves canonical names and records missing references as ordered recovery diagnostics. */
    private static List<String> resolveAssignments(Path source, List<AssignmentReference> rawAssignments,
            Map<String, String> presetNames, List<ProjectDiagnostic> diagnostics) {
        List<String> resolved = new ArrayList<>();
        for (AssignmentReference assignment : rawAssignments) {
            String canonical = presetNames.get(assignment.name().trim());
            if (canonical != null) {
                resolved.add(canonical);
            } else {
                SourceLocation location = new SourceLocation(Optional.of(source), Optional.of(assignment.path()),
                        optionalCoordinate(assignment.line()), optionalCoordinate(assignment.column()));
                diagnostics.add(new ProjectDiagnostic(ProjectDiagnosticCodes.SLIDER_PRESET_ASSIGNMENT_MISSING,
                        DiagnosticSeverity.WARNING, location,
                        "Slider Preset assignment '" + assignment.name() + "' does not exist and was omitted."));
            }
        }
        resolved.sort(CASE_INSENSITIVE_ORDER);
        return resolved;
    }

    /** Writes all three Project catalogs in deterministic root order. */
    private static void writeProject(JsonGenerator generator, Project project) {
        generator.writeStartObject();
        generator.writeName("SliderPresets");
        writeSliderPresets(generator, project.getSliderPresets());
        generator.writeName("CustomMorphTargets");
        writeTargets(generator, project.getCustomMorphTargets());
        generator.writeName("MorphedNPCs");
        writeNpcMorphAssignments(generator, project.getNpcMorphAssignments());
        generator.writeEndObject();
    }

    /** Writes Slider Presets while preserving explicit nulls and omission of unchanged synthesized defaults. */
    private static void writeSliderPresets(JsonGenerator generator, List<SliderPresetSnapshot> presets) {
        generator.writeStartObject();
        for (SliderPresetSnapshot preset : presets) {
            generator.writeName(preset.getName());
            generator.writeStartObject();
            generator.writeBooleanProperty("isUUNP", preset.isUunp());
            generator.writeName("SetSliders");
            generator.writeStartArray();
            for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
                if (!isOmittedMissingDefault(choice))
                    writeSliderChoice(generator, choice);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    /** Writes one explicit Slider Choice in the established fixed-field order. */
    private static void writeSliderChoice(JsonGenerator generator, SliderChoiceSnapshot choice) {
        generator.writeStartObject();
        generator.writeStringProperty("name", choice.getName());
        generator.writeBooleanProperty("enabled", choice.isEnabled());
        if (choice.getStoredSmallValue().isPresent())
            generator.writeNumberProperty("valueSmall", choice.getStoredSmallValue().getAsInt());
        else
            generator.writeNullProperty("valueSmall");
        if (choice.getStoredBigValue().isPresent())
            generator.writeNumberProperty("valueBig", choice.getStoredBigValue().getAsInt());
        else
            generator.writeNullProperty("valueBig");
        generator.writeNumberProperty("pctMin", choice.getPercentageMinimum());
        generator.writeNumberProperty("pctMax", choice.getPercentageMaximum());
        generator.writeEndObject();
    }

    /** Reports whether a synthesized default still has the intentionally omitted persisted state. */
    private static boolean isOmittedMissingDefault(SliderChoiceSnapshot choice) {
        return choice.isMissingDefault() && choice.isEnabled()
                && choice.getPercentageMinimum() == 100 && choice.getPercentageMaximum() == 100;
    }

    /** Writes canonical Custom Morph Targets and their canonical assignment names. */
    private static void writeTargets(JsonGenerator generator, List<CustomMorphTargetSnapshot> targets) {
        generator.writeStartObject();
        for (CustomMorphTargetSnapshot target : targets) {
            generator.writeName(target.getName());
            generator.writeStartObject();
            generator.writeName("SliderPresets");
            writeAssignments(generator, target.getSliderPresetNames());
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    /** Writes NPC Morph Assignments, deliberately retaining repeated display-name members. */
    private static void writeNpcMorphAssignments(
            JsonGenerator generator, List<NpcMorphAssignmentSnapshot> assignments) {
        generator.writeStartObject();
        for (NpcMorphAssignmentSnapshot assignment : assignments) {
            generator.writeName(assignment.getDisplayName());
            generator.writeStartObject();
            generator.writeStringProperty("Mod", assignment.getPluginName());
            generator.writeStringProperty("EditorId", assignment.getEditorId());
            generator.writeStringProperty("Race", assignment.getRace());
            generator.writeStringProperty("FormId", assignment.getFormId());
            generator.writeName("SliderPresets");
            writeAssignments(generator, assignment.getSliderPresetNames());
            generator.writeEndObject();
        }
        generator.writeEndObject();
    }

    /** Writes one deterministic array of canonical Slider Preset names. */
    private static void writeAssignments(JsonGenerator generator, List<String> assignments) {
        generator.writeStartArray();
        for (String assignment : assignments)
            generator.writeString(assignment);
        generator.writeEndArray();
    }

    /** Returns one supported fixed-schema name and rejects unknown or repeated fields. */
    private static String fixedMember(Reader reader, Set<String> seen, Set<String> supported, String ownerPath) {
        String name = reader.propertyName(ownerPath);
        String path = child(ownerPath, name);
        if (!supported.contains(name)) {
            throw reader.failure(ProjectDiagnosticCodes.PROJECT_FIELD_UNSUPPORTED, path,
                    "Project field '" + name + "' is not supported.");
        }
        if (!seen.add(name)) {
            throw reader.failure(ProjectDiagnosticCodes.PROJECT_MEMBER_DUPLICATE, path,
                    "Project field '" + name + "' occurs more than once.");
        }
        return name;
    }

    /** Reads one required string token and enforces the owned UTF-8 token-byte limit. */
    private static String readString(Reader reader, JsonToken token, String path) {
        require(token, JsonToken.VALUE_STRING, ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, reader, path,
                "Project member must be a string.");
        String value = reader.string();
        requireTextLimit(reader, value, path, "string");
        return value;
    }

    /** Reads one required boolean token without coercion. */
    private static boolean readBoolean(Reader reader, JsonToken token, String path) {
        if (token == JsonToken.VALUE_TRUE)
            return true;
        if (token == JsonToken.VALUE_FALSE)
            return false;
        throw reader.failure(ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, path,
                "Project member must be a boolean.");
    }

    /** Reads one exact signed 32-bit integer lexeme without numeric coercion. */
    private static int readInteger(Reader reader, JsonToken token, String path) {
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT) {
            throw reader.failure(ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, path,
                    "Project member must be an integer.");
        }
        String lexeme = reader.string();
        try {
            return Integer.parseInt(lexeme);
        } catch (NumberFormatException exception) {
            throw reader.failure(ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, path,
                    "Project integer must use exact signed 32-bit integer syntax.");
        }
    }

    /** Reads one explicit null or exact signed 32-bit integer lexeme. */
    private static Integer readNullableInteger(Reader reader, JsonToken token, String path) {
        if (token == JsonToken.VALUE_NULL)
            return null;
        return Integer.valueOf(readInteger(reader, token, path));
    }

    /** Rejects absent required members while keeping explicit null distinguishable. */
    private static void requireSeen(boolean seen, Reader reader, String path, String memberName) {
        if (!seen) {
            throw reader.failure(ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, path,
                    "The Project file is missing required member '" + memberName + "'.");
        }
    }

    /** Rejects absent required non-null values after a fixed-schema object closes. */
    private static void requirePresent(Object value, Reader reader, String path, String memberName) {
        if (value == null) {
            throw reader.failure(ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, path,
                    "The Project file is missing required member '" + memberName + "'.");
        }
    }

    /** Requires an exact token kind without enabling Jackson coercion. */
    private static void require(JsonToken actual, JsonToken expected, String code, Reader reader, String path,
            String message) {
        if (actual != expected)
            throw reader.failure(code, path, message);
    }

    /** Enforces the accepted one-MiB UTF-8 limit for names and string values. */
    private static void requireTextLimit(Reader reader, String value, String path, String kind) {
        if (JacksonJson.exceedsTextLimit(value)) {
            throw reader.failure(RESOURCE_LIMIT_CODE, path,
                    "Project " + kind + " exceeds the 1 MiB UTF-8 limit.");
        }
    }

    /** Appends one RFC 6901-escaped member segment to a Project path. */
    private static String child(String owner, String name) {
        return JacksonJson.memberPath(owner, name);
    }

    /** Applies the form-ID normalization historically used by Project loading. */
    private static String normalizeFormId(String value) {
        String normalized = value.trim();
        if (normalized.length() > 6)
            normalized = normalized.substring(normalized.length() - 6);
        return normalized.replaceFirst("^0+(?!$)", "");
    }

    /** Returns a present source coordinate only when the parser supplied one. */
    private static OptionalInt optionalCoordinate(int value) {
        return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
    }

    /** Normalizes a Jackson line coordinate to the stable one-based contract. */
    private static int line(TokenStreamLocation location) {
        return location == null ? 0 : Math.max(1, location.getLineNr());
    }

    /** Normalizes a Jackson column coordinate to the stable one-based contract. */
    private static int column(TokenStreamLocation location) {
        return location == null ? 0 : Math.max(1, location.getColumnNr());
    }

    /** Builds a codec-free failure from a parser location. */
    private static ProjectFormatException failure(String code, String source, String path,
            TokenStreamLocation location, String message) {
        return failure(code, source, path, line(location), column(location), message);
    }

    /** Builds a codec-free failure from already normalized fields. */
    private static ProjectFormatException failure(String code, String source, String path, int line, int column,
            String message) {
        return new ProjectFormatException(code, source, path, line, column,
                message == null || message.isBlank() ? "Project JSON processing failed." : message);
    }

    /** Returns a non-empty diagnostic message without retaining the source exception. */
    private static String readableMessage(Throwable exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    /** Complete immutable result of reading one Project candidate. */
    record Candidate(ProjectSnapshot snapshot, List<ProjectDiagnostic> diagnostics) {
        Candidate {
            Objects.requireNonNull(snapshot, "snapshot");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /** Stable adapter failure with no Jackson type or cause in its surface contract. */
    static final class ProjectFormatException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final String source;
        private final String path;
        private final int line;
        private final int column;

        /** Creates one stable Project-format failure. */
        ProjectFormatException(String code, String source, String path, int line, int column, String message) {
            super(message);
            this.code = code;
            this.source = source;
            this.path = path;
            this.line = line;
            this.column = column;
        }

        /** @return stable machine-readable failure code */
        String code() {
            return code;
        }

        /** @return stable source description */
        String source() {
            return source;
        }

        /** @return escaped JSON-pointer-like failure path */
        String path() {
            return path;
        }

        /** @return one-based line, or zero when unavailable */
        int line() {
            return line;
        }

        /** @return one-based column, or zero when unavailable */
        int column() {
            return column;
        }
    }

    /** Parser cursor that retains the best stable path when Jackson reports a failure. */
    private static final class Reader {
        private final JsonParser parser;
        private final String source;
        private String path = "/";

        /**
         * Wraps one parser with the stable source description used for every translated failure.
         *
         * @param parser Jackson cursor contained within this adapter
         * @param source normalized source-file description
         */
        Reader(JsonParser parser, String source) {
            this.parser = parser;
            this.source = source;
        }

        /** Advances the stream after recording the path the next token belongs to. */
        JsonToken next(String requestedPath) {
            path = requestedPath;
            return parser.nextToken();
        }

        /** Returns the current property name after enforcing its UTF-8 byte limit. */
        String propertyName(String ownerPath) {
            String name = parser.currentName();
            requireTextLimit(this, name, child(ownerPath, name), "member name");
            return name;
        }

        /** Returns the current string or numeric token text. */
        String string() {
            return parser.getString();
        }

        /** @return current token location */
        TokenStreamLocation location() {
            return parser.currentTokenLocation();
        }

        /** @return best path for a syntax or limit exception */
        String failurePath() {
            String contextPath = parser.streamReadContext().pathAsPointer().toString();
            return contextPath.isEmpty() ? path : contextPath;
        }

        /** Builds a failure at the parser's current token. */
        ProjectFormatException failure(String code, String requestedPath, String message) {
            return ProjectJacksonAdapter.failure(code, source, requestedPath, location(), message);
        }
    }

    /** Parsed root values before references are resolved. */
    private record ParsedProject(List<SliderPresetSnapshot> presets, List<RawTarget> targets,
            List<RawNpcMorphAssignment> npcMorphAssignments) {
    }

    /** Custom Morph Target value retaining unresolved assignment locations. */
    private record RawTarget(String name, List<AssignmentReference> assignments) {
    }

    /** NPC Morph Assignment value retaining unresolved assignment locations. */
    private record RawNpcMorphAssignment(String displayName, String pluginName, String editorId,
            String race, String formId, List<AssignmentReference> assignments) {
    }

    /** Persisted assignment string plus its naturally available source position. */
    private record AssignmentReference(String name, String path, int line, int column) {
    }

    /** Parsed explicit choice before the containing preset's Settings profile is known. */
    private record RawChoice(String name, boolean enabled, Integer storedSmall, Integer storedBig,
            int percentageMinimum, int percentageMaximum) {
    }

    /** Explicit choices plus their represented identities, used while applying defaults. */
    private record ExplicitChoices(List<RawChoice> choices, Set<String> representedNames) {
        /** Returns explicit choices plus synthesized defaults for the selected Settings profile. */
        List<SliderChoiceSnapshot> withDefaults(boolean uunp) {
            List<SliderChoiceSnapshot> completed = new ArrayList<>();
            for (RawChoice choice : choices) {
                completed.add(new SliderChoiceSnapshot(choice.name(), choice.enabled(), choice.storedSmall(),
                        choice.storedBig(),
                        SliderChoiceDefaults.effectiveSmall(choice.name(), choice.storedSmall(), uunp),
                        SliderChoiceDefaults.effectiveBig(choice.name(), choice.storedBig(), uunp),
                        choice.percentageMinimum(), choice.percentageMaximum(), false));
            }
            completed.addAll(SliderChoiceDefaults.synthesizeMissing(representedNames, uunp));
            completed.sort(Comparator.comparing(SliderChoiceSnapshot::getName, CASE_INSENSITIVE_ORDER));
            return Collections.unmodifiableList(completed);
        }
    }
}
