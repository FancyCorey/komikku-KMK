import http.client
import json
import tempfile
import threading
import unittest
import subprocess
from unittest.mock import patch
from pathlib import Path

from tools.sources_to_try_fixture import (
    FIXTURE_PACKAGES,
    PAGE_PNGS,
    FixtureArtifact,
    FixtureError,
    FixtureManifest,
    FixtureServer,
    file_sha256,
    load_manifest,
    manifest_json,
    parse_range,
    store_payload,
    run_tool,
)


class SourcesToTryFixtureTest(unittest.TestCase):
    def test_apk_inspection_is_bounded_and_does_not_retry_timeout(self):
        with patch("tools.sources_to_try_fixture.subprocess.run", side_effect=subprocess.TimeoutExpired("aapt2", 60)) as run:
            with self.assertRaises(subprocess.TimeoutExpired):
                run_tool(Path("aapt2.exe"), "dump", "badging", "fixture.apk")
        self.assertEqual(60, run.call_args.kwargs["timeout"])
        self.assertEqual(1, run.call_count)

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        root = Path(self.temp.name)
        self.artifacts = []
        for index, (package, source_id) in enumerate(FIXTURE_PACKAGES.items()):
            apk = root / f"fixture-{index}.apk"
            apk.write_bytes((package * 200).encode())
            self.artifacts.append(
                FixtureArtifact(package, source_id, str(apk), apk.stat().st_size, file_sha256(apk), "a" * 64, 1, "1.6.0")
            )
        self.manifest = FixtureManifest(1, "f" * 40, "success", tuple(self.artifacts))

    def tearDown(self):
        self.temp.cleanup()

    def request(self, server, method, path, headers=None):
        connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=2)
        connection.request(method, path, headers=headers or {})
        response = connection.getresponse()
        body = response.read()
        connection.close()
        return response, body

    def running_server(self, profile="success"):
        server = FixtureServer(("127.0.0.1", 0), self.manifest, profile)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        return server

    def test_store_payload_matches_production_field_contract(self):
        value = json.loads(store_payload(self.manifest, "http://127.0.0.1:1234"))
        self.assertEqual("a" * 64, value["signingKey"])
        self.assertEqual(set(FIXTURE_PACKAGES), {item["packageName"] for item in value["extensionList"]["extensions"]})
        for extension in value["extensionList"]["extensions"]:
            self.assertEqual("1.6", extension["extensionLib"])
            self.assertEqual("CONTENT_WARNING_SAFE", extension["contentWarning"])
            self.assertEqual(1, len(extension["sources"]))

    def test_server_allows_only_registered_paths_and_methods(self):
        server = self.running_server()
        self.assertEqual(200, self.request(server, "GET", "/index.json")[0].status)
        package = next(iter(FIXTURE_PACKAGES))
        self.assertEqual(200, self.request(server, "HEAD", f"/icons/{package}.png")[0].status)
        for path in ("/", "/missing", "/apks/unknown.apk", "/../index.json", "/%2e%2e/index.json"):
            self.assertEqual(404, self.request(server, "GET", path)[0].status)
        self.assertEqual(405, self.request(server, "POST", "/index.json")[0].status)

    def test_reader_pages_are_exact_allowlisted_pngs_with_head_and_range_support(self):
        server = self.running_server()
        path = "/pages/alpha/origin/chapter-2/page-3.png"

        response, body = self.request(server, "GET", path)
        self.assertEqual(200, response.status)
        self.assertEqual("image/png", response.getheader("Content-Type"))
        self.assertEqual(PAGE_PNGS[path], body)
        self.assertTrue(body.startswith(b"\x89PNG\r\n\x1a\n"))

        head, head_body = self.request(server, "HEAD", path)
        self.assertEqual(200, head.status)
        self.assertEqual(str(len(body)), head.getheader("Content-Length"))
        self.assertEqual(b"", head_body)

        ranged, ranged_body = self.request(server, "GET", path, {"Range": "bytes=1-8"})
        self.assertEqual(206, ranged.status)
        self.assertEqual("bytes 1-8/%d" % len(body), ranged.getheader("Content-Range"))
        self.assertEqual(body[1:9], ranged_body)

    def test_reader_pages_reject_foreign_paths_queries_and_methods(self):
        server = self.running_server()
        allowed = "/pages/beta/target/chapter-4/page-3.png"
        self.assertEqual(200, self.request(server, "GET", allowed)[0].status)
        for path in (
            "/pages/alpha/origin/chapter-4/page-1.png",
            "/pages/beta/target/chapter-5/page-1.png",
            "/pages/beta/target/chapter-4/page-4.png",
            "/pages/alpha/origin/chapter-1/page-1.jpg",
            allowed + "?token=private",
        ):
            self.assertEqual(404, self.request(server, "GET", path)[0].status)
        self.assertEqual(405, self.request(server, "POST", allowed)[0].status)

    def test_reader_page_failure_profiles_are_truthful(self):
        path = "/pages/alpha/origin/chapter-1/page-1.png"
        error_server = self.running_server("http_error")
        self.assertEqual(503, self.request(error_server, "GET", path)[0].status)

        truncate_server = self.running_server("truncate")
        connection = http.client.HTTPConnection("127.0.0.1", truncate_server.server_port, timeout=2)
        connection.request("GET", path)
        response = connection.getresponse()
        self.assertEqual(len(PAGE_PNGS[path]), int(response.getheader("Content-Length")))
        with self.assertRaises(http.client.IncompleteRead):
            response.read()
        connection.close()

        slow_server = self.running_server("slow")
        response, body = self.request(slow_server, "GET", path)
        self.assertEqual(200, response.status)
        self.assertEqual(PAGE_PNGS[path], body)

    def test_apk_range_has_exact_length_and_content_range(self):
        server = self.running_server()
        artifact = self.artifacts[0]
        response, body = self.request(server, "GET", f"/apks/{artifact.package_name}.apk", {"Range": "bytes=2-9"})
        self.assertEqual(206, response.status)
        self.assertEqual("bytes 2-9/%d" % artifact.byte_count, response.getheader("Content-Range"))
        self.assertEqual(8, len(body))
        self.assertEqual((2, 9), parse_range("bytes=2-9", artifact.byte_count))
        with self.assertRaises(FixtureError):
            parse_range("items=0-2", artifact.byte_count)

        index_response, index_body = self.request(server, "GET", "/index.json", {"Range": "bytes=0-9"})
        self.assertEqual(206, index_response.status)
        self.assertEqual(10, len(index_body))
        self.assertRegex(index_response.getheader("Content-Range"), r"bytes 0-9/\d+")

    def test_failure_and_truncation_profiles_are_truthful(self):
        artifact = self.artifacts[0]
        error_server = self.running_server("http_error")
        self.assertEqual(503, self.request(error_server, "GET", f"/apks/{artifact.package_name}.apk")[0].status)
        truncate_server = self.running_server("truncate")
        connection = http.client.HTTPConnection("127.0.0.1", truncate_server.server_port, timeout=2)
        connection.request("GET", f"/apks/{artifact.package_name}.apk")
        response = connection.getresponse()
        self.assertEqual(artifact.byte_count, int(response.getheader("Content-Length")))
        with self.assertRaises(http.client.IncompleteRead):
            response.read()
        connection.close()

    def test_slow_transfer_tolerates_client_cancellation(self):
        artifact = self.artifacts[0]
        server = self.running_server("slow")
        connection = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=2)
        connection.request("GET", f"/apks/{artifact.package_name}.apk")
        response = connection.getresponse()
        self.assertEqual(200, response.status)
        self.assertTrue(response.read(128))
        connection.close()
        self.assertEqual(200, self.request(server, "GET", "/index.json")[0].status)

    def test_manifest_rejects_changed_artifact(self):
        path = Path(self.temp.name) / "manifest.json"
        path.write_text(manifest_json(self.manifest), encoding="utf-8")
        self.assertEqual(self.manifest, load_manifest(path))
        Path(self.artifacts[0].apk_path).write_bytes(b"changed")
        with self.assertRaises(FixtureError):
            load_manifest(path)

    def test_relative_manifest_artifact_paths_resolve_from_manifest_directory(self):
        artifact = self.artifacts[0]
        relative = FixtureArtifact(
            artifact.package_name,
            artifact.source_id,
            Path(artifact.apk_path).name,
            artifact.byte_count,
            artifact.sha256,
            artifact.signer_sha256,
            artifact.version_code,
            artifact.version_name,
        )
        manifest = FixtureManifest(1, "f" * 40, "success", (relative,))
        path = Path(self.temp.name) / "portable.json"
        path.write_text(manifest_json(manifest), encoding="utf-8")

        loaded = load_manifest(path)

        self.assertEqual(Path(artifact.apk_path).resolve(), Path(loaded.artifacts[0].apk_path))

    def test_non_loopback_bind_is_rejected(self):
        with self.assertRaises(FixtureError):
            FixtureServer(("0.0.0.0", 0), self.manifest, "success")


if __name__ == "__main__":
    unittest.main()
