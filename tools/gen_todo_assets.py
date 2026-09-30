"""Inventory thumbnails and base UI text; gen_revision_assets supplies the final revision."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/teamecon'
NAMES=['slot_machine','multiplier_machine','hilo_table','roulette_table','penguin_machine','color_wheel_table','shop_machine','scratch_table']

def write(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def wheel(size=64):
    image=Image.new('RGBA',(size,size));d=ImageDraw.Draw(image)
    d.ellipse((0,0,size-1,size-1),fill='#263444',outline='#f5d68b',width=2)
    colors=['#303842']*40
    for i in [2,6,10,14,18,22,26,30,34]:colors[i]='#40c879'
    for i in [4,12,20,28,36]:colors[i]='#499ff4'
    colors[8]=colors[24]='#b374ea';colors[0]='#ffcf4d'
    for i,c in enumerate(colors):d.pieslice((4,4,size-5,size-5),i*9,(i+1)*9,fill=c)
    d.ellipse((size//2-5,size//2-5,size//2+5,size//2+5),fill='#f4d48a',outline='#ffffff')
    return image

def thumbnails():
    font=ImageFont.load_default(size=10)
    for name in NAMES+['guide_book']:
        im=Image.new('RGBA',(64,64));d=ImageDraw.Draw(im)
        dark='#20323e';edge='#adc6cb';gold='#efd084'
        if name=='color_wheel_table':
            d.rectangle((28,34,36,57),fill=edge);d.polygon([(16,55),(43,55),(51,60),(12,60)],fill=dark,outline=edge)
            im.alpha_composite(wheel(46),(9,6));d=ImageDraw.Draw(im)
            d.polygon([(26,3),(38,3),(32,13)],fill='#fff0b1',outline='#aa7539')
        elif name=='roulette_table':
            for x in [11,45]:d.rectangle((x,33,x+5,57),fill=dark,outline=edge)
            d.ellipse((4,21,59,48),fill='#10252d',outline=gold,width=2)
            top=Image.new('RGBA',(56,56));t=ImageDraw.Draw(top)
            for i in range(37):t.pieslice((0,0,55,55),i*360/37,(i+1)*360/37,fill='#42ba86' if i==0 else '#df6470' if i%2 else '#15242d')
            t.ellipse((18,18,38,38),fill='#286451',outline=gold,width=2)
            im.alpha_composite(top.resize((48,24),Image.Resampling.NEAREST),(8,20));d=ImageDraw.Draw(im)
            d.ellipse((28,25,36,32),fill=gold);d.ellipse((15,25,18,28),fill='white')
            d.rectangle((15,45,47,50),fill=edge);d.rectangle((18,46,27,49),fill='#df6470');d.rectangle((29,46,38,49),fill=dark)
        elif name=='guide_book':
            d.polygon([(16,8),(47,5),(52,10),(52,53),(20,59),(13,54),(13,13)],fill='#f0e9d7',outline='#15252b',width=2)
            d.polygon([(13,11),(45,7),(48,11),(48,55),(16,59),(13,55)],fill='#256353',outline='#8bd1aa',width=2)
            d.line((19,13,19,53),fill=gold,width=2);d.ellipse((25,25,41,41),fill=gold,outline='#fff1b6',width=2)
            d.text((29,28),'T',font=font,fill=dark);d.rectangle((26,46,40,48),fill=gold)
        else:
            wide=name=='penguin_machine';x0=6 if wide else 14;x1=52 if wide else 45
            accent={'slot_machine':'#f47b83','multiplier_machine':'#66e3a4','hilo_table':'#bca1f4','penguin_machine':'#78dcf0','shop_machine':'#75bfe8','scratch_table':'#dca6ef'}[name]
            d.polygon([(x0,15),(x0+8,8),(x1+8,8),(x1,15)],fill=accent,outline=edge)
            d.polygon([(x1,15),(x1+8,8),(x1+8,51),(x1,58)],fill='#344f5c',outline=edge)
            d.rectangle((x0,15,x1,58),fill=dark,outline=edge,width=2)
            d.rectangle((x0+3,17,x1-3,22),fill=accent)
            d.rectangle((x0+3,25,x1-3,42),fill='#0d2029',outline=accent)
            d.rectangle((x0+3,53,x1-3,56),fill='#0a171e')
            for x in range(x0+4,x1-2,6):d.rectangle((x,46,x+3,49),fill=gold)
            d.line((x0+4,51,x1-4,51),fill=accent)
            if name=='slot_machine':
                for x in [x0+5,x0+13,x0+21]:
                    d.rectangle((x,27,x+5,39),fill='#f6ead6');d.ellipse((x+1,31,x+4,35),fill='#e5566f')
                d.line((x1+7,28,x1+7,43),fill=edge,width=2);d.ellipse((x1+4,25,x1+10,31),fill=accent)
            elif name=='multiplier_machine':
                d.text((x0+6,26),'2.5x',font=font,fill=accent);d.line((x0+5,39,x0+11,37,x0+16,38,x0+24,34),fill=accent,width=2)
            elif name=='hilo_table':
                for x in [x0+5,x0+17]:
                    d.rectangle((x,29,x+8,38),fill='#f6e9d5');d.rectangle((x+2,31,x+3,32),fill='#543f85');d.rectangle((x+5,35,x+6,36),fill='#543f85')
            elif name=='penguin_machine':
                for i in range(4):d.rectangle((x0+5+i*9,39-i*3,x0+12+i*9,42-i*3),fill='#dcffff')
                d.rectangle((x0+16,28,x0+24,35),fill='#87d451',outline='#ccf7a6');d.rectangle((x0+18,30,x0+19,31),fill='#29432b');d.rectangle((x0+22,30,x0+23,31),fill='#29432b')
            elif name=='shop_machine':
                d.rectangle((x0+8,28,x0+24,38),fill='#91c3df');d.rectangle((x0+13,30,x0+17,34),fill=gold);d.line((x0+9,28,x0+12,25,x0+21,25,x0+24,28),fill=gold,width=2)
            else:d.rectangle((x0+10,27,x0+22,39),fill='#ebdab0',outline=accent);d.text((x0+13,28),'7',font=font,fill='#6b3e83')
        path=ASSETS/f'textures/item/{name}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
        write(ASSETS/f'models/item/{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'teamecon:item/{name}'}})
    path=ASSETS/'textures/gui/prize_wheel.png';path.parent.mkdir(parents=True,exist_ok=True);wheel().save(path)

TEXT={
'gui.teamecon.shop.tab.items':('物品','Items'),
'gui.teamecon.shop.tab.boxes':('盲盒','Boxes'),
'gui.teamecon.shop.tab.sell':('出售','Sell'),
'gui.teamecon.shop.filter_mod':('按 MOD 筛选','Filter by mod'),
'gui.teamecon.shop.all_mods':('全部 MOD','All mods'),
'gui.teamecon.shop.filter_search':('搜索筛选项…','Search filters…'),
'gui.teamecon.shop.search':('搜索 / @MOD / #标签','Search / @mod / #tag'),
'gui.teamecon.shop.per_page':('/页','/page'),
'gui.teamecon.shop.sell_title':('物品回收','Item recycling'),
'gui.teamecon.shop.sell_instructions':('将物品放入右侧回收格，查看报价后点击出售。Shift 点击可快速放入。','Place items in the sale slot, review the quote, then sell. Shift-click moves a stack.'),
'gui.teamecon.shop.sale_slot':('待售物品','Sale slot'),
'gui.teamecon.shop.sale_empty':('放入物品查看报价','Place a priced item here'),
'gui.teamecon.shop.sale_quote':('预计收入：%s 点','Estimated return: %s points'),
'gui.teamecon.shop.sell_stack':('出售格内物品','Sell this stack'),
'gui.teamecon.shop.sell_hint':('只出售回收格中的物品。未出售的物品在关闭时退回背包。连续出售同类材料会降低回收价，小数收入会保留；最终金额按成交时市场计算。','Only the sale-slot stack is sold. Unsold items return on closing. Related materials share demand decay; fractional income carries forward. Final price uses demand at the time of sale.'),
'gui.teamecon.shop.footer':('点击图标查看详情 · 滚轮翻页 · 出售页回收物品','Select an icon · Scroll for pages · Sell tab to recycle'),
'gui.teamecon.shop.category.all':('全部物品类型','All item types'),
'gui.teamecon.shop.category.blocks':('方块 / 建材','Blocks'),
'gui.teamecon.shop.category.tools':('工具','Tools'),
'gui.teamecon.shop.category.weapons':('武器','Weapons'),
'gui.teamecon.shop.category.armor':('盔甲','Armour'),
'gui.teamecon.shop.category.food':('食物','Food'),
'gui.teamecon.shop.category.magic':('附魔 / 药水','Magic / Potions'),
'gui.teamecon.shop.category.materials':('材料 / 其他','Materials / Other'),
'gui.teamecon.bet_factor':('下注倍数','Stake factor'),
'gui.teamecon.bet_multiply':('倍率','Factor'),
'gui.teamecon.jump_next':('跳到下一层','Jump one layer'),
'gui.teamecon.roulette.green':('绿零','Green 0'),
'gui.teamecon.crash_value':('可提取 %s 点','Cash out: %s'),
'gui.teamecon.crash_zero':('已崩盘，本局归零','Crashed · round lost'),
'gui.teamecon.crash_ready':('下注后上涨，随时提取','Start to grow · cash out anytime'),
'gui.teamecon.color_result':('指针结果：×%s','Pointer result: ×%s'),
'gui.teamecon.odds.crash':('持续上涨，随机崩盘归零','Grows until a random crash'),
'gui.teamecon.odds.crash_offline':('关界面 / 离线仍继续','Continues when closed/offline'),
'gui.teamecon.odds.next_layer':('第 %s 层：%s%% → ×%s','Layer %s: %s%% → ×%s'),
'gui.teamecon.odds.penguin':('每次跳一层，成功后继续或提取','One layer per jump; continue or cash out'),
'machine.teamecon.hint':('直接点击选项；倍率按钮设置下注倍数，应用后再开始','Choose directly; Factor edits the stake multiplier, then start'),
'machine.teamecon.bet_factor':('倍率','Factor'),
'machine.teamecon.color_rule':('指针停在色块上决定奖励','Pointer colour determines reward'),
'machine.teamecon.pointer':('上方指针','TOP POINTER'),
'machine.teamecon.next_layer':('下一跳：第 %s 层','NEXT: LAYER %s'),
'machine.teamecon.crash_choice':('持续上涨 · 随时提取','GROWS · CASH OUT ANYTIME'),
'machine.teamecon.crash_live':('已下注 %s 点：倍率上涨中，可随时提取','Staked %s: multiplier is rising; cash out anytime'),
'machine.teamecon.live_value':('可提取 %s 点','CASH OUT %s'),
'machine.teamecon.cash_out':('提取并结束','CASH OUT'),
'machine.teamecon.continue':('跳下一层','NEXT LAYER'),
'machine.teamecon.control.bet_multiplier':('右键：输入整数倍数，放大当前下注','Right-click: enter a factor for the current stake'),
'machine.teamecon.control.red':('右键：直接选择红色','Right-click: select red'),
'machine.teamecon.control.black':('右键：直接选择黑色','Right-click: select black'),
'machine.teamecon.control.green':('右键：直接选择绿色 0','Right-click: select green zero'),
'machine.teamecon.control.high':('右键：直接选择大（51–100）','Right-click: select high (51–100)'),
'machine.teamecon.control.low':('右键：直接选择小（1–50）','Right-click: select low (1–50)'),
'machine.teamecon.bet_input.title':('设置下注倍数','Set stake factor'),
'machine.teamecon.bet_input.base':('当前下注：%s 点','Current stake: %s points'),
'machine.teamecon.bet_input.factor':('输入整数倍数','Enter a whole-number factor'),
'machine.teamecon.bet_input.max':('最大倍数','Max factor'),
'machine.teamecon.bet_input.preview':('新下注：%s 点','New stake: %s points'),
'machine.teamecon.bet_input.invalid':('倍数无效或超出下注上限','Invalid factor or over limit'),
'machine.teamecon.bet_input.limit':('本机上限：%s 点','Machine limit: %s points'),
'machine.teamecon.bet_input.apply':('应用倍数','Apply factor'),
'machine.teamecon.bet_input.changed':('未应用：机器状态已变化，请重新设置','Not applied: machine state changed; reopen settings'),
'item.teamecon.guide_book':('团队经济指南','Team Economy Handbook'),
'book.teamecon.title':('团队经济指南','Team Economy Handbook'),
'book.teamecon.landing':('从物品回收到六种机器，了解下注、概率、返奖与团队钱包。书中列出默认参数；当前服务器的实际参数以商店、终端和机器提示为准。','Recycling, shared wallets and all six machines: stakes, odds and payouts. This book lists defaults. The shop, terminal and machine HUD show your server\'s actual settings.'),
'book.teamecon.missing':('阅读本指南需要安装 Patchouli（帕秋莉手册）1.21.1 NeoForge 版','Install Patchouli for Minecraft 1.21.1 NeoForge to read this handbook'),
'book.teamecon.failed':('指南暂时无法打开，请检查日志','The handbook could not open; check the log'),
}

def languages():
    for i,language in enumerate(['zh_cn','en_us']):
        path=ASSETS/f'lang/{language}.json';data=json.loads(path.read_text(encoding='utf-8'))
        data.update({k:v[i] for k,v in TEXT.items()})
        for key in ['block.teamecon.roulette_table','container.teamecon.roulette']:
            data[key]=('黑红轮盘','Red & Black Roulette')[i]
        for key in ['block.teamecon.color_wheel_table','container.teamecon.color_wheel']:
            data[key]=('幸运转盘','Lucky Wheel')[i]
        data['gui.teamecon.tab.roulette']=('黑红','R/B')[i]
        data['gui.teamecon.tab.color_wheel']=('转盘','Wheel')[i]
        for k in list(data):
            if k in ['gui.teamecon.shop.repair','gui.teamecon.shop.repair_hint','gui.teamecon.shop.repair_price','command.teamecon.shop_repaired','command.teamecon.shop_not_damaged']:del data[k]
        write(path,dict(sorted(data.items())))

def main():
    thumbnails();languages()
    from gen_completion_assets import main as completion
    completion()
    from gen_guide import main as guide
    guide()
    print('Generated machine thumbnails, upright wheel, guide and 0.4.1 translations.')

if __name__=='__main__':main()
