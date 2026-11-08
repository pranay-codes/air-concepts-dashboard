import { Component, type ReactNode } from "react";
import { AppFrame } from "./AppFrame";

/** Unexpected rendering/chunk failures never reveal raw exception details. Request failures have their own recovery. */
export class ErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? (
      <AppFrame>
        <p role="alert">The application could not open this section. Reload to try again.</p>
        <button type="button" onClick={() => window.location.reload()}>
          Reload application
        </button>
      </AppFrame>
    ) : (
      this.props.children
    );
  }
}
