package com.airconcepts.dashboard.vwork;

import java.time.Instant;
import java.util.*;

/** Deterministic in-memory test provider; its template IDs are synthetic, not provider evidence. */
public final class MockVworkClient implements VworkClient {
  private static final int PAGE_SIZE = 50;
  private final Map<String, Job> jobs = new LinkedHashMap<>();
  private int nextId = 1;

  @Override public synchronized Job create(VworkCredential credential, NewJob draft) {
    Objects.requireNonNull(credential, "credential");
    if (draft == null || blank(draft.reference()) || draft.reference().length() > 256 || blank(draft.templateId())
        || blank(draft.description()) || draft.scope().isEmpty()) throw failure("VWORK_INVALID_REQUEST", "Check the job details.", 400);
    if (jobs.values().stream().anyMatch(job -> draft.reference().equals(job.reference())))
      throw failure("VWORK_DUPLICATE_REFERENCE", "A job already uses this reference.", 409);
    String id = "mock-" + String.format(Locale.ROOT, "%06d", nextId++);
    var job = new Job(id, draft.reference(), draft.templateId(), null, Status.UNASSIGNED, null, null, null);
    jobs.put(id, job);
    return job;
  }
  @Override public synchronized Job job(VworkCredential credential, String id) {
    Objects.requireNonNull(credential, "credential");
    var result = jobs.get(id);
    if (result == null) throw failure("VWORK_JOB_NOT_FOUND", "The job is unavailable.", 404);
    return result;
  }
  @Override public synchronized Page jobs(VworkCredential credential, int page) {
    Objects.requireNonNull(credential, "credential");
    if (page < 1) throw new IllegalArgumentException("Page must be positive");
    var all = List.copyOf(jobs.values());
    long offset = (long) (page - 1) * PAGE_SIZE;
    int start = (int) Math.min(all.size(), offset);
    int end = Math.min(all.size(), start + PAGE_SIZE);
    return new Page(all.subList(start, end), page, PAGE_SIZE, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE, all.size());
  }
  @Override public synchronized List<Job> findByReference(VworkCredential credential, String reference) {
    Objects.requireNonNull(credential, "credential");
    return jobs.values().stream().filter(job -> Objects.equals(job.reference(), reference)).toList();
  }
  public synchronized void schedule(String id, String worker, Instant start) {
    if (blank(worker) || start == null) throw new IllegalArgumentException("Worker and start are required");
    var current = get(id);
    jobs.put(id, new Job(id, current.reference(), current.templateId(), current.templateName(), Status.ASSIGNED, worker, start, null));
  }
  public synchronized void complete(String id, Instant completedAt) {
    Objects.requireNonNull(completedAt, "completedAt");
    var current = get(id);
    jobs.put(id, new Job(id, current.reference(), current.templateId(), current.templateName(), Status.COMPLETED,
        current.workerName(), current.plannedStart(), completedAt));
  }
  private Job get(String id) {
    var result = jobs.get(id);
    if (result == null) throw failure("VWORK_JOB_NOT_FOUND", "The job is unavailable.", 404);
    return result;
  }
  private static boolean blank(String value) { return value == null || value.isBlank(); }
  private static VworkFailure failure(String code, String message, int status) { return new VworkFailure(code, message, status, null); }
}
