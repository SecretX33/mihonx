# MihonX changelog

This changelog records changes made by MihonX relative to its Mihon upstream. For upstream features, fixes, and dependency updates, see the upstream [`CHANGELOG.md`](CHANGELOG.md).

## Unreleased

Baseline: Mihon `v0.20.4` (`df6507256acce8e7f3660783a3db6dbd1a31b6b5`).

Backups are portable in both directions. Mihon `v0.20.4` backups restore in MihonX, and MihonX backups restore in Mihon with common data intact. Mihon ignores fork-only custom series information and hidden chapter state, so those fields are not retained if a backup is restored and re-exported by Mihon. The applications keep separate live databases, which are not intended to be copied between installations.

### User-facing changes

- Edit a library entry's title, author, artist, description, genres, and status, or reset individual fields to their source values. Custom titles appear throughout the library and series screens.
- Hide or unhide selected chapters, identify hidden chapters in the list, and filter between hidden and visible chapters.
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
- Verify the MihonX database upgrade paths and build process against the Mihon `v0.20.4` project structure.

### Notes for maintainers

- Keep this file limited to MihonX-specific behavior and maintenance changes.
- Do not duplicate upstream release notes here; update the baseline when MihonX is rebased or merged onto a new upstream release.
