package com.asdasfa.jbs2bg.presentation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** Maps Slider Preset names to deterministic Windows-safe BoS artifact filenames. */
final class BosFileNamePolicy {
    private static final String JSON_EXTENSION = ".json";
    private static final int MAX_COMPONENT_UTF16_CODE_UNITS = 255;
    private static final int HASH_HEX_LENGTH = 16;
    private static final Pattern RESERVED_BASENAME = Pattern.compile(
            "(?:CON|PRN|AUX|NUL|CLOCK\\$|COM[1-9¹²³]|LPT[1-9¹²³])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private BosFileNamePolicy() {
    }

    /**
     * Preserves safe names, percent-encodes unsafe UTF-8 bytes, and hashes only
     * when the complete component would exceed the owned length policy.
     *
     * @param sliderPresetName source Slider Preset display name
     * @return safe filename including the {@code .json} extension
     * @throws IllegalArgumentException when the name is empty or contains unpaired surrogates
     */
    static String map(String sliderPresetName) {
        if (sliderPresetName.isEmpty())
            throw new IllegalArgumentException("A BoS filename requires a non-empty Slider Preset name.");
        validateUnicodeScalars(sliderPresetName);

        int trailingUnsafeStart = trailingUnsafeStart(sliderPresetName);
        StringBuilder mapped = new StringBuilder(sliderPresetName.length());
        for (int offset = 0; offset < sliderPresetName.length();) {
            int codePoint = sliderPresetName.codePointAt(offset);
            if (offset >= trailingUnsafeStart || isUnsafe(codePoint))
                appendPercentEncoded(mapped, codePoint);
            else
                mapped.appendCodePoint(codePoint);
            offset += Character.charCount(codePoint);
        }

        if (isReservedBasename(mapped.toString())) {
            int firstCodePoint = mapped.codePointAt(0);
            StringBuilder safeReservedName = new StringBuilder(mapped.length() + 2);
            appendPercentEncoded(safeReservedName, firstCodePoint);
            safeReservedName.append(mapped, Character.charCount(firstCodePoint), mapped.length());
            mapped = safeReservedName;
        }

        String candidate = mapped + JSON_EXTENSION;
        if (candidate.length() <= MAX_COMPONENT_UTF16_CODE_UNITS)
            return candidate;

        String suffix = "~" + stableHash(sliderPresetName);
        int baseBudget = MAX_COMPONENT_UTF16_CODE_UNITS - suffix.length() - JSON_EXTENSION.length();
        return truncateMappedName(mapped.toString(), baseBudget) + suffix + JSON_EXTENSION;
    }

    /** Rejects malformed UTF-16 that cannot identify deterministic UTF-8 bytes. */
    private static void validateUnicodeScalars(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1)))
                    throw new IllegalArgumentException("A BoS filename contains an unpaired high surrogate.");
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("A BoS filename contains an unpaired low surrogate.");
            }
        }
    }

    /** Locates the first trailing dot or space because Windows strips those code points. */
    private static int trailingUnsafeStart(String value) {
        int start = value.length();
        while (start > 0) {
            int codePoint = value.codePointBefore(start);
            if (codePoint != '.' && codePoint != ' ')
                break;
            start -= Character.charCount(codePoint);
        }
        return start;
    }

    /** Reports whether a code point cannot appear literally in an owned Windows filename. */
    private static boolean isUnsafe(int codePoint) {
        return codePoint < 0x20 || codePoint == '%' || codePoint == '<' || codePoint == '>'
                || codePoint == ':' || codePoint == '"' || codePoint == '/' || codePoint == '\\'
                || codePoint == '|' || codePoint == '?' || codePoint == '*';
    }

    /** Appends one code point as uppercase percent-encoded UTF-8 bytes. */
    private static void appendPercentEncoded(StringBuilder output, int codePoint) {
        byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            output.append('%');
            output.append(Character.toUpperCase(Character.forDigit((value >>> 4) & 0x0F, 16)));
            output.append(Character.toUpperCase(Character.forDigit(value & 0x0F, 16)));
        }
    }

    /** Applies Windows device-name matching before the first literal dot. */
    private static boolean isReservedBasename(String mappedName) {
        int dot = mappedName.indexOf('.');
        String basename = dot >= 0 ? mappedName.substring(0, dot) : mappedName;
        return RESERVED_BASENAME.matcher(basename.toUpperCase(Locale.ROOT)).matches();
    }

    /** Truncates at code-point or percent-triplet boundaries within a UTF-16 component budget. */
    private static String truncateMappedName(String mappedName, int codeUnitBudget) {
        StringBuilder truncated = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < mappedName.length();) {
            int tokenLength;
            if (mappedName.charAt(offset) == '%') {
                tokenLength = 3;
            } else {
                int codePoint = mappedName.codePointAt(offset);
                tokenLength = Character.charCount(codePoint);
            }
            if (used + tokenLength > codeUnitBudget)
                break;
            truncated.append(mappedName, offset, offset + tokenLength);
            used += tokenLength;
            offset += tokenLength;
        }
        return truncated.toString();
    }

    /** Produces the stable short suffix used only for overlong components. */
    private static String stableHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, HASH_HEX_LENGTH / 2);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is mandatory in every Java implementation; absence means the runtime is unusable.
            throw new IllegalStateException("The Java runtime does not provide SHA-256.", exception);
        }
    }
}
