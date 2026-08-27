package com.asdasfa.jbs2bg.project;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Deeply immutable view of all persisted Project state and lifecycle metadata.
 */
public final class ProjectSnapshot {

	private static final ProjectSnapshot NO_PROJECT = new ProjectSnapshot(ProjectLifecycleStatus.NO_PROJECT);
	private static final ProjectSnapshot EMPTY = new ProjectSnapshot(ProjectLifecycleStatus.UNTITLED);

	private final List<SliderPresetSnapshot> sliderPresets;
	private final List<CustomMorphTargetSnapshot> customMorphTargets;
	private final List<NpcMorphAssignmentSnapshot> npcMorphAssignments;
	private final Path fileIdentity;
	private final boolean dirty;
	private final ProjectLifecycleStatus lifecycleStatus;

	/**
	 * Creates one of the canonical empty snapshots used before and after the first
	 * lifecycle operation.
	 *
	 * @param lifecycleStatus NO_PROJECT or UNTITLED lifecycle status
	 */
	private ProjectSnapshot(ProjectLifecycleStatus lifecycleStatus) {
		this(Collections.<SliderPresetSnapshot>emptyList(),
				Collections.<CustomMorphTargetSnapshot>emptyList(),
				Collections.<NpcMorphAssignmentSnapshot>emptyList(), Optional.<Path>empty(), false,
				lifecycleStatus);
	}

	/**
	 * Creates a deeply immutable Project snapshot from immutable child values.
	 * Caller-owned lists are copied and a present file identity is normalized to an
	 * absolute path.
	 *
	 * @param sliderPresets Slider Presets in canonical order
	 * @param customMorphTargets Custom Morph Targets in canonical order
	 * @param npcMorphAssignments NPC Morph Assignments in canonical order
	 * @param fileIdentity adopted Project path, or empty for an untitled Project
	 * @param dirty whether persisted content has unsaved changes
	 * @param lifecycleStatus stable lifecycle classification
	 * @throws NullPointerException when an argument or list element is null
	 * @throws IllegalArgumentException when Project content, dirty state, or file identity
	 *         contradicts the supplied lifecycle status
	 */
	public ProjectSnapshot(List<SliderPresetSnapshot> sliderPresets,
			List<CustomMorphTargetSnapshot> customMorphTargets,
			List<NpcMorphAssignmentSnapshot> npcMorphAssignments, Optional<Path> fileIdentity, boolean dirty,
			ProjectLifecycleStatus lifecycleStatus) {
		this.sliderPresets = ImmutableValues.copyOf(sliderPresets, "sliderPresets");
		this.customMorphTargets = ImmutableValues.copyOf(customMorphTargets, "customMorphTargets");
		this.npcMorphAssignments = ImmutableValues.copyOf(npcMorphAssignments, "npcMorphAssignments");
		Optional<Path> requiredFileIdentity = Objects.requireNonNull(fileIdentity, "fileIdentity");
		this.fileIdentity = requiredFileIdentity.isPresent()
				? Objects.requireNonNull(requiredFileIdentity.get(), "fileIdentity value").toAbsolutePath().normalize()
				: null;
		this.dirty = dirty;
		this.lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus");
		validateLifecycle();
	}

	/**
	 * Returns the canonical empty, clean, untitled Project snapshot.
	 *
	 * @return the canonical active New Project snapshot
	 */
	static ProjectSnapshot empty() {
		return EMPTY;
	}

	/**
	 * Returns the immutable pre-lifecycle snapshot for a session that has not yet
	 * established an active Project.
	 *
	 * @return the canonical pre-lifecycle snapshot
	 */
	static ProjectSnapshot noProject() {
		return NO_PROJECT;
	}

	/**
	 * Returns Slider Presets in canonical Project order.
	 *
	 * @return an immutable list of immutable Slider Presets
	 */
	public List<SliderPresetSnapshot> getSliderPresets() {
		return sliderPresets;
	}

	/**
	 * Returns Custom Morph Targets in canonical Project order.
	 *
	 * @return an immutable list of immutable Custom Morph Targets
	 */
	public List<CustomMorphTargetSnapshot> getCustomMorphTargets() {
		return customMorphTargets;
	}

	/**
	 * Returns NPC Morph Assignments in canonical Project order.
	 *
	 * @return an immutable list of immutable NPC Morph Assignments
	 */
	public List<NpcMorphAssignmentSnapshot> getNpcMorphAssignments() {
		return npcMorphAssignments;
	}

	/**
	 * Returns the adopted Project path when the lifecycle is file-backed.
	 *
	 * @return the normalized file identity, or empty for an untitled Project
	 */
	public Optional<Path> getFileIdentity() {
		return Optional.ofNullable(fileIdentity);
	}

	/**
	 * Reports whether persisted Project content has unsaved changes.
	 *
	 * @return true when the Project is dirty
	 */
	public boolean isDirty() {
		return dirty;
	}

	/**
	 * Returns the stable lifecycle classification of this Project.
	 *
	 * @return the Project lifecycle status
	 */
	public ProjectLifecycleStatus getLifecycleStatus() {
		return lifecycleStatus;
	}

	/**
	 * Protects file-identity and recovery invariants at snapshot construction.
	 *
	 * @throws IllegalArgumentException when Project content, dirty state, or file identity
	 *         contradicts the snapshot lifecycle status
	 */
	private void validateLifecycle() {
		if (lifecycleStatus == ProjectLifecycleStatus.NO_PROJECT) {
			if (fileIdentity != null || dirty || !sliderPresets.isEmpty() || !customMorphTargets.isEmpty()
					|| !npcMorphAssignments.isEmpty())
				throw new IllegalArgumentException("A session without an active Project cannot contain Project state");
			return;
		}
		if (lifecycleStatus == ProjectLifecycleStatus.UNTITLED && fileIdentity != null)
			throw new IllegalArgumentException("An untitled Project cannot have a file identity");
		if (lifecycleStatus != ProjectLifecycleStatus.UNTITLED && fileIdentity == null)
			throw new IllegalArgumentException("A file-backed Project requires a file identity");
		if (lifecycleStatus == ProjectLifecycleStatus.RECOVERED && !dirty)
			throw new IllegalArgumentException("A recovered Project must remain dirty");
	}
}
