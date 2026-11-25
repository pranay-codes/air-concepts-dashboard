import { describe, expect, it } from "vitest";
import { activityEntries } from "./activity";
import type { ActivityEvent } from "./activity";

const sam = { id: "00000000-0000-4000-8000-000000000001", displayName: "Sam" };
const alex = { id: "00000000-0000-4000-8000-000000000002", displayName: "Alex" };
const AT = "2026-11-23T10:11:00Z";

function event(overrides: Partial<ActivityEvent>): ActivityEvent {
  return { id: 1, at: AT, source: "XERO_SYNC", eventType: "STAGE_CHANGED", ...overrides };
}
function only(events: ActivityEvent[]) {
  const entries = activityEntries(events);
  expect(entries).toHaveLength(1);
  return entries[0]!;
}

describe("GL-07 activity history", () => {
  it("names the outcome and shows the previous and new stage and owner", () => {
    const entry = only([
      event({
        source: "VWORK_SYNC",
        fromStage: "WORK_IN_PROGRESS",
        toStage: "JOB_COMPLETED",
        fromOwner: sam,
        toOwner: alex,
        details: {
          fromJob: { status: "STARTED", technician: "Jo", scheduledStart: "2026-11-22T20:00:00Z" },
          toJob: {
            status: "COMPLETED",
            technician: "Jo",
            scheduledStart: "2026-11-22T20:00:00Z",
            completedAt: "2026-11-23T09:55:00Z",
          },
        },
      }),
    ]);
    expect(entry.title).toBe("Job completed");
    expect(entry.actor).toBe("vWork sync");
    expect(entry.changes).toEqual([
      { label: "Stage", from: "Work in progress", to: "Job completed" },
      { label: "Owner", from: "Sam", to: "Alex" },
      { label: "Completed in vWork", to: "23 Nov 2026, 10:55 pm" },
    ]);
  });

  it("shows when and to whom a job was scheduled, once for the rows of one action", () => {
    const toJob = {
      status: "ASSIGNED",
      technician: "Jo",
      scheduledStart: "2026-11-25T20:00:00Z",
    };
    const entry = only([
      event({
        id: 2,
        source: "VWORK_SYNC",
        fromStage: "TO_SCHEDULE",
        toStage: "SCHEDULED",
        details: { toJob },
      }),
      event({
        id: 1,
        source: "VWORK_SYNC",
        eventType: "JOB_LINKED",
        fromStage: "TO_SCHEDULE",
        toStage: "TO_SCHEDULE",
        details: { toJob },
      }),
    ]);
    expect(entry.title).toBe("Job scheduled");
    expect(entry.changes).toEqual([
      { label: "Stage", from: "To schedule", to: "Job scheduled" },
      { label: "Scheduled for", to: "26 Nov 2026, 9:00 am" },
      { label: "Technician", to: "Jo" },
    ]);
  });

  it("merges the linked invoice and its stage change into Invoice created", () => {
    const both = {
      fromStage: "JOB_COMPLETED",
      toStage: "INVOICE_CREATED",
      details: { invoiceNumber: "INV-42" },
    };
    const entry = only([
      event({ id: 2, ...both }),
      event({ id: 1, eventType: "INVOICE_LINKED", ...both }),
    ]);
    expect(entry.title).toBe("Invoice created");
    expect(entry.actor).toBe("Xero sync");
    expect(entry.changes).toEqual([
      { label: "Stage", from: "Job completed", to: "Invoice created" },
      { label: "Invoice", to: "INV-42" },
    ]);
  });

  it("attributes a confirmed invoice write to System", () => {
    const entry = only([
      event({ id: 2, fromStage: "JOB_COMPLETED", toStage: "INVOICE_CREATED" }),
      event({ id: 1, source: "SYSTEM", eventType: "INVOICE_LINKED" }),
    ]);
    expect(entry.actor).toBe("System");
  });

  it("records a manual owner change from Unassigned", () => {
    const entry = only([
      event({ source: "USER", actor: sam, eventType: "OWNER_CHANGED", toOwner: alex }),
    ]);
    expect(entry.title).toBe("Owner changed");
    expect(entry.actor).toBe("Sam");
    expect(entry.changes).toEqual([{ label: "Owner", from: "Unassigned", to: "Alex" }]);
  });

  it("groups one save of several fields without showing note text", () => {
    const user = { source: "USER", actor: sam };
    const entry = only([
      event({ id: 3, ...user, eventType: "OWNER_CHANGED", fromOwner: sam }),
      event({ id: 2, ...user, eventType: "NOTES_CHANGED" }),
      event({
        id: 1,
        ...user,
        eventType: "FOLLOW_UP_CHANGED",
        details: { toFollowUpDate: "2026-11-30" },
      }),
    ]);
    expect(entry.title).toBe("Details updated");
    expect(entry.changes).toEqual([
      { label: "Owner", from: "Sam", to: "Unassigned" },
      { label: "Follow-up", from: "Not set", to: "30 Nov 2026" },
      { label: "Notes", to: "Updated" },
    ]);
  });

  it("shows a reschedule that leaves the stage unchanged", () => {
    const entry = only([
      event({
        source: "VWORK_SYNC",
        eventType: "JOB_UPDATED",
        fromStage: "SCHEDULED",
        toStage: "SCHEDULED",
        details: {
          fromJob: { status: "ASSIGNED", technician: "Jo", scheduledStart: "2026-11-24T20:00:00Z" },
          toJob: { status: "ASSIGNED", technician: "Kit", scheduledStart: "2026-11-26T20:00:00Z" },
        },
      }),
    ]);
    expect(entry.title).toBe("Job rescheduled");
    expect(entry.changes).toEqual([
      { label: "Scheduled for", from: "25 Nov 2026, 9:00 am", to: "27 Nov 2026, 9:00 am" },
      { label: "Technician", from: "Jo", to: "Kit" },
    ]);
  });

  it("shows completion recorded after the invoice was created", () => {
    const entry = only([
      event({
        source: "VWORK_SYNC",
        eventType: "JOB_UPDATED",
        fromStage: "INVOICE_CREATED",
        toStage: "INVOICE_CREATED",
        details: {
          fromJob: { status: "STARTED" },
          toJob: { status: "COMPLETED", completedAt: "2026-11-23T09:55:00Z" },
        },
      }),
    ]);
    expect(entry.title).toBe("Job completed");
    expect(entry.changes).toEqual([
      { label: "vWork status", from: "Started", to: "Completed" },
      { label: "Completed in vWork", to: "23 Nov 2026, 10:55 pm" },
    ]);
  });

  it("says a job moved back rather than naming it as a new milestone", () => {
    const entry = only([event({ fromStage: "WORK_IN_PROGRESS", toStage: "SCHEDULED" })]);
    expect(entry.title).toBe("Moved back to Job scheduled");
  });

  it("shows payment and manual closure with the recorded reason", () => {
    expect(
      only([event({ fromStage: "INVOICED", toStage: "CLOSED", details: { closeReason: "PAID" } })])
        .title,
    ).toBe("Invoice paid");
    const lost = only([
      event({
        source: "USER",
        actor: sam,
        eventType: "CLOSED_LOST",
        fromStage: "QUOTED",
        toStage: "CLOSED",
        details: { reason: "Went elsewhere" },
      }),
    ]);
    expect(lost.title).toBe("Closed as lost");
    expect(lost.changes).toContainEqual({ label: "Reason", to: "Went elsewhere" });
  });

  it("shows only what an older record holds", () => {
    const entry = only([event({ source: "SYSTEM" })]);
    expect(entry.title).toBe("Stage changed");
    expect(entry.changes).toEqual([]);
  });

  it("keeps separate actions separate and newest first", () => {
    const entries = activityEntries([
      event({ id: 2, at: "2026-11-24T01:30:00Z", fromStage: "JOB_COMPLETED", toStage: "INVOICED" }),
      event({ id: 1, eventType: "ITEM_CREATED", toStage: "QUOTED", toOwner: sam }),
    ]);
    expect(entries.map((entry) => entry.title)).toEqual(["Invoice sent", "Work item created"]);
    expect(entries[1]!.changes).toEqual([
      { label: "Stage", to: "Quoted" },
      { label: "Owner", to: "Sam" },
    ]);
  });
});
