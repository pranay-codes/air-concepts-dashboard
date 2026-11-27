# Company forecast notes implementation plan

**Goal:** Persist one company note per entity, independent of document notes, shared across forecast directions and periods.

**Prompt contract:** Reuse existing note editor and forecast authorisation; keep notes out of Xero. Company identity follows the grid's exact displayed name, so a rename produces a new group. Never affect document notes, other entities or forecast values.

**Files and checks:** Extend the UC4 spec, create a forward-only RLS-protected migration, add repository/service/controller support and grid data, regenerate OpenAPI/types, and add grid note editing. Write focused backend and frontend tests first, run them and relevant frontend/API checks. CI handles database integration; no local Docker or hosted database.

**Definition of done:** A group note can be added, edited, cleared and reloaded independently of document notes, shares across directions within the same entity, denies unauthorised access, and preserves input on errors. Tests and contract checks pass, limitations are documented, and a PR against main is opened for independent review and CI.
