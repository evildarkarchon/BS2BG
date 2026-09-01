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
        return generate(snapshot, Settings.snapshot(), omitRedundantSliders);
    }

    /**
     * Generates every artifact from one explicit immutable Project and Settings basis. This overload prevents an
     * asynchronous command from mixing Settings publications while its captured Project snapshot is formatted.
     *
     * @param snapshot             immutable Project state to format
     * @param settings             immutable paired generation Settings captured with the Project
     * @param omitRedundantSliders whether redundant template sliders should be omitted
     * @return one immutable generated-output value
     * @throws NullPointerException when snapshot or settings is null
     * @throws BosOutputException   when any BoS filename or calculated value cannot publish safely
     */
    public static ProjectGeneratedOutput generate(ProjectSnapshot snapshot, Settings.Snapshot settings,
                                                   boolean omitRedundantSliders) {
        return generate(snapshot, settings, omitRedundantSliders, GenerationContext.nonCancellable());
    }

    /**
     * Generates from one captured basis with cooperative safe points between real output units.
     *
     * @param snapshot             immutable Project state to format
     * @param settings             immutable paired generation Settings
     * @param omitRedundantSliders whether redundant template sliders should be omitted
     * @param context              cancellation and measured-progress receiver retained for this call only
     * @return one immutable generated-output value
     */
    public static ProjectGeneratedOutput generate(ProjectSnapshot snapshot, Settings.Snapshot settings,
                                                   boolean omitRedundantSliders, GenerationContext context) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(context, "context");
        long totalUnits = Math.addExact(Math.multiplyExact(3L, snapshot.getSliderPresets().size()),
                Math.addExact(snapshot.getCustomMorphTargets().size(), snapshot.getNpcMorphAssignments().size()));
        GenerationProgress progress = new GenerationProgress(context, totalUnits);
        List<BosOutputDiagnostic> bosDiagnostics = new ArrayList<>();
        List<BosFileNameMapping> bosFileNameMappings = mapBosFileNames(snapshot.getSliderPresets(), bosDiagnostics,
                progress);
        // Payload validation continues after filename failures so one preflight report covers the whole command.
        List<BosJsonArtifact> bosJsonArtifacts = generateBosArtifacts(snapshot.getSliderPresets(),
                bosFileNameMappings, bosDiagnostics, settings, progress);
        if (!bosDiagnostics.isEmpty())
            throw new BosOutputException(bosFileNameMappings, bosDiagnostics);
        Map<String, String> templateLines = new LinkedHashMap<>();
        StringBuilder templatesText = new StringBuilder();
        String newLine = System.lineSeparator();

        for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
            progress.checkCancellation();
            String line = formatTemplateLine(preset, omitRedundantSliders, settings, progress);
            if (templatesText.length() > 0)
                templatesText.append(newLine);
            templatesText.append(line);
            templateLines.put(preset.getName(), line);
            progress.completedUnit();
        }

        List<CustomMorphTargetSnapshot> customWithoutPresets = new ArrayList<>();
        List<NpcMorphAssignmentSnapshot> npcsWithoutPresets = new ArrayList<>();
        StringBuilder morphsText = new StringBuilder();
        for (CustomMorphTargetSnapshot target : snapshot.getCustomMorphTargets()) {
            progress.checkCancellation();
            appendMorphLine(morphsText, target.getName(), target.getSliderPresetNames(), newLine);
            if (target.getSliderPresetNames().isEmpty())
                customWithoutPresets.add(target);
            progress.completedUnit();
        }
        for (NpcMorphAssignmentSnapshot npc : snapshot.getNpcMorphAssignments()) {
            progress.checkCancellation();
            appendMorphLine(morphsText, npc.getPluginName() + "|" + npc.getFormId(),
                    npc.getSliderPresetNames(), newLine);
            if (npc.getSliderPresetNames().isEmpty())
                npcsWithoutPresets.add(npc);
            progress.completedUnit();
        }

        return new ProjectGeneratedOutput(templatesText.toString(), morphsText.toString(), templateLines,
                bosJsonArtifacts, customWithoutPresets, npcsWithoutPresets);
    }

    /**
     * Maps every artifact name and accumulates all case-insensitive collision diagnostics.
     */
    private static List<BosFileNameMapping> mapBosFileNames(List<SliderPresetSnapshot> presets,
                                                            List<BosOutputDiagnostic> diagnostics,
                                                            GenerationProgress progress) {
        List<BosFileNameMapping> mappings = new ArrayList<>();
        Map<String, List<BosFileNameMapping>> mappingsByFileName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SliderPresetSnapshot preset : presets) {
            progress.checkCancellation();
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
            progress.completedUnit();
        }

        progress.checkCancellation();
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
                                                              List<BosFileNameMapping> mappings,
                                                              List<BosOutputDiagnostic> diagnostics,
                                                              Settings.Snapshot settings,
                                                              GenerationProgress progress) {
        List<BosJsonArtifact> artifacts = new ArrayList<>();
        for (int index = 0; index < presets.size(); index++) {
            progress.checkCancellation();
            SliderPresetSnapshot preset = presets.get(index);
            try {
                Utf8Json json = formatBosJson(preset, settings, progress);
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
            progress.completedUnit();
        }
        return artifacts;
    }

    /**
     * Formats one legacy templates.ini line from immutable Slider Preset values.
     */
    private static String formatTemplateLine(SliderPresetSnapshot preset, boolean omitRedundantSliders,
                                             Settings.Snapshot settings, GenerationProgress progress) {
        List<String> values = new ArrayList<>();
        for (SliderChoiceSnapshot choice : enabledChoices(preset, progress)) {
            progress.checkCancellation();
            if (!omitRedundantSliders || !isSliderChoiceRedundant(choice, preset.isUunp(), settings))
                values.add(formatSliderChoicePreview(choice, preset.isUunp(), settings));
        }
        return preset.getName() + "=" + String.join(", ", values);
    }

    /**
     * Formats one BoS JSON payload through the repository-owned canonical writer.
     */
    private static Utf8Json formatBosJson(SliderPresetSnapshot preset, Settings.Snapshot settings,
                                          GenerationProgress progress) {
        List<SliderChoiceSnapshot> included = new ArrayList<>();
        for (SliderChoiceSnapshot choice : enabledChoices(preset, progress)) {
            progress.checkCancellation();
            if (!isSliderChoiceRedundant(choice, preset.isUunp(), settings))
                included.add(choice);
        }

        List<String> sliderNames = new ArrayList<>();
        List<String> highValues = new ArrayList<>();
        List<String> lowValues = new ArrayList<>();
        for (SliderChoiceSnapshot choice : included) {
            progress.checkCancellation();
            sliderNames.add(choice.getName());
            highValues.add(formatBosLexeme(choice.getEffectiveBigValue(), choice.getName(), preset.isUunp(),
                    settings));
            lowValues.add(formatBosLexeme(choice.getEffectiveSmallValue(), choice.getName(), preset.isUunp(),
                    settings));
        }
        return BosJacksonWriter.write(new BosJacksonWriter.BosDocument(
                preset.getName(), sliderNames, highValues, lowValues));
    }

    /**
     * Preserves minimal-json's whole-float spelling while retaining exponent notation.
     */
    private static String formatBosLexeme(int value, String sliderName, boolean uunp, Settings.Snapshot settings) {
        String lexeme = Float.toString(formatBosValue(value, sliderName, uunp, settings));
        return lexeme.endsWith(".0") ? lexeme.substring(0, lexeme.length() - 2) : lexeme;
    }

    /**
     * Returns enabled explicit and synthesized choices in the legacy name order.
     */
    private static List<SliderChoiceSnapshot> enabledChoices(SliderPresetSnapshot preset,
                                                             GenerationProgress progress) {
        List<SliderChoiceSnapshot> enabled = new ArrayList<>();
        for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
            progress.checkCancellation();
            if (choice.isEnabled())
                enabled.add(choice);
        }
        Collections.sort(enabled, SLIDER_NAME_ORDER);
        return enabled;
    }

    /**
     * Formats one Slider choice exactly as it appears inside a BodyGen Templates line, including configured profile
     * inversion, multiplier, interpolation, float spelling, and legacy half-up rounding.
     *
     * @param choice immutable Slider choice to preview
     * @param uunp   whether the containing Slider Preset uses the UUNP Settings profile
     * @return exact BodyGen choice text without the containing preset name
     * @throws NullPointerException when choice is null
     */
    public static String formatSliderChoicePreview(SliderChoiceSnapshot choice, boolean uunp) {
        return formatSliderChoicePreview(choice, uunp, Settings.snapshot());
    }

    /** Formats one choice from an explicit captured Settings basis. */
    private static String formatSliderChoicePreview(SliderChoiceSnapshot choice, boolean uunp,
                                                    Settings.Snapshot settings) {
        Objects.requireNonNull(choice, "choice");
        float small = choice.getEffectiveSmallValue() * 0.01f;
        float big = choice.getEffectiveBigValue() * 0.01f;
        if (isInverted(choice.getName(), uunp, settings)) {
            small = 1f - small;
            big = 1f - big;
        }
        float difference = big - small;
        float minimum = small + difference * (choice.getPercentageMinimum() * 0.01f);
        float maximum = small + difference * (choice.getPercentageMaximum() * 0.01f);
        float multiplier = multiplier(choice.getName(), uunp, settings);
        minimum = roundLegacy(minimum * multiplier);
        maximum = roundLegacy(maximum * multiplier);
        return choice.getName() + "@" + (minimum != maximum ? minimum + ":" + maximum : Float.toString(maximum));
    }

    /**
     * Converts one effective Slider Preset endpoint into its BoS float value.
     */
    private static float formatBosValue(int value, String sliderName, boolean uunp, Settings.Snapshot settings) {
        float formatted = value * 0.01f;
        if (isInverted(sliderName, uunp, settings))
            formatted = 1f - formatted;
        float multiplied = formatted * multiplier(sliderName, uunp, settings);
        if (!Float.isFinite(multiplied))
            throw new BosValueException("Slider " + BosOutputException.escape(sliderName)
                    + " calculated a non-finite BoS endpoint.");
        return roundLegacy(multiplied);
    }

    /**
     * Reports whether one Slider choice matches the legacy neutral endpoint for its selected profile inversion.
     * Such enabled choices are omitted when Omit Redundant Sliders is selected and are always omitted from BoS.
     *
     * @param choice immutable Slider choice to inspect
     * @param uunp   whether the containing Slider Preset uses the UUNP Settings profile
     * @return whether both effective endpoints equal the profile's neutral endpoint
     * @throws NullPointerException when choice is null
     */
    public static boolean isSliderChoiceRedundant(SliderChoiceSnapshot choice, boolean uunp) {
        return isSliderChoiceRedundant(choice, uunp, Settings.snapshot());
    }

    /** Reports redundancy against an explicit captured Settings basis. */
    private static boolean isSliderChoiceRedundant(SliderChoiceSnapshot choice, boolean uunp,
                                                   Settings.Snapshot settings) {
        Objects.requireNonNull(choice, "choice");
        int small = choice.getEffectiveSmallValue();
        int big = choice.getEffectiveBigValue();
        int neutral = isInverted(choice.getName(), uunp, settings) ? 100 : 0;
        return small == neutral && small == big;
    }

    /**
     * Resolves the configured inversion family without exposing mutable Settings collections.
     */
    private static boolean isInverted(String sliderName, boolean uunp, Settings.Snapshot settings) {
        List<String> inverted = (uunp ? settings.uunp() : settings.standard()).inverted();
        for (String candidate : inverted)
            if (candidate.equalsIgnoreCase(sliderName))
                return true;
        return false;
    }

    /**
     * Resolves the configured output multiplier for the Slider Preset family.
     */
    private static float multiplier(String sliderName, boolean uunp, Settings.Snapshot settings) {
        return (uunp ? settings.uunp() : settings.standard()).multipliers()
                .getOrDefault(sliderName, Float.valueOf(1f)).floatValue();
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

    /** JavaFX-independent cancellation and truthful measured-progress seam for one generation call. */
    public interface GenerationContext {
        /** Returns a context for compatibility callers that need neither cancellation nor progress. */
        static GenerationContext nonCancellable() {
            return new GenerationContext() {
                @Override
                public void checkCancellation() {
                    // Synchronous compatibility generation is intentionally non-cancellable.
                }

                @Override
                public void report(long completedUnits, long totalUnits) {
                    // Synchronous compatibility generation intentionally discards progress.
                }
            };
        }

        /** Throws when cancellation has been accepted at the current ordinary safe point. */
        void checkCancellation();

        /**
         * Reports completed real formatting units.
         *
         * @param completedUnits completed preset/target/NPC formatting units
         * @param totalUnits     total real units in the captured Project
         */
        void report(long completedUnits, long totalUnits);
    }

    /** Counts real generation work while keeping cancellation checks adjacent to each immutable input unit. */
    private static final class GenerationProgress {
        private final GenerationContext context;
        private final long totalUnits;
        private final long reportInterval;
        private long completedUnits;

        /** Captures one context and its precomputed immutable Project work count. */
        private GenerationProgress(GenerationContext context, long totalUnits) {
            this.context = Objects.requireNonNull(context, "context");
            if (totalUnits < 0)
                throw new IllegalArgumentException("totalUnits must not be negative");
            this.totalUnits = totalUnits;
            reportInterval = Math.max(1L, totalUnits / 100L);
        }

        /** Checks cancellation before starting the next indivisible formatter unit. */
        private void checkCancellation() {
            context.checkCancellation();
        }

        /** Reports one completed real unit without inventing work for an empty Project. */
        private void completedUnit() {
            completedUnits++;
            if (totalUnits > 0 && (completedUnits == totalUnits || completedUnits % reportInterval == 0))
                context.report(completedUnits, totalUnits);
        }
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
