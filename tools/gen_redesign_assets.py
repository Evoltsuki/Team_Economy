"""Physical ticket art, full cabinet item previews and localized world controls for 0.3."""
from pathlib import Path
import json
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
ASSETS=RES/'assets/teamecon'
CARDS=[('match',10,(214,161,107),'LUCKY'),('dice',25,(134,188,177),'DICE 7'),
       ('fruit',50,(241,147,166),'FRUIT'),('sevens',100,(223,190,100),'SEVEN'),
       ('gems',250,(109,201,221),'GEMS'),('bingo',500,(174,148,227),'BINGO'),
       ('vault',1000,(107,164,217),'VAULT'),('crown',2500,(255,211,101),'CROWN')]
MACHINES=['slot_machine','multiplier_machine','hilo_table','roulette_table','penguin_machine','color_wheel_table']

def write(path,data):
    target=RES/path;target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def tickets():
    font=ImageFont.load_default(size=8)
    for index,(name,cost,color,title) in enumerate(CARDS):
        image=Image.new('RGBA',(64,64),(0,0,0,0));d=ImageDraw.Draw(image)
        d.rectangle((9,4,57,62),fill=(7,13,23,110))
        d.rectangle((7,2,55,60),fill=(239,227,198),outline=(92,78,59),width=1)
        d.rectangle((9,4,53,58),outline=color,width=2)
        d.rectangle((10,5,52,17),fill=color)
        box=d.textbbox((0,0),title,font=font);d.text((31-(box[2]-box[0])/2,6),title,font=font,fill=(29,37,49))
        d.rectangle((12,21,50,47),fill=(42,54,69),outline=color)
        for y in range(23,46,3): d.line((14,y,48,y),fill=(67,80,94))
        if name=='match':
            for x,number in [(16,'12'),(34,'12')]:d.text((x,27),number,font=font,fill=(246,224,162))
        elif name=='dice':
            for x,dots in [(16,3),(34,4)]:
                d.rectangle((x,27,x+12,39),fill=(243,236,218))
                for dx,dy in ([(3,3),(6,6),(9,9)] if dots==3 else [(3,3),(9,3),(3,9),(9,9)]):d.rectangle((x+dx-1,27+dy-1,x+dx,27+dy),fill=(31,44,55))
        elif name=='fruit':
            for x in [16,27,38]:d.ellipse((x,30,x+8,38),fill=(234,91,118));d.line((x+4,27,x+4,30),fill=(126,215,146),width=2)
        elif name=='sevens':
            for x in [17,28,39]:d.text((x,28),'7',font=font,fill=(255,220,113))
        elif name=='gems':
            d.polygon([(31,24),(41,30),(37,40),(31,44),(22,35),(21,30)],fill=(76,217,235),outline=(218,255,244));d.line((22,30,41,30),fill=(209,255,253));d.line((31,24,27,30,31,44),fill=(209,255,253))
        elif name=='bingo':
            for row in range(3):
                for col in range(3):d.rectangle((20+col*8,24+row*7,25+col*8,29+row*7),fill=(246,207,110) if row==1 or col==1 else (167,153,196))
        elif name=='vault':
            d.rectangle((19,24,44,43),fill=(83,117,155),outline=(189,216,231),width=2);d.ellipse((29,27,39,38),outline=(235,215,167),width=2);d.line((34,28,34,38),fill=(235,215,167));d.line((29,33,40,33),fill=(235,215,167))
        else:
            d.polygon([(18,28),(25,33),(31,23),(37,33),(45,28),(42,43),(21,43)],fill=(243,191,63),outline=(255,234,157));d.rectangle((23,39,40,42),fill=(255,225,119));d.rectangle((30,35,33,39),fill=(211,82,116))
        # Perforation and a foil corner give each item a recognizable ticket silhouette.
        for x in range(10,54,4):d.line((x,50,x+1,50),fill=(122,111,99))
        price=str(cost);box=d.textbbox((0,0),price,font=font);d.text((31-(box[2]-box[0])/2,51),price,font=font,fill=(51,61,70))
        d.polygon([(45,43),(50,38),(50,47),(42,47)],fill=color)
        path=ASSETS/f'textures/item/scratch_card_{name}.png';path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
        write(f'assets/teamecon/models/item/scratch_card_{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':f'teamecon:item/scratch_card_{name}'}})

