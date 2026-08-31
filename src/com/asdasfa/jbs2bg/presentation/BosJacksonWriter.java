package com.asdasfa.jbs2bg.presentation;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.asdasfa.jbs2bg.json.JacksonJson;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.SerializableString;
import tools.jackson.core.io.CharacterEscapes;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.core.util.Separators;
import tools.jackson.core.util.Separators.Spacing;

/**
 * Package-owned Jackson streaming writer for the exact BoS byte contract.
 */
final class BosJacksonWriter {
    private static final Pattern JSON_NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    private static final JsonFactory JSON_FACTORY = JacksonJson.canonicalWriterFactory().rebuild()
            .characterEscapes(new BosCharacterEscapes())
            .build();
    private static final ObjectWriteContext WRITE_CONTEXT = new BosWriteContext();

    private BosJacksonWriter() {
    }

    /**
     * Writes one already-calculated BoS document as canonical UTF-8 without BOM or a final newline.
     * Numeric lexemes are validated before any output is returned.
     *
     * @param document immutable ordered BoS values
     * @return defensively owned canonical JSON
     * @throws IllegalArgumentException for mismatched lists or unrepresentable numeric lexemes
     */
    static Utf8Json write(BosDocument document) {
        Objects.requireNonNull(document, "document");
        if (document.sliderNames().size() != document.highValues().size()
                || document.sliderNames().size() != document.lowValues().size()) {
            throw new IllegalArgumentException("BoS slider names and endpoint values must have equal sizes.");
        }
        for (String lexeme : document.highValues())
            validateNumber(lexeme);
        for (String lexeme : document.lowValues())
            validateNumber(lexeme);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(WRITE_CONTEXT, output)) {
            generator.writeStartObject();
            generator.writeName("string");
            generator.writeStartObject();
            generator.writeStringProperty("bodyname", document.bodyName());
            for (int index = 0; index < document.sliderNames().size(); index++)
                generator.writeStringProperty("slidername" + (index + 1), document.sliderNames().get(index));
            generator.writeEndObject();
            generator.writeName("int");
            generator.writeStartObject();
            generator.writeNumberProperty("slidersnumber", document.sliderNames().size());
            generator.writeEndObject();
            generator.writeName("float");
            generator.writeStartObject();
            for (int index = 0; index < document.highValues().size(); index++) {
                generator.writeName("highvalue" + (index + 1));
                generator.writeNumber(document.highValues().get(index));
            }
            for (int index = 0; index < document.lowValues().size(); index++) {
                generator.writeName("lowvalue" + (index + 1));
                generator.writeNumber(document.lowValues().get(index));
            }
            generator.writeEndObject();
            generator.writeEndObject();
        } catch (JacksonException exception) {
            // The in-memory sink cannot fail; translate Jackson's unchecked stream failure at the adapter boundary.
            throw new IllegalArgumentException("BoS JSON could not be represented.");
        }
        return new Utf8Json(output.toByteArray());
    }

    /**
     * Rejects numeric spellings that cannot be emitted as finite JSON numbers without normalization.
     */
    private static void validateNumber(String lexeme) {
        if (lexeme == null || !JSON_NUMBER.matcher(lexeme).matches())
            throw new IllegalArgumentException("BoS numeric lexeme is not valid JSON: " + lexeme);
        double value = Double.parseDouble(lexeme);
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("BoS numeric lexeme must be finite: " + lexeme);
    }

    /**
     * Ordered, already-calculated BoS values passed across the presentation-owned writer seam.
     * Lists are copied so validation and byte generation observe one immutable value.
     *
     * @param bodyName    output body name
     * @param sliderNames ordered slider names
     * @param highValues  ordered high-endpoint JSON number lexemes
     * @param lowValues   ordered low-endpoint JSON number lexemes
     */
    record BosDocument(String bodyName, List<String> sliderNames, List<String> highValues,
                       List<String> lowValues) {
        BosDocument {
            bodyName = Objects.requireNonNull(bodyName, "bodyName");
            sliderNames = List.copyOf(sliderNames);
            highValues = List.copyOf(highValues);
            lowValues = List.copyOf(lowValues);
        }
    }

    private static final class BosWriteContext extends ObjectWriteContext.Base {
        /**
         * Builds the exact BoS whitespace profile instead of inheriting a library
         * pretty-printer contract that could change independently.
         *
         * @return two-space, LF-only printer with spaces after name separators
         */
        @Override
        public PrettyPrinter getPrettyPrinter() {
            Separators separators = Separators.createDefaultInstance()
                    .withObjectNameValueSpacing(Spacing.AFTER)
                    .withObjectEmptySeparator("");
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(separators);
            printer.indentObjectsWith(new DefaultIndenter("  ", "\n"));
            return printer;
        }

        /**
         * @return true so every generator created with this context installs the BoS printer
         */
        @Override
        public boolean hasPrettyPrinter() {
            return true;
        }
    }

    private static final class BosCharacterEscapes extends CharacterEscapes {
        private static final long serialVersionUID = 1L;
        private final int[] asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();

        /**
         * @return a defensive copy of the minimal-json-compatible ASCII escape table
         */
        @Override
        public int[] getEscapeCodesForAscii() {
            return asciiEscapes.clone();
        }

        /**
         * Escapes JavaScript line separators that minimal-json historically emitted
         * as lowercase Unicode escapes while leaving ordinary Unicode as UTF-8.
         *
         * @param character Unicode code point considered by Jackson
         * @return the owned escape spelling, or null for ordinary Unicode
         */
        @Override
        public SerializableString getEscapeSequence(int character) {
            if (character == 0x2028)
                return new SerializedString("\\u2028");
            if (character == 0x2029)
                return new SerializedString("\\u2029");
            return null;
        }
    }
}
