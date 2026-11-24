/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.api.nbfx.layout;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.netbeans.api.nbfx.resources.ResourceRef;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Reads and writes the standard XML authoring form of a {@link LayoutSpec}. The XML is a declarative
 * form of the same immutable model, not a separate layout language.
 * <p>
 * DTDs, external entities and external XML resources are disabled. A document that is not well-formed
 * causes an {@link IOException}; a document that violates the layout grammar or model causes an
 * {@link IllegalArgumentException} whose message starts with an XPath-like location.
 *
 * @since 1.0
 */
public final class LayoutXml {

    private static final int CURRENT_VERSION = 1;
    private static final int MAX_INCLUDE_DEPTH = 64;
    private static final String ROOT_PATH = "/layout";
    private static final IncludeResolver NO_INCLUDES = (path, includingPath) -> {
        throw new IOException("Layout XML includes require an IncludeResolver at " + includingPath);
    };

    private LayoutXml() {
    }

    /**
     * Opens a layout XML include. The path is the opaque value of the including {@code path}
     * attribute; this API performs no implicit filesystem, classpath or network resolution.
     *
     * @param path the include path
     * @param includingPath the XPath-like location of the include element
     * @return a stream owned and closed by {@link LayoutXml}
     * @throws IOException if the include cannot be opened
     */
    @FunctionalInterface
    public interface IncludeResolver {

        InputStream open(String path, String includingPath) throws IOException;
    }

    /**
     * Reads a layout from XML that contains no includes. The caller owns the stream and is responsible
     * for closing it.
     *
     * @param input the XML input
     * @return the immutable layout
     * @throws IOException if the XML cannot be read or is not well-formed
     * @throws IllegalArgumentException if the document violates the layout grammar or model
     */
    public static LayoutSpec read(InputStream input) throws IOException {
        return read(input, NO_INCLUDES);
    }

