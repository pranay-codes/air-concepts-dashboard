# Spike: Is there an official eclipse-temurin 25 image with linux/arm64 that can be pinned by digest?

## Question
- Which official `eclipse-temurin` Java 25 tag, with a `linux/arm64` manifest, should the backend image use, and what digest should it be pinned to?

## Method
- Docker Hub public API read on 2026-11-06: https://hub.docker.com/v2/repositories/library/eclipse-temurin/tags/25-jre-noble . It returns the tag's index digest and each platform's digest.
- Earlier web search of listings is superseded by the API read.
- Docker was not run and no image was pulled. Docker is not run locally (infra/README.md). The build job re-checks arm64 in GitHub Actions before it pushes.

## Findings
- Confirmed by listing snippets only (not by direct manifest inspection):
  - Official tags for Java 25 with a `linux/arm64/v8` build include `25-jre-jammy`, `25-noble`, `25-jdk-noble` and `25-jdk-jammy`.
  - Point-release tags such as `25.0.2_10-jre-jammy` also list arm64 builds.
- Not confirmed:
  - Resolved: `25-jre-noble` exists, with an arm64/v8 entry (Docker Hub API, 2026-11-06).
  - Any digest. Digests from search snippets are stale or unverified and must not be used.
  - Unofficial mirrors were ignored on purpose.

## Decision
- Confirmed for the index digest `sha256:a98128a57de944a72bd31fbc79b898edd5a5864d3652071d5bbd2160ab7d7dce` (`eclipse-temurin:25-jre-noble`). The API lists an `arm64 v8` entry under it. The tag was last updated 2026-11-01.
- Used in `backend/Dockerfile`. The build job runs `docker buildx imagetools inspect` on it before pushing.
- Not used: unofficial mirrors.

## Date
- 2026-11-06

## Evidence
- Docker Hub search snippets for `eclipse-temurin` tags, 2026-11-06. Crawl dates vary.
- https://hub.docker.com/_/eclipse-temurin/tags (listed; not read directly)
