# Air Concepts Dashboard – UI/UX Design Specification

## Purpose

The dashboard is a calm, professional workspace for financial administration across connected organisations. Services, Commercial and HotAS are the client's current configuration, not an application limit. Users should immediately understand which entity they are viewing, what needs attention, the financial position and the next action available to them.

Apply this standard across the application. It defines presentation and interaction patterns; approved project requirements govern business capabilities and role permissions.

**Scope and conventions:** Standards below are mandatory unless labelled recommended or optional. Preserve the supplied wireframes' light surfaces, teal accents, attention-first hierarchy and adaptive list/detail layout. This specification does not authorise new financial capabilities or changes to backend contracts. Organisation scalability was confirmed by the human on 6 November 2026; keep the client-facing label **Entity** for the organisation context throughout the interface.

## 1. Visual design

### Appearance and colour

Use a light theme only; do not provide dark mode or switch with the operating-system theme. Use a pale grey workspace, white navigation and white cards, dark blue-grey text and a restrained teal accent. Create hierarchy through typography, spacing and grouping. Keep shadows subtle and decoration minimal.

| Purpose | Colour | Usage |
|---|---|---|
| Workspace | `#F5F6F8` | Main content background |
| Surface | `#FFFFFF` | Navigation, cards, inputs and dialogs |
| Primary text | `#17242E` | Headings, amounts and main content |
| Secondary text | `#52616B` | Supporting information and labels |
| Divider | `#E2E7EC` | Decorative borders and row separators |
| Control boundary | `#7B8790` | Outlines needed to identify controls |
| Primary accent | `#0F6B7F` | Primary actions, links and active navigation |
| Selected surface | `#E7F2F5` | Active navigation and selected rows |
| Success | `#18794E` on `#EAF6EF` | Confirmed success and healthy connections |
| Warning | `#805500` on `#FFF3D6` | Pending work and conditions needing attention |
| Error / overdue | `#B42318` on `#FDECE9` | Errors, overdue amounts and blocked work |
| Information | `#245E9B` on `#EAF1FB` | Informational states |

Use white labels on filled teal buttons. Entity badges use Services blue (`#245E9B` on `#EAF1FB`), Commercial purple (`#7046A6` on `#F1EBF9`) and HotAS orange/brown (`#9A4B10` on `#FBEFE5`). Always include the entity name. Additional organisations use configured, contrast-checked badge variants, with a neutral fallback; unique colours are not required. Keep entity identity separate from urgency and workflow status. Validate contrast in every component state.

### Typography, spacing and styling

- Use a system sans-serif stack led by Segoe UI on Windows. Set body text to 16 px, supporting text and table labels to 14 px, page headings to 24–32 px, section headings to 18–20 px and summary figures to 28–36 px. Use approximately 1.5 line height for body copy.
- Use regular weight for body text and semibold for headings, key values and totals. Use tabular numerals for financial comparisons.
- Use a 4 px spacing scale: 8 px between closely related elements, 12–16 px within rows and controls, and 24–32 px between sections. Default card padding is 24 px on desktop and 16 px on mobile.
- Use 12 px card corners, 8 px control corners and pill shapes for compact badges. Controls are normally 44 px high. Reserve stronger shadows for overlays.
- Use one outline icon family at 20–24 px with consistent stroke weight. Pair unfamiliar icons with text. Give icon-only controls accessible names and tooltips available on focus.

### Hierarchy and language

Place entity context and the page title first, followed by work needing attention and supporting financial information. Keep urgent work near the top on every device. A compact summary may sit beside it on wide screens.

Use plain NZ English and familiar labels such as “Owed to us”, “Bills due” and “Needs attention”. Format dates as `4 Nov 2026`. Right-align money and state GST treatment. Show transaction amounts to two decimal places. Summary figures may use abbreviations such as `$226.9k`, with exact amounts available through accessible detail. Identify NZD wherever currency could be ambiguous.

Use shared `Intl.NumberFormat` and `Intl.DateTimeFormat` helpers with `en-NZ` defaults and explicit currency/time-zone context. Show percentages with consistent precision and their period or basis. Preserve date-only invoice/due dates without time-zone shifts; label synchronisation timestamps with the applicable time zone (Pacific/Auckland for this client). Use the server's authoritative monetary values and rounding. Never add different currencies into one total without an approved conversion rule and visible currency/rate context.

## 2. Layout and navigation

### Application shell

