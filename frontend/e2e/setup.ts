import { build, preview } from "vite";

/** Own the server directly: no nested npm processes to orphan during Windows teardown. */
export default async function setup() {
  process.env.VITE_API_BASE_URL = "https://synthetic-api.example.invalid";
  process.env.VITE_SUPABASE_URL = "https://synthetic-auth.example.invalid";
  process.env.VITE_SUPABASE_PUBLISHABLE_KEY = "synthetic-publishable-key";
  await build();
  const server = await preview({ preview: { port: 4173, strictPort: true } });
  return async () => {
    if ("closeAllConnections" in server.httpServer) server.httpServer.closeAllConnections();
    await new Promise<void>((resolve, reject) =>
      server.httpServer.close((error) => (error ? reject(error) : resolve())),
    );
  };
}
