"""Authoritative 0.4.1 strings, enlarged wheel sectors and concise player handbook pages."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

RES=Path(__file__).resolve().parents[1]/'src/main/resources'
ASSETS=RES/'assets/teamecon'
TEXT={
    'gui.teamecon.shop.search':('名称/拼音/ID/#标签','Name / pinyin / ID / #tag'),
    'gui.teamecon.shop.card_stake':('票面 %s 积分','Face value: %s'),
    'gui.teamecon.shop.sale_slot':('待售区 · 27 格','Sale deposits · 27 slots'),
    'gui.teamecon.shop.sell_stack':('出售全部待售物品','Sell all deposits'),
    'gui.teamecon.shop.sell_instructions':('Shift 点击放入待售区，核对总报价后出售。','Shift-click items into the deposit grid, review the total, then sell.'),
    'gui.teamecon.shop.sell_hint':('只出售 27 格待售区中的原版物品。未出售或不支持的物品在关闭时退回。相同材料共享需求，小数收入保留。','Sells vanilla items in the 27 deposit slots. Unsold and unsupported items return on closing. Related materials share demand; fractions carry forward.'),
    'gui.teamecon.shop.sell_hint_short':('未出售的物品在关闭时退回。','Unsold items return on closing.'),
    'gui.teamecon.shop.sale_quote':('总报价：%s 点','Total quote: %s'),
    'gui.teamecon.shop.sale_empty':('请放入可回收的原版物品','Deposit vanilla items to recycle'),
    'gui.teamecon.shop.footer':('按类型浏览 · 名称 / ID / #标签搜索 · 滚轮翻页','Browse by type · Search name / ID / #tag · Scroll pages'),
    'shop.teamecon.lock.mod_disabled':('暂不支持模组物品交易','Mod item trading is currently disabled'),
    'shop.teamecon.box_batch':('已开启 %s 个盲盒，花费 %s 点','Opened %s boxes for %s points'),
    'gui.teamecon.boxes.title':('盲盒小站','Mystery Box Counter'),
    'gui.teamecon.boxes.subtitle':('挑选一款盲盒，开启这一份惊喜','Choose a box and reveal your surprise'),
    'gui.teamecon.boxes.unit_price':('%s 点 / 个','%s points each'),
    'gui.teamecon.boxes.total':('%s 个 · 共 %s 点','%s boxes · %s points'),
    'gui.teamecon.boxes.open':('开启盲盒','Open boxes'),
    'gui.teamecon.boxes.empty':('暂无可用盲盒','No boxes available'),
    'gui.teamecon.boxes.loading':('正在载入…','Loading…'),
    'gui.teamecon.boxes.opening':('惊喜即将揭晓…','Opening your surprise…'),
    'gui.teamecon.boxes.ready':('选择数量后开启','Choose a quantity to open'),
    'gui.teamecon.boxes.delivery':('奖品直接放入背包 · 无成就限制','Prizes go into your inventory · No advancement requirement'),
    'gui.teamecon.boxes.no_prize':('空盒','Empty box'),
    'gui.teamecon.boxes.reward_page':('奖品 %s/%s · 滚轮翻页','Prizes %s/%s · Scroll to browse'),
    'machine.teamecon.bet_input.base':('固定基数：%s 点','Base stake: %s'),
    'machine.teamecon.bet_input.factor':('设置整数倍数（可调低）','Set an integer factor'),
    'machine.teamecon.control.bet_multiplier':('右键：设置下注倍数','Right-click: set stake factor'),
    'machine.teamecon.hint':('点击按钮设置金额，再点击开始','Set the stake, then press Start'),
    'machine.teamecon.control.start':('右键：开始 / 继续','Right-click: start / continue'),
    'machine.teamecon.control.cash_out':('右键：提取并结束','Right-click: cash out'),
    'machine.teamecon.control.none':('瞄准按钮并右键','Aim at a button and right-click'),
    'machine.teamecon.control.high':('右键：选大','Right-click: high'),
    'machine.teamecon.control.low':('右键：选小','Right-click: low'),
    'machine.teamecon.control.red':('右键：选红色','Right-click: red'),
    'machine.teamecon.control.black':('右键：选黑色','Right-click: black'),
    'machine.teamecon.control.green':('右键：选绿色 0','Right-click: green zero'),
    'machine.teamecon.control.wheel':('右键：选择单号','Right-click: select number'),
    'gui.teamecon.bet_multiply':('应用倍数','Apply factor'),
    'gui.teamecon.odds_header':('玩法与奖励','Rules & rewards'),
    'gui.teamecon.odds.next_layer':('第 %s 层 → ×%s','Layer %s → ×%s'),
    'gui.teamecon.odds.hilo.chance':('猜中奖励 ×%s','Correct guess pays ×%s'),
    'gui.teamecon.odds.roulette.outside':('红黑 / 单双 / 大小：×%s','Outside bets: ×%s'),
    'gui.teamecon.odds.roulette.straight':('单号（含绿色 0）：×%s','Straight number (including 0): ×%s'),
    'scratch.teamecon.paytable':('奖级倍率','Prize multipliers'),
    'hud.teamecon.balance':('总余额  %s','Balance  %s'),
}

def write(path,data):
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def main():
    for i,lang in enumerate(['zh_cn','en_us']):
        path=ASSETS/f'lang/{lang}.json';data=json.loads(path.read_text(encoding='utf-8'))
        data.update({k:v[i] for k,v in TEXT.items()});write(path,dict(sorted(data.items())))
    # Same 40 probability units as the game, arranged into 20 larger, outlined colour sectors.
    colors=['#ffcf4d','#303842','#40c879','#303842','#499ff4','#303842','#b374ea','#303842','#40c879','#303842',
            '#499ff4','#303842','#40c879','#303842','#b374ea','#303842','#40c879','#303842','#499ff4','#40c879']
    weights=[1,3,2,3,2,3,1,3,2,3,2,2,2,2,1,2,2,2,1,1]
    im=Image.new('RGBA',(512,512));d=ImageDraw.Draw(im)
    d.ellipse((2,2,509,509),fill='#14242f',outline='#ffe3a0',width=8)
    start=0
    for color,weight in zip(colors,weights):
        d.pieslice((15,15,496,496),start*9,(start+weight)*9,fill=color,outline='#edcc7b',width=5);start+=weight
    d.ellipse((215,215,297,297),fill='#203e41',outline='#edcc7b',width=6)
    im.save(ASSETS/'textures/gui/prize_wheel.png')

def handbook():
    from gen_guide import entry
    book=RES/'data/teamecon/patchouli_books/casino_guide/book.json'
    data=json.loads(book.read_text(encoding='utf-8'));data['version']='0.4.1';write(book,data)
    for lang in ['zh_cn','en_us']:
        path=ASSETS/f'patchouli_books/casino_guide/{lang}/categories/machines.json'
        data=json.loads(path.read_text(encoding='utf-8'));data['description']='操作、奖励与结算规则。' if lang=='zh_cn' else 'Controls, prizes and settlement.';write(path,data)
    entry('welcome','teamecon:guide_book','开始使用','Start here',[
        ('首次进入会获赠一本指南，也可用一本书与一块铁锭合成。右键阅读，需要安装帕秋莉手册。$(br2)新钱包起始 200 点；回收日常采集的物资是稳定收入来源。游戏可能亏损，不需要靠中奖解锁升级。',
         'First login grants this guide. You can also craft it with a book and an iron ingot. Reading requires Patchouli.$(br2)New wallets start with 200 points. Selling gathered resources funds progression. Games can lose money; winning is not required for upgrades.'),
        ('奖励倍率均包含本金：下注 100 点，×2 入账 200 点；亏损则入账 0。$(br2)本书介绍操作与奖级。实际价格、等级与额度以当前服务器为准。',
         'Payout multipliers include the stake: betting 100 at ×2 credits 200; a loss credits zero.$(br2)This guide explains controls and prizes. Live prices, levels and limits follow the server settings.')
    ],'basics',0)
    entry('economy','teamecon:shop_machine','商店与回收','Shop and recycling',[
        ('商店有物品、出售、机器、刮卡、升级五页。物品页包含 43 种附魔书，用上方分类图标按物品类型筛选。名称、ID 与 #标签均可搜索，滚轮翻页。$(br2)商店与盲盒机都是两格高；取货动画结束前，购买物品已放入背包。',
         'Shop tabs: Items, Sell, Machines, Tickets and Levels. Items include 43 enchantment books. Use the visible type icons and search names, IDs or #tags; scroll to turn pages.$(br2)Both vending cabinets are two blocks tall. Purchases go directly into your inventory.'),
        ('出售页有 27 格待售区。Shift 点击快速放入，核对总报价后一次出售。未售与不支持的物品关闭时退回。$(br2)同材料组共享需求递减，小数收入会保留。购买至少按基础回收价 ×2 收费；美西螈桶默认至少 8,192 点。',
         'The Sell page has 27 deposit slots. Shift-click items in, review the total, then sell the batch. Unsold items return on closing.$(br2)Related materials share demand decay; fractional income carries forward. Buying costs at least twice base resale value. Axolotl buckets cost at least 8,192 points.'),
        ('第三方模组物品暂不买卖。原版关键战利品仍只回收不售卖，其他受限商品按冒险进度解锁。附魔购买提供实体附魔书。$(br2)盲盒仅在盲盒机的独立界面购买：无成就门槛，可开 1 / 10 / 64 个，奖品清单随后显示。',
         'Third-party mod items cannot be traded. Key vanilla loot remains sell-only; other restricted items unlock through exploration. Enchantment purchases give real books.$(br2)Boxes have a separate cabinet screen with no advancement requirement. Open 1 / 10 / 64 boxes and inspect the prize receipt.'),
        ('有 FTB Teams 时队员共享钱包。透明计分栏位于最右侧居中，显示总余额及当前成员净赚，含离线成员。$(br2)净赚 = 回收收入 + 游戏奖励 − 下注；购物不计入。人数较多时自动翻页。无线终端提供商店、回收、升级与游戏。',
         'With FTB Teams, members share a wallet. The transparent board at the right centre shows balance and member net earnings, including offline members.$(br2)Net earnings = sales + game payouts − stakes. Shopping is excluded. Large teams page automatically. The terminal opens shopping, recycling, upgrades and games.')
    ],'basics',1)
    entry('controls','teamecon:multiplier_machine','下注与实体操作','Physical controls',[
        ('一般机器需 2×2、高 3 格空间；跳冰宽 3 格，两种轮盘占地 3×3。$(br2)面板从上到下为：开始、下注金额、选项与金额调整按钮。右键 ± 按钮调整，蹲下为十倍步长。',
         'Most machines need 2×2 with height 3; Slime Hop is 3 wide and both wheels use a 3×3 footprint.$(br2)Panel order: Start, stake display, then choices and adjustment buttons. Right-click ±; sneak for tenfold steps.'),
        ('倍率基于固定基数。例如基数 100，设 ×10 是 1,000；重新设 ×2 是 200，设 ×1 回到 100。重新输入不会叠乘。使用 ± 按钮或直接输入金额会建立新的基数。$(br2)应用倍数不会自动下注。闯关只能由原玩家继续或提取，结算回原出资钱包。',
         'Factors use a fixed base. With base 100, ×10 sets 1,000; changing to ×2 sets 200; ×1 restores 100. Editing a factor never compounds it. Manual stake changes establish a new base.$(br2)Applying a factor does not bet. Only the run owner can continue or cash out, into the original wallet.')
    ],'basics',2)
    entry('levels','minecraft:experience_bottle','钱包与设备等级','Wallets and levels',[
        ('队友共享设备等级，退队恢复个人等级。升级需逐级支付：$(br)Lv.2：1,200 点$(br)Lv.3：10,000 点$(br)Lv.4：50,000 点$(br)Lv.5：200,000 点$(br2)升级解锁使用权限，机器需另外合成或购买。',
         'Team members share machine levels; leaving restores your personal level. Upgrade costs:$(br)Lv.2: 1,200$(br)Lv.3: 10,000$(br)Lv.4: 50,000$(br)Lv.5: 200,000$(br2)Levels unlock access; machines are crafted or purchased separately.'),
        ('各机器刚解锁时的下注上限：猜大小 2,000、跳冰 10,000、幸运转盘 50,000、黑红轮盘 100,000、老虎与倍率机 200,000。$(br2)钱包高出解锁等级 1/2/3/4 级时，上限变为 ×2/5/10/25，仍受服务器总上限限制。早期机器后期仍可用。',
         'Initial stake limits: High/Low 2,000; Slime Hop 10,000; Lucky Wheel 50,000; Roulette 100,000; Slots/Crash 200,000.$(br2)Being 1/2/3/4 levels above unlock raises the limit by ×2/5/10/25, within the server cap. Earlier machines remain useful.'),
        ('无线终端默认需要 Lv.5、完成「解放末地」，并支付 100,000 点。刮卡可提高票面倍数，最高票面随钱包等级提高。',
         'The terminal requires Lv.5, Free the End and 100,000 points by default. Ticket stake factors increase their face value, within a limit that grows with wallet level.')
    ],'basics',3)
    entry('hilo','teamecon:hilo_table','猜大小','High / Low',[
        ('选择小（1–50）或大（51–100），再点击开始。猜对默认 ×1.96，猜错归零。$(br2)默认 Lv.1，初始上限 2,000 点，Lv.5 可到 50,000 点。适合小额、短局游戏。',
         'Choose Low (1–50) or High (51–100), then Start. A correct guess pays ×1.96; a miss pays zero.$(br2)Lv.1 limit: 2,000, growing to 50,000 at Lv.5. Short rounds with smaller stakes.')
    ],order=0)
    entry('penguin','teamecon:penguin_machine','史莱姆跳冰','Slime Hop',[
        ('一局只扣一次本金，每次向上一层；落稳后可继续或提取。倍率越高，下一跳越难。即使最后一跳被上限截短，也不会变容易。$(br2)踏空后冰块碎裂，整局归零。默认 Lv.2，初始下注上限 10,000。',
         'One stake funds a run. Each jump climbs one layer; land, then continue or cash out. Higher multipliers make the next jump harder, even at a shortened final step.$(br2)Falling breaks the ice and loses the run. Default Lv.2, initial stake limit 10,000.'),
        ('各层总倍率：$(br)×1.2、×1.5、×2、×2.75、×4、×6、×10、×16、×25、×40。$(br2)最高 ×40，到达后提取。离开机器、断线或卸载区块时，已完成的跳冰进度自动结算回原钱包。',
         'Layer multipliers:$(br)×1.2, ×1.5, ×2, ×2.75, ×4, ×6, ×10, ×16, ×25, ×40.$(br2)Cash out at the cap. Leaving, disconnecting or unloading the machine automatically settles completed hop progress to the original wallet.')
    ],order=1)
    entry('color_wheel','teamecon:color_wheel_table','幸运转盘','Lucky Wheel',[
        ('下注后转盘旋转，顶部大指针指向最终结果。无需猜颜色。转盘使用较宽的色块，每块带金色边框。$(br2)奖励包含本金：灰 ×0、绿 ×1、蓝 ×2、紫 ×4、金 ×10。默认 Lv.3，初始上限 50,000 点。',
         'Stake and spin. The large top pointer marks the result; no colour guess is needed. Wider colour sectors have individual gold borders.$(br2)Payouts include the stake: grey ×0, green ×1, blue ×2, purple ×4, gold ×10. Default Lv.3, initial limit 50,000.')
    ],order=2)
    entry('roulette','teamecon:roulette_table','黑红轮盘','Red & Black Roulette',[
        ('轮盘台面已降低，站在前面可看到轮面。37 个数字：18 红、18 黑、绿色 0。$(br2)点击红或黑，中奖 ×2；点击绿色相当于押单号 0，中奖 ×32。也可右键轮面选其他单号，中奖 ×32。',
         'The lowered tabletop is visible while standing in front. The wheel has 37 numbers: 18 red, 18 black and green zero.$(br2)Red or black pays ×2. Green bets on number 0 and pays ×32. Click other wheel numbers for straight bets paying ×32.'),
        ('所有倍率包含本金。绿色 0 会使红黑等外围下注失败。默认 Lv.4，初始上限 100,000 点。',
         'All multipliers include the stake. Zero loses outside bets such as red/black. Default Lv.4, initial limit 100,000.')
    ],order=3)
    entry('slots','teamecon:slot_machine','老虎机','Slots',[
        ('三个转轴依次停下，按最终组合给奖。多个条件同时满足只取最高一项。$(br2)任意位置 2 樱桃 ×2；3 樱桃 ×6；3 柠檬 ×8；3 橙子 ×13；3 铃铛 ×28；3 星星 ×66；3 钻石 ×275。',
         'Three reels stop in sequence and award the highest matching combination once.$(br2)2 cherries anywhere ×2; 3 cherries ×6; 3 lemons ×8; 3 oranges ×13; 3 bells ×28; 3 stars ×66; 3 diamonds ×275.'),
        ('其余组合无奖励。默认 Lv.5，初始上限 200,000 点。动画完成后自动入账，离线或机器卸载不会丢失已安排的结算。',
         'Other combinations pay zero. Default Lv.5, initial limit 200,000. Payout is automatic after the animation; disconnecting or unloading does not lose scheduled settlement.')
    ],order=4)
    entry('multiplier','teamecon:multiplier_machine','倍率竞猜机','Crash multiplier',[
        ('下注后倍率从 ×1 持续上涨，越到后面上涨越快。随时点击提取，按服务器收到操作时的倍率结算。$(br2)高倍率很罕见，但有极小机会达到 ×100；默认封顶 ×1,000，存活到封顶会自动提取。',
         'The multiplier rises continuously from ×1 and accelerates over time. Cash out at the value when the server receives your action.$(br2)High values are rare, with a very small chance of ×100. The default cap is ×1,000, which automatically cashes out survivors.'),
        ('崩盘直接归零，失去本金及未提取收益。曲线只显示已经发生的上涨，不能预测崩盘。$(br2)关闭界面、离开机器或断线不会暂停或自动提取。默认 Lv.5，初始上限 200,000 点。',
         'A crash loses the stake and unclaimed gains. The chart shows observed growth; it cannot predict the crash.$(br2)Closing, leaving or disconnecting does not pause or cash out. Default Lv.5, initial limit 200,000.')
    ],order=5)
    # Keep the existing ticket instructions and illustrations; replace statistical tables with prize tiers.
    tiers={'starter':('数字 10 点、骰子 25 点；Lv.1。','Numbers 10, Dice 25; Lv.1.'),
           'middle':('水果 50、幸运七 100（Lv.2）；宝石 250 点（Lv.3）。','Fruit 50, Sevens 100 (Lv.2); Gems 250 (Lv.3).'),
           'advanced':('宾果 500、密码宝库 1,000 点；Lv.4。','Bingo 500, Vault 1,000; Lv.4.'),
           'crown':('皇冠 2,500 点；Lv.5。7 顶皇冠最高 ×500。','Crown 2,500; Lv.5. Seven crowns pay up to ×500.')}
    for langIndex,lang in enumerate(['zh_cn','en_us']):
        for name,pair in tiers.items():
            path=ASSETS/f'patchouli_books/casino_guide/{lang}/entries/{name}.json';data=json.loads(path.read_text(encoding='utf-8'))
            text=pair[langIndex]+('$(br2)购卡时可提高票面倍数，奖金同比提高。票面上限随钱包等级增长；具体奖级见卡片。刮开后自动入账。' if langIndex==0 else '$(br2)A higher stake factor scales both price and prize. Face-value limits grow with wallet level. Prize tiers appear on each card; scratching settles automatically.')
            data['pages']=data['pages'][:1]+[{'type':'patchouli:text','title':'','text':text}];write(path,data)

if __name__=='__main__':main()
