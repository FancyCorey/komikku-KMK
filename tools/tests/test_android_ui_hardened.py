import json
import tempfile
import unittest
from pathlib import Path

from tools.android_ui_hardened import (
    AmbiguousSelectorError,
    Bounds,
    InvalidBoundsError,
    Selector,
    UiElement,
    deduplicate,
    resolve,
    validate_routes,
)


def element(index: int, text: str, clickable: bool = True, bounds: str = "[0,0][100,100]") -> UiElement:
    return UiElement(index, text, "", "", "android.view.View", "app.test", clickable, False, False, True, True, False, False, False, False, Bounds.parse(bounds), None, 0)


class AndroidUiHardenedTests(unittest.TestCase):
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

    def test_ambiguous_selector_never_picks_first(self):
        with self.assertRaises(AmbiguousSelectorError):
            resolve([element(0, "Browse"), element(1, "Browse", bounds="[0,100][100,200]")], Selector(text="Browse"))

    def test_selector_occurrence_is_explicit(self):
        selected = resolve([element(0, "Browse"), element(1, "Browse", bounds="[0,100][100,200]")], Selector(text="Browse", occurrence=1))
        self.assertEqual(selected.index, 1)

    def test_route_schema_requires_verification(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "routes.json"
            path.write_text(json.dumps({"broken": {"steps": [{"action": "click-text", "value": "Browse"}]}}), encoding="utf-8")
            with self.assertRaises(Exception):
                validate_routes(path)


if __name__ == "__main__":
    unittest.main()
