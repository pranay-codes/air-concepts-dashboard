# Environment configuration (INF-006)

One file per environment, `test.json` and `live.json`, with the same keys. They hold **non-secret** values only:
resource identifiers, hostnames, sizing and feature switches. Secrets stay in Parameter Store
(`/air-concepts/<env>/`) and GitHub secrets.

Workflows, composite actions and scripts read these files through `infra/scripts/environment_config.py`.
They contain no `if live` logic: to change an environment, edit its file; to add one, copy a file.

| Key | Meaning |
|---|---|
| `environment` | `test` or `live`; must match the file name |
| `awsRegion`, `awsAccountId` | where the stack lives (Sydney; one account for both today) |
| `stackName` | CloudFormation stack, `air-concepts-<env>-infrastructure` |
| `deployRoleArn` | the GitHub OIDC role the deploy assumes |
| `instanceId`, `apiHostname` | API host and its public name |
| `frontendUrl`, `frontendBucket`, `cloudFrontDistributionId` | static frontend |
| `ecrRepositoryUri` | this environment's backend image repository |
| `ssmPrefix` | `/air-concepts/<env>/` |
| `supabaseProjectRef` | the one Supabase project this environment may use (empty only for Test, meaning "not pinned") |
| `apiMemoryLimit` | container memory limit, for example `512m` or `1g`; size it from the host, not from the other environment |
| `allowTestOnlyFeatures` | lets Test-only switches (Xero request diagnostics) through; must be `false` for Live |

`REPLACE_ME_*` values mean "not provisioned yet". CI accepts them in `live.json` only; a Live deploy refuses
them. Fill them from the stack outputs:

```
python infra/scripts/environment_config.py from-stack live --profile <aws-profile>   # prints the values
python infra/scripts/environment_config.py from-stack live --profile <aws-profile> --check
```

Checks: `python infra/scripts/environment_config.py validate` (also runs in CI). See `docs/environments.md`.
