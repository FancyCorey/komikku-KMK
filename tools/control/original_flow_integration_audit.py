"""Run the bounded host contract audit for original Komikku flow integration."""

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
    main = _read("app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt")
    library = _read("app/src/main/java/eu/kanade/tachiyomi/ui/library/LibraryTab.kt")
    browse = _read("app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreen.kt")
    browse_model = _read(
        "app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/browse/BrowseSourceScreenModel.kt"
    )
    reader = _read("app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt")
    reader_model = _read("app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt")
    feed_model = _read("app/src/main/java/eu/kanade/tachiyomi/ui/browse/feed/FeedScreenModel.kt")
    source_feed_model = _read("app/src/main/java/eu/kanade/tachiyomi/ui/browse/source/feed/SourceFeedScreenModel.kt")
    settings_data = _read(
        "app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreen.kt"
    )
    deeplink_tests = _read("app/src/test/java/eu/kanade/tachiyomi/ui/deeplink/DeepLinkIntentSanitizerTest.kt")
    backup_tests = _read("app/src/test/java/eu/kanade/tachiyomi/ui/main/BackupDeepLinkPolicyTest.kt")
    route_tests = _read(
        "app/src/test/java/eu/kanade/tachiyomi/ui/browse/source/browse/SelectBrowseSourcePagingSourceTest.kt"
    )
    reader_tests = _read("app/src/test/java/eu/kanade/tachiyomi/ui/reader/ReaderChapterCompletionPromptJournalTest.kt")
    settings_tests = _read(
        "app/src/test/java/eu/kanade/presentation/more/settings/screen/SettingsDataScreenCsvExportCleanupTest.kt"
    )

    checks = [
        _check(
            "root_navigation_and_intents",
            all(
                marker in main
                for marker in (
                    "Navigator(",
                    "screen = HomeScreen",
                    "DefaultNavigatorScreenTransition",
                    "HandleOnNewIntent(context = context, navigator = navigator)",
                    "navigator.popUntilRoot()",
                )
            ),
            "the existing Home/Voyager root, transition, and new-intent owner remain connected",
        ),
        _check(
            "library_state_owner",
            all(marker in library for marker in ("rememberScreenModel { LibraryScreenModel() }", "collectAsState()", "LoadingScreen", "EmptyScreen", "LibraryContent"))
            and "navigator.push(MangaScreen" in library,
            "Library keeps one ScreenModel owner, loading/empty/content branches, and existing manga navigation",
        ),
        _check(
            "browse_state_and_source_boundary",
            all(marker in browse for marker in ("rememberScreenModel", "collectAsState()", "LoadingScreen", "BrowseSourceContent", "navigator.pop()"))
            and all(marker in browse_model for marker in ("SourceRuntime.runBlockingSourceCall", "FilterList()", "SourceRuntimeOperation.FilterList")),
            "Browse retains ScreenModel/state ownership and the shared recoverable-source boundary",
        ),
        _check(
            "reader_lifecycle_and_back_stack",
            all(marker in reader for marker in ("viewModel.state.collectAsState()", "onBackPressedDispatcher::onBackPressed", "setInitialChapterError", "ReaderAppBars"))
            and all(marker in reader_model for marker in ("override fun onCleared()", "scheduleEntitlement = ReaderScheduleEntitlement.Closed", "onActivityFinish()")),
            "Reader keeps state/error presentation, back navigation, and ViewModel cleanup ownership",
        ),
        _check(
            "browse_lifecycle_cleanup",
            all(marker in feed_model for marker in ("override fun onDispose()", "coroutineDispatcher.close()"))
            and all(marker in source_feed_model for marker in ("override fun onDispose()", "coroutineDispatcher.close()")),
            "feed ScreenModels close their dispatcher at lifecycle disposal",
        ),
        _check(
            "settings_backup_and_route_regression",
            all(marker in main for marker in ("BackupDeepLinkPolicy.isAllowed", "navigator.push(RestoreBackupScreen", "ExtensionStoreUrlPolicy::isAllowed"))
            and all(marker in settings_data for marker in ("rememberScreenModel { SettingsDataScreenModel() }", "exportCleanupOffer", "SafExportCoordinator"))
            and all(marker in deeplink_tests for marker in ("VIEW", "unsupported", "SanitizedDeepLink"))
            and all(marker in backup_tests for marker in ("file and content", "network schemes", "non-backup")),
            "backup/repository deep links and Settings/Data export keep their existing guarded owners and tests",
        ),
        _check(
            "focused_original_flow_tests",
            all(marker in route_tests for marker in ("fixtureModeEnabled", "realPagingSourceProvider", "fixturePagingSourceProvider"))
            and "cancellation" in reader_tests.lower()
            and all(marker in settings_tests for marker in ("rememberScreenModel", "SafExportCoordinator", "cleanup")),
            "focused host tests cover route fallback, reader mutation behavior, and Settings/Data lifecycle cleanup",
        ),
    ]
    return {
        "domain_id": "DOM-ORIGINAL-FLOW-INTEGRATION",
        "checks": checks,
        "host_contract_valid": all(check["passed"] for check in checks),
        "device_evidence_created": False,
        "runtime_uncertainty": [
            "real navigation/back-stack behavior across Library, Browse, Reader, and Settings",
            "configuration recreation and process death for ScreenModels and ReaderViewModel",
            "theme, accessibility, and loaded/empty/error rendering on the authorized device",
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
