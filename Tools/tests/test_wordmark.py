from pathlib import Path
import unittest,xml.etree.ElementTree as ET,hashlib
from fontTools.ttLib import TTFont
ROOT=Path(__file__).resolve().parents[2]
class Wordmark(unittest.TestCase):
 def test_static_font_and_original_license(self):
  font=TTFont(ROOT/'TMessagesProj/src/main/assets/fonts/Foldegram-Bricolage650.ttf')
  self.assertNotIn('fvar',font)
  self.assertEqual(font['OS/2'].usWeightClass,650)
  self.assertTrue(all(ord(c) in font.getBestCmap() for c in 'Foldegram'))
  license=(ROOT/'TMessagesProj/src/main/assets/fonts/BricolageGrotesque-OFL.txt').read_bytes()
  self.assertEqual(hashlib.sha256(license).hexdigest(),'4b5a7d8f37f5602621c8a8d7358a6a2e71317e6c231c661e15aef0275d3e07ba')
 def test_lockup_preserves_crane_and_outlines_wordmark(self):
  a='{http://schemas.android.com/apk/res/android}'
  base=ROOT/'TMessagesProj/src/main/res/drawable'
  crane=ET.parse(base/'foldegram_crane_mark.xml').getroot()
  lockup=ET.parse(base/'foldegram_wordmark.xml').getroot()
  self.assertEqual([p.get(a+'pathData') for p in crane],[p.get(a+'pathData') for p in lockup.find('group')])
  self.assertEqual(len(lockup.findall('path')),9)
  svg=ET.parse(ROOT/'docs/foldegram/wordmark.svg').getroot()
  self.assertFalse(svg.findall('.//{http://www.w3.org/2000/svg}text'))
 def test_brand_font_scope_is_explicit(self):
  ui=ROOT/'TMessagesProj/src/main/java/org/telegram/ui'
  helper=(ui/'Components/FoldegramWordmark.java').read_text()
  self.assertIn('start + 9',helper)
  self.assertIn('TypefaceSpan(typeface())',helper)
  self.assertNotIn('FoldegramWordmark',(ui/'ChatActivity.java').read_text())
  self.assertIn('position == 0 ? org.telegram.ui.Components.FoldegramWordmark.typeface()', (ui/'IntroActivity.java').read_text())
if __name__=='__main__':unittest.main()
