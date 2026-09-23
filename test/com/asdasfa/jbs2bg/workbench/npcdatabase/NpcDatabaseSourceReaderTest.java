package com.asdasfa.jbs2bg.workbench.npcdatabase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.Charset;
import java.util.concurrent.CancellationException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.asdasfa.jbs2bg.data.NPC;

/** Exercises the detached NPC source reader through its file result. */
class NpcDatabaseSourceReaderTest {

    @TempDir
    Path temporaryDirectory;

    /** A valid file stages legacy values without publishing a partially read catalog. */
    @Test
    void readStagesTrimmedRowsWithLegacyFallbacks() throws Exception {
        Path source = temporaryDirectory.resolve("npcs.txt");
        Files.writeString(source, "\n Skyrim.esm | | FemaleNord | NordRace \"Nord\" | 0001A696 | ignored\n"
                + " Update.esm | Lydia | HousecarlWhiterun | NordRace | 00000ABC\n");

        NpcDatabaseSourceReader.ReadResult result = NpcDatabaseSourceReader.read(source);

        assertEquals(NpcDatabaseSourceReader.Status.ACCEPTED, result.status());
        assertEquals(source.toAbsolutePath().normalize(), result.source());
        assertEquals(2, result.rows().size());
        NPC first = result.rows().getFirst();
        assertEquals("Skyrim.esm", first.getMod());
        assertEquals("Unnamed (FemaleNord)", first.getName());
        assertEquals("NordRace", first.getRace());
        assertEquals("1A696", first.getFormId());
        assertTrue(result.diagnostics().isEmpty());
    }

    /** A bad row after a valid row rejects every staged row and reports its source line. */
    @Test
    void malformedRowRejectsWholeSourceWithLineEvidence() throws Exception {
        Path source = temporaryDirectory.resolve("malformed.txt");
        Files.writeString(source, "Master.esm | Valid | ValidId | NordRace | 000001\n"
                + "Master.esm | Bad | BadId | \" | 000002\n"
                + "Master.esm | Later | LaterId | NordRace | 000003\n");

        NpcDatabaseSourceReader.ReadResult result = NpcDatabaseSourceReader.read(source);

        assertEquals(NpcDatabaseSourceReader.Status.MALFORMED, result.status());
        assertTrue(result.rows().isEmpty());
        assertEquals(1, result.diagnostics().size());
        assertEquals(NpcDatabaseSourceReader.MALFORMED_ROW, result.diagnostics().getFirst().code());
        assertEquals(2, result.diagnostics().getFirst().line().orElseThrow());
    }

    /** An unreadable selected path reports a source-local failure rather than producing a source entry. */
    @Test
    void missingFileReportsReadFailureWithoutRows() {
        Path source = temporaryDirectory.resolve("missing.txt");

        NpcDatabaseSourceReader.ReadResult result = NpcDatabaseSourceReader.read(source);

        assertEquals(NpcDatabaseSourceReader.Status.FAILED, result.status());
        assertTrue(result.rows().isEmpty());
        assertEquals(NpcDatabaseSourceReader.READ_FAILED, result.diagnostics().getFirst().code());
        assertEquals(source.toAbsolutePath().normalize(), result.diagnostics().getFirst().source());
        assertTrue(result.diagnostics().getFirst().line().isEmpty());
    }

    /** Accepted cancellation returns no candidate that a caller could accidentally commit. */
    @Test
    void cancellationStopsBeforeReturningAnySourceRows() throws Exception {
        Path source = temporaryDirectory.resolve("cancel.txt");
        Files.writeString(source, "Master.esm | Valid | ValidId | NordRace | 000001\n");

        assertThrows(CancellationException.class, () -> NpcDatabaseSourceReader.read(source, () -> true));
    }

    /** A detected legacy single-byte source keeps non-ASCII display names intact. */
    @Test
    void readDetectsLegacySourceCharset() throws Exception {
        Path source = temporaryDirectory.resolve("legacy-encoded.txt");
        String row = "Master.esm | Café | Cafe01 | NordRace | 000001\n";
        Files.write(source, row.getBytes(Charset.forName("windows-1252")));

        NpcDatabaseSourceReader.ReadResult result = NpcDatabaseSourceReader.read(source);

        assertEquals(NpcDatabaseSourceReader.Status.ACCEPTED, result.status());
        assertEquals("Café", result.rows().getFirst().getName());
    }
}
