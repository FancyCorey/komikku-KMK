import io
import json
import unittest
from contextlib import redirect_stdout
from datetime import datetime, timedelta, timezone
from pathlib import Path
from tempfile import TemporaryDirectory

from tools.capture_coordinator import PACKAGE, PrivacyGateError, Session, build_sidecar, create_session, main, update_review, validate_manifest


class CaptureCoordinatorTests(unittest.TestCase):
    def result(self):
        return {"deviceOnline": True, "deviceUnambiguous": True, "deviceSerial": "test-device", "package": PACKAGE, "activity": "app.komikku.dev/MainActivity", "activityVerified": True, "evaluationModeVerified": True, "overlayClear": True, "knownState": "main-evaluation-home"}

    def test_gate_rejects_wrong_package(self):
        result = self.result(); result["package"] = "com.google.android.youtube"
        with self.assertRaises(PrivacyGateError): create_session(result)

    def test_gate_rejects_missing_evaluation_mode(self):
        result = self.result(); result["evaluationModeVerified"] = False
        with self.assertRaises(PrivacyGateError): create_session(result)

    def test_gate_rejects_overlay(self):
        result = self.result(); result["overlayClear"] = False
        with self.assertRaises(PrivacyGateError): create_session(result)

    def test_capture_is_single_use(self):
        session = create_session(self.result())
        self.assertEqual("not-reviewed", build_sidecar(session, "for-you", "for-you.png")["reviewStatus"])
        with self.assertRaises(PrivacyGateError): session.consume_capture().consume_capture()

    def test_expired_session_rejected(self):
        session = Session("x", "d", PACKAGE, "a", True, True, "s", "2026-01-01T00:00:00+00:00", (datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat())
        with self.assertRaises(PrivacyGateError): session.authorize_capture()

    def test_manifest_requires_reasons(self):
        with self.assertRaises(Exception): validate_manifest({"routes": [{"name": "x", "status": "blocked"}]})
        validate_manifest({"routes": [{"name": "x", "status": "planned", "capture": {"allowed": True}}]})

    def test_review_preserves_capture_facts(self):
        sidecar = {"routeName": "for-you", "package": PACKAGE, "reviewStatus": "not-reviewed"}
        reviewed = update_review(sidecar, "approved", "checked")
        self.assertEqual("for-you", reviewed["routeName"])
        self.assertEqual("approved", reviewed["reviewStatus"])

    def test_utf8_manifest_listing(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "routes.json"
            path.write_text(json.dumps({"routes": [{"name": "тест", "status": "planned"}]}, ensure_ascii=False), encoding="utf-8")
            output = io.StringIO()
            with redirect_stdout(output): main(["manifest", "list", str(path)])
            self.assertIn("тест", output.getvalue())


if __name__ == "__main__":
    unittest.main()
