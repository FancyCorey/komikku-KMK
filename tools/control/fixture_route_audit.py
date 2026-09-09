"""Reconcile every evidence route against canonical fixture readiness, fail closed."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from tools.capture_coordinator import load_json, validate_manifest, validate_manifest_fixture_catalog
from tools.evidence_fixture_registry import Fixture, load_fixtures

from .control_common import SOURCE_ROOT, WORKSPACE_ROOT, workspace_relative, write_json


DEFAULT_MANIFEST = SOURCE_ROOT / "tools" / "capture_routes.json"
DEFAULT_CATALOG = SOURCE_ROOT / "tools" / "evidence_fixtures.json"
DEFAULT_REPORT = (
    WORKSPACE_ROOT
    / "private"
    / "docs"
    / "audits-and-reports"
    / "KMK_A16_2_ROUTE_FIXTURE_READINESS_2026-08-14.json"
)
READINESS_LAYERS = (
    "hostContract",
    "appFixture",
    "deviceFixture",
    "cleanup",
    "privacy",
    "captureAuthorization",
)
READINESS_STATUSES = {"ready", "blocked", "not-applicable"}


def _sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest().upper()


def _display_path(path: Path) -> str:
    try:
        return workspace_relative(path)
    except ValueError:
        return str(path.resolve())


def _layer_status(fixture: Fixture | None, *, not_applicable: bool) -> dict[str, str]:
    if not_applicable:
        return {layer: "not-applicable" for layer in READINESS_LAYERS}
    if fixture is None:
        return {layer: "blocked" for layer in READINESS_LAYERS}
    layers = fixture.readiness_layers(WORKSPACE_ROOT)
    return {layer: "ready" if layers[layer] else "blocked" for layer in READINESS_LAYERS}


def _disposition(route: dict[str, Any], fixture: Fixture | None, readiness: dict[str, str]) -> str:
    if route["status"] == "not-applicable":
        return "NOT_APPLICABLE"
    if readiness["captureAuthorization"] == "ready":
        return "DEVICE_FIXTURE_READY"
    if fixture is None:
        return "EVIDENCE_PENDING"
    if fixture.target == "host-only" and readiness["hostContract"] == "ready":
        return "HOST_FIXTURE_ONLY"
    if readiness["appFixture"] == "blocked":
        return "MISSING_APP_FIXTURE"
    if readiness["deviceFixture"] == "blocked":
        return "DEVICE_VALIDATION_REQUIRED"
    return "CAPTURE_AUTHORIZATION_REQUIRED"


def _fixture_row(fixture: Fixture) -> dict[str, Any]:
    readiness = _layer_status(fixture, not_applicable=False)
    return {
        "route": fixture.route,
        "revision": fixture.revision,
        "owner": fixture.owner,
        "kind": fixture.kind,
        "target": fixture.target,
        "mutationBoundary": fixture.mutation_boundary,
        "checkpointIds": sorted(fixture.checkpoint_ids),
        "readiness": readiness,
        "externalEffect": fixture.rollback_evidence["externalEffect"],
        "claimsFullRollback": fixture.rollback_evidence["claimsFullRollback"],
        "artifactPath": fixture.artifact_path,
        "verificationPath": fixture.verification_path,
    }


def audit(manifest_path: Path = DEFAULT_MANIFEST, catalog_path: Path = DEFAULT_CATALOG) -> dict[str, Any]:
    manifest = load_json(manifest_path)
    validate_manifest(manifest)
    validate_manifest_fixture_catalog(manifest, catalog_path)
    fixtures = load_fixtures(catalog_path)
    fixture_map = {fixture.id: fixture for fixture in fixtures}

    referenced_fixture_ids = {
        fixture_id
        for route in manifest["routes"]
        if (fixture_id := route.get("fixtureId")) is not None
    }
    orphan_fixture_ids = sorted(set(fixture_map) - referenced_fixture_ids)
    if orphan_fixture_ids:
        raise ValueError(f"Catalog contains unreferenced fixtures: {orphan_fixture_ids}")

    route_rows: list[dict[str, Any]] = []
    for route in manifest["routes"]:
        fixture_id = route.get("fixtureId")
        fixture = fixture_map.get(fixture_id) if fixture_id else None
        not_applicable = route["status"] == "not-applicable"
        readiness = _layer_status(fixture, not_applicable=not_applicable)
        if readiness["captureAuthorization"] == "ready" or route.get("capture", {}).get("allowed"):
            raise ValueError(f"A16 route {route['name']} cannot authorize capture.")
        route_rows.append(
            {
                "name": route["name"],
                "status": route["status"],
                "blockerType": route.get("blockerType"),
                "blockerOwner": route.get("owner"),
                "blockingReason": route.get("blockingReason"),
                "exitCondition": route.get("exitCondition"),
                "cleanupMethod": route.get("cleanupMethod"),
                "fixtureId": fixture_id,
                "fixtureRevision": fixture.revision if fixture else None,
                "checkpointIds": list(route.get("checkpointIds", [])),
                "readiness": readiness,
                "disposition": _disposition(route, fixture, readiness),
                "captureAllowed": False,
            }
        )

    fixture_rows = {fixture.id: _fixture_row(fixture) for fixture in fixtures}
    return {
        "schemaVersion": 1,
        "domainId": "DOM-FIXTURE-ROUTE-EVIDENCE",
        "inputs": {
            "manifest": _display_path(manifest_path),
            "manifestSha256": _sha256(manifest_path),
            "catalog": _display_path(catalog_path),
            "catalogSha256": _sha256(catalog_path),
        },
        "summary": {
            "routeCount": len(route_rows),
            "fixtureCount": len(fixture_rows),
            "fixtureBackedRouteCount": len(referenced_fixture_ids),
            "deviceReadyRouteCount": sum(
                row["readiness"]["deviceFixture"] == "ready" for row in route_rows
            ),
            "captureAuthorizedRouteCount": 0,
            "routeCatalogDrift": False,
            "deviceEvidenceCreated": False,
        },
        "readinessLayers": list(READINESS_LAYERS),
        "fixtures": fixture_rows,
        "routes": route_rows,
    }


def write_private_report(path: Path, result: dict[str, Any]) -> None:
    private_root = (WORKSPACE_ROOT / "private").resolve()
    resolved = path.resolve()
    if resolved != private_root and private_root not in resolved.parents:
        raise ValueError("Fixture readiness reports must remain under private/.")
    write_json(resolved, result)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    result = audit(args.manifest, args.catalog)
    if args.output:
        write_private_report(args.output, result)
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
