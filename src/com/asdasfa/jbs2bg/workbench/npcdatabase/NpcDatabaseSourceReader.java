package com.asdasfa.jbs2bg.workbench.npcdatabase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

import org.mozilla.universalchardet.UniversalDetector;

import com.asdasfa.jbs2bg.data.NPC;

/** Reads detached legacy NPC Database sources before a catalog decides whether to commit them. */
public final class NpcDatabaseSourceReader {
    public static final String MALFORMED_ROW = "NPC_DATABASE_SOURCE_MALFORMED";
    public static final String READ_FAILED = "NPC_DATABASE_SOURCE_READ_FAILED";

    private NpcDatabaseSourceReader() {
    }

    /** The outcome of staging one complete source. */
    public enum Status {
        ACCEPTED,
        MALFORMED,
        FAILED
    }

    /** Source-local diagnostic with a one-based line when a row caused the failure. */
    public record Diagnostic(String code, Path source, OptionalInt line, String message) {
        /** Requires complete, immutable diagnostic values. */
        public Diagnostic {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(line, "line");
            Objects.requireNonNull(message, "message");
        }
    }

    /** A detached source result; rejected sources expose no staged rows. */
    public record ReadResult(Path source, Status status, List<NPC> rows, List<Diagnostic> diagnostics) {
        /** Copies source rows and diagnostics before a caller can publish them. */
        public ReadResult {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(status, "status");
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
            if (status != Status.ACCEPTED && !rows.isEmpty())
                throw new IllegalArgumentException("Rejected sources cannot expose staged rows");
        }
    }

    /**
     * Stages a complete local NPC source, using UTF-8 when charset detection is inconclusive.
     *
     * @param source selected source file
     * @return accepted rows or a source-local failure with no rows
     */
    public static ReadResult read(Path source) {
        return read(source, () -> false);
    }

    /**
     * Stages a complete source with cancellation checks between rows. A malformed row rejects the whole source.
     *
     * @param source selected source file
     * @param cancellationRequested worker cancellation state
     * @return an immutable result containing rows only when the entire source was accepted
     * @throws CancellationException when cancellation was requested before staging completed
     */
    public static ReadResult read(Path source, BooleanSupplier cancellationRequested) {
        Path normalized = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        Objects.requireNonNull(cancellationRequested, "cancellationRequested");
        List<NPC> rows = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            checkCancellation(cancellationRequested);
            String detected = detectCharset(normalized, cancellationRequested);
            Charset charset = detected == null ? StandardCharsets.UTF_8 : Charset.forName(detected);
            try (BufferedReader reader = Files.newBufferedReader(normalized, charset)) {
                String text;
                int lineNumber = 0;
                while ((text = reader.readLine()) != null) {
                    lineNumber++;
                    checkCancellation(cancellationRequested);
                    String rowText = text.trim();
                    if (rowText.isEmpty())
                        continue;
                    try {
                        rows.add(new NPC(rowText));
                    } catch (IllegalArgumentException exception) {
                        // Keep every bad line's evidence, then reject the entire source after parsing finishes.
                        diagnostics.add(new Diagnostic(MALFORMED_ROW, normalized, OptionalInt.of(lineNumber),
                                "Malformed NPC row: " + exception.getMessage()));
                    }
                }
            }
            checkCancellation(cancellationRequested);
            if (!diagnostics.isEmpty())
                return new ReadResult(normalized, Status.MALFORMED, List.of(), diagnostics);
            return new ReadResult(normalized, Status.ACCEPTED, rows, List.of());
        } catch (IOException | IllegalArgumentException exception) {
            return new ReadResult(normalized, Status.FAILED, List.of(), List.of(new Diagnostic(
                    READ_FAILED, normalized, OptionalInt.empty(), "Could not read NPC source: "
                    + exception.getMessage())));
        }
    }

    /** Detects the charset in bounded chunks so a large source remains cancellable before line parsing begins. */
    private static String detectCharset(Path source, BooleanSupplier cancellationRequested) throws IOException {
        UniversalDetector detector = new UniversalDetector();
        try (InputStream input = Files.newInputStream(source)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                checkCancellation(cancellationRequested);
                detector.handleData(buffer, 0, read);
                if (detector.isDone())
                    break;
            }
        }
        checkCancellation(cancellationRequested);
        detector.dataEnd();
        return detector.getDetectedCharset();
    }

    /** Stops parsing before any detached rows can be returned to a caller. */
    private static void checkCancellation(BooleanSupplier cancellationRequested) {
        if (cancellationRequested.getAsBoolean())
            throw new CancellationException("NPC source import cancelled");
    }
}
