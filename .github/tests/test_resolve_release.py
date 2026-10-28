import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT_DIRECTORY = Path(__file__).resolve().parents[1] / "scripts"
sys.path.insert(0, str(SCRIPT_DIRECTORY))
from resolve_release import git, resolve_release


class ResolveReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name)
        self.git("init", "--initial-branch=main")
        self.git("config", "user.name", "Test User")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "commit.gpgsign", "false")
        self.initial_commit = self.commit("Initial")

    def git(self, *arguments):
        return git(self.repository, *arguments)

    def commit(self, message):
        self.git("commit", "--allow-empty", "-m", message)
        return self.git("rev-parse", "HEAD")

    def test_first_release_starts_at_0_0_1(self):
        self.assertEqual(resolve_release(self.repository), ("v0.0.1", self.initial_commit))

    def test_duplicate_tags_on_one_commit_bump_0_0_12_to_0_0_13(self):
        self.git("tag", "v0.0.11")
        self.git("tag", "v0.0.12")
        current = self.commit("Merge feature")

        self.assertEqual(resolve_release(self.repository), ("v0.0.13", current))

    def test_annotated_and_lightweight_tags_use_highest_version(self):
        self.git("tag", "-a", "v0.0.11", "-m", "Release")
        self.git("tag", "v0.0.12")

        self.assertEqual(resolve_release(self.repository)[0], "v0.0.13")

    def test_version_order_is_numeric(self):
        self.git("tag", "v0.0.9")
        self.git("tag", "v0.0.12")

        self.assertEqual(resolve_release(self.repository)[0], "v0.0.13")

    def test_older_higher_version_wins_over_nearer_lower_version(self):
        self.git("tag", "v0.1.0")
        self.commit("Later commit")
        self.git("tag", "v0.0.99")

        self.assertEqual(resolve_release(self.repository)[0], "v0.1.1")

    def test_merged_upstream_tags_do_not_change_fork_version(self):
        self.git("tag", "v0.0.12")
        self.git("checkout", "-b", "upstream")
        self.commit("Upstream release")
        self.git("tag", "-a", "v0.21.3", "-m", "Upstream")
        self.git("checkout", "main")
        self.commit("Fork change")
        self.git("merge", "--no-ff", "upstream", "-m", "Merge upstream")

        self.assertEqual(resolve_release(self.repository)[0], "v0.0.13")

    def test_skips_a_tag_reserved_on_another_branch(self):
        self.git("tag", "v0.0.12")
        self.git("checkout", "-b", "other")
        self.commit("Other release")
        self.git("tag", "v0.0.13")
        self.git("checkout", "main")

        self.assertEqual(resolve_release(self.repository)[0], "v0.0.14")

    def test_prerelease_and_non_version_tags_are_ignored(self):
        self.git("tag", "v0.0.12")
        for tag in ("v0.1.0-rc1", "vlatest", "v2026.10", "v00.1.0"):
            self.git("tag", tag)

        self.assertEqual(resolve_release(self.repository)[0], "v0.0.13")

    def test_historical_target_still_uses_latest_fork_version(self):
        self.commit("Current release")
        self.git("tag", "v0.0.12")

        self.assertEqual(
            resolve_release(self.repository, input_commit=self.initial_commit),
            ("v0.0.13", self.initial_commit),
        )

    def test_explicit_tag_is_kept(self):
        self.assertEqual(resolve_release(self.repository, input_tag="v1.2.3")[0], "v1.2.3")

    def test_existing_explicit_tag_on_same_commit_can_be_rebuilt(self):
        self.git("tag", "v0.0.12")

        self.assertEqual(resolve_release(self.repository, input_tag="v0.0.12")[0], "v0.0.12")

    def test_existing_explicit_tag_on_another_commit_is_rejected(self):
        self.git("tag", "v0.0.12")
        self.commit("Another commit")

        with self.assertRaisesRegex(ValueError, "different commit"):
            resolve_release(self.repository, input_tag="v0.0.12")

    def test_invalid_explicit_tags_are_rejected(self):
        for tag in ("0.0.13", "v00.0.13", "v0.0.13-rc1", "v0.0.13;echo unexpected"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                resolve_release(self.repository, input_tag=tag)

    def test_outputs_are_written_without_creating_a_tag(self):
        self.git("tag", "v0.0.11")
        self.git("tag", "v0.0.12")
        output = self.repository / "outputs"
        environment = os.environ | {"INPUT_TAG": "", "INPUT_COMMIT": "", "GITHUB_OUTPUT": str(output)}

        subprocess.run(
            [sys.executable, str(SCRIPT_DIRECTORY / "resolve_release.py")],
            cwd=self.repository,
            env=environment,
            check=True,
            capture_output=True,
            text=True,
        )

        self.assertEqual(output.read_text(), f"tag=v0.0.13\ncommit={self.initial_commit}\n")
        self.assertEqual(set(self.git("tag", "--list").splitlines()), {"v0.0.11", "v0.0.12"})


if __name__ == "__main__":
    unittest.main()
