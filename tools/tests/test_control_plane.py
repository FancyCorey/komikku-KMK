import hashlib
import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from tools.control.attempts import attempt_key, existing_keys, record_attempt
from tools.control.control_common import canonical_json, dependency_fingerprint_map, domain_files, fingerprint_domain, fingerprint_patterns, git_snapshot, iter_files, load_domain, load_domains
from tools.control.controller_gate import record_no_op, record_progress, validate_controller
from tools.control.validate_certificate import validate_certificate
from tools.control.fixture_route_audit import READINESS_LAYERS, audit as audit_fixture_routes, write_private_report
from tools.control.security_privacy_audit import audit as audit_security_privacy
from tools.control.saf_export_audit import audit as audit_saf_export
from tools.control.backup_recovery_audit import audit as audit_backup_recovery
from tools.control.evaluation_mode_audit import audit as audit_evaluation_mode
from tools.control.action_history_audit import audit as audit_action_history
from tools.control.recommendation_engine_audit import audit as audit_recommendation_engine
from tools.control.original_flow_integration_audit import audit as audit_original_flow_integration
from tools.control.validate_evidence import validate_record
from tools.control.validate_graph import topological_order, validate as validate_graph
from tools.control.validate_state import validate_state
from tools.workspace_paths import find_workspace_root


SOURCE_ROOT = Path(__file__).parents[2]


def write_temp_evidence(root: Path, domain_id: str, fingerprint: str, result: str = "pass") -> tuple[Path, Path, str]:
    evidence_root = root / "evidence"
    attempts_root = root / "attempts"
    evidence_root.mkdir()
    attempts_root.mkdir()
    record = {
        "id": "EVID-test",
        "domain_id": domain_id,
        "evidence_class": "fixture_validation",
        "command": "python -m unittest",
        "source_fingerprint": fingerprint,
        "dependency_fingerprints": {},
        "redaction_review": {"secrets_redacted": True},
        "tool": "python",
        "tool_version": "3",
        "diagnostic_hash": "b" * 64,
        "git_snapshot": {"head": "c" * 40, "dirty": False, "status_hash": "d" * 64},
        "recorded_at": "2026-08-06T00:00:00Z",
        "result": result,
    }
    key = attempt_key(
        {
            "domain": domain_id,
            "evidence_class": record["evidence_class"],
            "tool": record["tool"],
            "tool_version": record["tool_version"],
            "fingerprint": fingerprint,
            "diagnostic_hash": record["diagnostic_hash"],
        }
    )
    record["attempt_key"] = key
    record["content_sha256"] = hashlib.sha256(canonical_json(record)).hexdigest()
    (evidence_root / "EVID-test.json").write_text(json.dumps(record), encoding="utf-8")
    (evidence_root / "index.json").write_text(
        json.dumps({"records": {"EVID-test": record["content_sha256"]}}), encoding="utf-8"
    )
    (attempts_root / f"{domain_id}.jsonl").write_text(json.dumps({"attempt_key": key}) + "\n", encoding="utf-8")
    return evidence_root, attempts_root, key


