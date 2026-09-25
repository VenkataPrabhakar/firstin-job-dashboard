# PROCESS.md — development workflow (binding)

Phase 0 process infrastructure. Product code may not land on `main` until this
process is live. Approved by the full review panel; see the Phase 0 PR.

## 1. Branches

- `main` is protected. No direct pushes — implementation or otherwise.
- Work happens on `feat/...`, `fix/...`, or `docs/...` branches.
- Every change lands via pull request using `.github/pull_request_template.md`.

## 2. Branch protection on `main` (applied via API)

- Require pull request before merging; dismiss stale reviews on new pushes.
- Required status checks (strict): `process-validation`, `security-scan`
  (the check-run names as reported by CI; backend/frontend jobs join the
  required set when those directories land).
- Require conversation resolution before merging (no unresolved threads).
- Required approving review count: **0 while the repo has a single collaborator**
  (GitHub blocks self-approval, so any nonzero value would make merging
  impossible). The department/persona approvals below are recorded as labeled
  PR comments, and the repository owner gives the explicit final merge approval
  (in chat). When a second human collaborator joins, raise this to 1 and make
  formal GitHub approvals mandatory.
- Enforce for administrators. No force pushes, no deletions.

## 3. The PR loop

1. Author opens the PR from a feature branch with the template filled in.
2. CI runs on the PR and on every new push to the branch. Red blocks merging.
3. The pre-merge checklist bot posts the checklist comment.
4. Reviewers raise questions and request changes.
5. Author updates code, re-pushes, CI re-runs, reviewers re-check.
6. Repeat until reviewers are satisfied: no unresolved threads, no active
   "request changes" reviews.
7. Team lead verifies every checklist box, including all department approvals.
8. Squash merge only. Merge to `main` triggers deployment.

## 4. Required approvals

Every PR needs all five departments. The personas are named review roles, not
separate GitHub accounts: each records findings and approval as a labeled PR
comment (GitHub does not allow the PR author to approve their own PR, and
there are no other collaborators yet). The team lead verifies the full set
before merge.

- Backend — Hulk
- Frontend — Spider-Man
- QA — Daredevil
- UX — Doctor Strange
- Team lead — Captain America

Architecture-impacting PRs additionally need the senior panel:
- Solution Architect — Mister Fantastic
- Engineering Manager — Nick Fury
- Senior Director — Professor X

Final word on every PR: VP — Iron Man.

## 5. CI gates (`.github/workflows/ci.yml`)

- `process-validation` (always runs): design docs exist, PR template has the
  required sections, every workflow action is pinned by 40-char commit SHA.
- `security-scan` (always runs): gitleaks secret scanning over full history.
- `backend` (runs when `backend/**` changes): Temurin JDK 21, build + tests;
  fails loudly if `backend/` exists without a build wrapper.
- `frontend` (runs when `frontend/**` changes): Node 22, `npm ci`, build,
  tests; lockfile required; `npm audit --audit-level=high` blocks on
  high/critical CVEs.
- OWASP Dependency-Check for the backend is wired in the Phase 1 backend PR
  alongside the Maven build; the policy (high/critical CVEs block merging) is
  already binding.
- Dependabot (weekly) keeps SHA-pinned Actions fresh; npm/maven entries are
  added by the Phase 1/2 PRs that introduce those directories.

## 6. Toolchain decisions (recorded)

- JDK: Eclipse Temurin 21 everywhere — CI, local dev, and Render.
- Node: 22 LTS for the frontend build.
- Package manager: npm with a committed `package-lock.json` (revisit only if a
  later phase justifies pnpm/yarn; that decision would be its own PR).
- Workflow concurrency: cancel-in-progress per ref, so stale runs never gate a PR.

## 7. Costs

This process costs $0/month: GitHub public repo (unlimited Actions minutes),
gitleaks and Dependabot are free, no external services involved.