def items():
    for name in MACHINES:
        table=name in ['roulette_table','color_wheel_table'];w=24 if table or name=='penguin_machine' else 16;depth=24 if table else 16
        elements=[]
        def cube(a,b,texture):
            elements.append({'from':a,'to':b,'faces':{f:{'texture':'#'+texture} for f in ['north','south','east','west','up','down']}})
        if table:
            for x in [1,w-3]:
                for z in [1,depth-3]:cube([x,0,z],[x+2,11.6,z+2],'side')
            cube([.3,11.2,.3],[w-.3,12.8,depth-.3],'side');cube([0,12.4,0],[w,13,depth],'top')
            cube([1,13,1],[w-1,13.4,depth-1],'front')
            for x in [1,w-2]:cube([x,13.4,depth-2],[x+.7,18.8,depth-1.3],'metal')
            cube([1.6,17,depth-1.8],[w-1.6,19.4,depth-.8],'front')
            for x,z in [(7,4),(4,7),(7,17),(17,7)]:cube([x,13.4,z],[x+3,14.1,z+3],'top')
            cube([w/2-1,13.4,depth/2-1],[w/2+1,16,depth/2+1],'metal')
        else:
            cube([.6,.8,.8],[w-.6,22.2,depth-.8],'side')
            for y in [1,22.2]:cube([.2,y,.5],[w-.2,y+1.2,depth-.5],'metal')
            for x in [.6,w-1.3]:cube([x,2,0],[x+.7,22.2,1],'top')
            cube([1.3,12.3,-.4],[w-1.3,21.6,.9],'front')
            cube([1,21.5,-.6],[w-1,22.2,1],'top')
            for x in [1.2,w-3.2]:
                for z in [1.2,depth-3.2]:cube([x,0,z],[x+2,1,z+2],'side')
            if name=='slot_machine':
                for x in [2.2,6.2,10.2]:cube([x,15,-1],[x+3.4,19.8,0],'reel')
                cube([w-.4,14,3],[w+.2,20,3.6],'metal');cube([w-1,20,2.5],[w+1,21.5,4.5],'top')
        cube([1.1,5.4,-.7],[w-1.1,12.2,0],'side')
        for i in range(4):cube([1.3+i*(w-2.4)/4,5.8,-1.3],[1.3+i*(w-2.4)/4+(w-3)/4,7.3,-.6],'metal')
        cube([1.3,10.3,-1.3],[w-1.3,11.9,-.6],'top')
        write(f'assets/teamecon/models/item/{name}.json',{'parent':'minecraft:block/block',
            'textures':{'front':f'teamecon:block/{name}_front','side':f'teamecon:block/{name}_side',
                        'top':f'teamecon:block/{name}_top','metal':'teamecon:block/cabinet_metal','reel':'teamecon:block/slot_machine_front'},
            'elements':elements,'display':{'gui':{'rotation':[20,35,0],'translation':[-2 if w==24 else 0,-3,0],'scale':[.59,.59,.59]},
                'ground':{'translation':[0,3,0],'scale':[.25,.25,.25]},'fixed':{'rotation':[0,180,0],'translation':[0,-3,0],'scale':[.5,.5,.5]},
                'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2,0],'scale':[.3,.3,.3]},
                'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,-1,0],'scale':[.4,.4,.4]}}})
    material=Image.new('RGBA',(16,16),(174,195,204));d=ImageDraw.Draw(material)
    for y in range(0,16,3):d.line((0,y,15,y),fill=(197,215,222))
    material.save(ASSETS/'textures/block/cabinet_metal.png')
    write('assets/teamecon/models/block/machine_part.json',{'textures':{'particle':'teamecon:block/cabinet_metal'},'elements':[]})
    write('assets/teamecon/blockstates/machine_part.json',{'variants':{'':{'model':'teamecon:block/machine_part'}}})
    tag=RES/'data/minecraft/tags/block/mineable/pickaxe.json'
    document=json.loads(tag.read_text(encoding='utf-8'))
    if 'teamecon:machine_part' not in document['values']:document['values'].append('teamecon:machine_part')
    write(tag.relative_to(RES),document)

PAIRS={
'shop.teamecon.requires_advancement':('需先完成进度：%s','Complete advancement first: %s'),
'shop.teamecon.requires_stage':('需先解锁阶段：%s','Required stage: %s'),
'shop.teamecon.lock.sell_only':('探索／制作获取：只可回收，不可购买','Obtain through exploration or crafting; selling only'),
'shop.teamecon.lock.modded':('该模组物品尚未配置购买解锁规则','No purchase unlock rule is configured for this modded item'),
'shop.teamecon.lock.config':('购买规则配置有误，请联系服主','Purchase rules need repair; contact the server owner'),
'shop.teamecon.progression_locked':('商品尚未解锁：%s','Purchase locked: %s'),
'gui.teamecon.shop.items_hint':('高级商品需先完成对应进度；悬停查看解锁条件。','Advanced goods require progression. Hover for unlock conditions.'),
'gui.teamecon.shop.boxes_hint':('奖池内全部物品解锁后才可购买；可能抽空。','Unlock every possible reward before buying. A box may be empty.'),
'gui.teamecon.shop.books_hint':('附魔书需先完成附魔进度；使用铁砧应用到装备。','First enchant an item to unlock books. Apply books using an anvil.'),
'block.teamecon.penguin_machine':('史莱姆跳冰机','Slime Ice Hop Machine'),
'container.teamecon.penguin':('史莱姆跳冰','Slime Ice Hop'),
'gui.teamecon.tab.penguin':('跳冰','Ice Hop'),
'gui.teamecon.odds.penguin':('跳得越远，冰面碎裂风险越高','Longer hops increase the risk of breaking the ice'),
'message.teamecon.penguin_fell':('冰块碎裂，史莱姆落水！本局损失 %s 点','Ice shattered! The slime fell in. This run lost %s points'),
'message.teamecon.penguin_landed':('史莱姆安全着陆！倍率 ×%s，可继续跳或收取奖励','Slime landed safely! ×%s. Hop again or cash out'),
'block.teamecon.color_wheel_table':('滚珠彩轮桌','Marble Colour Wheel'),
'container.teamecon.color_wheel':('滚珠彩轮','Marble Colour Wheel'),
'gui.teamecon.tab.color_wheel':('彩轮','Colour Wheel'),
'gui.teamecon.tab.scratch':('购卡','Tickets'),
'item.teamecon.terminal.tooltip':('无线购卡、小游戏与物品回收；点数与队伍共享','Buy scratch tickets, play games and sell items; points are shared with your team'),
'scratch.teamecon.open_hint':('主手持卡，左键打开并刮取','Hold in your main hand and left-click to scratch'),
'scratch.teamecon.price':('票价 %s 点','Price: %s'),
'scratch.teamecon.blank':('空白样品；有效刮刮卡请在无线终端购买','Blank sample. Buy a valid ticket in the wireless terminal'),
'scratch.teamecon.invalid':('这张卡已使用或无效','This ticket was used or is invalid'),
'scratch.teamecon.purchased':('刮刮卡已放入背包；手持左键打开','Ticket added to inventory; hold it and left-click'),
'scratch.teamecon.buy':('购买一张 · %s 点','Buy ticket · %s'),
'scratch.teamecon.target':('幸运数字：%s','Winning number: %s'),
'scratch.teamecon.code':('保险箱密码：%s','Vault code: %s'),
'scratch.teamecon.line_prize':('连线奖金：%s 点','Line prize: %s'),
'scratch.teamecon.collect':('收集图案，对照右侧奖表','Collect symbols · see paytable'),
'scratch.teamecon.sum_seven':('同一行两颗骰子之和为 7','A pair must total 7'),
'scratch.teamecon.three_match':('同一行三个相同水果中奖','Match three fruit in a row'),
'scratch.teamecon.find_seven':('找到幸运数字 7','Find lucky number 7'),
'scratch.teamecon.paytable':('倍数 / 概率','Payout / Odds'),
'scratch.teamecon.max_prize':('最高 %s 点','Max: %s'),
'scratch.teamecon.credited':('已入账 +%s 点 · 余额 %s','Credited +%s · Balance %s'),
'scratch.teamecon.no_prize':('本张未中奖 · 余额 %s','No prize · Balance %s'),
'scratch.teamecon.settling':('票面已刮开，正在结算…','Ticket uncovered. Settling…'),
'scratch.teamecon.automatic':('刮开全部区域后自动入账','Fully scratch to auto-credit'),
'scratch.teamecon.finished':('本张已结算，可直接关闭','Ticket settled. You may close'),
'scratch.teamecon.drag':('按住左键拖动刮取 · 已刮开 %s%%','Hold left-click and drag · %s%% uncovered'),
'scratch.teamecon.rule.match':('数字与幸运数字相同，获得该格标注倍数 × 票价。','Match the winning number to win the cell multiplier × ticket price.'),
'scratch.teamecon.rule.dice':('同一行两颗骰子之和为 7，获得该行倍数 × 票价。','A dice pair totaling 7 wins its row multiplier × ticket price.'),
'scratch.teamecon.rule.fruit':('同一行出现三个相同水果，获得该行倍数 × 票价。','Three matching fruit in a row win its multiplier × ticket price.'),
'scratch.teamecon.rule.sevens':('刮出数字 7，获得该格标注倍数 × 票价。','Find a 7 to win the cell multiplier × ticket price.'),
'scratch.teamecon.rule.gems':('收集至少 3 颗钻石；数量越多倍数越高，火石不计数。','Collect 3 or more diamonds. More diamonds pay more; flint does not count.'),
'scratch.teamecon.rule.bingo':('金币横向、纵向或斜向连成一线，即获上方连线奖金。','Make a row, column or diagonal of gold nuggets to win the line prize.'),
'scratch.teamecon.rule.vault':('任意一行的三位数字与保险箱密码相同，获得该行奖金。','Match a row of three digits to the vault code to win its row prize.'),
'scratch.teamecon.rule.crown':('收集至少 3 顶皇冠；7 顶皇冠获得 500 倍大奖。','Collect 3 or more crowns. Seven crowns win the 500× jackpot.'),
'machine.teamecon.footprint':('占地 %s × %s 格，高 %s 格；前方留出操作空间','Footprint %s × %s, height %s; leave room in front'),
'machine.teamecon.item_hint':('右键机身按钮下注；动画和结算在世界中完成','Right-click physical buttons; watch and settle in the world'),
'machine.teamecon.hint':('右键机身按钮调注、选项和开始；蹲下调注为十倍步长','Right-click the bet, option or start buttons; sneak for larger bet steps'),
'machine.teamecon.world_hint':('右键按钮开始游戏','Right-click buttons to play'),
'machine.teamecon.running':('动画进行中，结束后自动结算','Animation running; settlement follows'),
'machine.teamecon.running_short':('运行中…','Running…'),
'machine.teamecon.occupied':('%s 正在这台机器闯关','%s has an active run on this machine'),
'machine.teamecon.bet':('下注金额：%s 点','Stake: %s points'),
'machine.teamecon.stake':('下注 %s 点','STAKE %s'),
'machine.teamecon.wallet':('余额 %s','BALANCE %s'),
'machine.teamecon.cannot_start':('无法开始：请检查余额、游戏选项或当前闯关','Cannot start: check balance, selection or your active run'),
'machine.teamecon.start':('下注并开始','BET & START'),
'machine.teamecon.continue':('继续闯关','CONTINUE'),
'machine.teamecon.cash_out':('收取奖励','CASH OUT'),
'machine.teamecon.round':('闯关成功 ×%s','Round won ×%s'),
'machine.teamecon.paid':('已入账 %s 点','Credited %s points'),
'machine.teamecon.auto_paid':('闯关已结束，%s 点已返还原下注钱包','Run closed; %s points credited to the original wallet'),
'machine.teamecon.lost':('本局损失 %s 点','This run lost %s points'),
'machine.teamecon.number':('单号 %s','Number %s'),
'machine.teamecon.color_rule':('滚珠落色决定返奖倍数','Ball colour determines payout'),
'machine.teamecon.spin_rule':('三个转轴，组合返奖','Three reels · match symbols'),
'machine.teamecon.return_to_machine':('请回到当前实体机继续或收取奖励','Return to your active machine to continue or cash out'),
'machine.teamecon.legacy':('旧版机型：拆下重新放置即可升级为大型实体机','Legacy machine: break and place again to upgrade'),
'machine.teamecon.hud_bet':('下注 %s 点 · %s','Stake %s · %s'),
'machine.teamecon.hud_run':('当前 ×%s · 可收取 %s 点','Current ×%s · Cash out %s'),
'machine.teamecon.hud_odds':('成功率 %s%% · 返奖 ×%s（包含本金）','Chance %s%% · Return ×%s (stake included)'),
'machine.teamecon.color_odds_one':('灰 ×0：57.5% · 绿 ×1：22.5% · 蓝 ×2：12.5%','Grey ×0: 57.5% · Green ×1: 22.5% · Blue ×2: 12.5%'),
'machine.teamecon.color_odds_two':('紫 ×4：5% · 金 ×10：2.5%（返奖包含本金）','Purple ×4: 5% · Gold ×10: 2.5% (stake included)'),
'machine.teamecon.control.none':('瞄准机身按钮，右键操作','Aim at a physical button and right-click'),
'machine.teamecon.control.minus_100':('右键：−100 点 · 蹲下：−1000 点','Right-click: −100 · Sneak: −1000'),
'machine.teamecon.control.minus_10':('右键：−10 点 · 蹲下：−100 点','Right-click: −10 · Sneak: −100'),
'machine.teamecon.control.plus_10':('右键：+10 点 · 蹲下：+100 点','Right-click: +10 · Sneak: +100'),
'machine.teamecon.control.plus_100':('右键：+100 点 · 蹲下：+1000 点','Right-click: +100 · Sneak: +1000'),
'machine.teamecon.control.previous':('右键：切换上一个选项','Right-click: previous option'),
'machine.teamecon.control.next':('右键：切换下一个选项','Right-click: next option'),
'machine.teamecon.control.start':('右键：下注 / 继续，观看动画后结算','Right-click: bet / continue, then watch settlement'),
'machine.teamecon.control.cash_out':('右键：结束闯关并收取奖励','Right-click: end the run and cash out'),
'machine.teamecon.control.wheel':('右键轮盘上的数字区域：选择该单号','Right-click a numbered pocket to select it'),
}

def languages():
    names=[('幸运数字刮刮卡','Lucky Numbers','数字'),('骰子凑七刮刮卡','Dice Seven','骰子'),
           ('水果连线刮刮卡','Fruit Lines','水果'),('幸运七刮刮卡','Lucky Sevens','幸运七'),
           ('宝石寻踪刮刮卡','Gem Hunt','宝石'),('金币宾果刮刮卡','Golden Bingo','宾果'),
           ('密码宝库刮刮卡','Secret Vault','宝库'),('皇冠大奖刮刮卡','Crown Jackpot','皇冠')]
    for (name,cost,_,_),(zh,en,short) in zip(CARDS,names):
        PAIRS['item.teamecon.scratch_card_'+name]=(zh,en+' Ticket')
        PAIRS['scratch.teamecon.short.'+name]=(short+' '+str(cost),name.title()+' '+str(cost))
    for index,language in enumerate(['zh_cn','en_us']):
        path=ASSETS/f'lang/{language}.json';data=json.loads(path.read_text(encoding='utf-8'))
        data.update({k:v[index] for k,v in PAIRS.items()});write(path.relative_to(RES),dict(sorted(data.items())))

def main():
    tickets();items();languages()
    from localize_progression import main as progression_languages
    progression_languages()
    for relative in ['data/teamecon/recipe/scratch_table.json','data/teamecon/advancement/recipes/scratch_table.json',
                     'data/teamecon/recipe/terminal.json','data/teamecon/advancement/recipes/terminal.json']:
        target=(RES/relative).resolve()
        if target.is_relative_to(RES.resolve()) and target.is_file():target.unlink()
    print('Generated eight physical ticket items, cabinet previews and world-control translations.')
    from gen_todo_assets import main as todo_assets
    todo_assets()

if __name__=='__main__':main()
