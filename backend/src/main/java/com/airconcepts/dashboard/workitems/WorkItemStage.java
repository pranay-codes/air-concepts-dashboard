package com.airconcepts.dashboard.workitems;

public enum WorkItemStage {
  /** Approved dashboard stages: Quoted, To schedule, Job scheduled, Work in progress, Job completed, Invoiced, plus Closed. */
  QUOTED, TO_SCHEDULE, SCHEDULED, WORK_IN_PROGRESS, JOB_COMPLETED, INVOICE_CREATED, INVOICED, CLOSED;

  /** Stages in which the next step is on finance: the job is complete, and the invoice is waiting to be created or sent. */
  public boolean awaitsFinance() {
    return this == JOB_COMPLETED || this == INVOICE_CREATED;
  }
}
