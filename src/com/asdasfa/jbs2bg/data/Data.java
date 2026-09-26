package com.asdasfa.jbs2bg.data;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.prefs.Preferences;

import org.mozilla.universalchardet.UniversalDetector;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 *
 * @author Totiman
 */
public class Data {

    /** Legacy Java Preferences key migrated into the Workbench generation-preference store. */
    public static final String LEGACY_OMIT_REDUNDANT_SLIDERS = "Omit redundant sliders";

    public final ObservableList<NPC> npcDatabase = FXCollections.observableArrayList();
    public final String LAST_USED_FOLDER = "Last used folder";
    public final String LAST_USED_PRESET_FOLDER = "Last used preset folder";
    public final String LAST_USED_NPC_FOLDER = "Last used npc folder";
    public final String LAST_USED_INI_FOLDER = "Last used ini folder";
    public final String LAST_USED_JSON_FOLDER = "Last used json folder";
    public final String OMIT_REDUNDANT_SLIDERS = LEGACY_OMIT_REDUNDANT_SLIDERS;
    public final String encoding = "UTF-8";
    public File homeDir;
    public Preferences prefs;

    public Data() {
        homeDir = new File(System.getProperty("user.home"));

        // The classpath root is a directory only in an exploded (IDE / target/classes) run; from the jar the
        // packaged launcher puts on the classpath, getResource(".") is null (issue #97 smoke run), so the
        // preference node falls back to the class name instead of a per-install-directory node.
        URL classpathRoot = ClassLoader.getSystemClassLoader().getResource(".");
        File jarDir = classpathRoot == null ? null : new File(classpathRoot.getPath());
        String prefPath = getClass().getName();
        if (jarDir != null) {
            prefPath = jarDir.getAbsolutePath();
        }

        try {
            prefs = Preferences.userRoot().node(prefPath);
        } catch (Exception e) { // If the preference path fails, fall back to getClass().getName()
            e.printStackTrace();
            prefs = Preferences.userRoot().node(getClass().getName());
        }

    }

    /**
     * Reads one legacy NPC Database file with the detected charset and deterministic UTF-8 fallback.
     *
     * @param file source file whose unique NPC rows are appended to the session database
     */
    public void parseNpcFile(File file) {
        try {
            String inputEncoding = UniversalDetector.detectCharset(file);
            Charset charset = inputEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(inputEncoding);
            try (BufferedReader reader = Files.newBufferedReader(file.toPath(), charset)) {
                String sourceLine;
                while ((sourceLine = reader.readLine()) != null) {
                    String line = sourceLine.trim();
                    if (line.isEmpty())
                        continue;
                    NPC npc = new NPC(line);
                    if (!npcExistsInDatabase(npc)) {
                        npcDatabase.add(npc);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean npcExistsInDatabase(NPC npc) {
        for (int i = 0; i < npcDatabase.size(); i++) {
            NPC n = npcDatabase.get(i);
            // Same mod AND same editor id
            if (n.getMod().equalsIgnoreCase(npc.getMod()) && n.getEditorId().equalsIgnoreCase(npc.getEditorId()))
                return true;
        }
        return false;
    }

}
