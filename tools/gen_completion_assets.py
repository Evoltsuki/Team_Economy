"""Sharp wheels, two-block shop/box cabinets and final UI strings. Reproducible asset source."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import json,math
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources';ASSETS=RES/'assets/teamecon'
def write(path,data):
    target=RES/path;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def wheels():
    order=[0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26]
    red={1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36}
    for kind in ['roulette_wheel','prize_wheel']:
        im=Image.new('RGBA',(512,512));d=ImageDraw.Draw(im);n=37 if kind=='roulette_wheel' else 40
        d.ellipse((2,2,509,509),fill='#14242f',outline='#ffe3a0',width=8)
        for i in range(n):
            if kind=='roulette_wheel':c='#46c18b' if order[i]==0 else '#cf4758' if order[i] in red else '#20313c'
            else:c='#ffc943' if i==0 else '#b679ee' if i in [8,24] else '#4b9ef0' if i in [4,12,20,28,36] else '#40c879' if i in [2,6,10,14,18,22,26,30,34] else '#303e49'
            a=i*360/n;b=(i+1)*360/n;d.pieslice((15,15,496,496),a,b,fill=c,outline='#cbd3c2',width=2)
            rad=math.radians((a+b)/2);x=256+204*math.cos(rad);y=256+204*math.sin(rad)
            if kind=='roulette_wheel':d.text((x,y),str(order[i]),font=ImageFont.truetype('C:/Windows/Fonts/arialbd.ttf',18),fill='white',anchor='mm',stroke_width=1,stroke_fill='#13222b')
        d.ellipse((133,133,379,379),fill='#203e41',outline='#e9ce83',width=8)
        for i in range(12):
            a=i*math.pi/6;d.line((256,256,256+106*math.cos(a),256+106*math.sin(a)),fill='#31595b',width=3)
        d.ellipse((218,218,294,294),fill='#f1cf77',outline='#fff4cd',width=5)
        d.ellipse((238,238,274,274),fill='#425c63',outline='#c89b49',width=3)
        im.save(ASSETS/f'textures/gui/{kind}.png')
def cabinets():
    colors={'steel':'#99afb9','dark':'#172833','panel':'#29434e','gold':'#e9c978','screen':'#10212c','blue':'#72c7e3','violet':'#b99be8','light':'#dfece5'}
    for name,color in colors.items():
        im=Image.new('RGBA',(16,16),color);im.save(ASSETS/f'textures/block/store_{name}.png')
    for name in ['shop_machine','blind_box_machine']:
        accent='blue' if name=='shop_machine' else 'violet'
        for half in ['bottom','top']:
            elements=[]
            def box(a,b,color):elements.append({'from':a,'to':b,'faces':{face:{'texture':'#'+color,'uv':[0,0,16,16]} for face in ['up','down','north','south','east','west']}})
            box([1,0,2],[15,16,15],'dark');box([1,0,1],[2,16,15],'steel');box([14,0,1],[15,16,15],'steel')
            if half=='bottom':
                box([1,0,1],[15,2,15],'steel');box([2,2,1],[14,15,3],'panel');box([4,3,.6],[12,7,1.1],'screen')
                box([3.5,2.5,0],[12.5,3.5,3],'steel');box([5,11,.5],[11,14,1],'screen');box([5.5,11.7,.3],[10.5,13.3,.5],accent)
                for x in [3,12]:box([x,9,.5],[x+1,10.5,1],accent)
                for y in [4,6,8]:box([15,y,7],[15.1,y+.5,12],'screen')
            else:
                box([1,14,1],[15,16,15],'steel');box([2,12.3,.7],[14,14,2],accent)
                box([2.5,1,1],[13.5,11.5,3],'gold');box([3,1.5,.7],[13,11,1],'screen')
                if name=='shop_machine':
                    for x,y in [(4,3),(7.5,3),(11,3),(4,7),(7.5,7),(11,7)]:box([x,y,.4],[x+1.2,y+2,.7],accent)
                else:
                    for x,y,c in [(4,3,'gold'),(7,5,'blue'),(10,2,'violet'),(4,7,'light'),(10,7,'gold')]:box([x,y,.4],[x+2,y+2,.7],c)
                box([2,0,.8],[14,.8,2],'steel')
            write(f'assets/teamecon/models/block/{name}_{half}.json',{'textures':{c:f'teamecon:block/store_{c}' for c in colors},'elements':elements})
        write(f'assets/teamecon/blockstates/{name}.json',{'variants':{f'facing={face},half={half}':{'model':f'teamecon:block/{name}_{half}','y':angle} for face,angle in [('north',0),('east',90),('south',180),('west',270)] for half in ['bottom','top']}})
        write(f'data/teamecon/loot_table/blocks/{name}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':'teamecon:'+name}],'conditions':[{'condition':'minecraft:survives_explosion'},{'condition':'minecraft:block_state_property','block':'teamecon:'+name,'properties':{'half':'bottom'}}]}]})
    write('assets/teamecon/models/item/blind_box_machine.json',{'parent':'minecraft:item/generated','textures':{'layer0':'teamecon:item/blind_box_machine'}})
    im=Image.open(ASSETS/'textures/item/shop_machine.png').convert('RGBA');d=ImageDraw.Draw(im);d.rectangle((17,17,42,22),fill='#b99be8');d.rectangle((22,28,36,39),fill='#e9c978',outline='#fdf1d3',width=2);d.line((29,28,29,39),fill='#b99be8',width=2);im.save(ASSETS/'textures/item/blind_box_machine.png')
    write('data/teamecon/recipe/blind_box_machine.json',{'type':'minecraft:crafting_shaped','category':'misc','pattern':['IGI','ICI','IRI'],'key':{'I':{'item':'minecraft:iron_ingot'},'G':{'item':'minecraft:glass'},'C':{'item':'minecraft:chest'},'R':{'item':'minecraft:redstone'}},'result':{'id':'teamecon:blind_box_machine','count':1}})
    write('data/teamecon/advancement/recipes/blind_box_machine.json',{'parent':'minecraft:recipes/root','criteria':{'has_iron':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:iron_ingot'}]}}},'requirements':[['has_iron']],'rewards':{'recipes':['teamecon:blind_box_machine']}})
    path=RES/'data/minecraft/tags/block/mineable/pickaxe.json';data=json.loads(path.read_text(encoding='utf-8'));data['values']=sorted(set(data['values']+['teamecon:blind_box_machine']));write(path.relative_to(RES),data)
TEXT={
'machine.teamecon.pickup':('取货口','PICKUP'),
'gui.teamecon.terminal.games':('选择游戏','Choose a game'),
'gui.teamecon.shop.factor_input':('票面倍数（1–10000）','Stake factor (1–10000)'),
'gui.teamecon.roulette.green':('绿','Green'),
'block.teamecon.blind_box_machine':('盲盒机','Mystery Box Machine'),
'gui.teamecon.shop.terminal_home':('终端首页','Terminal home'),
'gui.teamecon.terminal.home':('无线终端','Wireless Terminal'),
'gui.teamecon.terminal.subtitle':('团队资产与娱乐中心','Team wallet & arcade'),
'gui.teamecon.terminal.shop':('物品商店','Shop'),
'gui.teamecon.terminal.sell':('物品回收','Recycle'),
'gui.teamecon.terminal.boxes':('盲盒中心','Mystery boxes'),
'gui.teamecon.terminal.levels':('设备升级','Upgrades'),
'gui.teamecon.terminal.back':('首页','Home'),
'gui.teamecon.terminal.live':('进行中：%s · 可提取 %s','Live: %s · Cash out %s'),
'gui.teamecon.terminal.hint':('选择服务或游戏 · 所有交易使用当前队伍钱包','Choose a service or game · Shared team wallet'),
'hud.teamecon.balance':('队伍余额  %s','Team balance  %s'),
'hud.teamecon.earnings':('成员净赚','Member net earnings'),
'scratch.teamecon.stake_invalid':('票面金额超出当前等级上限','Ticket stake exceeds this level limit'),
'scratch.teamecon.factor':('票面 ×%s','Stake ×%s'),
'gui.teamecon.shop.books_hint':('购买实体附魔书；高阶附魔仍需完成对应冒险进度。','Receive an enchanted book; advanced enchantments require progression.'),
'gui.teamecon.shop.card_limit':('本级票面上限：%s','Ticket stake limit: %s'),
}
def languages():
    for i,lang in enumerate(['zh_cn','en_us']):
        path=ASSETS/f'lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8'));data.update({k:v[i] for k,v in TEXT.items()});write(path.relative_to(RES),dict(sorted(data.items())))
def main():
    wheels();cabinets();languages()
    from gen_vending_assets import main as vending
    vending()
    from gen_revision_assets import main as revision
    revision()
    from gen_release_assets import main as release
    release()
if __name__=='__main__':main()
