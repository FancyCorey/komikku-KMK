import hashlib
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools.emulator_fixture import (
    DEFAULT_PACKAGE,
    DEFAULT_SERIAL,
    Adb,
    FixtureError,
    build_parser,
    checkpoint,
    fixture_dir,
    load_context,
    require_package,
    require_target,
    seed_local_cbz,
    storage_status,
    main,
    sha256,
    validate_remote_root,
    verify_cleanup,
    verify_local_cbz,
    write_receipt,
)
from tools.evidence_fixture_registry import FixtureRegistryError, load_fixtures


TOOLS = Path(__file__).parents[1]
CATALOG = TOOLS / "evidence_fixtures.json"
ROOT = "/storage/TEST/Download/KMK Fixture Root"
FIXTURE_ID = "reader-completion-prompt-local-chapter"
REVISION = "a16.1-r1"


class FakeRunner:
    def __init__(self):
        self.responses = {}
        self.calls = []

    def respond(self, args, returncode=0, stdout="", stderr=""):
        self.responses[tuple(args)] = subprocess.CompletedProcess(args, returncode, stdout, stderr)

    def __call__(self, args, **kwargs):
        self.calls.append(tuple(args))
        return self.responses.get(tuple(args), subprocess.CompletedProcess(args, 0, "", ""))


