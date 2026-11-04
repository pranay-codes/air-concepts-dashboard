import type { components } from "./generated/schema";

export type Me = components["schemas"]["MeResponse"];

/** Calls the protected profile endpoint. The token is sent only in the Authorization header. */
export async function fetchMe(
  apiBaseUrl: string,
  token: string,
  fetchImpl: typeof fetch = fetch,
): Promise<Me> {
  const response = await fetchImpl(`${apiBaseUrl}/api/v1/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    throw new Error(`GET /api/v1/me failed with status ${response.status}`);
  }
  return (await response.json()) as Me;
}