The desktop shell has a left sidebar, top context bar and scrollable workspace. The sidebar contains the Air Concepts identity, primary navigation, Settings and the signed-in user's identity. The top bar contains the entity selector, connection/freshness information and refresh control.

Use this navigation order: **Overview, Tasks, Invoices, Service jobs, Projects, Payables, Forecast**. Place Settings separately near the bottom. Show authorised destinations only. Mark the active destination using teal text/icon, a tinted background and an accessible current-page state. Counts indicate actionable work and have a consistent meaning.

Allow the sidebar to collapse into an icon rail. Preserve destination order and provide accessible labels and focusable tooltips.

### Entity context

Keep a prominent selector labelled **Entity**, showing the full current name. Populate it from the user's authorised organisations using stable IDs, not hard-coded names, positions or a fixed organisation count. Support one or many organisations with a searchable, keyboard-operable selector, predictable ordering and readable long names. Offer **All entities** only to authorised users. If no organisation is available, explain the access/connection state and the permitted next step.

All entities is an aggregate viewing context. Users open a specific entity before acting on its records. Label records with their entity and identify which entities contribute to combined totals.

When switching entity, clear incompatible selections and show loading before displaying the new data. Never show one entity's records under another entity's heading. Retain filters only when valid. Protect unsaved edits with save/discard choices before switching.

Keep focus on the selector after a successful switch and announce the new entity and loading/completion state. A cancelled switch keeps the original context. Use one shared organisation context for routes, headings, forms, requests and caches; resolve and validate it before fetching records. Revalidate stored or linked selections against current access, and provide a clear recovery path for an inaccessible entity or record.

### List and detail navigation

Use side-by-side list/detail panes when both remain readable. Highlight the selected row with a tinted surface and teal edge. On smaller screens, open detail as a separate view with a clear Back control. Returning restores filters, selection and scroll position.

## 3. Shared UI components

| Component | Interface standard |
|---|---|
| Buttons | Filled teal primary, outlined secondary and text-style low-emphasis actions. Use specific verbs, with one dominant action per action group. Provide hover, focus, pressed, disabled and busy states. |
| Links | Use teal and underline links within content. Links navigate; buttons perform actions. Financial values may link to explaining records. |
| Cards | White surface, fine border and consistent padding/radius. Place titles top left and supporting actions top right. Use dividers for related rows. Make clickable cards clearly identifiable and keyboard accessible. |
| Summary figures | Short label, prominent value and relevant period/freshness context. Use identical metric definitions across devices. Distinguish forecasts from actual balances. |
| Tables | Left-align descriptions and right-align money. Keep column precision consistent. Use subtle row separators, bold totals, result counts and visible sort direction. Preserve headers in long tables. |
| Attention lists | Identify urgency, affected record, entity, ownership where relevant and a clear next action. Apply the same urgency order across devices. Keep all items reachable. |
| Forms | Persistent labels above controls, clear required/optional treatment, concise helper text and inline validation. Preserve input after errors. Placeholders provide examples rather than replacing labels. |
| Dropdowns | Show the selected value and a disclosure indicator. Support keyboard selection and search for long lists. Distinguish inactive people and unavailable choices. |
| Search and filters | Place above results. Show selected filters and counts where useful, with “Clear filters”. Wrap controls or group extra filters into an accessible menu before labels clip. |
| Tabs | Switch related content panels with a clear active state and keyboard support. Keep every label reachable without competing scrollbars. |
| Badges | Pair text and semantic colour. Show entity, workflow and urgency separately; “Awaiting payment” and “Overdue” can coexist. |
| Modals | Use for short decisions or focused edits. Include a title and clear action/cancel choices, set initial focus, make background content inert, contain focus and return it to the trigger or a logical successor. Escape follows cancel, protecting unsaved edits. Use full detail views for lengthy work. |
| Charts | Prefer simple bars or lines with direct labels, units, periods and consistent scales. Provide accessible equivalent data. Distinguish actual/forecast series, zero and unavailable values. |

### Financial lists and tables

