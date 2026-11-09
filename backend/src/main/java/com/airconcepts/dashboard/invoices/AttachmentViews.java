package com.airconcepts.dashboard.invoices;

import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class AttachmentViews {
  private AttachmentViews() {}
  @Schema(name = "InvoiceAttachment", requiredProperties = {"id", "fileName", "contentType", "size", "previewable"})
  public record Attachment(UUID id, String fileName, String contentType, long size, boolean previewable) {}
  /** Internal bounded bytes; never serialised as a JSON DTO. */
  public record File(String fileName, String contentType, byte[] content, boolean previewable) {
    @Override public String toString() { return "AttachmentFile[redacted]"; }
  }
}
