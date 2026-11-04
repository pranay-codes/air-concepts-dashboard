import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SignIn } from "./SignIn";
import { AuthError, type AuthService } from "./authService";

function fakeAuth(overrides: Partial<AuthService> = {}): AuthService {
  return {
    getAccessToken: vi.fn().mockResolvedValue(null),
    signIn: vi.fn().mockResolvedValue(undefined),
    signOut: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  };
}

function fillAndSubmit(email: string, password: string) {
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: email } });
  fireEvent.change(screen.getByLabelText("Password"), { target: { value: password } });
  fireEvent.submit(screen.getByRole("form", { name: "Sign in" }));
}

describe("SignIn", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("passes the entered credentials to sign-in and reports success", async () => {
    const auth = fakeAuth();
    const onSignedIn = vi.fn();
    render(<SignIn auth={auth} onSignedIn={onSignedIn} />);

    fillAndSubmit("tester@example.invalid", "placeholder-password");

    await waitFor(() => expect(onSignedIn).toHaveBeenCalledTimes(1));
    expect(auth.signIn).toHaveBeenCalledWith("tester@example.invalid", "placeholder-password");
  });

  it("shows the sign-in error and does not report success when sign-in fails", async () => {
    const auth = fakeAuth({
      signIn: vi
        .fn()
        .mockRejectedValue(new AuthError("Sign-in failed. Check your email and password.")),
    });
    const onSignedIn = vi.fn();
    render(<SignIn auth={auth} onSignedIn={onSignedIn} />);

    fillAndSubmit("tester@example.invalid", "wrong");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Sign-in failed. Check your email and password.",
    );
    expect(onSignedIn).not.toHaveBeenCalled();
  });

  it("shows a generic message for an unexpected error", async () => {
    const auth = fakeAuth({ signIn: vi.fn().mockRejectedValue(new Error("network down")) });
    render(<SignIn auth={auth} onSignedIn={vi.fn()} />);

    fillAndSubmit("tester@example.invalid", "placeholder-password");

    expect(await screen.findByRole("alert")).toHaveTextContent("Sign-in failed. Try again.");
    expect(screen.queryByText(/network down/)).not.toBeInTheDocument();
  });
});
