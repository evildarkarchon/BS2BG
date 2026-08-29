package com.asdasfa.jbs2bg.presentation;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Immutable canonical JSON value that owns UTF-8 bytes without a byte-order mark. */
final class Utf8Json {
    private final byte[] bytes;

    /**
     * Takes a defensive copy of one complete canonical JSON document.
     *
     * @param bytes canonical UTF-8 JSON bytes
     */
    Utf8Json(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
    }

    /** @return a defensive copy of the canonical UTF-8 bytes */
    byte[] bytes() {
        return bytes.clone();
    }

    /** @return the canonical document decoded from the same bytes used for publication */
    String text() {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
