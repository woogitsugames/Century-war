"""Regresja konfiguracji bramek CI; w CI dodatkowo sprawdzamy graf Gradle."""

import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class ReleaseGatesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = (ROOT / ".github/workflows/android.yml").read_text(encoding="utf-8")
        cls.gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

    def job(self, name: str) -> str:
        match = re.search(
            rf"(?ms)^  {re.escape(name)}:\n(.*?)(?=^  [a-z][a-z-]*:\n|\Z)",
            self.workflow,
        )
        self.assertIsNotNone(match, f"Brak joba {name}")
        return match.group(1)

    def test_tag_requires_same_sha_build_and_test_jobs(self):
        for name in ("build", "fast-tests", "screenshot-tests", "balance-tests"):
            with self.subTest(job=name):
                self.assertNotIn("!startsWith(github.ref, 'refs/tags/v')", self.job(name))
        release = self.job("release")
        self.assertIn(
            "needs: [build, fast-tests, screenshot-tests, balance-tests]", release
        )
        self.assertIn("startsWith(github.ref, 'refs/tags/v')", release)

    def test_release_receives_services_without_logging_values(self):
        release = self.job("release")
        for value in (
            "CW_SERVER_URL: ${{ vars.CW_SERVER_URL }}",
            "CW_HMAC_KEY: ${{ secrets.CW_HMAC_KEY }}",
            "CW_PRIVACY_URL: ${{ vars.CW_PRIVACY_URL }}",
        ):
            self.assertIn(value, release)
        self.assertIn("for name in CW_SERVER_URL CW_HMAC_KEY CW_PRIVACY_URL;", release)
        self.assertIn('"${!name:-}"', release)

    def test_debug_only_test_graph_is_checked(self):
        self.assertIn("gradle.projectsEvaluated {", self.gradle)
        self.assertIn('setDependsOn(listOf(tasks.named("testDebugUnitTest")))', self.gradle)
        self.assertIn("./gradlew :app:test --dry-run", self.job("fast-tests"))
        self.assertIn("testReleaseUnitTest|releaseChecklist", self.job("fast-tests"))

    def test_memory_limit_and_heavy_screenshots_are_isolated(self):
        self.assertIn("forkEvery = 1L", self.gradle)
        self.assertIn('maxHeapSize = "384m"', self.gradle)
        rules = (ROOT / "gradle/test-categories.properties").read_text(encoding="utf-8")
        self.assertIn("*PrereleasePolish*", rules)

    def test_png_outputs_are_cached_and_checked_before_upload(self):
        self.assertIn('outputs.dir(layout.buildDirectory.dir("screenshots"))', self.gradle)
        self.assertIn("outputs.doNotCacheIf(", self.gradle)
        screenshot = self.job("screenshot-tests")
        self.assertIn(
            "python3 scripts/verify_ci_screenshots.py app/build/screenshots", screenshot
        )
        self.assertIn("app/build/screenshots/", screenshot)
        self.assertIn("FROM-CACHE", screenshot)


if __name__ == "__main__":
    unittest.main()
