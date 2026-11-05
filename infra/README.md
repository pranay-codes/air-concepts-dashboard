# Infrastructure

This folder holds infrastructure as code and deployment files. So far it holds the Parameter Store setup script for the Test environment. There are no CloudFormation templates yet.

Planned subfolders:
- `cloudformation/`: AWS stack templates
- `params/`: example parameter files with `REPLACE_ME` values. Real `*.local.json` files are never committed.
- `deploy/`: Docker Compose, Caddy and remote deploy files

Humans deploy infrastructure with their own AWS identity. Agents write files only and never run commands that contact AWS.

Source: the Infrastructure Architecture page dated 3 November 2026 (Notion project "Air Concepts - Financial Admin Dashboard").

## Parameter Store setup (Test)

`scripts/setup-test-parameters.py` asks you for each value and writes it to AWS Systems Manager Parameter Store under `/air-concepts/test/` in `ap-southeast-2`. It covers the backend API and the frontend. Parameter list, IAM and policy JSON: [docs/security-access.md](../docs/security-access.md).

How it works:

- Without `--apply` it lists every key, with its type and whether it is already set. It writes nothing and asks you nothing.
- With `--apply` it asks for each key that is not set. Type the value and press Enter. Secret values are hidden as you type. A blank answer skips that key.
- Existing keys are kept. `--overwrite` asks for them too and replaces them. In a dry run it shows them as `would replace`.
- Values are never printed or saved to a file.
- The script stops before anything is written if the caller is not in `--expected-account-id`, or if a path is outside `/air-concepts/test/` or names `live`.

The script does not check the values. A wrong value is written as typed. A required key left blank means the API may not start, so check the values before the next deploy.

Install the script dependencies once:

```bash
python -m pip install -r infra/scripts/requirements.txt
```

Run the script tests (mocked AWS, no network):

```bash
python -m pytest infra/scripts -q
```

Commands. Use your own named Test profile. Agents do not run the last two.

```bash
# 1. List every key and what is set. Writes nothing.
python infra/scripts/setup-test-parameters.py --profile <test-profile> --expected-account-id 923079246426

# 2. Enter values for keys that are not set yet. Blank skips a key.
python infra/scripts/setup-test-parameters.py --profile <test-profile> --expected-account-id 923079246426 --apply

# 3. Same, with a customer-managed KMS key for SecureString values
python infra/scripts/setup-test-parameters.py --profile <test-profile> --expected-account-id 923079246426 --apply --kms-key-id alias/<test-key>

# 4. Replace existing values as well (asks for every key)
python infra/scripts/setup-test-parameters.py --profile <test-profile> --expected-account-id 923079246426 --apply --overwrite
```

Exit codes: `0` success or dry run, `1` a write failed, `2` refused by a safety check. Nothing is written on exit `2`.
