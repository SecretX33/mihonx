import os
from pathlib import Path
import re
import subprocess
import sys


STABLE_TAG = re.compile(r"v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")


def git(repository: Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", *arguments],
        cwd=repository,
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def commit_for(repository: Path, reference: str) -> str:
    object_id = git(repository, "rev-parse", "--verify", "--end-of-options", reference)
    commit = git(repository, "rev-list", "--max-count=1", object_id)
    if not commit:
        raise ValueError(f"Reference {reference} does not resolve to a commit")
    return commit


def resolve_release(repository: Path, input_tag: str = "", input_commit: str = "") -> tuple[str, str]:
    commit = commit_for(repository, input_commit.strip() or "HEAD")
    tags = set(git(repository, "for-each-ref", "--format=%(refname:strip=2)", "refs/tags").splitlines())
    tag = input_tag.strip()

    if tag:
        if not STABLE_TAG.fullmatch(tag):
            raise ValueError("Release tags must use vMAJOR.MINOR.PATCH without leading zeroes")
        if tag in tags and commit_for(repository, f"refs/tags/{tag}") != commit:
            raise ValueError(f"Tag {tag} already exists on a different commit")
    else:
        mainline = set(git(repository, "rev-list", "--first-parent", "HEAD").splitlines())
        versions = []
        for existing_tag in tags:
            match = STABLE_TAG.fullmatch(existing_tag)
            if match and commit_for(repository, f"refs/tags/{existing_tag}") in mainline:
                versions.append(tuple(int(part) for part in match.groups()))

        major, minor, patch = max(versions, default=(0, 0, 0))
        while True:
            patch += 1
            tag = f"v{major}.{minor}.{patch}"
            if tag not in tags:
                break

    return tag, commit


def main() -> int:
    try:
        tag, commit = resolve_release(
            Path.cwd(),
            input_tag=os.environ.get("INPUT_TAG", ""),
            input_commit=os.environ.get("INPUT_COMMIT", ""),
        )
    except (ValueError, subprocess.CalledProcessError) as error:
        detail = error.stderr.strip() if isinstance(error, subprocess.CalledProcessError) else str(error)
        print(f"::error::{detail}", file=sys.stderr)
        return 1

    if output := os.environ.get("GITHUB_OUTPUT"):
        with open(output, "a", encoding="utf-8") as stream:
            stream.write(f"tag={tag}\ncommit={commit}\n")
    print(f"Resolved tag: {tag} for commit: {commit}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