- Provide search, relevant filters, sortable columns and pagination for growing record lists. Show result counts, current page/range, page size and labelled previous/next controls; announce result changes without moving keyboard focus. Reset pagination when search, filters or sort change.
- Use server-side search, filtering, sorting and bounded pagination for large or unbounded datasets. Do not fetch every record to display a page. Keep operations consistent across the full result set, use stable ordering and label whether totals cover the page or all matching records. Client-side operations are suitable only for demonstrably small, bounded datasets.
- Keep filters and sort predictable within each entity and list. Preserve them on list/detail return; use URL state for non-sensitive, shareable criteria where appropriate. Do not put private search terms, financial data or credentials in URLs or persistent browser storage. Debounce remote search and ignore superseded responses.
- On small screens, prioritise identity, status, amount and next action; expose remaining fields through accessible details or column controls. Preserve meaningful comparisons in a contained table when cards would obscure them. Selection and bulk-action scope must be explicit across pages and reset when the context changes.
- Use text labels as well as colour for overdue items and exceptions. A confirmed zero is `0.00`; missing data is “Not recorded”, loading has a loading state, and failed/restricted data is “Unavailable” or an explicit permission message. Explain any dash used for “Not applicable”. Never substitute zero or plot failed data as zero.

## 4. Interaction and feedback

Use a simple flow: identify an issue, inspect context, take an authorised action and confirm the outcome. Reveal secondary detail progressively while keeping amounts, entity identity and next actions visible.

| State | Required experience |
|---|---|
| Initial loading | Stable skeletons that preserve layout. Explain long waits and provide recovery on failure. |
| Refreshing | Retain usable data with its last successful fetch time and a refresh indicator. Mark stale data clearly. |
| No records | Explain that there is nothing to show and provide a relevant next step where available. |
| No filter matches | Keep controls available, explain the result and offer “Clear filters”. |
| Validation error | Explain the correction beside the field, preserve values and guide focus appropriately after submission. |
| Request or connection failure | Persistent message identifying the affected entity/section, trustworthy remaining data and retry/reconnect action. Never display unavailable money as zero. |
| Action in progress | Show progress on the initiating control and prevent duplicate submissions. Keep its purpose readable. |
| Success | Confirm near the action. Keep important financial outcomes available after transient notifications disappear. |
| Insufficient permission | Explain the restriction without exposing protected records. Keep this distinct from empty data. |

Show connection status using text plus an icon or colour and the last successful fetch time. Distinguish the dashboard fetch time from the last successful Xero synchronisation; fetching cached API data does not imply a new Xero sync. Include a date for earlier days and identify unknown freshness explicitly. In aggregate views, identify per-entity issues and mark incomplete totals. A healthy connection for one entity must not imply every organisation is up to date.

Before consequential actions, show the entity, record and action clearly. Protect unsaved changes during navigation and confirm success only after confirmation from the service. Error messages should explain recovery in business language.

For destructive or financially consequential actions already permitted by requirements, provide a review/confirmation step showing affected records and amounts, consequences and a safe Cancel choice. Offer undo only when supported by the service. Preserve edits on failure, explain recovery and do not automatically retry an uncertain financial write; reconcile its server outcome before offering another attempt. Notifications must not be the only location of an actionable error. Keep success messages non-interrupting and critical errors persistent.

## 5. Responsive design

Retain the same terminology, metric definitions, priorities and authorised tasks across layouts.

- **Desktop:** Expanded sidebar, comparison-friendly financial tables and list/detail panes. Default sidebar width is approximately 240 px, with 32 px main padding. Group related information rather than stretching text across the window.
- **Tablet:** Collapse to an icon rail when needed, use 24 px main padding and switch to separate detail views before split panes become cramped. Support touch and keyboard equally.
- **Mobile:** One content column with 16 px side padding and entity context at the top. Use labelled bottom navigation: Overview, Invoices, Tasks and More. More exposes remaining authorised destinations and Settings. Convert attention rows into cards with explicit actions; keep urgent work near the top.

Choose layout transitions by content fit. Keep typography readable. Reflow ordinary content without page-wide horizontal scrolling; contain wide tables within labelled scroll regions, with a clear route to complete record detail.

Use shared breakpoint tokens, initially 48 rem for tablet and 75 rem for expanded desktop layouts; adjust against content fit rather than device names. Verify at 320, 390, 768, 1024 and 1440 CSS px, including intermediate widths, both orientations and long organisation names. Keep the mobile Overview label consistent with desktop; demo role/state controls in the wireframes are not production navigation.

Allow for safe areas, browser zoom and the software keyboard. Sticky navigation must not cover focused controls, messages, actions or final rows.

## 6. Roles and multiple organisations

Support Director, Financial Admin and Standard user roles according to the approved permission matrix. Apply it consistently to navigation, entities, records, summaries and actions. Do not infer permissions from role names.

