package com.asdasfa.jbs2bg.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.asdasfa.jbs2bg.json.JacksonJson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.TokenStreamLocation;

/** Owns the production JSON format boundary for paired Standard and UUNP Settings documents. */
final class SettingsJacksonAdapter {
    private SettingsJacksonAdapter() {
    }

    /**
     * Parses both Settings sources completely before returning one publishable candidate.
     * No live Settings collection is mutated when either source fails.
     *
     * @param standardSource Standard Settings JSON
     * @param uunpSource UUNP Settings JSON
     * @return detached pair and ordered non-blocking diagnostics
     * @throws SettingsFormatException when either document is invalid
     */
    static SettingsCandidate readPair(Path standardSource, Path uunpSource) {
        Objects.requireNonNull(standardSource, "standardSource");
        Objects.requireNonNull(uunpSource, "uunpSource");
        List<SettingsDiagnostic> diagnostics = new ArrayList<>();
        SettingsProfile standard = read(standardSource, diagnostics);
        SettingsProfile uunp = read(uunpSource, diagnostics);
        return new SettingsCandidate(standard, uunp, diagnostics);
    }

    /**
     * Canonically serializes both profiles before returning one defensively owned byte pair.
     * The method has no filesystem side effects, leaving atomic publication to the owning Settings workflow.
     *
     * @param candidate detached validated Settings candidate
     * @return canonical Standard and UUNP UTF-8 documents
     * @throws NullPointerException when the candidate is null
     * @throws SettingsFormatException when an in-memory value cannot be represented as Settings JSON
     */
    static SettingsPairBytes writePair(SettingsCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        return new SettingsPairBytes(writeProfile(candidate.standard()), writeProfile(candidate.uunp()));
    }

