"""Patchouli book definition in data/, localized content in assets/."""
from pathlib import Path
import json

ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
BOOK='teamecon:casino_guide'
CAPTIONS={
    'welcome':('首次进入自动获赠；书与铁锭也能合成。','Granted on first login; also crafted with a book and iron.'),
    'economy':('选择商品购买；把待售物品放入回收格。','Buy selected goods; use the sale slot to recycle.'),
    'controls':('用“倍率”放大下注，再单独点击开始。','Use Factor to change the stake, then press Start.'),
    'levels':('逐级开放六种机器，机器需另外购买。','Unlock the machines one level at a time.'),
    'hilo':('小：1–50；大：51–100。','Low: 1–50. High: 51–100.'),
    'penguin':('每次前进一层，踏空则结束本局。','Climb one layer per jump; a fall ends the run.'),
    'color_wheel':('顶部固定指针所指色块决定奖励。','The fixed top pointer selects the prize colour.'),
    'roulette':('直接选择红、黑或绿色 0。','Select red, black or green zero directly.'),
    'slots':('示例：三樱桃 ×6，三钻石 ×275。','Examples: three cherries ×6; three diamonds ×275.'),
    'multiplier':('曲线为玩法示意，崩盘时间每局不同。','Illustration only: each round has its own crash time.'),
    'tickets':('购卡后拿在主手，左键打开并拖动刮取。','Hold a bought ticket, left-click, then drag to scratch.'),
    'starter':('幸运数字与骰子凑七。','Lucky Numbers and Lucky Dice.'),
    'middle':('水果连线、幸运七与宝石寻踪。','Fruit Match, Lucky Sevens and Gem Hunt.'),
    'advanced':('金币宾果与密码宝库。','Coin Bingo and Code Vault.'),
    'crown':('皇冠越多，对应的奖级越高。','More crowns reveal a higher prize tier.')
}

def write(path,obj):
    path=ROOT/path;path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def entry(id,icon,zh,en,pages,category='machines',order=0):
    for i,lang in enumerate(['zh_cn','en_us']):
        write(f'assets/teamecon/patchouli_books/casino_guide/{lang}/entries/{id}.json',{
            'name':[zh,en][i],'icon':icon,'category':f'teamecon:{category}','sortnum':order,
            'pages':[{'type':'patchouli:image','title':[zh,en][i],
                      'images':[f'teamecon:textures/gui/guide/{id}.png'],'border':False,'text':CAPTIONS[id][i]}]
                    +[{'type':'patchouli:text','title':'','text':pair[i]} for pair in pages]})