Show the user's name and role in the account area. Display readable ownership names and explicit “Unassigned” or “Inactive” labels. Hide irrelevant actions; explain disabled actions when a prerequisite is useful to understand. If access changes, remove affected information and guide the user to an authorised context. Backend authorisation must enforce the same boundaries.

Services, Commercial and HotAS each map to a separate Xero organisation in the current client configuration. Load organisation identity, connection and permissions from authorised server data; do not encode these three names as application rules. Keep entity identity visible during financial review and action. Xero is the ledger and source of truth for invoices. Label data sources and freshness, and distinguish forecasts from ledger facts. Integration capabilities and financial actions follow approved project requirements.

Scope query keys, request parameters, drafts, selections and persisted preferences to the signed-in user and stable organisation ID; aggregate keys must include the authorised organisation set and query criteria. Cancel obsolete reads and reject late responses after context/access changes, including A → B → A switching. Never reuse another organisation's records as loading placeholders. Clear protected caches on sign-out or account change and purge revoked access immediately. Revalidate permissions after a server denial; backend authorisation on every request remains mandatory and frontend visibility provides no security boundary. Xero/OAuth integration tokens remain server-side and never enter browser state, logs or storage.

## 7. Accessibility

Meet [WCAG 2.2 AA](https://www.w3.org/TR/WCAG22/) throughout the application.

- Maintain at least 4.5:1 text contrast, 3:1 for qualifying large text and 3:1 for essential control boundaries and graphical information. Pair colour with text, shape or another visible cue.
- Support full keyboard operation, logical focus order, a skip-to-content link and a consistent, visible, unobscured focus indicator.
- Use accessible names, semantic headings, table headers and navigation landmarks. Associate field errors with inputs and announce important state changes without unnecessarily moving focus.
- Use pointer targets at least 24 × 24 CSS px, subject to WCAG exceptions; use 44 × 44 px targets for primary controls and touch navigation.
- Support 200% text resizing and reflow at 320 CSS px, with exceptions for genuinely two-dimensional content. Respect reduced motion.
- Make information available without hover, including exact amounts, icon explanations and chart values. Validate keyboard, screen-reader, zoom and touch behaviour in the implemented interface.
- Prefer native HTML controls and tables. Give tables captions or accessible names, scoped column/row headers and `aria-sort` on the sorted header. Use buttons within sortable headers; avoid ARIA grids unless their full keyboard interaction is needed and implemented.
- Follow accessible dialog, menu, combobox and tab patterns, including Escape, appropriate arrow-key behaviour and focus restoration. Tooltips must be dismissible, hoverable and persistent while needed. Provide alternatives to dragging and avoid repeated entry of information already supplied in the same process.
- Associate errors using `aria-describedby` and `aria-invalid`; on invalid submission focus an error summary or first invalid field. Announce routine updates through polite status regions and reserve alerts for urgent errors. Mark result regions busy during loading without repeatedly announcing skeleton elements.
- Allow paste, password-manager assistance and one-time-code autofill in the approved sign-in flow; do not require manual code transcription or cognitive puzzles without an accessible alternative. Explain expired codes and session recovery while retaining non-sensitive work where permitted.

## 8. Design consistency and review

Maintain shared tokens and reusable components for the application shell, entity selector, navigation, cards, financial rows, badges, controls and feedback states. Variants must share sizing, colour meaning and interaction behaviour.

Represent colours, typography, spacing, breakpoints, radii, shadows and focus styles as semantic CSS variables or equivalent shared tokens. Define default, hover, focus-visible, pressed, selected, disabled, busy and invalid variants centrally. Use a subtle shared card shadow and a stronger overlay shadow; avoid per-screen styling values. Record accessible component examples and usage alongside the shared components; a separate component catalogue tool is optional.

Use consistent destination names, statuses, action labels, financial formatting, entity colours and error placement. Sample data must agree across desktop, tablet and mobile.

## 9. React architecture and state

Use a supported stable React 19 or later release with matching React DOM, Vite and strict TypeScript. The repository currently declares React/React DOM `^19.3.1` and enables `strict` and `noUncheckedIndexedAccess`. Validate dependency compatibility and security before upgrades; this standard does not require a framework migration or automatic version upgrades. Preserve the React/Vite client, Spring Boot API and S3/CloudFront delivery architecture.

- Organise by business feature, with shared UI primitives, formatting and API utilities. Separate presentational components from feature hooks/domain calculations and API access. Compose small interfaces; avoid a generic framework or global state store before a clear need exists.
- Keep transient interaction state local, share organisation/session context only where needed, and give server data a single cache owner. Derive filtered values and totals rather than duplicating them in state. Keep authoritative financial calculations on the server.
- Use Effects to synchronise external systems, with cleanup and cancellation; use event handlers for actions and render-time derivation for computed values. Use stable record IDs as keys. Profile before adding memoisation; keep expensive computations and broad context updates away from keystroke interactions.
- Consume generated OpenAPI types through typed API adapters; do not hand-edit generated files. Validate untrusted data at boundaries where needed. Map API validation, permission and provider failures to the shared UI states without exposing raw errors or secrets. Client validation improves feedback; the server remains authoritative.
- Define per-resource freshness, refetch and bounded retry policies. Deduplicate reads, invalidate affected organisation records and summaries after confirmed mutations, and avoid retrying validation/permission failures. Disable duplicate submissions and use backend idempotency for external writes. Do not optimistically present financial actions as completed.
- Lazy-load routes and substantial optional features with stable fallbacks and recoverable error boundaries. Use client-compatible libraries; React Server Components and server-only APIs require a separately approved architecture change.

**Recommended, optional tools:** TanStack Query for shared server-state caching; TanStack Table for complex headless tables; React Router for routes and URL state; React Hook Form with Zod for complex forms; Tailwind CSS and shadcn/ui or other accessible primitives for consistent components. Choose only tools that solve demonstrated needs and support the approved stack. These are not currently declared dependencies and must not introduce a second styling system or duplicate existing state/form ownership. Accessible primitives still require application-level accessibility verification.

## 10. Performance and quality

Target **LCP ≤ 2.5 seconds, INP ≤ 200 milliseconds and CLS ≤ 0.1**, measured at the 75th percentile and assessed separately for mobile and desktop. Record representative device, network and dataset conditions. Use production-build lab checks before release and privacy-preserving real-user measurements when available; a Lighthouse score or lab run alone does not prove field compliance.

Reserve dimensions for skeletons, charts and deferred content. Keep the main content loading path small; load independent reads in parallel and avoid request waterfalls. Use bounded pages for large lists; introduce virtualisation only after measurement, preserving keyboard focus, table semantics and an accessible way to reach records. Keep chart processing and expensive calculations off the input path. Establish bundle/API budgets from measured representative journeys and investigate regressions before release.

Use the existing Vitest/React Testing Library and Playwright toolchain. Test observable behaviour: formatting and state transitions; loading/empty/error/retry states; keyboard and form interactions; permission denials; organisation switching with delayed/out-of-order responses; cache clearing on sign-out; and pagination/filter consistency. Use synthetic fixtures with long names, many organisations, large results and partial connection failures. Add automated accessibility checks and manual keyboard, screen-reader, contrast and zoom checks; automation alone is insufficient.

Support the current and previous stable major releases of Chrome, Edge, Firefox and Safari, including iOS Safari and Android Chrome. Verify core journeys in Playwright Chromium, Firefox and WebKit and perform representative touch/device checks. Record tested versions and known limitations; engine emulation alone does not prove real-device compatibility.

**Definition of Done for an interface change:** It follows these visual standards and approved business requirements; makes organisation context and permitted next actions clear; supplies applicable loading, empty, success, error and permission states; works at the specified viewport sizes and browser baseline; passes component, end-to-end and accessibility checks; verifies organisation/cache isolation and financial formatting; and records performance evidence or an explicit measurement gap. Run the relevant repository lint, typecheck, test, build and API-contract checks; report commands/results and any checks not run. Apply the project's independent review, CI and Test acceptance gates where relevant.

**Project references:** [AGENTS.md](../AGENTS.md) defines entity/security constraints; [README.md](../README.md) defines application context; [requirements guidance](requirements/README.md) defines precedence and approval. Approved task specifications govern business behaviour.

**Standards references:** [React stable versions](https://react.dev/versions), [Effects guidance](https://react.dev/learn/you-might-not-need-an-effect), [query-key dependencies](https://tanstack.com/query/latest/docs/framework/react/guides/query-keys), [server-side pagination](https://tanstack.com/table/v8/docs/guide/pagination), [WAI-ARIA interaction patterns](https://www.w3.org/WAI/ARIA/apg/patterns/), [accessible authentication](https://www.w3.org/WAI/WCAG22/Understanding/accessible-authentication-minimum.html) and [Core Web Vitals](https://web.dev/articles/vitals). Recheck official guidance when adopting or upgrading a dependency.
