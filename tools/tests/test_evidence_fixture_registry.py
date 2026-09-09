import copy
import hashlib
import json
import shutil
import unittest
from datetime import datetime, timezone
from pathlib import Path
from tempfile import NamedTemporaryFile, TemporaryDirectory

from tools.evidence_fixture_registry import FixtureRegistryError, load_fixtures, readiness
from tools.workspace_paths import find_workspace_root


SOURCE_ROOT = Path(__file__).parents[2]
WORKSPACE_ROOT = find_workspace_root(SOURCE_ROOT)
CATALOG = SOURCE_ROOT / "tools" / "evidence_fixtures.json"
FUTURE = datetime(2026, 8, 14, tzinfo=timezone.utc)


class EvidenceFixtureRegistryTests(unittest.TestCase):
    def synthetic_runtime_packet(self):
        fixture = load_fixtures(CATALOG, fixture_id="alternate-source-reader-scenario-matrix")[0]
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        artifact = json.loads((WORKSPACE_ROOT / fixture.artifact_path).read_text(encoding="utf-8"))
        verification = json.loads((WORKSPACE_ROOT / fixture.verification_path).read_text(encoding="utf-8"))
        # Isolate registry behavior from historical acceptance fingerprints.
        for index, item in enumerate(artifact["runtimeValidation"]["inputs"]):
            target = root / item["path"]
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(f"synthetic runtime input {index}", encoding="utf-8")
            item["sha256"] = hashlib.sha256(target.read_bytes()).hexdigest().upper()
        target = root / fixture.artifact_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(artifact), encoding="utf-8")
        digest = hashlib.sha256(target.read_bytes()).hexdigest().upper()
        fixture.provenance["fixtureInputSha256"] = digest
        verification["fixtureInputSha256"] = digest
        target = root / fixture.verification_path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(json.dumps(verification), encoding="utf-8")
        object.__setattr__(fixture, "source_root", root)
        return fixture, root, artifact

    def host_catalog(self):
        raw = self.raw_catalog()
        for row in raw["fixtures"]:
            row["lifecycle"]["deviceReady"] = False
        with NamedTemporaryFile(mode="w", encoding="utf-8", dir=CATALOG.parent, suffix=".json", prefix="test-host-", delete=False) as handle:
            path = Path(handle.name)
            self.addCleanup(path.unlink)
            json.dump(raw, handle)
        return path

    def raw_catalog(self):
        return json.loads(CATALOG.read_text(encoding="utf-8"))

    @staticmethod
    def fixture(raw, fixture_id):
        return next(row for row in raw["fixtures"] if row["id"] == fixture_id)

    def load_modified(self, raw):
        raw = copy.deepcopy(raw)
        # Synthetic promotion cases should not inherit device readiness from
        # the catalog's historical private receipt.
        self.fixture(raw, "for-you-top-picks-debug")["lifecycle"]["deviceReady"] = False
        with TemporaryDirectory() as directory:
            path = Path(directory) / "fixtures.json"
            path.write_text(json.dumps(raw), encoding="utf-8")
            return load_fixtures(path, now=FUTURE, root=WORKSPACE_ROOT)

    def promote(self, fixture):
        fixture["target"] = "emulator"
        fixture["seed"]["supported"] = True
        fixture["provenance"]["apkSha256"] = "A" * 64
        fixture["provenance"]["signatureDigest"] = "B" * 64
        fixture["lifecycle"]["deviceReady"] = True
        fixture["lifecycle"]["expiresAt"] = "2026-08-15T00:00:00Z"
        fixture["lifecycle"]["validatedFingerprints"] = {
            field: fixture["provenance"][field]
            for field in fixture["lifecycle"]["invalidatingFingerprints"]
        }

    def assert_rejected(self, raw):
        with self.assertRaises(FixtureRegistryError):
            self.load_modified(raw)

    def test_catalog_separates_host_availability_from_device_readiness(self):
        fixtures = load_fixtures(self.host_catalog(), now=FUTURE)
        self.assertEqual(13, len(fixtures))
        self.assertTrue(any(fixture.host_contract_available(WORKSPACE_ROOT) for fixture in fixtures))
        self.assertEqual(
            set(),
            {fixture.id for fixture in fixtures if fixture.device_ready},
        )
        self.assertEqual(
            {
                "browse-deterministic-source-failure",
                "best-version-migration-disposable-library",
                "alternate-source-reader-scenario-matrix",
                "extension-package-private-export",
                "manga-detail-privacy-disposable-library",
                "rated-collections-local-manga",
                "recommendation-settings-index-navigation",
                "source-evaluation-read-only-and-failure",
                "sources-to-try-disposable-extension",
                "reader-completion-prompt-local-chapter",
                "for-you-top-picks-debug",
            },
            {fixture.id for fixture in fixtures if fixture.seed["supported"]},
        )
        for fixture in fixtures:
            if fixture.app_fixture_available(WORKSPACE_ROOT):
                self.assertTrue(fixture.host_contract_available(WORKSPACE_ROOT))
                self.assertTrue(fixture.seed["supported"])

    def test_readiness_reports_each_layer_and_never_capture_authorization(self):
        output = readiness(self.host_catalog(), WORKSPACE_ROOT)
        self.assertEqual(13, len(output))
        self.assertTrue(all("hostContract=" in row for row in output))
        self.assertEqual(
            set(),
            {row.split(":", 1)[0] for row in output if "deviceFixture=ready" in row},
        )
        self.assertTrue(all("captureAuthorization=blocked" in row for row in output))

    def test_host_contract_hash_is_verified(self):
        fixture = load_fixtures(CATALOG, now=FUTURE, fixture_id="browse-deterministic-source-failure")[0]
        raw = copy.deepcopy(fixture.provenance)
        fixture.provenance["fixtureInputSha256"] = "0" * 64
        self.assertFalse(fixture.host_contract_available(WORKSPACE_ROOT))
        fixture.provenance.update(raw)

    def test_seed_boolean_without_revision_bound_packet_does_not_promote_app_fixture(self):
        fixture = next(fixture for fixture in load_fixtures(self.host_catalog(), now=FUTURE) if not fixture.seed["supported"])
        fixture.seed["supported"] = True
        self.assertFalse(fixture.app_fixture_available(WORKSPACE_ROOT))

    def test_sources_to_try_packet_is_hash_bound_but_not_device_ready(self):
        fixture = next(
            fixture
            for fixture in load_fixtures(CATALOG, now=FUTURE, fixture_id="sources-to-try-disposable-extension")
            if fixture.id == "sources-to-try-disposable-extension"
        )

        self.assertTrue(fixture.app_fixture_available(WORKSPACE_ROOT))
        self.assertFalse(fixture.device_ready)
        self.assertFalse(fixture.readiness_layers(WORKSPACE_ROOT)["captureAuthorization"])

    def test_best_version_packet_is_revision_and_hash_bound_but_not_device_ready(self):
        fixture = next(
            fixture
            for fixture in load_fixtures(CATALOG, now=FUTURE, fixture_id="best-version-migration-disposable-library")
            if fixture.id == "best-version-migration-disposable-library"
        )

        self.assertTrue(fixture.app_fixture_available(WORKSPACE_ROOT))
        self.assertFalse(fixture.device_ready)
        self.assertFalse(fixture.readiness_layers(WORKSPACE_ROOT)["captureAuthorization"])

        object.__setattr__(fixture, "revision", "stale-f2-revision")
        self.assertFalse(fixture.app_fixture_available(WORKSPACE_ROOT))

    def test_alternate_source_packet_is_revision_and_runtime_hash_bound(self):
        fixture, root, _ = self.synthetic_runtime_packet()
        self.assertTrue(fixture.host_contract_available(root))
        self.assertTrue(fixture.app_fixture_available(root))
        self.assertFalse(fixture.device_ready)

        object.__setattr__(fixture, "revision", "stale-bridge-revision")
        self.assertFalse(fixture.host_contract_available(root))

    def test_alternate_source_packet_rejects_stale_runtime_input(self):
        fixture, root, artifact = self.synthetic_runtime_packet()
        self.assertTrue(fixture.host_contract_available(root))
        stale = root / artifact["runtimeValidation"]["inputs"][0]["path"]
        stale.write_text("stale", encoding="utf-8")
        self.assertFalse(fixture.host_contract_available(root))

    def test_host_available_flag_fails_closed(self):
        fixture = next(
            fixture
            for fixture in load_fixtures(CATALOG, now=FUTURE, fixture_id="alternate-source-reader-scenario-matrix")
            if fixture.id == "alternate-source-reader-scenario-matrix"
        )

        fixture.lifecycle["hostAvailable"] = False
        self.assertFalse(fixture.host_contract_available(WORKSPACE_ROOT))
        self.assertFalse(fixture.app_fixture_available(WORKSPACE_ROOT))

    def test_packet_revision_must_match_catalog_revision(self):
        fixture = next(
            fixture
            for fixture in load_fixtures(CATALOG, now=FUTURE, fixture_id="reader-completion-prompt-local-chapter")
            if fixture.id == "reader-completion-prompt-local-chapter"
        )
        object.__setattr__(fixture, "revision", "stale-revision")
        self.assertFalse(fixture.app_fixture_available(WORKSPACE_ROOT))

    def test_navigation_packet_must_be_read_only(self):
        fixture = next(
            fixture
            for fixture in load_fixtures(CATALOG, now=FUTURE, fixture_id="recommendation-settings-index-navigation")
            if fixture.id == "recommendation-settings-index-navigation"
        )
        fixture.rollback_evidence["externalEffect"] = "preference"
        object.__setattr__(fixture, "mutation_boundary", "isolated")
        self.assertFalse(fixture.app_fixture_available(WORKSPACE_ROOT))

    def test_recommendation_settings_fixture_binds_current_build_without_device_promotion(self):
        raw = self.raw_catalog()
        fixture = self.fixture(raw, "recommendation-settings-index-navigation")
        expected = {
            "sourceCommit": "89788717e01353ef47ba05ba3a39ed30b259d8a1",
            "version": "1.14.1-50",
            "apkSha256": "A7DB2DD298EA83154D4C64DC0BAC8A7619B9F7F536181B11A59299D4E7792C21",
            "signatureDigest": "AF5EC546F3F640B71E3E19E69B9C1913AF27803E255C80ABE65BB96C022D417A",
        }
        self.assertEqual(expected, {key: fixture["provenance"][key] for key in expected})
        self.assertEqual(expected["sourceCommit"], fixture["lifecycle"]["validatedFingerprints"]["sourceCommit"])
        self.assertEqual(expected["apkSha256"], fixture["lifecycle"]["validatedFingerprints"]["apkSha256"])
        self.assertEqual(expected["signatureDigest"], fixture["lifecycle"]["validatedFingerprints"]["signatureDigest"])
        self.assertFalse(fixture["privacy"]["evaluationModeRequired"])
        self.assertFalse(fixture["lifecycle"]["deviceReady"])
        route = next(route for route in json.loads((SOURCE_ROOT / "tools" / "capture_routes.json").read_text(encoding="utf-8"))["routes"] if route["name"] == "recommendation-settings")
        self.assertFalse(route["capture"]["allowed"])

    def test_runtime_inputs_can_resolve_from_explicit_workspace_source_root(self):
        raw = self.raw_catalog()
        self.fixture(raw, "for-you-top-picks-debug")["lifecycle"]["deviceReady"] = False
        with TemporaryDirectory() as directory:
            path = Path(directory) / "fixtures.json"
            path.write_text(json.dumps(raw), encoding="utf-8")
            fixtures = load_fixtures(
                path,
                now=FUTURE,
                root=WORKSPACE_ROOT,
                source_root=WORKSPACE_ROOT,
            )
        fixture = next(fixture for fixture in fixtures if fixture.id == "for-you-top-picks-debug")
        self.assertEqual(WORKSPACE_ROOT, fixture.source_root)

    def test_apk_staging_packet_must_be_isolated_and_mutating(self):
        fixture = next(
            fixture
            for fixture in load_fixtures(CATALOG, now=FUTURE, fixture_id="extension-package-private-export")
            if fixture.id == "extension-package-private-export"
        )
        object.__setattr__(fixture, "mutation_boundary", "read-only")
        self.assertFalse(fixture.app_fixture_available(WORKSPACE_ROOT))

    def test_rejects_stale_fingerprint(self):
        raw = self.raw_catalog()
        fixture = self.fixture(raw, "source-evaluation-read-only-and-failure")
        self.promote(fixture)
        fixture["provenance"]["sourceCommit"] = "different-commit"
        self.assert_rejected(raw)

    def test_rejects_missing_terminal_predicate(self):
        raw = self.raw_catalog()
        self.fixture(raw, "browse-deterministic-source-failure")["terminalStates"]["error"] = []
        self.assert_rejected(raw)

    def test_rejects_missing_cleanup_predicate(self):
        raw = self.raw_catalog()
        self.fixture(raw, "browse-deterministic-source-failure")["cleanup"]["predicates"] = []
        self.assert_rejected(raw)

    def test_rejects_host_only_promotion(self):
        raw = self.raw_catalog()
        fixture = self.fixture(raw, "browse-deterministic-source-failure")
        self.promote(fixture)
        fixture["target"] = "host-only"
        self.assert_rejected(raw)

    def test_rejects_external_effect_full_rollback_claim(self):
        raw = self.raw_catalog()
        fixture = next(row for row in raw["fixtures"] if row["id"] == "sources-to-try-disposable-extension")
        fixture["rollbackEvidence"]["claimsFullRollback"] = True
        self.assert_rejected(raw)

    def test_rejects_expired_device_validation(self):
        raw = self.raw_catalog()
        fixture = self.fixture(raw, "source-evaluation-read-only-and-failure")
        self.promote(fixture)
        fixture["lifecycle"]["expiresAt"] = "2026-08-13T00:00:00Z"
        self.assert_rejected(raw)

    def test_accepts_complete_unexpired_device_contract(self):
        raw = self.raw_catalog()
        self.promote(self.fixture(raw, "browse-deterministic-source-failure"))
        fixtures = self.load_modified(raw)
        promoted = next(
            fixture for fixture in fixtures if fixture.id == "browse-deterministic-source-failure"
        )
        self.assertTrue(promoted.device_ready)

    def test_rejects_device_promotion_without_provisioning_packet(self):
        raw = self.raw_catalog()
        fixture = next(row for row in raw["fixtures"] if not row["seed"]["supported"])
        self.promote(fixture)
        self.assert_rejected(raw)


if __name__ == "__main__":
    unittest.main()
