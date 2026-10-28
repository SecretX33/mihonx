---
name: sync-mihonx-fork
description: Sync MihonX with an official Mihon release while preserving fork behavior and original upstream commit hashes. Use when planning or performing an upstream release update for this fork.
---

# Sync MihonX Fork

Roll the fork forward to an official release from `mihonapp/mihon`. Keep the process lean and preserve upstream history. Follow the requested scope: planning a sync does not authorize performing it or opening a pull request.

## Establish the baseline

Read the repository's `AGENTS.md` and `CHANGELOG_MIHONX.md`. Refresh the fork's remote default branch, preferring `origin/main`, then `origin/master`, then `origin/HEAD`. Use that branch as the starting point unless the user specifies otherwise; keep unrelated feature work separate.

Use the upstream release number and full commit hash recorded in the fork changelog as the previous baseline. Verify the release-to-commit mapping and its ancestry in the fork. Investigate missing history or a previous squash before treating an ancestry failure as an incorrect baseline; do not silently substitute a different baseline.

Resolve the requested official upstream release, or the latest stable release when unspecified, to its exact commit. Fetch upstream tags into a separate namespace such as `refs/upstream-tags/*` to avoid collisions with MihonX release tags. Integrate the pinned release commit. Exclude unreleased upstream branch commits and changes from other forks. Leave the disabled weekly sync workflow outside this task's scope.

## Capture and integrate

Record the starting fork commit and save the complete diff from the previous upstream baseline to that commit outside the tracked working tree. Include binary changes and full object IDs when saving the patch. This snapshot captures the fork's accumulated customizations regardless of intervening commits or merges.

Use direct comparisons between commit contents, equivalent to an IDE's branch comparison:

| Comparison | Purpose |
|---|---|
| Previous upstream baseline to starting fork commit | Identify existing fork customizations |
| Previous upstream baseline to target upstream release | Understand upstream changes |
| Target upstream release to completed update branch | Review the resulting fork customizations |

Use `git diff --find-renames OLD_COMMIT NEW_COMMIT` for these comparisons. Inspect the full diff and surrounding code, including additions, deletions, and binary assets. Commit logs and the changelog provide context; the file comparisons establish the actual changes.

Create a branch such as `chore/sync-mihon-v<VERSION>` from the starting fork commit and merge the pinned upstream release into it. Preserve original upstream commits without squashing, cherry-picking, or rebasing them. If the default branch advances during review, merge it into the update branch and revalidate.

Resolve conflicts according to behavior and upstream's current structure. Use the saved snapshot as a reference for adapting customizations. Account for each existing fork feature, including code that merged cleanly. Remove redundant fork implementations when upstream provides equivalent behavior.

## Validate and finish

Review the final delta against the target release. Preserve MihonX identity, release and update behavior, telemetry choices, and bidirectional backup compatibility. Keep database migration numbering aligned with upstream and avoid fork-only migrations when a compatible solution is practical.

Build and run relevant existing checks, focusing on affected fork behavior, database upgrades, and backup compatibility. Report any validation that could not be completed. Update the fork changelog baseline to the new upstream release and full commit hash, keeping its entries concise and fork-specific.

When writing a sync pull request description, invoke `$writing-pr-descriptions` and follow the repository template. Include the previous and target upstream release numbers and commit hashes, meaningful adaptations, and validation. Require GitHub's **Create a merge commit** option; squash merging and rebase merging do not preserve the imported upstream history as required here.

End the pull request description with this exact line, after all other content:

**Required merge method:** `MERGE COMMIT` (preserve upstream commit hashes)
