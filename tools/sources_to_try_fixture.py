"""Deterministic loopback store for inert Sources To Try fixture extensions."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import subprocess
import time
import zlib
from dataclasses import asdict, dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit


FIXTURE_PACKAGES = {
    "app.komikku.fixture.sources.alpha": 910000000000000001,
    "app.komikku.fixture.sources.beta": 910000000000000002,
}
ICON_PNG = bytes.fromhex(
    "89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C489"
    "0000000D49444154789C6360F8CFC000000301010018DD8DB10000000049454E44AE426082"
)


def make_page_png(red: int, green: int, blue: int, width: int = 48, height: int = 72) -> bytes:
    """Build one small deterministic RGB page without external image dependencies."""
    signature = b"\x89PNG\r\n\x1a\n"

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))

    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            stripe = 24 if (x + y) % 17 < 3 else 0
            rows.extend((min(255, red + stripe), min(255, green + stripe), min(255, blue + stripe)))
    return signature + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) + \
        chunk(b"IDAT", zlib.compress(bytes(rows), 9)) + chunk(b"IEND", b"")


def build_page_payloads() -> dict[str, bytes]:
    pages: dict[str, bytes] = {}
    sources = (("alpha/origin", range(1, 4), (128, 32, 48)), ("beta/target", range(1, 5), (32, 72, 128)))
    for source_index, (source_path, chapters, base_color) in enumerate(sources):
        for chapter in chapters:
            for page in range(1, 4):
                color = tuple(min(220, channel + chapter * 9 + page * 5 + source_index * 3) for channel in base_color)
                pages[f"/pages/{source_path}/chapter-{chapter}/page-{page}.png"] = make_page_png(*color)
    return pages


PAGE_PNGS = build_page_payloads()
PACKAGE_LINE = re.compile(
    r"package: name='(?P<package>[^']+)' versionCode='(?P<code>\d+)' versionName='(?P<name>[^']+)'"
)
SIGNER_LINE = re.compile(r"Signer #1 certificate SHA-256 digest: (?P<digest>[0-9a-fA-F]+)")


class FixtureError(RuntimeError):
    pass


def run_tool(tool: Path, *args: str) -> str:
    command = [str(tool), *args]
    if tool.suffix.lower() in {".bat", ".cmd"}:
        command = ["cmd.exe", "/d", "/c", str(tool), *args]
    return subprocess.run(
        command,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        timeout=60,
    ).stdout


@dataclass(frozen=True)
class FixtureArtifact:
    package_name: str
    source_id: int
    apk_path: str
    byte_count: int
    sha256: str
    signer_sha256: str
    version_code: int
    version_name: str


@dataclass(frozen=True)
class FixtureManifest:
    schema_version: int
    source_commit: str
    server_profile: str
    artifacts: tuple[FixtureArtifact, ...]


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def inspect_apk(apk: Path, aapt2: Path, apksigner: Path) -> FixtureArtifact:
    if not apk.is_file():
        raise FixtureError(f"Missing fixture APK: {apk}")
    badging = run_tool(aapt2, "dump", "badging", str(apk))
    package_match = PACKAGE_LINE.search(badging)
    if package_match is None:
        raise FixtureError(f"Could not inspect package metadata: {apk}")
    package_name = package_match.group("package")
    source_id = FIXTURE_PACKAGES.get(package_name)
    if source_id is None:
        raise FixtureError(f"Unexpected fixture package: {package_name}")
    if not any(
        marker in badging
        for marker in (
            "uses-feature: name='tachiyomi.extension'",
            "uses-feature-not-required: name='tachiyomi.extension'",
        )
    ):
        raise FixtureError(f"Fixture feature marker missing: {package_name}")
    if "uses-permission: name='android.permission.INTERNET'" in badging:
        raise FixtureError(f"Fixture must not request INTERNET: {package_name}")

    signer_output = run_tool(apksigner, "verify", "--print-certs", str(apk))
    signer_match = SIGNER_LINE.search(signer_output)
    if signer_match is None:
        raise FixtureError(f"Could not inspect signer: {apk}")
    return FixtureArtifact(
        package_name=package_name,
        source_id=source_id,
        apk_path=str(apk.resolve()),
        byte_count=apk.stat().st_size,
        sha256=file_sha256(apk),
        signer_sha256=signer_match.group("digest").lower(),
        version_code=int(package_match.group("code")),
        version_name=package_match.group("name"),
    )


def build_manifest(
    apks: list[Path],
    aapt2: Path,
    apksigner: Path,
    source_commit: str,
    profile: str,
) -> FixtureManifest:
    artifacts = tuple(sorted((inspect_apk(apk, aapt2, apksigner) for apk in apks), key=lambda it: it.package_name))
    if set(item.package_name for item in artifacts) != set(FIXTURE_PACKAGES):
        raise FixtureError("Manifest requires exactly the alpha and beta fixture packages")
    signer_digests = {item.signer_sha256 for item in artifacts}
    if len(signer_digests) != 1:
        raise FixtureError("Fixture APKs must use one fixture-only debug signer")
    return FixtureManifest(1, source_commit, profile, artifacts)


def manifest_json(manifest: FixtureManifest) -> str:
    value = asdict(manifest)
    value["artifacts"] = [asdict(item) for item in manifest.artifacts]
    return json.dumps(value, indent=2, sort_keys=True) + "\n"


def load_manifest(path: Path) -> FixtureManifest:
    value = json.loads(path.read_text(encoding="utf-8"))
    artifacts = tuple(
        FixtureArtifact(
            **{
                **item,
                "apk_path": str(
                    (path.parent / item["apk_path"]).resolve()
                    if not Path(item["apk_path"]).is_absolute()
                    else Path(item["apk_path"]).resolve()
                ),
            },
        )
        for item in value["artifacts"]
    )
    manifest = FixtureManifest(value["schema_version"], value["source_commit"], value["server_profile"], artifacts)
    for artifact in artifacts:
        apk = Path(artifact.apk_path)
        if not apk.is_file() or apk.stat().st_size != artifact.byte_count or file_sha256(apk) != artifact.sha256:
            raise FixtureError(f"Fixture artifact no longer matches manifest: {artifact.package_name}")
    return manifest


def store_payload(manifest: FixtureManifest, base_url: str) -> bytes:
    signer = {item.signer_sha256 for item in manifest.artifacts}
    if len(signer) != 1:
        raise FixtureError("Store requires one signer digest")
    extensions = []
    for item in manifest.artifacts:
        label = "Fixture Source Alpha" if item.package_name.endswith(".alpha") else "Fixture Source Beta"
        extensions.append(
            {
                "name": label,
                "packageName": item.package_name,
                "resources": {
                    "apkUrl": f"{base_url}/apks/{item.package_name}.apk",
                    "iconUrl": f"{base_url}/icons/{item.package_name}.png",
                },
                "extensionLib": "1.6",
                "versionCode": item.version_code,
                "versionName": item.version_name,
                "contentWarning": "CONTENT_WARNING_SAFE",
                "sources": [
                    {
                        "id": item.source_id,
                        "name": label,
                        "language": "en",
                        "homeUrl": "",
                        "mirrorUrls": [],
                    }
                ],
            }
        )
    return json.dumps(
        {
            "name": "KMK Local Fixture Store",
            "badgeLabel": "Fixture",
            "signingKey": next(iter(signer)),
            "contact": {"website": base_url, "discord": None},
            "extensionList": {"extensions": extensions},
            "extensionListUrl": None,
        },
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")


def parse_range(value: str | None, length: int) -> tuple[int, int] | None:
    if value is None:
        return None
    match = re.fullmatch(r"bytes=(\d+)-(\d*)", value)
    if match is None:
        raise FixtureError("Unsupported Range header")
    start = int(match.group(1))
    end = int(match.group(2)) if match.group(2) else length - 1
    if start >= length or end < start:
        raise FixtureError("Unsatisfiable Range header")
    return start, min(end, length - 1)


class FixtureServer(ThreadingHTTPServer):
    def __init__(self, address: tuple[str, int], manifest: FixtureManifest, profile: str):
        if address[0] not in {"127.0.0.1", "::1", "localhost"}:
            raise FixtureError("Fixture server may bind only to loopback")
        if profile not in {"success", "http_error", "truncate", "slow"}:
            raise FixtureError(f"Unknown server profile: {profile}")
        self.manifest = manifest
        self.profile = profile
        super().__init__(address, FixtureRequestHandler)


class FixtureRequestHandler(BaseHTTPRequestHandler):
    server: FixtureServer

    def do_HEAD(self) -> None:
        self._serve(False)

    def do_GET(self) -> None:
        self._serve(True)

    def do_POST(self) -> None:
        self.send_error(405)

    def _serve(self, include_body: bool) -> None:
        parsed = urlsplit(self.path)
        if parsed.query or parsed.fragment or ".." in parsed.path or "%" in parsed.path:
            self.send_error(404)
            return
        base_url = f"http://127.0.0.1:{self.server.server_port}"
        content_type = "application/octet-stream"
        if parsed.path == "/index.json":
            body = store_payload(self.server.manifest, base_url)
            content_type = "application/json"
        elif parsed.path.startswith("/icons/"):
            package = parsed.path.removeprefix("/icons/").removesuffix(".png")
            if package not in FIXTURE_PACKAGES or parsed.path != f"/icons/{package}.png":
                self.send_error(404)
                return
            body = ICON_PNG
            content_type = "image/png"
        elif parsed.path.startswith("/apks/"):
            package = parsed.path.removeprefix("/apks/").removesuffix(".apk")
            artifact = next((it for it in self.server.manifest.artifacts if it.package_name == package), None)
            if artifact is None or parsed.path != f"/apks/{package}.apk":
                self.send_error(404)
                return
            if self.server.profile == "http_error":
                self.send_error(503)
                return
            body = Path(artifact.apk_path).read_bytes()
        elif parsed.path in PAGE_PNGS:
            if self.server.profile == "http_error":
                self.send_error(503)
                return
            body = PAGE_PNGS[parsed.path]
            content_type = "image/png"
        else:
            self.send_error(404)
            return

        try:
            requested = parse_range(self.headers.get("Range"), len(body))
        except FixtureError:
            self.send_error(416)
            return
        status = 200
        total_length = len(body)
        if requested is not None:
            start, end = requested
            status = 206
            body = body[start : end + 1]
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Accept-Ranges", "bytes")
        if requested is not None:
            self.send_header("Content-Range", f"bytes {start}-{end}/{total_length}")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        if not include_body:
            return
        is_binary_fixture = parsed.path.startswith("/apks/") or parsed.path in PAGE_PNGS
        sent_body = body[: max(1, len(body) // 2)] if self.server.profile == "truncate" and is_binary_fixture else body
        try:
            if self.server.profile == "slow" and is_binary_fixture:
                for offset in range(0, len(sent_body), 1024):
                    self.wfile.write(sent_body[offset : offset + 1024])
                    self.wfile.flush()
                    time.sleep(0.05)
            else:
                self.wfile.write(sent_body)
        except (BrokenPipeError, ConnectionAbortedError, ConnectionResetError):
            pass

    def log_message(self, _format: str, *_args: Any) -> None:
        return


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    generate = sub.add_parser("generate")
    generate.add_argument("--apk", action="append", type=Path, required=True)
    generate.add_argument("--aapt2", type=Path, required=True)
    generate.add_argument("--apksigner", type=Path, required=True)
    generate.add_argument("--source-commit", required=True)
    generate.add_argument("--profile", default="success")
    generate.add_argument("--output", type=Path, required=True)
    serve = sub.add_parser("serve")
    serve.add_argument("--manifest", type=Path, required=True)
    serve.add_argument("--profile", default="success")
    serve.add_argument("--port", type=int, default=0)
    args = parser.parse_args(argv)
    if args.action == "generate":
        manifest = build_manifest(args.apk, args.aapt2, args.apksigner, args.source_commit, args.profile)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        portable_artifacts = tuple(
            FixtureArtifact(
                **{
                    **asdict(artifact),
                    "apk_path": Path(artifact.apk_path).resolve().relative_to(args.output.parent.resolve()).as_posix(),
                },
            )
            for artifact in manifest.artifacts
        )
        args.output.write_text(
            manifest_json(
                FixtureManifest(
                    manifest.schema_version,
                    manifest.source_commit,
                    manifest.server_profile,
                    portable_artifacts,
                ),
            ),
            encoding="utf-8",
        )
        return 0
    manifest = load_manifest(args.manifest)
    server = FixtureServer(("127.0.0.1", args.port), manifest, args.profile)
    print(f"http://127.0.0.1:{server.server_port}/index.json", flush=True)
    server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
