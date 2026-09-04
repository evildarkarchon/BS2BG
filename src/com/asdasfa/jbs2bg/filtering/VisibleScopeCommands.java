package com.asdasfa.jbs2bg.filtering;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentEdits;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentIdentity;
import com.asdasfa.jbs2bg.project.NpcMorphAssignmentSnapshot;
import com.asdasfa.jbs2bg.project.NpcSliderPresetChoice;
import com.asdasfa.jbs2bg.project.ProjectEdit;
import com.asdasfa.jbs2bg.project.ProjectSession;
import com.asdasfa.jbs2bg.project.SliderPresetEdits;
import com.asdasfa.jbs2bg.project.SliderPresetSnapshot;

/**
 * Builds the single edit each bulk command submits from the visible scope it
 * froze first. Every command operates on the complete visible set of its table
 * (never the selection, never the unfiltered source), and every random choice
 * is completed here so the resulting edit is plain immutable data.
 * <p>
 * Callers freeze the scope with {@link FilteredView#visibleSet()} before
 * building the edit, then submit it through {@link ProjectSession#apply}. Since
 * rendering the outcome replaces the view's rows, the scope must not be read
 * from the live view during the edit; the frozen {@link VisibleSet} makes that
 * impossible.
 */
public final class VisibleScopeCommands {

    private VisibleScopeCommands() {
    }

    /**
     * Clear Slider Presets: removes exactly the frozen visible Slider Preset identities and every relationship to
     * them, leaving hidden presets untouched.
     *
     * @param scope frozen visible Slider Presets
     * @return one atomic filtered deletion edit
     * @throws NullPointerException when scope is null
     */
    public static ProjectEdit clearSliderPresets(VisibleSet<SliderPresetSnapshot, NameIdentity> scope) {
        List<String> names = Objects.requireNonNull(scope, "scope").getIdentities().stream()
                .map(NameIdentity::getName)
                .toList();
        return SliderPresetEdits.deleteAll(names);
    }

    /**
     * Clear NPC Morph Assignments: removes every visible NPC Morph Assignment.
     *
     * @param scope frozen visible NPC Morph Assignments
     * @return one atomic remove edit for exactly those identities
     * @throws NullPointerException when scope is null
     */
    public static ProjectEdit clearNpcMorphAssignments(
            VisibleSet<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> scope) {
        return NpcMorphAssignmentEdits.removeNpcs(Objects.requireNonNull(scope, "scope").getIdentities());
    }

    /**
     * Clear Assignments: removes every Slider Preset relationship from every
     * visible NPC Morph Assignment.
     *
     * @param scope frozen visible NPC Morph Assignments
     * @return one atomic clear edit for exactly those identities
     * @throws NullPointerException when scope is null
     */
    public static ProjectEdit clearAssignments(
            VisibleSet<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> scope) {
        return NpcMorphAssignmentEdits.clearSliderPresetsForNpcs(
                Objects.requireNonNull(scope, "scope").getIdentities());
    }

    /**
     * Fill Empty: assigns one randomly chosen Slider Preset to each visible NPC
     * Morph Assignment that currently has none. Visible NPCs that already have an
     * assignment and every hidden NPC are left alone.
     *
     * @param scope             frozen visible NPC Morph Assignments
     * @param sliderPresetNames the caller-selected Slider Presets to draw from
     * @param randomIndex       draws an index in {@code [0, bound)} for the given bound;
     *                          invoked once per empty visible NPC
     * @return one atomic fill edit carrying every completed choice
     * @throws NullPointerException     when an argument or name is null
     * @throws IllegalArgumentException when no Slider Preset is offered, or the
     *                                  draw returns an index outside {@code [0, bound)}
     */
    public static ProjectEdit fillEmpty(VisibleSet<NpcMorphAssignmentSnapshot, NpcMorphAssignmentIdentity> scope,
                                        List<String> sliderPresetNames, IntUnaryOperator randomIndex) {
        Objects.requireNonNull(scope, "scope");
        List<String> names = new ArrayList<>(Objects.requireNonNull(sliderPresetNames, "sliderPresetNames"));
        Objects.requireNonNull(randomIndex, "randomIndex");
        for (String name : names)
            Objects.requireNonNull(name, "slider preset name");
        if (names.isEmpty())
            throw new IllegalArgumentException("at least one Slider Preset must be offered");
        List<NpcSliderPresetChoice> choices = new ArrayList<>();
        for (int index = 0; index < scope.size(); index++) {
            NpcMorphAssignmentSnapshot npc = scope.getRows().get(index);
            if (!npc.getSliderPresetNames().isEmpty())
                continue;
            int drawn = randomIndex.applyAsInt(names.size());
            if (drawn < 0 || drawn >= names.size())
                throw new IllegalArgumentException("random index " + drawn + " outside [0, " + names.size() + ")");
            choices.add(new NpcSliderPresetChoice(scope.getIdentities().get(index), names.get(drawn)));
        }
        return NpcMorphAssignmentEdits.fillEmpty(choices);
    }

    /**
     * NPC Database Add All: promotes every visible NPC Database entry into an NPC
     * Morph Assignment in one atomic edit. Entries whose identity is already in
     * the Project are no-ops for the session.
     *
     * @param scope               frozen visible NPC Database entries
     * @param sliderPresetChooser completes the optional random assignment for one
     *                            entry; invoked once per visible entry and may return an empty list
     * @return one atomic bulk add edit with copied source values
     * @throws NullPointerException when an argument or a chosen list is null
     */
    public static ProjectEdit addAllNpcs(VisibleSet<NPC, NpcMorphAssignmentIdentity> scope,
                                         Supplier<List<String>> sliderPresetChooser) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(sliderPresetChooser, "sliderPresetChooser");
        List<NpcMorphAssignmentSnapshot> sources = new ArrayList<>(scope.size());
        for (NPC source : scope.getRows())
            sources.add(copySource(source, Objects.requireNonNull(sliderPresetChooser.get(), "chosen presets")));
        return NpcMorphAssignmentEdits.addNpcs(sources);
    }

    /**
     * NPC Database Clear: the entries to remove from the session-scoped NPC
     * Database. This is the one bulk command that is not a Project edit; the
     * frozen list lets the caller mutate the live database without iterating it.
     *
     * @param scope frozen visible NPC Database entries
     * @return the immutable entries to remove, in presentation order
     * @throws NullPointerException when scope is null
     */
    public static List<NPC> clearNpcDatabase(VisibleSet<NPC, NpcMorphAssignmentIdentity> scope) {
        return Objects.requireNonNull(scope, "scope").getRows();
    }

    /**
     * Copies source values into an immutable Project value without retaining the
     * NPC Database entry, keeping the NPC Database independent of the Project.
     */
    private static NpcMorphAssignmentSnapshot copySource(NPC source, List<String> sliderPresetNames) {
        return new NpcMorphAssignmentSnapshot(source.getName(), source.getMod(), source.getEditorId(),
                source.getRace(), source.getFormId(), sliderPresetNames);
    }
}
