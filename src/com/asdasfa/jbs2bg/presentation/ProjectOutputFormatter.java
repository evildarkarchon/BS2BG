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

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.project.CustomMorphTargetSnapshot;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.ProjectSnapshot;
import com.asdasfa.jbs2bg.project.SliderChoiceSnapshot;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.WriterConfig;

/** Generates all Project-derived output from one immutable snapshot without JavaFX state. */
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
	 * @param snapshot immutable Project state to format
	 * @param omitRedundantSliders whether redundant template sliders should be omitted
	 * @return one immutable generated-output value
	 * @throws NullPointerException when snapshot is null
	 */
	public static ProjectGeneratedOutput generate(ProjectSnapshot snapshot, boolean omitRedundantSliders) {
		Objects.requireNonNull(snapshot, "snapshot");
		Map<String, String> templateLines = new LinkedHashMap<>();
		Map<String, String> bosJson = new LinkedHashMap<>();
		StringBuilder templatesText = new StringBuilder();
		String newLine = System.lineSeparator();

		for (SliderPresetSnapshot preset : snapshot.getSliderPresets()) {
			String line = formatTemplateLine(preset, omitRedundantSliders);
			if (templatesText.length() > 0)
				templatesText.append(newLine);
			templatesText.append(line);
			templateLines.put(preset.getName(), line);
			bosJson.put(preset.getName() + ".json", formatBosJson(preset));
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
				bosJson, customWithoutPresets, npcsWithoutPresets);
	}

	/** Formats one legacy templates.ini line from immutable Slider Preset values. */
	private static String formatTemplateLine(SliderPresetSnapshot preset, boolean omitRedundantSliders) {
		List<String> values = new ArrayList<>();
		for (SliderChoiceSnapshot choice : enabledChoices(preset)) {
			if (!omitRedundantSliders || !isRedundant(choice, preset.isUunp()))
				values.add(formatSliderValue(choice, preset.isUunp()));
		}
		return preset.getName() + "=" + String.join(", ", values);
	}

	/** Formats one BoS JSON document while retaining the legacy property order. */
	private static String formatBosJson(SliderPresetSnapshot preset) {
		JsonObject strings = new JsonObject();
		JsonObject integers = new JsonObject();
		JsonObject floats = new JsonObject();
		List<SliderChoiceSnapshot> included = new ArrayList<>();

		strings.add("bodyname", preset.getName());
		for (SliderChoiceSnapshot choice : enabledChoices(preset)) {
			if (!isRedundant(choice, preset.isUunp()))
				included.add(choice);
		}
		for (int index = 0; index < included.size(); index++) {
			strings.add("slidername" + (index + 1), included.get(index).getName());
		}
		for (int index = 0; index < included.size(); index++) {
			SliderChoiceSnapshot choice = included.get(index);
			floats.add("highvalue" + (index + 1),
					formatBosValue(choice.getEffectiveBigValue(), choice.getName(), preset.isUunp()));
		}
		for (int index = 0; index < included.size(); index++) {
			SliderChoiceSnapshot choice = included.get(index);
			floats.add("lowvalue" + (index + 1),
					formatBosValue(choice.getEffectiveSmallValue(), choice.getName(), preset.isUunp()));
		}
		integers.add("slidersnumber", included.size());

		JsonObject root = new JsonObject();
		root.add("string", strings);
		root.add("int", integers);
		root.add("float", floats);
		return root.toString(WriterConfig.PRETTY_PRINT);
	}

	/** Returns enabled explicit and synthesized choices in the legacy name order. */
	private static List<SliderChoiceSnapshot> enabledChoices(SliderPresetSnapshot preset) {
		List<SliderChoiceSnapshot> enabled = new ArrayList<>();
		for (SliderChoiceSnapshot choice : preset.getSliderChoices()) {
			if (choice.isEnabled())
				enabled.add(choice);
		}
		Collections.sort(enabled, SLIDER_NAME_ORDER);
		return enabled;
	}

	/** Formats one BodyGen slider range using the legacy float and rounding behavior. */
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

	/** Converts one effective Slider Preset endpoint into its BoS float value. */
	private static float formatBosValue(int value, String sliderName, boolean uunp) {
		float formatted = value * 0.01f;
		if (isInverted(sliderName, uunp))
			formatted = 1f - formatted;
		return roundLegacy(formatted * multiplier(sliderName, uunp));
	}

	/** Reports whether a choice matches the legacy neutral endpoint for its inversion. */
	private static boolean isRedundant(SliderChoiceSnapshot choice, boolean uunp) {
		int small = choice.getEffectiveSmallValue();
		int big = choice.getEffectiveBigValue();
		int neutral = isInverted(choice.getName(), uunp) ? 100 : 0;
		return small == neutral && small == big;
	}

	/** Resolves the configured inversion family without exposing mutable Settings collections. */
	private static boolean isInverted(String sliderName, boolean uunp) {
		return uunp ? Settings.isInvertedUUNP(sliderName) : Settings.isInverted(sliderName);
	}

	/** Resolves the configured output multiplier for the Slider Preset family. */
	private static float multiplier(String sliderName, boolean uunp) {
		return uunp ? Settings.getMultiplierUUNP(sliderName) : Settings.getMultiplier(sliderName);
	}

	/** Preserves the legacy two-decimal, half-up rounding performed through float text. */
	private static float roundLegacy(float value) {
		return new BigDecimal(Float.toString(value)).setScale(2, RoundingMode.HALF_UP).floatValue();
	}

	/** Appends one target line and the legacy trailing platform newline. */
	private static void appendMorphLine(StringBuilder output, String identity, List<String> presetNames,
			String newLine) {
		output.append((identity + "=" + String.join("|", presetNames)).trim()).append(newLine);
	}
}
