# Phase 0 — Process infrastructure

Status: complete. PR #2 open, fully reviewed, awaiting owner's merge approval.
Branch: `feat/phase-0-process-infrastructure`.

## Objective

Build the development process machinery BEFORE any product code: protected
branches, PR discipline, CI on every push, automated pre-merge checklist,
dependency and secret scanning. No Java, no React, no Kafka in this phase —
only the rails the product phases will run on.

## Design — what was built

1. **Branch protection on `main`** (live): no direct pushes, PR required,
   strict required status checks (`process-validation`, `security-scan`),
   conversation resolution required, admins enforced, no force pushes or
   deletions. Required approving reviews = 0 while the repo has a single
   collaborator (GitHub blocks self-approval); rises to 1 when a second human
   joins.
2. **`.github/pull_request_template.md`** — every PR states what changed, why,
   which design section it implements, QA criteria affected, and test evidence.
3. **`.github/workflows/ci.yml`** — path-filtered pipeline:
   - `detect changed areas` → backend / frontend / docs-only
   - `backend build and test` (Java 21, Maven or Gradle wrapper auto-detect;
     fails loudly if neither wrapper is committed)
   - `frontend build and test` (`npm ci` → build → tests → `npm audit
     --audit-level=high`)
   - `process-validation` (SHA-pinned actions, PR template present,
     `docs/UI-UX.md` section linked for feature PRs)
   - `security-scan` (gitleaks on full history)
   - `checklist` (always green; the human-readable gate is the bot comment)
4. **`.github/workflows/pre-merge-checklist.yml`** — bot posts the checklist
   once per PR and updates the same comment on later pushes.
5. **`.github/dependabot.yml`** — weekly updates for GitHub Actions
   (npm/Maven join when those ecosystems land).
6. **`docs/PROCESS.md`** — the written process: branch strategy, the PR loop,
   department/persona approvals, senior panel, Iron Man's final word, QA gates,
   toolchain decisions (Temurin 21, npm), and the solo-developer review reality.

## Exact process followed (step by step)

1. Created branch `feat/phase-0-process-infrastructure` from `main`.
2. Committed the workflow, template, Dependabot, and process files.
3. Opened PR #2 with the template filled in.
4. CI ran on first push — **red**: gitleaks needed `GITHUB_TOKEN`; the SHA-pin
   check flagged its own docs. Fixed both, re-pushed, CI green.
5. Posted the persona review loop as labeled PR comments. Hulk and Spider-Man
   asked toolchain questions (Temurin 21, npm) — answered and recorded as
   decisions in `docs/PROCESS.md`.
6. Discovered branch protection used check names CI never reports
   (`CI / process-validation`) — the PR could never have merged. Fixed the
   required contexts; PR became mergeable.
7. Owner challenged review rigor → critical pass found: checklist bot posting
   duplicate comments (fixed to update in place; stale copies removed),
   "merge triggers deployment" over-promising (reworded — deploy wiring lands
   in Phase 1), Maven-vs-Gradle recorded as an open Phase 1 decision.
8. All nine personas approved (Daredevil, Hulk, Spider-Man, Doctor Strange,
   Captain America, Mister Fantastic, Nick Fury, Professor X, Iron Man final
   word). CI green on the final push. No unresolved threads.
9. Merge: pending the owner's explicit approval (this step).

## Decisions recorded

- Eclipse Temurin 21 everywhere (CI, local, Render).
- npm + committed `package-lock.json`; pnpm/yarn need their own justifying PR.
- Persona approvals are labeled PR comments; the owner gives the final merge
  approval until a second human collaborator joins.
- Deploy-on-merge activates with Phase 1's Render wiring.
- Open for Phase 1: Maven vs Gradle.

## Outcome

`main` is protected, every future change rides a reviewed PR with green CI,
and the process has already caught and fixed real defects before any product
code exists.
