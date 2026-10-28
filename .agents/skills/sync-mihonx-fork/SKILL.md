---
name: sync-mihonx-fork
description: Sync MihonX with an official Mihon release while preserving fork behavior and original upstream commit hashes. Use when planning or performing an upstream release update for this fork.
---

# Sync MihonX Fork

Sync to an official `mihonapp/mihon` release while preserving upstream history. Keep the process lean. A bare invocation syncs the latest stable release on a dedicated branch. User instructions override the target, starting point, or scope; planning-only requests remain planning-only.

## Establish the baseline

Read `AGENTS.md` and `CHANGELOG_MIHONX.md`. Refresh the remote default branch, preferring `origin/main`, then `origin/master`, then `origin/HEAD`. Start there unless instructed otherwise; keep unrelated feature work separate.

Use the changelog's upstream release and full commit hash as the previous baseline. Verify the release-to-commit mapping and fork ancestry. Investigate missing history or a prior squash before declaring the baseline incorrect; never silently substitute another.

Resolve the target release to its exact commit. Fetch upstream tags into a separate namespace such as `refs/upstream-tags/*` to avoid MihonX tag collisions. Exclude unreleased upstream branch commits and other forks' changes. Leave the disabled weekly sync workflow outside scope.

Before creating a branch or changing files, check the current and starting branches for a completed sync to the target or newer release. Use the recorded baseline and commit ancestry, not equality with fork `HEAD`, since fork commits remain on top. If either branch is up to date, report that and stop without creating a branch, commit, or pull request. Resume unfinished matching sync work instead.

## Capture and integrate

Record the starting fork commit and save its complete diff from the previous upstream baseline outside the tracked working tree, including binary changes and full object IDs. This captures accumulated customizations across intervening commits and merges.

Compare commit contents directly with `git diff --find-renames OLD_COMMIT NEW_COMMIT`:

| Comparison | Purpose |
|---|---|
| Previous upstream baseline to starting fork commit | Identify existing fork customizations |
| Previous upstream baseline to target upstream release | Understand upstream changes |
| Target upstream release to completed update branch | Review the resulting fork customizations |

Inspect full diffs and surrounding code, including additions, deletions, and binary assets. Logs and the changelog provide context; file comparisons establish the actual changes.

Use `sync/mihon-v<RELEASE>` unless instructed otherwise. Reuse the matching current branch or resume a matching local or remote branch after verifying its target release, preserving in-progress work. Create it from the starting fork commit only if none exists. Merge the pinned release unless already an ancestor; never squash, cherry-pick, or rebase upstream commits. If the default branch advances during review, merge it into the sync branch and repeat validation.

Resolve conflicts by behavior and upstream's current structure, using the saved snapshot to adapt customizations. Account for every fork feature, including cleanly merged code; remove redundant implementations when upstream provides equivalent behavior.

## Handle database migrations

Include schema definitions, `data/src/main/sqldelight/tachiyomi/migrations/`, and migration code in the comparisons. Preserve fork fields and storage behavior. Minimize upstream code differences for current and future syncs: accept simple query adaptations, but retain straightforward schema additions over complex runtime workarounds.

Keep upstream migration numbers where possible. For overlaps, preserve the fork's lineage and consolidate pending upstream changes in dependency order above its highest migration number. Add a new migration so installed users execute them; editing already-executed files is insufficient. Combine overlaps with upstream changes at the first available number, retaining later numbers. With fork migrations 11 and 12, upstream 11 alone becomes 13; upstream 11 through 15 become combined 13 containing upstream 11 through 13, followed by 14 and 15.

When upstream rebuilds a table containing fork fields, adapt the pending migration or rebuild code to retain those columns and copy their values before dropping the old table. Keep `INSERT ... SELECT` mappings and current schema definitions consistent. Defaults cannot recover discarded values; assess recovery separately if a destructive migration already shipped.

## Validate and finish

Review the final delta against the target release. Preserve MihonX identity, release and update behavior, and telemetry choices; keep database divergence minimal.

Build and run relevant existing checks for affected fork behavior and bidirectional backup compatibility. Validate fresh installations and upgrades from the fork's highest schema version and affected earlier supported versions: matching final schemas and preserved non-default custom values, row identities, and relationships. Report checks that could not be completed.

Update the fork changelog baseline to the target release and full commit hash, keeping entries concise and fork-specific. In sync plans and pull requests, report previous and target release numbers and hashes, meaningful adaptations (including migration-number mappings and field preservation), and validation results or limitations.

Title the sync pull request `Merge upstream v<RELEASE>`. For descriptions, invoke `$writing-pr-descriptions` and follow the repository template. Require GitHub's **Create a merge commit** option to preserve upstream hashes; squash and rebase merging are unsuitable.

End the pull request description with this exact line, after all other content:

**Required merge method:** `MERGE COMMIT` (preserve upstream commit hashes)
