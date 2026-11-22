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

/** Documented v5 reads only. Live writes require verified template and no-price safeguards. */
public final class VworkLiveClient implements VworkClient {
  private static final String BASE = "https://api.vworkapp.com/api/v5/jobs";
  private static final int MAX_BYTES = 10 * 1024 * 1024;
  private static final int PAGE_SIZE = 50;
  @FunctionalInterface public interface Transport { Response send(URI uri, Map<String, String> headers) throws Exception; }
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
    return new VworkLiveClient((uri, headers) -> {
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET();
      headers.forEach(request::header);
      var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      try (var body = response.body()) {
        byte[] bytes = body.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Response exceeds limit");
        var selected = new HashMap<String, String>();
        response.headers().firstValue("Content-Encoding").ifPresent(v -> selected.put("Content-Encoding", v));
        response.headers().firstValue("Retry-After").ifPresent(v -> selected.put("Retry-After", v));
        return new Response(response.statusCode(), selected, bytes);
      }
    }, clock);
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

  @Override public Job create(VworkCredential credential, NewJob draft) {
    throw new VworkFailure("VWORK_CAPABILITY_UNVERIFIED", "Live job creation is not available until the template is verified.", 0, null);
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
