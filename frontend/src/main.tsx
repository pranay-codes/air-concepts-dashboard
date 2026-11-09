import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { App } from "./App";
import { createSupabaseAuth } from "./auth/supabaseAuth";
import { loadEnv } from "./env";
import "./ui/styles.css";

const container = document.getElementById("root");
if (!container) {
  throw new Error("Missing #root element in index.html");
}

const env = loadEnv();

createRoot(container).render(
  <StrictMode>
    <App auth={createSupabaseAuth(env)} apiBaseUrl={env.apiBaseUrl} />
  </StrictMode>,
);
