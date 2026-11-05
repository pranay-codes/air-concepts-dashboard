# Spike: Does the official caddy image publish linux/arm64, and what digest should CI-002 pin?

## Question
- Does the official `caddy` image publish a `linux/arm64` manifest for the tag the Compose file will use, and what digest should be pinned?

## Method
- Docker Hub public API read on 2026-11-06: https://hub.docker.com/v2/repositories/library/caddy/tags/2/ . It returns the index digest and each platform's digest.
- WebFetch of https://github.com/caddyserver/caddy-docker on 2026-11-06 (no platform list; superseded by the API read).
- Web search on 2026-11-06. Results were third-party images only.
- The official Docker Hub page (hub.docker.com/_/caddy) was not read.
- No image was pulled and no digest was checked.

## Findings
- Not confirmed: the caddy-docker README does not list platforms, tag names or digests. It points to the Docker Hub page for documentation.
- Not confirmed: the search returned only third-party images, so they say nothing about the official image.
- Not confirmed: the arm64 manifest on the official image. Background knowledge suggests the official image is multi-arch, but this spike does not rely on that.

## Decision
- Confirmed for the index digest `sha256:dee6bce21b6c3d17c78805046901f9558f0bcfe9d3853f26ab7eb5cf06021ba4` (`caddy:2`). The Docker Hub API lists an `arm64 v8` entry under it.
- Used as `CADDY_IMAGE_DIGEST` in `.github/workflows/deploy-test.yml`. The build job runs `docker buildx imagetools inspect` on it before pushing.
- Caveat: this spike read public metadata only. The Test run confirms that Caddy starts on the host.

## Date
- 2026-11-06

## Evidence
- https://github.com/caddyserver/caddy-docker (read 2026-11-06; no platform list)
- https://hub.docker.com/_/caddy (not read)
