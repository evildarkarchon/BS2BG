package com.asdasfa.jbs2bg.presentation;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.asdasfa.jbs2bg.project.ProjectDiagnostic;
import com.asdasfa.jbs2bg.project.SourceLocation;

/**
 * Formats complete structured Project diagnostics for presentation without losing source coordinates.
 */
public final class ProjectDiagnosticFormatter {

    private ProjectDiagnosticFormatter() {
    }

    /**
     * Formats diagnostics in source order with severity, stable code, path, element, line, column, and message.
     *
     * @param diagnostics structured diagnostics to format
     * @return a platform-neutral multi-line representation, or an empty string when no diagnostics exist
     */
    public static String format(List<ProjectDiagnostic> diagnostics) {
        StringBuilder text = new StringBuilder();
        for (ProjectDiagnostic diagnostic : List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"))) {
            if (text.length() > 0)
                text.append(System.lineSeparator());
            text.append(diagnostic.getSeverity()).append(" [").append(diagnostic.getCode()).append("] ");
            appendLocation(text, diagnostic.getSourceLocation());
            text.append(": ").append(diagnostic.getMessage());
        }
        return text.toString();
    }

    /**
     * Appends every available stable coordinate from one immutable Project source location.
     */
    private static void appendLocation(StringBuilder text, SourceLocation location) {
        boolean hasLocation = false;
        if (location.getPath().isPresent()) {
            Path path = location.getPath().orElseThrow();
            Path fileName = path.getFileName();
            text.append(fileName == null ? path : fileName);
            hasLocation = true;
        }
        if (location.getElement().isPresent()) {
            String element = location.getElement().orElseThrow();
            if (!hasLocation || !"/".equals(element)) {
                if (hasLocation)
                    text.append(element.startsWith("/") ? " " : " / ");
                text.append(element);
                hasLocation = true;
            }
        }
        if (location.getLine().isPresent()) {
            if (hasLocation)
                text.append(' ');
            text.append("(line ").append(location.getLine().getAsInt());
            if (location.getColumn().isPresent())
                text.append(", column ").append(location.getColumn().getAsInt());
            text.append(')');
            hasLocation = true;
        }
        if (!hasLocation)
            text.append("Project");
    }
}
