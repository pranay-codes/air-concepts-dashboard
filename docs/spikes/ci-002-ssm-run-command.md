# Spike: Can SSM Run Command reach the Test host with the approved template alone?

## Question
- Can the deploy send commands to the Test EC2 host through SSM Run Command, with no SSH, using only the instance profile and the CloudFormation template already on `main`?

## Method
- Read `infra/cloudformation/infrastructure.json` on 2026-11-06: the instance role policy attachments and the bootstrap script.
- Web search for secondary sources on SSM agent and policy requirements. The AWS Systems Manager User Guide was not fetched, so the AWS-side facts below are not confirmed against the primary source.
- Nothing was run against AWS. Agents do not contact AWS (infra/README.md).

## Findings
- Confirmed in the template (not yet tested in AWS):
  - The instance role attaches `AmazonSSMManagedInstanceCore`.
  - The bootstrap enables `docker` and `amazon-ssm-agent`.
  - The bootstrap installs Docker Compose and verifies its checksum.
  - The instance profile is attached to the host.
- Confirmed by secondary sources only (not the AWS User Guide):
  - The SSM agent ships preinstalled on Amazon Linux 2023.
  - `AmazonSSMManagedInstanceCore` is the policy the sources give for Run Command.
  - The host needs outbound HTTPS to the SSM endpoints. The template's egress is broad, so this should hold.
- Not confirmed:
  - Whether Run Command needs any extra policy for its output, if output goes to S3.
  - Whether the deploy role needs `ssm:GetCommandInvocation` or similar. This is checked as part of the deploy role check (spec, open question 7).
  - The Run Command payload size limit. The plan must check the size of the Compose and Caddy files sent in the payload.

## Decision
- Partial.
- Allows: the approved design, SSM Run Command to the Test instance with no SSH.
- Blocks nothing yet, but the first Test run must confirm that `send-command` succeeds and that the host reports the new container healthy.

## Date
- 2026-11-06

## Evidence
- `infra/cloudformation/infrastructure.json` (InstanceRole policy attachments, bootstrap script), read 2026-11-06.
- Secondary sources from web search, 2026-11-06, not the AWS User Guide. The AWS User Guide page for Run Command should be read before the plan is approved.
