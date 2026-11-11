import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { TransactionNoteEditor } from "./TransactionNoteEditor";

const label = "INV-0017 · City Limousines";

it("opens an inline editor from Add note, and Escape cancels and returns focus to Add note", () => {
  const onSave = vi.fn();
  render(<TransactionNoteEditor label={label} note={null} onSave={onSave} />);
  const add = screen.getByRole("button", { name: `Add note for ${label}` });
  fireEvent.click(add);
  const field = screen.getByRole("textbox", { name: `Note for ${label}` });
  expect(document.activeElement).toBe(field);
  fireEvent.keyDown(field, { key: "Escape" });
  expect(screen.queryByRole("textbox")).toBeNull();
  expect(document.activeElement).toBe(
    screen.getByRole("button", { name: `Add note for ${label}` }),
  );
  expect(onSave).not.toHaveBeenCalled();
});

it("shows a preview of an existing note and reopens the editor with the full text", () => {
  const note = "Customer pays by bank transfer on the 20th, chase if late";
  render(<TransactionNoteEditor label={label} note={note} onSave={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: `Edit note for ${label}: ${note}` }));
  expect((screen.getByRole("textbox") as HTMLTextAreaElement).value).toBe(note);
});

it("saves trimmed text, closes the editor and confirms the save", async () => {
  const onSave = vi.fn().mockResolvedValue(undefined);
  render(<TransactionNoteEditor label={label} note={null} onSave={onSave} />);
  fireEvent.click(screen.getByRole("button", { name: `Add note for ${label}` }));
  fireEvent.change(screen.getByRole("textbox"), { target: { value: "  Call Monday  " } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() => expect(onSave).toHaveBeenCalledWith("Call Monday"));
  expect(await screen.findByRole("status")).toBeTruthy();
  expect(screen.queryByRole("textbox")).toBeNull();
});

it("keeps the draft and shows the error when a save fails", async () => {
  const onSave = vi.fn().mockRejectedValue(new Error("Network down"));
  render(<TransactionNoteEditor label={label} note={null} onSave={onSave} />);
  fireEvent.click(screen.getByRole("button", { name: `Add note for ${label}` }));
  fireEvent.change(screen.getByRole("textbox"), { target: { value: "Keep this text" } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  expect(await screen.findByRole("alert")).toBeTruthy();
  expect((screen.getByRole("textbox") as HTMLTextAreaElement).value).toBe("Keep this text");
  expect(screen.getByRole("button", { name: "Save" })).toBeTruthy();
});

it("rejects text over 2000 code points without calling the server", async () => {
  const onSave = vi.fn();
  render(<TransactionNoteEditor label={label} note={null} onSave={onSave} />);
  fireEvent.click(screen.getByRole("button", { name: `Add note for ${label}` }));
  fireEvent.change(screen.getByRole("textbox"), { target: { value: "😀".repeat(2001) } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  expect((await screen.findByRole("alert")).textContent).toContain("up to 2000 characters");
  expect(onSave).not.toHaveBeenCalled();
});

it("accepts exactly 2000 code points", async () => {
  const onSave = vi.fn().mockResolvedValue(undefined);
  render(<TransactionNoteEditor label={label} note={null} onSave={onSave} />);
  fireEvent.click(screen.getByRole("button", { name: `Add note for ${label}` }));
  fireEvent.change(screen.getByRole("textbox"), { target: { value: "😀".repeat(2000) } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
});

it("saves with Ctrl+Enter from the textarea", async () => {
  const onSave = vi.fn().mockResolvedValue(undefined);
  render(<TransactionNoteEditor label={label} note={null} onSave={onSave} />);
  fireEvent.click(screen.getByRole("button", { name: `Add note for ${label}` }));
  const field = screen.getByRole("textbox");
  fireEvent.change(field, { target: { value: "Shortcut save" } });
  fireEvent.keyDown(field, { key: "Enter", ctrlKey: true });
  await waitFor(() => expect(onSave).toHaveBeenCalledWith("Shortcut save"));
});

it("sends a blank note so the saved note is removed", async () => {
  const onSave = vi.fn().mockResolvedValue(undefined);
  render(<TransactionNoteEditor label={label} note="Old note" onSave={onSave} />);
  fireEvent.click(screen.getByRole("button", { name: `Edit note for ${label}: Old note` }));
  fireEvent.change(screen.getByRole("textbox"), { target: { value: "   " } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() => expect(onSave).toHaveBeenCalledWith(""));
});

it("closes without a server call when the text is unchanged", () => {
  const onSave = vi.fn();
  render(<TransactionNoteEditor label={label} note="Same" onSave={onSave} />);
  fireEvent.click(screen.getByRole("button", { name: `Edit note for ${label}: Same` }));
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  expect(onSave).not.toHaveBeenCalled();
  expect(screen.queryByRole("textbox")).toBeNull();
});

it("keeps the visible hint to a few words and gives the keyboard shortcut as an accessible description", () => {
  render(<TransactionNoteEditor label={label} note={null} onSave={vi.fn()} />);
  fireEvent.click(screen.getByRole("button", { name: `Add note for ${label}` }));
  const field = screen.getByRole("textbox", { name: `Note for ${label}` });
  const description = (field.getAttribute("aria-describedby") ?? "")
    .split(" ")
    .map((id) => document.getElementById(id)?.textContent ?? "")
    .join(" ");
  expect(description).toContain("Blank removes the note.");
  expect(description).toContain("Ctrl or Cmd with Enter to save.");
  expect(screen.queryByText(/Leave blank/)).toBeNull();
  expect(screen.getByText("Blank removes the note.", { exact: false })).toBeTruthy();
});
