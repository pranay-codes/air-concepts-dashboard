package com.airconcepts.dashboard.vwork;

import java.time.Instant;
import java.util.List;

/** Server-only boundary; templateId is null unless independently established by the adapter. */
public interface VworkClient {
  enum Status { UNASSIGNED, UNPUBLISHED, DRAFT, PENDING, DECLINED, ASSIGNED, POSTPONED, STARTED, PAUSED, CANCELED, COMPLETED }
  record Job(String id, String reference, String templateId, String templateName, Status status,
      String workerName, Instant plannedStart, Instant completedAt) {
    @Override public String toString() { return "VworkJob[redacted]"; }
  }
  record Page(List<Job> jobs, int page, int perPage, int totalPages, int totalEntries) {
    public Page { jobs = List.copyOf(jobs); }
  }
  /** No financial fields are accepted by the job-creation boundary. */
  record ScopeLine(String description, String quantity) {}
  /** customerThirdPartyId carries the Xero contact ID; it is derived server-side, never taken from the browser. */
  record NewJob(String reference, String templateId, String description, List<ScopeLine> scope,
      String customer, String contactPerson, String phone, String siteAddress, String notes, String customerThirdPartyId) {
    public NewJob(String reference, String templateId, String description, List<ScopeLine> scope,
        String customer, String contactPerson, String phone, String siteAddress, String notes) {
      this(reference, templateId, description, scope, customer, contactPerson, phone, siteAddress, notes, null);
    }
    public NewJob(String reference, String templateId, String description, List<ScopeLine> scope) {
      this(reference, templateId, description, scope, null, null, null, null, null, null);
    }
    public NewJob { scope = List.copyOf(scope); }
    public NewJob withCustomerThirdPartyId(String id) {
      return new NewJob(reference, templateId, description, scope, customer, contactPerson, phone, siteAddress, notes, id);
    }
    @Override public String toString() { return "VworkNewJob[redacted]"; }
  }
  Job job(VworkCredential credential, String id);
  Page jobs(VworkCredential credential, int page);
  List<Job> findByReference(VworkCredential credential, String reference);
  Job create(VworkCredential credential, NewJob draft);
}
