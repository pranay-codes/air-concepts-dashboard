package com.airconcepts.dashboard.workitems.operations;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.vwork.VworkClient;
import com.airconcepts.dashboard.vwork.VworkTemplatesRepository;
import com.airconcepts.dashboard.web.ApiFailure;
import com.airconcepts.dashboard.xero.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Builds a price-free draft from authorised Xero reads. */
@Service
public class VworkJobDraftService {
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  @io.swagger.v3.oas.annotations.media.Schema(name="CreateJobDraft", requiredProperties={"entityId","itemId","version","quoteNumber","scope","templates","missingFields"})
  public record Draft(UUID entityId, UUID itemId, int version, String reference, String quoteNumber, String customer,
      String contactPerson, String phone, String siteAddress, List<VworkClient.ScopeLine> scope,
      List<VworkTemplatesRepository.Template> templates, String defaultTemplate, Set<String> missingFields) {
    public Draft { scope = List.copyOf(scope); templates = List.copyOf(templates); missingFields = Set.copyOf(missingFields); }
    @Override public String toString() { return "VworkJobDraft[redacted]"; }
  }
  private final VworkJobDraftContext contexts;
  private final XeroConnectionService connections;
  private final XeroQuoteClient quotes;
  private final XeroContactClient contacts;
  public VworkJobDraftService(VworkJobDraftContext contexts, XeroConnectionService connections,
      XeroQuoteClient quotes, XeroContactClient contacts) {
    this.contexts=contexts; this.connections=connections; this.quotes=quotes; this.contacts=contacts;
  }
  public Draft draft(CurrentUser user, UUID entity, UUID item) {
    var context = contexts.load(user, entity, item);
    var access = connections.authorisedAccess(user, entity);
    XeroScopes.require(access.grantedScopes(), XeroScopes.QUOTES_READ);
    var quote = quotes.detail(access, context.quoteId());
    if (quote.status() != XeroQuoteClient.Status.ACCEPTED) throw changed();
    XeroContactClient.Contact contact = null;
    if (quote.contactId() != null) {
      var current = sameAccess(access, connections.authorisedAccess(user, entity));
      XeroScopes.require(current.grantedScopes(), XeroScopes.CONTACTS_READ);
      contact = contacts.detail(current, quote.contactId());
    }
    var finalAccess = sameAccess(access, connections.authorisedAccess(user, entity));
    if (quote.contactId() != null) XeroScopes.require(finalAccess.grantedScopes(), XeroScopes.CONTACTS_READ);
    if (!context.equals(contexts.load(user, entity, item))) throw changed();
    var scope = quote.lines() == null ? List.<VworkClient.ScopeLine>of() : quote.lines().stream()
        .filter(Objects::nonNull).map(line -> new VworkClient.ScopeLine(line.description(),
            line.quantity() == null ? null : line.quantity().stripTrailingZeros().toPlainString())).toList();
    var customer = contact == null ? quote.contactName() : contact.name();
    String person = contact == null ? null : person(contact);
    String phone = contact == null ? null : phone(contact);
    String reference = blank(context.reference()) ? context.quoteNumber() : context.reference();
    String defaultTemplate = context.templates().stream().filter(VworkTemplatesRepository.Template::isDefault)
        .map(VworkTemplatesRepository.Template::templateRef).findFirst().orElse(null);
    Set<String> missing = new LinkedHashSet<>();
    if (blank(reference)) missing.add("reference");
    if (blank(customer)) missing.add("customer");
    if (blank(person)) missing.add("contactPerson");
    if (blank(phone)) missing.add("phone");
    missing.add("siteAddress");
    if (scope.isEmpty() || scope.stream().noneMatch(line -> !blank(line.description()) && !blank(line.quantity()))) missing.add("scope");
    if (blank(defaultTemplate)) missing.add("templateRef");
    return new Draft(entity,item,context.version(),reference,context.quoteNumber(),customer,person,phone,null,
        scope,context.templates(),defaultTemplate,missing);
  }
  private static TenantAccess sameAccess(TenantAccess expected, TenantAccess actual) {
    if (actual == null || !expected.entityId().equals(actual.entityId()) || !expected.tenantId().equals(actual.tenantId())
        || !expected.grantId().equals(actual.grantId())) throw changed();
    XeroScopes.require(actual.grantedScopes(), XeroScopes.QUOTES_READ);
    return actual;
  }
  private static String person(XeroContactClient.Contact contact) {
    if (contact.persons() != null) for (var p : contact.persons()) {
      if (p != null) { var name=join(p.firstName(),p.lastName()); if (!blank(name)) return name; }
    }
    return join(contact.firstName(),contact.lastName());
  }
  private static String phone(XeroContactClient.Contact contact) {
    if (contact.phones() == null) return null;
    for (String type : List.of("DEFAULT", "MOBILE")) for (var p : contact.phones())
      if (p != null && type.equalsIgnoreCase(p.type()) && !blank(p.number())) return join(p.countryCode(),p.areaCode(),p.number());
    return null;
  }
  private static String join(String... values) {
    String result=Arrays.stream(values).filter(value -> !blank(value)).map(String::strip).reduce((a,b) -> a+" "+b).orElse("");
    return result.isEmpty() ? null : result;
  }
  private static boolean blank(String value) { return value == null || value.isBlank(); }
  private static ApiFailure changed() { return new ApiFailure(HttpStatus.CONFLICT, "WORK_ITEM_CHANGED", "The job or Xero connection changed. Refresh and try again."); }
}
