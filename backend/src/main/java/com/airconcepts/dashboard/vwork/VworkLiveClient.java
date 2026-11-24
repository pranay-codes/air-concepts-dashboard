package com.airconcepts.dashboard.vwork;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Documented v5 jobs API. Creation uses the Templated Jobs clone endpoint and carries no prices: the payload has no
 * invoice, pricebook or currency fields, and a response that contains pricing is rejected for review.
 */
public final class VworkLiveClient implements VworkClient {
  /** Documented host for the v5 API (see the v5 API overview and the Templated Jobs OpenAPI servers entry). */
  private static final String BASE = "https://go.vworkapp.com/api/v5/jobs";
  private static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final int PAGE_SIZE = 50;
  private static final int MAX_NOTES = 8000;
  /** Elements that would carry prices. A created job must not contain any of them with content. */
  private static final Set<String> PRICING_ELEMENTS = Set.of("invoice", "line_items", "line_item", "pricebook",
      "pricebook_id", "unit_cost", "tax_rate", "requested_quantity", "actual_quantity");
  @FunctionalInterface public interface Transport {
    Response send(URI uri, Map<String, String> headers) throws Exception;
    /** Writes need a method and body. Read-only transports keep the default and reject writes. */
    default Response send(String method, URI uri, byte[] body, Map<String, String> headers) throws Exception {
      throw new UnsupportedOperationException("This transport is read-only");
    }
  }
  public record Response(int status, Map<String, String> headers, byte[] body) {
    public Response { headers = Map.copyOf(headers); body = body.clone(); }
    @Override public String toString() { return "VworkResponse[redacted]"; }
  }
  private final Transport transport;
  private final Clock clock;
  public VworkLiveClient(Transport transport, Clock clock) {
    this.transport = Objects.requireNonNull(transport); this.clock = Objects.requireNonNull(clock);
  }

