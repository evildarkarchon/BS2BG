package com.asdasfa.jbs2bg.presentation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

/**
 * Generates all Project-derived output from one immutable snapshot without JavaFX state.
 */
public final class ProjectOutputFormatter {

    private static final Comparator<SliderChoiceSnapshot> SLIDER_NAME_ORDER =
            new Comparator<SliderChoiceSnapshot>() {
                @Override
                public int compare(SliderChoiceSnapshot left, SliderChoiceSnapshot right) {
                    return left.getName().compareToIgnoreCase(right.getName());
                }
            };

    private ProjectOutputFormatter() {
    }

    /**
     * Generates Templates, Morphs, BoS payloads, and no-preset diagnostics from the
     * supplied coherent snapshot. No session or mutable Project state is retained.
     *
     * @param snapshot             immutable Project state to format
     * @param omitRedundantSliders whether redundant template sliders should be omitted
     * @return one immutable generated-output value
     * @throws NullPointerException when snapshot is null
     * @throws BosOutputException   when any BoS filename or calculated value cannot publish safely
     */
    public static ProjectGeneratedOutput generate(ProjectSnapshot snapshot, boolean omitRedundantSliders) {
        Objects.requireNonNull(snapshot, "snapshot");
        List<BosOutputDiagnostic> bosDiagnostics = new ArrayList<>();
        List<BosFileNameMapping> bosFileNameMappings = mapBosFileNames(snapshot.getSliderPresets(), bosDiagnostics);
        // Payload validation continues after filename failures so one preflight report covers the whole command.
        List<BosJsonArtifact> bosJsonArtifacts = generateBosArtifacts(snapshot.getSliderPresets(),
                bosFileNameMappings, bosDiagnostics);
        if (!bosDiagnostics.isEmpty())
            throw new BosOutputException(bosFileNameMappings, bosDiagnostics);
        Map<String, String> templateLines = new LinkedHashMap<>();
        StringBuilder templatesText = new StringBuilder();
        String newLine = System.lineSeparator();

        for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
            String line = formatTemplateLine(preset, omitRedundantSliders);
            if (templatesText.length() > 0)
                templatesText.append(newLine);
            templatesText.append(line);
            templateLines.put(preset.getName(), line);
        }

