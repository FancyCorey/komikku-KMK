import unittest

from tools.android_ui_hardened import Bounds, UiElement
from tools.android_ui_hardened_entry import _deduplicate


def element(index: int, text: str) -> UiElement:
    return UiElement(
        index=index,
        text=text,
        description="",
        resource_id="",
        class_name="android.widget.TextView",
        package_name="app.komikku.dev",
        clickable=True,
        long_clickable=False,
        scrollable=False,
        enabled=True,
        focusable=False,
        focused=False,
        selected=False,
        checked=False,
        checkable=False,
        bounds=Bounds(0, 0, 100, 50),
        parent_index=None,
        depth=0,
    )


class HardenedEntryTests(unittest.TestCase):
    def test_reindexing_does_not_pass_duplicate_index(self):
        values = _deduplicate([element(9, "Browse"), element(10, "Browse")])
        self.assertEqual([0], [item.index for item in values])


if __name__ == "__main__":
    unittest.main()
