## Linked requirement IDs
- FR-...
- Spec pack: docs/specs/...

## Test evidence
- Command:
- Result:
- Not run, and why:

## Security checklist
See AGENTS.md §7 and §11.
- [ ] Entity isolation checked
- [ ] Backend role and entity checks present
- [ ] Tokens server-side and encrypted; none in browser, logs, git or prompts
- [ ] Passcode limits unchanged or tested
- [ ] External writes idempotent on retry
- [ ] No test weakened or removed
- [ ] No secrets or production values

## Migration checklist
Only if migrations changed. Otherwise delete this section. See AGENTS.md §8.
- [ ] New migration files only; existing ones untouched
- [ ] Expand and contract followed
- [ ] RLS enabled on new tables, explicit grants set
- [ ] Lock timeouts on risky statements
- [ ] Every DROP, TRUNCATE and unscoped DELETE listed for human approval:
- [ ] Compensating migration approach noted

## Reviewing agent
- Written by:
- Reviewed by:

## Rough usage consumed for the slice
-
