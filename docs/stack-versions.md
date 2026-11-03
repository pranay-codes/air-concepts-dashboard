# Stack versions for the skeleton

Versions checked on 4 November 2026 against the sources listed. Re-check before upgrading.

| Component | Chosen version | Source |
|---|---|---|
| Java | 25 (LTS) | Local `java -version`: Temurin 25.0.5 |
| Spring Boot | 4.1.2 | start.spring.io metadata: newest non-RC, non-snapshot release (4.2.0 is RC1) |
| springdoc-openapi | 3.2.0 | springdoc.org FAQ 13.80: Boot 4.1.x pairs with springdoc 3.1.x to 3.2.x. Maven Central lists 3.2.0 as latest |
| Maven | 3.9.16 (via Maven Wrapper) | Maven Central metadata: newest 3.9.x. The 4.0.0 line has only RC builds |
| Maven Wrapper plugin | 3.3.4 | Maven Central metadata: latest release |
| Maven Enforcer plugin | 3.7.0 | Maven Central metadata: latest release |
| Testcontainers | 2.0.5 | Managed by Spring Boot 4.1.2 BOM |
| PostgreSQL JDBC | 42.7.14 | Managed by Spring Boot 4.1.2 BOM |
| Node.js | 26 | nodejs.org release index: 26.13.0 is labelled Latest LTS. 25 is end of life. Active vs Maintenance status not confirmed from the schedule page |
| React, react-dom | 19.3.1 | npm registry |
| Vite | 8.3.5 | npm registry |
| @vitejs/plugin-react | 6.2.1 | npm registry (peer: vite ^8) |
| TypeScript | 6.0.3 | npm registry. 7.0.2 is deferred, see below |
| Vitest | 5.1.0 | npm registry |
| jsdom | 30.1.3 | npm registry |
| @testing-library/react | 16.3.3 | npm registry |
| @testing-library/dom | 10.4.2 | npm registry (required peer of RTL 16) |
| @testing-library/jest-dom | 7.0.1 | npm registry |
| Playwright | 1.64.1 | npm registry |
| ESLint | 10.14.0 | npm registry |
| typescript-eslint | 8.72.1 | npm registry (peer: typescript below 6.1.0) |
| eslint-plugin-react-hooks | 7.1.1 | npm registry |
| Prettier | 3.9.10 | npm registry |
| openapi-typescript | 7.13.0 | npm registry |

## Decisions and deferrals
- **TypeScript 7 is deferred.** 7.0.2 is the newest stable release, but typescript-eslint 8.72.1 declares a peer range of `<6.1.0` and cannot load the TypeScript 7 native compiler. Revisit when typescript-eslint supports it.
- **Node 26 is pinned in `.nvmrc`** and `engines`. It is the newest LTS line. Confirm its Active status in the Node release schedule before relying on it.
- **Supabase CLI is not installed**, so `supabase init` and `supabase/config.toml` are not created. TODO for the CI prompt: install the CLI in CI and create `config.toml` there.

## Unverified
- Supabase signs JWTs with ES256 or RS256. The JWT decoder accepts both. This has not been checked against a real project.
- The Testcontainers test uses the `postgres:17-alpine` image. It has not been pulled or run, because Docker is not running here.
