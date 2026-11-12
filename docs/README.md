# Project documentation

## Configuration

[configuration.md](configuration.md) lists every environment variable and Parameter Store parameter, with where each is read, whether it is required and its format. Keep it in step with `.env.EXAMPLE`, the README and `security-access.md`.

## INF-002 infrastructure

The [infrastructure guide](../infra/README.md#shared-cloudformation-blueprint)
documents the shared Test/Live CloudFormation template, configurable parameters,
local validation, human-reviewed change sets, cost considerations and recovery.
The [implementation plan](plans/INF-002.md) records scope and verification limits.

Provisioned stack outputs, DNS/HTTPS acceptance and instance-capacity evidence
have not been collected. Record them only after human Test provisioning and the
CI-002 application release. Live infrastructure and application release require
separate human approval and the documented production-readiness gates.

## INF-006 Live environment

- [environments.md](environments.md): what differs between Test and Live, and where each difference lives.
- [live-environment-runbook.md](live-environment-runbook.md): step-by-step setup of Supabase production, AWS, DNS, GitHub, the first release, backups, verification and rollback.
- [ADR-004](adr/ADR-004-live-release-workflow.md): the release workflow and configuration decisions.
- [Spec pack](specs/INF-006-live-environment.md) and [plan](plans/INF-006.md): acceptance criteria, test mapping and the implementation ledger.
- [infra/environments/README.md](../infra/environments/README.md): the per-environment configuration files.
