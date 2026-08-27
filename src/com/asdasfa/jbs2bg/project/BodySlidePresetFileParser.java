package com.asdasfa.jbs2bg.project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.asdasfa.jbs2bg.data.Settings;
import com.asdasfa.jbs2bg.data.Settings.DefaultSliderValue;

/**
 * Parses one BodySlide XML file into detached immutable Slider Preset values.
 * No Project state is changed until the caller accepts the complete result.
 */
final class BodySlidePresetFileParser {

    private BodySlidePresetFileParser() {
    }

    /**
     * Parses a complete BodySlide source using a hardened, per-call DOM builder.
     *
     * @param source XML file to parse
     * @return detached Slider Presets and name locations in canonical name order
     * @throws IOException when the source cannot be read
     * @throws SAXException when XML syntax is malformed
     * @throws ParserConfigurationException when secure XML parsing is unavailable
     * @throws InvalidBodySlidePresetException when valid XML violates import rules
     */
    static List<ParsedPreset> parse(Path source)
            throws IOException, SAXException, ParserConfigurationException {
        DocumentBuilderFactory factory = secureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler() {
            /** Keeps parser diagnostics structured instead of printing them to stderr. */
            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            /** Keeps fatal parser diagnostics structured instead of printing them to stderr. */
            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        Document document = builder.parse(source.toFile());
        document.getDocumentElement().normalize();
        Element root = document.getDocumentElement();
        if (root == null || !"SliderPresets".equalsIgnoreCase(root.getNodeName()))
            throw new InvalidBodySlidePresetException(ProjectDiagnosticCodes.SLIDER_PRESET_XML_STRUCTURE_INVALID,
                    "/", "The BodySlide XML root must be SliderPresets.");

        Map<String, ParsedPreset> presets = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        NodeList presetNodes = root.getElementsByTagName("Preset");
        for (int index = 0; index < presetNodes.getLength(); index++) {
            Node node = presetNodes.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            String presetElement = "/SliderPresets/Preset[" + (index + 1) + "]";
            ParsedPreset candidate = parsePreset((Element) node, presetElement);
            ParsedPreset previous = presets.get(candidate.getPreset().getName());
            if (previous != null) {
                // Legacy import treats repeated logical names as later payloads for the
                // same preset, retaining the first display identity.
                SliderPresetSnapshot candidateValue = candidate.getPreset();
                candidate = new ParsedPreset(new SliderPresetSnapshot(previous.getPreset().getName(),
                        candidateValue.isUunp(), candidateValue.getSliderChoices()), candidate.getNameElement());
            }
            presets.put(candidate.getPreset().getName(), candidate);
        }
        return Collections.unmodifiableList(new ArrayList<>(presets.values()));
    }

    /**
     * Creates a parser configuration that cannot resolve document-controlled
     * external entities or DTDs.
     *
     * @return hardened DOM factory
     * @throws ParserConfigurationException when the runtime lacks a required safety feature
     */
    private static DocumentBuilderFactory secureDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    /**
     * Converts one XML Preset into the complete standard-profile snapshot used by
     * Project persistence and edits.
     *
     * @param element Preset element
     * @param presetElement stable diagnostic element path
     * @return detached, canonically ordered Slider Preset with its name location
     * @throws InvalidBodySlidePresetException when a slider choice is invalid
     */
    private static ParsedPreset parsePreset(Element element, String presetElement) {
        String name = element.getAttribute("name").replace('.', ' ').trim();

        Map<String, MutableChoice> choices = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        NodeList sliderNodes = element.getElementsByTagName("SetSlider");
        for (int index = 0; index < sliderNodes.getLength(); index++) {
            Node node = sliderNodes.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE)
                continue;
            String choiceElement = presetElement + "/SetSlider[" + (index + 1) + "]";
            parseChoice((Element) node, choiceElement, choices);
        }
        return new ParsedPreset(new SliderPresetSnapshot(name, false, completeChoices(choices)),
                presetElement + "/@name");
    }

