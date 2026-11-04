import { describe, expect, it } from "vitest";

import { readEnv } from "./env";

const complete = {
  VITE_API_BASE_URL: "http://localhost:8080",
  VITE_SUPABASE_URL: "https://placeholder.supabase.co",
  VITE_SUPABASE_PUBLISHABLE_KEY: "placeholder-publishable-key",
};

describe("readEnv", () => {
  it("returns all settings when present", () => {
    expect(readEnv(complete)).toEqual({
      apiBaseUrl: "http://localhost:8080",
      supabaseUrl: "https://placeholder.supabase.co",
      supabasePublishableKey: "placeholder-publishable-key",
    });
  });

  it("names every missing setting in one error", () => {
    expect(() => readEnv({})).toThrowError(
      /VITE_API_BASE_URL, VITE_SUPABASE_URL, VITE_SUPABASE_PUBLISHABLE_KEY/,
    );
  });

  it("treats a blank setting as missing", () => {
    expect(() => readEnv({ ...complete, VITE_SUPABASE_URL: "   " })).toThrowError(
      /Missing required frontend settings: VITE_SUPABASE_URL/,
    );
  });

  it("requires the publishable key so the browser can sign in", () => {
    expect(() => readEnv({ ...complete, VITE_SUPABASE_PUBLISHABLE_KEY: "" })).toThrowError(
      /VITE_SUPABASE_PUBLISHABLE_KEY/,
    );
  });
});
