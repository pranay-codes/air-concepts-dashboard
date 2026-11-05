# INF-002 Test stack: read-only AWS checks

Date: 2026-11-06
Stack: air-concepts-test-infrastructure (ap-southeast-2)
Profile: air-concepts (IAM user air-concepts, account 923079246426, matches ExpectedAccountId)
Scope: read-only checks. No AWS resources were changed, nothing was deployed, Live was not accessed, and no secrets were read.

## Stack status and outputs

Status: CREATE_COMPLETE (last updated 2026-11-05 08:36 UTC)
Tags: environment=test, client=air-concepts
Termination protection: off. Drift: NOT_CHECKED.

| Output | Value |
|---|---|
| ApiElasticIp | 54.79.168.175 |
| ApiInstanceId | i-01b897b9899cecadc (t4g.micro, running) |
| ApiSecurityGroupId | sg-0749f4b34f1540fa3 (ingress 80 and 443 from 0.0.0.0/0) |
| ApiInstanceRoleArn | arn:aws:iam::923079246426:role/air-concepts-test-infrastructure-AirConceptsApiRole-jDqUWHpkuU1h |
| CloudFrontDistributionId | E18MS4R04I9HIA |
| FrontendUrl | https://d95r2yxgiabf4.cloudfront.net |
| CloudFrontDomain | d95r2yxgiabf4.cloudfront.net |
| FrontendBucketName | air-concepts-test-infrast-airconceptsfrontendbucke-8spryertwbuf |
| ApiUrl | https://air-concepts-api-test.insyte-tech.com |
| EcrRepositoryUri | 923079246426.dkr.ecr.ap-southeast-2.amazonaws.com/air-concepts-test-backend |
| BackendParameterPath | /air-concepts/test/backend/ |
| Environment | test |
| StackName | air-concepts-test-infrastructure |

## Checks

| # | Check | Result | Evidence | Remaining action |
|---|---|---|---|---|
| 1 | CloudFront deployment status | PASS | Distribution E18MS4R04I9HIA: Status Deployed, Enabled true. Origin is the private S3 bucket. DefaultRootObject is index.html. | None. |
| 2 | Frontend HTTPS response | FAIL (unpublished frontend, not a configuration failure) | https://d95r2yxgiabf4.cloudfront.net/ returns 403 with `<Code>AccessDenied</Code>` from S3 via CloudFront. The bucket holds 0 objects. The origin, OAC and default root are configured correctly, and a missing index.html returns AccessDenied under this setup. | Publish the frontend build through the approved CI release path (CI-002). Nothing was uploaded. |
| 3 | API hostname DNS | PASS | air-concepts-api-test.insyte-tech.com resolves to 54.79.168.175, which equals ApiElasticIp. Checked with the local resolver only. ApiHostedZoneId is empty, so the record is managed outside this stack. | Authoritative lookup not done (needs the DNS provider's zone). |
| 4 | API HTTPS | FAIL | Connection refused on 443 and on 80, not a timeout. The host is reachable, but nothing is listening. `ss -ltn` shows only port 22 and an internal 127.0.0.1 port. `nginx` and `caddy` are both inactive. | Release the API and its TLS reverse proxy through the approved release path, then re-check. |
| 5 | SSM connectivity | PASS | PingStatus Online. Agent 3.3.5700.0. Amazon Linux 2023. Last ping 2026-11-06 09:30 +13:00. | None. |
| 6 | Docker service | PASS | `systemctl is-active docker` returned `active`. Docker Engine 25.0.14. | None. |
| 7 | Compose version | PASS | Docker Compose v2.40.3, matching the pinned ComposeVersion parameter. | None. |
| 8 | Running containers | FAIL | `docker ps -a` returned no containers, not even stopped ones. The API has not been deployed to this host. | Deploy the backend image and Compose stack through the approved release path. |

## Notes
- Checks 5 to 8 ran through SSM Run Command (AWS-RunShellScript), which creates command history on the instance. Two commands were sent, both read-only: systemctl, docker --version, docker compose version, docker ps, ss, and systemctl is-active for nginx and caddy. No `.env` files or parameter values were read.
- The second SSM command reported Status Failed only because systemctl exits with status 3 for inactive units. Its output is valid.
- Not verified: the API health endpoint (port 443 refused); the authoritative DNS record; whether a reverse proxy or app server was expected to exist before the release step.
- Profile note: the `default` profile returns an invalid token; the `air-concepts` profile was used.
