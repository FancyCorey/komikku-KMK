"""Run the bounded host contract audit for normal Action History."""

from __future__ import annotations

import argparse
import json
from typing import Any

from .control_common import SOURCE_ROOT, WORKSPACE_ROOT


def _read(relative: str) -> str:
    return (SOURCE_ROOT / relative).read_text(encoding="utf-8")


def _check(name: str, condition: bool, detail: str) -> dict[str, Any]:
    return {"name": name, "passed": condition, "detail": detail}


def audit() -> dict[str, Any]:
    registry = _read("app/src/main/java/exh/util/ActionHistoryRegistry.kt")
    screen = _read("app/src/main/java/exh/util/EvaluationModeActionHistoryScreen.kt")
    screen_controller = _read("app/src/main/java/exh/util/ActionHistoryScreenController.kt")
    non_undoable = _read("app/src/main/java/exh/util/NonUndoableEventJournal.kt")
    package_receipt = _read("app/src/main/java/exh/util/PackageOperationReceipt.kt")
    migration_receipt = _read("app/src/main/java/exh/util/MigrationReceipt.kt")
    download_receipt = _read("app/src/main/java/exh/util/DownloadReceipt.kt")
    track_receipt = _read("app/src/main/java/exh/util/TrackWriteReceipt.kt")
    binding_receipt = _read("app/src/main/java/exh/util/TrackerBindingReceipt.kt")
    advanced_settings = _read(
        "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsAdvancedScreen.kt"
    )
    registry_tests = _read("app/src/test/java/exh/util/ActionHistoryRegistryTest.kt")
    follow_up_tests = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (SOURCE_ROOT / "app/src/test/java/exh/util").glob("*FollowUp*.kt")
    )
    receipt_tests = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (SOURCE_ROOT / "app/src/test/java/exh/util").glob("*Receipt*Test.kt")
    )

    checks = [
        _check(
            "complete_registry_matrix",
            all(
                marker in registry
                for marker in (
                    'familyId: String = "taste"',
                    'familyId: String = "group"',
                    'familyId: String = "cross_source_identity"',
                    'familyId: String = "alternate_source_bridge"',
                    'familyId: String = "library"',
                    'familyId: String = "preference"',
                    'familyId: String = "chapter"',
                    'familyId: String = "cover"',
                    'familyId: String = "nonundoable"',
                    "JournalFamily.entries",
                    "ActionHistoryRegistry.sources",
                )
            )
            and "sources.forEach { it.clear() }" in registry,
            "all nine declared journal families share one registry and clear-all traversal",
        ),
        _check(
            "stable_merge_order",
            "sources.flatMap { it.snapshot() }.sortedByDescending { it.timestamp }" in registry
            and "id = \"taste:${entry.id}\"" in registry
            and "id = \"event:${event.id}\"" in registry,
            "entries are merged newest-first with source-qualified stable row ids",
        ),
        _check(
            "private_nonundoable_context",
            all(marker in non_undoable for marker in ("val id: String", "val timestamp: Long", "val eventType:"))
            and "package name" in non_undoable
            and "no manga id" in non_undoable
            and "summary = { ctx -> nonUndoableEventSummary(ctx, event) }" in registry
            and "receipt.packageName" not in registry.split("private fun nonUndoableEventSummary", 1)[-1].split("private fun", 1)[0],
            "visible non-undoable rows stay event-category based while package and receipt details remain private",
        ),
        _check(
            "receipt_clear_correlation",
            all(marker in registry for marker in ("PackageOperationJournal.clear()", "MigrationReceiptJournal.clear()", "DownloadReceiptJournal.clear()", "TrackWriteReceiptJournal.clear()", "TrackerBindingReceiptJournal.clear()"))
            and all(marker in receipt for receipt in (package_receipt, migration_receipt, download_receipt, track_receipt, binding_receipt) for marker in ("fun clear()", "synchronized(lock)")),
            "private recovery receipts are bounded and cleared with their visible event family",
        ),
        _check(
            "failure_and_cancellation",
            "catch (error: CancellationException)" in screen
            and "throw error" in screen
            and "catch (e: CancellationException)" in registry
            and all(marker in follow_up_tests for marker in ("Failed", "CancellationException", "clear")),
            "UI and follow-up triggers preserve cancellation while mapping ordinary failure to a safe result",
        ),
        _check(
            "normal_history_route_and_diagnostic_gate",
            all(
                marker in screen
                for marker in (
                    "class ActionHistoryScreen",
                    "ActionHistoryCommandController()",
                    "ActionHistoryRowPresentationPolicy.canExposeDiagnostics",
                )
            )
            and all(
                marker in screen_controller
                for marker in (
                    "ActionHistoryRegistry::snapshot",
                    "ActionHistoryRegistry::clearAll",
                    "DeveloperOptionsGatePolicy.canExposeDiagnostics",
                )
            )
            and "navigator.push(exh.util.ActionHistoryScreen())" in advanced_settings,
            "normal Advanced Settings route, bounded history, clear action, and Developer Options diagnostic gate remain integrated",
        ),
        _check(
            "host_matrix_tests",
            all(
                marker in registry_tests
                for marker in (
                    "registers every known journal family by identity",
                    '"cross_source_identity"',
                    '"alternate_source_bridge"',
                    "clearAll",
                    "non-undoable",
                )
            )
            and all(marker in receipt_tests for marker in ("MAX_ENTRIES", "opaque", "evaluation mode"))
            and "ActionHistoryRegistry" in follow_up_tests,
            "focused tests cover registry completeness, ordering, clear-all, privacy, and follow-up safety",
        ),
    ]
    return {
        "domain_id": "DOM-ACTION-HISTORY",
        "checks": checks,
        "host_contract_valid": all(check["passed"] for check in checks),
        "device_evidence_created": False,
        "runtime_uncertainty": [
            "real Evaluation Mode navigation and live preference gating on device",
            "process recreation and in-memory journal lifetime",
            "package, migration, download, and tracker follow-up behavior against real external state",
        ],
        "workspace_root": str(WORKSPACE_ROOT),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args(argv)
    result = audit()
    print(json.dumps(result, indent=2, sort_keys=True))
    return 0 if result["host_contract_valid"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
