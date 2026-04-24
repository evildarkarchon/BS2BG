package com.asdasfa.jbs2bg.testharness;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import com.asdasfa.jbs2bg.data.CustomMorphTarget;
import com.asdasfa.jbs2bg.data.Data;
import com.asdasfa.jbs2bg.data.MorphTarget;
import com.asdasfa.jbs2bg.data.NPC;
import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.SliderPreset;

/**
 * Headless driver that regenerates the contents of tests/fixtures/expected/ by
 * calling the same model + serialization paths the GUI uses, without touching
 * JavaFX Stage / FXMLLoader.
 *
 * Launched by tests/tools/generate-expected.ps1 once per fixture scenario.
 *
 * IMPORTANT: This driver assumes the process CWD contains settings.json and
 * settings_UUNP.json, because Settings.init() does `new File("settings.json")`.
 * The PowerShell caller is responsible for chdir'ing to an ephemeral workdir
 * that holds those profile files.
 *
 * CLI:
 *   java ... FixtureDriver --xml-dir &lt;dir&gt; [--npcs &lt;file&gt;] --out &lt;dir&gt;
 */
public class FixtureDriver {

    public static void main(String[] args) throws Exception {
        Map<String, String> opts = parseArgs(args);
        File xmlDir = new File(require(opts, "--xml-dir"));
        File outDir = new File(require(opts, "--out"));
        String npcsPath = opts.get("--npcs");

        int ok = Settings.init();
        if (ok <= 0) {
            throw new IllegalStateException("Settings.init failed with code " + ok
                + ". Ensure CWD contains settings.json and settings_UUNP.json.");
        }

        Data data = new Data();

        File[] xmls = xmlDir.listFiles((d, name) -> name.toLowerCase().endsWith(".xml"));
        if (xmls == null || xmls.length == 0) {
            throw new IllegalStateException("No XML presets found in " + xmlDir);
        }
        Arrays.sort(xmls, Comparator.comparing(File::getName));
        for (File f : xmls) {
            data.parseXmlPreset(f);
        }
        data.sortPresets();

        if (npcsPath != null) {
            File npcFile = new File(npcsPath);
            data.parseNpcFile(npcFile);

            // Move parsed NPCs from npcDatabase to morphedNpcs with deterministic
            // round-robin preset assignment. This differs from the GUI's
            // ThreadLocalRandom-based "Assign Random" in PopupNpcDatabaseController,
            // but gives reproducible output for byte-identical golden-file tests.
            List<SliderPreset> presets = new ArrayList<>(data.sliderPresets);
            int i = 0;
            for (NPC npc : new ArrayList<>(data.npcDatabase)) {
                if (!presets.isEmpty()) {
                    npc.addSliderPreset(presets.get(i % presets.size()));
                }
                data.morphedNpcs.add(npc);
                i++;
            }
            data.npcDatabase.clear();

            // Deterministic custom morph target, only for fixtures that load NPCs.
            CustomMorphTarget cmt = new CustomMorphTarget("All|Female");
            if (!presets.isEmpty()) {
                cmt.addSliderPreset(presets.get(0));
                if (presets.size() > 1) {
                    cmt.addSliderPreset(presets.get(1));
                }
                cmt.sortPresets();
            }
            data.customMorphTargets.add(cmt);
        }

        outDir.mkdirs();
        new File(outDir, "bos-json").mkdirs();

        // project.jbs2bg
        File projectFile = new File(outDir, "project.jbs2bg");
        data.saveToFile(projectFile);

        // templates.ini (omit=false) and templates-omit.ini (omit=true)
        writeTemplates(data, new File(outDir, "templates.ini"), false);
        writeTemplates(data, new File(outDir, "templates-omit.ini"), true);

        // morphs.ini
        writeMorphs(data, new File(outDir, "morphs.ini"));

        // BoS JSON per preset
        File bosDir = new File(outDir, "bos-json");
        for (SliderPreset p : data.sliderPresets) {
            File f = new File(bosDir, sanitize(p.getName()) + ".json");
            FileUtils.writeStringToFile(f, p.toBosJson(), "UTF-8");
        }

        System.out.println("Wrote " + outDir.getAbsolutePath());
    }

    /**
     * Mirror of MainController.doGenerateTemplates + doExport, minus TextArea.
     */
    private static void writeTemplates(Data data, File out, boolean omit) throws Exception {
        String newline = System.getProperty("line.separator");
        StringBuilder sb = new StringBuilder();
        List<SliderPreset> presets = new ArrayList<>(data.sliderPresets);
        for (int i = 0; i < presets.size(); i++) {
            sb.append(presets.get(i).toLine(omit));
            if (i < presets.size() - 1) sb.append(newline);
        }
        FileUtils.writeStringToFile(out, sb.toString(), "UTF-8");
    }

    /**
     * Mirror of MainController.doGenerateMorphs: custom targets in insertion
     * order, then NPCs sorted by mod (case-insensitive).
     */
    private static void writeMorphs(Data data, File out) throws Exception {
        String newline = System.getProperty("line.separator");
        List<String> lines = new ArrayList<>();
        for (CustomMorphTarget cmt : data.customMorphTargets) {
            lines.add(cmt.toLine());
        }
        List<NPC> npcs = new ArrayList<>(data.morphedNpcs);
        npcs.sort(Comparator.comparing(NPC::getMod, String.CASE_INSENSITIVE_ORDER));
        for (NPC n : npcs) lines.add(n.toLine());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(lines.get(i));
            if (i < lines.size() - 1) sb.append(newline);
        }
        FileUtils.writeStringToFile(out, sb.toString(), "UTF-8");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[<>:\"/\\\\|?*]", "_");
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            out.put(args[i], args[i + 1]);
        }
        return out;
    }

    private static String require(Map<String, String> opts, String key) {
        String v = opts.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required arg " + key);
        return v;
    }
}
