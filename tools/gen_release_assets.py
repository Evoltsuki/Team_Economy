"""Final player-facing 1.0 resources. Called last by both resource and guide generators."""
from pathlib import Path
import json
from opencc import OpenCC

RES = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = RES / 'assets/teamecon'
BOOK = ASSETS / 'patchouli_books/casino_guide'
TEXT = {
    'gui.teamecon.hilo_range': ('小 1–5 / 大 6–10', 'Low 1–5 / High 6–10'),
    'gui.teamecon.odds.hilo.high': ('大：6–10', 'High: 6–10'),
    'gui.teamecon.odds.hilo.low': ('小：1–5', 'Low: 1–5'),
    'gui.teamecon.odds.hilo.line': ('大 6–10 或小 1–5，猜中奖励 ×%s', 'High 6–10 or Low 1–5; correct guess pays ×%s'),
    'gui.teamecon.boxes.contents': ('奖池预览', 'Contents'),
    'gui.teamecon.boxes.receipt': ('本次奖品', 'Last opening'),
    'gui.teamecon.boxes.page': ('%s / %s', '%s / %s'),
    'gui.teamecon.boxes.one_prize': ('每个盲盒随机获得其中一项', 'Each box awards one random entry'),
    'gui.teamecon.boxes.rare_prize': ('珍稀奖品 · 不可购买或回收', 'Rare prize · Cannot be bought or sold'),
    'gui.teamecon.boxes.subtitle': ('先看奖池，再选择数量开启', 'Preview the contents, then choose how many to open'),
    'shop.teamecon.pool.common': ('物资盲盒', 'Supply Box'),
    'shop.teamecon.pool.rare': ('珍藏盲盒', 'Treasure Box'),
    'hud.teamecon.balance': ('余额 %s', 'Balance %s'),
    'book.teamecon.landing': ('把多余物资变成小队的共同目标。\n从回收到升级，再到六种实体小游戏。\n\n制作：洁柔厨\nTeam Economy 1.0', 'Turn spare supplies into shared goals.\nRecycling, upgrades and six physical minigames.\n\nCreated by 洁柔厨\nTeam Economy 1.0'),
}

def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def chapter(id, titles, icon, picture, captions, pages, category='basics', order=0):
    for i, lang in enumerate(('zh_cn', 'en_us')):
        write(BOOK/lang/'entries'/f'{id}.json', {
            'name': titles[i], 'icon': icon, 'category': 'teamecon:'+category, 'sortnum': order,
            'pages': [{'type': 'patchouli:image', 'title': titles[i],
                       'images': [f'teamecon:textures/gui/guide/{picture}.png'], 'border': False, 'text': captions[i]}]
                     + [{'type': 'patchouli:text', 'title': '', 'text': pair[i]} for pair in pages]})

