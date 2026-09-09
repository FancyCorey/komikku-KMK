"""Validate a closure certificate against live fingerprint-bound state."""

from __future__ import annotations

from pathlib import Path
from typing import Any

from .control_common import CONTROL_ROOT, STATE_ROOT, dependency_fingerprint_map, fingerprint_domain, read_json
from .validate_evidence import HEX64, resolve_positive_evidence


def validate_certificate(
    path: Path,
    domain: dict[str, Any],
    state: dict[str, Any],
    evidence_root: Path = CONTROL_ROOT / "evidence",
    attempts_root: Path = CONTROL_ROOT / "attempts",
    index_path: Path | None = None,
) -> list[str]:
    certificate = read_json(path)
    domain_id = domain["id"]
    errors: list[str] = []
    expected_id = f"CERT-{domain_id}"
    if certificate.get("certificate_id") != expected_id:
        errors.append(f"{path.name}: certificate_id must be {expected_id}")
    if path.stem != certificate.get("certificate_id"):
        errors.append(f"{path.name}: filename must match certificate_id")
    if certificate.get("schema_version") != "1.0.0":
        errors.append(f"{path.name}: unsupported schema_version")
    if certificate.get("domain_id") != domain_id:
        errors.append(f"{path.name}: domain_id mismatch")
    if certificate.get("issued_by") != "tools.control.issue_certificate":
        errors.append(f"{path.name}: certificate was not issued by controller tooling")
    fingerprint, _ = fingerprint_domain(domain)
    issued_for = certificate.get("issued_for_fingerprint")
    if not isinstance(issued_for, str) or not HEX64.fullmatch(issued_for):
        errors.append(f"{path.name}: issued_for_fingerprint must be sha256 hex")
    elif issued_for != fingerprint:
        errors.append(f"{path.name}: issued_for_fingerprint is stale")
    dependencies = certificate.get("dependency_fingerprints_at_issuance")
    if not isinstance(dependencies, dict) or any(
        not isinstance(key, str) or not isinstance(value, str) or not HEX64.fullmatch(value)
        for key, value in (dependencies.items() if isinstance(dependencies, dict) else ())
    ):
        errors.append(f"{path.name}: dependency_fingerprints_at_issuance must map names to sha256 hex")
    elif dependencies != dependency_fingerprint_map(domain):
        errors.append(f"{path.name}: dependency fingerprints are stale")
    status_snapshot = certificate.get("status_snapshot")
    if not isinstance(status_snapshot, dict):
        errors.append(f"{path.name}: status_snapshot must be an object")
    elif status_snapshot != state.get("statuses"):
        errors.append(f"{path.name}: status_snapshot does not match live state")
    if certificate.get("voided_at") is not None:
        if not isinstance(certificate["voided_at"], str):
            errors.append(f"{path.name}: voided_at must be null or an ISO timestamp")
        else:
            errors.append(f"{path.name}: certificate is voided")
    issued_at = certificate.get("issued_at")
    if not isinstance(issued_at, str) or not issued_at:
        errors.append(f"{path.name}: issued_at must be a non-empty timestamp")
    evidence = certificate.get("evidence")
    if not isinstance(evidence, list) or not evidence or any(
        not isinstance(reference, str) or not reference.startswith("EVID-") for reference in evidence
    ):
        errors.append(f"{path.name}: certificate requires evidence references")
    elif len(set(evidence)) != len(evidence):
        errors.append(f"{path.name}: certificate evidence references must be unique")
    else:
        for reference in evidence:
            _, resolution_errors = resolve_positive_evidence(
                reference,
                domain_id,
                fingerprint,
                evidence_root,
                attempts_root,
                index_path or evidence_root / "index.json",
            )
            errors.extend(f"{path.name}: {error}" for error in resolution_errors)
    return errors


def validate_certificate_id(certificate_id: str, domains: dict[str, dict[str, Any]]) -> list[str]:
    path = CONTROL_ROOT / "certificates" / f"{certificate_id}.cert.json"
    if not path.is_file():
        return [f"missing closure certificate: {certificate_id}"]
    try:
        certificate = read_json(path)
        domain_id = certificate.get("domain_id")
        if domain_id not in domains:
            return [f"{path.name}: certificate references unknown domain"]
        state_path = STATE_ROOT / f"{domain_id}.state.json"
        state = read_json(state_path)
        return validate_certificate(path, domains[domain_id], state)
    except (OSError, ValueError) as exc:
        return [f"{path.name}: {exc}"]
