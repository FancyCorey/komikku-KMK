"""Run the bounded host contract audit for recommendation search ownership."""

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
    helper = _read("app/src/main/java/exh/recs/batch/RecommendationSearchHelper.kt")
    library = _read("app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryScreenModel.kt")
    tab = _read("app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt")
    progress = _read("app/src/main/java/exh/recs/batch/RecommendationSearchProgressDialog.kt")
    cancellation_test = _read("app/src/test/java/exh/recs/RecommendationSearchCancellationTest.kt")
    source_failure_test = _read("app/src/test/java/exh/recs/RecommendationSourceFailureIsolationTest.kt")
    checks = [
        _check(
            "outer_cancellation_propagation",
            "catch (e: CancellationException)" in helper
            and "catch (_: CancellationException)" not in helper
            and "throw e" in helper[helper.index("status.value = when") :],
            "the outer recommendation search boundary rethrows cancellation after entering cleanup",
        ),
        _check(
            "library_owner_and_cancel_route",
            "recommendationSearch.runSearch(screenModelScope, selection)" in library
            and "recommendationSearchJob?.cancel()" in library
            and "is SearchStatus.Cancelling" in tab
            and "screenModel.cancelRecommendationSearch()" in tab,
            "Library owns the job and the existing dialog/tab cancellation route remains intact",
        ),
        _check(
            "safe_failure_classification",
            "RecommendationErrorClassifier.classifyToStorageKey(e)" in helper
            and "RecommendationErrorKind.fromStorageKey(status.message)" in progress,
            "ordinary failures remain mapped to bounded storage keys and localized dialog text",
        ),
        _check(
            "per_source_isolation",
            "jobs.awaitAll()" in helper
            and "catch (e: CancellationException)" in helper
            and "catch (e: Exception)" in helper,
            "source-level failures remain isolated while cancellation is not downgraded",
        ),
        _check(
            "focused_host_contracts",
            "cancellation propagates" in cancellation_test
            and "sibling sources" in source_failure_test
            and "CancellationException" in source_failure_test,
            "host tests cover the cancellation boundary and adjacent per-source isolation contract",
        ),
    ]
    return {
        "domain_id": "DOM-RECOMMENDATION-ENGINE",
        "checks": checks,
        "host_contract_valid": all(check["passed"] for check in checks),
        "device_evidence_created": False,
        "runtime_uncertainty": [
            "real Library recommendation search and cancellation on device",
            "installed-source network behavior and tracker recommendation results",
            "process recreation during a recommendation search",
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
