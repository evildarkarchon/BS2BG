package com.asdasfa.jbs2bg.project;

/**
 * Presentation-independent severity assigned to a Project diagnostic.
 */
public enum DiagnosticSeverity {
	/** Informational context that does not indicate invalid state. */
	INFO,
	/** Recoverable or cautionary condition callers should surface. */
	WARNING,
	/** Rejection or failure that prevented requested behavior. */
	ERROR
}
