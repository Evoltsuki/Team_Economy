"""Small handbook diagrams built from the project's existing procedural art."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import math

ASSETS=Path(__file__).resolve().parents[1]/'src/main/resources/assets/teamecon'
OUT=ASSETS/'textures/gui/guide'
GOLD='#f0cc7d'; GREEN='#8ae0b3'; WHITE='#edf1eb'; INK='#172c34'

def font(size=18):
    return ImageFont.truetype('C:/Windows/Fonts/arial.ttf',size)

def canvas():
    im=Image.new('RGBA',(256,256),INK);d=ImageDraw.Draw(im)
    d.rounded_rectangle((3,3,252,252),radius=12,outline=GOLD,width=3)
    return im,d

def icon(im,name,x,y,size=64):
    source=Image.open(ASSETS/f'textures/item/{name}.png').convert('RGBA')
    im.alpha_composite(source.resize((size,size),Image.Resampling.NEAREST),(x,y))

def label(d,text,x,y,size=18,color=WHITE):
    d.text((x,y),text,font=font(size),fill=color,anchor='mm')

def arrow(d,start,end,color=GREEN,width=4):
    d.line((*start,*end),fill=color,width=width)
    angle=math.atan2(end[1]-start[1],end[0]-start[0])
    d.polygon([end,(end[0]-11*math.cos(angle-.5),end[1]-11*math.sin(angle-.5)),
               (end[0]-11*math.cos(angle+.5),end[1]-11*math.sin(angle+.5))],fill=color)

def save(name,im):
    OUT.mkdir(parents=True,exist_ok=True);im.save(OUT/f'{name}.png')

def cherries(d,x,y):
    d.line((x-10,y-10,x,y-29,x+13,y-12),fill=GREEN,width=3)
    d.ellipse((x-23,y-15,x-1,y+9),fill='#ef7178',outline='#ffccd1',width=2)
    d.ellipse((x+1,y-10,x+23,y+14),fill='#e95765',outline='#ffccd1',width=2)

def diamond(d,x,y):
    d.polygon([(x-23,y-11),(x-11,y-23),(x+11,y-23),(x+23,y-11),(x,y+17)],fill='#60dfe6',outline='#d5fdff',width=2)
    d.line((x-23,y-11,x+23,y-11),fill='#e8ffff',width=2)
    d.line((x-11,y-23,x-8,y-11,x,y+17,x+8,y-11,x+11,y-23),fill='#b5f6fb',width=2)

def main():
    im,d=canvas();icon(im,'guide_book',73,18,112)
    for i,name in enumerate(['hilo_table','penguin_machine','color_wheel_table','roulette_table','slot_machine','multiplier_machine']):
        icon(im,name,23+i%3*79,139+i//3*50,46)
    save('welcome',im)

    im,d=canvas();icon(im,'shop_machine',18,17,94)
    for i in range(9):d.rectangle((133+i%3*29,30+i//3*27,156+i%3*29,52+i//3*27),outline='#85aca7',width=2)
    d.rectangle((25,153,86,215),outline=WHITE,width=3);diamond(d,56,188)
    arrow(d,(102,184),(150,184))
    for y in [201,193,185,177]:d.ellipse((172,y-13,226,y+6),fill=GOLD,outline='#997539',width=2)
    save('economy',im)

    im,d=canvas();icon(im,'multiplier_machine',91,15,74)
    for i,word in enumerate(['-100','-10','+10','+100','×']):
        x=12+i*47;d.rectangle((x,110,x+43,147),fill='#344e56',outline=GOLD,width=2);label(d,word,x+22,129,14)
    label(d,'200',60,195,25);arrow(d,(100,195),(146,195));label(d,'2000',200,195,25,color=GREEN)
    save('controls',im)

    im,d=canvas()
    for i,name in enumerate(['hilo_table','penguin_machine','color_wheel_table','roulette_table','slot_machine']):
        x=16+i*46;y=186-i*31;d.rectangle((x,y,x+40,230),fill='#31584e',outline=GREEN,width=2)
        icon(im,name,x+1,y-45,39);label(d,str(i+1),x+20,y+20,18,color=GOLD)
    save('levels',im)

    im,d=canvas();icon(im,'slot_machine',102,9,52)
    for y,symbol,mult in [(99,cherries,'×6'),(194,diamond,'×275')]:
        for x in [47,128,209]:
            d.rounded_rectangle((x-31,y-35,x+31,y+26),radius=5,fill='#29434a',outline=GOLD,width=2);symbol(d,x,y)
        label(d,mult,128,y+40,20,color=GOLD)
    save('slots',im)

    im,d=canvas();icon(im,'hilo_table',94,9,68)
    for x,lo,hi,color in [(19,'1','5','#9483c7'),(138,'6','10','#538eb1')]:
        d.rounded_rectangle((x,90,x+99,200),radius=8,fill=color,outline=WHITE,width=2)
        label(d,lo,x+50,111,22);label(d,'–',x+50,139,22);label(d,hi,x+50,168,22)
        label(d,'x1.8',x+50,226,21,color=GOLD)
    save('hilo',im)

    for name,texture in [('color_wheel','prize_wheel'),('roulette','roulette_wheel')]:
        im,d=canvas();wheel=Image.open(ASSETS/f'textures/gui/{texture}.png').convert('RGBA').resize((196,196),Image.Resampling.LANCZOS)
        im.alpha_composite(wheel,(30,28));d.polygon([(115,14),(141,14),(128,43)],fill=GOLD,outline=WHITE,width=2)
        if name=='roulette':
            for x,color in [(69,'#eb6976'),(128,'#0b1722'),(187,'#49cb88')]:
                d.ellipse((x-16,222,x+16,250),fill=color,outline=GOLD,width=2)
            label(d,'0',187,236,17)
        save(name,im)

    im,d=canvas()
    for i,mult in enumerate(['×1','×1.2','×1.5','×2','×2.75']):
        x=15+i*47;y=201-i*28
        d.rectangle((x,y,x+38,y+12),fill='#7fcee3',outline='#d7ffff',width=2)
        d.rectangle((x+4,y+12,x+34,y+20),fill='#427b9c');label(d,mult,x+19,y+34,15)
    slime=Image.open(ASSETS/'textures/gui/slime_hop.png').convert('RGBA').resize((48,48),Image.Resampling.NEAREST)
    im.alpha_composite(slime,(58,122));arrow(d,(101,125),(141,94));arrow(d,(153,91),(188,63))
    save('penguin',im)

    im,d=canvas();icon(im,'multiplier_machine',15,10,51);label(d,'×1 → ×2 → …',156,36,21,color=GREEN)
    for x in [30,76,122,168,214]:d.line((x,76,x,215),fill='#375953',width=1)
    for y in [76,110,145,180,215]:d.line((30,y,226,y),fill='#375953',width=1)
    curve=[(31+t,215-int(18*math.exp(t/57))) for t in range(139)]
    d.line(curve,fill=GREEN,width=4);end=curve[-1];d.line((end,(end[0],215)),fill='#ef7d85',width=4)
    d.ellipse((end[0]-5,210,end[0]+5,220),fill='#ef7d85');label(d,'0',end[0]+18,215,18,color='#ef7d85')
    arrow(d,(30,226),(228,226),color='#94b5ab',width=2);label(d,'t',234,231,16)
    save('multiplier',im)

    groups={'tickets':['match','dice','fruit','sevens','gems','bingo','vault','crown'],
            'starter':['match','dice'],'middle':['fruit','sevens','gems'],'advanced':['bingo','vault'],'crown':['crown']}
    for name,cards in groups.items():
        im,d=canvas()
        if len(cards)==8:
            for i,card in enumerate(cards):icon(im,'scratch_card_'+card,11+i%4*61,27+i//4*115,58)
        else:
            size=142 if len(cards)==1 else 96 if len(cards)==2 else 73
            gap=5;start=(256-(size+gap)*len(cards)+gap)//2
            for i,card in enumerate(cards):icon(im,'scratch_card_'+card,start+i*(size+gap),(256-size)//2,size)
        save(name,im)

if __name__=='__main__':main()
