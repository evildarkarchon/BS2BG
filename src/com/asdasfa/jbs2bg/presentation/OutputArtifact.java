package com.asdasfa.jbs2bg.presentation;

/**
 * One immutable generated Output document whose preview and publication share the same owned UTF-8 bytes.
 */
public interface OutputArtifact {

    /** @return safe destination filename for this artifact */
    String getFileName();

    /** @return a defensive copy of the accepted UTF-8 bytes */
    byte[] getBytes();

    /** @return preview text decoded from the same accepted bytes */
    String getText();
}
