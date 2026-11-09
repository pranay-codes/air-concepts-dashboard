# Spike: UC5 provider query, grant and OTP contracts

Date: 6 November 2026. Decision: partial, documentation verified; demo/runtime verification remains open.

## Method and evidence

Read official [Xero invoices API](https://developer.xero.com/documentation/api/accounting/invoices), [request conventions](https://developer.xero.com/documentation/api/accounting/requests-and-responses/), [tenants](https://developer.xero.com/documentation/guides/oauth2/tenants/), [token lifecycle](https://developer.xero.com/documentation/guides/oauth2/token-types/) and [OAuth overview](https://developer.xero.com/documentation/guides/oauth2/overview). No authorised demo API calls were made and no credentials were inspected.

## Findings and implementation consequences

- Invoice list supports `where`, `order`, page and pageSize. Apply search/date/status/overdue before paging. Default provider order differs from UC5; explicitly request invoice date descending with invoice ID tie-breaker. The optimized sort list is limited; due date/amount/status/contact require demo verification and explicit recovery if the provider rejects a large query.
- `SearchTerm` covers invoice number and reference, omitting contact name. The candidate adapter uses a grouped `Contains` expression across all three required fields instead. Escaping, unicode/case behaviour and compound-filter performance require a real demo contract test. Local query tests establish expression construction only.
- List response can omit detail information; retrieve details by immutable invoice ID. Do not assume `summaryOnly` includes payments or attachment flags. Validate page metadata and document type; never label an incomplete response a full successful result.
- Grant tokens can authorize multiple tenants. Discover `/connections`; validate chosen tenant against the fresh discovery result. Entity mapping and encrypted OAuth grant are separate records; serialize refresh per grant. Preserve the rotated refresh token. No fixed three-tenant limit belongs in the app; the app's actual provider tier/limits remain an operational constraint.
- Read scopes, attachment response content types/limits, exact 429 policy and grant rotation recovery remain adapter acceptance items. Broad legacy transaction scopes must not be selected from memory; verify current granular scope requirements against the registered demo application before consent.

## Supabase OTP

Official [passwordless email guide](https://supabase.com/docs/guides/auth/auth-email-passwordless), [CLI configuration](https://supabase.com/docs/guides/local-development/cli/config) and [OTP failure guidance](https://supabase.com/kb/troubleshooting/otp-verification-failures-token-has-expired-or-otp_expired-errors) were checked. Use signInWithOtp with `shouldCreateUser: false`, verifyOtp with type email, and email template `{{ .Token }}`. Configure six digits, 600-second expiration and request/verification limits. Hosted Auth settings must be separately verified; editing local config alone cannot establish hosted compliance. Active staff registration is also checked by the backend for data access.

Supabase Auth's official [administration source](https://github.com/supabase/auth/blob/master/internal/api/admin.go) confirms custom UUID creation and ban/unban duration semantics; the adapter reserves an ID before creation and reconciles by that ID. The official [Auth hooks guide](https://supabase.com/docs/guides/auth/auth-hooks) and [Custom Access Token hook](https://supabase.com/docs/guides/auth/auth-hooks/custom-access-token-hook) document pre-issuance checks and structured denial. The [verification implementation](https://github.com/supabase/auth/blob/master/internal/api/verify.go) uses the OTP authentication method for code verification. These were read on 6 November; adapter mock tests pass, while hosted runtime verification remains open.

## Remaining validation

Line amount tax basis checked 7 November against Xero's [types and codes](https://developer.xero.com/documentation/api/accounting/types/) and [tax guidance](https://developer.xero.com/documentation/best-practices/data-integrity/taxes/): Exclusive, Inclusive and NoTax describe recorded line values. SubTotal excludes tax; do not recalculate inclusive line amounts to imitate Xero's approved-invoice screen, which can display them differently. Detail responses retain the recorded basis; missing basis remains unrecorded rather than guessing.

The OAuth connection stage now implements documented token lifetimes, previous-refresh-token recovery and Retry-After parsing against mock HTTP. It uses granular invoice/attachment read scopes as documented by Xero's [granular-scope guidance](https://developer.xero.com/faq/granular-scopes), [official attachment SDK reference](https://xeroapi.github.io/Xero-Java/v4/accounting/index.html) and [refresh FAQ](https://developer.xero.com/faq/oauth2). Actual registered-app scope/connection eligibility remains unverified. Parameter reads are batched according to the [AWS maximum of ten names](https://docs.aws.amazon.com/systems-manager/latest/APIReference/API_GetParameters.html). See `docs/uc5-xero-operations.md` for recovery and proof boundaries.

Demo-only OAuth consent and refresh rotation, invoice and bill pagination/filter/sort/search combinations, missing attachments, permission revocation and rate limits. No capability is labelled production-verified by this note. The implementation must continue with mock transport tests and preserve explicit runtime evidence gaps.
# Four-decimal unit-price retrieval — 8 November 2026

The official [Invoices API](https://developer.xero.com/documentation/api/accounting/invoices) and [Xero Java SDK reference](https://xeroapi.github.io/Xero-Java/v4/accounting/index.html) document `unitdp=4` as the opt-in for four-decimal unit amounts. Invoice/bill detail GET requests must include it; otherwise precision is lost upstream before decimal-string mapping. The transport regression checks the parameter and preservation of `50.1234`. This is a read-only query parameter; tenant headers, authorisation and token boundaries are unchanged. Actual demo data comparison remains outstanding.