def main():
    from gen_guide_images import main as illustrations
    illustrations()
    write('data/teamecon/patchouli_books/casino_guide/book.json',{
        'name':'book.teamecon.title','landing_text':'book.teamecon.landing','version':'0.4.0',
        'use_resource_pack':True,'i18n':False,'custom_book_item':'teamecon:guide_book','show_progress':False,
        'pause_game':False,'book_texture':'patchouli:textures/gui/book_green.png','model':'patchouli:book_green',
        'text_color':'263a30','header_color':'21493a','nameplate_color':'e7c57a','link_color':'306a51'})
    for i,lang in enumerate(['zh_cn','en_us']):
        for id,zh,en,desc,icon,order in [
            ('basics','从这里开始','Getting started',('钱包、购买、出售和设备等级。','Wallets, shopping, sales and levels.'),'teamecon:guide_book',0),
            ('machines','六种机器','The six machines',('操作、概率、返奖和结算规则。','Controls, odds, rewards and settlement.'),'teamecon:slot_machine',1),
            ('tickets','实体刮刮卡','Scratch tickets',('八种票面与完整奖金分布。','Eight ticket designs and their prize distributions.'),'teamecon:scratch_card_crown',2)]:
            write(f'assets/teamecon/patchouli_books/casino_guide/{lang}/categories/{id}.json',{
                'name':[zh,en][i],'description':desc[i],'icon':icon,'sortnum':order})
    entry('welcome','teamecon:guide_book','开始使用','Start here',[
        ('$(bold)制作指南$()：一本书 + 一块铁锭，无序合成；首次进入自动发一本。右键阅读，需安装帕秋莉手册。$(br2)本书的倍率均$(bold)包含本金$()；下注 100 点、返奖 ×2，入账 200 点，净赚 100。失败入账 0。',
         '$(bold)Craft this guide$() with a book and iron ingot; first login grants one. Right-click to read; Patchouli is required.$(br2)All payouts $(bold)include the stake$(). A 100-point bet at ×2 credits 200: a net gain of 100. A loss credits zero.'),
        ('本书介绍操作、图解、中奖组合与事件概率。服主可调整等级、下注上限与赔率；商店详情、终端和机身提示显示实际值。$(br2)图解是玩法示意。概率表示某种情况出现的可能性，不保证接下来某一局会出现该结果。',
         'This guide covers controls, diagrams, prize combinations and event probabilities. Server owners can change the settings; check the live shop, terminal and machine HUD.$(br2)Diagrams explain the rules. A probability does not guarantee the next result.')
    ],'basics',0)
    entry('economy','teamecon:shop_machine','商店与回收','Shop and recycling',[
        ('商店有物品、出售、机器、刮卡、盲盒、升级六页，43 种附魔书合并在物品中。图标网格随窗口缩放，滚轮或箭头翻页。$(br2)点击 MOD 与类型按钮选择分类；搜索支持名称、完整 ID、$(bold)@modid$() 和 $(bold)#标签$()。点击商品后在右侧购买。',
         'Seven tabs: Items, Sell, Machines, Tickets, Boxes, Enchants and Levels. The icon grid resizes with the window; scroll or use page arrows.$(br2)Filter by mod or type. Search names, IDs, $(bold)@modid$() or $(bold)#tags$(). Select an icon, then buy.'),
        ('$(bold)出售$()：切到出售页，把物品放进回收格，查看报价，再点击出售。Shift 点击快速移入。关闭时未卖物品退回背包。$(br2)市场按同材料组递减，方块与锭共享需求；小数收入保留。最终价格按成交时计算。',
         '$(bold)Selling$(): put a stack in the Sell tab slot, review the quote, then sell. Shift-click moves a stack. Unsold items return when closing.$(br2)Related materials share demand decay. Fractional income carries forward; final prices use demand at sale time.'),
        ('高阶物品不能靠低价越过探索：部分只回收不出售，其余检查原版进度；未知模组商品默认锁定。$(br2)附魔购买给出附魔书，需用铁砧应用。经验修补默认至少 65,536 点，相当于 256 颗钻石的基础回收价；钻石定价升高时门槛也会上调。',
         'Progression matters: some loot is sell-only; other goods require advancements. Unconfigured mod items are locked.$(br2)Enchant purchases give books for an anvil. Mending costs at least 65,536 points: 256 base-value diamonds. The floor rises with diamond prices.')
    ],'basics',1)
    entry('controls','teamecon:multiplier_machine','下注与实体操作','Physical controls',[
        ('放置机器前留足空间：一般机型占 2×2、高 3 格；跳冰占 3×2；两种轮盘保留 3×3 占地。$(br2)右键机身的减/加按钮调整下注；蹲下是十倍步长。$(bold)倍率$() 打开输入框，预览放大后的金额，应用后再点击开始。',
         'Leave room before placing machines: most need 2×2 with height 3; Slime Hop needs 3×2 and both wheels keep a 3×3 footprint.$(br2)Right-click ± buttons; sneak for tenfold steps. $(bold)Factor$() opens the multiplier field. Apply it, then start.'),
        ('颜色与大小均可直接点击，不需要循环切换。机器有人闯关时由该玩家继续或提取。$(br2)下注在服务器扣款；上限、等级、余额均由服务器检查。设置倍数不会自动下注。提取只支付一次，并回到最初出资本局的钱包。',
         'Select colours or high/low directly. An active run belongs to its player.$(br2)The server checks funds, level and stake limits. Applying a factor does not place a bet. Cash-out pays once, into the wallet that originally funded the round.')
    ],'basics',2)
    entry('levels','minecraft:experience_bottle','钱包与设备等级','Wallets and levels',[
        ('有 FTB Teams 时队友共用钱包及设备等级；退队恢复个人原有等级。$(br2)升级必须逐级进行：Lv.2 花费 2,000；Lv.3 为 10,000；Lv.4 为 50,000；Lv.5 为 200,000 点。重复请求不会重复扣款。',
         'With FTB Teams, teammates share a wallet and machine level. Leaving restores your personal level.$(br2)Upgrade one level at a time: Lv.2 costs 2,000; Lv.3 10,000; Lv.4 50,000; Lv.5 200,000 points. Repeated requests do not charge twice.'),
        ('默认解锁时的下注上限：$(br)Lv.1 猜大小：2,000$(br)Lv.2 跳冰：10,000$(br)Lv.3 幸运转盘：50,000$(br)Lv.4 黑红轮盘：100,000$(br)Lv.5 老虎/倍率机：200,000$(br2)高出解锁等级 1/2/3/4 级时，上限 ×2/5/10/25，受全局上限约束。终端需 Lv.5、解放末地及 100,000 点。',
         'Default unlocks / stake limits:$(br)Lv.1 High/Low: 2,000$(br)Lv.2 Slime Hop: 10,000$(br)Lv.3 Lucky Wheel: 50,000$(br)Lv.4 Roulette: 100,000$(br)Lv.5 Slots/Crash: 200,000$(br2)Terminal: Lv.5, Free the End, and 100,000 points. No crafting recipe.')
    ],'basics',3)
    entry('hilo','teamecon:hilo_table','猜大小','High / Low',[
        ('直接选择大或小，然后下注开始。服务器等概率掷出 1–100。$(br2)小：1–50；大：51–100。两者胜率均为 50%，默认成功返奖 ×1.96，失败归零。$(br2)默认 Lv.1，上限 2,000 点。',
         'Choose high or low, then start. The server draws uniformly from 1–100.$(br2)Low: 1–50. High: 51–100. Each wins 50% of the time, paying ×1.96 by default; losses pay zero.$(br2)Default: Lv.1, stake limit 2,000.')
    ],order=0)
    entry('penguin','teamecon:penguin_machine','史莱姆跳冰','Slime Hop',[
        ('一次开始只扣一次本金，然后史莱姆每次跳$(bold)一层$()。落稳后可继续下一层，或点击$(bold)提取并结束$()，立即结算。$(br2)一旦踏空，冰块碎裂、史莱姆坠落，整局倍率与可提取金额归零，游戏结束。动画每跳约 0.7 秒。',
         'One stake funds the run. Each jump advances $(bold)one layer$(). After landing, jump again or $(bold)cash out$() to end and collect immediately.$(br2)A fall breaks the ice and ends the run at zero. Each jump animation takes about 0.7 seconds.'),
        ('默认各层总返奖倍率：$(br)1: ×1.20　2: ×1.50$(br)3: ×2.00　4: ×2.75$(br)5: ×4.00　6: ×6.00$(br)7: ×10　8: ×16$(br)9: ×25　10: ×40$(br2)每步成功率 = 0.96 × 当前倍率 / 下一层倍率。因此第一跳为 80%，第一层到第二层为 76.8%。',
         'Default total payout by layer:$(br)1: ×1.20; 2: ×1.50$(br)3: ×2; 4: ×2.75$(br)5: ×4; 6: ×6$(br)7: ×10; 8: ×16$(br)9: ×25; 10: ×40$(br2)Step chance = 0.96 × current / next multiplier. First jump: 80%; layer 1 to 2: 76.8%.'),
        ('每多跳一次，都要重新承担失败风险；之前成功不会保证下一跳安全。默认最高 ×40，达到后只能提取。$(br2)默认 Lv.2，上限 10,000 点。离开实体机、断线或机器卸载时，已完成的跳冰进度自动提取回原钱包。',
         'Every additional layer risks the entire run. Earlier success does not guarantee a safe next jump. The default cap is ×40; then cash out.$(br2)Default: Lv.2, limit 10,000. Leaving, disconnecting or unloading the machine cashes out completed hop progress.')
    ],order=1)
    entry('color_wheel','teamecon:color_wheel_table','幸运转盘','Lucky Wheel',[
        ('原「滚珠彩轮」改为$(bold)直立指针转盘$()。下注后轮面旋转，固定在顶端的指针所指色块就是结果，无需猜颜色。$(br2)40 格等概率：灰 23 格；绿 9 格；蓝 5 格；紫 2 格；金 1 格。',
         'Formerly the marble wheel: now an $(bold)upright pointer wheel$(). Stake and spin; the fixed pointer at the top determines the colour. No colour bet is needed.$(br2)40 equal pockets: 23 grey, 9 green, 5 blue, 2 purple and 1 gold.'),
        ('默认返奖（包含本金）：$(br)灰 ×0：57.5%$(br)绿 ×1：22.5%$(br)蓝 ×2：12.5%$(br)紫 ×4：5%$(br)金 ×10：2.5%$(br2)默认 Lv.3，上限 50,000 点。转盘停稳后自动入账。',
         'Default payouts (stake included):$(br)Grey ×0: 57.5%$(br)Green ×1: 22.5%$(br)Blue ×2: 12.5%$(br)Purple ×4: 5%$(br)Gold ×10: 2.5%$(br2)Default: Lv.3, limit 50,000. Credits when the wheel stops.')
    ],order=2)
    entry('roulette','teamecon:roulette_table','黑红轮盘','Red & Black Roulette',[
        ('原「幸运转盘」现名黑红轮盘，为欧式单零 37 格轮盘：18 红、18 黑、绿色 0。$(br2)机身直接选择红、黑、绿零；也可右键轮面数字选单号。终端还支持奇偶与 1–18 / 19–36。绿色按钮等同押单号 0。',
         'The former Lucky Roulette is Red & Black Roulette: a European single-zero wheel with 18 red, 18 black and green 0.$(br2)Select red, black or green directly; click a numbered pocket for a straight bet. The terminal also offers odd/even and low/high ranges.'),
        ('红黑、奇偶与大小区间胜率 18/37，默认实际返奖$(bold)×1.8$()；单号胜率 1/37，默认实际返奖$(bold)×32.4$()，均包含本金。$(br2)0 使所有外围下注失败，绿色按钮只写“绿”，仍代表押 0。默认 Lv.4，上限 100,000 点。',
         'Outside bets win 18/37 and pay $(bold)×1.8$() by default. Straight numbers win 1/37 and pay $(bold)×32.4$(). Both include the stake.$(br2)Zero loses all outside bets, but wins a green-zero bet. Default: Lv.4, limit 100,000.')
    ],order=3)
    entry('slots','teamecon:slot_machine','老虎机','Slots',[
        ('三个转轴独立抽取，各轴默认权重相同：樱桃 25%、柠檬 25%、橙子 20%、铃铛 15%、星星 10%、钻石 5%。$(br2)动画依次停下，按最终组合支付。多个条件同时满足时只取最高一项，不叠加。',
         'Three independent reels use these weights: cherry 25%, lemon 25%, orange 20%, bell 15%, star 10%, diamond 5%.$(br2)Reels stop in sequence. The final combination pays once: only the highest matching prize applies.'),
        ('默认组合与总返奖倍率：$(br)任意位置 2 樱桃：×2$(br)3 樱桃：×6$(br)3 柠檬：×8$(br)3 橙子：×13$(br)3 铃铛：×28$(br)3 星星：×66$(br)3 钻石：×275$(br2)其余 ×0。3 钻石概率为 0.05³ = 0.0125%（1/8,000）。',
         'Default combinations / total payout:$(br)2 cherries anywhere: ×2$(br)3 cherries: ×6$(br)3 lemons: ×8$(br)3 oranges: ×13$(br)3 bells: ×28$(br)3 stars: ×66$(br)3 diamonds: ×275$(br2)Others: ×0. Three diamonds: 0.0125% (1 in 8,000).'),
        ('默认 Lv.5，上限 200,000 点。$(br2)实体机动画完成后自动入账，即使中途离线、机器被拆或区块卸载，已生成的结算仍由服务器执行一次。',
         'Default: Lv.5, stake limit 200,000.$(br2)Physical machines credit when their animation completes. Disconnecting, breaking or unloading a machine does not lose an already scheduled payout; the server settles it once.')
    ],order=4)
    entry('multiplier','teamecon:multiplier_machine','倍率竞猜机','Crash multiplier',[
        ('下注后倍率从 ×1.00 $(bold)持续上涨$()，不再选择风险档或点击下一轮。点击$(bold)提取并结束$()，按服务器收到操作时的倍率结算。$(br2)崩盘时直接变成 ×0.00，整局本金和未提取收益清零；崩盘后的提取不能救回本金。',
         'After betting, the multiplier $(bold)rises continuously$() from ×1.00. No tiers or next-round button. $(bold)Cash out$() at the value when the server receives your action.$(br2)A crash drops it to ×0.00: stake and unclaimed gains are lost. Late cash-out cannot rescue them.'),
        ('走势图横向表示经过时间，纵向表示倍率，只画已经发生的上涨；崩盘时显示红线落到零。$(br2)默认约 5.8 秒到 ×2，但只有约 39.8% 的开局能活到这里；约 20.3% 会开局即归零。曲线不能预测下一刻何时崩盘。',
         'The chart plots elapsed time horizontally and the multiplier vertically. It only shows observed growth; a crash draws a red fall to zero.$(br2)It takes about 5.8 seconds to reach ×2; roughly 39.8% of starts survive that far. About 20.3% crash instantly. The curve cannot predict the crash.'),
        ('$(bold)关闭界面、离开机器、断线不会暂停或自动提取$()，服务器继续计时，可能全部亏损。服务器停止运行时游戏刻暂停。$(br2)默认 Lv.5，上限 200,000 点，倍率封顶 ×1,000；若存活至上限则自动提取。提取始终返回原出资钱包。',
         '$(bold)Closing, leaving or disconnecting does not pause or cash out$(). Server time continues and the round can be lost. Time pauses only when the server stops.$(br2)Default: Lv.5, limit 200,000; surviving to ×1,000 auto-cashes out to the funding wallet.')
    ],order=5)
    entry('tickets','teamecon:scratch_card_match','刮卡玩法','How to scratch',[
        ('从商店或终端买到实体卡，放主手$(bold)左键$()打开，按住左键拖动刮开全部银层。完成后自动兑奖，无需按钮。$(br2)购买时已确定奖项；关闭重开不会重抽。可以转交他人，由实际持卡者完成刮取后收款。每张序列只支付一次。$(br2)可选择票面倍数；奖金也同比放大，概率不变。Lv.1–5 单张票面上限为 100 / 1,000 / 10,000 / 100,000 / 250,000 点。',
         'Buy a physical card, hold it and $(bold)left-click$() to open. Drag to scratch all foil; payment is automatic.$(br2)The prize is fixed at purchase. Reopening does not reroll. Cards may be transferred; the holder receives the prize once.$(br2)Stake factors multiply both price and prize, with unchanged odds. Lv.1–5 ticket stake caps: 100 / 1,000 / 10,000 / 100,000 / 250,000.'),
        ('数字卡：匹配幸运数字。骰子卡：同一对相加为 7。水果卡：一行三个相同水果。幸运七：找到 7。$(br2)宝石卡：至少 3 颗钻石。宾果：金币横、竖、斜连线。密码卡：匹配三位密码。皇冠卡：至少 3 顶皇冠，7 顶获得 ×500。',
         'Numbers: match the target. Dice: a pair totals 7. Fruit: three matching fruit in a row. Sevens: find 7.$(br2)Gems: 3+ diamonds. Bingo: a gold line. Vault: match the three-digit code. Crown: 3+ crowns; seven crowns pay ×500.')
    ],'tickets',0)
    for lang,text in [('zh_cn','无线终端首页提供商店、回收、盲盒、升级与全部游戏。盲盒也有独立的两格高机器；奖励继续检查冒险进度。$(br2)游戏右侧显示队伍余额与当前成员净赚：回收收入 + 游戏返奖 − 下注。购物花费不扣净赚；离线成员也显示，多人时自动翻页。'),('en_us','The terminal home opens the shop, recycler, boxes, upgrades and games. Mystery boxes also have a dedicated two-block cabinet, with the same progression rules.$(br2)The right-side team panel shows balance and member net earnings: sales + payouts - stakes. Shopping is excluded. Offline members are included; large teams page automatically.')]:
        path=ROOT/f'assets/teamecon/patchouli_books/casino_guide/{lang}/entries/economy.json'
        data=json.loads(path.read_text(encoding='utf-8'));data['pages'].append({'type':'patchouli:text','title':'','text':text});write(path.relative_to(ROOT),data)
    specs=[('starter','入门卡','Starter cards','match','数字 10 点、骰子 25 点；Lv.1。','Numbers 10, Dice 25 points; Lv.1.',[(0,42),(1,38),(2,15),(5,4.5),(10,.5)],95.5),
           ('middle','中阶卡','Mid-tier cards','fruit','水果 50、幸运七 100 点（Lv.2）；宝石 250 点（Lv.3）。','Fruit 50, Sevens 100 (Lv.2); Gems 250 points (Lv.3).',[(0,56.5),(1,24),(2,13.5),(5,4.5),(10,1.3),(30,.2)],92.5),
           ('advanced','高阶卡','Advanced cards','vault','宾果 500 点、密码宝库 1,000 点；Lv.4。','Bingo 500, Vault 1,000 points; Lv.4.',[(0,80.05),(1,12),(3,5),(10,2.4),(50,.5),(200,.05)],86),
           ('crown','皇冠大奖','Crown jackpot','crown','皇冠 2,500 点；Lv.5。','Crown 2,500 points; Lv.5.',[(0,91),(1,5),(5,2.5),(20,1.2),(50,.25),(500,.05)],79)]
    for order,(id,zh,en,icon,zhintro,enintro,odds,rtp) in enumerate(specs,1):
        table='$(br)'.join(f'×{m}: {p:g}%' for m,p in odds)
        entry(id,'teamecon:scratch_card_'+icon,zh,en,[(zhintro+'$(br2)各奖级出现概率：$(br)'+table,enintro+'$(br2)Chance of each prize tier:$(br)'+table)],'tickets',order)
    write('data/teamecon/recipe/guide_book.json',{'type':'minecraft:crafting_shapeless','category':'misc',
        'ingredients':[{'item':'minecraft:book'},{'item':'minecraft:iron_ingot'}],'result':{'id':'teamecon:guide_book','count':1}})
    write('data/teamecon/advancement/recipes/guide_book.json',{'parent':'minecraft:recipes/root',
        'criteria':{'has_book':{'trigger':'minecraft:inventory_changed','conditions':{'items':[{'items':'minecraft:book'}]}},
                    'has_the_recipe':{'trigger':'minecraft:recipe_unlocked','conditions':{'recipe':'teamecon:guide_book'}}},
        'requirements':[['has_book','has_the_recipe']],'rewards':{'recipes':['teamecon:guide_book']}})
    from gen_revision_assets import handbook
    handbook()
    from gen_release_assets import main as release
    release()

if __name__=='__main__':main()