    /**
     * Applies one small or big endpoint to a case-insensitive explicit choice.
     * Repeated endpoints remain later-wins for legacy BodySlide compatibility.
     *
     * @param element SetSlider element
     * @param choiceElement stable diagnostic element path
     * @param choices accumulated explicit choices
     * @throws InvalidBodySlidePresetException when the slider name or value is invalid
     */
    private static void parseChoice(Element element, String choiceElement, Map<String, MutableChoice> choices) {
        String name = element.getAttribute("name");
        if (name.trim().isEmpty())
            throw new InvalidBodySlidePresetException(ProjectDiagnosticCodes.SLIDER_PRESET_XML_STRUCTURE_INVALID,
                    choiceElement + "/@name", "A BodySlide slider name must not be empty.");

        int value;
        try {
            value = Integer.parseInt(element.getAttribute("value"));
        } catch (NumberFormatException exception) {
            throw new InvalidBodySlidePresetException(ProjectDiagnosticCodes.SLIDER_PRESET_XML_VALUE_INVALID,
                    choiceElement + "/@value", "A BodySlide slider value must be an integer.");
        }

        MutableChoice choice = choices.get(name);
        if (choice == null) {
            choice = new MutableChoice(name);
            choices.put(name, choice);
        }
        if ("small".equalsIgnoreCase(element.getAttribute("size")))
            choice.small = Integer.valueOf(value);
        else
            choice.big = Integer.valueOf(value);
    }

    /**
     * Builds explicit choices and configured missing defaults with persistence-
     * compatible effective values and canonical ordering.
     *
     * @param explicit parsed explicit endpoints keyed by logical slider identity
     * @return complete immutable choice list
     */
    private static List<SliderChoiceSnapshot> completeChoices(Map<String, MutableChoice> explicit) {
        Map<String, SliderChoiceSnapshot> choices = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (MutableChoice choice : explicit.values()) {
            choices.put(choice.name, new SliderChoiceSnapshot(choice.name, true, choice.small, choice.big,
                    choice.small == null ? Settings.getDefaultValueSmall(choice.name) : choice.small.intValue(),
                    choice.big == null ? Settings.getDefaultValueBig(choice.name) : choice.big.intValue(),
                    100, 100, false));
        }
        for (Map.Entry<String, DefaultSliderValue> entry : Settings.getDefaultsMap().entrySet()) {
            if (!choices.containsKey(entry.getKey())) {
                choices.put(entry.getKey(), new SliderChoiceSnapshot(entry.getKey(), true, null, null,
                        (int) (entry.getValue().getValueSmall() * 100),
                        (int) (entry.getValue().getValueBig() * 100), 100, 100, true));
            }
        }
        return new ArrayList<>(choices.values());
    }

    /** Mutable parse-only holder for the two endpoints of one slider choice. */
    private static final class MutableChoice {
        private final String name;
        private Integer small;
        private Integer big;

        private MutableChoice(String name) {
            this.name = name;
        }
    }

    /** Detached imported value paired with its stable XML name location. */
    static final class ParsedPreset {
        private final SliderPresetSnapshot preset;
        private final String nameElement;

        /** Creates one parsed value for later Project validation and publication. */
        private ParsedPreset(SliderPresetSnapshot preset, String nameElement) {
            this.preset = preset;
            this.nameElement = nameElement;
        }

        /** @return detached Slider Preset value */
        SliderPresetSnapshot getPreset() {
            return preset;
        }

        /** @return stable XML location of the imported name */
        String getNameElement() {
            return nameElement;
        }
    }

    /** Stable schema or value rejection raised only after XML syntax succeeds. */
    static final class InvalidBodySlidePresetException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String code;
        private final String element;

        /** Creates a structured validation failure for one XML element. */
        InvalidBodySlidePresetException(String code, String element, String message) {
            super(message);
            this.code = code;
            this.element = element;
        }

        /** @return stable diagnostic code */
        String getCode() {
            return code;
        }

        /** @return stable XML element path */
        String getElement() {
            return element;
        }
    }
}
