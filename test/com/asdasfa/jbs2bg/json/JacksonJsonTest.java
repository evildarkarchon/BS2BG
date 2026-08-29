package com.asdasfa.jbs2bg.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** Verifies the repository-owned strict Jackson profiles independently of production routing. */
final class JacksonJsonTest {

    /** Exact duplicates reject at the escaped member path with one-based source coordinates. */
    @Test
    void duplicateMemberReportsStablePathAndCoordinates() {
        JsonFormatException exception = assertThrows(JsonFormatException.class,
                () -> JacksonJson.validate("fixture.json", JsonProfile.PROJECT,
                        "{\"SliderPresets\":{},\"SliderPresets\":{}}".getBytes(StandardCharsets.UTF_8)));

        assertEquals("JSON_MEMBER_DUPLICATE", exception.code());
        assertEquals("/SliderPresets", exception.path());
        assertEquals(1, exception.line());
        assertTrue(exception.column() > 1);
    }

    /** The one legacy display-name map permits repeats without weakening any other object. */
    @Test
    void projectProfileAllowsOnlyTheLegacyRepeatedNpcDisplayNameLocation() {
        byte[] repeatedNpcNames = ("""
                {"SliderPresets":{},"CustomMorphTargets":{},"MorphedNPCs":{
                  "Guard":{},"Guard":{}
                }}""").getBytes(StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> JacksonJson.validate("fixture.jbs2bg", JsonProfile.PROJECT,
                repeatedNpcNames));
    }

    /** Array indexes and RFC 6901 escaping remain stable when a nested duplicate is diagnosed. */
    @Test
    void nestedDuplicateReportsEscapedArrayMemberPath() {
        JsonFormatException exception = assertThrows(JsonFormatException.class,
                () -> JacksonJson.validate("fixture.json", JsonProfile.SETTINGS,
                        "{\"array\":[{\"a/b~c\":1,\"a/b~c\":2}]}".getBytes(StandardCharsets.UTF_8)));

        assertEquals("/array/0/a~1b~0c", exception.path());
    }

    /** Trailing documents reject without retaining a Jackson exception in the translated cause chain. */
    @Test
    void trailingDocumentReportsSyntaxWithoutLeakingJackson() {
        JsonFormatException exception = assertThrows(JsonFormatException.class,
                () -> JacksonJson.validate("fixture.json", JsonProfile.PROJECT,
                        "{} []".getBytes(StandardCharsets.UTF_8)));

        assertEquals("JSON_TRAILING_DATA", exception.code());
        assertEquals("/", exception.path());
        assertEquals("fixture.json", exception.source());
        assertTrue(exception.getCause() == null
                || !exception.getCause().getClass().getName().startsWith("tools.jackson"));
    }

    /** Project integers accept only the exact signed Java integer lexical and range contract. */
    @Test
    void projectIntegerUsesExactJavaLexicalContract() {
        assertEquals(0, JacksonJson.parseProjectInteger("-0"));
        assertEquals(Integer.MAX_VALUE, JacksonJson.parseProjectInteger("2147483647"));
        assertEquals("JSON_INTEGER_INVALID", assertThrows(JsonFormatException.class,
                () -> JacksonJson.parseProjectInteger("1e0")).code());
        assertEquals("JSON_INTEGER_INVALID", assertThrows(JsonFormatException.class,
                () -> JacksonJson.parseProjectInteger("2147483648")).code());
    }

    /** Nesting beyond the owned depth limit rejects with a trustworthy source coordinate. */
    @Test
    void projectProfileRejectsNestingBeyondOwnedLimit() {
        String document = "[".repeat(65) + "]".repeat(65);
        JsonFormatException exception = assertThrows(JsonFormatException.class,
                () -> JacksonJson.validate("deep.json", JsonProfile.PROJECT,
                        document.getBytes(StandardCharsets.UTF_8)));

        assertEquals("JSON_RESOURCE_LIMIT", exception.code());
        assertEquals("deep.json", exception.source());
        assertTrue(exception.line() > 0);
        assertTrue(exception.column() > 0);
    }

    /** Document bytes, tokens, UTF-8 text bytes, and numeric lexemes each enforce the named Settings profile. */
    @Test
    void settingsProfileEnforcesOwnedDocumentTokenTextAndNumberLimits() {
        byte[] oversizedDocument = new byte[(8 * 1024 * 1024) + 1];
        Arrays.fill(oversizedDocument, (byte) ' ');
        assertResourceLimit(oversizedDocument);

        String tooManyTokens = "[" + "0,".repeat(500_000) + "0]";
        assertResourceLimit(tooManyTokens.getBytes(StandardCharsets.UTF_8));

        String oversizedUtf8Name = "😀".repeat(262_145);
        assertResourceLimit(("{\"" + oversizedUtf8Name + "\":0}").getBytes(StandardCharsets.UTF_8));

        assertResourceLimit(("[" + "1".repeat(129) + "]").getBytes(StandardCharsets.UTF_8));
    }

    /** The permanent Project profile descriptor drives actual 64 MiB and five-million-token boundaries. */
    @Test
    void projectProfileEnforcesItsPermanentDocumentAndTokenLimits() throws IOException {
        Properties limits = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("test-resources", "json-oracles", "project",
                "resource-limits.properties"), StandardCharsets.UTF_8)) {
            limits.load(reader);
        }
        int maximumDocumentBytes = Integer.parseInt(limits.getProperty("maximumDocumentBytes"));
        int maximumTokens = Integer.parseInt(limits.getProperty("maximumTokens"));
        assertEquals(maximumDocumentBytes, JsonProfile.PROJECT.maximumDocumentBytes());
        assertEquals(maximumTokens, JsonProfile.PROJECT.maximumTokens());

        byte[] oversizedDocument = new byte[maximumDocumentBytes + 1];
        Arrays.fill(oversizedDocument, (byte) ' ');
        assertResourceLimit(JsonProfile.PROJECT, oversizedDocument);

        String tooManyTokens = "[" + "0,".repeat(maximumTokens) + "0]";
        assertResourceLimit(JsonProfile.PROJECT, tooManyTokens.getBytes(StandardCharsets.UTF_8));
    }

    /** Verifies one Settings payload rejects through the stable resource-limit translation. */
    private static void assertResourceLimit(byte[] document) {
        assertResourceLimit(JsonProfile.SETTINGS, document);
    }

    /** Verifies one payload rejects through the selected profile's stable resource-limit translation. */
    private static void assertResourceLimit(JsonProfile profile, byte[] document) {
        JsonFormatException exception = assertThrows(JsonFormatException.class,
                () -> JacksonJson.validate("limited.json", profile, document));

        assertEquals("JSON_RESOURCE_LIMIT", exception.code());
        assertEquals("limited.json", exception.source());
        assertTrue(exception.line() > 0);
        assertTrue(exception.column() > 0);
    }
}
