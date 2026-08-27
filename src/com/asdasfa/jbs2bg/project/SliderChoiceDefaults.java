package com.asdasfa.jbs2bg.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;

/**
 * Applies the configured Slider settings to slider choices for one body mode:
 * resolves effective values for absent stored endpoints and synthesizes the
 * configured defaults a Slider Preset does not explicitly represent.
 *
 * <p>Project file loading, BodySlide XML import, and UUNP edits all go through
 * this single implementation so every path publishes the same synthesized state
 * and a save/reopen cycle cannot change a Slider Preset's meaning.
 */
final class SliderChoiceDefaults {

    private static final Comparator<SliderChoiceSnapshot> NAME_ORDER = new Comparator<SliderChoiceSnapshot>() {
        @Override
        public int compare(SliderChoiceSnapshot left, SliderChoiceSnapshot right) {
            return left.getName().compareToIgnoreCase(right.getName());
        }
    };

    private SliderChoiceDefaults() {
    }

    /**
     * Resolves the effective small value, deferring to the mode's Slider settings
     * when no value is stored.
     *
     * @param name slider name used by settings lookup
     * @param stored persisted small value, or null to use the configured default
     * @param uunp whether the UUNP defaults apply
     * @return stored value, configured default as a percentage, or zero when unconfigured
     */
    static int effectiveSmall(String name, Integer stored, boolean uunp) {
        if (stored != null)
            return stored.intValue();
        return uunp ? Settings.getDefaultValueSmallUUNP(name) : Settings.getDefaultValueSmall(name);
    }

    /**
     * Resolves the effective big value, deferring to the mode's Slider settings
     * when no value is stored.
     *
     * @param name slider name used by settings lookup
     * @param stored persisted big value, or null to use the configured default
     * @param uunp whether the UUNP defaults apply
     * @return stored value, configured default as a percentage, or zero when unconfigured
     */
    static int effectiveBig(String name, Integer stored, boolean uunp) {
        if (stored != null)
            return stored.intValue();
        return uunp ? Settings.getDefaultValueBigUUNP(name) : Settings.getDefaultValueBig(name);
    }

    /**
     * Synthesizes an enabled, all-default choice for every configured default of the
     * requested mode whose name is not already represented.
     *
     * @param representedNames slider names already present, compared without regard to case
     * @param uunp whether the UUNP defaults apply
     * @return synthesized choices in settings order; callers sort as needed
     */
    static List<SliderChoiceSnapshot> synthesizeMissing(Set<String> representedNames, boolean uunp) {
        Map<String, DefaultSliderValue> defaults = uunp ? Settings.getDefaultsMapUUNP() : Settings.getDefaultsMap();
        List<SliderChoiceSnapshot> synthesized = new ArrayList<>();
        for (Map.Entry<String, DefaultSliderValue> entry : defaults.entrySet()) {
            if (!representedNames.contains(entry.getKey()))
                synthesized.add(new SliderChoiceSnapshot(entry.getKey(), true, null, null,
                        (int) (entry.getValue().getValueSmall() * 100),
                        (int) (entry.getValue().getValueBig() * 100), 100, 100, true));
        }
        return synthesized;
    }

    /**
     * Rebuilds a complete choice list for the requested mode, matching the legacy
     * UUNP toggle: every previously synthesized default is discarded (including any
     * enabled or percentage edits made to it), explicit choices are retained with
     * their effective values re-resolved for absent stored endpoints, and the
     * requested mode's missing defaults are synthesized afresh.
     *
     * @param choices current complete choices of the Slider Preset
     * @param uunp whether the UUNP defaults apply to the rebuilt list
     * @return complete choices in canonical case-insensitive order
     */
    static List<SliderChoiceSnapshot> rebuildForMode(List<SliderChoiceSnapshot> choices, boolean uunp) {
        List<SliderChoiceSnapshot> rebuilt = new ArrayList<>();
        Set<String> representedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (SliderChoiceSnapshot choice : choices) {
            if (choice.isMissingDefault())
                continue;
            representedNames.add(choice.getName());
            rebuilt.add(resolveEffective(choice, uunp));
        }
        rebuilt.addAll(synthesizeMissing(representedNames, uunp));
        Collections.sort(rebuilt, NAME_ORDER);
        return rebuilt;
    }

    /**
     * Returns a copy of one choice whose effective endpoints are derived from its
     * stored endpoints and the requested mode, discarding whatever effective values
     * the caller supplied. Only stored endpoints are persisted and the Project file
     * loader re-derives effective values from them, so any other effective values
     * would silently change generated output across a save/reopen cycle.
     *
     * @param choice caller-supplied choice
     * @param uunp whether the UUNP defaults apply
     * @return an immutable copy with mode-consistent effective values and every
     *         other value preserved
     */
    static SliderChoiceSnapshot resolveEffective(SliderChoiceSnapshot choice, boolean uunp) {
        Integer storedSmall = choice.getStoredSmallValue().isPresent()
                ? Integer.valueOf(choice.getStoredSmallValue().getAsInt()) : null;
        Integer storedBig = choice.getStoredBigValue().isPresent()
                ? Integer.valueOf(choice.getStoredBigValue().getAsInt()) : null;
        return new SliderChoiceSnapshot(choice.getName(), choice.isEnabled(), storedSmall, storedBig,
                effectiveSmall(choice.getName(), storedSmall, uunp),
                effectiveBig(choice.getName(), storedBig, uunp),
                choice.getPercentageMinimum(), choice.getPercentageMaximum(), choice.isMissingDefault());
    }

    /**
     * Applies {@link #resolveEffective(SliderChoiceSnapshot, boolean)} to every
     * choice, preserving list order.
     *
     * @param choices caller-supplied choices
     * @param uunp whether the UUNP defaults apply
     * @return a new list of mode-consistent copies in the same order
     */
    static List<SliderChoiceSnapshot> resolveEffective(List<SliderChoiceSnapshot> choices, boolean uunp) {
        List<SliderChoiceSnapshot> resolved = new ArrayList<>(choices.size());
        for (SliderChoiceSnapshot choice : choices)
            resolved.add(resolveEffective(choice, uunp));
        return resolved;
    }
}
