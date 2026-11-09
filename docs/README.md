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
