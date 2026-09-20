# Repository Overview

MihonX is a light fork of Mihon. Keep the fork focused on small, compatible enhancements and fixes rather than large architectural changes, breaking changes, or broad divergence from upstream.

# Project Working Agreements

- Keep changes as close to upstream as practical so upstream commits can be merged into `main` with minimal conflict or fork-specific adaptation.
- Avoid fork-only database schema migrations and version increments when a compatible runtime or query-level solution is practical. Keep database migration numbering aligned with upstream.
- Preserve bidirectional backup compatibility. Backups created by upstream must remain importable in this fork, and backups created by this fork must remain importable upstream.
- Keep `CHANGELOG_MIHONX.md` reasonably current with concise, user-visible, fork-specific changes. Keep it lean and do not duplicate upstream release notes.
