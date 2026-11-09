import { act, renderHook, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useResource } from "./useResource";

function deferred<T>() {
  let resolve: (value: T) => void = () => {};
  let reject: (reason: Error) => void = () => {};
  const promise = new Promise<T>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}

describe("UC5 cancellable resource owner", () => {
  it("FR-UC15-30 rejects late A→B→A responses even when the transport ignores abort", async () => {
    const first = deferred<string>(),
      second = deferred<string>(),
      third = deferred<string>();
    const load = vi
      .fn<(signal: AbortSignal) => Promise<string>>()
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
      .mockReturnValueOnce(third.promise);
    const view = renderHook(({ key }) => useResource(key, load), {
      initialProps: { key: "staff:A" },
    });
    await waitFor(() => expect(load).toHaveBeenCalledTimes(1));
    view.rerender({ key: "staff:B" });
    expect(view.result.current.data).toBeUndefined();
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
    view.rerender({ key: "staff:A" });
    await waitFor(() => expect(load).toHaveBeenCalledTimes(3));
    await act(async () => {
      first.resolve("stale A");
      second.resolve("stale B");
    });
    expect(view.result.current.data).toBeUndefined();
    expect(view.result.current.status).toBe("loading");
    await act(async () => third.resolve("fresh A"));
    expect(view.result.current.data).toBe("fresh A");
    expect(load.mock.calls[0]?.[0].aborted).toBe(true);
    expect(load.mock.calls[1]?.[0].aborted).toBe(true);
  });

  it("FR-UC15-13 keeps a same-context refresh visibly stale, then removes data on failure", async () => {
    const next = deferred<string>();
    const load = vi
      .fn<(signal: AbortSignal) => Promise<string>>()
      .mockResolvedValueOnce("confirmed")
      .mockReturnValueOnce(next.promise);
    const { result } = renderHook(() => useResource("staff:A", load));
    await waitFor(() => expect(result.current.status).toBe("success"));
    act(() => result.current.refresh());
    expect(result.current.status).toBe("refreshing");
    expect(result.current.data).toBe("confirmed");
    await act(async () => next.reject(new Error("synthetic failure")));
    expect(result.current.status).toBe("error");
    expect(result.current.data).toBeUndefined();
  });

  it("FR-UC15-18 disabled/revoked resources expose no old data and refetch on restoration", async () => {
    const next = deferred<string>();
    const load = vi
      .fn<(signal: AbortSignal) => Promise<string>>()
      .mockResolvedValueOnce("old protected value")
      .mockReturnValueOnce(next.promise);
    const view = renderHook(({ enabled }) => useResource("staff:A", load, enabled), {
      initialProps: { enabled: true },
    });
    await waitFor(() => expect(view.result.current.status).toBe("success"));
    view.rerender({ enabled: false });
    expect(view.result.current.status).toBe("idle");
    expect(view.result.current.data).toBeUndefined();
    view.rerender({ enabled: true });
    expect(view.result.current.data).toBeUndefined();
    await waitFor(() => expect(load).toHaveBeenCalledTimes(2));
    await act(async () => next.resolve("fresh authorised value"));
    expect(view.result.current.data).toBe("fresh authorised value");
  });

  it("FR-UC15-30 aborts unmounted requests and ignores their failures", async () => {
    const pending = deferred<string>();
    const load = vi.fn<(signal: AbortSignal) => Promise<string>>().mockReturnValue(pending.promise);
    const view = renderHook(() => useResource("staff:A", load));
    await waitFor(() => expect(load).toHaveBeenCalledOnce());
    view.unmount();
    expect(load.mock.calls[0]?.[0].aborted).toBe(true);
    await act(async () => pending.reject(new Error("late synthetic error")));
  });
});