  /** A dedicated HTTP client has no redirects, request logging or shared Xero interceptors. */
  public static VworkLiveClient production(Clock clock) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    Transport transport = new Transport() {
      @Override public Response send(URI uri, Map<String, String> headers) throws Exception {
        return exchange(http, "GET", uri, null, headers);
      }
      @Override public Response send(String method, URI uri, byte[] body, Map<String, String> headers) throws Exception {
        return exchange(http, method, uri, body, headers);
      }
    };
    return new VworkLiveClient(transport, clock);
  }

  private static Response exchange(HttpClient http, String method, URI uri, byte[] body, Map<String, String> headers)
      throws Exception {
    var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20));
    if (body == null) request.GET();
    else request.method(method, HttpRequest.BodyPublishers.ofByteArray(body));
    headers.forEach(request::header);
    var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    try (var stream = response.body()) {
      byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
      if (bytes.length > MAX_BYTES) throw new IOException("Response exceeds limit");
      var selected = new HashMap<String, String>();
      response.headers().firstValue("Content-Encoding").ifPresent(v -> selected.put("Content-Encoding", v));
      response.headers().firstValue("Retry-After").ifPresent(v -> selected.put("Retry-After", v));
      return new Response(response.statusCode(), selected, bytes);
    }
  }

  @Override public Job job(VworkCredential credential, String id) {
    if (id == null || !id.matches("[0-9]{1,32}")) throw new IllegalArgumentException("Invalid vWork job ID");
    var result = parseJob(request(credential, "/" + id + ".xml", ""));
    if (!id.equals(result.id())) throw malformed();
    return result;
  }

  @Override public List<Job> findByReference(VworkCredential credential, String reference) {
    if (reference == null || reference.isBlank() || reference.length() > 256) throw new IllegalArgumentException("Invalid reference");
    try {
      var result = parseJob(request(credential, "/" + encode(reference) + ".xml", "&use_third_party_id=true"));
      if (!reference.equals(result.reference())) throw malformed();
      return List.of(result);
    } catch (VworkFailure failure) {
      if ("VWORK_JOB_NOT_FOUND".equals(failure.code())) return List.of();
      throw failure;
    }
  }

  @Override public Page jobs(VworkCredential credential, int page) {
    if (page < 1) throw new IllegalArgumentException("Page must be positive");
    Element root = request(credential, ".xml", "&page=" + page + "&per_page=" + PAGE_SIZE);
    if (!"jobs".equals(root.getTagName())) throw malformed();
    int current = attribute(root, "current_page"), perPage = attribute(root, "per_page");
    int totalPages = attribute(root, "total_pages"), totalEntries = attribute(root, "total_entries");
    if (current != page || perPage != PAGE_SIZE || totalPages < 0 || totalEntries < 0) throw malformed();
    var jobs = new ArrayList<Job>();
    var seen = new HashSet<String>();
    var children = root.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element element) {
        if (!"job".equals(element.getTagName())) throw malformed();
        Job job = parseJob(element);
        if (!seen.add(job.id())) throw malformed();
        jobs.add(job);
      }
    }
    if (jobs.size() > perPage) throw malformed();
    return new Page(jobs, current, perPage, totalPages, totalEntries);
  }

  /** Creates a job from the entity's template with the reference as third-party ID. Unassigned, price-free. */
  @Override public Job create(VworkCredential credential, NewJob draft) {
    Objects.requireNonNull(credential, "credential");
    Objects.requireNonNull(draft, "draft");
    byte[] body = clonePayload(draft);
    URI uri = URI.create(BASE + "/clone.xml?api_key=" + encode(credential.value()));
    Response response;
    try {
      response = transport.send("POST", uri, body, Map.of("Accept-Encoding", "gzip", "Accept", "application/xml",
          "Content-Type", "application/xml; charset=utf-8"));
    }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw unavailable(); }
    catch (Exception failure) { throw unavailable(); }
    if (response == null) throw unavailable();
    if (response.status() < 200 || response.status() >= 300) throw httpFailure(response);
    Element root = document(response);
    rejectPricing(root);
    Job result = parseJob(root);
    if (!draft.reference().equals(result.reference())) throw malformed();
    return result;
  }

  /**
   * The clone payload. Only the template reference, third-party ID, Xero contact ID and descriptive notes are sent.
   * There is no invoice block, pricebook, worker or planned start, so nothing priced reaches vWork from the dashboard.
   */
  static byte[] clonePayload(NewJob draft) {
    String reference = required(draft.reference(), 255, "reference");
    String template = required(draft.templateId(), 120, "template");
    String customer = required(draft.customerThirdPartyId(), 64, "customer");
    if (!customer.matches("[A-Za-z0-9-]{1,64}")) throw new IllegalArgumentException("Invalid customer ID");
    var xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<job>");
    if (template.matches("[0-9]{1,32}")) element(xml, "template_id", template);
    else element(xml, "template_name", template);
    element(xml, "third_party_id", reference);
    element(xml, "customer_third_party_id", customer);
    String notes = notesText(draft);
    if (!notes.isEmpty()) {
      xml.append("<notes type=\"array\"><note>");
      element(xml, "body", notes);
      xml.append("</note></notes>");
    }
    xml.append("</job>");
    return xml.toString().getBytes(StandardCharsets.UTF_8);
  }

  static String notesText(NewJob draft) {
    var text = new StringBuilder();
    line(text, "Description", draft.description());
    line(text, "Contact", draft.contactPerson());
    line(text, "Phone", draft.phone());
    line(text, "Site", draft.siteAddress());
    if (!draft.scope().isEmpty()) {
      text.append("Scope:\n");
      for (var item : draft.scope()) {
        text.append("- ").append(item.description());
        if (item.quantity() != null && !item.quantity().isBlank()) text.append(" (qty ").append(item.quantity()).append(')');
        text.append('\n');
      }
    }
    line(text, "Notes", draft.notes());
    if (text.length() > MAX_NOTES) throw new IllegalArgumentException("Job notes are too long");
    return text.toString().strip();
  }

  private static void line(StringBuilder text, String label, String value) {
    if (value != null && !value.isBlank()) text.append(label).append(": ").append(value.strip()).append('\n');
  }

  private static String required(String value, int max, String field) {
    if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException("Invalid " + field);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') throw new IllegalArgumentException("Invalid " + field);
    }
    return value.strip();
  }

  private static void element(StringBuilder xml, String name, String value) {
    xml.append('<').append(name).append('>').append(escape(value)).append("</").append(name).append('>');
  }

  static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
  }

  /** A created job must not come back with pricing. If it does, the outcome is unknown and goes to review. */
  private static void rejectPricing(Element root) {
    var all = root.getElementsByTagName("*");
    for (int i = 0; i < all.getLength(); i++) {
      if (all.item(i) instanceof Element element && PRICING_ELEMENTS.contains(element.getTagName())
          && !element.getTextContent().isBlank())
        throw new VworkFailure("VWORK_PRICE_RETURNED", "vWork returned pricing for a job created without prices. Review it in vWork before retrying.", 0, null);
    }
  }

  private Element request(VworkCredential credential, String path, String query) {
    Objects.requireNonNull(credential, "credential");
    URI uri = URI.create(BASE + path + "?api_key=" + encode(credential.value()) + query);
    Response response;
    try { response = transport.send(uri, Map.of("Accept-Encoding", "gzip", "Accept", "application/xml")); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw unavailable(); }
    catch (Exception failure) { throw unavailable(); }
    if (response == null) throw unavailable();
    if (response.status() < 200 || response.status() >= 300) throw httpFailure(response);
    return document(response);
  }

  private static Element document(Response response) {
    try {
      byte[] bytes = response.body();
      if (bytes == null || bytes.length > MAX_BYTES) throw new IOException("Response exceeds limit");
      String encoding = header(response.headers(), "Content-Encoding");
      if (encoding != null && !encoding.isBlank()) {
        if (!encoding.equalsIgnoreCase("gzip")) throw new IOException("Unsupported encoding");
        try (var input = new GZIPInputStream(new ByteArrayInputStream(bytes))) { bytes = input.readNBytes(MAX_BYTES + 1); }
        if (bytes.length > MAX_BYTES) throw new IOException("Response exceeds limit");
      }
      var factory = DocumentBuilderFactory.newInstance();
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      var parser = factory.newDocumentBuilder();
      parser.setErrorHandler(new DefaultHandler() {
        @Override public void error(SAXParseException error) throws SAXException { throw error; }
        @Override public void fatalError(SAXParseException error) throws SAXException { throw error; }
      });
      return parser.parse(new ByteArrayInputStream(bytes)).getDocumentElement();
    } catch (Exception failure) { throw malformed(); }
  }

  private static Job parseJob(Element root) {
    if (root == null || !"job".equals(root.getTagName())) throw malformed();
    String id = child(root, "id"), rawStatus = child(root, "status");
    if (id == null || !id.matches("[0-9]{1,32}") || rawStatus == null) throw malformed();
    Status status;
    try { status = Status.valueOf(rawStatus.toUpperCase(Locale.ROOT)); }
    catch (IllegalArgumentException invalid) { throw malformed(); }
    String start = child(root, "planned_start_at");
    Instant planned = null;
    if (start != null) {
      try {
        planned = start.endsWith("Z") || start.matches(".*[+-]\\d{2}(?::?\\d{2})?$")
            ? OffsetDateTime.parse(start, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
            : LocalDateTime.parse(start, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
      } catch (RuntimeException invalid) { throw malformed(); }
    }
    // The documented free-text template_name is not proof of a stable template ID.
    return new Job(id, child(root, "third_party_id"), null, child(root, "template_name"), status,
        child(root, "worker_name"), planned, null);
  }

  private static String child(Element parent, String name) {
    String value = null; boolean found = false;
    var children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element element && name.equals(element.getTagName())) {
        if (found) throw malformed();
        found = true;
        value = element.getTextContent().strip();
      }
    }
    return value == null || value.isEmpty() ? null : value;
  }
  private static int attribute(Element root, String name) {
    try { return Integer.parseInt(root.getAttribute(name)); }
    catch (NumberFormatException invalid) { throw malformed(); }
  }
  private VworkFailure httpFailure(Response response) {
    int status = response.status();
    String code = status == 401 || status == 403 ? "VWORK_KEY_REJECTED" : status == 404 ? "VWORK_JOB_NOT_FOUND"
        : status == 429 ? "VWORK_RATE_LIMITED" : "VWORK_PROVIDER_FAILED";
    return new VworkFailure(code, "vWork could not complete this request. Check the connection and retry.", status,
        retryAfter(header(response.headers(), "Retry-After")));
  }
  private Integer retryAfter(String value) {
    if (value == null) return null;
    try { return Math.max(0, Integer.parseInt(value.strip())); }
    catch (NumberFormatException invalid) {
      try {
        Duration duration = Duration.between(clock.instant(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
        return Math.toIntExact(Math.max(0, duration.getSeconds() + (duration.getNano() > 0 ? 1 : 0)));
      } catch (RuntimeException ignored) { return null; }
    }
  }
  private static String header(Map<String, String> headers, String name) {
    return headers.entrySet().stream().filter(entry -> name.equalsIgnoreCase(entry.getKey())).map(Map.Entry::getValue).findFirst().orElse(null);
  }
  private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
  private static VworkFailure unavailable() { return new VworkFailure("VWORK_UNAVAILABLE", "vWork is unavailable. Retry later.", 0, null); }
  private static VworkFailure malformed() { return new VworkFailure("VWORK_PROVIDER_FAILED", "vWork returned an invalid response.", 0, null); }
}