        List<CustomMorphTargetSnapshot> customWithoutPresets = new ArrayList<>();
        List<NpcMorphAssignmentSnapshot> npcsWithoutPresets = new ArrayList<>();
        StringBuilder morphsText = new StringBuilder();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            appendMorphLine(morphsText, target.getName(), target.getSliderPresetNames(), newLine);
            if (target.getSliderPresetNames().isEmpty())
                customWithoutPresets.add(target);
        }
        for (NpcMorphAssignmentSnapshot npc : snapshot.getNpcMorphAssignments()) {
            appendMorphLine(morphsText, npc.getPluginName() + "|" + npc.getFormId(),
                    npc.getSliderPresetNames(), newLine);
            if (npc.getSliderPresetNames().isEmpty())
                npcsWithoutPresets.add(npc);
        }

        return new ProjectGeneratedOutput(templatesText.toString(), morphsText.toString(), templateLines,
                bosJsonArtifacts, customWithoutPresets, npcsWithoutPresets);
    }

    /**
     * Maps every artifact name and accumulates all case-insensitive collision diagnostics.
     */
    private static List<BosFileNameMapping> mapBosFileNames(List<SliderPresetSnapshot> presets,
                                                            List<BosOutputDiagnostic> diagnostics) {
        List<BosFileNameMapping> mappings = new ArrayList<>();
        Map<String, List<BosFileNameMapping>> mappingsByFileName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SliderPresetSnapshot preset : presets) {
            String fileName = null;
            try {
                fileName = BosFileNamePolicy.map(preset.getName());
            } catch (IllegalArgumentException exception) {
                diagnostics.add(new BosOutputDiagnostic("BOS_FILENAME_UNREPRESENTABLE", preset.getName(),
                        exception.getMessage()));
            }
            BosFileNameMapping mapping = new BosFileNameMapping(preset.getName(), fileName);
            mappings.add(mapping);
            if (fileName != null)
                mappingsByFileName.computeIfAbsent(fileName, ignored -> new ArrayList<>()).add(mapping);
        }

        for (Map.Entry<String, List<BosFileNameMapping>> entry : mappingsByFileName.entrySet()) {
            if (entry.getValue().size() < 2)
                continue;
            String message = "Mapped filename collides without regard to case: " + entry.getKey();
            for (BosFileNameMapping mapping : entry.getValue()) {
                diagnostics.add(new BosOutputDiagnostic("BOS_FILENAME_COLLISION",
                        mapping.getSliderPresetName(), message));
            }
        }
        return mappings;
    }

    /**
     * Validates every payload and creates artifacts only where a safe mapped filename exists, accumulating all
     * rejected Slider Presets into the command-wide diagnostic list.
     */
    private static List<BosJsonArtifact> generateBosArtifacts(List<SliderPresetSnapshot> presets,
                                                              List<BosFileNameMapping> mappings, List<BosOutputDiagnostic> diagnostics) {
        List<BosJsonArtifact> artifacts = new ArrayList<>();
        for (int index = 0; index < presets.size(); index++) {
            SliderPresetSnapshot preset = presets.get(index);
            try {
                Utf8Json json = formatBosJson(preset);
                String fileName = mappings.get(index).getFileName().orElse(null);
                if (fileName != null)
                    artifacts.add(new BosJsonArtifact(preset.getName(), fileName, json));
            } catch (BosValueException exception) {
                diagnostics.add(new BosOutputDiagnostic("BOS_VALUE_NON_FINITE", preset.getName(),
                        exception.getMessage()));
            } catch (IllegalArgumentException exception) {
                diagnostics.add(new BosOutputDiagnostic("BOS_VALUE_UNREPRESENTABLE", preset.getName(),
                        exception.getMessage()));
            }
        }
        return artifacts;
    }

    /**
     * Formats one legacy templates.ini line from immutable Slider Preset values.
     */
    private static String formatTemplateLine(SliderPresetSnapshot preset, boolean omitRedundantSliders) {
        List<String> values = new ArrayList<>();
        for (SliderChoiceSnapshot choice : enabledChoices(preset)) {
            if (!omitRedundantSliders || !isRedundant(choice, preset.isUunp()))
                values.add(formatSliderValue(choice, preset.isUunp()));
        }
        return preset.getName() + "=" + String.join(", ", values);
    }

    /**
     * Formats one BoS JSON payload through the repository-owned canonical writer.
     */
    private static Utf8Json formatBosJson(SliderPresetSnapshot preset) {
        List<SliderChoiceSnapshot> included = new ArrayList<>();
        for (SliderChoiceSnapshot choice : enabledChoices(preset)) {
            if (!isRedundant(choice, preset.isUunp()))
                included.add(choice);
        }

        List<String> sliderNames = new ArrayList<>();
        List<String> highValues = new ArrayList<>();
        List<String> lowValues = new ArrayList<>();
        for (SliderChoiceSnapshot choice : included) {
            sliderNames.add(choice.getName());
            highValues.add(formatBosLexeme(choice.getEffectiveBigValue(), choice.getName(), preset.isUunp()));
            lowValues.add(formatBosLexeme(choice.getEffectiveSmallValue(), choice.getName(), preset.isUunp()));
        }
        return BosJacksonWriter.write(new BosJacksonWriter.BosDocument(
                preset.getName(), sliderNames, highValues, lowValues));
    }

    /**
     * Preserves minimal-json's whole-float spelling while retaining exponent notation.
     */
    private static String formatBosLexeme(int value, String sliderName, boolean uunp) {
        String lexeme = Float.toString(formatBosValue(value, sliderName, uunp));
        return lexeme.endsWith(".0") ? lexeme.substring(0, lexeme.length() - 2) : lexeme;
    }

    /**
     * Returns enabled explicit and synthesized choices in the legacy name order.
     */
    private static List<SliderChoiceSnapshot> enabledChoices(SliderPresetSnapshot preset) {
        List<SliderChoiceSnapshot> enabled = new ArrayList<>();
        for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
            if (choice.isEnabled())
                enabled.add(choice);
        }
        Collections.sort(enabled, SLIDER_NAME_ORDER);
        return enabled;
    }

    /**
     * Formats one BodyGen slider range using the legacy float and rounding behavior.
     */
    private static String formatSliderValue(SliderChoiceSnapshot choice, boolean uunp) {
        float small = choice.getEffectiveSmallValue() * 0.01f;
        float big = choice.getEffectiveBigValue() * 0.01f;
        if (isInverted(choice.getName(), uunp)) {
            small = 1f - small;
            big = 1f - big;
        }
        float difference = big - small;
        float minimum = small + difference * (choice.getPercentageMinimum() * 0.01f);
        float maximum = small + difference * (choice.getPercentageMaximum() * 0.01f);
        float multiplier = multiplier(choice.getName(), uunp);
        minimum = roundLegacy(minimum * multiplier);
        maximum = roundLegacy(maximum * multiplier);
        return choice.getName() + "@" + (minimum != maximum ? minimum + ":" + maximum : Float.toString(maximum));
    }

    /**
     * Converts one effective Slider Preset endpoint into its BoS float value.
     */
    private static float formatBosValue(int value, String sliderName, boolean uunp) {
        float formatted = value * 0.01f;
        if (isInverted(sliderName, uunp))
            formatted = 1f - formatted;
        float multiplied = formatted * multiplier(sliderName, uunp);
        if (!Float.isFinite(multiplied))
            throw new BosValueException("Slider " + BosOutputException.escape(sliderName)
                    + " calculated a non-finite BoS endpoint.");
        return roundLegacy(multiplied);
    }

    /**
     * Reports whether a choice matches the legacy neutral endpoint for its inversion.
     */
    private static boolean isRedundant(SliderChoiceSnapshot choice, boolean uunp) {
        int small = choice.getEffectiveSmallValue();
        int big = choice.getEffectiveBigValue();
        int neutral = isInverted(choice.getName(), uunp) ? 100 : 0;
        return small == neutral && small == big;
    }

    /**
     * Resolves the configured inversion family without exposing mutable Settings collections.
     */
    private static boolean isInverted(String sliderName, boolean uunp) {
        return uunp ? Settings.isInvertedUUNP(sliderName) : Settings.isInverted(sliderName);
    }

    /**
     * Resolves the configured output multiplier for the Slider Preset family.
     */
    private static float multiplier(String sliderName, boolean uunp) {
        return uunp ? Settings.getMultiplierUUNP(sliderName) : Settings.getMultiplier(sliderName);
    }

    /**
     * Preserves the legacy two-decimal, half-up rounding performed through float text.
     */
    private static float roundLegacy(float value) {
        return new BigDecimal(Float.toString(value)).setScale(2, RoundingMode.HALF_UP).floatValue();
    }

    /**
     * Appends one target line and the legacy trailing platform newline.
     */
    private static void appendMorphLine(StringBuilder output, String identity, List<String> presetNames,
                                        String newLine) {
        output.append((identity + "=" + String.join("|", presetNames)).trim()).append(newLine);
    }

    /**
     * Internal marker translated into the stable public BoS diagnostic contract.
     */
    private static final class BosValueException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        /**
         * Creates one non-finite calculation failure without exposing numeric implementation details.
         */
        BosValueException(String message) {
            super(message);
        }
    }
}
