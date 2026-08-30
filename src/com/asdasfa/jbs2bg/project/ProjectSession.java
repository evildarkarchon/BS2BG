package com.asdasfa.jbs2bg.project;

import java.nio.file.Path;
import java.util.List;

/**
 * External seam for synchronous access to the active Project and its lifecycle.
 * Implementations are thread-safe and expose state only as immutable snapshots.
 */
public interface ProjectSession {

	/**
	 * Returns the latest completely published Project snapshot.
	 *
	 * @return the current immutable snapshot
	 */
	ProjectSnapshot getSnapshot();

	/**
	 * Replaces the active state with a new empty Project.
	 *
	 * @return an outcome carrying the resulting snapshot
	 */
	ProjectOutcome newProject();

	/**
	 * Opens a Project from a local filesystem path. Parsing and validation complete
	 * before a changed snapshot may be published.
	 *
	 * @param source Project file to open
	 * @return a typed outcome carrying the replaced or preserved latest snapshot
	 * @throws NullPointerException when source is null
	 */
	default ProjectOutcome open(Path source) {
		return open(source, ProjectOperationContext.nonCancellable());
	}

	/**
	 * Opens a Project with cooperative cancellation, truthful progress, and an atomic publication boundary.
	 *
	 * @param source Project file to open
	 * @param context operation context retained for the synchronous call only
	 * @return a typed outcome carrying the replaced or preserved latest snapshot
	 * @throws NullPointerException when source or context is null
	 */
	ProjectOutcome open(Path source, ProjectOperationContext context);

	/**
	 * Saves the current Project to its adopted file identity.
	 *
	 * @return a typed outcome carrying the saved or preserved latest snapshot
	 */
	default ProjectOutcome save() {
		return save(ProjectOperationContext.nonCancellable());
	}

	/**
	 * Saves the adopted Project identity through the supplied operation context.
	 *
	 * @param context operation context retained for the synchronous call only
	 * @return a typed outcome carrying the saved or preserved latest snapshot
	 */
	ProjectOutcome save(ProjectOperationContext context);

	/**
	 * Saves the current Project to a requested path and adopts that identity only
	 * after persistence succeeds.
	 *
	 * @param target requested Project file identity
	 * @return a typed outcome carrying the saved or preserved latest snapshot
	 * @throws NullPointerException when target is null
	 */
	default ProjectOutcome saveAs(Path target) {
		return saveAs(target, ProjectOperationContext.nonCancellable());
	}

	/**
	 * Saves to a requested identity through the supplied operation context.
	 *
	 * @param target requested Project file identity
	 * @param context operation context retained for the synchronous call only
	 * @return a typed outcome carrying the saved or preserved latest snapshot
	 */
	ProjectOutcome saveAs(Path target, ProjectOperationContext context);

	/**
	 * Parses and upserts Slider Presets from an ordered batch of BodySlide XML
	 * files. Each source is parsed before its changes are committed, so a rejected
	 * or failed source cannot discard successful imports from other files.
	 *
	 * @param sources BodySlide XML files in selection order
	 * @return aggregate and per-source typed outcomes carrying coherent snapshots
	 * @throws NullPointerException when sources or any contained source is null
	 */
	default SliderPresetImportOutcome importSliderPresets(List<Path> sources) {
		return importSliderPresets(sources, ProjectOperationContext.nonCancellable());
	}

	/**
	 * Imports an ordered source batch with cancellation safe points between sources.
	 *
	 * @param sources BodySlide XML files in selection order
	 * @param context operation context retained for the synchronous call only
	 * @return aggregate and per-source outcomes, including any effects committed before cancellation
	 */
	SliderPresetImportOutcome importSliderPresets(List<Path> sources, ProjectOperationContext context);

	/**
	 * Applies one explicit Project edit atomically. Unknown edit request types are
	 * rejected with a structured diagnostic and the unchanged latest snapshot.
	 *
	 * @param edit immutable edit request data
	 * @return a typed outcome carrying the latest coherent snapshot
	 * @throws NullPointerException when edit is null
	 */
	ProjectOutcome apply(ProjectEdit edit);
}
