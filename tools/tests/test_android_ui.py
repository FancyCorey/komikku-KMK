import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch

from tools import android_ui


class ConsoleEncodingTests(unittest.TestCase):
    def test_configures_utf8_for_reconfigurable_streams(self):
        class Stream:
            def __init__(self):
                self.calls = []

            def reconfigure(self, **kwargs):
                self.calls.append(kwargs)

        stdout = Stream()
        stderr = Stream()
        with patch.object(android_ui.sys, "stdout", stdout), patch.object(android_ui.sys, "stderr", stderr):
            android_ui.configure_console_encoding()

        expected = {"encoding": "utf-8", "errors": "replace"}
        self.assertEqual(stdout.calls, [expected])
        self.assertEqual(stderr.calls, [expected])


class AdbPathTests(unittest.TestCase):
    def test_explicit_adb_path_has_precedence(self):
        with patch.dict(os.environ, {"ADB_PATH": "configured-adb"}):
            self.assertEqual("configured-adb", android_ui.find_adb())

    def test_falls_back_to_control_root_local_sdk(self):
        with tempfile.TemporaryDirectory() as directory:
            source_root = Path(directory) / "worktrees" / "active"
            workspace_root = Path(directory)
            adb = workspace_root / "komikku-source" / ".tools" / "android-sdk" / "platform-tools" / "adb.exe"
            adb.parent.mkdir(parents=True)
            adb.write_bytes(b"")
            with patch.dict(os.environ, {}, clear=False):
                os.environ.pop("ADB_PATH", None)
                with patch.object(android_ui, "ROOT", source_root), patch.object(android_ui, "find_workspace_root", return_value=workspace_root):
                    self.assertEqual(str(adb), android_ui.find_adb())


class AndroidUiDumpTests(unittest.TestCase):
    def test_text_is_utf8_decoded_but_binary_output_is_not_decoded(self):
        with patch.dict(android_ui.os.environ, {"ADB_PATH": "adb-for-test"}):
            adb = android_ui.Adb(serial="emulator-5554")
            completed = MagicMock(returncode=0, stdout="source •", stderr="")
            with patch("tools.android_ui.subprocess.run", return_value=completed) as run:
                self.assertEqual("source •", adb.run("shell", "cat"))
                self.assertEqual("utf-8", run.call_args.kwargs["encoding"])
                self.assertEqual("replace", run.call_args.kwargs["errors"])
            completed.stdout = b"png"
            with patch("tools.android_ui.subprocess.run", return_value=completed) as run:
                self.assertEqual(b"png", adb.run("exec-out", "screencap", "-p", binary=True))
                self.assertIsNone(run.call_args.kwargs["encoding"])
                self.assertIsNone(run.call_args.kwargs["errors"])

    def test_streamed_dump_is_trimmed_to_the_xml_document(self):
        with patch.dict(android_ui.os.environ, {"ADB_PATH": "adb-for-test"}):
            adb = android_ui.Adb(serial="emulator-5554")
            adb.ensure_target = MagicMock()
            adb.run = MagicMock(
                return_value="<?xml version='1.0'?><hierarchy><node /></hierarchy>UI hierchary dumped to: /dev/tty\n",
            )

            self.assertEqual(
                adb.ui_xml(),
                "<?xml version='1.0'?><hierarchy><node /></hierarchy>",
            )
            adb.run.assert_called_once_with("exec-out", "uiautomator", "dump", "/dev/tty", timeout=20)

    def test_non_rendered_zero_bounds_nodes_are_not_exposed(self):
        adb = object.__new__(android_ui.Adb)
        adb.ui_xml = MagicMock(
            return_value=(
                "<?xml version='1.0'?><hierarchy>"
                '<node text="Visible" enabled="true" clickable="false" scrollable="false" '
                'bounds="[1,1][2,2]" />'
                '<node text="Hidden" enabled="true" clickable="false" scrollable="false" '
                'bounds="[0,0][0,0]" />'
                "</hierarchy>"
            ),
        )

        self.assertEqual([element.text for element in adb.elements()], ["Visible"])

    def test_busy_automation_session_has_a_specific_error(self):
        with patch.dict(android_ui.os.environ, {"ADB_PATH": "adb-for-test"}):
            adb = android_ui.Adb(serial="emulator-5554")
            adb.ensure_target = MagicMock()
            adb.run = MagicMock(return_value="Killed\n")

            with self.assertRaisesRegex(android_ui.UiError, "automation is busy"):
                adb.ui_xml()

    def test_scroll_allows_multiple_changed_hierarchies_and_stops_at_bound(self):
        frame = lambda label: android_ui.Element(label, "", "", "View", False, True, True, False, "[0,0][100,100]")
        adb = MagicMock()
        adb.elements.side_effect = [[frame("One")], [frame("Two")], [frame("Three")], [frame("Target")]]
        android_ui.scroll_to_text(adb, "Target", click_target=False, max_swipes=3)
        self.assertEqual(3, len(adb.shell.call_args_list))

        adb = MagicMock()
        adb.elements.side_effect = [[frame("One")], [frame("Two")]]
        with self.assertRaisesRegex(android_ui.UiError, "after 1 bounded swipes"):
            android_ui.scroll_to_text(adb, "Target", click_target=False, max_swipes=1)
        self.assertEqual(1, len(adb.shell.call_args_list))


if __name__ == "__main__":
    unittest.main()
