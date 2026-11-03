---
name: api-contract
description: Use when a task changes a backend controller or DTO, so the OpenAPI snapshot and the frontend's generated types are regenerated and committed together.
---

# api-contract

Changes the public API. Rules: AGENTS.md sections 5 and 13. A human must approve the contract before this runs (AGENTS.md section 5, step 4).

## Scope
- `docs/api/openapi.json` and `frontend/src/api/generated/` are generated. Never edit either by hand.
- Stop and ask if the snapshot diff contains endpoints unrelated to the task.

## Steps
1. Change the controller or DTO in `backend/src/main/java/com/airconcepts/dashboard/web/`.
2. From `/backend`, regenerate the snapshot: `./mvnw -q test -Dopenapi.update=true`.
3. Review `git diff docs/api/openapi.json`. If it touches unrelated endpoints, stop and ask.
4. From `/frontend`, regenerate the types: `npm run api:generate`.
5. From the repo root, run both test scripts: `./scripts/test-backend.sh` and `./scripts/test-frontend.sh`.
6. Confirm `./scripts/check-all.sh` passes. It includes `api:check`.
7. Commit `docs/api/openapi.json` and `frontend/src/api/generated/` together with the code change, in a conventional commit that includes the requirement ID.

## If a check fails
- Backend snapshot test fails: the message says to regenerate. Do step 2.
- `api:check` fails: the generated types are stale. Do step 4, then commit.
