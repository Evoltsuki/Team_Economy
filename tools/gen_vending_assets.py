"""Detailed two-block vending cabinets, using native Minecraft model elements."""
from pathlib import Path
import json, random
from PIL import Image, ImageDraw

RES=Path(__file__).resolve().parents[1]/'src/main/resources'
A=RES/'assets/teamecon'

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def visible_surfaces(elements):
    """Subtract covered face rectangles, including coplanar overlays, instead of z-fighting."""
    def subtract(rect, cut):
        x,y,X,Y=rect;a,b,A,B=cut
        a,b,A,B=max(x,a),max(y,b),min(X,A),min(Y,B)
        if A-a<1e-6 or B-b<1e-6:return [rect]
        return [r for r in [(x,y,a,Y),(A,y,X,Y),(a,y,A,b),(a,B,A,Y)] if r[2]-r[0]>1e-6 and r[3]-r[1]>1e-6]
    result=[]
    for i,e in enumerate(elements):
        for face,axis,positive in [('west',0,False),('east',0,True),('down',1,False),('up',1,True),('north',2,False),('south',2,True)]:
            uv=[n for n in range(3) if n!=axis];u,v=uv
            plane=e['to' if positive else 'from'][axis]
            rects=[(e['from'][u],e['from'][v],e['to'][u],e['to'][v])]
            for j,other in enumerate(elements):
                if i==j:continue
                low,high=other['from'][axis],other['to'][axis]
                covers=(low<plane-1e-6 and high>plane+1e-6)
                covers |= (abs(low-plane)<1e-6 and high>plane+1e-6) if positive else (abs(high-plane)<1e-6 and low<plane-1e-6)
                # Later trim/detail material owns a shared outer face.
                covers |= j>i and abs((high if positive else low)-plane)<1e-6
                if covers:
                    cut=(other['from'][u],other['from'][v],other['to'][u],other['to'][v])
                    rects=[part for rect in rects for part in subtract(rect,cut)]
                    if not rects:break
            for x,y,X,Y in rects:
                a=e['from'].copy();b=e['to'].copy();a[u]=x;a[v]=y;b[u]=X;b[v]=Y
                result.append({'from':a,'to':b,'faces':{face:e['faces'][face]}})
    return result

