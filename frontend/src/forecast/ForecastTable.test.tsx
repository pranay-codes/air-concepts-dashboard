import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ForecastTable } from "./ForecastTable";
it("FR-UC04-05 shows missing opening as Not recorded, flags negative cash and opens a month", () => {
  const onOpen = vi.fn();
  const onOpening = vi.fn();
  render(
    <ForecastTable
      months={[
        {
          month: "2026-11",
          opening: null,
          receipts: "100.00",
          payments: "200.00",
          net: "-100.00",
          closing: "-100.00",
          receiptCount: 1,
          paymentCount: 1,
          containsOverdue: true,
        },
      ]}
      onOpen={onOpen}
      onOpening={onOpening}
    />,
  );
  expect(screen.getAllByText("Not recorded").length).toBeGreaterThan(0);
  expect(screen.getAllByText("Negative closing balance").length).toBeGreaterThan(0);
  fireEvent.click(screen.getAllByRole("button", { name: /View .*2026/ })[0]!);
  expect(onOpen).toHaveBeenCalledWith("2026-11");
});
