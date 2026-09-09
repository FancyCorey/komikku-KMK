import io
import json
import struct
import unittest
import zlib
from contextlib import redirect_stdout
from datetime import datetime, timedelta, timezone
from pathlib import Path
from tempfile import NamedTemporaryFile, TemporaryDirectory

from tools.capture_coordinator import (
    PACKAGE,
    CaptureExpectation,
    PrivacyGateError,
    ManifestError,
    SidecarError,
    authorize_publication,
    build_capture_expectation,
    build_sidecar,
    create_session,
    main,
    update_cleanup_result,
    update_privacy_review,
    update_review,
    validate_manifest,
    validate_manifest_fixture_catalog,
)
from tools.evidence_fixture_registry import FixtureRegistryError, load_fixtures


TOOLS = Path(__file__).parents[1]
MANIFEST = TOOLS / "capture_routes.json"
CATALOG = TOOLS / "evidence_fixtures.json"


class CaptureCoordinatorTests(unittest.TestCase):
    def host_catalog(self):
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        # Structural tests are not live device-readiness attestations.
        for fixture in catalog["fixtures"]:
            fixture["lifecycle"]["deviceReady"] = False
        with NamedTemporaryFile(mode="w", encoding="utf-8", suffix=".json", prefix="test-host-fixtures-", dir=TOOLS, delete=False) as handle:
            path = Path(handle.name)
            self.addCleanup(path.unlink)
            json.dump(catalog, handle)
        return path

    def test_manifest_ignores_unreferenced_expired_fixture_but_rejects_referenced_one(self):
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        fixture = next(row for row in catalog["fixtures"] if row["id"] == "reader-completion-prompt-local-chapter")
        expired = json.loads(json.dumps(fixture))
        expired["id"] = "expired-other-route"
        expired["lifecycle"]["deviceReady"] = True
        expired["lifecycle"]["expiresAt"] = "2000-01-01T00:00:00Z"
        route = {"name": fixture["route"], "fixtureId": fixture["id"], "checkpointIds": ["completion-prompt-visible"]}
        with TemporaryDirectory() as directory:
            path = Path(directory) / "fixtures.json"
            path.write_text(json.dumps({"schemaVersion": 2, "fixtures": [fixture, expired]}), encoding="utf-8")
            result = validate_manifest_fixture_catalog({"routes": [route]}, path)
            self.assertEqual([fixture["id"]], list(result))
            with self.assertRaises(ManifestError):
                validate_manifest_fixture_catalog({"routes": [dict(route, fixtureId=expired["id"])]}, path)
            with self.assertRaises(FixtureRegistryError):
                load_fixtures(path)
            with self.assertRaisesRegex(FixtureRegistryError, "Unknown fixture id"):
                load_fixtures(path, fixture_ids={fixture["id"], "missing"})
            with self.assertRaisesRegex(FixtureRegistryError, "not both"):
                load_fixtures(path, fixture_id=fixture["id"], fixture_ids={fixture["id"]})

    def expectation(self):
        return CaptureExpectation(
            route_name="reader-completion-rating",
            fixture_id="reader-completion-prompt-local-chapter",
            fixture_revision="a16.1-r1",
            checkpoint_id="completion-prompt-visible",
            source_commit="89788717e01353ef47ba05ba3a39ed30b259d8a1e",
            app_id=PACKAGE,
            app_version="1.14.1-test",
            apk_sha256="A" * 64,
            signature_digest="B" * 64,
            expected_package=PACKAGE,
            expected_activity="app.komikku.dev/ReaderActivity",
        )

    def result(self):
        expectation = self.expectation()
        return {
            "deviceOnline": True,
            "deviceUnambiguous": True,
            "deviceSerial": "test-device",
            "routeName": expectation.route_name,
            "fixtureId": expectation.fixture_id,
            "fixtureRevision": expectation.fixture_revision,
            "checkpointId": expectation.checkpoint_id,
            "sourceCommit": expectation.source_commit,
            "appId": expectation.app_id,
            "appVersion": expectation.app_version,
            "apkSha256": expectation.apk_sha256,
            "signatureDigest": expectation.signature_digest,
            "package": expectation.expected_package,
            "activity": expectation.expected_activity,
            "activityVerified": True,
            "evaluationModeVerified": True,
            "overlayClear": True,
            "knownState": "main-evaluation-home",
            "markerVerified": True,
        }

    def png_bytes(self):
        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

        ihdr = struct.pack(">IIBBBBB", 1, 1, 8, 6, 0, 0, 0)
        return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(b"\x00\x00\x00\x00\x00")) + chunk(b"IEND", b"")

    def sidecar(self, directory, crop_status="not-cropped"):
        image = Path(directory) / "capture.png"
        image.write_bytes(self.png_bytes())
        return build_sidecar(
            create_session(self.result(), self.expectation()),
            str(image),
            self.result(),
            crop_status=crop_status,
            timestamp="2026-08-14T00:00:00+00:00",
        )

    def referenced_route(self):
        return {
            "name": "reader",
            "status": "blocked",
            "blockingReason": "Needs a fixture.",
            "blockerType": "missing-fixture",
            "owner": "fixture design",
            "exitCondition": "A disposable reader fixture is available.",
            "cleanupMethod": "Restore the saved reader snapshot.",
            "fixtureId": "reader-fixture",
            "checkpointIds": ["prompt-visible"],
            "capture": {"allowed": False},
        }

    def test_gate_rejects_wrong_package(self):
        result = self.result()
        result["package"] = "com.google.android.youtube"
        with self.assertRaises(PrivacyGateError):
            create_session(result, self.expectation())

    def test_gate_rejects_missing_evaluation_mode(self):
        result = self.result()
        result["evaluationModeVerified"] = False
        with self.assertRaises(PrivacyGateError):
            create_session(result, self.expectation())

    def test_gate_rejects_overlay(self):
        result = self.result()
        result["overlayClear"] = False
        with self.assertRaises(PrivacyGateError):
            create_session(result, self.expectation())

    def test_capture_is_single_use(self):
        with TemporaryDirectory() as directory:
            image = Path(directory) / "capture.png"
            image.write_bytes(self.png_bytes())
            session = create_session(self.result(), self.expectation())
            sidecar = build_sidecar(session, str(image), self.result())
            self.assertEqual("not-reviewed", sidecar["privateReview"]["status"])
            with self.assertRaises(PrivacyGateError):
                build_sidecar(session, str(image), self.result())

    def test_expired_session_rejected(self):
        start = datetime(2026, 8, 14, tzinfo=timezone.utc)
        session = create_session(
            self.result(), self.expectation(), ttl_seconds=1, now=start,
        )
        with self.assertRaises(PrivacyGateError):
            session.authorize_capture(start + timedelta(seconds=2))

    def test_gate_rejects_wrong_activity_and_build_hash(self):
        result = self.result()
        result["activity"] = "app.komikku.dev/MainActivity"
        with self.assertRaises(PrivacyGateError):
            create_session(result, self.expectation())
        result = self.result()
        result["apkSha256"] = "C" * 64
        with self.assertRaises(PrivacyGateError):
            create_session(result, self.expectation())

    def test_gate_rejects_invalid_session_ttl(self):
        with self.assertRaises(PrivacyGateError):
            create_session(self.result(), self.expectation(), ttl_seconds=301)

    def test_expectation_factory_requires_verified_route_device_fixture_and_exact_build(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        route = next(route for route in manifest["routes"] if route["name"] == "reader-completion-rating")
        fixture = next(
            fixture
            for fixture in load_fixtures(CATALOG, fixture_id="reader-completion-prompt-local-chapter")
            if fixture.id == "reader-completion-prompt-local-chapter"
        )
        build = {
            "sourceCommit": fixture.provenance["sourceCommit"],
            "appId": fixture.provenance["appId"],
            "version": fixture.provenance["version"],
            "apkSha256": "A" * 64,
            "signatureDigest": "B" * 64,
        }
        with self.assertRaises(PrivacyGateError):
            build_capture_expectation(route, fixture, "completion-prompt-visible", build)

        route["status"] = "verified"
        route["verification"] = {"status": "passed", "record": "synthetic-test-only"}
        route["capture"]["allowed"] = True
        fixture.lifecycle["deviceReady"] = True
        fixture.provenance["apkSha256"] = build["apkSha256"]
        fixture.provenance["signatureDigest"] = build["signatureDigest"]
        expectation = build_capture_expectation(route, fixture, "completion-prompt-visible", build)
        self.assertEqual(fixture.revision, expectation.fixture_revision)
        self.assertEqual("app.komikku.dev/ReaderActivity", expectation.expected_activity)

        build["apkSha256"] = "C" * 64
        with self.assertRaises(PrivacyGateError):
            build_capture_expectation(route, fixture, "completion-prompt-visible", build)

    def test_sidecar_binds_route_build_target_and_png_integrity(self):
        with TemporaryDirectory() as directory:
            sidecar = self.sidecar(directory)
        self.assertEqual(2, sidecar["schemaVersion"])
        self.assertEqual("a16.1-r1", sidecar["route"]["fixtureRevision"])
        self.assertEqual("completion-prompt-visible", sidecar["route"]["checkpointId"])
        self.assertEqual("A" * 64, sidecar["build"]["apkSha256"])
        self.assertEqual("app.komikku.dev/ReaderActivity", sidecar["target"]["expectedActivity"])
        self.assertEqual(1, sidecar["image"]["width"])
        self.assertEqual(1, sidecar["image"]["height"])
        self.assertEqual("png-chunks-and-idat-decoded", sidecar["image"]["decoderResult"])
        self.assertEqual(64, len(sidecar["image"]["sha256"]))
        self.assertEqual("human-gate-required", sidecar["publication"]["status"])

    def test_sidecar_rejects_invalid_png(self):
        with TemporaryDirectory() as directory:
            image = Path(directory) / "bad.png"
            image.write_bytes(b"not a png")
            with self.assertRaises(SidecarError):
                build_sidecar(create_session(self.result(), self.expectation()), str(image), self.result())

    def test_sidecar_rechecks_activity_and_marker_at_capture_time(self):
        with TemporaryDirectory() as directory:
            image = Path(directory) / "capture.png"
            image.write_bytes(self.png_bytes())
            checkpoint = self.result()
            checkpoint["activity"] = "app.komikku.dev/MainActivity"
            with self.assertRaises(PrivacyGateError):
                build_sidecar(create_session(self.result(), self.expectation()), str(image), checkpoint)
            checkpoint = self.result()
            checkpoint["markerVerified"] = False
            with self.assertRaises(PrivacyGateError):
                build_sidecar(create_session(self.result(), self.expectation()), str(image), checkpoint)

    def test_blocked_route_requires_fixture_and_checkpoint_references(self):
        route = self.referenced_route()
        route.pop("fixtureId")
        with self.assertRaises(Exception):
            validate_manifest({"routes": [route]})
        route["fixtureId"] = "reader-fixture"
        validate_manifest({"routes": [route]})

    def test_route_rejects_duplicated_fixture_safety_object(self):
        route = self.referenced_route()
        route["fixture"] = {"available": True, "deviceReady": True}
        with self.assertRaises(Exception):
            validate_manifest({"routes": [route]})

    def test_real_manifest_and_catalog_match(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        validate_manifest(manifest)
        validate_manifest_fixture_catalog(manifest, self.host_catalog())

    def test_catalog_rejects_wrong_route_binding(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        manifest["routes"][1]["fixtureId"] = "source-evaluation-read-only-and-failure"
        with self.assertRaises(Exception):
            validate_manifest_fixture_catalog(manifest, self.host_catalog())

    def test_catalog_rejects_unknown_checkpoint(self):
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        manifest["routes"][1]["checkpointIds"] = ["not-a-real-checkpoint"]
        with self.assertRaises(Exception):
            validate_manifest_fixture_catalog(manifest, self.host_catalog())

    def test_manifest_readiness_reports_layered_state(self):
        output = io.StringIO()
        with redirect_stdout(output):
            code = main(["manifest", "readiness", str(MANIFEST), "--fixtures", str(self.host_catalog())])
        self.assertEqual(0, code)
        text = output.getvalue()
        self.assertIn("browse-error-unavailable: hostContract=ready", text)
        self.assertIn("deviceFixture=blocked", text)
        self.assertIn("captureAuthorization=blocked", text)

    def test_nonexistent_route_cannot_claim_fixture_reference(self):
        route = {
            "name": "missing-feature", "status": "not-applicable",
            "blockingReason": "The action does not exist.", "blockerType": "nonexistent",
            "owner": "product decision", "exitCondition": "Define the product feature first.",
            "cleanupMethod": "No app state is entered.", "fixtureId": "fake-feature",
            "checkpointIds": ["fake"], "capture": {"allowed": False},
        }
        with self.assertRaises(Exception):
            validate_manifest({"routes": [route]})

    def test_private_approval_requires_privacy_and_cleanup(self):
        with TemporaryDirectory() as directory:
            sidecar = self.sidecar(directory)
        with self.assertRaises(SidecarError):
            update_review(sidecar, "approved", "private-reviewer", "checked")

    def test_review_and_publication_require_explicit_ordered_human_transitions(self):
        with TemporaryDirectory() as directory:
            sidecar = self.sidecar(directory)
        privacy = update_privacy_review(
            sidecar, "passed", "passed", "privacy-reviewer", "no canaries",
            now="2026-08-14T00:01:00+00:00",
        )
        cleanup = update_cleanup_result(
            privacy,
            "passed",
            "Remove named fixture through supported UI.",
            [{"id": "fixture-absent", "status": "passed"}],
            "cleanup-reviewer",
            now="2026-08-14T00:02:00+00:00",
        )
        reviewed = update_review(
            cleanup, "approved", "private-reviewer", "private evidence accepted",
            now="2026-08-14T00:03:00+00:00",
        )
        published = authorize_publication(
            reviewed, "publication-reviewer", "Approved for the named public packet only.",
            now="2026-08-14T00:04:00+00:00",
        )
        self.assertEqual("approved", reviewed["privateReview"]["status"])
        self.assertEqual("human-gate-required", cleanup["publication"]["status"])
        self.assertEqual("authorized", published["publication"]["status"])
        self.assertEqual("reader-completion-rating", published["route"]["name"])

    def test_publication_cannot_bypass_private_approval(self):
        with TemporaryDirectory() as directory:
            sidecar = self.sidecar(directory)
        with self.assertRaises(SidecarError):
            authorize_publication(sidecar, "publication-reviewer", "approve")

    def test_cleanup_pass_rejects_failed_predicate(self):
        with TemporaryDirectory() as directory:
            sidecar = self.sidecar(directory)
        with self.assertRaises(SidecarError):
            update_cleanup_result(
                sidecar,
                "passed",
                "Remove fixture.",
                [{"id": "fixture-absent", "status": "failed"}],
                "cleanup-reviewer",
            )

    def test_unreviewed_crop_blocks_private_approval(self):
        with TemporaryDirectory() as directory:
            sidecar = self.sidecar(directory, crop_status="cropped-unreviewed")
        sidecar = update_privacy_review(sidecar, "passed", "passed", "privacy-reviewer")
        sidecar = update_cleanup_result(
            sidecar, "passed", "Remove fixture.",
            [{"id": "fixture-absent", "status": "passed"}], "cleanup-reviewer",
        )
        with self.assertRaises(SidecarError):
            update_review(sidecar, "approved", "private-reviewer")

    def test_utf8_manifest_listing(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "routes.json"
            path.write_text(json.dumps({"routes": [{"name": "test-route", "status": "planned"}]}), encoding="utf-8")
            output = io.StringIO()
            with redirect_stdout(output):
                main(["manifest", "list", str(path)])
            self.assertIn("test-route", output.getvalue())


if __name__ == "__main__":
    unittest.main()
