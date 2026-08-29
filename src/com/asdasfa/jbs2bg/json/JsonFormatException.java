package com.asdasfa.jbs2bg.json;

/** Stable repository diagnostic translated from JSON syntax, schema, and resource failures. */
final class JsonFormatException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final String source;
    private final String path;
    private final int line;
    private final int column;

    /**
     * Creates a JSON failure that contains no codec-specific type.
     *
     * @param code stable diagnostic code
     * @param source source-file description
     * @param path escaped JSON-pointer-like path
     * @param line one-based line, or zero when unavailable
     * @param column one-based column, or zero when unavailable
     * @param message readable failure detail
     */
    JsonFormatException(String code, String source, String path, int line, int column, String message) {
        super(message);
        this.code = code;
        this.source = source;
        this.path = path;
        this.line = line;
        this.column = column;
    }

    String code() {
        return code;
    }

    String source() {
        return source;
    }

    String path() {
        return path;
    }

    int line() {
        return line;
    }

    int column() {
        return column;
    }
}
