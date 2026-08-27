package com.asdasfa.jbs2bg.project;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Structured source location for a Project file, field, or edit request.
 */
public final class SourceLocation {

	private final Path path;
	private final String element;
	private final Integer line;
	private final Integer column;

	/**
	 * Creates a source location. Paths are normalized to absolute paths, and line
	 * and column numbers are one-based when present.
	 *
	 * @param path source file path, or empty for a logical Project source
	 * @param element logical field, JSON pointer, XML element, or edit field
	 * @param line one-based source line, or empty when unknown
	 * @param column one-based source column, or empty when unknown
	 * @throws NullPointerException when an optional or contained value is null
	 * @throws IllegalArgumentException for non-positive positions or a column without a line
	 */
	public SourceLocation(Optional<Path> path, Optional<String> element, OptionalInt line, OptionalInt column) {
		Optional<Path> requiredPath = Objects.requireNonNull(path, "path");
		Optional<String> requiredElement = Objects.requireNonNull(element, "element");
		this.path = requiredPath.isPresent()
				? Objects.requireNonNull(requiredPath.get(), "path value").toAbsolutePath().normalize()
				: null;
		this.element = requiredElement.isPresent()
				? Objects.requireNonNull(requiredElement.get(), "element value")
				: null;
		Objects.requireNonNull(line, "line");
		Objects.requireNonNull(column, "column");
		if (line.isPresent() && line.getAsInt() < 1)
			throw new IllegalArgumentException("line must be one-based");
		if (column.isPresent() && column.getAsInt() < 1)
			throw new IllegalArgumentException("column must be one-based");
		if (column.isPresent() && !line.isPresent())
			throw new IllegalArgumentException("column requires a line");
		this.line = line.isPresent() ? Integer.valueOf(line.getAsInt()) : null;
		this.column = column.isPresent() ? Integer.valueOf(column.getAsInt()) : null;
	}

	/** @return the normalized source path, or empty when not file-backed */
	public Optional<Path> getPath() {
		return Optional.ofNullable(path);
	}

	/** @return the logical element within the source, or empty when unknown */
	public Optional<String> getElement() {
		return Optional.ofNullable(element);
	}

	/** @return the one-based source line, or empty when unknown */
	public OptionalInt getLine() {
		return line == null ? OptionalInt.empty() : OptionalInt.of(line.intValue());
	}

	/** @return the one-based source column, or empty when unknown */
	public OptionalInt getColumn() {
		return column == null ? OptionalInt.empty() : OptionalInt.of(column.intValue());
	}
}
