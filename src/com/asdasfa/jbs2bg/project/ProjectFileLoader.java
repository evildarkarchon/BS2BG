package com.asdasfa.jbs2bg.project;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.mozilla.universalchardet.UniversalDetector;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonObject.Member;
import com.eclipsesource.json.JsonValue;

/**
 * Parses legacy .jbs2bg files into detached immutable candidate state. No active
 * Project state is touched until the caller publishes the completed result.
 */
final class ProjectFileLoader {

    private static final Comparator<String> CASE_INSENSITIVE_ORDER = new Comparator<String>() {
        @Override
        public int compare(String left, String right) {
            return left.compareToIgnoreCase(right);
        }
    };

    private ProjectFileLoader() {
    }

    /**
     * Reads and parses a complete candidate Project from the production filesystem.
     *
     * @param source legacy Project path
     * @return detached loaded Project ready for atomic publication
     * @throws IOException when the source cannot be detected or read
     * @throws com.eclipsesource.json.ParseException when the JSON syntax is malformed
     * @throws InvalidProjectFileException when the parsed document violates the schema or domain rules
     */
    static LoadedProject load(Path source) throws IOException {
        Charset charset = detectCharset(source);
        String content = new String(Files.readAllBytes(source), charset);
        JsonValue document = Json.parse(content);
        if (!document.isObject())
            throw invalidStructure("/", "The Project document root must be an object.");
        JsonObject root = document.asObject();
        validateSupportedMembers(root, "/", "SliderPresets", "CustomMorphTargets", "MorphedNPCs");
        Map<String, String> presetNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<ProjectDiagnostic> diagnostics = new ArrayList<>();
        List<SliderPresetSnapshot> presets = parseSliderPresets(requiredObject(root, "SliderPresets", "/"),
                presetNames);
        List<CustomMorphTargetSnapshot> targets = parseCustomMorphTargets(requiredObject(root,
                "CustomMorphTargets", "/"), presetNames, source, diagnostics);
        List<NpcMorphAssignmentSnapshot> npcs = parseNpcMorphAssignments(requiredObject(root, "MorphedNPCs", "/"),
                presetNames, source, diagnostics);
        boolean recovered = !diagnostics.isEmpty();
        ProjectSnapshot snapshot = new ProjectSnapshot(presets, targets, npcs, Optional.of(source), recovered,
                recovered ? ProjectLifecycleStatus.RECOVERED : ProjectLifecycleStatus.FILE_BACKED);
        return new LoadedProject(snapshot, diagnostics);
    }

    /**
     * Detects the legacy input encoding, falling back to UTF-8 for ASCII files.
     *
     * @param source Project file whose bytes determine the charset
     * @return detected or fallback charset used for the subsequent read
     * @throws IOException when the source cannot be inspected
     */
    private static Charset detectCharset(Path source) throws IOException {
        String detected = UniversalDetector.detectCharset(source.toFile());
        return detected == null ? StandardCharsets.UTF_8 : Charset.forName(detected);
    }

    /**
     * Returns one required object member or rejects the candidate structure.
     *
     * @param owner containing JSON object
     * @param memberName required member name
     * @param ownerElement stable location of the containing object
     * @return required object value
     * @throws InvalidProjectFileException when the member is absent or not an object
     */
    private static JsonObject requiredObject(JsonObject owner, String memberName, String ownerElement) {
        String element = childElement(ownerElement, memberName);
        JsonValue value = owner.get(memberName);
        if (value == null)
            throw invalidStructure(element, "The Project file is missing required object member '" + memberName
                    + "'.");
        if (!value.isObject())
            throw invalidStructure(element, "Project member '" + memberName + "' must be an object.");
        return value.asObject();
    }

    /**
     * Returns one required array member or rejects its missing/wrong value.
     *
     * @param owner containing JSON object
     * @param memberName required member name
     * @param ownerElement stable location of the containing object
     * @return required array value
     * @throws InvalidProjectFileException when the member is absent or not an array
     */
    private static JsonArray requiredArray(JsonObject owner, String memberName, String ownerElement) {
        String element = childElement(ownerElement, memberName);
        JsonValue value = owner.get(memberName);
        if (value == null)
            throw invalidStructure(element, "The Project file is missing required array member '" + memberName
                    + "'.");
        if (!value.isArray())
            throw invalidValue(element, "Project member '" + memberName + "' must be an array.");
        return value.asArray();
    }