class EmulatorFixtureContractTest(unittest.TestCase):
    def test_unrelated_expired_fixture_does_not_block_selected_lifecycle(self):
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        selected = next(row for row in catalog["fixtures"] if row["id"] == FIXTURE_ID)
        expired = json.loads(json.dumps(selected))
        expired["id"] = "unrelated-expired"
        expired["lifecycle"]["deviceReady"] = True
        expired["lifecycle"]["expiresAt"] = "2000-01-01T00:00:00Z"
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "catalog.json"
            path.write_text(json.dumps({"schemaVersion": 2, "fixtures": [selected, expired]}), encoding="utf-8")
            self.assertEqual(FIXTURE_ID, load_fixtures(path, root=TOOLS.parent, fixture_id=FIXTURE_ID)[0].id)
            with self.assertRaises(FixtureRegistryError):
                load_fixtures(path, root=TOOLS.parent, fixture_id="unrelated-expired")
            with self.assertRaises(FixtureRegistryError):
                load_fixtures(path, root=TOOLS.parent)

    def test_storage_status_reports_actual_root_despite_unrelated_invalid_xml(self):
        prefs = '''<map><string name="cache">&#28;</string>
        <string name="__APP_STATE_storage_dir">content://com.android.externalstorage.documents/tree/primary%3ADownload%2FExisting%20Root/document/primary%3ADownload%2FExisting%20Root</string>
        <boolean name="evaluation_mode" value="true" /></map>'''
        result = storage_status(prefs, "/storage/emulated/0/Download/New Root")
        self.assertEqual("/storage/emulated/0/Download/Existing Root", result["adopted_root"])
        self.assertTrue(result["source_labels_masked"])
        self.assertFalse(result["matches_expected_root"])
        self.assertTrue(storage_status(prefs, result["adopted_root"])["matches_expected_root"])

    def test_storage_status_needs_no_fixture_mutation_arguments(self):
        self.assertEqual("storage-status", build_parser().parse_args(["storage-status"]).action)
        self.assertIsNone(storage_status("<map />")["adopted_root"])

    def test_mutation_still_rejects_missing_fixture_identity(self):
        self.assertEqual(2, main(["checkpoint"]))

    def context(self, action="checkpoint"):
        return load_context(CATALOG, FIXTURE_ID, REVISION, ROOT, action)

    def adb(self):
        runner = FakeRunner()
        return Adb(DEFAULT_SERIAL, "adb", runner), runner

    def test_only_exact_named_fixture_root_is_accepted(self):
        self.assertEqual(ROOT, validate_remote_root(ROOT + "/"))
        self.assertEqual(
            "/storage/emulated/0/Download/KMK Fixture Root",
            validate_remote_root("/storage/emulated/0/Download/KMK Fixture Root"),
        )
        self.assertEqual(
            "/storage/emulated/0/KomikkuFC",
            validate_remote_root("/storage/emulated/0/KomikkuFC"),
        )
        for invalid in (
            "/sdcard/Download/KMK Fixture Root",
            "/storage/TEST/KMK Fixture Root",
            "/storage/emulated/0/OtherApp",
            "/storage/TEST/Download/Other/KMK Fixture Root",
            "/storage/TEST/Download/KMK Fixture Root/../escape",
            "/storage/TEST;rm/Download/KMK Fixture Root",
        ):
            with self.subTest(invalid=invalid), self.assertRaises(FixtureError):
                validate_remote_root(invalid)

    def test_cli_accepts_explicit_shared_workspace_root(self):
        args = build_parser().parse_args([
            "--workspace-root",
            "C:/workspace",
            "--remote-root",
            "/storage/emulated/0/KomikkuFC",
            "--fixture-id",
            FIXTURE_ID,
            "--fixture-revision",
            REVISION,
            "--receipt",
            "receipt.json",
            "checkpoint",
        ])
        self.assertEqual(Path("C:/workspace"), args.workspace_root)

    def test_cli_accepts_explicit_source_root(self):
        args = build_parser().parse_args(["--source-root", "C:/workspace/komikku-source", "--remote-root", "/storage/emulated/0/KomikkuFC", "--fixture-id", FIXTURE_ID, "--fixture-revision", REVISION, "--receipt", "receipt.json", "checkpoint"])
        self.assertEqual(Path("C:/workspace/komikku-source"), args.source_root)

    def test_fixture_directory_is_exact_and_catalog_id_is_required(self):
        self.assertEqual(f"{ROOT}/local/{FIXTURE_ID}", fixture_dir(ROOT, FIXTURE_ID))
        for invalid in ("../escape", "reader/escape", "UPPER", "reader fixture", ""):
            with self.subTest(invalid=invalid), self.assertRaises(FixtureError):
                fixture_dir(ROOT, invalid)
        with self.assertRaises(FixtureError):
            load_context(CATALOG, "unknown-fixture", REVISION, ROOT, "checkpoint")
        with self.assertRaises(FixtureError):
            load_context(CATALOG, FIXTURE_ID, "stale-revision", ROOT, "checkpoint")

    def test_only_supported_fixture_type_and_actions_are_available(self):
        context = self.context("seed")
        self.assertEqual("local-cbz-import", context.fixture.seed["type"])
        for fixture_id in (
            "rated-collections-local-manga",
            "manga-detail-privacy-disposable-library",
        ):
            with self.subTest(fixture_id=fixture_id):
                supported = load_context(CATALOG, fixture_id, "a16.5b-r1", ROOT, "seed")
                self.assertEqual("local-cbz-import", supported.fixture.seed["type"])
        with self.assertRaises(FixtureError):
            load_context(
                CATALOG,
                "sources-to-try-disposable-extension",
                REVISION,
                ROOT,
                "seed",
            )
        with self.assertRaises(FixtureError):
            load_context(CATALOG, FIXTURE_ID, REVISION, ROOT, "delete")

    def test_extension_export_uses_exact_apk_staging_filename(self):
        context = load_context(
            CATALOG,
            "extension-package-private-export",
            "a16.5c-r1",
            ROOT,
            "seed",
        )
        self.assertEqual("local-apk-staging", context.fixture.seed["type"])
        self.assertEqual(
            f"{ROOT}/local/extension-package-private-export/fixture.apk",
            context.remote_file,
        )

    def test_target_and_package_checks_use_fake_transport(self):
        adb, runner = self.adb()
        runner.respond(
            ["adb", "devices", "-l"],
            stdout=f"List of devices attached\n{DEFAULT_SERIAL} device product:test\n",
        )
        runner.respond(["adb", "-s", DEFAULT_SERIAL, "get-state"], stdout="device\n")
        runner.respond(
            ["adb", "-s", DEFAULT_SERIAL, "shell", "pm", "path", DEFAULT_PACKAGE],
            stdout="package:/data/app/base.apk\n",
        )
        require_target(adb)
        require_package(adb, DEFAULT_PACKAGE)
        with self.assertRaises(FixtureError):
            require_package(adb, "other.package")

    def test_target_rejects_multiple_or_physical_devices(self):
        adb, runner = self.adb()
        runner.respond(
            ["adb", "devices", "-l"],
            stdout=f"List of devices attached\n{DEFAULT_SERIAL} device\nphysical device\n",
        )
        with self.assertRaises(FixtureError):
            require_target(adb)

    def test_checkpoint_reads_only_exact_named_file(self):
        adb, runner = self.adb()
        context = self.context()
        runner.respond(
            ["adb", "-s", DEFAULT_SERIAL, "shell", f"test -f '{context.remote_file}'"],
            returncode=1,
        )
        result = checkpoint(adb, context)
        self.assertFalse(result["present"])
        self.assertEqual("checkpoint", result["action"])
        self.assertNotIn("find", " ".join(" ".join(call) for call in runner.calls))

    def test_seed_is_idempotent_and_does_not_overwrite(self):
        with tempfile.TemporaryDirectory() as temp:
            archive = Path(temp) / "fixture.cbz"
            archive.write_bytes(b"fixture")
            digest = sha256(archive)
            context = self.context("seed")

            adb, runner = self.adb()
            runner.respond(
                ["adb", "-s", DEFAULT_SERIAL, "shell", f"test -f '{context.remote_file}'"],
                returncode=1,
            )
            runner.respond(
                ["adb", "-s", DEFAULT_SERIAL, "shell", f"sha256sum '{context.remote_file}'"],
                stdout=digest + "  chapter.cbz\n",
            )
            result = seed_local_cbz(adb, context, archive, digest)
            self.assertTrue(result["seeded"])
            self.assertIn(("adb", "-s", DEFAULT_SERIAL, "push", str(archive), context.remote_file), runner.calls)

            adb, runner = self.adb()
            runner.respond(
                ["adb", "-s", DEFAULT_SERIAL, "shell", f"test -f '{context.remote_file}'"],
                returncode=0,
            )
            runner.respond(
                ["adb", "-s", DEFAULT_SERIAL, "shell", f"sha256sum '{context.remote_file}'"],
                stdout=digest + "  chapter.cbz\n",
            )
            result = seed_local_cbz(adb, context, archive, digest)
            self.assertTrue(result["idempotentReuse"])
            self.assertFalse(any("push" in call for call in runner.calls))

            adb, runner = self.adb()
            runner.respond(
                ["adb", "-s", DEFAULT_SERIAL, "shell", f"test -f '{context.remote_file}'"],
                returncode=0,
            )
            runner.respond(
                ["adb", "-s", DEFAULT_SERIAL, "shell", f"sha256sum '{context.remote_file}'"],
                stdout="F" * 64 + "  chapter.cbz\n",
            )
            with self.assertRaises(FixtureError):
                seed_local_cbz(adb, context, archive, digest)
            self.assertFalse(any("push" in call for call in runner.calls))

    def test_verify_and_cleanup_are_read_only_and_machine_checkable(self):
        context = self.context("verify")
        digest = "A" * 64
        adb, runner = self.adb()
        runner.respond(
            ["adb", "-s", DEFAULT_SERIAL, "shell", f"test -f '{context.remote_file}'"],
            returncode=0,
        )
        runner.respond(
            ["adb", "-s", DEFAULT_SERIAL, "shell", f"sha256sum '{context.remote_file}'"],
            stdout=digest + "  chapter.cbz\n",
        )
        verified = verify_local_cbz(adb, context, digest, True)
        self.assertTrue(verified["verified"])

        cleanup_context = self.context("cleanup")
        adb, runner = self.adb()
        runner.respond(
            ["adb", "-s", DEFAULT_SERIAL, "shell", f"test -f '{cleanup_context.remote_file}'"],
            returncode=1,
        )
        cleaned = verify_cleanup(adb, cleanup_context)
        self.assertTrue(cleaned["cleanupVerified"])
        self.assertEqual([{"id": "named-fixture-absent", "status": "passed"}], cleaned["predicates"])
        self.assertEqual(cleaned, verify_cleanup(adb, cleanup_context))
        forbidden = " ".join(" ".join(call) for call in runner.calls)
        for command in ("rm ", "pm clear", "sqlite", "database"):
            self.assertNotIn(command, forbidden)

    def test_source_contains_no_destructive_device_or_database_primitive(self):
        source = (TOOLS / "emulator_fixture.py").read_text(encoding="utf-8")
        for primitive in (
            'shell("rm"',
            'shell("sqlite',
            '"pm", "clear"',
            ".unlink(",
            "os.remove(",
            "shell_command(",
        ):
            with self.subTest(primitive=primitive):
                self.assertNotIn(primitive, source)

    def test_receipts_are_immutable_but_identical_rewrites_are_idempotent(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "receipt.json"
            write_receipt(path, {"fixtureId": FIXTURE_ID, "status": "passed"})
            original = path.read_bytes()
            write_receipt(path, {"fixtureId": FIXTURE_ID, "status": "passed"})
            self.assertEqual(original, path.read_bytes())
            with self.assertRaises(FixtureError):
                write_receipt(path, {"fixtureId": FIXTURE_ID, "status": "failed"})

    def test_sha256_is_stable(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "fixture.cbz"
            path.write_bytes(b"fixture")
            self.assertEqual(hashlib.sha256(b"fixture").hexdigest().upper(), sha256(path))


if __name__ == "__main__":
    unittest.main()
