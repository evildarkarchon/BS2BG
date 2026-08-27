package com.asdasfa.jbs2bg.project;

import java.util.Objects;

/**
 * Structured Project diagnostic whose formatting remains a caller concern.
 */
public final class ProjectDiagnostic {

	private final String code;
	private final DiagnosticSeverity severity;
	private final SourceLocation sourceLocation;
	private final String message;

	/**
	 * Creates an immutable diagnostic.
	 *
	 * @param code stable machine-readable code
	 * @param severity presentation-independent severity
	 * @param sourceLocation structured source location
	 * @param message human-readable explanation without presentation formatting
	 * @throws NullPointerException when an argument is null
	 * @throws IllegalArgumentException when code or message is empty
	 */
	public ProjectDiagnostic(String code, DiagnosticSeverity severity, SourceLocation sourceLocation, String message) {
		this.code = requireText(code, "code");
		this.severity = Objects.requireNonNull(severity, "severity");
		this.sourceLocation = Objects.requireNonNull(sourceLocation, "sourceLocation");
		this.message = requireText(message, "message");
	}

	/** @return the stable machine-readable diagnostic code */
	public String getCode() {
		return code;
	}

	/** @return the diagnostic severity */
	public DiagnosticSeverity getSeverity() {
		return severity;
	}

	/** @return the structured source location */
	public SourceLocation getSourceLocation() {
		return sourceLocation;
	}

	/** @return the human-readable unformatted diagnostic message */
	public String getMessage() {
		return message;
	}

	/**
	 * Requires non-blank diagnostic text while retaining the caller's wording.
	 *
	 * @param value text to validate
	 * @param name argument name used in validation failures
	 * @return the original validated text
	 * @throws NullPointerException when value or name is null
	 * @throws IllegalArgumentException when value is blank
	 */
	private static String requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.trim().isEmpty())
			throw new IllegalArgumentException(name + " must not be empty");
		return value;
	}
}
