package com.asdasfa.jbs2bg.project;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Immutable values for one BodySlide slider choice in a Slider Preset.
 */
public final class SliderChoiceSnapshot {

	private final String name;
	private final boolean enabled;
	private final Integer storedSmallValue;
	private final Integer storedBigValue;
	private final int effectiveSmallValue;
	private final int effectiveBigValue;
	private final int percentageMinimum;
	private final int percentageMaximum;
	private final boolean missingDefault;

	/**
	 * Creates a slider-choice snapshot. Nullable stored values preserve the legacy
	 * distinction between persisted choices and synthesized defaults.
	 *
	 * <p>A synthesized default ({@code missingDefault == true}) never carries a
	 * stored value; its effective values come from the Slider settings alone. The
	 * reverse is intentionally not enforced: a legacy file may persist an explicit
	 * choice whose stored values are both null, and that choice must stay explicit
	 * so it is serialized rather than omitted on save.
	 *
	 * @param name slider name
	 * @param enabled whether the slider participates in output
	 * @param storedSmallValue persisted small value, or null when synthesized
	 * @param storedBigValue persisted big value, or null when synthesized
	 * @param effectiveSmallValue effective small value after defaults
	 * @param effectiveBigValue effective big value after defaults
	 * @param percentageMinimum lower randomization percentage
	 * @param percentageMaximum upper randomization percentage
	 * @param missingDefault whether this choice was synthesized from defaults
	 * @throws NullPointerException when name is null
	 * @throws IllegalArgumentException when a synthesized default carries a stored value
	 */
	public SliderChoiceSnapshot(String name, boolean enabled, Integer storedSmallValue, Integer storedBigValue,
			int effectiveSmallValue, int effectiveBigValue, int percentageMinimum, int percentageMaximum,
			boolean missingDefault) {
		this.name = Objects.requireNonNull(name, "name");
		// Rejecting this here, rather than in the session, keeps the contradictory
		// state unrepresentable for every producer (loader, XML parser, UI copies).
		if (missingDefault && (storedSmallValue != null || storedBigValue != null))
			throw new IllegalArgumentException(
					"A synthesized default slider choice cannot carry stored values: " + name);
		this.enabled = enabled;
		this.storedSmallValue = storedSmallValue;
		this.storedBigValue = storedBigValue;
		this.effectiveSmallValue = effectiveSmallValue;
		this.effectiveBigValue = effectiveBigValue;
		this.percentageMinimum = percentageMinimum;
		this.percentageMaximum = percentageMaximum;
		this.missingDefault = missingDefault;
	}

	/** @return the slider name */
	public String getName() {
		return name;
	}

	/** @return true when the slider participates in output */
	public boolean isEnabled() {
		return enabled;
	}

	/** @return the persisted small value, or empty when synthesized */
	public OptionalInt getStoredSmallValue() {
		return storedSmallValue == null ? OptionalInt.empty() : OptionalInt.of(storedSmallValue.intValue());
	}

	/** @return the persisted big value, or empty when synthesized */
	public OptionalInt getStoredBigValue() {
		return storedBigValue == null ? OptionalInt.empty() : OptionalInt.of(storedBigValue.intValue());
	}

	/** @return the effective small value after applying defaults */
	public int getEffectiveSmallValue() {
		return effectiveSmallValue;
	}

	/** @return the effective big value after applying defaults */
	public int getEffectiveBigValue() {
		return effectiveBigValue;
	}

	/** @return the lower randomization percentage */
	public int getPercentageMinimum() {
		return percentageMinimum;
	}

	/** @return the upper randomization percentage */
	public int getPercentageMaximum() {
		return percentageMaximum;
	}

	/** @return true when the choice was synthesized from Slider Preset defaults */
	public boolean isMissingDefault() {
		return missingDefault;
	}

	/**
	 * Returns a copy with the requested participation state while preserving every
	 * stored, effective, percentage, and synthesized-default value.
	 *
	 * @param requestedEnabled whether the copied choice participates in output
	 * @return an immutable copy with the requested enabled state
	 */
	public SliderChoiceSnapshot withEnabled(boolean requestedEnabled) {
		return new SliderChoiceSnapshot(name, requestedEnabled, storedSmallValue, storedBigValue,
				effectiveSmallValue, effectiveBigValue, percentageMinimum, percentageMaximum, missingDefault);
	}

	/**
	 * Returns a copy with a new percentage range while preserving the choice's
	 * source values and synthesized-default identity.
	 *
	 * @param requestedMinimum requested lower randomization percentage
	 * @param requestedMaximum requested upper randomization percentage
	 * @return an immutable copy with the requested percentage range
	 */
	public SliderChoiceSnapshot withPercentageRange(int requestedMinimum, int requestedMaximum) {
		return new SliderChoiceSnapshot(name, enabled, storedSmallValue, storedBigValue, effectiveSmallValue,
				effectiveBigValue, requestedMinimum, requestedMaximum, missingDefault);
	}
}
