#!/usr/bin/env python3
"""Generate the approved 650/100/24 font instance and outlined lockups from official font bytes.
Usage: python3 Tools/generate_foldegram_wordmark.py /path/to/BricolageGrotesque.ttf
Requires fontTools and uharfbuzz. Does not download files or alter the approved crane.
"""
from pathlib import Path
import sys,hashlib,xml.etree.ElementTree as ET
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
import uharfbuzz as hb
ROOT=Path(__file__).resolve().parents[1]
source=Path(sys.argv[1]); raw=source.read_bytes()
assert hashlib.sha256(raw).hexdigest()=='413e7357809ddd12fd80a96a8a396de0e401638d4acd3cb3e37532f0472ac682'
font=TTFont(source,recalcTimestamp=False)
font=instantiateVariableFont(font,{'wght':650,'wdth':100,'opsz':24},inplace=False)
font.recalcTimestamp=False
asset=ROOT/'TMessagesProj/src/main/assets/fonts/Foldegram-Bricolage650.ttf'
font.save(asset)
face=hb.Face(asset.read_bytes()); shaped=hb.Font(face);hb.ot_font_set_funcs(shaped)
buf=hb.Buffer();buf.add_str('Foldegram');buf.guess_segment_properties();hb.shape(shaped,buf)
glyphs=font.getGlyphSet();order=font.getGlyphOrder();scale=20/font['head'].unitsPerEm
bounds=[]
for info,pos in zip(buf.glyph_infos,buf.glyph_positions):
 p=BoundsPen(glyphs);glyphs[order[info.codepoint]].draw(p)
 if p.bounds:bounds.append((p.bounds[1]+pos.y_offset,p.bounds[3]+pos.y_offset))
ymin=min(x[0] for x in bounds);ymax=max(x[1] for x in bounds)
baseline=13+(ymin+ymax)*scale/2
x=34;paths=[]
for info,pos in zip(buf.glyph_infos,buf.glyph_positions):
 p=SVGPathPen(glyphs,ntos=lambda v:format(v,'.4f').rstrip('0').rstrip('.'))
 glyphs[order[info.codepoint]].draw(TransformPen(p,(scale,0,0,-scale,x+pos.x_offset*scale,baseline-pos.y_offset*scale)))
 paths.append(p.getCommands());x+=pos.x_advance*scale
width=round(x+1,3)
crane=ET.parse(ROOT/'TMessagesProj/src/main/res/drawable/foldegram_crane_mark.xml').getroot()
crane_paths=[p.attrib['{http://schemas.android.com/apk/res/android}pathData'] for p in crane]
svg_crane=''.join(f'<path d="{d}"/>' for d in crane_paths)
svg_text=''.join(f'<path d="{d}"/>' for d in paths)
lockup=f'<g fill="#fff"><g transform="translate(1.826 1.425) scale(.375)">{svg_crane}</g>{svg_text}</g>'
svg=f'''<svg xmlns="http://www.w3.org/2000/svg" width="{round(width*3+64,3)}" height="142" viewBox="0 0 {round(width*3+64,3)} 142" role="img" aria-label="Foldegram">
<title>Foldegram — Bricolage Grotesque 650, width 100, optical size 24</title>
<rect width="100%" height="100%" rx="20" fill="#152733"/>
<g transform="translate(32 32) scale(3)">{lockup}</g>
</svg>\n'''
(ROOT/'docs/foldegram/wordmark.svg').write_text(svg)
xml=f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Outlined Bricolage Grotesque 650/100/24; font is SIL OFL 1.1. Approved crane unchanged. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{width}dp" android:height="26dp" android:viewportWidth="{width}" android:viewportHeight="26">
<group android:translateX="1.826" android:translateY="1.425" android:scaleX="0.375" android:scaleY="0.375">
'''+''.join(f'<path android:fillColor="#FFFFFF" android:pathData="{d}"/>\n' for d in crane_paths)+ '</group>\n'+''.join(f'<path android:fillColor="#FFFFFF" android:pathData="{d}"/>\n' for d in paths)+'</vector>\n'
(ROOT/'TMessagesProj/src/main/res/drawable/foldegram_wordmark.xml').write_text(xml)
print(f'Generated static font ({asset.stat().st_size} bytes), {width}dp lockup, exact axes 650/100/24.')
