package com.asdasfa.jbs2bg.json;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonWriteFeature;

/**
 * Repository-owned Jackson configuration and stable exception-translation boundary.
 *
 * <p>The class is public only because Java has no friend-package visibility for the
 * three package-owned format adapters. Repository policy tests enforce that no other
 * production source references this internal implementation or imports Jackson.
 */
public final class JacksonJson {
    private static final int MAXIMUM_NESTING_DEPTH = 64;
    private static final int MAXIMUM_TEXT_BYTES = 1024 * 1024;
    private static final int MAXIMUM_NUMBER_CHARACTERS = 128;
    private static final JsonFactory PROJECT_READER_FACTORY = readerFactory(JsonProfile.PROJECT);
    private static final JsonFactory SETTINGS_READER_FACTORY = readerFactory(JsonProfile.SETTINGS);
    // Preserve Unicode scalar UTF-8 and lowercase legacy control escapes across every canonical writer profile.
    private static final JsonFactory CANONICAL_WRITER_FACTORY = JsonFactory.builder()
            .enable(JsonWriteFeature.COMBINE_UNICODE_SURROGATES_IN_UTF8)
            .disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE)
            .build();

    private JacksonJson() {
    }

    /** @return the shared Project parser factory carrying the repository's named constraints */
    public static JsonFactory projectReaderFactory() {
        return PROJECT_READER_FACTORY;
    }

    /** @return the shared Settings parser factory carrying the repository's named constraints */
    public static JsonFactory settingsReaderFactory() {
        return SETTINGS_READER_FACTORY;
    }

    /** @return the repository's deterministic Core-only writer factory for semantic JSON formats */
    public static JsonFactory canonicalWriterFactory() {
        return CANONICAL_WRITER_FACTORY;
    }

    /** @return maximum accepted Project source bytes */
    public static long projectMaximumDocumentBytes() {
        return JsonProfile.PROJECT.maximumDocumentBytes();
    }

    /** @return maximum accepted bytes for each Settings source */
    public static long settingsMaximumDocumentBytes() {
        return JsonProfile.SETTINGS.maximumDocumentBytes();
    }

    /** Reports whether decoded text exceeds the shared one-MiB UTF-8 token limit. */
    public static boolean exceedsTextLimit(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_TEXT_BYTES;
    }

    /** Reports whether Jackson identified one of the repository-configured stream constraints. */
    public static boolean isConstraintFailure(JacksonException exception) {
        return exception.getClass().getName().contains("StreamConstraints");
    }

    /** Uses parser state when a Jackson failure does not carry its own source coordinate. */
    public static TokenStreamLocation bestLocation(JacksonException exception, JsonParser parser) {
        TokenStreamLocation location = exception.getLocation();
        if (location != null)
            return location;
        return parser == null ? null : parser.currentLocation();
    }

    /**
     * Strictly validates one complete UTF-8 JSON document with the selected owned profile.
     * Exact duplicate names are rejected except for legal Project NPC display-name members.
     *
     * @param source stable source description used by diagnostics
     * @param profile owned resource-limit and duplicate-name profile
     * @param utf8 complete document bytes
     * @throws JsonFormatException for syntax, duplicate, trailing-data, or limit failures
     */
    static void validate(String source, JsonProfile profile, byte[] utf8) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(utf8, "utf8");
        if (utf8.length > profile.maximumDocumentBytes()) {
            throw failure("JSON_RESOURCE_LIMIT", source, "/", 1, 1,
                    "JSON input exceeds the " + profile.maximumDocumentBytes() + " byte limit.");
        }

        JsonFactory factory = profile == JsonProfile.PROJECT ? PROJECT_READER_FACTORY : SETTINGS_READER_FACTORY;
        Deque<ContainerFrame> containers = new ArrayDeque<>();
        String currentPath = "/";
        boolean complete = false;
        JsonParser parser = null;
        try {
            parser = factory.createParser(ObjectReadContext.empty(), utf8, 0, utf8.length);
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (complete) {
                    throw failure("JSON_TRAILING_DATA", source, "/", parser.currentTokenLocation(),
                            "JSON input contains data after the first complete document.");
                }

                if (token == JsonToken.PROPERTY_NAME) {
                    ObjectFrame frame = requireObjectFrame(containers);
                    String name = parser.currentName();
                    currentPath = memberPath(frame.path(), name);
                    enforceTextLimit(source, currentPath, parser.currentTokenLocation(), "member name", name);
                    if (!frame.recordName(name) && !profile.allowsExactDuplicateNamesAt(frame.path())) {
                        throw failure("JSON_MEMBER_DUPLICATE", source, currentPath,
                                parser.currentTokenLocation(),
                                "JSON member '" + name + "' occurs more than once.");
                    }
                    frame.expectValueAt(currentPath);
                } else if (token == JsonToken.START_OBJECT) {
                    currentPath = nextValuePath(containers);
                    consumeValue(containers);
                    containers.push(new ObjectFrame(currentPath));
                } else if (token == JsonToken.START_ARRAY) {
                    currentPath = nextValuePath(containers);
                    consumeValue(containers);
                    containers.push(new ArrayFrame(currentPath));
                } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                    currentPath = containers.pop().path();
                    complete = containers.isEmpty();
                } else {
                    currentPath = nextValuePath(containers);
                    if (token == JsonToken.VALUE_STRING) {
                        enforceTextLimit(source, currentPath, parser.currentTokenLocation(), "string",
                                parser.getString());
                    }
                    consumeValue(containers);
                    complete = containers.isEmpty();
                }
            }
            if (!complete) {
                throw failure("JSON_SYNTAX_INVALID", source, currentPath, parser.currentLocation(),
                        "JSON input does not contain one complete document.");
            }
        } catch (JsonFormatException exception) {
            throw exception;
        } catch (JacksonException exception) {
            String code = isConstraintFailure(exception) ? "JSON_RESOURCE_LIMIT" : "JSON_SYNTAX_INVALID";
            throw failure(code, source, currentPath, bestLocation(exception, parser),
                    exception.getOriginalMessage());
        } finally {
            close(parser);
        }
    }

    /**
     * Parses a Project integer using the exact persisted lexical contract.
     *
     * @param lexeme raw JSON numeric spelling
     * @return signed 32-bit value
     * @throws JsonFormatException when the lexeme is not accepted by {@link Integer#parseInt(String)}
     */
    static int parseProjectInteger(String lexeme) {
        try {
            return Integer.parseInt(lexeme);
        } catch (NumberFormatException exception) {
            throw failure("JSON_INTEGER_INVALID", "", "/", 0, 0,
                    "Project integer must use exact signed 32-bit integer syntax.");
        }
    }

    /** Builds the immutable Jackson limit set for one repository profile. */
    private static StreamReadConstraints constraints(JsonProfile profile) {
        return StreamReadConstraints.builder()
                .maxNestingDepth(MAXIMUM_NESTING_DEPTH)
                .maxDocumentLength(profile.maximumDocumentBytes())
                .maxTokenCount(profile.maximumTokens())
                // Jackson counts decoded UTF-16 units; enforceTextLimit supplies the required UTF-8-byte check.
                .maxNameLength(MAXIMUM_TEXT_BYTES)
                .maxStringLength(MAXIMUM_TEXT_BYTES)
                .maxNumberLength(MAXIMUM_NUMBER_CHARACTERS)
                .build();
    }

    /** Builds one named strict-reader factory; adapters share the completed configuration. */
    private static JsonFactory readerFactory(JsonProfile profile) {
        return JsonFactory.builder().streamReadConstraints(constraints(profile)).build();
    }

    /** Returns the object frame that owns a property token; malformed placement remains a syntax failure. */
    private static ObjectFrame requireObjectFrame(Deque<ContainerFrame> containers) {
        ContainerFrame frame = containers.peek();
        if (frame instanceof ObjectFrame objectFrame)
            return objectFrame;
        throw failure("JSON_SYNTAX_INVALID", "", "/", 0, 0,
                "A JSON member name occurred outside an object.");
    }

    /** Resolves the path where the next scalar or container value will be consumed. */
    private static String nextValuePath(Deque<ContainerFrame> containers) {
        ContainerFrame frame = containers.peek();
        return frame == null ? "/" : frame.nextValuePath();
    }

    /** Advances only the parent frame; a newly started container owns its children independently. */
    private static void consumeValue(Deque<ContainerFrame> containers) {
        ContainerFrame frame = containers.peek();
        if (frame != null)
            frame.consumeValue();
    }

    /** Enforces the policy's UTF-8 byte limit in addition to Jackson's decoded-length guard. */
    private static void enforceTextLimit(String source, String path, TokenStreamLocation location,
            String description, String value) {
        if (exceedsTextLimit(value)) {
            throw failure("JSON_RESOURCE_LIMIT", source, path, location,
                    "JSON " + description + " exceeds the 1 MiB UTF-8 limit.");
        }
    }

    /**
     * Appends one RFC 6901-style escaped member segment to an owned diagnostic path.
     *
     * @param owner escaped path of the containing object
     * @param name unescaped JSON member name
     * @return escaped path of the member
     */
    public static String memberPath(String owner, String name) {
        String escaped = name.replace("~", "~0").replace("/", "~1");
        return "/".equals(owner) ? owner + escaped : owner + "/" + escaped;
    }

    /** Closes the in-memory parser without allowing a codec-specific close failure to escape the boundary. */
    private static void close(JsonParser parser) {
        if (parser == null)
            return;
        try {
            parser.close();
        } catch (JacksonException ignored) {
            // Parsing already consumed an in-memory byte array; there is no external resource left to recover.
        }
    }

    /** Creates a translated failure from an optional Jackson coordinate. */
    private static JsonFormatException failure(String code, String source, String path,
            TokenStreamLocation location, String message) {
        int line = location == null ? 1 : Math.max(1, location.getLineNr());
        int column = location == null ? 1 : Math.max(1, location.getColumnNr());
        return failure(code, source, path, line, column, message);
    }

    /** Creates the stable repository exception with no Jackson cause. */
    private static JsonFormatException failure(String code, String source, String path,
            int line, int column, String message) {
        return new JsonFormatException(code, source, path, line, column, message);
    }

    /** Mutable parse context shared by object and array frames. */
    private interface ContainerFrame {
        /** @return escaped path of this container */
        String path();

        /** @return escaped path where the next child value will be consumed */
        String nextValuePath();

        /** Advances this container after its pending child value has been consumed. */
        void consumeValue();
    }

    /** Tracks exact names and the pending value path for one JSON object. */
    private static final class ObjectFrame implements ContainerFrame {
        private final String path;
        private final Set<String> names = new HashSet<>();
        private String pendingValuePath;

        /** Creates one object frame rooted at its already resolved JSON path. */
        ObjectFrame(String path) {
            this.path = path;
        }

        @Override
        public String path() {
            return path;
        }

        /** Records one exact member name and reports whether this is its first occurrence. */
        boolean recordName(String name) {
            return names.add(name);
        }

        /** Stores the member path that the next scalar or container token must consume. */
        void expectValueAt(String valuePath) {
            pendingValuePath = valuePath;
        }

        @Override
        public String nextValuePath() {
            return pendingValuePath == null ? path : pendingValuePath;
        }

        @Override
        public void consumeValue() {
            pendingValuePath = null;
        }
    }

    /** Tracks the next stable index path for one JSON array. */
    private static final class ArrayFrame implements ContainerFrame {
        private final String path;
        private int nextIndex;

        /** Creates one array frame whose first value has index zero. */
        ArrayFrame(String path) {
            this.path = path;
        }

        @Override
        public String path() {
            return path;
        }

        @Override
        public String nextValuePath() {
            return path + "/" + nextIndex;
        }

        @Override
        public void consumeValue() {
            nextIndex++;
        }
    }
}
