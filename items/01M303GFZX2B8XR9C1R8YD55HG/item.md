---
schema_version: 1
id: 01M303GFZX2B8XR9C1R8YD55HG
key: GV-22
type: chore
title: Forgejo default merge message templates so kontor doctor's repo check passes
created_by: kevin
created_at: 2026-09-20T19:08:27Z
---

## Scope

`kontor doctor` reports `MERGE_TEMPLATE.md`, `REBASE_TEMPLATE.md` and `SQUASH_TEMPLATE.md` missing from `origin/development` (found at GV-4). Every sibling repo carries them under `.gitea/default_merge_message/` so Forgejo composes conforming merge subjects. Copy the sibling set with this repo's scope.

## Approach

Copy the three templates from `villager_voices` with the scope renamed; no other change.

## Acceptance criteria

- [x] The three templates exist under `.gitea/default_merge_message/` on `development` with scope `grounded_villages`.
- [x] `kontor doctor` passes the merge-template check; merged through a Forgejo pull request.

## Constraints and prior findings

Found by GV-4; the templates are Forgejo default merge messages, nothing runtime.
