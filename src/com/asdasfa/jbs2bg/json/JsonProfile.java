package com.asdasfa.jbs2bg.json;

/**
 * Named, non-configurable JSON input profiles owned by the repository.
 */
enum JsonProfile {
    PROJECT(64L * 1024L * 1024L, 5_000_000L),
    SETTINGS(8L * 1024L * 1024L, 500_000L);

    private final long maximumDocumentBytes;
    private final long maximumTokens;

    JsonProfile(long maximumDocumentBytes, long maximumTokens) {
        this.maximumDocumentBytes = maximumDocumentBytes;
        this.maximumTokens = maximumTokens;
    }

    long maximumDocumentBytes() {
        return maximumDocumentBytes;
    }

    long maximumTokens() {
        return maximumTokens;
    }

    /**
     * Reports the one legacy Project object whose member names are display labels rather than identities.
     */
    boolean allowsExactDuplicateNamesAt(String path) {
        return this == PROJECT && "/MorphedNPCs".equals(path);
    }
}
