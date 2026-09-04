package com.asdasfa.jbs2bg.project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *
 * <p>This is also where a choice list's canonical case-insensitive order and its
 * lookup by slider name live. Choices are a Slider Preset's payload, which the
 * {@link Project} aggregate replaces wholesale and never inspects (ADR-0002), so
 * their ordering belongs with the other slider-configuration rules rather than
 * with Project integrity or with the session.
 */
final class SliderChoiceDefaults {

    /**
     * Canonical slider-choice order: slider name, without regard to case.
     */
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
     * @param name   slider name used by settings lookup
     * @param stored persisted small value, or null to use the configured default
     * @param uunp   whether the UUNP defaults apply
     * @return stored value, configured default as a percentage, or zero when unconfigured
     */
    static int effectiveSmall(String name, Integer stored, boolean uunp) {
        return effectiveSmall(name, stored, uunp, Settings.snapshot());
    }

    /**
     * Resolves the effective big value, deferring to the mode's Slider settings
     * when no value is stored.
     *
     * @param name   slider name used by settings lookup
     * @param stored persisted big value, or null to use the configured default
     * @param uunp   whether the UUNP defaults apply
     * @return stored value, configured default as a percentage, or zero when unconfigured
     */
    static int effectiveBig(String name, Integer stored, boolean uunp) {
        return effectiveBig(name, stored, uunp, Settings.snapshot());
    }

    /**
     * Resolves one small endpoint against a caller-pinned Settings generation.
     *
     * @param name     exact slider identity used by Settings
     * @param stored   persisted endpoint, or null to use Settings
     * @param uunp     whether the UUNP profile applies
     * @param settings complete immutable Settings generation
     * @return stored or pinned configured percentage, otherwise zero
     */
    private static int effectiveSmall(String name, Integer stored, boolean uunp, Settings.Snapshot settings) {
        if (stored != null)
            return stored.intValue();
        DefaultSliderValue configured = defaults(uunp, settings).get(name);
        return configured == null ? 0 : (int) (configured.getValueSmall() * 100);
    }

    /**
     * Resolves one big endpoint against a caller-pinned Settings generation.
     *
     * @param name     exact slider identity used by Settings
     * @param stored   persisted endpoint, or null to use Settings
     * @param uunp     whether the UUNP profile applies
     * @param settings complete immutable Settings generation
     * @return stored or pinned configured percentage, otherwise zero
     */
    private static int effectiveBig(String name, Integer stored, boolean uunp, Settings.Snapshot settings) {
        if (stored != null)
            return stored.intValue();
        DefaultSliderValue configured = defaults(uunp, settings).get(name);
        return configured == null ? 0 : (int) (configured.getValueBig() * 100);
    }

    /**
     * Selects the requested mode's immutable defaults from one complete Settings generation.
     *
     * @param uunp     whether the UUNP profile applies
     * @param settings complete immutable Settings generation
     * @return immutable defaults in pinned encounter order
     */
    private static Map<String, DefaultSliderValue> defaults(boolean uunp, Settings.Snapshot settings) {
        Settings.Snapshot pinned = Objects.requireNonNull(settings, "settings");
        return (uunp ? pinned.uunp() : pinned.standard()).defaults();
    }

    /**
     * Synthesizes an enabled, all-default choice for every configured default of the
     * requested mode whose name is not already represented.
     *
     * @param representedNames slider names already present, compared without regard to case
     * @param uunp             whether the UUNP defaults apply
     * @return synthesized choices in settings order; callers sort as needed
     */
    static List<SliderChoiceSnapshot> synthesizeMissing(Set<String> representedNames, boolean uunp) {
        return synthesizeMissing(representedNames, uunp, Settings.snapshot());
    }

