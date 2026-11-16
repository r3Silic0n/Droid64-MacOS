import sys
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen
rom=open(sys.argv[1],'rb').read()[0:1024]
U=1024; P=U//8
names=['.notdef']; glyphs={}; cmap={}; metrics={}
def glyph(bits):
    pen=TTGlyphPen(None)
    for row in range(8):
        b=bits[row]; x=0
        while x<8:
            if b&(0x80>>x):
                x0=x
                while x<8 and b&(0x80>>x): x+=1
                y1=(8-row)*P; y0=y1-P
                pen.moveTo((x0*P,y0)); pen.lineTo((x0*P,y1)); pen.lineTo((x*P,y1)); pen.lineTo((x*P,y0)); pen.closePath()
            else: x+=1
    return pen.glyph()
glyphs['.notdef']=glyph([0]*8); metrics['.notdef']=(U,0)
for sc in range(256):
    bits=list(rom[(sc&0x7f)*8:(sc&0x7f)*8+8])
    if sc&0x80: bits=[(~v)&0xff for v in bits]
    n='sc%02X'%sc; names.append(n); glyphs[n]=glyph(bits); metrics[n]=(U,0); cmap[0xE000+sc]=n
cmap[0x20]='sc20'
fb=FontBuilder(U,isTTF=True); fb.setupGlyphOrder(names); fb.setupCharacterMap(cmap); fb.setupGlyf(glyphs)
for n,g in glyphs.items():
    g.recalcBounds(None) if False else None
fb.setupHorizontalMetrics(metrics); fb.setupHorizontalHeader(ascent=U,descent=0)
fb.setupNameTable({'familyName':'DroiD64 C64','styleName':'Regular'})
fb.setupOS2(sTypoAscender=U,sTypoDescender=0,sTypoLineGap=0,usWinAscent=U,usWinDescent=0,fsSelection=0x40|0x80,version=4)
fb.setupPost(isFixedPitch=1)
f=fb.font; gl=f['glyf']
for n in names:
    g=gl[n]; g.recalcBounds(gl)
    f['hmtx'].metrics[n]=(U, g.xMin if g.numberOfContours else 0)
fb.save(sys.argv[2])
