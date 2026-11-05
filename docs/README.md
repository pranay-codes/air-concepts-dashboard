# Project documentation

## INF-002 infrastructure

The [infrastructure guide](../infra/README.md#shared-cloudformation-blueprint)
documents the shared Test/Live CloudFormation template, configurable parameters,
local validation, human-reviewed change sets, cost considerations and recovery.
The [implementation plan](plans/INF-002.md) records scope and verification limits.

Provisioned stack outputs, DNS/HTTPS acceptance and instance-capacity evidence
have not been collected. Record them only after human Test provisioning and the
CI-002 application release. Live infrastructure and application release require
separate human approval and the documented production-readiness gates.
