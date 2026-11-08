package com.airconcepts.dashboard.invoices;

import com.airconcepts.dashboard.security.CurrentUser;
import com.airconcepts.dashboard.xero.XeroAttachmentClient;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AttachmentService {
  private final InvoiceService invoices;
  private final XeroAttachmentClient attachments;
  public AttachmentService(InvoiceService invoices, XeroAttachmentClient attachments) { this.invoices = invoices; this.attachments = attachments; }
  public List<AttachmentViews.Attachment> list(CurrentUser user, UUID entity, InvoiceType type, UUID invoice) {
    return invoices.readDocument(user, entity, type, invoice, (access, document) -> attachments.list(access, invoice));
  }
  public AttachmentViews.File file(CurrentUser user, UUID entity, InvoiceType type, UUID invoice, UUID attachment) {
    return invoices.readDocument(user, entity, type, invoice, (access, document) -> attachments.file(access, invoice, attachment));
  }
}
