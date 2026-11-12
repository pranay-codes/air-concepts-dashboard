<!--
Release notes template for a Live release (INF-006). Copy into the GitHub release of the tag, for example v0.1.
Keep the Tag, Commit and Image lines exactly as shown, each on its own line: the Live release workflow reads them.
Copy Commit and Image from the deploy-test run Summary, not by hand. Delete this comment before publishing.
-->
Tag: v0.1
Commit: <full 40-character commit, from the deploy-test Summary>
Image: <image tag, from the deploy-test Summary>

## Summary

<One or two sentences: what this release puts on Live, in plain language.>

## Deployed

- <Change or capability, with its requirement ID, for example UC4-xx>
- <Change or capability>

## Database

- Migrations applied on Live: <latest migration file name, or "none">
- Rollback: restore the previous release with the deploy-live rollback mode. The database is not changed by a rollback.
