#!/usr/bin/env python3
"""Unit regressions for the real-APK verifier's XML comparison."""
import re
import unittest
import xml.etree.ElementTree as ET
from verify_ninegag import ROOT, VERSIONS, canonical


class CanonicalXmlTests(unittest.TestCase):
    def test_inline_text_tail_change_is_detected(self):
        original = ET.fromstring('<string>Read <b>this</b> safe</string>')
        changed = ET.fromstring('<string>Read <b>this</b> CHANGED</string>')
        self.assertNotEqual(canonical(original), canonical(changed))

    def test_formatting_only_tail_whitespace_is_ignored(self):
        original = ET.fromstring('<resources><string name="x">value</string></resources>')
        formatted = ET.fromstring('<resources>\n  <string name="x">value</string>\n</resources>')
        self.assertEqual(canonical(original), canonical(formatted))


class VersionTableTests(unittest.TestCase):
    def test_descriptors_mirror_patch_symbols(self):
        source = (ROOT / "patches/src/main/kotlin/app/template/patches/ninegag/shared/Symbols.kt").read_text()
        rows = dict(re.findall(r'"([0-9.]+)" to NinegagSymbols\((.*?)\n    \)', source, re.S))
        self.assertEqual(set(rows), set(VERSIONS))
        for version, body in rows.items():
            s = dict(re.findall(r'(\w+) = "([^"]+)"', body))
            v = VERSIONS[version]
            self.assertEqual(v["display"], s["feedQueryClass"] + "->j(ILjava/lang/String;Z)Ljava/util/List;")
            self.assertEqual(v["database"], s["feedQueryClass"] + "->d(Ljava/lang/String;IIIZ)" + s["feedQueryBuilder"])
            self.assertEqual(v["banner"], s["bannerClass"] + "->f(Landroid/widget/FrameLayout;" + s["bannerCallback"] + ")V")
            self.assertEqual(v["policy"], s["adPolicyClass"] + "->b(Z" + s["paidAccess"] + s["adEntitlement"] + ")" + s["blockReason"])
            self.assertEqual(v["blocker"], s["blockReason"] + "->a:" + s["blockReason"])


if __name__ == '__main__':
    unittest.main()
