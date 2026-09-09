#!/usr/bin/env python3
"""Manage exact, catalog-backed fixtures on an isolated emulator.

The helper never clears app data, edits a database, deletes device files, or
handles credentials. Cleanup is performed through supported Android UI; this
module only verifies the named fixture's post-cleanup predicates.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shlex
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Protocol
from urllib.parse import unquote, urlparse
import xml.etree.ElementTree as ET

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from tools.evidence_fixture_registry import Fixture, FixtureRegistryError, load_fixtures
from tools.android_ui import UiError, find_adb


DEFAULT_SERIAL = "emulator-5554"
DEFAULT_PACKAGE = "app.komikku.dev"
DEFAULT_ADB = "adb"
DEFAULT_CATALOG = Path(__file__).with_name("evidence_fixtures.json")
ROOT_PATTERN = re.compile(
    r"^/storage/(?:emulated/[0-9]+|[A-Za-z0-9._-]+)/Download/KMK Fixture Root$"
)
CONFIGURED_BASE_PATTERN = re.compile(r"^/storage/emulated/[0-9]+/KomikkuFC$")
FIXTURE_ID_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
SUPPORTED_LIFECYCLES = {
    "local-cbz-import": {"checkpoint", "seed", "verify", "cleanup"},
    "local-apk-staging": {"checkpoint", "seed", "verify", "cleanup"},
}
REMOTE_FILENAMES = {
    "local-cbz-import": "chapter.cbz",
    "local-apk-staging": "fixture.apk",
}


class FixtureError(RuntimeError):
    pass


class CompletedProcess(Protocol):
    returncode: int
    stdout: str
    stderr: str


ProcessRunner = Callable[..., CompletedProcess]


class Adb:
    def __init__(self, serial: str, adb: str, runner: ProcessRunner = subprocess.run):
        if serial != DEFAULT_SERIAL:
            raise FixtureError(f"This helper accepts only the isolated {DEFAULT_SERIAL} target.")
        self.serial = serial
        self.adb = adb
        self._runner = runner

    def invoke(self, *args: str) -> CompletedProcess:
        return self._runner(
            [self.adb, "-s", self.serial, *args],
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            timeout=60,
        )

    def host_invoke(self, *args: str) -> CompletedProcess:
        return self._runner([self.adb, *args], check=False, capture_output=True, text=True, encoding="utf-8", timeout=30)

    def run(self, *args: str) -> str:
        result = self.invoke(*args)
        if result.returncode:
            raise FixtureError(f"ADB failed: {(result.stderr or result.stdout).strip()}")
        return result.stdout

    def shell(self, *args: str) -> str:
        return self.run("shell", *args)

    def file_exists(self, remote_file: str) -> bool:
        result = self.invoke("shell", f"test -f {shlex.quote(remote_file)}")
        if result.returncode not in {0, 1}:
            raise FixtureError(f"ADB file probe failed: {(result.stderr or result.stdout).strip()}")
        return result.returncode == 0


@dataclass(frozen=True)
class FixtureContext:
    fixture: Fixture
    remote_root: str
    remote_directory: str
    remote_file: str


def require_target(adb: Adb) -> None:
    raw = adb.host_invoke("devices", "-l")
    if raw.returncode:
        raise FixtureError(raw.stderr.strip() or "adb devices failed")
    rows = [line for line in raw.stdout.splitlines() if line.strip() and not line.startswith("List of devices")]
    online = [line.split()[0] for line in rows if len(line.split()) > 1 and line.split()[1] == "device"]
    if online != [DEFAULT_SERIAL]:
        raise FixtureError(f"Expected only {DEFAULT_SERIAL} as an authorized target; found {online!r}")
    state = adb.run("get-state").strip()
    if state != "device":
        raise FixtureError(f"Target state is {state!r}, not device")


def validate_remote_root(remote_root: str) -> str:
    if not isinstance(remote_root, str):
        raise FixtureError("remote-root must be text")
    normalized = remote_root.rstrip("/")
    allowed = ROOT_PATTERN.fullmatch(normalized) or CONFIGURED_BASE_PATTERN.fullmatch(normalized)
    if not allowed or any(part in {".", ".."} for part in normalized.split("/")):
        raise FixtureError(
            "remote-root must be the exact named KMK Fixture Root under Download or the approved /storage/emulated/*/KomikkuFC base"
        )
    return normalized


def validate_fixture_id(fixture_id: str) -> str:
    if not isinstance(fixture_id, str) or not FIXTURE_ID_PATTERN.fullmatch(fixture_id):
        raise FixtureError("fixture-id must be one lowercase hyphenated catalog id")
    return fixture_id


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def require_sha256(value: str) -> str:
    normalized = value.upper() if isinstance(value, str) else ""
    if len(normalized) != 64 or any(character not in "0123456789ABCDEF" for character in normalized):
        raise FixtureError("Expected SHA-256 must contain 64 hexadecimal characters")
    return normalized


def require_package(adb: Adb, package: str) -> None:
    if package != DEFAULT_PACKAGE:
        raise FixtureError(f"Fixture helper package must be {DEFAULT_PACKAGE}")
    output = adb.shell("pm", "path", package)
    if not any(line.startswith("package:") for line in output.splitlines()):
        raise FixtureError(f"Expected installed package {package}")


def fixture_dir(remote_root: str, fixture_id: str) -> str:
    return f"{validate_remote_root(remote_root)}/local/{validate_fixture_id(fixture_id)}"


def load_context(
    catalog_path: Path,
    fixture_id: str,
    fixture_revision: str,
    remote_root: str,
    action: str,
    workspace_root: Path | None = None,
    source_root: Path | None = None,
) -> FixtureContext:
    fixture_id = validate_fixture_id(fixture_id)
    try:
        fixtures = {
            fixture.id: fixture
            for fixture in load_fixtures(catalog_path, root=workspace_root, source_root=source_root, fixture_id=fixture_id)
        }
    except FixtureRegistryError as exc:
        raise FixtureError(str(exc)) from exc
    fixture = fixtures.get(fixture_id)
    if fixture is None:
        raise FixtureError(f"Unknown fixture id: {fixture_id}")
    if fixture.revision != fixture_revision:
        raise FixtureError(
            f"Fixture revision mismatch: expected {fixture.revision}, received {fixture_revision}"
        )
    seed_type = fixture.seed["type"]
    if not fixture.seed["supported"] or action not in SUPPORTED_LIFECYCLES.get(seed_type, set()):
        raise FixtureError(f"Fixture {fixture_id} does not support lifecycle action {action}")
    if fixture.target != "emulator" or fixture.mutation_boundary != "isolated":
        raise FixtureError(f"Fixture {fixture_id} is not an isolated emulator fixture")
    remote_root = validate_remote_root(remote_root)
    remote_directory = fixture_dir(remote_root, fixture_id)
    return FixtureContext(
        fixture=fixture,
        remote_root=remote_root,
        remote_directory=remote_directory,
        remote_file=f"{remote_directory}/{REMOTE_FILENAMES[seed_type]}",
    )


def device_file_hash(adb: Adb, remote_file: str) -> str:
    output = adb.shell(f"sha256sum {shlex.quote(remote_file)}").split()
    if not output:
        raise FixtureError("Device hash command returned no digest")
    return require_sha256(output[0])


def receipt(context: FixtureContext, action: str, **values: Any) -> dict[str, Any]:
    return {
        "schemaVersion": 2,
        "action": action,
        "target": DEFAULT_SERIAL,
        "package": DEFAULT_PACKAGE,
        "fixtureId": context.fixture.id,
        "fixtureRevision": context.fixture.revision,
        "remoteRootSha256": hashlib.sha256(context.remote_root.encode()).hexdigest().upper(),
        "fixtureDirectorySha256": hashlib.sha256(context.remote_directory.encode()).hexdigest().upper(),
        "cleanupContract": "Supported Android UI only; helper performs verification and no deletion.",
        **values,
    }


def checkpoint(adb: Adb, context: FixtureContext) -> dict[str, Any]:
    present = adb.file_exists(context.remote_file)
    return receipt(
        context,
        "checkpoint",
        present=present,
        archiveSha256=device_file_hash(adb, context.remote_file) if present else None,
    )


def seed_local_cbz(
    adb: Adb,
    context: FixtureContext,
    archive: Path,
    expected_sha256: str,
) -> dict[str, Any]:
    if not archive.is_file():
        raise FixtureError(f"Fixture archive does not exist: {archive}")
    expected = require_sha256(expected_sha256)
    actual = sha256(archive)
    if actual != expected:
        raise FixtureError(f"Fixture SHA-256 mismatch: expected {expected}, got {actual}")
    if adb.file_exists(context.remote_file):
        observed = device_file_hash(adb, context.remote_file)
        if observed != actual:
            raise FixtureError("Refusing to overwrite a different file in the named fixture directory")
        return receipt(context, "seed", present=True, archiveSha256=actual, seeded=False, idempotentReuse=True)
    adb.shell(f"mkdir -p {shlex.quote(context.remote_directory)}")
    adb.run("push", str(archive), context.remote_file)
    observed = device_file_hash(adb, context.remote_file)
    if observed != actual:
        raise FixtureError(f"Device fixture SHA-256 mismatch: expected {actual}, got {observed}")
    return receipt(context, "seed", present=True, archiveSha256=actual, seeded=True, idempotentReuse=False)


def verify_local_cbz(
    adb: Adb,
    context: FixtureContext,
    expected_sha256: str,
    present: bool,
) -> dict[str, Any]:
    expected = require_sha256(expected_sha256)
    exists = adb.file_exists(context.remote_file)
    if exists != present:
        raise FixtureError(f"Expected fixture file present={present}, observed present={exists}")
    observed = device_file_hash(adb, context.remote_file) if exists else None
    if observed is not None and observed != expected:
        raise FixtureError(f"Device fixture SHA-256 mismatch: expected {expected}, got {observed}")
    return receipt(context, "verify", present=exists, archiveSha256=observed, verified=True)


def verify_cleanup(adb: Adb, context: FixtureContext) -> dict[str, Any]:
    if adb.file_exists(context.remote_file):
        raise FixtureError("Named fixture remains present; complete cleanup through supported Android UI")
    return receipt(
        context,
        "cleanup",
        cleanupVerified=True,
        predicates=[{"id": "named-fixture-absent", "status": "passed"}],
    )


def write_receipt(path: Path, value: dict[str, Any]) -> None:
    encoded = json.dumps(value, indent=2, sort_keys=True) + "\n"
    if path.exists():
        try:
            current = path.read_text(encoding="utf-8")
        except OSError as exc:
            raise FixtureError(f"Could not read existing receipt {path}: {exc}") from exc
        if current != encoded:
            raise FixtureError(f"Refusing to overwrite a different fixture receipt: {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(encoded, encoding="utf-8")


def storage_status(preferences: str, expected_root: str | None = None) -> dict:
    # Unrelated Android preference caches may contain XML-1.0-invalid entities.
    # Isolate the scalar elements before using an XML parser.
    def scalar(name: str) -> ET.Element | None:
        pattern = rf'<(?:string|boolean)\s+name="{re.escape(name)}"(?:\s[^>]*?/\s*>|>.*?</string>)'
        match = re.search(pattern, preferences, re.DOTALL)
        return ET.fromstring(match.group()) if match else None

    storage = scalar("__APP_STATE_storage_dir")
    uri = storage.text if storage is not None else None
    adopted_root = None
    if uri:
        parsed = urlparse(uri)
        parts = parsed.path.split("/")
        if parsed.netloc == "com.android.externalstorage.documents" and "tree" in parts:
            index = parts.index("tree") + 1
            document_id = unquote(parts[index]) if index < len(parts) else ""
            volume, separator, relative = document_id.partition(":")
            if separator and volume == "primary" and ".." not in relative.split("/"):
                adopted_root = "/storage/emulated/0/" + relative.strip("/")
    evaluation = scalar("evaluation_mode")
    return {
        "storage_uri": uri,
        "adopted_root": adopted_root,
        "expected_root": expected_root,
        "matches_expected_root": (
            adopted_root.rstrip("/") == expected_root.rstrip("/")
            if adopted_root is not None and expected_root is not None else None
        ),
        "source_labels_masked": evaluation is not None and evaluation.get("value") == "true",
        "boundary": "Preference identity only; archive hash and successful in-app reading are separate proofs.",
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=DEFAULT_SERIAL)
    parser.add_argument("--adb", default=os.environ.get("ADB_PATH"))
    parser.add_argument("--package", default=DEFAULT_PACKAGE)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--workspace-root", type=Path, default=None)
    parser.add_argument("--source-root", type=Path, default=None)
    parser.add_argument("--remote-root")
    parser.add_argument("--fixture-id")
    parser.add_argument("--fixture-revision")
    parser.add_argument("--receipt", type=Path)
    sub = parser.add_subparsers(dest="action", required=True)
    sub.add_parser("storage-status", help="Read adopted storage and source-label masking without modifying the profile")
    sub.add_parser("checkpoint")
    seed = sub.add_parser("seed")
    seed.add_argument("--archive", type=Path, required=True)
    seed.add_argument("--sha256", required=True)
    verify = sub.add_parser("verify")
    verify.add_argument("--sha256", required=True)
    verify.add_argument("--absent", action="store_true")
    sub.add_parser("cleanup")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        if args.action == "storage-status":
            adb = Adb(args.serial, args.adb or find_adb())
            require_target(adb)
            require_package(adb, args.package)
            prefs = shlex.quote(f"shared_prefs/{args.package}_preferences.xml")
            result = storage_status(adb.shell(f"run-as {shlex.quote(args.package)} cat {prefs}"), args.remote_root)
            if args.receipt:
                write_receipt(args.receipt, result)
            print(json.dumps(result, sort_keys=True))
            return 2 if result["matches_expected_root"] is False or not result["storage_uri"] else 0
        if not all((args.remote_root, args.fixture_id, args.fixture_revision, args.receipt)):
            raise FixtureError("File operations require --remote-root, --fixture-id, --fixture-revision and --receipt")
        context = load_context(
            args.catalog,
            args.fixture_id,
            args.fixture_revision,
            args.remote_root,
            args.action,
            args.workspace_root,
            args.source_root,
        )
        adb = Adb(args.serial, args.adb or find_adb())
        require_target(adb)
        require_package(adb, args.package)
        if args.action == "checkpoint":
            result = checkpoint(adb, context)
        elif args.action == "seed":
            result = seed_local_cbz(adb, context, args.archive, args.sha256)
        elif args.action == "verify":
            result = verify_local_cbz(adb, context, args.sha256, not args.absent)
        else:
            result = verify_cleanup(adb, context)
        write_receipt(args.receipt, result)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (FixtureError, UiError, OSError, ValueError, subprocess.TimeoutExpired, ET.ParseError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
