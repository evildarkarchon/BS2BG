package com.asdasfa.jbs2bg.data;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the instance-store contract: every Settings store is bound to one directory and owns its own current pair.
 */
final class SettingsTest {

    /** The store keeps no process-wide state: no static field and no static accessor can hold or read a pair. */
    @Test
    void storeDeclaresNoStaticStateOrStaticAccessors() {
        for (Field field : Settings.class.getDeclaredFields())
            assertFalse(Modifier.isStatic(field.getModifiers()), "static Settings field: " + field.getName());
        for (Method method : Settings.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()))
                assertTrue(Modifier.isPrivate(method.getModifiers()),
                        "non-private static Settings method: " + method.getName());
        }
    }

    /** A store holds no pair until its first reload, so nothing is read from another store or directory. */
    @Test
    void unreloadedStoreHasAnEmptyPairBoundToItsDirectory(@TempDir Path directory) {
        Settings store = new Settings(directory);

        assertEquals(directory.toAbsolutePath().normalize(), store.directory());
        assertTrue(store.snapshot().standard().defaults().isEmpty());
        assertTrue(store.snapshot().uunp().defaults().isEmpty());
        assertTrue(store.snapshot().standard().multipliers().isEmpty());
    }

    /** Reloading or persisting one store never changes the pair another store in another directory holds. */
    @Test
    void storesInDifferentDirectoriesKeepIndependentPairs(@TempDir Path root) throws IOException {
        Path first = Files.createDirectory(root.resolve("first"));
        Path second = Files.createDirectory(root.resolve("second"));
        Settings firstStore = new Settings(first);
        Settings secondStore = new Settings(second);
        assertTrue(firstStore.reload().isSuccessful());
        assertTrue(secondStore.reload().isSuccessful());

        Settings.Snapshot replacement = new Settings.Snapshot(
                new Settings.Profile(Map.of(), Map.of("Waist", Float.valueOf(4f)), List.of()),
                new Settings.Profile(Map.of(), Map.of(), List.of("Arms")));
        assertTrue(firstStore.persist(replacement).isSuccessful());

        assertEquals(replacement, firstStore.snapshot());
        assertEquals(4f, firstStore.getMultiplier("Waist"));
        assertTrue(firstStore.isInvertedUUNP("Arms"));
        assertEquals(1f, secondStore.getMultiplier("Waist"));
        assertEquals(20, secondStore.getDefaultValueSmall("Breasts"));
        assertEquals(12, secondStore.snapshot().standard().defaults().size());
    }
}