def main():
    from PIL import Image, ImageDraw, ImageFont
    logo=Image.new('RGB',(512,256),'#10272c'); draw=ImageDraw.Draw(logo)
    draw.rounded_rectangle((16,16,496,240),24,outline='#73e2b0',width=4)
    stamp=Image.open(ASSETS/'textures/item/shop_machine.png').convert('RGBA').resize((176,176),Image.Resampling.NEAREST)
    logo.paste(stamp,(28,40),stamp)
    for y,word in ((76,'TEAM'),(128,'ECONOMY')):
        draw.text((216,y),word,font=ImageFont.load_default(size=37),fill='#f1cf7c')
    logo.save(RES/'teamecon-logo.png')
    for i, lang in enumerate(('zh_cn','en_us')):
        path=ASSETS/'lang'/f'{lang}.json'; data=json.loads(path.read_text(encoding='utf-8'))
        data.update({k:v[i] for k,v in TEXT.items()}); write(path, dict(sorted(data.items())))
    book=RES/'data/teamecon/patchouli_books/casino_guide/book.json'
    data=json.loads(book.read_text(encoding='utf-8')); data['version']='1.0.0'; write(book,data)
    chapter('welcome',('第一天怎么开始','Your first day'),'teamecon:guide_book','welcome',
            ('一本书＋一块铁锭，也能合成这本指南。','You can also craft this guide with a book and an iron ingot.'),[
        ('首次进入获赠指南，安装帕秋莉手册后右键阅读。全新个人开局领取 200 点，已有钱包不补发，换队不会重复领取。$(br2)还没造商店？输入 $(thing)/teamecon shop$() 即可打开商店。保留工具和食物，将余料放入「出售」页，核对报价后出售。',
         'Your first guide is free; install Patchouli to read it. A new personal start grants 200 points once. Existing wallets and team changes do not grant it again.$(br2)Use $(thing)/teamecon shop$() to open the shop. Keep tools and food; deposit spare materials in Sell, check the quote, then sell.'),
        ('Lv.2 升级费为 1,200 点；史莱姆机器另需 1,500 点，也可按本书配方合成。全部用点数购买，至少先攒 2,700 点，并另留游玩预算。$(br2)小游戏会损失点数，升级不要求中奖。',
         'Lv.2 costs 1,200 points. A Slime Hop machine costs another 1,500, or you can craft it. Budget at least 2,700 to buy both, plus a small stake.$(br2)Games can lose points. Winning is never required to upgrade.'),
        ('奖励倍数包含本金：下注 100，奖励 ×2，入账 200，净赚 100。没有中奖则入账 0。$(br2)书中列出默认规则；实际金额、权限与上限以当前服务器界面为准。',
         'Payouts include the stake. Bet 100 at ×2: receive 200, a net gain of 100. A loss pays zero.$(br2)This guide lists defaults. Your server’s prices, permissions and limits appear in its interfaces.')],order=0)
    chapter('hilo',('猜大小','High / Low'),'teamecon:hilo_table','hilo',
            ('小：1–5；大：6–10。','Low: 1–5. High: 6–10.'),[
        ('选择小（1–5）或大（6–10），设置金额，再点击开始。每局独立抽出 1–10 中的一个整数。$(br2)猜中默认奖励 ×1.8；猜错没有奖励。上一局结果不影响下一局。',
         'Choose Low (1–5) or High (6–10), set a stake, then Start. Each round independently draws one integer from 1–10.$(br2)A correct guess pays ×1.8 by default; a miss pays zero. Previous results do not affect the next round.'),
        ('默认 Lv.1 开放，初始单注上限 2,000 点，随钱包等级成长。建议从小额体验；点击金额倍数只会改金额，不会自动开始。',
         'Available at Lv.1, with an initial stake limit of 2,000 points that grows with wallet level. Start small. Applying a stake factor changes the amount; it does not start a round.')],category='machines',order=0)
    chapter('economy',('商店与批量回收','Shop and bulk recycling'),'teamecon:shop_machine','economy',
            ('把多余物资转化为点数。','Turn spare supplies into points.'),[
        ('商店分为物品、出售、机器、刮卡、升级五页。按类型图标筛选，输入名称、ID 或 #标签搜索，滚轮或箭头翻页。$(br2)购买后直接进入背包。背包放不下时不会扣费。',
         'Five shop tabs: Items, Sell, Machines, Tickets and Levels. Filter by type, search names, IDs or #tags, and scroll or use arrows to browse.$(br2)Purchases go into your inventory. Insufficient space prevents charging.'),
        ('出售页有 27 格待售区，Shift 点击快速放入。核对总报价后点「出售全部」。关闭时，未售物品退回。$(br2)相同材料共享需求，反复出售会降价。拆成小堆或压成方块不会增加收入，小数结余会保留。',
         'The Sell tab holds 27 stacks. Shift-click to deposit, review the total and sell all. Unsold items return on closing.$(br2)Related materials share demand decay. Splitting stacks or compressing blocks does not increase income. Fractional proceeds carry forward.'),
        ('只交易原版物品及专用目录中的本模组设备。附魔以实体附魔书交付。$(br2)关键战利品保留探索获取，其他高阶商品需要进度条件。购买价高于回收价，适合补缺和建造，不适合倒卖。',
         'Trading supports vanilla goods and this mod’s dedicated equipment catalog. Enchants arrive as books.$(br2)Key loot remains exploration-only; other advanced goods require progress. Retail costs exceed resale values: use the shop for supplies and building.')],order=1)
    chapter('boxes',('盲盒与珍稀奖品','Boxes and rare prizes'),'teamecon:blind_box_machine','economy',
            ('独立盲盒机：先预览，再开启。','Use the dedicated box machine: preview, then open.'),[
        ('物资盲盒 64 点，珍藏盲盒 512 点。选中盲盒后，「奖池预览」显示可开出的物品和数量；每盒只随机获得其中一项。$(br2)一次开 1、10 或 64 盒。开启后切换到「本次奖品」，查看已经放入背包的实际奖励。',
         'Supply Boxes cost 64 points; Treasure Boxes cost 512. Contents shows possible items and quantities. Each box awards one random entry.$(br2)Open 1, 10 or 64 at once. Last opening shows the actual prizes delivered to your inventory.'),
        ('默认奖池没有空盒、腐肉等凑数奖品，基础奖品侧重实用物资。普通奖项按默认商店售价衡量，不代表能回收相同点数。$(br2)珍藏盲盒有极小机会得到牛或羊刷怪蛋。这两种蛋无法在商店购买，也不能回收。',
         'Default pools contain useful supplies, with no empty boxes or filler rotten flesh. Ordinary rewards are valued against default retail prices, not resale proceeds.$(br2)Treasure Boxes can very rarely award cow or sheep spawn eggs. These cannot be bought or sold.'),
        ('盲盒没有进度或等级门槛。批量开启前须为所有可能奖品留足空间；空间不足不会扣费。$(br2)服主可修改奖池，界面预览以当前服务器为准。',
         'Boxes have no advancement or level gate. Leave space for all possible outcomes of the selected batch; insufficient space prevents charging.$(br2)Server owners can customize pools. The preview reflects the current server.')],order=4)
    chapter('food',('食物与升级预算','Food and your budget'),'minecraft:bread','economy',
            ('保留自己的食物来源，商店用来应急。','Keep a food supply; shop for emergencies.'),[
        ('默认单个购买底价：土豆 8，生肉 16，面包／烤土豆 24，牛排／熟猪排 32 点。实际价格还受服务器配置影响。$(br2)起始 200 点只够买 6 块牛排。种植、养殖和烹饪仍然划算。',
         'Default retail floors per item: potato 8, raw meat 16, bread/baked potato 24, steak/cooked porkchop 32. Server settings can raise these.$(br2)Your initial 200 points buys only six steaks. Farming, breeding and cooking remain useful.'),
        ('优先卖多余材料，不必卖掉做工具和升级装备所需的资源。不同材料搭配出售，通常比反复卖一种材料更合适。$(br2)机器价格、升级费用和游玩金额是三笔开销，买机器前先留好预算。',
         'Sell spare materials; keep what you need for tools and gear. Selling varied resources usually works better than repeatedly selling one material.$(br2)Machine purchases, upgrades and stakes are separate expenses. Budget for all three.')],order=5)
    chapter('teams',('队伍钱包','Team wallets'),'minecraft:emerald','levels',
            ('和队友一起积攒点数。','Save points together with teammates.'),[
        ('安装 FTB Teams 后，队员共享钱包和设备等级；未安装时各自独立。退队恢复原个人等级。$(br2)右侧小栏显示队名、余额和成员净赚；灰色名字表示离线，人多时自动翻页。',
         'With FTB Teams, members share a wallet and machine level. Without it, wallets are personal. Leaving restores your previous personal level.$(br2)The small right-hand board shows the team, balance and member earnings. Grey names are offline; larger teams rotate pages.'),
        ('成员净赚 = 回收收入 + 游戏奖励 − 下注。购物消费不计入，所以成员数值相加不等于当前余额。$(br2)商品的冒险进度仍按操作玩家检查，共享钱包不等于共享所有进度。',
         'Member earnings = recycling income + game payouts − stakes. Shopping is excluded, so these values do not sum to the current balance.$(br2)Purchase advancements still belong to the player using the shop; sharing a wallet does not share all progress.')],order=6)
    chapter('terminal',('无线终端','Wireless terminal'),'teamecon:terminal','controls',
            ('后期把常用功能带在身上。','Carry the main services into the late game.'),[
        ('默认需要 Lv.5、完成「解放末地」，并花费 100,000 点兑换。终端不能合成。$(br2)右键打开首页，可进入商店、回收、升级和远程小游戏。盲盒仍需使用独立盲盒机。',
         'Requires Lv.5, Free the End, and a 100,000-point purchase by default. The terminal has no crafting recipe.$(br2)Right-click for shopping, recycling, upgrades and remote games. Boxes remain exclusive to their dedicated machine.'),
        ('终端转交他人后，使用者仍需满足权限。远程小游戏同样扣除钱包点数。$(br2)倍率竞猜关闭界面不会暂停；若想拿回当前奖励，需在崩盘前主动提取。',
         'Passing a terminal to another player does not bypass their requirements. Remote games also spend wallet points.$(br2)Closing Crash does not pause it. Cash out before the crash to collect the current payout.')],order=7)
    chapter('help',('常见问题','Common questions'),'teamecon:guide_book','welcome',
            ('卡住时，从这里找答案。','Quick answers when you get stuck.'),[
        ('打不开指南？确认安装了适用于 1.21.1 NeoForge 的 Patchouli。物品买不了？看商品详情中的条件。盲盒不能开？检查余额与背包空位。$(br2)升级后机器仍需单独获取；拿到机器也不代表已经解锁使用权限。',
         'Guide will not open? Install Patchouli for 1.21.1 NeoForge. Cannot buy an item? Check its listed requirements. Box will not open? Check funds and inventory space.$(br2)Upgrades and machine purchases are separate. Owning a machine does not bypass its level requirement.'),
        ('回收比上次便宜？同类材料需求在下降，换其他材料或等待恢复。切换语言后，本书和界面会使用对应翻译；未提供的语言回退为英文。$(br2)报告问题时请附游戏／模组版本、复现步骤和相关日志。',
         'Lower resale quote? Demand for that material has fallen; sell something else or allow it to recover. Interfaces and this guide follow your language, falling back to English where a translation is unavailable.$(br2)For bug reports include versions, steps and relevant logs.')],order=8)
    chapter('credits',('制作与声明','Credits and notice'),'teamecon:guide_book','welcome',
            ('Team Economy · 制作：洁柔厨','Team Economy · Created by 洁柔厨'),[
        ('制作与玩法设计：洁柔厨。代码与素材制作使用了 AI 辅助工具。$(br2)本模组是非官方 Minecraft 项目，与 Mojang 或 Microsoft 无隶属关系。原版素材及依赖归各权利人所有。',
         'Created and designed by 洁柔厨, with AI-assisted code and asset production.$(br2)An unofficial Minecraft project, not affiliated with Mojang or Microsoft. Minecraft resources and dependencies belong to their respective owners.'),
        ('所有点数仅用于游戏内玩法，没有现实货币价值。本项目保留权利，具体使用范围见发布包 LICENSE。$(br2)感谢游玩！欢迎反馈有趣的玩法片段、平衡体验和问题。',
         'Points are only for in-game play and have no real-money value. Rights are reserved; see the release LICENSE for permitted uses.$(br2)Thanks for playing. Share gameplay clips, balance feedback and reproducible bug reports.')],order=9)
    # Remove revision notes and internal payment mechanics from the remaining player pages.
    for i, lang in enumerate(('zh_cn','en_us')):
        p=BOOK/lang/'categories/tickets.json';d=json.loads(p.read_text(encoding='utf-8'))
        d['description']=('八种卡片的操作与中奖规则。','How to play all eight ticket designs.')[i];write(p,d)
        p=BOOK/lang/'entries/penguin.json';d=json.loads(p.read_text(encoding='utf-8'))
        d['pages'][1]['text']=d['pages'][1]['text'].replace('即使最后一跳被上限截短，也不会变容易。','').replace(', even at a shortened final step','')
        write(p,d)
        p=BOOK/lang/'entries/roulette.json';d=json.loads(p.read_text(encoding='utf-8'))
        d['pages'][1]['text']=d['pages'][1]['text'].replace('轮盘台面已降低，站在前面可看到轮面。','站在操作台前选择下注。').replace('The lowered tabletop is visible while standing in front. ','Select a bet from the controls in front. ');write(p,d)
    # Keep acquisition instructions alongside each activity, with real recipes rendered by Patchouli.
    crafting = {
        'welcome': 'guide_book', 'economy': 'shop_machine', 'boxes': 'blind_box_machine',
        'hilo': 'hilo_table', 'penguin': 'penguin_machine', 'color_wheel': 'color_wheel_table',
        'roulette': 'roulette_table', 'slots': 'slot_machine', 'multiplier': 'multiplier_machine',
    }
    for lang in ('zh_cn', 'en_us'):
        for entry_id, recipe in crafting.items():
            path = BOOK/lang/'entries'/f'{entry_id}.json'
            data = json.loads(path.read_text(encoding='utf-8'))
            data['pages'] = [p for p in data['pages'] if p['type'] != 'patchouli:crafting']
            data['pages'].append({'type': 'patchouli:crafting', 'recipe': 'teamecon:'+recipe,
                'text': ('合成后右键使用。游戏机仍需对应钱包等级。' if entry_id not in ('welcome','economy','boxes')
                         else '也可以在生存模式按此配方合成。') if lang == 'zh_cn' else
                        ('Craft, then right-click to use. Machines still require the matching wallet level.'
                         if entry_id not in ('welcome','economy','boxes') else 'You can craft this item in survival using this recipe.')})
            write(path, data)
    from gen_gameplay_revision import update
    update(ASSETS, BOOK, write)
    # Traditional Chinese is shipped, rather than pretending all languages are translated.
    cc=OpenCC('s2twp')
    def convert(value):
        if isinstance(value,str): return cc.convert(value).replace('潔柔廚','洁柔厨')
        if isinstance(value,list): return [convert(v) for v in value]
        if isinstance(value,dict): return {k:convert(v) for k,v in value.items()}
        return value
    write(ASSETS/'lang/zh_tw.json',convert(json.loads((ASSETS/'lang/zh_cn.json').read_text(encoding='utf-8'))))
    for path in (BOOK/'zh_cn').rglob('*.json'):
        write(BOOK/'zh_tw'/path.relative_to(BOOK/'zh_cn'),convert(json.loads(path.read_text(encoding='utf-8'))))

if __name__=='__main__': main()