    /**
     * Synthesizes missing choices exclusively from one caller-pinned Settings generation.
     *
     * @param representedNames slider names already represented by explicit choices
     * @param uunp             whether the UUNP profile applies
     * @param settings         complete immutable Settings generation for this rebuild
     * @return synthesized choices in pinned Settings encounter order
     */
    static List<SliderChoiceSnapshot> synthesizeMissing(Set<String> representedNames, boolean uunp,
                                                        Settings.Snapshot settings) {
        Map<String, DefaultSliderValue> configuredDefaults = defaults(uunp, settings);
        List<SliderChoiceSnapshot> synthesized = new ArrayList<>();
        Set<String> completedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        completedNames.addAll(representedNames);
        for (Map.Entry<String, DefaultSliderValue> entry : configuredDefaults.entrySet()) {
            // Settings intentionally accepts case-distinct keys, while a Slider
            // Preset has one logical choice identity regardless of display casing.
            if (completedNames.add(entry.getKey()))
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
     * @param uunp    whether the UUNP defaults apply to the rebuilt list
     * @return complete choices in canonical case-insensitive order
     */
    static List<SliderChoiceSnapshot> rebuildForMode(List<SliderChoiceSnapshot> choices, boolean uunp) {
        return rebuildForMode(choices, uunp, Settings.snapshot());
    }

    /**
     * Rebuilds one complete choice list using exactly one immutable Settings generation for membership and endpoints.
     *
     * @param choices  current complete choices of the Slider Preset
     * @param uunp     whether the UUNP profile applies
     * @param settings complete immutable Settings generation for this rebuild
     * @return rebuilt choices in canonical case-insensitive order
     */
    static List<SliderChoiceSnapshot> rebuildForMode(List<SliderChoiceSnapshot> choices, boolean uunp,
                                                     Settings.Snapshot settings) {
        Settings.Snapshot pinned = Objects.requireNonNull(settings, "settings");
        List<SliderChoiceSnapshot> rebuilt = new ArrayList<>();
        Set<String> representedNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (SliderChoiceSnapshot choice : choices) {
            if (choice.isMissingDefault())
                continue;
            representedNames.add(choice.getName());
            rebuilt.add(resolveEffective(choice, uunp, pinned));
        }
        rebuilt.addAll(synthesizeMissing(representedNames, uunp, pinned));
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
     * @param uunp   whether the UUNP defaults apply
     * @return an immutable copy with mode-consistent effective values and every
     * other value preserved
     */
    static SliderChoiceSnapshot resolveEffective(SliderChoiceSnapshot choice, boolean uunp) {
        return resolveEffective(choice, uunp, Settings.snapshot());
    }

    /**
     * Resolves one explicit choice exclusively against a caller-pinned Settings generation.
     *
     * @param choice   explicit choice whose stored endpoints remain authoritative
     * @param uunp     whether the UUNP profile applies
     * @param settings complete immutable Settings generation for endpoint fallback
     * @return copied choice with effective endpoints from the pinned generation
     */
    static SliderChoiceSnapshot resolveEffective(SliderChoiceSnapshot choice, boolean uunp,
                                                 Settings.Snapshot settings) {
        Integer storedSmall = choice.getStoredSmallValue().isPresent()
                ? Integer.valueOf(choice.getStoredSmallValue().getAsInt()) : null;
        Integer storedBig = choice.getStoredBigValue().isPresent()
                ? Integer.valueOf(choice.getStoredBigValue().getAsInt()) : null;
        return new SliderChoiceSnapshot(choice.getName(), choice.isEnabled(), storedSmall, storedBig,
                effectiveSmall(choice.getName(), storedSmall, uunp, settings),
                effectiveBig(choice.getName(), storedBig, uunp, settings),
                choice.getPercentageMinimum(), choice.getPercentageMaximum(), choice.isMissingDefault());
    }

    /**
     * Copies a choice list into canonical case-insensitive slider-name order. The
     * input is never mutated.
     *
     * @param choices choices in any order
     * @return a new list in canonical order
     */
    static List<SliderChoiceSnapshot> sortedByName(List<SliderChoiceSnapshot> choices) {
        List<SliderChoiceSnapshot> sorted = new ArrayList<>(choices);
        Collections.sort(sorted, NAME_ORDER);
        return sorted;
    }

    /**
     * Upserts one choice by case-insensitive slider name: the choice replaces the
     * existing one with that name, or is added when the name is new, and the
     * result is in canonical order. Display casing is not part of a choice's
     * identity, so "Breasts" replaces "breasts". The input is never mutated.
     *
     * @param choices current choices, in any order
     * @param choice  replacement or new choice
     * @return a new list in canonical order carrying the choice
     */
    static List<SliderChoiceSnapshot> withChoice(List<SliderChoiceSnapshot> choices, SliderChoiceSnapshot choice) {
        List<SliderChoiceSnapshot> next = new ArrayList<>(choices);
        int index = indexOfName(next, choice.getName());
        if (index >= 0)
            next.set(index, choice);
        else
            next.add(choice);
        Collections.sort(next, NAME_ORDER);
        return next;
    }

    /**
     * Locates a choice by slider name without regard to case.
     *
     * @return the index, or -1 when no choice has that name
     */
    private static int indexOfName(List<SliderChoiceSnapshot> choices, String name) {
        for (int index = 0; index < choices.size(); index++) {
            if (choices.get(index).getName().equalsIgnoreCase(name))
                return index;
        }
        return -1;
    }

    /**
     * Applies {@link #resolveEffective(SliderChoiceSnapshot, boolean)} to every
     * choice, preserving list order.
     *
     * @param choices caller-supplied choices
     * @param uunp    whether the UUNP defaults apply
     * @return a new list of mode-consistent copies in the same order
     */
    static List<SliderChoiceSnapshot> resolveEffective(List<SliderChoiceSnapshot> choices, boolean uunp) {
        Settings.Snapshot settings = Settings.snapshot();
        List<SliderChoiceSnapshot> resolved = new ArrayList<>(choices.size());
        for (SliderChoiceSnapshot choice : choices)
            resolved.add(resolveEffective(choice, uunp, settings));
        return resolved;
    }
}