def main():
    palette={'body':(24,42,50),'side':(36,57,64),'trim':(115,142,148),'edge':(188,202,192),
             'brass':(189,146,65),'gold':(236,204,124),'black':(9,20,25),'interior':(16,35,41),
             'mint':(91,181,158),'purple':(171,129,203),'screen':(16,67,68),'lamp':(188,240,211)}
    rng=random.Random(71)
    for name,c in palette.items():
        im=Image.new('RGB',(32,32));pixels=im.load()
        for y in range(32):
            for x in range(32):
                shade=rng.choice([-2,-1,0,0,0,1,2])+(2 if name in ('trim','edge') and y%4==0 else 0)
                pixels[x,y]=tuple(max(0,min(255,v+shade)) for v in c)
        im.save(A/f'textures/block/vendor_{name}.png')
    for name in ['shop_machine','blind_box_machine']:
        accent='mint' if name=='shop_machine' else 'purple'
        elements=[]
        def box(x,y,z,X,Y,Z,material):
            # Model and BER share a south-facing (+Z) local front.
            a=[x,y,16-Z];b=[X,Y,16-z]
            elements.append({'from':a,'to':b,'faces':{face:{'texture':'#'+material,'uv':[0,0,16,16]} for face in ['up','down','north','south','east','west']}})
        # Separate adjustable feet, heavy bottom plinth, stepped shoulder and roof cap.
        for x in [1.5,12]:
            for z in [2,12]:
                box(x,0,z,x+2.5,1.2,z+2,'black');box(x-.2,1.0,z-.2,x+2.7,1.6,z+2.2,'trim')
        box(.8,1.5,1.4,15.2,3,15.3,'body');box(.7,2.6,1.1,15.3,3.3,15.4,'trim')
        box(1,3,7,15,29.2,15,'body')
        box(11.2,3,2,15,28.2,7,'body')
        # Full-depth side shells; layered edges avoid a plain rectangular silhouette.
        for x in [1,14.2]:
            box(x,3,1.1,x+.8,29.4,14.7,'side')
            box(x+.15,3.5,.8,x+.6,28.7,1.4,'trim')
        box(1.4,29,1.1,14.6,30.5,14.8,'trim')
        box(2,30.5,1.7,14,31.4,14.3,'body')
        box(2.7,31.4,2.2,13.3,31.9,13.5,'side')
        box(1.8,28.2,.65,14.2,30,2,'brass')
        box(2.25,28.5,.3,13.75,29.6,.65,'black')
        # Deep display cavity, with separate rear panel, jambs and shelf lips.
        box(2,13.9,6.5,11.2,27.8,7,'black')
        box(2.55,14.4,6.2,10.65,27.2,6.5,'interior')
        box(2,13.9,1.8,2.5,27.8,6.5,'black')
        box(10.7,13.9,1.8,11.2,27.8,6.5,'black')
        for x in [1.9,10.8]:
            box(x,13.5,.4,x+.65,28.2,2.5,'brass')
            box(x+.17,14.0,.18,x+.37,27.7,.45,'gold')
        for y in [13.5,20.4,27.7]:
            box(2.2,y,.4,11.2,y+.5,3,'trim')
        for y in [14.0,20.8]:
            box(2.5,y,.9,10.8,y+.3,2.8,'side')
            box(2.65,y+.1,.5,10.65,y+.3,.9,'edge')
            # Individual product labels, with tiny dark type-like pixels.
            for x in [3.2,5.9,8.6]:
                box(x,y-.55,.2,x+1.7,y-.05,.5,'black')
                box(x+.2,y-.4,.1,x+1.2,y-.22,.2,'gold')
        box(2.7,27.25,.8,10.6,27.55,2,'lamp')
        # Thin corner reflections: transparent centre stays unobstructed.
        box(2.8,25.9,.15,3,27,.24,'trim');box(3,26.8,.15,4.6,27,.24,'trim')
        box(10.2,15.1,.15,10.4,16.3,.24,'trim')
        # Asymmetric recessed control island: digital display, keypad, coin slot, return button.
        box(11.6,12.6,.45,14,27.8,2,'black')
        box(11.85,23.5,.18,13.8,26.9,.5,'trim');box(12.05,23.75,.05,13.6,26.65,.18,'screen')
        for y,w in [(26.1,1.15),(25.4,.75),(24.7,1)]:box(12.2,y,-.02,12.2+w,y+.14,.06,accent)
        for row in range(4):
            for col in range(3):
                x=11.95+col*.57;y=22.4-row*.65
                box(x,y,-.08,x+.4,y+.38,.25,'trim' if row<3 else accent)
        box(12,17.8,.05,13.7,18.8,.4,'brass');box(12.22,18.18,-.05,13.48,18.4,.05,'black')
        box(12.4,15.6,.0,13.3,16.5,.4,accent);box(12.6,14.1,.0,13.1,14.7,.35,'gold')
        # Lower delivery bay. A real recess with back, roof, floor and separate protective flap.
        box(2,4.1,.8,14,12.6,3,'side')
        box(2.8,5.1,.4,11.3,10.6,2,'brass');box(3.3,5.6,.1,10.8,10.15,1.2,'black')
        box(3.7,5.9,1.05,10.4,9.65,1.4,'interior')
        box(3.15,5.15,-.4,11,5.8,2.4,'trim');box(3.6,5.8,-.05,10.55,6.0,1.2,'edge')
        box(3.3,9.75,-.15,10.8,10.15,.6,'body')
        for x in [3.25,10.5]:box(x,9.3,-.22,x+.32,10.2,.5,'trim')
        box(3.4,11.5,.25,10.6,12.15,.55,accent)
        box(12,6.5,.35,13.5,11.8,.65,'black')
        for y in [7,8,9,10,11]:box(12.2,y,.15,13.3,y+.2,.35,'trim')
        # Side service panels, cooling louvers, rear hatch and hinges.
        for x in [.85,14.9]:
            box(x,5,5,x+.25,15,13,'black')
            for y in range(6,14):box(x-.08,y,5.5,x+.35,y+.28,12.4,'trim')
            box(x,17,4,x+.25,26,13,'side')
            for y in [17.5,25.5]:box(x-.06,y,4.5,x+.3,y+.35,5,'brass')
        box(2.5,4.5,14.95,13.5,27.5,15.25,'black');box(2.8,4.8,15.25,13.2,27.2,15.45,'side')
        for y in [7,23]:box(2.5,y,15.4,3.1,y+2,15.8,'trim')
        box(11.5,15,15.4,12.1,18,15.8,'trim')
        for x in [1.5,14.15]:
            for y in [4,12.8,28.2]:box(x,y,-.02,x+.3,y+.3,.3,'edge')
        # Distinct illuminated gift motif on the mystery cabinet's service door.
        if name=='blind_box_machine':
            for x in [.55,15.2]:
                box(x,19,5,x+.22,24,10,accent)
                box(x-.04,19,7.2,x+.27,24,7.8,'gold')
                box(x-.05,21.2,5,x+.28,21.8,10,'gold')
        elements=visible_surfaces(elements)
        # Split at the chunk-safe block boundary; no giant block model bounds.
        for half in ['bottom','top']:
            low,high=(0,16) if half=='bottom' else (16,32)
            clipped=[]
            for e in elements:
                a=e['from'].copy();b=e['to'].copy()
                if b[1]<=low or a[1]>=high:continue
                faces=e['faces'].copy()
                if b[1]>high:faces.pop('up',None)
                if a[1]<low:faces.pop('down',None)
                if not faces:continue
                a[1]=max(a[1],low)-low;b[1]=min(b[1],high)-low
                clipped.append(dict(e,**{'from':a,'to':b,'faces':faces}))
            write(A/f'models/block/{name}_{half}.json',{'ambientocclusion':True,'textures':{k:f'teamecon:block/vendor_{k}' for k in palette},'elements':clipped})
        write(A/f'blockstates/{name}.json',{'variants':{f'facing={face},half={half}':{'model':f'teamecon:block/{name}_{half}','y':angle} for face,angle in [('north',180),('east',270),('south',0),('west',90)] for half in ['bottom','top']}})
        # Thumbnail preserves the new silhouette and asymmetric controls at inventory scale.
        im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
        d.polygon([(12,10),(20,5),(49,5),(43,10)],fill='#8daba9',outline='#202f36')
        d.polygon([(43,10),(49,5),(49,55),(43,60)],fill='#29434a',outline='#799a9f')
        d.rectangle((12,10,43,59),fill='#233c43',outline='#aec5bb',width=2)
        d.rectangle((15,12,40,17),fill='#121f26',outline='#ba944a');d.line((20,14,35,14),fill='#e9ce7f')
        d.rectangle((15,19,33,44),fill='#10282c',outline='#c5a263');d.rectangle((16,31,32,32),fill='#a9b9ae')
        for x in [19,26]:
            for y in [23,35]:d.rectangle((x,y,x+4,y+6),fill='#78b999' if name=='shop_machine' else '#bc94d4',outline='#dae4ba')
        d.rectangle((35,20,40,28),fill='#62b8a1' if name=='shop_machine' else '#ac8bd0')
        for y in [31,34,37]:
            for x in [35,38]:d.rectangle((x,y,x+1,y+1),fill='#b6c9c3')
        d.rectangle((18,48,33,55),fill='#0a1e26',outline='#ac914e');d.line((18,55,34,55),fill='#d2d9c0',width=2)
        d.rectangle((14,60,19,62),fill='#15262d');d.rectangle((37,60,42,62),fill='#15262d')
        im.save(A/f'textures/item/{name}.png')

if __name__=='__main__':main()
