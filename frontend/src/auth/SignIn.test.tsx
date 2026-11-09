import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SignIn } from "./SignIn";
import { AuthError, type AuthService } from "./authService";

function fakeAuth(overrides: Partial<AuthService> = {}): AuthService {
  return {
    getAccessToken: vi.fn().mockResolvedValue(null),
    requestCode: vi.fn().mockResolvedValue(undefined),
    verifyCode: vi.fn().mockResolvedValue(undefined),
    signOut: vi.fn().mockResolvedValue(undefined),
    onSessionChange: vi.fn(() => () => {}),
    ...overrides,
  };
}

function request(email = "tester@example.invalid") {
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: email } });
  fireEvent.submit(screen.getByRole("form", { name: "Sign in" }));
}

async function enterCode(value = "123456") {
  const input = await screen.findByLabelText("Six-digit code");
  fireEvent.change(input, { target: { value } });
  fireEvent.submit(screen.getByRole("form", { name: "Verify code" }));
}

afterEach(() => vi.useRealTimers());

describe("SignIn", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("FR-UC15-17 requests then verifies a pasted code with autofill support", async () => {
    const auth = fakeAuth();
    const onSignedIn = vi.fn();
    render(<SignIn auth={auth} onSignedIn={onSignedIn} />);

    request();
    const input = await screen.findByLabelText("Six-digit code");
    expect(input).toHaveAttribute("autocomplete", "one-time-code");
    expect(input).toHaveAttribute("inputmode", "numeric");
    expect(screen.queryByLabelText("Password")).not.toBeInTheDocument();
    expect(onSignedIn).not.toHaveBeenCalled();
    fireEvent.paste(input, { clipboardData: { getData: () => "123 456" } });
    fireEvent.submit(screen.getByRole("form", { name: "Verify code" }));

    await waitFor(() => expect(onSignedIn).toHaveBeenCalledTimes(1));
    expect(auth.requestCode).toHaveBeenCalledWith("tester@example.invalid");
    expect(auth.verifyCode).toHaveBeenCalledWith("tester@example.invalid", "123456");
  });

  it("FR-UC15-17 preserves email and shows safe request errors without reporting success", async () => {
    const auth = fakeAuth({
      requestCode: vi
        .fn()
        .mockRejectedValue(
          new AuthError("Could not send a code. Check your registered email and try again."),
        ),
    });
    const onSignedIn = vi.fn();
    render(<SignIn auth={auth} onSignedIn={onSignedIn} />);

    request();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not send a code. Check your registered email and try again.",
    );
    expect(screen.getByLabelText("Email")).toHaveValue("tester@example.invalid");
    expect(onSignedIn).not.toHaveBeenCalled();
  });

  it("shows a generic message for an unexpected error", async () => {
    const auth = fakeAuth({ requestCode: vi.fn().mockRejectedValue(new Error("network down")) });
    render(<SignIn auth={auth} onSignedIn={vi.fn()} />);

    request();

    expect(await screen.findByRole("alert")).toHaveTextContent("Could not send a code. Try again.");
    expect(screen.queryByText(/network down/)).not.toBeInTheDocument();
  });

  it("FR-UC15-17 validates code length and keeps expired-code recovery visible", async () => {
    const auth = fakeAuth({
      verifyCode: vi
        .fn()
        .mockRejectedValue(
          new AuthError("This code has expired. Request a new code and try again."),
        ),
    });
    const done = vi.fn();
    render(<SignIn auth={auth} onSignedIn={done} />);
    request();
    await enterCode("123");
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Enter the six-digit code from your email.",
    );
    expect(auth.verifyCode).not.toHaveBeenCalled();
    await enterCode();
    expect(await screen.findByRole("alert")).toHaveTextContent("expired");
    expect(done).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: /Resend code/ })).toBeInTheDocument();
  });

  it("FR-UC15-17 enforces resend cooldown and clears code when changing email", async () => {
    vi.useFakeTimers();
    const auth = fakeAuth();
    render(<SignIn auth={auth} onSignedIn={vi.fn()} />);
    await act(async () => request());
    expect(screen.getByLabelText("Six-digit code")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Resend code/ })).toBeDisabled();
    act(() => vi.advanceTimersByTime(61000));
    expect(screen.getByRole("button", { name: /Resend code/ })).toBeEnabled();
    vi.useRealTimers();
    fireEvent.click(screen.getByRole("button", { name: "Use a different email" }));
    expect(screen.getByLabelText("Email")).toHaveValue("tester@example.invalid");
    expect(screen.queryByLabelText("Six-digit code")).not.toBeInTheDocument();
    expect(auth.requestCode).toHaveBeenCalledOnce();
  });

  it("FR-UC15-17 prevents duplicate requests while the provider is pending", async () => {
    let finish = () => {};
    const auth = fakeAuth({
      requestCode: vi.fn(
        () =>
          new Promise<void>((resolve) => {
            finish = resolve;
          }),
      ),
    });
    render(<SignIn auth={auth} onSignedIn={vi.fn()} />);
    request();
    fireEvent.submit(screen.getByRole("form", { name: "Sign in" }));
    expect(auth.requestCode).toHaveBeenCalledOnce();
    await act(async () => finish());
    await waitFor(() => expect(screen.getByLabelText("Six-digit code")).toBeInTheDocument());
  });

  it("FR-UC15-17 shows expiry recovery after ten minutes", async () => {
    vi.useFakeTimers();
    render(<SignIn auth={fakeAuth()} onSignedIn={vi.fn()} />);
    await act(async () => request());
    act(() => vi.advanceTimersByTime(601000));
    expect(screen.getByText("This code has expired. Request a new code.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Verify and sign in" })).toBeDisabled();
    expect(screen.getByRole("button", { name: /Resend code/ })).toBeEnabled();
  });

  it("FR-UC15-18 ignores a verification result after leaving the sign-in view", async () => {
    let finish = () => {};
    const auth = fakeAuth({
      verifyCode: vi.fn(
        () =>
          new Promise<void>((resolve) => {
            finish = resolve;
          }),
      ),
    });
    const signedIn = vi.fn();
    const view = render(<SignIn auth={auth} onSignedIn={signedIn} />);
    request();
    await enterCode();
    view.unmount();
    await act(async () => finish());
    expect(signedIn).not.toHaveBeenCalled();
  });
});
