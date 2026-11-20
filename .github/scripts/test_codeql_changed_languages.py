import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from codeql_changed_languages import select_languages


class CodeQLChangedLanguagesTest(unittest.TestCase):
    def test_selects_language_and_build_inputs(self):
        cases = {
            "src/main/java/example/App.java": ["java-kotlin"],
            "contracts/src/main/java/example/App.java": ["java-kotlin"],
            "build.gradle": ["java-kotlin"],
            "gradle/wrapper/gradle-wrapper.properties": ["java-kotlin"],
            ".github/workflows/ash.yml": ["actions"],
            ".github/actions/example/action.yml": ["actions"],
        }
        for path, expected in cases.items():
            with self.subTest(path=path):
                self.assertEqual(select_languages([path]), expected)

    def test_mixed_and_documentation_changes(self):
        self.assertEqual(select_languages(["README.md", "docs/design.md"]), [])
        self.assertEqual(
            select_languages(["src/main/App.java", ".github/workflows/ash.yml"]),
            ["actions", "java-kotlin"],
        )

    def test_unknown_and_codeql_configuration_scan_all(self):
        for path in (
            "src/main/resources/application.yml",
            ".github/workflows/codeql.yml",
            ".github/scripts/codeql_changed_languages.py",
            ".github/scripts/test_codeql_changed_languages.py",
            ".github/codeql/config.yml",
        ):
            with self.subTest(path=path):
                self.assertEqual(select_languages([path]), ["actions", "java-kotlin"])

    def test_git_diff_includes_both_sides_of_rename(self):
        script = Path(__file__).with_name("codeql_changed_languages.py")
        with tempfile.TemporaryDirectory() as directory:
            repo = Path(directory)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.name", "Test"], cwd=repo, check=True)
            subprocess.run(["git", "config", "user.email", "test@example.invalid"], cwd=repo, check=True)
            (repo / "Example.java").write_text("class Example {}\n", encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "base"], cwd=repo, check=True)
            base = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip()
            (repo / "Example.java").rename(repo / "README.md")
            subprocess.run(["git", "add", "-A"], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "rename"], cwd=repo, check=True)
            output = repo / "output"
            subprocess.run(
                [sys.executable, str(script), base],
                cwd=repo,
                env={**os.environ, "GITHUB_OUTPUT": str(output)},
                check=True,
            )
            self.assertEqual(output.read_text(encoding="utf-8"), 'languages=["java-kotlin"]\n')


if __name__ == "__main__":
    unittest.main()
