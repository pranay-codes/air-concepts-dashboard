package com.airconcepts.dashboard.web;

import com.airconcepts.dashboard.invoices.*;
import com.airconcepts.dashboard.security.CurrentUserResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequestMapping(value = "/api/v1/entities/{entityId}/invoices/{invoiceId}/attachments", produces = MediaType.APPLICATION_JSON_VALUE)
public class AttachmentController {
  private final AttachmentService attachments;
  private final CurrentUserResolver users;
  public AttachmentController(AttachmentService attachments, CurrentUserResolver users) { this.attachments = attachments; this.users = users; }

  @GetMapping
  @Operation(operationId = "listInvoiceAttachments", summary = "Read attachment metadata after verifying the entity and document type")
  public ResponseEntity<List<AttachmentViews.Attachment>> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId,
      @PathVariable UUID invoiceId, @RequestParam(defaultValue = "ACCREC") InvoiceType type) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(attachments.list(users.from(jwt), entityId, type, invoiceId));
  }

  @GetMapping(value = "/{attachmentId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
  @Operation(operationId = "downloadInvoiceAttachment", summary = "Download a verified bounded file; safe PDF/raster content may be requested inline")
  public ResponseEntity<byte[]> file(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID entityId, @PathVariable UUID invoiceId,
      @PathVariable UUID attachmentId, @RequestParam(defaultValue = "ACCREC") InvoiceType type,
      @RequestParam(defaultValue = "false") boolean preview) {
    var file = attachments.file(users.from(jwt), entityId, type, invoiceId, attachmentId);
    var disposition = preview && file.previewable() ? ContentDisposition.inline() : ContentDisposition.attachment();
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.parseMediaType(file.contentType()))
        .contentLength(file.content().length)
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
        .header("X-Content-Type-Options", "nosniff").header("Referrer-Policy", "no-referrer")
        .header("Content-Security-Policy", "sandbox; default-src 'none'").body(file.content());
  }
}
