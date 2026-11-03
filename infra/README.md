# Infrastructure

This folder holds infrastructure as code and deployment files. It is a placeholder: there are no templates, scripts or parameters yet.

Planned subfolders:
- `cloudformation/`: AWS stack templates
- `params/`: example parameter files with `REPLACE_ME` values. Real `*.local.json` files are never committed.
- `deploy/`: Docker Compose, Caddy and remote deploy files

Humans deploy infrastructure with their own AWS identity. Agents write files only and never run commands that contact AWS.

Source: the Infrastructure Architecture page dated 3 November 2026 (Notion project "Air Concepts - Financial Admin Dashboard").
