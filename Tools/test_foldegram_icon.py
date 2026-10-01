#!/usr/bin/env python3
"""Regression checks for the original adaptive crane (no Android runtime needed)."""
import math
import re
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'TMessagesProj_AppFoldegram/src/common/res'
A = '{http://schemas.android.com/apk/res/android}'

class CraneIconTest(unittest.TestCase):
    def test_foreground_safe_circle(self):
        for name in ('foldegram_crane', 'foldegram_crane_monochrome'):
            root = ET.parse(RES / f'drawable/{name}.xml').getroot()
            self.assertEqual(root.get(A + 'viewportWidth'), '108')
            self.assertEqual(root.get(A + 'viewportHeight'), '108')
            points = []
            for path in root.findall('path'):
                data = path.get(A + 'pathData')
                self.assertFalse(re.search(r'[CQAHVSTcqahvst]', data), 'Check curves/extents separately')
                for x,y in re.findall(r'(\d+(?:\.\d+)?),(\d+(?:\.\d+)?)', data):
                    x,y = float(x),float(y)
                    self.assertLessEqual(math.hypot(x-54,y-54), 33)
                    points.append((x,y))
            self.assertGreaterEqual(max(y for x,y in points)-min(y for x,y in points),48)
            self.assertGreaterEqual(max(x for x,y in points)-min(x for x,y in points),48)
    def test_monochrome_is_white_without_background(self):
        paths=ET.parse(RES/'drawable/foldegram_crane_monochrome.xml').getroot().findall('path')
        self.assertEqual(len(paths),1)
        self.assertEqual(paths[0].get(A+'fillColor'),'#FFFFFF')
    def test_legacy_call_icon_matches(self):
        self.assertEqual((RES/"drawable-anydpi/ic_launcher_dr.xml").read_text(), (RES/"mipmap-anydpi/foldegram_launcher.xml").read_text())
    def test_adaptive_resources(self):
        for version in (26,33):
            root=ET.parse(RES/f'mipmap-anydpi-v{version}/foldegram_launcher.xml').getroot()
            self.assertEqual(root.tag,'adaptive-icon')
            self.assertEqual(root.find('foreground').get(A+'drawable'),'@drawable/foldegram_crane')
            self.assertEqual(root.find('background').get(A+'drawable'),'@color/foldegram_ink')
        self.assertEqual(root.find('monochrome').get(A+'drawable'),'@drawable/foldegram_crane_monochrome')

if __name__ == '__main__': unittest.main()
