package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/entities/{entityId}/invoices", produces = MediaType.APPLICATION_JSON_VALUE)
public class InvoiceController {
  public record AssignInvoice(@io.swagger.v3.oas.annotations.media.Schema(types = {"string", "null"}, format = "uuid",
      description = "Active staff ID; omit or use null to unassign") UUID assigneeId) {}
  private final InvoiceService invoices;
  private final CurrentUserResolver users;
  public InvoiceController(InvoiceService invoices, CurrentUserResolver users) { this.invoices = invoices; this.users = users; }

  @GetMapping
  @Operation(operationId = "listInvoices", summary = "Read a live filtered Xero invoice page; supplier bills require Director or Financial Admin")
  public ResponseEntity<InvoiceViews.Page> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @RequestParam(defaultValue = "ACCREC") InvoiceType type, @RequestParam(defaultValue = "") String search,
      @RequestParam(required = false) InvoiceStatus status, @RequestParam(defaultValue = "false") boolean overdue,
      @RequestParam(defaultValue = "INVOICE_DATE") InvoiceQuery.DateField dateField,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "DATE") InvoiceQuery.Sort sort, @RequestParam(defaultValue = "DESC") InvoiceQuery.Direction direction,
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "25") int pageSize) {
    InvoiceQuery query;
    try { query = new InvoiceQuery(type, search, status, overdue, dateField, from, to, sort, direction, page, pageSize); }
    catch (IllegalArgumentException invalid) {
      throw new ApiFailure(HttpStatus.BAD_REQUEST, "INVALID_INVOICE_QUERY", "Check the search, date range and page limits (1–100 items).");
    }
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invoices.list(users.from(jwt), entityId, query));
  }

  @GetMapping("/{invoiceId}")
  @Operation(operationId = "getInvoice", summary = "Read a live Xero invoice or supplier bill, including lines and payments")
  public ResponseEntity<InvoiceViews.Detail> detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @PathVariable UUID invoiceId, @RequestParam(defaultValue = "ACCREC") InvoiceType type) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(invoices.detail(users.from(jwt), entityId, type, invoiceId));
  }

  @PutMapping("/{invoiceId}/assignment")
  @Operation(operationId = "assignSalesInvoice", summary = "Assign or unassign a verified sales invoice; Director or Financial Admin only")
  public ResponseEntity<InvoiceViews.Assignment> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @PathVariable UUID invoiceId, @RequestParam(defaultValue = "ACCREC") InvoiceType type, @RequestBody AssignInvoice request) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(invoices.assign(users.from(jwt), entityId, type, invoiceId, request.assigneeId()));
  }
}
