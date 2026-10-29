# MihonX changelog

This changelog records changes made by MihonX relative to its Mihon upstream. For upstream features, fixes, and dependency updates, see the upstream [`CHANGELOG.md`](CHANGELOG.md).

## Unreleased

Baseline: Mihon `v0.21.3` (`feadad5e1e2bf638be6e2ca317d11657b09d4596`).

Backups are portable in both directions. Mihon `v0.21.3` backups restore in MihonX, and MihonX backups restore in Mihon with common data intact. Mihon ignores fork-only custom series information, hidden chapter state, and scanlator filler rules, so those fields are not retained if a backup is restored and re-exported by Mihon. The applications keep separate live databases, which are not intended to be copied between installations.

### User-facing changes

- Explicitly save, restore, update, or clear default filters for each source. Apply saved defaults when reopening a source, keep temporary filter changes separate, and include defaults in backups that contain app settings. Popular and Latest remain available without saved filters.
- Choose another source entry for a library title's cover from the cover viewer toolbar. Keep a persistent cover link, fall back to existing cover behavior when unavailable, and include links in backups that contain app settings. Removing an entry from the library clears its cover link. Cache alternative-source covers across app restarts and refresh them with the original entry's normal cover updates, so scrolling reuses cached covers.
- Use a compact selector to choose whether migration keeps the current source cover, cover link, or custom cover, or switches to the destination source cover. Default to keeping an existing cover link and otherwise use the destination cover. Migrating to the linked cover entry uses its cover directly without a redundant link. Keep this choice separate from custom text metadata.
- Skip configurable beginning and ending filler pages for each scanlator in a manga entry while reading. Show visible page numbers in the reader and chapter progress, mark skipped positions in orange on the reader slider, and adjust filler counts with plus and minus buttons.
- Edit a library entry's title, author, artist, description, genres, and status, or reset individual fields to their source values. Custom titles appear throughout the library, series screens, and initial tracker search.
- Hide or unhide selected chapters, identify hidden chapters in the list, and filter between hidden and visible chapters. Reader navigation skips hidden chapters, and library unread counts exclude them.
- View chapter details such as its number, scanlator, upload and fetch dates, last-read date, read duration, and reading progress.
- Filter subchapters separately. Reader navigation respects this choice when configured to skip filtered chapters.
- Limit global and migration searches to pinned sources with a setting that persists between searches.
- Preserve selected custom series information, custom covers, notes, and matching reading history during manga migration. When duplicate old chapters share a chapter number, the most recent reading record is used.
- Include custom series information and hidden chapter state in backups and restores.
- Prevent manga migration from continuing without a selected source and preserve back navigation to its configuration screen.

### Fork and release behavior

- Use MihonX branding, a separate application identity, MihonX release artifacts, and update checks from `SecretX33/mihonx`.
- Keep MihonX's version sequence independent from upstream release tags.
- Disable telemetry and Firebase integration in MihonX builds.
- Support manual releases, pipeline-managed versions, MihonX APK names, and isolated upstream tags.
- Check weekly whether the fork can fast-forward from upstream without rewriting MihonX history.
- Preserve MihonX custom data through the Mihon `v0.21.3` database upgrades.

### Notes for maintainers

- Keep this file limited to MihonX-specific behavior and maintenance changes.
- Do not duplicate upstream release notes here; update the baseline when MihonX is rebased or merged onto a new upstream release.
