# Quote sync completion plan

**Goal:** A normal short Xero quote page must not fail a sync.

**Approach:** Request successive pages for each quote status until Xero returns an empty page, following Xero's general paging guidance. Preserve all entity, duplicate, invoice, operation and page limit checks. Xero Quotes-specific termination and live account behaviour remain unverified; do not describe them as tested.

**Files:** `DocumentedQuoteSource.java`, `QuoteSyncTest.java`, and the Xero quote spike note.

**Tests:** Verify short and full nonempty pages continue, an empty page completes, records are processed, and the existing safety checks still pass. Run focused backend tests. No hosted system access.

**Done:** Quote sync no longer fails solely because a page has fewer than 100 records; actual failures still surface; tests and verification limits are reported.
