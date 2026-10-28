# Repository Overview

MihonX is a light fork of Mihon. Keep the fork focused on small, compatible enhancements and fixes rather than large architectural changes, breaking changes, or broad divergence from upstream.

# Project Working Agreements

- Keep changes as close to upstream as practical so upstream commits can be merged into `main` with minimal conflict or fork-specific adaptation.
- Minimize database code differences from upstream. Preserve fork fields through straightforward schema changes; use query adaptations when simple, and avoid complex runtime workarounds. Keep upstream migration numbering where compatible with the fork's existing migration history.
- Preserve bidirectional backup compatibility. Backups created by upstream must remain importable in this fork, and backups created by this fork must remain importable upstream.
- Keep `CHANGELOG_MIHONX.md` reasonably current with concise, user-visible, fork-specific changes. Keep it lean and do not duplicate upstream release notes.
