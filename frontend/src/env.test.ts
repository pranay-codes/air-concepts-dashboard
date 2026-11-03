import { describe, expect, it } from "vitest";

import { readEnv } from "./env";

const complete = {
  VITE_API_BASE_URL: "http://localhost:8080",
  VITE_SUPABASE_URL: "https://placeholder.invalid",
};

describe("readEnv", () => {
  it("returns both settings when present", () => {
    expect(readEnv(complete)).toEqual({
      apiBaseUrl: "http://localhost:8080",
      supabaseUrl: "https://placeholder.invalid",
    });
  });

  it("names every missing setting in one error", () => {
    expect(() => readEnv({})).toThrowError(/VITE_API_BASE_URL, VITE_SUPABASE_URL/);
  });

  it("treats a blank setting as missing", () => {
    expect(() => readEnv({ ...complete, VITE_SUPABASE_URL: "   " })).toThrowError(
      /Missing required frontend settings: VITE_SUPABASE_URL/,
    );
  });
});
