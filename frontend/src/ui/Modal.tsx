import { useEffect, useId, useRef, type ReactNode, type KeyboardEvent } from "react";
import { createPortal } from "react-dom";

const focusableSelector = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  '[tabindex]:not([tabindex="-1"])',
].join(",");

/**
 * Accessible modal dialog: focus moves inside on open, Tab stays inside, Escape calls onClose,
 * the page behind is inert, and focus returns to the element that opened the dialog.
 * The caller decides what closing means; a caller with unsaved input can refuse or confirm.
 */
export function Modal({
  title,
  children,
  onClose,
  describedBy,
}: {
  title: string;
  children: ReactNode;
  onClose(): void;
  describedBy?: string;
}) {
  const titleId = useId();
  const dialog = useRef<HTMLDivElement>(null);
  const opener = useRef<Element | null>(document.activeElement);
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    const page = document.getElementById("root");
    page?.setAttribute("inert", "");
    const returnFocus = opener.current;
    const first =
      dialog.current?.querySelector<HTMLElement>("[data-autofocus]") ??
      dialog.current?.querySelector<HTMLElement>(focusableSelector);
    first?.focus();
    return () => {
      page?.removeAttribute("inert");
      if (returnFocus instanceof HTMLElement && returnFocus.isConnected) returnFocus.focus();
    };
  }, []);

  function keyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.stopPropagation();
      onCloseRef.current();
      return;
    }
    if (event.key !== "Tab" || !dialog.current) return;
    const items = Array.from(dialog.current.querySelectorAll<HTMLElement>(focusableSelector));
    if (items.length === 0) return;
    const firstItem = items[0]!;
    const lastItem = items[items.length - 1]!;
    if (event.shiftKey && document.activeElement === firstItem) {
      event.preventDefault();
      lastItem.focus();
    } else if (!event.shiftKey && document.activeElement === lastItem) {
      event.preventDefault();
      firstItem.focus();
    }
  }

  return createPortal(
    <div className="modal-backdrop">
      <div
        ref={dialog}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={describedBy}
        onKeyDown={keyDown}
      >
        <h2 id={titleId}>{title}</h2>
        {children}
      </div>
    </div>,
    document.body,
  );
}