class ControlPlaneTests(unittest.TestCase):
    def test_workspace_root_resolves_from_nested_worktree(self):
        with TemporaryDirectory() as directory:
            workspace = Path(directory)
            (workspace / "private" / "control").mkdir(parents=True)
            source = workspace / "worktrees" / "isolated"
            source.mkdir(parents=True)

            self.assertEqual(workspace.resolve(), find_workspace_root(source))

    def test_declared_graph_is_acyclic_and_contains_all_domains(self):
        from tools.control.control_common import read_json, CONTROL_ROOT, load_domains

        graph = read_json(CONTROL_ROOT / "graph.json")
        order = topological_order(graph, set(load_domains()))
        self.assertEqual(len(load_domains()), len(order))
        self.assertEqual(len(set(order)), len(order))

    def test_declared_graph_validates_workspace_private_ownership_from_worktree(self):
        self.assertEqual([], validate_graph())

    def test_graph_cycle_is_rejected(self):
        with self.assertRaises(ValueError):
            topological_order(
                {"edges": [{"from": "a", "to": "b"}, {"from": "b", "to": "a"}]},
                {"a", "b"},
            )

    def test_fingerprint_is_deterministic_for_a_domain(self):
        domain = load_domain("DOM-FIXTURE-ROUTE-EVIDENCE")
        first, files = fingerprint_patterns(domain["owned_globs"])
        second, second_files = fingerprint_patterns(domain["owned_globs"])
        self.assertEqual(first, second)
        self.assertEqual(files, second_files)
        self.assertTrue(files)

    def test_all_declared_domains_expand_to_files_without_ownership_collisions(self):
        ownership = {}
        for domain_id, domain in load_domains().items():
            files = domain_files(domain)
            self.assertTrue(files, domain_id)
            for path in files:
                relative = path.as_posix()
                self.assertNotIn(relative, ownership, f"{relative}: {ownership.get(relative)} / {domain_id}")
                ownership[relative] = domain_id

    def test_graph_dependency_fingerprint_is_bound_to_upstream_domain(self):
        dependent = load_domain("DOM-BACKUP-RECOVERY")
        self.assertIn("domain:DOM-SAF-EXPORT", dependency_fingerprint_map(dependent))

    def test_path_traversal_is_rejected(self):
        with self.assertRaises(ValueError):
            iter_files(["../outside"])

    def test_fixture_route_audit_correlates_host_contracts_without_device_claims(self):
        result = audit_fixture_routes()
        self.assertEqual("DOM-FIXTURE-ROUTE-EVIDENCE", result["domainId"])
        self.assertFalse(result["summary"]["deviceEvidenceCreated"])
        self.assertFalse(result["summary"]["routeCatalogDrift"])
        self.assertEqual(13, result["summary"]["fixtureCount"])
        self.assertEqual(20, result["summary"]["routeCount"])
        self.assertEqual(0, result["summary"]["captureAuthorizedRouteCount"])
        self.assertTrue(
            result["fixtures"]["browse-deterministic-source-failure"]["readiness"]["hostContract"] == "ready"
        )
        self.assertEqual(
            "blocked",
            result["fixtures"]["best-version-migration-disposable-library"]["readiness"]["deviceFixture"],
        )
        self.assertEqual(
            "ready",
            result["fixtures"]["best-version-migration-disposable-library"]["readiness"]["appFixture"],
        )
        self.assertEqual(
            "ready",
            result["fixtures"]["alternate-source-reader-scenario-matrix"]["readiness"]["hostContract"],
        )
        self.assertTrue(
            all(
                fixture["readiness"]["captureAuthorization"] == "blocked"
                for fixture in result["fixtures"].values()
            )
        )
        self.assertTrue(all(set(route["readiness"]) == set(READINESS_LAYERS) for route in result["routes"]))
        self.assertTrue(all(not route["captureAllowed"] for route in result["routes"]))
        self.assertTrue(
            all(route["blockerOwner"] and route["exitCondition"] for route in result["routes"])
        )

    def test_fixture_route_audit_classifies_host_only_and_not_applicable_routes(self):
        result = audit_fixture_routes()
        routes = {route["name"]: route for route in result["routes"]}
        self.assertEqual("DEVICE_VALIDATION_REQUIRED", routes["browse-error-unavailable"]["disposition"])
        self.assertEqual("DEVICE_VALIDATION_REQUIRED", routes["recommendation-settings"]["disposition"])
        self.assertEqual("DEVICE_VALIDATION_REQUIRED", routes["rated-collections"]["disposition"])
        self.assertEqual("DEVICE_VALIDATION_REQUIRED", routes["manga-detail-privacy"]["disposition"])
        self.assertEqual("DEVICE_VALIDATION_REQUIRED", routes["extension-export"]["disposition"])
        self.assertEqual("CAPTURE_AUTHORIZATION_REQUIRED", routes["source-evaluation"]["disposition"])
        self.assertEqual("DEVICE_VALIDATION_REQUIRED", routes["sources-to-try"]["disposition"])
        self.assertEqual("CAPTURE_AUTHORIZATION_REQUIRED", routes["for-you"]["disposition"])
        self.assertFalse(routes["for-you"]["captureAllowed"])
        self.assertEqual("NOT_APPLICABLE", routes["group-disassociation"]["disposition"])
        self.assertTrue(
            all(
                status == "not-applicable"
                for status in routes["group-disassociation"]["readiness"].values()
            )
        )

    def test_fixture_route_audit_rejects_manifest_catalog_disagreement(self):
        from tools.control.fixture_route_audit import DEFAULT_CATALOG, DEFAULT_MANIFEST

        manifest = json.loads(DEFAULT_MANIFEST.read_text(encoding="utf-8"))
        manifest["routes"][1]["checkpointIds"] = ["unknown-checkpoint"]
        with TemporaryDirectory() as directory:
            path = Path(directory) / "routes.json"
            path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaises(Exception):
                audit_fixture_routes(path, DEFAULT_CATALOG)

    def test_fixture_route_audit_rejects_orphan_catalog_fixture(self):
        from tools.control.fixture_route_audit import DEFAULT_CATALOG, DEFAULT_MANIFEST

        catalog = json.loads(DEFAULT_CATALOG.read_text(encoding="utf-8"))
        for fixture in catalog["fixtures"]:
            if fixture.get("lifecycle", {}).get("deviceReady"):
                fixture["lifecycle"]["deviceReady"] = False
        clone = dict(catalog["fixtures"][0])
        clone["id"] = "orphan-fixture"
        catalog["fixtures"].append(clone)
        with TemporaryDirectory() as directory:
            path = Path(directory) / "catalog.json"
            path.write_text(json.dumps(catalog), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "unreferenced fixtures"):
                audit_fixture_routes(DEFAULT_MANIFEST, path)

    def test_fixture_route_report_writer_rejects_public_output(self):
        with TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "under private"):
                write_private_report(Path(directory) / "report.json", audit_fixture_routes())

    def test_security_privacy_audit_correlates_host_contracts_without_runtime_claims(self):
        result = audit_security_privacy()
        self.assertTrue(result["host_contract_valid"])
        self.assertFalse(result["device_evidence_created"])
        self.assertIn("merged manifest behavior", result["runtime_uncertainty"])

    def test_saf_export_audit_correlates_host_contracts_without_runtime_claims(self):
        result = audit_saf_export()
        self.assertEqual("DOM-SAF-EXPORT", result["domain_id"])
        self.assertTrue(result["host_contract_valid"])
        self.assertFalse(result["device_evidence_created"])
        self.assertIn("WorkManager execution and pruning/reconciliation", result["runtime_uncertainty"])

    def test_backup_recovery_audit_correlates_host_contracts_without_runtime_claims(self):
        result = audit_backup_recovery()
        self.assertEqual("DOM-BACKUP-RECOVERY", result["domain_id"])
        self.assertTrue(result["host_contract_valid"])
        self.assertFalse(result["device_evidence_created"])
        self.assertIn("process death while backup work is active", result["runtime_uncertainty"])

    def test_evaluation_mode_audit_correlates_host_contracts_without_runtime_claims(self):
        result = audit_evaluation_mode()
        self.assertEqual("DOM-EVALUATION-MODE", result["domain_id"])
        self.assertTrue(result["host_contract_valid"])
        self.assertFalse(result["device_evidence_created"])
        self.assertIn("crash-recovery rendering", result["runtime_uncertainty"][0])

    def test_action_history_audit_correlates_host_contracts_without_runtime_claims(self):
        result = audit_action_history()
        self.assertEqual("DOM-ACTION-HISTORY", result["domain_id"])
        self.assertTrue(result["host_contract_valid"])
        self.assertFalse(result["device_evidence_created"])
        self.assertIn("process recreation", result["runtime_uncertainty"][1])

    def test_recommendation_engine_audit_correlates_host_contracts_without_runtime_claims(self):
        result = audit_recommendation_engine()
        self.assertEqual("DOM-RECOMMENDATION-ENGINE", result["domain_id"])
        self.assertTrue(result["host_contract_valid"])
        self.assertFalse(result["device_evidence_created"])
        self.assertIn("Library recommendation search", result["runtime_uncertainty"][0])

    def test_original_flow_integration_audit_correlates_host_contracts_without_runtime_claims(self):
        result = audit_original_flow_integration()
        self.assertEqual("DOM-ORIGINAL-FLOW-INTEGRATION", result["domain_id"])
        self.assertTrue(result["host_contract_valid"])
        self.assertFalse(result["device_evidence_created"])
        self.assertIn("real navigation", result["runtime_uncertainty"][0])

    def test_positive_claim_rejects_fabricated_evidence_reference(self):
        domain = load_domain("DOM-FIXTURE-ROUTE-EVIDENCE")
        fingerprint, _ = fingerprint_domain(domain)
        statuses = {
            name: {"value": False, "as_of": None, "evidence": []}
            for name in ("implemented", "host_tested", "device_tested", "release_ready")
        }
        statuses["implemented"] = {"value": True, "as_of": fingerprint, "evidence": ["EVID-TOTALLY-FABRICATED-9999"]}
        with TemporaryDirectory() as directory:
            root = Path(directory)
            state_path = root / "state.json"
            state_path.write_text(
                json.dumps(
                    {
                        "schema_version": "1.0.0",
                        "domain_id": domain["id"],
                        "owned_fingerprint": fingerprint,
                        "dependency_fingerprints": dependency_fingerprint_map(domain),
                        "git_snapshot": git_snapshot(),
                        "statuses": statuses,
                        "blocked": None,
                    }
                ),
                encoding="utf-8",
            )
            errors = validate_state(state_path, domain, root / "evidence", root / "attempts")
        self.assertIn("DOM-FIXTURE-ROUTE-EVIDENCE: evidence reference does not resolve: EVID-TOTALLY-FABRICATED-9999", errors)

    def test_positive_claim_rejects_failing_evidence_record(self):
        domain = load_domain("DOM-FIXTURE-ROUTE-EVIDENCE")
        fingerprint, _ = fingerprint_domain(domain)
        with TemporaryDirectory() as directory:
            root = Path(directory)
            evidence_root, attempts_root, _ = write_temp_evidence(root, domain["id"], fingerprint, "fail")
            statuses = {
                name: {"value": False, "as_of": None, "evidence": []}
                for name in ("implemented", "host_tested", "device_tested", "release_ready")
            }
            statuses["implemented"] = {"value": True, "as_of": fingerprint, "evidence": ["EVID-test"]}
            state_path = root / "state.json"
            state_path.write_text(
                json.dumps(
                    {
                        "schema_version": "1.0.0",
                        "domain_id": domain["id"],
                        "owned_fingerprint": fingerprint,
                        "dependency_fingerprints": dependency_fingerprint_map(domain),
                        "git_snapshot": git_snapshot(),
                        "statuses": statuses,
                        "blocked": None,
                    }
                ),
                encoding="utf-8",
            )
            errors = validate_state(state_path, domain, evidence_root, attempts_root)
        self.assertIn("DOM-FIXTURE-ROUTE-EVIDENCE: positive claim cites non-passing evidence EVID-test", errors)

    def test_release_ready_requires_every_declared_evidence_class(self):
        domain = load_domain("DOM-FIXTURE-ROUTE-EVIDENCE")
        fingerprint, _ = fingerprint_domain(domain)
        with TemporaryDirectory() as directory:
            root = Path(directory)
            evidence_root, attempts_root, _ = write_temp_evidence(root, domain["id"], fingerprint, "pass")
            statuses = {
                name: {"value": True, "as_of": fingerprint, "evidence": ["EVID-test"]}
                for name in ("implemented", "host_tested", "device_tested", "release_ready")
            }
            state_path = root / "state.json"
            state_path.write_text(
                json.dumps(
                    {
                        "schema_version": "1.0.0",
                        "domain_id": domain["id"],
                        "owned_fingerprint": fingerprint,
                        "dependency_fingerprints": dependency_fingerprint_map(domain),
                        "git_snapshot": git_snapshot(),
                        "statuses": statuses,
                        "blocked": None,
                    }
                ),
                encoding="utf-8",
            )
            errors = validate_state(state_path, domain, evidence_root, attempts_root)
        self.assertTrue(any("release_ready is missing required evidence classes" in error for error in errors))

    def test_positive_claim_requires_current_fingerprint_and_evidence(self):
        domain = load_domain("DOM-FIXTURE-ROUTE-EVIDENCE")
        fingerprint, _ = fingerprint_patterns(domain["owned_globs"])
        statuses = {
            name: {"value": False, "as_of": None, "evidence": []}
            for name in ("implemented", "host_tested", "device_tested", "release_ready")
        }
        statuses["implemented"] = {"value": True, "as_of": "old", "evidence": []}
        with TemporaryDirectory() as directory:
            state_path = Path(directory) / "state.json"
            state_path.write_text(
                json.dumps(
                    {
                        "schema_version": "1.0.0",
                        "domain_id": domain["id"],
                        "owned_fingerprint": fingerprint,
                        "statuses": statuses,
                        "blocked": None,
                    }
                ),
                encoding="utf-8",
            )
            errors = validate_state(state_path, domain)
        self.assertTrue(any("not bound to current fingerprint" in error for error in errors))
        self.assertTrue(any("requires evidence references" in error for error in errors))

    def test_status_order_is_enforced(self):
        domain = load_domain("DOM-FIXTURE-ROUTE-EVIDENCE")
        fingerprint, _ = fingerprint_patterns(domain["owned_globs"])
        statuses = {
            name: {"value": False, "as_of": None, "evidence": []}
            for name in ("implemented", "host_tested", "device_tested", "release_ready")
        }
        statuses["device_tested"] = {"value": True, "as_of": fingerprint, "evidence": ["EVID-test"]}
        with TemporaryDirectory() as directory:
            state_path = Path(directory) / "state.json"
            state_path.write_text(
                json.dumps(
                    {
                        "schema_version": "1.0.0",
                        "domain_id": domain["id"],
                        "owned_fingerprint": fingerprint,
                        "statuses": statuses,
                        "blocked": None,
                    }
                ),
                encoding="utf-8",
            )
            errors = validate_state(state_path, domain)
        self.assertIn("DOM-FIXTURE-ROUTE-EVIDENCE: device_tested requires host_tested", errors)

    def test_terminal_attempt_key_is_stable_and_append_only_lookup_detects_it(self):
        values = {
            "domain": "DOM-FIXTURE-ROUTE-EVIDENCE",
            "evidence_class": "fixture_validation",
            "tool": "python",
            "tool_version": "3",
            "fingerprint": "abc",
            "diagnostic_hash": "def",
        }
        self.assertEqual(attempt_key(values), attempt_key(dict(values)))
        with TemporaryDirectory() as directory:
            path = Path(directory) / "attempts.jsonl"
            path.write_text(json.dumps({"attempt_key": attempt_key(values)}) + "\n", encoding="utf-8")
            self.assertIn(attempt_key(values), existing_keys(path))
            recorded, _ = record_attempt(values, path)
            self.assertFalse(recorded)

    def test_controller_stays_open_until_certificates_and_release_states_exist(self):
        self.assertEqual([], validate_controller())
        with TemporaryDirectory() as directory:
            controller_path = Path(directory) / "controller.json"
            controller_path.write_text(
                json.dumps(
                    {
                        "terminal_state": "COMPLETE",
                        "certificates": [],
                        "no_op_passes": 0,
                        "no_op_limit": 2,
                    }
                ),
                encoding="utf-8",
            )
            errors = validate_controller(controller_path)
        self.assertTrue(any("closure certificate" in error for error in errors))
        self.assertTrue(any("release_ready" in error for error in errors))

    def test_no_progress_budget_is_counted(self):
        with TemporaryDirectory() as directory:
            controller_path = Path(directory) / "controller.json"
            controller_path.write_text(
                json.dumps({"no_op_passes": 0, "no_op_limit": 2}), encoding="utf-8"
            )
            self.assertEqual(1, record_no_op(controller_path))
            self.assertEqual(2, record_no_op(controller_path))
            self.assertTrue(any("no-op budget" in error for error in validate_controller(controller_path)))
            record_progress(controller_path)
            self.assertEqual(0, json.loads(controller_path.read_text(encoding="utf-8"))["no_op_passes"])

    def test_certificate_requires_content_and_current_state_binding(self):
        domain = load_domain("DOM-FIXTURE-ROUTE-EVIDENCE")
        state = {
            "statuses": {
                name: {"value": False, "as_of": None, "evidence": []}
                for name in ("implemented", "host_tested", "device_tested", "release_ready")
            }
        }
        with TemporaryDirectory() as directory:
            path = Path(directory) / "CERT-test.cert.json"
            path.write_text(json.dumps({"schema_version": "1.0.0"}), encoding="utf-8")
            errors = validate_certificate(path, domain, state)
        self.assertTrue(any("issued_for_fingerprint" in error for error in errors))
        self.assertTrue(any("issued by controller tooling" in error for error in errors))

    def test_certificate_schema_rejects_malformed_fields_and_missing_evidence(self):
        domain = load_domain("DOM-FIXTURE-ROUTE-EVIDENCE")
        state = {
            "statuses": {
                name: {"value": False, "as_of": None, "evidence": []}
                for name in ("implemented", "host_tested", "device_tested", "release_ready")
            }
        }
        with TemporaryDirectory() as directory:
            path = Path(directory) / "CERT-wrong.cert.json"
            path.write_text(
                json.dumps(
                    {
                        "certificate_id": "CERT-wrong",
                        "schema_version": "1.0.0",
                        "domain_id": domain["id"],
                        "issued_for_fingerprint": "not-a-fingerprint",
                        "dependency_fingerprints_at_issuance": [],
                        "status_snapshot": [],
                        "evidence": ["EVID-missing"],
                        "issued_at": 123,
                        "issued_by": "tools.control.issue_certificate",
                        "voided_at": False,
                    }
                ),
                encoding="utf-8",
            )
            errors = validate_certificate(path, domain, state, Path(directory) / "evidence", Path(directory) / "attempts")
        self.assertTrue(any("certificate_id must be CERT-DOM-FIXTURE-ROUTE-EVIDENCE" in error for error in errors))
        self.assertTrue(any("issued_for_fingerprint must be sha256 hex" in error for error in errors))
        self.assertTrue(any("dependency_fingerprints_at_issuance must map" in error for error in errors))
        self.assertTrue(any("status_snapshot must be an object" in error for error in errors))
        self.assertTrue(any("voided_at must be null or an ISO timestamp" in error for error in errors))
        self.assertTrue(any("evidence reference does not resolve" in error for error in errors))

    def test_evidence_record_requires_redaction_and_current_provenance_shape(self):
        record = {
            "id": "EVID-test",
            "domain_id": "DOM-FIXTURE-ROUTE-EVIDENCE",
            "evidence_class": "fixture_validation",
            "command": "python -m unittest",
            "source_fingerprint": "a" * 64,
            "dependency_fingerprints": {},
            "redaction_review": {"secrets_redacted": True},
            "tool": "python",
            "tool_version": "3",
            "diagnostic_hash": "b" * 64,
            "git_snapshot": {"head": "c" * 40, "dirty": False, "status_hash": "d" * 64},
            "recorded_at": "2026-08-06T00:00:00Z",
            "result": "pass",
        }
        with TemporaryDirectory() as directory:
            path = Path(directory) / "EVID-test.json"
            attempts_root = Path(directory) / "attempts"
            attempts_root.mkdir()
            key = attempt_key(
                {
                    "domain": record["domain_id"],
                    "evidence_class": record["evidence_class"],
                    "tool": record["tool"],
                    "tool_version": record["tool_version"],
                    "fingerprint": record["source_fingerprint"],
                    "diagnostic_hash": record["diagnostic_hash"],
                }
            )
            record["attempt_key"] = key
            record["content_sha256"] = hashlib.sha256(canonical_json(record)).hexdigest()
            index_path = Path(directory) / "index.json"
            index_path.write_text(json.dumps({"records": {"EVID-test": record["content_sha256"]}}), encoding="utf-8")
            (attempts_root / "DOM-FIXTURE-ROUTE-EVIDENCE.jsonl").write_text(
                json.dumps({"attempt_key": key}) + "\n", encoding="utf-8"
            )
            path.write_text(json.dumps(record), encoding="utf-8")
            self.assertEqual([], validate_record(path, {"DOM-FIXTURE-ROUTE-EVIDENCE"}, attempts_root, index_path))
            record["redaction_review"]["secrets_redacted"] = False
            path.write_text(json.dumps(record), encoding="utf-8")
            self.assertTrue(validate_record(path, {"DOM-FIXTURE-ROUTE-EVIDENCE"}, attempts_root, index_path))


if __name__ == "__main__":
    unittest.main()
