package ch.so.agi.integrator;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

public final class Xml {
  public static final String ILI = "http://www.interlis.ch/xtf/2.4/INTERLIS",
      SHEET = "http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Datasheet_20260523",
      BASE = "http://www.interlis.ch/xtf/2.4/SO_AGI_DataCatalog_Base_20260529";

  public static Document parse(String xml) {
    try {
      var f = DocumentBuilderFactory.newInstance();
      f.setNamespaceAware(true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setFeature("http://xml.org/sax/features/external-general-entities", false);
      f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      f.setXIncludeAware(false);
      f.setExpandEntityReferences(false);
      var b = f.newDocumentBuilder();
      b.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
      return b.parse(
          new org.xml.sax.InputSource(
              new StringReader(xml.startsWith("\uFEFF") ? xml.substring(1) : xml)));
    } catch (Exception e) {
      throw new Problem("invalid_xml", "XML kann nicht sicher gelesen werden.");
    }
  }

  public static Document parse(Path p) {
    return parse(Json.contents(p));
  }

  public static List<Element> children(Node n) {
    var l = new ArrayList<Element>();
    for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling())
      if (c instanceof Element e) l.add(e);
    return l;
  }

  public static List<Element> all(Node n) {
    var l = new ArrayList<Element>();
    if (n instanceof Element e) l.add(e);
    for (Element c : children(n)) l.addAll(all(c));
    return l;
  }

  public static String local(Element e) {
    return e.getLocalName() != null ? e.getLocalName() : e.getTagName();
  }

  public static String text(Element e, String name) {
    return children(e).stream()
        .filter(c -> local(c).equals(name))
        .map(Element::getTextContent)
        .findFirst()
        .orElse(null);
  }

  public static Map<String, Object> structure(Element e) {
    var result = Json.map();
    for (Element c : children(e)) {
      String k = local(c);
      Object value = children(c).isEmpty() ? c.getTextContent() : structure(c);
      if (result.containsKey(k)) {
        Object old = result.get(k);
        if (old instanceof List) Json.list(old).add(value);
        else result.put(k, new ArrayList<>(Arrays.asList(old, value)));
      } else result.put(k, value);
    }
    return result;
  }

  public static List<Element> datasets(Document d) {
    return all(d).stream()
        .filter(
            e ->
                SHEET.equals(e.getNamespaceURI())
                    && Set.of("Dataset", "DatasetSeries").contains(local(e)))
        .toList();
  }

  public static List<Object> attributes(Element node) {
    var result = new ArrayList<Object>();
    for (Element w : children(node))
      if (local(w).equals("attributes"))
        for (Element a : children(w))
          result.add(
              Json.map(
                  "name",
                  text(a, "name"),
                  "data_type",
                  text(a, "dataType"),
                  "mandatory",
                  "true".equals(text(a, "mandatory")),
                  "description",
                  text(a, "description"),
                  "unit",
                  text(a, "unit"),
                  "code_list",
                  text(a, "codeList")));
    return result;
  }

  public static Map<String, Object> describe(Path p, String id, String issue) {
    var nodes =
        datasets(parse(p)).stream()
            .filter(n -> id == null || id.equals(text(n, "identifier")))
            .toList();
    if (nodes.size() != 1)
      throw new Problem(
          "ambiguous_datasheet",
          "Genau ein passendes Datenblatt erforderlich.",
          "matches",
          nodes.size());
    Element n = nodes.getFirst();
    var issues =
        all(n).stream()
            .filter(e -> SHEET.equals(e.getNamespaceURI()) && local(e).equals("DatasetIssue"))
            .toList();
    var selected =
        issues.stream().filter(i -> Objects.equals(issue, text(i, "issueLabel"))).toList();
    if (issue != null && selected.size() != 1)
      throw new Problem(
          "unknown_issue",
          "Ausgabe muss im Datenblatt eindeutig vorhanden sein.",
          "issue_label",
          issue);
    var attrs = selected.isEmpty() ? attributes(n) : attributes(selected.getFirst());
    if (attrs.isEmpty()) attrs = attributes(n);
    var values = new ArrayList<Object>();
    for (var i : issues)
      values.add(
          Json.map(
              "identifier",
              text(i, "identifier"),
              "label",
              text(i, "issueLabel"),
              "current",
              "true".equals(text(i, "isCurrentIssue")),
              "status",
              text(i, "publicationStatus")));
    return Json.map(
        "identifier",
        text(n, "identifier"),
        "kind",
        local(n).equals("DatasetSeries") ? "series" : "dataset",
        "title",
        text(n, "title"),
        "creator_ref",
        text(n, "creatorRef"),
        "publication_status",
        text(selected.isEmpty() ? n : selected.getFirst(), "publicationStatus"),
        "attributes",
        attrs,
        "fields",
        structure(n),
        "issues",
        values,
        "selected_issue",
        selected.isEmpty() ? null : text(selected.getFirst(), "identifier"));
  }

  public static Object business(Object o) {
    if (o instanceof List l) return l.stream().map(Xml::business).toList();
    if (!(o instanceof Map)) return o;
    var result = Json.map();
    Json.obj(o)
        .forEach(
            (k, v) -> {
              if (Set.of("issued", "modified").contains(k)) return;
              if (Set.of("attributes", "issues").contains(k)) {
                var flat = new ArrayList<>();
                for (Object entry : v instanceof List ? Json.list(v) : Collections.singletonList(v))
                  Json.obj(entry)
                      .forEach(
                          (tag, contents) -> {
                            for (Object item :
                                contents instanceof List
                                    ? Json.list(contents)
                                    : Collections.singletonList(contents))
                              flat.add(Json.map(tag, business(item)));
                          });
                result.put(k, flat);
              } else if (Set.of("keywords", "themes").contains(k))
                result.put(k, v instanceof List ? v : Collections.singletonList(v));
              else result.put(k, business(v));
            });
    return result;
  }

  public static String serialize(Document d) {
    try {
      var f = TransformerFactory.newInstance();
      f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
      f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
      var t = f.newTransformer();
      t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
      var s = new StringWriter();
      t.transform(new DOMSource(d), new StreamResult(s));
      return s.toString();
    } catch (Exception e) {
      throw new Problem("xml_export_failed", "XML kann nicht geschrieben werden.");
    }
  }

  public static String select(String collection, String id) {
    var d = parse(collection);
    var found = datasets(d).stream().filter(e -> id.equals(text(e, "identifier"))).toList();
    if (found.isEmpty()) return null;
    if (found.size() > 1)
      throw new Problem("duplicate_identifier", "Mehrere Datenblätter mit gleichem Identifier.");
    for (Element e : datasets(d))
      if (!id.equals(text(e, "identifier"))) e.getParentNode().removeChild(e);
    return serialize(d);
  }

  public static List<Object> checkOffices(Path p, String creator) {
    var errors = new ArrayList<Object>();
    var ids =
        all(parse(p)).stream()
            .filter(e -> local(e).equals("Office.Office"))
            .map(e -> text(e, "identifier"))
            .toList();
    if (new HashSet<>(ids).size() != ids.size())
      errors.add(
          Json.map("code", "duplicate_office", "message", "Doppelte Dienststellen-Identifier."));
    if (Collections.frequency(ids, creator) != 1)
      errors.add(
          Json.map(
              "code",
              "unknown_office",
              "message",
              "Datenherr fehlt oder ist nicht eindeutig.",
              "creator_ref",
              creator));
    return errors;
  }
}