    /**
     * Reads a layout from XML, resolving {@code <include path="..."/>} elements in {@code <areas>}
     * and {@code <groups>} through the supplied resolver. The caller owns the main stream; streams
     * returned by the resolver are closed by this method. Include nesting is limited to 64 levels.
     *
     * @param input the XML input
     * @param includeResolver the include resolver
     * @return the immutable layout
     * @throws IOException if the XML or an include cannot be read or is not well-formed
     * @throws IllegalArgumentException if the document violates the layout grammar or model
     */
    public static LayoutSpec read(InputStream input, IncludeResolver includeResolver) throws IOException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(includeResolver, "includeResolver");
        Element root = parse(new NonClosingInputStream(input), ROOT_PATH);
        if (root == null || !"layout".equals(root.getTagName())) {
            throw error(ROOT_PATH, "expected <layout>");
        }
        return readLayout(root, new Context(includeResolver));
    }

    /**
     * Writes a layout as deterministic UTF-8 XML. The caller owns the stream and is responsible for
     * closing it. Only the current XML format version can be written.
     *
     * @param layout the layout to write
     * @param output the XML output
     * @throws IOException if the XML cannot be written
     * @throws IllegalArgumentException if the layout uses an unsupported XML format version
     */
    public static void write(LayoutSpec layout, OutputStream output) throws IOException {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(output, "output");
        if (layout.formatVersion() != CURRENT_VERSION) {
            throw new IllegalArgumentException("unsupported layout XML version: " + layout.formatVersion());
        }
        try {
            Document document = factory().newDocumentBuilder().newDocument();
            Element root = document.createElement("layout");
            root.setAttribute("id", layout.id());
            root.setAttribute("version", Integer.toString(layout.formatVersion()));
            document.appendChild(root);
            writeAreas(document, root, layout.areas());
            writeGroups(document, root, layout.groups());
            Element tree = document.createElement("tree");
            root.appendChild(tree);
            writeNode(document, tree, layout.root());
            writeMetadata(document, root, layout.metadata());
            Transformer transformer = transformerFactory().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.transform(new DOMSource(document), new StreamResult(output));
        } catch (Exception ex) {
            throw new IOException("Cannot write layout XML", ex);
        }
    }

    private static LayoutSpec readLayout(Element root, Context context) throws IOException {
        checkAttributes(root, ROOT_PATH, "id", "version");
        checkChildren(root, ROOT_PATH, "areas", "groups", "tree", "metadata");
        String id = required(root, "id", ROOT_PATH);
        int version = parseInt(required(root, "version", ROOT_PATH), ROOT_PATH + "/@version");
        if (version != CURRENT_VERSION) {
            throw error(ROOT_PATH + "/@version", "unsupported layout version: " + version);
        }
        Map<PaneRole, ViewArea> areas = new LinkedHashMap<>();
        readAreas(singleChild(root, "areas", ROOT_PATH, false), ROOT_PATH + "/areas", areas, context);
        Map<DockGroupId, DockGroupSpec> groups = new LinkedHashMap<>();
        Map<DockGroupId, String> groupPaths = new LinkedHashMap<>();
        readGroups(singleChild(root, "groups", ROOT_PATH, false), ROOT_PATH + "/groups", areas,
                groups, groupPaths, context);
        Element tree = requiredChild(root, "tree", ROOT_PATH);
        checkAttributes(tree, ROOT_PATH + "/tree");
        List<Element> nodes = childElements(tree);
        checkText(tree, ROOT_PATH + "/tree");
        if (nodes.size() != 1) {
            throw error(ROOT_PATH + "/tree", "expected exactly one layout node");
        }
        Element node = nodes.getFirst();
        String nodePath = ROOT_PATH + "/tree/" + node.getTagName() + "[1]";
        if ("slot".equals(node.getTagName())) {
            throw error(nodePath, "layout root must be a split or group");
        }
        LayoutNode layoutNode = readNode(node, nodePath, areas, groups, new TreeContext());
        Map<String, String> metadata = readMetadata(singleChild(root, "metadata", ROOT_PATH, false));
        validateUnreferencedGroups(groups, layoutNode, groupPaths);
        return parse(ROOT_PATH, () -> new LayoutSpec(id, version, areas, groups, layoutNode, metadata));
    }

    private static void readAreas(Element parent, String path, Map<PaneRole, ViewArea> areas,
            Context context) throws IOException {
        if (parent == null) {
            return;
        }
        checkAttributes(parent, path);
        checkChildren(parent, path, "area", "include");
        Map<String, Integer> occurrences = new HashMap<>();
        for (Element element : childElements(parent)) {
            String elementPath = occurrencePath(path, element, occurrences);
            if ("include".equals(element.getTagName())) {
                readInclude(element, elementPath, "areas", context,
                        root -> readAreas(root, elementPath, areas, context));
                continue;
            }
            checkAttributes(element, elementPath, "id", "kind", "displayName", "location", "weight",
                    "presentation", "capabilities", "policyId", "visibility", "emptyPolicy", "order",
                    "style");
            checkChildren(element, elementPath, "style");
            PaneRole id = parse(elementPath, () -> PaneRole.of(required(element, "id", elementPath)));
            if (areas.containsKey(id)) {
                throw error(elementPath, "duplicate area " + id);
            }
            ViewAreaKind kind = parse(elementPath,
                    () -> ViewAreaKind.of(attr(element, "kind", "MAIN")));
            String displayName = required(element, "displayName", elementPath);
            DockLocation location = enumValue(DockLocation.class,
                    attr(element, "location", "CENTER"), elementPath);
            double weight = parseDouble(attr(element, "weight", "1.0"), elementPath + "/@weight");
            PresentationId presentation = parse(elementPath,
                    () -> PresentationId.of(attr(element, "presentation", "tabbed")));
            Set<ViewAreaCapability> capabilities = parseEnumList(ViewAreaCapability.class,
                    attr(element, "capabilities", ""), elementPath + "/@capabilities");
            String policyId = attr(element, "policyId", "default");
            ViewAreaVisibility visibility = enumValue(ViewAreaVisibility.class,
                    attr(element, "visibility", "STARTUP"), elementPath);
            EmptyAreaPolicy emptyPolicy = enumValue(EmptyAreaPolicy.class,
                    attr(element, "emptyPolicy", "KEEP"), elementPath);
            int order = parseInt(attr(element, "order", Integer.toString(areas.size())),
                    elementPath + "/@order");
            AreaStyle style = readStyle(singleChild(element, "style", elementPath, false), elementPath);
            areas.put(id, parse(elementPath, () -> new ViewArea(id, kind, displayName, location, weight,
                    presentation, capabilities, policyId, style, visibility, emptyPolicy, order)));
        }
    }

    private static void readGroups(Element parent, String path, Map<PaneRole, ViewArea> areas,
            Map<DockGroupId, DockGroupSpec> groups, Map<DockGroupId, String> groupPaths,
            Context context) throws IOException {
        if (parent == null) {
            return;
        }
        checkAttributes(parent, path);
        checkChildren(parent, path, "group", "include");
        Map<String, Integer> occurrences = new HashMap<>();
        for (Element element : childElements(parent)) {
            String groupPath = occurrencePath(path, element, occurrences);
            if ("include".equals(element.getTagName())) {
                readInclude(element, groupPath, "groups", context,
                        root -> readGroups(root, groupPath, areas, groups, groupPaths, context));
                continue;
            }
            checkAttributes(element, groupPath, "id", "area", "kind", "presentation", "capacity",
                    "policyId", "lifetime", "style");
            checkChildren(element, groupPath, "style");
            DockGroupId id = parse(groupPath, () -> DockGroupId.of(required(element, "id", groupPath)));
            if (groups.containsKey(id)) {
                throw error(groupPath, "duplicate group " + id);
            }
            PaneRole area = parse(groupPath, () -> PaneRole.of(required(element, "area", groupPath)));
            ViewArea areaSpec = areas.get(area);
            if (areaSpec == null) {
                throw error(groupPath, "unknown area " + area);
            }
            PaneKind kind = enumValue(PaneKind.class, attr(element, "kind", "VIEW"), groupPath);
            PresentationId presentation = element.hasAttribute("presentation")
                    ? parse(groupPath, () -> PresentationId.of(element.getAttribute("presentation")))
                    : areaSpec.defaultPresentation();
            ContentCapacity capacity = enumValue(ContentCapacity.class,
                    attr(element, "capacity", "MANY"), groupPath);
            String policyId = element.hasAttribute("policyId")
                    ? element.getAttribute("policyId") : areaSpec.policyId();
            GroupLifetime lifetime = enumValue(GroupLifetime.class,
                    attr(element, "lifetime", "PERSISTENT"), groupPath);
            AreaStyle style = readStyle(singleChild(element, "style", groupPath, false), groupPath);
            groups.put(id, parse(groupPath, () -> new DockGroupSpec(id, area, kind, presentation,
                    capacity, policyId, lifetime, style)));
            groupPaths.put(id, groupPath);
        }
    }

    private static LayoutNode readNode(Element element, String path,
            Map<PaneRole, ViewArea> areas, Map<DockGroupId, DockGroupSpec> groups,
            TreeContext tree) {
        return switch (element.getTagName()) {
            case "group" -> {
                checkLeaf(element, path);
                checkAttributes(element, path, "ref");
                DockGroupId id = parse(path, () -> DockGroupId.of(required(element, "ref", path)));
                if (!groups.containsKey(id)) {
                    throw error(path, "unknown group " + id);
                }
                if (!tree.groups.add(id)) {
                    throw error(path, "duplicate group reference " + id);
                }
                yield new LayoutNode.Group(id);
            }
            case "slot" -> {
                checkLeaf(element, path);
                checkAttributes(element, path, "id", "area", "side");
                SlotId id = parse(path, () -> SlotId.of(required(element, "id", path)));
                PaneRole area = parse(path, () -> PaneRole.of(required(element, "area", path)));
                if (!areas.containsKey(area)) {
                    throw error(path, "unknown area " + area);
                }
                if (!tree.slots.add(id)) {
                    throw error(path, "duplicate slot id " + id);
                }
                yield new LayoutNode.Slot(id, area,
                        enumValue(DockSide.class, attr(element, "side", "CENTER"), path));
            }
            case "split" -> {
                checkAttributes(element, path, "orientation", "dividers");
                checkChildren(element, path, "group", "slot", "split");
                List<LayoutNode> children = new ArrayList<>();
                Map<String, Integer> occurrences = new HashMap<>();
                for (Element child : childElements(element)) {
                    children.add(readNode(child, occurrencePath(path, child, occurrences),
                            areas, groups, tree));
                }
                List<Double> dividers = readDividers(required(element, "dividers", path),
                        path + "/@dividers");
                yield parse(path, () -> new LayoutNode.Split(enumValue(SplitOrientation.class,
                        attr(element, "orientation", "HORIZONTAL"), path), children, dividers));
            }
            default -> throw error(path, "unexpected layout node <" + element.getTagName() + ">");
        };
    }

    private static Map<String, String> readMetadata(Element parent) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (parent == null) {
            return metadata;
        }
        String path = ROOT_PATH + "/metadata";
        checkAttributes(parent, path);
        checkChildren(parent, path, "entry");
        Map<String, Integer> occurrences = new HashMap<>();
        for (Element element : childElements(parent)) {
            String entryPath = occurrencePath(path, element, occurrences);
            checkLeaf(element, entryPath);
            checkAttributes(element, entryPath, "key", "value");
            String key = required(element, "key", entryPath);
            if (metadata.containsKey(key)) {
                throw error(entryPath, "duplicate metadata key " + key);
            }
            metadata.put(key, required(element, "value", entryPath));
        }
        return metadata;
    }

    private static AreaStyle readStyle(Element parent, String ownerPath) {
        if (parent == null) {
            return AreaStyle.EMPTY;
        }
        String path = ownerPath + "/style";
        checkAttributes(parent, path);
        checkChildren(parent, path, "class", "stylesheet", "themeOverlay");
        Set<String> classes = new LinkedHashSet<>();
        List<ResourceRef> stylesheets = new ArrayList<>();
        List<ResourceRef> themeOverlays = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (Element element : childElements(parent)) {
            String elementPath = occurrencePath(path, element, occurrences);
            if ("class".equals(element.getTagName())) {
                checkLeaf(element, elementPath);
                checkAttributes(element, elementPath, "name");
                classes.add(required(element, "name", elementPath));
            } else {
                checkLeaf(element, elementPath);
                checkAttributes(element, elementPath, "module", "path");
                ResourceRef ref = parse(elementPath, () -> new ResourceRef(
                        required(element, "module", elementPath), required(element, "path", elementPath)));
                if ("stylesheet".equals(element.getTagName())) {
                    stylesheets.add(ref);
                } else {
                    themeOverlays.add(ref);
                }
            }
        }
        return new AreaStyle(classes, stylesheets, themeOverlays);
    }

    private static List<Double> readDividers(String value, String path) {
        List<Double> dividers = new ArrayList<>();
        for (String item : csv(value, path)) {
            dividers.add(parseDouble(item, path));
        }
        return dividers;
    }

    private static void readInclude(Element element, String path, String expectedRoot,
            Context context, IncludeConsumer consumer) throws IOException {
        checkLeaf(element, path);
        checkAttributes(element, path, "path");
        String includePath = required(element, "path", path);
        if (includePath.isBlank()) {
            throw error(path, "include path must not be blank");
        }
        if (context.activeIncludes.size() >= MAX_INCLUDE_DEPTH) {
            throw error(path, "maximum include depth of " + MAX_INCLUDE_DEPTH + " exceeded");
        }
        if (!context.activeIncludes.add(includePath)) {
            throw error(path, "cyclic include: " + includePath);
        }
        try {
            InputStream stream;
            try {
                stream = context.resolver.open(includePath, path);
                if (stream == null) {
                    throw new IOException("Include resolver returned null at " + path);
                }
            } catch (IOException ex) {
                throw new IOException("Cannot open layout include " + includePath + " at " + path, ex);
            }
            try (stream) {
                Element root = parse(stream, path);
                if (root == null || !expectedRoot.equals(root.getTagName())) {
                    throw error(path, "expected <" + expectedRoot + "> in include " + includePath);
                }
                consumer.read(root);
            }
        } finally {
            context.activeIncludes.remove(includePath);
        }
    }

    private static void validateUnreferencedGroups(Map<DockGroupId, DockGroupSpec> groups,
            LayoutNode root, Map<DockGroupId, String> groupPaths) {
        Set<DockGroupId> referenced = new LinkedHashSet<>();
        collectGroups(root, referenced);
        for (DockGroupSpec group : groups.values()) {
            if (!referenced.contains(group.id()) && group.lifetime() != GroupLifetime.TRANSIENT) {
                throw error(groupPaths.get(group.id()), "unreferenced persistent group " + group.id());
            }
        }
    }

    private static void collectGroups(LayoutNode node, Set<DockGroupId> referenced) {
        if (node instanceof LayoutNode.Group group) {
            referenced.add(group.groupId());
        } else if (node instanceof LayoutNode.Split split) {
            split.children().forEach(child -> collectGroups(child, referenced));
        }
    }

    private static void writeAreas(Document document, Element root, Map<PaneRole, ViewArea> areas) {
        Element parent = document.createElement("areas");
        root.appendChild(parent);
        List<ViewArea> ordered = areas.values().stream()
                .sorted(Comparator.comparingInt(ViewArea::order)
                        .thenComparing(area -> area.id().value()))
                .toList();
        for (ViewArea area : ordered) {
            Element element = document.createElement("area");
            element.setAttribute("id", area.id().value());
            element.setAttribute("kind", area.kind().value());
            element.setAttribute("displayName", area.displayName());
            element.setAttribute("location", area.defaultLocation().name());
            element.setAttribute("weight", Double.toString(area.weight()));
            element.setAttribute("presentation", area.defaultPresentation().value());
            element.setAttribute("capabilities", String.join(" ", area.capabilities().stream()
                    .map(Enum::name).sorted().toList()));
            element.setAttribute("policyId", area.policyId());
            element.setAttribute("visibility", area.visibility().name());
            element.setAttribute("emptyPolicy", area.emptyPolicy().name());
            element.setAttribute("order", Integer.toString(area.order()));
            writeStyle(document, element, area.style());
            parent.appendChild(element);
        }
    }

    private static void writeGroups(Document document, Element root,
            Map<DockGroupId, DockGroupSpec> groups) {
        Element parent = document.createElement("groups");
        root.appendChild(parent);
        List<DockGroupSpec> ordered = groups.values().stream()
                .sorted(Comparator.comparing(group -> group.id().value()))
                .toList();
        for (DockGroupSpec group : ordered) {
            Element element = document.createElement("group");
            element.setAttribute("id", group.id().value());
            element.setAttribute("area", group.area().value());
            element.setAttribute("kind", group.kind().name());
            element.setAttribute("presentation", group.presentation().value());
            element.setAttribute("capacity", group.capacity().name());
            element.setAttribute("policyId", group.policyId());
            element.setAttribute("lifetime", group.lifetime().name());
            writeStyle(document, element, group.style());
            parent.appendChild(element);
        }
    }

    private static void writeNode(Document document, Element parent, LayoutNode node) {
        if (node instanceof LayoutNode.Group group) {
            Element element = document.createElement("group");
            element.setAttribute("ref", group.groupId().value());
            parent.appendChild(element);
        } else if (node instanceof LayoutNode.Slot slot) {
            Element element = document.createElement("slot");
            element.setAttribute("id", slot.id().value());
            element.setAttribute("area", slot.area().value());
            element.setAttribute("side", slot.side().name());
            parent.appendChild(element);
        } else if (node instanceof LayoutNode.Split split) {
            Element element = document.createElement("split");
            element.setAttribute("orientation", split.orientation().name());
            element.setAttribute("dividers", split.dividers().stream()
                    .map(Object::toString).collect(Collectors.joining(",")));
            parent.appendChild(element);
            split.children().forEach(child -> writeNode(document, element, child));
        }
    }

    private static void writeMetadata(Document document, Element root, Map<String, String> metadata) {
        if (metadata.isEmpty()) {
            return;
        }
        Element parent = document.createElement("metadata");
        root.appendChild(parent);
        metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Element element = document.createElement("entry");
                    element.setAttribute("key", entry.getKey());
                    element.setAttribute("value", entry.getValue());
                    parent.appendChild(element);
                });
    }

    private static void writeStyle(Document document, Element owner, AreaStyle style) {
        if (AreaStyle.EMPTY.equals(style)) {
            return;
        }
        Element parent = document.createElement("style");
        style.styleClasses().stream().sorted()
                .forEach(name -> {
                    Element element = document.createElement("class");
                    element.setAttribute("name", name);
                    parent.appendChild(element);
                });
        for (ResourceRef ref : style.stylesheets()) {
            writeResource(document, parent, "stylesheet", ref);
        }
        for (ResourceRef ref : style.themeOverlays()) {
            writeResource(document, parent, "themeOverlay", ref);
        }
        owner.appendChild(parent);
    }

    private static void writeResource(Document document, Element parent, String name, ResourceRef ref) {
        Element element = document.createElement(name);
        element.setAttribute("module", ref.module());
        element.setAttribute("path", ref.path());
        parent.appendChild(element);
    }

    private static Element parse(InputStream input, String path) throws IOException {
        try {
            return factory().newDocumentBuilder().parse(input).getDocumentElement();
        } catch (SAXException | ParserConfigurationException ex) {
            throw new IOException("Invalid layout XML at " + path, ex);
        }
    }

    private static DocumentBuilderFactory factory() throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            return factory;
        } catch (Exception ex) {
            throw new IOException("Cannot configure XML parser", ex);
        }
    }

    private static TransformerFactory transformerFactory() throws IOException {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            return factory;
        } catch (Exception ex) {
            throw new IOException("Cannot configure XML transformer", ex);
        }
    }

    private static Element singleChild(Element parent, String name, String path, boolean required) {
        List<Element> matches = new ArrayList<>();
        for (Element element : childElements(parent)) {
            if (name.equals(element.getTagName())) {
                matches.add(element);
            }
        }
        if (matches.size() > 1) {
            throw error(path + "/" + name, "expected at most one <" + name + ">");
        }
        if (matches.isEmpty()) {
            if (required) {
                throw error(path, "missing <" + name + ">");
            }
            return null;
        }
        return matches.getFirst();
    }

    private static Element requiredChild(Element parent, String name, String path) {
        return singleChild(parent, name, path, true);
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private static String occurrencePath(String parentPath, Element element,
            Map<String, Integer> occurrences) {
        String name = element.getTagName();
        int occurrence = occurrences.merge(name, 1, Integer::sum);
        return parentPath + "/" + name + "[" + occurrence + "]";
    }

    private static void checkChildren(Element parent, String path, String... allowed) {
        checkText(parent, path);
        List<String> names = Arrays.asList(allowed);
        for (Element element : childElements(parent)) {
            if (!names.contains(element.getTagName())) {
                throw error(path + "/" + element.getTagName(), "unexpected element");
            }
        }
    }

    private static void checkLeaf(Element element, String path) {
        checkText(element, path);
        List<Element> children = childElements(element);
        if (!children.isEmpty()) {
            throw error(path + "/" + children.getFirst().getTagName(), "nested elements are not allowed");
        }
    }

    private static void checkAttributes(Element element, String path, String... allowed) {
        List<String> names = Arrays.asList(allowed);
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            String name = attributes.item(i).getNodeName();
            if (!names.contains(name)) {
                throw error(path + "/@" + name, "unexpected attribute");
            }
        }
    }

    private static void checkText(Element element, String path) {
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if ((node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE)
                    && !node.getTextContent().isBlank()) {
                throw error(path, "unexpected text content");
            }
        }
    }

    private static String attr(Element element, String name, String fallback) {
        return element.hasAttribute(name) ? element.getAttribute(name) : fallback;
    }

    private static String required(Element element, String name, String path) {
        if (!element.hasAttribute(name)) {
            throw error(path, "missing @" + name);
        }
        return element.getAttribute(name);
    }

    private static List<String> csv(String value, String path) {
        String text = value.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : text.split(",", -1)) {
            String token = item.trim();
            if (token.isEmpty()) {
                throw error(path, "empty list item");
            }
            result.add(token);
        }
        return result;
    }

    private static List<String> tokens(String value, String path) {
        String text = value.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String item : text.split("[,\\s]+", -1)) {
            if (item.isEmpty()) {
                throw error(path, "empty list item");
            }
            result.add(item);
        }
        return result;
    }

    private static int parseInt(String value, String path) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw error(path, "expected an integer: " + value);
        }
    }

    private static double parseDouble(String value, String path) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            throw error(path, "expected a number: " + value);
        }
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value, String path) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw error(path, "unknown " + type.getSimpleName() + ": " + value);
        }
    }

    private static <T extends Enum<T>> Set<T> parseEnumList(Class<T> type, String value, String path) {
        Set<T> result = new LinkedHashSet<>();
        for (String item : tokens(value, path)) {
            result.add(enumValue(type, item, path));
        }
        return result;
    }

    private static <T> T parse(String path, Supplier<T> parser) {
        try {
            return parser.get();
        } catch (IllegalArgumentException ex) {
            throw error(path, ex.getMessage());
        }
    }

    private static IllegalArgumentException error(String path, String message) {
        return new IllegalArgumentException(path + ": " + message);
    }

    @FunctionalInterface
    private interface IncludeConsumer {

        void read(Element root) throws IOException;
    }

    private static final class NonClosingInputStream extends FilterInputStream {

        private NonClosingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public void close() {
            // The caller owns the main layout stream.
        }
    }

    private static final class Context {

        private final IncludeResolver resolver;
        private final Set<String> activeIncludes = new HashSet<>();

        private Context(IncludeResolver resolver) {
            this.resolver = resolver;
        }
    }

    private static final class TreeContext {

        private final Set<DockGroupId> groups = new HashSet<>();
        private final Set<SlotId> slots = new HashSet<>();
    }
}