    /**
     * Returns one required string member or rejects its missing/wrong value.
     *
     * @param owner containing JSON object
     * @param memberName required member name
     * @param ownerElement stable location of the containing object
     * @return required string value, including an allowed empty string
     * @throws InvalidProjectFileException when the member is absent or not a string
     */
    private static String requiredString(JsonObject owner, String memberName, String ownerElement) {
        String element = childElement(ownerElement, memberName);
        JsonValue value = owner.get(memberName);
        if (value == null)
            throw invalidStructure(element, "The Project file is missing required string member '" + memberName
                    + "'.");
        if (!value.isString())
            throw invalidValue(element, "Project member '" + memberName + "' must be a string.");
        return value.asString();
    }

    /**
     * Returns one required integer member or rejects non-integral/out-of-range values.
     *
     * @param owner containing JSON object
     * @param memberName required member name
     * @param ownerElement stable location of the containing object
     * @return range-safe integer value
     * @throws InvalidProjectFileException when the member is absent or not a Java integer
     */
    private static int requiredInteger(JsonObject owner, String memberName, String ownerElement) {
        String element = childElement(ownerElement, memberName);
        JsonValue value = owner.get(memberName);
        if (value == null)
            throw invalidStructure(element, "The Project file is missing required integer member '" + memberName
                    + "'.");
        if (!value.isNumber())
            throw invalidValue(element, "Project member '" + memberName + "' must be an integer.");
        try {
            return value.asInt();
        } catch (NumberFormatException exception) {
            throw invalidValue(element, "Project member '" + memberName + "' must be a range-safe integer.");
        }
    }

    /**
     * Returns one required nullable-integer member with strict numeric typing.
     *
     * @param owner containing JSON object
     * @param memberName required member name
     * @param ownerElement stable location of the containing object
     * @return stored integer, or null when the persisted value uses Slider defaults
     * @throws InvalidProjectFileException when absent or neither null nor a Java integer
     */
    private static Integer requiredNullableInteger(JsonObject owner, String memberName, String ownerElement) {
        String element = childElement(ownerElement, memberName);
        JsonValue value = owner.get(memberName);
        if (value == null)
            throw invalidStructure(element, "The Project file is missing required nullable integer member '"
                    + memberName + "'.");
        if (value.isNull())
            return null;
        if (!value.isNumber())
            throw invalidValue(element, "Project member '" + memberName + "' must be an integer or null.");
        try {
            return Integer.valueOf(value.asInt());
        } catch (NumberFormatException exception) {
            throw invalidValue(element, "Project member '" + memberName
                    + "' must be null or a range-safe integer.");
        }
    }

    /**
     * Requires an object-valued catalog entry at its already resolved location.
     *
     * @param value catalog member value
     * @param element stable location of the catalog entry
     * @param description readable entry kind for diagnostics
     * @return validated object value
     * @throws InvalidProjectFileException when the entry is not an object
     */
    private static JsonObject requiredObjectValue(JsonValue value, String element, String description) {
        if (!value.isObject())
            throw invalidValue(element, description + " must be an object.");
        return value.asObject();
    }

    /**
     * Appends an escaped member segment to a stable JSON-pointer-like location.
     *
     * @param ownerElement parent location, using slash for the document root
     * @param memberName raw JSON member name
     * @return child location with RFC 6901-style tilde and slash escaping
     */
    private static String childElement(String ownerElement, String memberName) {
        String escaped = memberName.replace("~", "~0").replace("/", "~1");
        return "/".equals(ownerElement) ? ownerElement + escaped : ownerElement + "/" + escaped;
    }

    /**
     * Creates a structured candidate-shape rejection.
     *
     * @param element invalid Project location
     * @param message readable validation failure
     * @return exception carrying the public structure diagnostic contract
     */
    private static InvalidProjectFileException invalidStructure(String element, String message) {
        return new InvalidProjectFileException(ProjectDiagnosticCodes.PROJECT_STRUCTURE_INVALID, element, message);
    }

