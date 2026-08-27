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
	ProjectOutcome open(Path source);

	/**
	 * Saves the current Project to its adopted file identity.
	 *
	 * @return a typed outcome carrying the saved or preserved latest snapshot
	 */
	ProjectOutcome save();

	/**
	 * Saves the current Project to a requested path and adopts that identity only
	 * after persistence succeeds.
	 *
	 * @param target requested Project file identity
	 * @return a typed outcome carrying the saved or preserved latest snapshot
	 * @throws NullPointerException when target is null
	 */
	ProjectOutcome saveAs(Path target);

	/**
	 * Parses and upserts Slider Presets from an ordered batch of BodySlide XML
	 * files. Each source is parsed before its changes are committed, so a rejected
	 * or failed source cannot discard successful imports from other files.
	 *
	 * @param sources BodySlide XML files in selection order
	 * @return aggregate and per-source typed outcomes carrying coherent snapshots
	 * @throws NullPointerException when sources or any contained source is null
	 */
	SliderPresetImportOutcome importSliderPresets(List<Path> sources);

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