    /**
     * Writes one Settings profile in deterministic schema and encounter order.
     * A single LF terminates the repository-owned canonical form; Settings readers do not depend on whitespace.
     *
     * @param profile immutable profile to encode
     * @return canonical UTF-8 bytes
     * @throws SettingsFormatException when a value is non-finite or Jackson cannot encode the profile
     */
    private static byte[] writeProfile(SettingsProfile profile) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = JacksonJson.canonicalWriterFactory()
                .createGenerator(ObjectWriteContext.empty(), output)) {
            generator.writeStartObject();
            generator.writeName("Defaults");
            generator.writeStartObject();
            for (Map.Entry<String, DefaultValue> entry : profile.defaults().entrySet()) {
                generator.writeName(entry.getKey());
                generator.writeStartObject();
                writeFiniteNumber(generator, "valueSmall", entry.getValue().valueSmall(),
                        child(child("/Defaults", entry.getKey()), "valueSmall"));
                writeFiniteNumber(generator, "valueBig", entry.getValue().valueBig(),
                        child(child("/Defaults", entry.getKey()), "valueBig"));
                generator.writeEndObject();
            }
            generator.writeEndObject();
            generator.writeName("Multipliers");
            generator.writeStartObject();
            for (Map.Entry<String, Float> entry : profile.multipliers().entrySet())
                writeFiniteNumber(generator, entry.getKey(), entry.getValue(), child("/Multipliers", entry.getKey()));
            generator.writeEndObject();
            generator.writeName("Inverted");
            generator.writeStartArray();
            for (String sliderName : profile.inverted())
                generator.writeString(sliderName);
            generator.writeEndArray();
            generator.writeEndObject();
            generator.writeRaw('\n');
        } catch (SettingsFormatException exception) {
            throw exception;
        } catch (JacksonException exception) {
            // ByteArrayOutputStream cannot fail; this translation keeps Jackson out of the Settings seam.
            throw new SettingsFormatException("SETTINGS_WRITE_FAILED", "<detached-settings>", "/", 0, 0,
                    "Settings JSON could not be represented.");
        }
        return output.toByteArray();
    }

    /** Writes one finite float property while retaining Java float spelling, including signed zero. */
    private static void writeFiniteNumber(JsonGenerator generator, String name, float value, String path) {
        if (!Float.isFinite(value))
            throw new SettingsFormatException("SETTINGS_NUMBER_NON_FINITE", "<detached-settings>", path, 0, 0,
                    "Settings number must be finite before writing.");
        generator.writeNumberProperty(name, value);
    }

    /**
     * Reads one Settings source into detached collections, translating I/O and streaming failures before
     * the caller can combine it with the other profile.
     */
    static SettingsProfile read(Path source, List<SettingsDiagnostic> diagnostics) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(source);
        } catch (IOException exception) {
            throw failure("SETTINGS_IO_FAILED", source, "/", 0, 0, exception.getMessage());
        }
        if (bytes.length > JacksonJson.settingsMaximumDocumentBytes()) {
            throw failure("SETTINGS_RESOURCE_LIMIT", source, "/", 1, 1,
                    "Settings input exceeds the 8 MiB limit.");
        }
        try (JsonParser parser = JacksonJson.settingsReaderFactory()
                .createParser(ObjectReadContext.empty(), bytes, 0, bytes.length)) {
            return readProfile(parser, source, diagnostics);
        } catch (SettingsFormatException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw jacksonFailure(exception, source, "/", exception.getLocation());
        }
    }

    /**
     * Parses one complete Settings token stream while the parser remains available for location fallback.
     * Some Jackson constraint exceptions omit their own location, so translating inside this lifetime preserves
     * the last trustworthy parser coordinate.
     */
    private static SettingsProfile readProfile(JsonParser parser, Path source,
            List<SettingsDiagnostic> diagnostics) {
        try {
            require(parser.nextToken(), JsonToken.START_OBJECT, source, "/", parser);
            Map<String, DefaultValue> defaults = new LinkedHashMap<>();
            Map<String, Float> multipliers = new LinkedHashMap<>();
            List<String> inverted = new ArrayList<>();
            Set<String> rootNames = new LinkedHashSet<>();
            JsonToken token;
            while ((token = parser.nextToken()) != JsonToken.END_OBJECT) {
                require(token, JsonToken.PROPERTY_NAME, source, "/", parser);
                String name = requireName(parser, source, "/");
                String path = child("/", name);
                if (!rootNames.add(name))
                    throw duplicate(source, path, parser);
                JsonToken valueToken = parser.nextToken();
                try {
                    switch (name) {
                        case "Defaults" -> readDefaults(parser, valueToken, source, path, defaults, diagnostics);
                        case "Multipliers" -> readMultipliers(parser, valueToken, source, path, multipliers);
                        case "Inverted" -> readInverted(parser, valueToken, source, path, inverted);
                        default -> {
                            diagnostics.add(new SettingsDiagnostic("SETTINGS_MEMBER_UNKNOWN", source.toString(), path,
                                    "Unknown Settings member '" + name + "' was ignored."));
                            parser.skipChildren();
                        }
                    }
                } catch (JacksonException exception) {
                    TokenStreamLocation location = exception.getLocation() == null
                            ? parser.currentLocation() : exception.getLocation();
                    throw jacksonFailure(exception, source, path, location);
                }
            }
            for (String required : List.of("Defaults", "Multipliers", "Inverted")) {
                if (!rootNames.contains(required))
                    throw failure("SETTINGS_MEMBER_MISSING", source, child("/", required),
                            parser.currentLocation(), "Settings member '" + required + "' is required.");
            }
            if (parser.nextToken() != null)
                throw failure("SETTINGS_TRAILING_DATA", source, "/", parser.currentTokenLocation(),
                        "Settings input contains trailing data.");
            return new SettingsProfile(defaults, multipliers, inverted);
        } catch (SettingsFormatException exception) {
            throw exception;
        } catch (JacksonException exception) {
            TokenStreamLocation location = exception.getLocation() == null
                    ? parser.currentLocation() : exception.getLocation();
            throw jacksonFailure(exception, source, "/", location);
        }
    }

    /** Translates a Jackson syntax or constraint failure to the stable Settings diagnostic vocabulary. */
    private static SettingsFormatException jacksonFailure(JacksonException exception, Path source, String path,
            TokenStreamLocation location) {
        String code = JacksonJson.isConstraintFailure(exception)
                ? "SETTINGS_RESOURCE_LIMIT" : "SETTINGS_JSON_MALFORMED";
        return failure(code, source, path, location, exception.getOriginalMessage());
    }

    /**
     * Reads dynamic slider defaults while applying the legacy endpoint defaults and retaining
     * forward-compatible warnings for unknown fields inside each fixed-schema slider object.
     */
    private static void readDefaults(JsonParser parser, JsonToken token, Path source, String path,
            Map<String, DefaultValue> destination, List<SettingsDiagnostic> diagnostics) {
        require(token, JsonToken.START_OBJECT, source, path, parser);
        Set<String> names = new LinkedHashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = requireName(parser, source, path);
            String memberPath = child(path, name);
            if (!names.add(name))
                throw duplicate(source, memberPath, parser);
            require(parser.nextToken(), JsonToken.START_OBJECT, source, memberPath, parser);
            float small = 0f;
            float big = 1f;
            Set<String> fields = new LinkedHashSet<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = requireName(parser, source, memberPath);
                String fieldPath = child(memberPath, field);
                if (!fields.add(field))
                    throw duplicate(source, fieldPath, parser);
                JsonToken valueToken = parser.nextToken();
                if ("valueSmall".equals(field))
                    small = finiteFloat(parser, valueToken, source, fieldPath);
                else if ("valueBig".equals(field))
                    big = finiteFloat(parser, valueToken, source, fieldPath);
                else {
                    diagnostics.add(new SettingsDiagnostic("SETTINGS_MEMBER_UNKNOWN", source.toString(), fieldPath,
                            "Unknown Settings member '" + field + "' was ignored."));
                    parser.skipChildren();
                }
            }
            destination.put(name, new DefaultValue(small, big));
        }
    }

    /** Reads dynamic finite multiplier values while rejecting exact duplicate Slider keys. */
    private static void readMultipliers(JsonParser parser, JsonToken token, Path source, String path,
            Map<String, Float> destination) {
        require(token, JsonToken.START_OBJECT, source, path, parser);
        Set<String> names = new LinkedHashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = requireName(parser, source, path);
            String memberPath = child(path, name);
            if (!names.add(name))
                throw duplicate(source, memberPath, parser);
            destination.put(name, finiteFloat(parser, parser.nextToken(), source, memberPath));
        }
    }

    /** Reads string identities and canonically deduplicates them by first case-insensitive encounter. */
    private static void readInverted(JsonParser parser, JsonToken token, Path source, String path,
            List<String> destination) {
        require(token, JsonToken.START_ARRAY, source, path, parser);
        Set<String> identities = new LinkedHashSet<>();
        int index = 0;
        while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
            String itemPath = path + "/" + index++;
            require(token, JsonToken.VALUE_STRING, source, itemPath, parser);
            String value = parser.getString();
            requireTextLimit(source, itemPath, value, "string", parser);
            if (identities.add(value.toLowerCase(Locale.ROOT)))
                destination.add(value);
        }
    }

    /** Converts an ordinary JSON number through the accepted finite Java-float compatibility path. */
    private static float finiteFloat(JsonParser parser, JsonToken token, Path source, String path) {
        if (token != JsonToken.VALUE_NUMBER_INT && token != JsonToken.VALUE_NUMBER_FLOAT)
            throw failure("SETTINGS_VALUE_TYPE_INVALID", source, path, parser.currentTokenLocation(),
                    "Settings value must be a JSON number.");
        float value = Float.parseFloat(parser.getString());
        if (!Float.isFinite(value))
            throw failure("SETTINGS_NUMBER_NON_FINITE", source, path, parser.currentTokenLocation(),
                    "Settings number must convert to a finite Java float.");
        return value;
    }

    /** Returns one property name after token-kind and UTF-8 resource-limit validation. */
    private static String requireName(JsonParser parser, Path source, String ownerPath) {
        require(parser.currentToken(), JsonToken.PROPERTY_NAME, source, ownerPath, parser);
        String name = parser.currentName();
        requireTextLimit(source, child(ownerPath, name), name, "member name", parser);
        return name;
    }

    /** Requires one exact streaming token kind without enabling Jackson coercion. */
    private static void require(JsonToken actual, JsonToken expected, Path source, String path,
            JsonParser parser) {
        if (actual != expected)
            throw failure("SETTINGS_VALUE_TYPE_INVALID", source, path, parser.currentTokenLocation(),
                    "Expected " + expected + " but found " + actual + ".");
    }

    /** Creates the stable duplicate-member rejection at the member token's coordinate. */
    private static SettingsFormatException duplicate(Path source, String path, JsonParser parser) {
        return failure("SETTINGS_MEMBER_DUPLICATE", source, path, parser.currentTokenLocation(),
                "Settings member occurs more than once.");
    }

    /** Rejects a member name or string value that crosses the shared one-MiB UTF-8 limit. */
    private static void requireTextLimit(Path source, String path, String value, String kind, JsonParser parser) {
        if (JacksonJson.exceedsTextLimit(value)) {
            throw failure("SETTINGS_RESOURCE_LIMIT", source, path, parser.currentTokenLocation(),
                    "Settings " + kind + " exceeds the 1 MiB UTF-8 limit.");
        }
    }

    /** Appends one RFC 6901-style escaped member segment to a Settings diagnostic path. */
    private static String child(String owner, String name) {
        return JacksonJson.memberPath(owner, name);
    }

    /** Translates an optional Jackson coordinate into a stable Settings failure. */
    private static SettingsFormatException failure(String code, Path source, String path,
            TokenStreamLocation location, String message) {
        return failure(code, source, path, location == null ? 0 : Math.max(1, location.getLineNr()),
                location == null ? 0 : Math.max(1, location.getColumnNr()), message);
    }

    /** Creates the codec-free Settings exception at already normalized coordinates. */
    private static SettingsFormatException failure(String code, Path source, String path,
            int line, int column, String message) {
        return new SettingsFormatException(code, source.toString(), path, line, column, message);
    }

    /** One Settings slider's effective endpoint defaults after omission handling. */
    record DefaultValue(float valueSmall, float valueBig) {
    }

    /** Detached immutable Settings profile in canonical encounter order. */
    record SettingsProfile(Map<String, DefaultValue> defaults, Map<String, Float> multipliers,
            List<String> inverted) {
        SettingsProfile {
            defaults = Collections.unmodifiableMap(new LinkedHashMap<>(defaults));
            multipliers = Collections.unmodifiableMap(new LinkedHashMap<>(multipliers));
            inverted = List.copyOf(inverted);
        }
    }

    /** Ordered forward-compatibility warning for one ignored Settings member. */
    record SettingsDiagnostic(String code, String source, String path, String message) {
    }

    /** Pairwise publication unit containing both validated profiles and their ordered warnings. */
    record SettingsCandidate(SettingsProfile standard, SettingsProfile uunp,
            List<SettingsDiagnostic> diagnostics) {
        SettingsCandidate {
            diagnostics = List.copyOf(diagnostics);
        }
    }

    /** Immutable, defensively owned canonical bytes for pairwise Settings publication. */
    static final class SettingsPairBytes {
        private final byte[] standardUtf8;
        private final byte[] uunpUtf8;

        /** Takes defensive ownership so neither the writer nor a caller can alter published bytes. */
        private SettingsPairBytes(byte[] standardUtf8, byte[] uunpUtf8) {
            this.standardUtf8 = standardUtf8.clone();
            this.uunpUtf8 = uunpUtf8.clone();
        }

        /** Returns an independent copy of the canonical Standard Settings document. */
        byte[] standardUtf8() {
            return standardUtf8.clone();
        }

        /** Returns an independent copy of the canonical UUNP Settings document. */
        byte[] uunpUtf8() {
            return uunpUtf8.clone();
        }
    }

    /** Stable Settings failure translated at the format-adapter boundary. */
    static final class SettingsFormatException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final String source;
        private final String path;
        private final int line;
        private final int column;

        /** Creates one codec-free Settings failure at a stable source path and coordinate. */
        SettingsFormatException(String code, String source, String path, int line, int column, String message) {
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
}