    /**
     * Returns one required boolean member or rejects its missing/wrong value.
     *
     * @param owner containing JSON object
     * @param memberName required member name
     * @param ownerElement stable location of the containing object
     * @return required boolean value
     * @throws InvalidProjectFileException when the member is absent or not boolean
     */
    private static boolean requiredBoolean(JsonObject owner, String memberName, String ownerElement) {
        String element = childElement(ownerElement, memberName);
        JsonValue value = owner.get(memberName);
        if (value == null)
            throw invalidStructure(element, "The Project file is missing required boolean member '" + memberName
                    + "'.");
        if (!value.isBoolean())
            throw invalidValue(element, "Project member '" + memberName + "' must be a boolean.");
        return value.asBoolean();
    }

    /**
     * Creates a structured wrong-value-type rejection.
     *
     * @param element invalid Project location
     * @param message readable validation failure
     * @return exception carrying the public value-type diagnostic contract
     */
    private static InvalidProjectFileException invalidValue(String element, String message) {
        return new InvalidProjectFileException(ProjectDiagnosticCodes.PROJECT_VALUE_TYPE_INVALID, element, message);
    }

    /**
     * Rejects the first unsupported or exactly repeated object member.
     *
     * @param object object whose complete member set is validated
     * @param ownerElement stable location of the object
     * @param supportedNames exact legacy field-name allowlist
     * @throws InvalidProjectFileException on an unsupported or duplicate field
     */
    private static void validateSupportedMembers(JsonObject object, String ownerElement, String... supportedNames) {
        Set<String> supported = new HashSet<>(Arrays.asList(supportedNames));
        Set<String> seen = new HashSet<>();
        for (Member member : object) {
            String element = childElement(ownerElement, member.getName());
            if (!supported.contains(member.getName())) {
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.PROJECT_FIELD_UNSUPPORTED, element,
                        "Project field '" + member.getName() + "' is not supported.");
            }
            if (!seen.add(member.getName()))
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.PROJECT_MEMBER_DUPLICATE, element,
                        "Project field '" + member.getName() + "' occurs more than once.");
        }
    }

    /**
     * Parses and canonically orders the legacy Slider Preset catalog.
     *
     * @param object persisted Slider Preset map
     * @param presetNames destination identity-to-canonical-name index
     * @return complete immutable Slider Preset values in canonical order
     * @throws InvalidProjectFileException on invalid names, fields, values, or choices
     */
    private static List<SliderPresetSnapshot> parseSliderPresets(JsonObject object,
            Map<String, String> presetNames) {
        List<SliderPresetSnapshot> presets = new ArrayList<>();
        for (Member member : object) {
            String name = member.getName().trim();
            String presetElement = childElement("/SliderPresets", member.getName());
            if (name.isEmpty())
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_REQUIRED,
                        presetElement, "A Slider Preset name must not be empty.");
            if (name.indexOf('.') >= 0)
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_CONTAINS_DOT,
                        presetElement, "A Slider Preset name must not contain dots.");
            if (presetNames.containsKey(name))
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.SLIDER_PRESET_NAME_DUPLICATE,
                        presetElement, "A Slider Preset with this name already exists in the Project file.");
            JsonObject value = requiredObjectValue(member.getValue(), presetElement, "A Slider Preset");
            validateSupportedMembers(value, presetElement, "isUUNP", "SetSliders");
            boolean uunp = requiredBoolean(value, "isUUNP", presetElement);
            String choicesElement = childElement(presetElement, "SetSliders");
            List<SliderChoiceSnapshot> choices = parseSliderChoices(requiredArray(value, "SetSliders", presetElement),
                    choicesElement, uunp);
            presets.add(new SliderPresetSnapshot(name, uunp, choices));
            presetNames.put(name, name);
        }
        Collections.sort(presets, new Comparator<SliderPresetSnapshot>() {
            @Override
            public int compare(SliderPresetSnapshot left, SliderPresetSnapshot right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return presets;
    }

    /**
     * Parses explicit slider choices and synthesizes configured missing defaults.
     *
     * @param array persisted slider-choice values
     * @param choicesElement stable location of the choice array
     * @param uunp whether UUNP Slider settings provide effective defaults
     * @return complete choices in canonical case-insensitive order
     * @throws InvalidProjectFileException on invalid fields, values, or duplicate names
     */
    private static List<SliderChoiceSnapshot> parseSliderChoices(JsonArray array, String choicesElement,
            boolean uunp) {
        List<SliderChoiceSnapshot> choices = new ArrayList<>();
        Set<String> representedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int index = 0; index < array.size(); index++) {
            String choiceElement = choicesElement + "/" + index;
            JsonObject value = requiredObjectValue(array.get(index), choiceElement, "A slider choice");
            validateSupportedMembers(value, choiceElement, "name", "enabled", "valueSmall", "valueBig", "pctMin",
                    "pctMax");
            String name = requiredString(value, "name", choiceElement);
            if (name.trim().isEmpty())
                throw invalidStructure(childElement(choiceElement, "name"),
                        "A slider-choice name must not be empty.");
            if (!representedNames.add(name))
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.SLIDER_CHOICE_NAME_DUPLICATE,
                        childElement(choiceElement, "name"),
                        "A Slider Preset cannot contain duplicate slider-choice names.");
            Integer storedSmall = requiredNullableInteger(value, "valueSmall", choiceElement);
            Integer storedBig = requiredNullableInteger(value, "valueBig", choiceElement);
            choices.add(new SliderChoiceSnapshot(name, requiredBoolean(value, "enabled", choiceElement),
                    storedSmall, storedBig,
                    storedSmall == null ? defaultSmall(name, uunp) : storedSmall.intValue(),
                    storedBig == null ? defaultBig(name, uunp) : storedBig.intValue(),
                    requiredInteger(value, "pctMin", choiceElement), requiredInteger(value, "pctMax", choiceElement),
                    storedSmall == null && storedBig == null));
        }
        Map<String, DefaultSliderValue> defaults = uunp ? Settings.getDefaultsMapUUNP() : Settings.getDefaultsMap();
        for (Map.Entry<String, DefaultSliderValue> entry : defaults.entrySet()) {
            if (!representedNames.contains(entry.getKey())) {
                choices.add(new SliderChoiceSnapshot(entry.getKey(), true, null, null,
                        (int) (entry.getValue().getValueSmall() * 100),
                        (int) (entry.getValue().getValueBig() * 100), 100, 100, true));
            }
        }
        Collections.sort(choices, new Comparator<SliderChoiceSnapshot>() {
            @Override
            public int compare(SliderChoiceSnapshot left, SliderChoiceSnapshot right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return choices;
    }

    /**
     * Resolves an effective small value from the active Slider settings.
     *
     * @param name slider name used by settings lookup
     * @param uunp whether the UUNP defaults apply
     * @return configured small value as a percentage, or zero when absent
     */
    private static int defaultSmall(String name, boolean uunp) {
        return uunp ? Settings.getDefaultValueSmallUUNP(name) : Settings.getDefaultValueSmall(name);
    }

    /**
     * Resolves an effective big value from the active Slider settings.
     *
     * @param name slider name used by settings lookup
     * @param uunp whether the UUNP defaults apply
     * @return configured big value as a percentage, or zero when absent
     */
    private static int defaultBig(String name, boolean uunp) {
        return uunp ? Settings.getDefaultValueBigUUNP(name) : Settings.getDefaultValueBig(name);
    }

    /**
     * Parses Custom Morph Targets and resolves their assignment references.
     *
     * @param object persisted Custom Morph Target map
     * @param presetNames validated Slider Preset identity index
     * @param source Project source used in recovery locations
     * @param diagnostics destination for recoverable missing-reference warnings
     * @return complete targets in canonical order
     * @throws InvalidProjectFileException on any non-recoverable target problem
     */
    private static List<CustomMorphTargetSnapshot> parseCustomMorphTargets(JsonObject object,
            Map<String, String> presetNames, Path source, List<ProjectDiagnostic> diagnostics) {
        List<CustomMorphTargetSnapshot> targets = new ArrayList<>();
        Set<String> targetNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Member member : object) {
            String targetElement = childElement("/CustomMorphTargets", member.getName());
            String name = member.getName().trim();
            if (name.isEmpty())
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_REQUIRED,
                        targetElement, "A Custom Morph Target name must not be empty.");
            if (!targetNames.add(name))
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.CUSTOM_MORPH_TARGET_NAME_DUPLICATE,
                        targetElement, "A Custom Morph Target with this name already exists in the Project file.");
            JsonObject value = requiredObjectValue(member.getValue(), targetElement, "A Custom Morph Target");
            validateSupportedMembers(value, targetElement, "SliderPresets");
            String assignmentsElement = childElement(targetElement, "SliderPresets");
            JsonArray assignments = requiredArray(value, "SliderPresets", targetElement);
            targets.add(new CustomMorphTargetSnapshot(name,
                    resolveAssignments(assignments, assignmentsElement, presetNames, source, diagnostics)));
        }
        Collections.sort(targets, new Comparator<CustomMorphTargetSnapshot>() {
            @Override
            public int compare(CustomMorphTargetSnapshot left, CustomMorphTargetSnapshot right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return targets;
    }

    /**
     * Parses NPC Morph Assignments and their detached persisted values.
     *
     * @param object persisted NPC Morph Assignment map
     * @param presetNames validated Slider Preset identity index
     * @param source Project source used in recovery locations
     * @param diagnostics destination for recoverable missing-reference warnings
     * @return complete assignments in canonical identity order
     * @throws InvalidProjectFileException on any non-recoverable NPC problem
     */
    private static List<NpcMorphAssignmentSnapshot> parseNpcMorphAssignments(JsonObject object,
            Map<String, String> presetNames, Path source, List<ProjectDiagnostic> diagnostics) {
        List<NpcMorphAssignmentSnapshot> npcs = new ArrayList<>();
        Set<NpcMorphAssignmentIdentity> npcIdentities = new HashSet<>();
        Set<String> displayNameMembers = new HashSet<>();
        for (Member member : object) {
            String npcElement = childElement("/MorphedNPCs", member.getName());
            if (!displayNameMembers.add(member.getName()))
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.PROJECT_MEMBER_DUPLICATE, npcElement,
                        "NPC Morph Assignment member '" + member.getName() + "' occurs more than once.");
            JsonObject value = requiredObjectValue(member.getValue(), npcElement, "An NPC Morph Assignment");
            validateSupportedMembers(value, npcElement, "Mod", "EditorId", "Race", "FormId", "SliderPresets");
            String pluginName = requiredString(value, "Mod", npcElement);
            String editorId = requiredString(value, "EditorId", npcElement);
            NpcMorphAssignmentIdentity npcIdentity = new NpcMorphAssignmentIdentity(pluginName, editorId);
            if (!npcIdentities.add(npcIdentity))
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.NPC_MORPH_ASSIGNMENT_DUPLICATE,
                        npcElement, "An NPC Morph Assignment with this plugin and editor ID already exists.");
            String assignmentsElement = childElement(npcElement, "SliderPresets");
            npcs.add(new NpcMorphAssignmentSnapshot(member.getName(), pluginName, editorId,
                    requiredString(value, "Race", npcElement),
                    normalizeFormId(requiredString(value, "FormId", npcElement)),
                    resolveAssignments(requiredArray(value, "SliderPresets", npcElement), assignmentsElement,
                            presetNames, source, diagnostics)));
        }
        Collections.sort(npcs, new Comparator<NpcMorphAssignmentSnapshot>() {
            @Override
            public int compare(NpcMorphAssignmentSnapshot left, NpcMorphAssignmentSnapshot right) {
                int pluginOrder = left.getPluginName().compareToIgnoreCase(right.getPluginName());
                return pluginOrder != 0 ? pluginOrder : left.getEditorId().compareToIgnoreCase(right.getEditorId());
            }
        });
        return npcs;
    }

    /**
     * Resolves legacy assignment strings to canonical Slider Preset names.
     *
     * @param array persisted assignment strings
     * @param assignmentsElement stable location of the assignment array
     * @param presetNames validated Slider Preset identity index
     * @param source Project source used in recovery locations
     * @param diagnostics destination for missing-reference warnings
     * @return valid canonical names in deterministic order
     * @throws InvalidProjectFileException on wrong types or duplicate references
     */
    private static List<String> resolveAssignments(JsonArray array, String assignmentsElement,
            Map<String, String> presetNames, Path source, List<ProjectDiagnostic> diagnostics) {
        List<String> assignments = new ArrayList<>();
        Set<String> representedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int index = 0; index < array.size(); index++) {
            JsonValue value = array.get(index);
            String assignmentElement = assignmentsElement + "/" + index;
            if (!value.isString())
                throw invalidValue(assignmentElement, "A Slider Preset assignment must be a string.");
            String assignmentName = value.asString();
            String normalizedAssignmentName = assignmentName.trim();
            if (!representedNames.add(normalizedAssignmentName))
                throw new InvalidProjectFileException(ProjectDiagnosticCodes.SLIDER_PRESET_ASSIGNMENT_DUPLICATE,
                        assignmentElement, "A Slider Preset assignment occurs more than once.");
            String resolved = presetNames.get(normalizedAssignmentName);
            if (resolved != null)
                assignments.add(resolved);
            else
                diagnostics.add(missingAssignment(source, assignmentElement, assignmentName));
        }
        Collections.sort(assignments, CASE_INSENSITIVE_ORDER);
        return assignments;
    }

    /**
     * Builds one recoverable missing-assignment warning at its array location.
     *
     * @param source loaded Project path
     * @param element indexed assignment location
     * @param missingName unresolved persisted Slider Preset name
     * @return structured warning explaining the omitted relationship
     */
    private static ProjectDiagnostic missingAssignment(Path source, String element, String missingName) {
        SourceLocation location = new SourceLocation(Optional.of(source), Optional.of(element), OptionalInt.empty(),
                OptionalInt.empty());
        return new ProjectDiagnostic(ProjectDiagnosticCodes.SLIDER_PRESET_ASSIGNMENT_MISSING,
                DiagnosticSeverity.WARNING, location,
                "Slider Preset assignment '" + missingName + "' does not exist and was omitted.");
    }

    /**
     * Applies the form-ID normalization historically used by legacy Project loading.
     *
     * @param value persisted form ID, including an optional mod index and zeroes
     * @return trimmed ID with at most six digits and no redundant leading zeroes
     */
    private static String normalizeFormId(String value) {
        String normalized = value.trim();
        if (normalized.length() > 6)
            normalized = normalized.substring(normalized.length() - 6);
        return normalized.replaceFirst("^0+(?!$)", "");
    }

    /** Complete detached result of parsing one candidate Project. */
    static final class LoadedProject {
        private final ProjectSnapshot snapshot;
        private final List<ProjectDiagnostic> diagnostics;

        /**
         * Creates one immutable candidate load result.
         *
         * @param snapshot complete candidate snapshot
         * @param diagnostics ordered recovery warnings, if any
         */
        LoadedProject(ProjectSnapshot snapshot, List<ProjectDiagnostic> diagnostics) {
            this.snapshot = snapshot;
            this.diagnostics = diagnostics;
        }

        /** @return the complete candidate Project snapshot */
        ProjectSnapshot getSnapshot() {
            return snapshot;
        }

        /** @return structured candidate diagnostics */
        List<ProjectDiagnostic> getDiagnostics() {
            return diagnostics;
        }
    }

    /** Structured internal validation failure converted to a public diagnostic. */
    static final class InvalidProjectFileException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String code;
        private final String element;

        /**
         * Creates one validation failure at a stable Project element.
         *
         * @param code stable public diagnostic code
         * @param element JSON-pointer-like invalid location
         * @param message readable validation failure
         */
        InvalidProjectFileException(String code, String element, String message) {
            super(message);
            this.code = code;
            this.element = element;
        }

        /** @return stable public diagnostic code */
        String getCode() {
            return code;
        }

        /** @return JSON-pointer-like Project element */
        String getElement() {
            return element;
        }
    }
}
