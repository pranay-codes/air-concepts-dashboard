export interface AppEnv {
  apiBaseUrl: string;
  supabaseUrl: string;
}

const REQUIRED = ["VITE_API_BASE_URL", "VITE_SUPABASE_URL"] as const;

/** Throws one error that names every missing setting. Takes a plain object so tests need no build env. */
export function readEnv(source: Record<string, string | undefined>): AppEnv {
  const missing = REQUIRED.filter((name) => !source[name]?.trim());
  if (missing.length > 0) {
    throw new Error(
      `Missing required frontend settings: ${missing.join(", ")}. Copy .env.EXAMPLE in the repo root to .env and set them.`,
    );
  }
  return {
    apiBaseUrl: source.VITE_API_BASE_URL?.trim() ?? "",
    supabaseUrl: source.VITE_SUPABASE_URL?.trim() ?? "",
  };
}

export function loadEnv(): AppEnv {
  return readEnv(import.meta.env);
}
