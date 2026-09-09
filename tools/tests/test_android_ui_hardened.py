import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, patch

from tools.android_ui_hardened import (
    AmbiguousSelectorError,
    ActionVerificationError,
    Adb,
    Bounds,
    DisplayInfo,
    ElementNotFoundError,
    _back_until_text,
    _click_bottom_navigation_text,
    _ensure_bottom_navigation_text,
    _click_tab_text,
    _scroll_to_text,
    execute_route,
    InvalidBoundsError,
    main,
    Selector,
    UiElement,
    deduplicate,
    resolve,
    resolve_click_target,
    resolve_selected_text,
    validate_routes,
)


def element(index: int, text: str, clickable: bool = True, bounds: str = "[0,0][100,100]", enabled: bool = True, selected: bool = False, checked: bool = False, parent_index=None, depth: int = 0) -> UiElement:
    return UiElement(index, text, "", "", "android.view.View", "app.test", clickable, False, False, enabled, True, False, selected, checked, False, Bounds.parse(bounds), parent_index, depth)


class AndroidUiHardenedTests(unittest.TestCase):
    def test_adb_text_output_is_decoded_as_utf8_on_windows(self):
        adb = Adb.__new__(Adb)
        adb.adb = "adb"
        adb.serial = "emulator-5554"
        adb.timeout = 15.0
        completed = SimpleNamespace(returncode=0, stdout="source é", stderr="")

        with patch("tools.android_ui_hardened.subprocess.run", return_value=completed) as run:
            self.assertEqual(adb.run("shell", "cat", "window.xml"), "source é")

        self.assertEqual(run.call_args.kwargs["encoding"], "utf-8")
        self.assertEqual(run.call_args.kwargs["errors"], "replace")

    def test_hierarchy_stream_rejects_incomplete_dump(self):
        adb = Adb.__new__(Adb)
        adb.ensure_target = MagicMock()
        adb.run = MagicMock(return_value="ERROR: could not dump window hierarchy\n")
        with self.assertRaisesRegex(Exception, "fresh, complete"):
            adb.hierarchy()

    def test_scroll_allows_multiple_changed_hierarchies_and_stops_at_bound(self):
        def frame(label):
            return UiElement(0, label, "", "", "android.view.View", "app.test", False, False, True, True, False, False, False, False, False, Bounds.parse("[0,0][100,100]"), None, 0)

        class FakeAdb:
            def __init__(self, frames):
                self.frames = iter(frames)
                self.calls = []

            def elements(self):
                return next(self.frames)

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb([[frame("One")], [frame("Two")], [frame("Three")], [frame("Target")]])
        _scroll_to_text(adb, "Target", 3)
        self.assertEqual(3, len(adb.calls))

        adb = FakeAdb([[frame("One")], [frame("Two")]])
        with self.assertRaisesRegex(ElementNotFoundError, "after 1 bounded swipes"):
            _scroll_to_text(adb, "Target", 1)
        self.assertEqual(1, len(adb.calls))

    def test_elements_ignore_malformed_non_actionable_bounds_in_selector_mode(self):
        adb = Adb.__new__(Adb)
        adb.hierarchy = lambda: (
            '<hierarchy>'
            '<node text="For You" content-desc="" resource-id="" class="android.view.View" package="app.test" '
            'clickable="false" long-clickable="false" scrollable="false" enabled="true" focusable="false" '
            'focused="false" selected="false" checked="false" checkable="false" bounds="[0,0][100,100]" />'
            '<node text="" content-desc="" resource-id="" class="android.view.View" package="app.test" '
            'clickable="false" long-clickable="false" scrollable="false" enabled="false" focusable="false" '
            'focused="false" selected="false" checked="false" checkable="false" bounds="" />'
            '</hierarchy>'
        )

        self.assertEqual(["For You"], [item.text for item in adb.elements()])
        with self.assertRaises(InvalidBoundsError):
            adb.elements(include_all=True)

    def test_bounds_reject_inverted_values(self):
        with self.assertRaises(InvalidBoundsError):
            Bounds.parse("[10,10][10,20]")

    def test_bounds_geometry(self):
        bounds = Bounds.parse("[10,20][110,70]")
        self.assertEqual(bounds.width, 100)
        self.assertEqual(bounds.height, 50)
        self.assertEqual(bounds.area, 5000)
        self.assertEqual((bounds.center_x, bounds.center_y), (60, 45))

    def test_duplicate_semantics_are_removed(self):
        values = deduplicate([element(0, "Browse"), element(1, "Browse"), element(2, "For You")])
        self.assertEqual([item.text for item in values], ["Browse", "For You"])
        self.assertEqual([item.index for item in values], [0, 1])

    def test_deduplicate_remaps_parent_indices(self):
        values = deduplicate([
            element(0, "Duplicate"),
            element(1, "Tab", clickable=False, parent_index=0),
            element(2, "Duplicate"),
            element(3, "For You", clickable=False, parent_index=1),
        ])
        self.assertEqual([item.index for item in values], [0, 1, 2])
        self.assertEqual(values[1].parent_index, 0)
        self.assertEqual(values[2].parent_index, 1)

    def test_ambiguous_selector_never_picks_first(self):
        with self.assertRaises(AmbiguousSelectorError):
            resolve([element(0, "Browse"), element(1, "Browse", bounds="[0,100][100,200]")], Selector(text="Browse"))

    def test_selector_occurrence_is_explicit(self):
        selected = resolve([element(0, "Browse"), element(1, "Browse", bounds="[0,100][100,200]")], Selector(text="Browse", occurrence=1))
        self.assertEqual(selected.index, 1)

    def test_selected_text_resolves_selected_ancestor(self):
        values = [
            element(0, "", clickable=True, selected=True, bounds="[0,0][100,100]"),
            element(1, "Off (normal browsing)", clickable=False, parent_index=0),
        ]
        self.assertEqual(0, resolve_selected_text(values, "Off (normal browsing)").index)

    def test_selected_text_rejects_unselected_ancestor(self):
        values = [
            element(0, "", clickable=True, selected=False, bounds="[0,0][100,100]"),
            element(1, "Off (normal browsing)", clickable=False, parent_index=0),
        ]
        with self.assertRaises(ActionVerificationError):
            resolve_selected_text(values, "Off (normal browsing)")

    def test_selected_text_resolves_checked_ancestor(self):
        values = [
            element(0, "", clickable=True, checked=True, bounds="[0,0][100,100]"),
            element(1, "Off (normal browsing)", clickable=False, parent_index=0),
        ]
        self.assertEqual(0, resolve_selected_text(values, "Off (normal browsing)").index)

    def test_selected_text_rejects_ambiguous_labels(self):
        with self.assertRaises(AmbiguousSelectorError):
            resolve_selected_text([element(0, "Off"), element(1, "Off")], "Off")

    def test_click_target_promotes_non_clickable_label_to_ancestor(self):
        values = [
            element(0, "", clickable=True, bounds="[0,0][100,100]"),
            element(1, "chapter", clickable=False, bounds="[10,40][60,60]", parent_index=0),
        ]
        self.assertEqual(0, resolve_click_target(values, values[1]).index)

    def test_click_target_rejects_multiple_clickable_ancestors(self):
        values = [
            element(0, "", clickable=True, bounds="[0,0][100,100]"),
            element(1, "", clickable=True, bounds="[0,0][100,100]", parent_index=0),
            element(2, "chapter", clickable=False, bounds="[10,40][60,60]", parent_index=1),
        ]
        with self.assertRaises(AmbiguousSelectorError):
            resolve_click_target(values, values[2])

    def test_route_schema_requires_verification(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "routes.json"
            path.write_text(json.dumps({"broken": {"steps": [{"action": "click-text", "value": "Browse"}]}}), encoding="utf-8")
            with self.assertRaises(Exception):
                validate_routes(path)

    def test_route_schema_rejects_blank_semantic_values(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "routes.json"
            path.write_text(json.dumps({"broken": {"steps": [{"action": "click-text", "value": ""}], "verify": {"selector": "text", "value": "Browse"}}}), encoding="utf-8")
            with self.assertRaises(Exception):
                validate_routes(path)

    def test_route_schema_accepts_bottom_navigation_text(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "routes.json"
            path.write_text(json.dumps({"bottom-nav": {"steps": [{"action": "click-bottom-navigation-text", "value": "Browse"}], "verify": {"selector": "text", "value": "Browse"}}}), encoding="utf-8")
            self.assertIn("bottom-nav", validate_routes(path))

    def test_route_schema_accepts_bounded_pager_swipes(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "routes.json"
            path.write_text(json.dumps({"pager": {"steps": [{"action": "swipe-pager-to-text", "value": "For You", "swipes": 2}], "verify": {"selector": "text", "value": "For You"}}}), encoding="utf-8")
            self.assertIn("pager", validate_routes(path))

    def test_route_schema_rejects_unbounded_back_navigation(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "routes.json"
            path.write_text(json.dumps({"broken": {"steps": [{"action": "back-until-text", "value": "More", "max_backs": 5}], "verify": {"selector": "text", "value": "More"}}}), encoding="utf-8")
            with self.assertRaises(Exception):
                validate_routes(path)

    def test_settings_advanced_route_scrolls_to_intermediate_settings_rows(self):
        routes = validate_routes(Path(__file__).parents[1] / "routes.json")
        self.assertEqual(
            ["click-text", "scroll-to-text", "click-text", "scroll-to-text", "click-text"],
            [step["action"] for step in routes["settings-advanced"]["steps"]],
        )
        self.assertEqual(
            ["More", "Settings", "Settings", "Advanced", "Advanced"],
            [step["value"] for step in routes["settings-advanced"]["steps"]],
        )

    def test_recommendation_settings_route_has_bounded_semantic_navigation(self):
        routes = validate_routes(Path(__file__).parents[1] / "routes.json")
        route = routes["recommendation-settings"]
        self.assertEqual(
            ["back-until-text", "ensure-bottom-navigation-text", "click-tab-text", "click-description"],
            [step["action"] for step in route["steps"]],
        )
        self.assertEqual(
            ["More", "Browse", "For You", "Recommendation Settings"],
            [step["value"] for step in route["steps"]],
        )
        self.assertEqual(
            {"selector": "text", "value": "Recommendation Settings"},
            route["verify"],
        )
        self.assertEqual(4, route["steps"][0]["max_backs"])

    def test_browse_route_uses_bottom_navigation_owner_for_duplicate_labels(self):
        routes = validate_routes(Path(__file__).parents[1] / "routes.json")
        self.assertEqual(
            ["click-bottom-navigation-text"],
            [step["action"] for step in routes["browse"]["steps"]],
        )
        self.assertEqual("Browse", routes["browse"]["steps"][0]["value"])

    def test_bottom_navigation_text_uses_only_the_lower_clickable_match(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return [element(0, "Browse", bounds="[0,40][100,60]"), element(1, "Browse", bounds="[0,85][100,100]")]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        with patch("tools.android_ui_hardened.time.sleep") as sleep:
            _click_bottom_navigation_text(adb, "Browse")
        sleep.assert_called_once_with(0.5)
        self.assertEqual([("input", "tap", "50", "92")], adb.calls)

    def test_bottom_navigation_text_taps_unique_clickable_ancestor_of_label(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return [element(0, "", bounds="[0,80][100,100]", parent_index=None), element(1, "Browse", clickable=False, bounds="[20,85][80,95]", parent_index=0)]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        _click_bottom_navigation_text(adb, "Browse")
        self.assertEqual([("input", "tap", "50", "90")], adb.calls)

    def test_bottom_navigation_text_allows_parent_to_extend_above_lower_strip(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return [element(0, "", bounds="[0,60][100,95]"), element(1, "Browse", clickable=False, bounds="[20,85][80,95]", parent_index=0)]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        _click_bottom_navigation_text(adb, "Browse")
        self.assertEqual([("input", "tap", "50", "77")], adb.calls)

    def test_bottom_navigation_text_rejects_non_navigation_or_non_clickable_matches(self):
        class FakeAdb:
            def __init__(self, values):
                self.values = values
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return self.values

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb([element(0, "Browse", bounds="[0,40][100,60]")])
        with self.assertRaises(ElementNotFoundError):
            _click_bottom_navigation_text(adb, "Browse")
        self.assertEqual([], adb.calls)

    def test_bottom_navigation_text_rejects_ambiguous_matches_without_tapping(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return [element(0, "Browse", bounds="[0,82][45,100]"), element(1, "Browse", bounds="[55,82][100,100]")]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        with self.assertRaises(AmbiguousSelectorError):
            _click_bottom_navigation_text(adb, "Browse")
        self.assertEqual([], adb.calls)

    def test_bottom_navigation_text_uses_unique_label_when_ancestor_is_missing(self):
        class FakeAdb:
            def __init__(self, values):
                self.values = values
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return self.values

            def shell(self, *args):
                self.calls.append(args)

        missing = FakeAdb([element(1, "Browse", clickable=False, bounds="[20,85][80,95]", parent_index=9)])
        _click_bottom_navigation_text(missing, "Browse")
        self.assertEqual([("input", "tap", "50", "90")], missing.calls)

        ambiguous = FakeAdb([
            element(0, "", clickable=True, bounds="[0,80][100,100]", parent_index=2),
            element(1, "Browse", clickable=False, bounds="[20,85][80,95]", parent_index=0),
            element(2, "", clickable=True, bounds="[0,80][100,100]"),
        ])
        with self.assertRaises(AmbiguousSelectorError):
            _click_bottom_navigation_text(ambiguous, "Browse")
        self.assertEqual([], ambiguous.calls)

    def test_tab_text_taps_unique_clickable_ancestor_in_upper_tab_band(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return [element(0, "", bounds="[0,20][100,40]"), element(1, "For You", clickable=False, bounds="[20,25][80,35]", parent_index=0)]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        _click_tab_text(adb, "For You")
        self.assertEqual([("input", "tap", "50", "30")], adb.calls)

    def test_tab_text_rejects_absent_or_ambiguous_upper_tab_labels(self):
        class FakeAdb:
            def __init__(self, values):
                self.values = values
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return self.values

            def shell(self, *args):
                self.calls.append(args)

        for values in (
            [element(0, "For You", bounds="[0,50][100,60]")],
            [element(0, "For You", bounds="[0,20][45,40]"), element(1, "For You", bounds="[55,20][100,40]")],
        ):
            adb = FakeAdb(values)
            with self.assertRaises((ElementNotFoundError, AmbiguousSelectorError)):
                _click_tab_text(adb, "For You")
            self.assertEqual([], adb.calls)

    def test_tab_text_falls_back_to_unique_label_center_without_ancestor(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return [element(1, "For You", clickable=False, bounds="[20,25][80,35]", parent_index=9)]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        _click_tab_text(adb, "For You")
        self.assertEqual([("input", "tap", "50", "30")], adb.calls)

    def test_back_until_text_stops_without_back_when_target_is_visible(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def elements(self):
                return [element(0, "More")]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        with patch("tools.android_ui_hardened.time.sleep") as sleep:
            _back_until_text(adb, "More", 4, expected_package="app.test")
        self.assertEqual([], adb.calls)

    def test_ensure_bottom_navigation_text_does_not_reselect_selected_tab(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return [
                    element(0, "", clickable=True, bounds="[0,80][100,100]", selected=True),
                    element(1, "Browse", clickable=False, bounds="[20,85][80,95]", parent_index=0),
                ]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        _ensure_bottom_navigation_text(adb, "Browse")
        self.assertEqual([], adb.calls)

    def test_ensure_bottom_navigation_text_taps_unselected_tab(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []
                self.values = [
                    element(0, "", clickable=True, bounds="[0,80][100,100]", selected=False),
                    element(1, "Browse", clickable=False, bounds="[20,85][80,95]", parent_index=0),
                ]

            def display(self):
                return DisplayInfo(100, 100, "portrait")

            def elements(self):
                return self.values

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        with patch("tools.android_ui_hardened.time.sleep"):
            _ensure_bottom_navigation_text(adb, "Browse")
        self.assertEqual([("input", "tap", "50", "90")], adb.calls)

    def test_back_until_text_recovers_with_bounded_back_navigation(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []
                self.frames = [[element(0, "Advanced")], [element(0, "Settings")], [element(0, "More")]]

            def elements(self):
                return self.frames.pop(0)

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        with patch("tools.android_ui_hardened.time.sleep") as sleep:
            _back_until_text(adb, "More", 4, expected_package="app.test")
        self.assertEqual([("input", "keyevent", "4"), ("input", "keyevent", "4")], adb.calls)
        self.assertEqual(2, sleep.call_count)

    def test_back_until_text_fails_after_its_bound(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []
                self.frames = [[element(0, "Advanced")], [element(0, "Settings")]]

            def elements(self):
                return self.frames.pop(0)

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        with patch("tools.android_ui_hardened.time.sleep") as sleep:
            with self.assertRaises(ElementNotFoundError):
                _back_until_text(adb, "More", 1, expected_package="app.test")
        self.assertEqual([("input", "keyevent", "4")], adb.calls)
        self.assertEqual(1, sleep.call_count)

    def test_back_until_text_fails_closed_before_leaving_expected_package(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []
                self.frames = [[element(0, "Advanced")], [
                    UiElement(0, "", "", "", "android.view.View", "com.google.android.apps.nexuslauncher", True, False, False, True, True, False, False, False, False, Bounds.parse("[0,0][100,100]"), None, 0),
                ]]

            def elements(self):
                return self.frames.pop(0)

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        with patch("tools.android_ui_hardened.time.sleep"):
            with self.assertRaises(ActionVerificationError):
                _back_until_text(adb, "More", 4, expected_package="app.test")
        self.assertEqual([("input", "keyevent", "4")], adb.calls)

    def test_route_executes_semantic_steps_and_final_verification(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []
                self.frames = [[element(0, "Browse")], [element(0, "For You")], [element(0, "For You")]]

            def elements(self):
                return self.frames.pop(0)

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        execute_route(adb, {
            "steps": [{"action": "click-text", "value": "Browse"}, {"action": "click-text", "value": "For You"}],
            "verify": {"selector": "text", "value": "For You"},
        }, verification_timeout=0)
        self.assertEqual(adb.calls, [("input", "tap", "50", "50"), ("input", "tap", "50", "50")])

    def test_route_rejects_ambiguous_selector_without_tapping(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def elements(self):
                return [element(0, "Browse"), element(1, "Browse", bounds="[0,100][100,200]")]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        with self.assertRaises(AmbiguousSelectorError):
            execute_route(adb, {"steps": [{"action": "click-text", "value": "Browse"}], "verify": {"selector": "text", "value": "Browse"}}, verification_timeout=0)
        self.assertEqual(adb.calls, [])

    def test_route_final_verification_failure_is_reported(self):
        class FakeAdb:
            def elements(self):
                return [element(0, "Browse")]

            def shell(self, *args):
                pass

        with self.assertRaises(ActionVerificationError):
            execute_route(FakeAdb(), {"steps": [{"action": "click-text", "value": "Browse"}], "verify": {"selector": "text", "value": "For You"}}, verification_timeout=0)

    def test_route_pager_verifies_target_after_bounded_swipes(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def display(self):
                return DisplayInfo(1000, 2000, 420)

            def elements(self):
                return [element(0, "For You")]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        execute_route(
            adb,
            {"steps": [{"action": "swipe-pager-to-text", "value": "For You", "swipes": 2}], "verify": {"selector": "text", "value": "For You"}},
            verification_timeout=0,
        )
        self.assertEqual(2, len(adb.calls))
        self.assertTrue(all(call[0:2] == ("input", "swipe") for call in adb.calls))

    def test_route_pager_fails_at_target_check_when_target_is_absent(self):
        class FakeAdb:
            def __init__(self):
                self.calls = []

            def display(self):
                return DisplayInfo(1000, 2000, 420)

            def elements(self):
                return [element(0, "Browse")]

            def shell(self, *args):
                self.calls.append(args)

        adb = FakeAdb()
        with self.assertRaises(ActionVerificationError):
            execute_route(
                adb,
                {"steps": [{"action": "swipe-pager-to-text", "value": "For You", "swipes": 2}], "verify": {"selector": "text", "value": "For You"}},
                verification_timeout=0,
            )
        self.assertEqual(2, len(adb.calls))

    def test_route_dry_run_never_constructs_adb_or_mutates_a_device(self):
        with patch("tools.android_ui_hardened.Adb", side_effect=AssertionError("dry-run must not construct ADB")):
            self.assertEqual(main(["route", "for-you", "--dry-run"]), 0)


if __name__ == "__main__":
    unittest.main()
