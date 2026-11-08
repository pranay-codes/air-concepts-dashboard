import type { ReactNode } from "react";

export function AppFrame({ children }: { children?: ReactNode }) {
  return (
    <main className="app-root">
      <h1>Air Concepts Financial Admin Dashboard</h1>
      <section aria-label="Dashboard content">{children}</section>
    </main>
  );
}
