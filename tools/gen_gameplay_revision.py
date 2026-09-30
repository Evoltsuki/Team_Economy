"""Final gameplay guide corrections; invoked by gen_release_assets before zh-TW conversion."""
import json


def update(assets, book, write):
    texts = {
        'container.teamecon.hilo': ('猜大小机', 'High / Low Machine'),
        'command.teamecon.admin_status': ('钱包等级 %s；余额 %s；团队经济进度限制豁免：%s。', 'Wallet level %s; balance %s; Team Economy advancement-gate bypass: %s.'),
        'gui.teamecon.boxes.chance': ('概率：%s', 'Chance: %s'),
        'block.teamecon.hilo_table': ('猜大小机', 'High / Low Machine'),
        'command.teamecon.admin_progress': ('钱包等级：%s；团队经济进度限制豁免：%s。原版进度不变。', 'Wallet level: %s; Team Economy advancement-gate bypass: %s. Vanilla advancements unchanged.'),
        'command.teamecon.admin_machine': ('已给予八种机器。', 'Granted all eight machines.'),
        'book.teamecon.landing': ('回收物资、升级设备，与队友一起游玩。$(br2)制作：洁柔厨$(br)Team Economy 1.0', 'Recycle, upgrade and play together.$(br2)Created by 洁柔厨$(br)Team Economy 1.0'),
    }
    odds = {
        'hilo': ('每局独立等概率抽取 1–10。小为 1–5，大为 6–10，各有 50% 机会猜中。猜中默认 ×1.8，猜错无奖励。', 'Each round draws 1–10 uniformly. Low (1–5) and High (6–10) each win 50%. A win pays ×1.8; a miss pays nothing.'),
        'roulette': ('37 个格子等概率：红 18 格、黑 18 格、绿 0 一格。红或黑胜率各为 18/37（约 48.65%），绿色为 1/37（约 2.70%）。$(br2)默认红黑奖励 ×2，绿色 ×32；绿色不是红或黑。', 'All 37 pockets are equally likely: 18 red, 18 black and one green zero. Red/black each win 18/37 (48.65%); green wins 1/37 (2.70%).$(br2)Default payouts: red/black ×2; green ×32. Zero is neither red nor black.'),
        'slots': ('每个转轴独立抽取：樱桃 25%、柠檬 25%、橙子 20%、铃铛 15%、星星 10%、钻石 5%。$(br2)任意位置两个相同图案均奖励 ×1.2。三个相同时按三连奖励，仅支付最高一项。', 'Each reel draws independently: cherry 25%, lemon 25%, orange 20%, bell 15%, star 10%, diamond 5%.$(br2)Any matching pair pays ×1.2. Triples use the higher triple prize; prizes do not stack.'),
    }
    for i, lang in enumerate(('zh_cn', 'en_us')):
        path = assets/'lang'/f'{lang}.json'
        data = json.loads(path.read_text(encoding='utf-8'))
        data.update({key: value[i] for key, value in texts.items()})
        for key, value in data.items():
            if isinstance(value, str): data[key] = value.replace('猜大小桌', '猜大小机')
        write(path, data)
        for name, pair in odds.items():
            path = book/lang/'entries'/f'{name}.json'
            data = json.loads(path.read_text(encoding='utf-8'))
            pages = [p for p in data['pages'] if p['type'] != 'patchouli:crafting']
            if name == 'slots':
                pages[0]['text'] = ('任意对子 ×1.2；三钻石 ×200。', 'Any pair ×1.2; triple diamonds ×200.')[i]
                pages = [pages[0], {'type':'patchouli:text','title':('操作与概率','Play and odds')[i], 'text':pair[i]},
                    {'type':'patchouli:text','title':('中奖组合','Winning combinations')[i], 'text':(
                        '三连奖励：$(br)樱桃 ×3；柠檬 ×4；橙子 ×7$(br)铃铛 ×16；星星 ×40；钻石 ×200$(br2)默认中奖概率合计 51.25%。三钻石概率 0.0125%（约八千分之一）。每局独立，连输不会提高下次概率。',
                        'Triples:$(br)Cherry ×3; lemon ×4; orange ×7$(br)Bell ×16; star ×40; diamond ×200$(br2)Total winning chance: 51.25%. Triple diamonds: 0.0125% (1/8,000). Spins are independent.')[i]},
                    {'type':'patchouli:text','title':('使用条件','Requirements')[i], 'text':('默认 Lv.5，初始单注上限 200,000 点。设置下注后点击开始，三个转轴停下后自动结算。倍数包含本金。服务器自定义配置以实际提示为准。', 'Default Lv.5; initial stake limit 200,000. Set a stake and start; settlement follows the final reel. Payouts include the stake. Custom server rules may differ.')[i]}]
            else:
                pages.insert(2, {'type':'patchouli:text','title':('概率规则','Odds')[i], 'text':pair[i]})
                # Repeated regeneration must not duplicate the odds page.
                pages = [p for j,p in enumerate(pages) if j == 2 or p.get('title') not in ('概率规则','Odds')]
            data['pages'] = pages + [p for p in data['pages'] if p['type'] == 'patchouli:crafting']
            if name == 'hilo': data['name'] = ('猜大小机', 'High / Low Machine')[i]
            write(path, data)
        path = book/lang/'entries/boxes.json'; data = json.loads(path.read_text(encoding='utf-8'))
        data['pages'][2]['text'] = (
            '物资盒有 11 种奖项，钻石两颗概率 1.8%，下界合金锭概率 0.2%。$(br2)珍藏盒包含更多矿物、16 颗钻石（0.5%）、附魔金苹果（0.5%）和下界合金锭（1%）。友好生物刷怪蛋合计 10%，包含村民、悦灵、嗅探兽及动物等；部分中立生物受攻击会反击。',
            'Supply: 11 entries, including two diamonds (1.8%) and a netherite ingot (0.2%).$(br2)Treasure includes 16 diamonds (0.5%), an enchanted golden apple (0.5%) and a netherite ingot (1%). Friendly mob eggs total 10%, including villagers, allays, sniffers and animals. Neutral mobs can retaliate.')[i]
        data['pages'].insert(3, {'type':'patchouli:text','title':('抽取说明','Drawing prizes')[i], 'text':(
            '每项概率 = 该项权重 ÷ 奖池总权重。每盒独立抽一次，批量开启不会提高单盒中奖概率。无保底机制。$(br2)刷怪蛋只作盲盒奖品，不能购买或回收。其他奖励可有很高单次价值，但并不保证每盒都赚。',
            'Entry chance = entry weight / total weight. Each box draws independently; bulk opening does not improve individual odds. There is no pity guarantee.$(br2)Eggs are prize-only and cannot be traded. Valuable outcomes do not guarantee a profit on every box.')[i]})
        write(path, data)
        path = book/lang/'entries/teams.json'; data = json.loads(path.read_text(encoding='utf-8'))
        data['pages'][2]['text'] = ('成员净收支包括回收、游戏奖励、下注、购物、升级与盲盒扣款。支出会让榜单数字下降，也可能为负。$(br2)管理员直接设置余额不算经营收入；历史退款在旧档中可能缺少完整明细。进度权限仍按操作玩家检查。', 'Member net income includes recycling, game prizes, stakes, shopping, upgrades and boxes. Spending lowers the number, which may be negative.$(br2)Admin balance overrides are excluded. Old saves may lack full refund details. Advancement checks still apply to the acting player.')[i]
        write(path, data)
    complete_machine_pages(book, write)
    # Patchouli renders line breaks with markup; raw newlines can appear as LF glyphs.
    for path in book.rglob('*.json'):
        data = json.loads(path.read_text(encoding='utf-8'))
        for page in data.get('pages', []):
            if 'text' in page: page['text'] = page['text'].replace('\r\n','$(br)').replace('\n','$(br)').replace('猜大小桌','猜大小机')
        write(path, data)


def complete_machine_pages(book, write):
    """Short, titled pages with event odds, without aggregate financial statistics."""
    rules = {
        'hilo': [
            ('选择小（1–5）或大（6–10），用减／加按钮设置本金，再点击开始。数字停下后自动结算。', 'Choose Low (1–5) or High (6–10), adjust your stake with the buttons, then Start. Settlement is automatic when the number stops.'),
            ('每局独立等概率抽取 1–10，各数字概率 10%。大小各占 5 个数字，猜中概率均为 50%。$(br2)猜中默认 ×1.8，猜错 ×0。连续猜错不会提高下一局概率。', 'Each round independently draws 1–10; each number has a 10% chance. High and Low each win 50%.$(br2)Correct: ×1.8. Wrong: ×0. Losing streaks do not improve the next chance.'),
        ],
        'roulette': [
            ('站在低台前，选择红、黑或绿 0；也可右键轮面数字押单号。设置本金后点击开始，滚珠停稳即结算。$(br2)终端还可选奇偶、1–18 或 19–36。绿色 0 不属于任何外围下注。', 'Choose red, black or green zero at the low controls, or right-click a numbered pocket. Set your stake and Start. The settled ball determines the result.$(br2)The terminal also offers odd/even and 1–18 / 19–36. Zero loses all outside bets.'),
            ('37 格等概率：18 红、18 黑、一个绿 0。$(br2)红黑／奇偶／大小区间：18/37，约 48.65%，默认 ×2。$(br2)任一单号（含绿 0）：1/37，约 2.70%，默认 ×32。', '37 equally likely pockets: 18 red, 18 black and one green zero.$(br2)Outside bets: 18/37 (about 48.65%), paying ×2.$(br2)Any straight number, including zero: 1/37 (about 2.70%), paying ×32.'),
        ],
        'color_wheel': [
            ('设置本金后点击开始，不需要猜颜色。转盘旋转后逐渐停下，顶部固定指针所指色块决定奖励。$(br2)每种颜色可分布于多个扇区；同色概率按全部同色格子合计。', 'Set a stake and Start; no colour prediction is needed. The wheel slows to a stop beneath the fixed top pointer.$(br2)A colour can occupy several sectors. Its probability combines all pockets of that colour.'),
            ('40 个等概率格子：$(br)灰：23 格，57.5%，×0$(br)绿：9 格，22.5%，×1$(br)蓝：5 格，12.5%，×2$(br)紫：2 格，5%，×4$(br)金：1 格，2.5%，×10$(br2)绿色仅返还本金。每次旋转独立，无保底。', '40 equally likely pockets:$(br)Grey: 23, 57.5%, ×0$(br)Green: 9, 22.5%, ×1$(br)Blue: 5, 12.5%, ×2$(br)Purple: 2, 5%, ×4$(br)Gold: 1, 2.5%, ×10$(br2)Green returns the stake. Spins are independent; there is no pity system.'),
        ],
        'penguin': [
            ('一局只扣一次本金。点击开始跳一层，落稳后可继续或提取；踏空后冰块碎裂，整局归零。$(br2)第 1–10 层总倍率：$(br)1.2、1.5、2、2.75、4、6、10、16、25、40。', 'One stake funds the run. Start jumps one layer; after landing, continue or cash out. A fall breaks the ice and ends the entire run at zero.$(br2)Total multipliers, layers 1–10:$(br)1.2, 1.5, 2, 2.75, 4, 6, 10, 16, 25, 40.'),
            ('', ''),  # Populated from the layer/risk formula below.
        ],
        'multiplier': [
            ('下注后从 ×1 持续上涨，越往后增长越快。崩盘前点击提取，按服务器收到操作时的倍率结算。$(br2)崩盘后本金及未提取收益全部归零。走势图只显示已经发生的变化，不能预测结果。', 'The multiplier rises continuously from ×1 and accelerates. Cash out before the crash; the server uses the multiplier when your action arrives.$(br2)A crash loses the stake and all unclaimed winnings. The chart shows past movement and cannot predict the result.'),
            ('默认约 20.32% 的开局立即崩盘。存活到 ×2 约 39.8%，×10 约 7.97%，×100 约 0.159%。$(br2)高倍率越难遇到；这些是从开局到目标倍率的概率，实际按服务器游戏刻结算。每局独立，没有安全提取时刻或保底。', 'About 20.32% of default rounds crash instantly. Survival from the start: about 39.8% to ×2, 7.97% to ×10 and 0.159% to ×100.$(br2)Higher values are rarer. Settlement uses server ticks. Rounds are independent; there is no guaranteed safe cash-out time.'),
        ],
    }
    # Calculate the jump table from the same documented layer/risk rule as PenguinGame.
    layers = [1, 1.2, 1.5, 2, 2.75, 4, 6, 10, 16, 25, 40]
    chances = [f'{.96 / max(layers[n+1]/layers[n], 1.2+.08*n)*100:.3f}'.rstrip('0').rstrip('.')+'%' for n in range(10)]
    rules['penguin'][1] = (
        '到达第 1–5 层的单步成功概率：$(br)'+'、'.join(chances[:5])+'。$(br2)第 6–10 层：$(br)'+'、'.join(chances[5:])+'。$(br2)这是已到上一层后的单步概率，并非从开局连跳到该层的概率。',
        'Single-jump chances to layers 1–5:$(br)'+', '.join(chances[:5])+'.$(br2)Layers 6–10:$(br)'+', '.join(chances[5:])+'.$(br2)These are conditional step chances after reaching the previous layer, not full-run probabilities.')
    requirements = {'hilo':(1,'2,000'),'penguin':(2,'10,000'),'color_wheel':(3,'50,000'),'roulette':(4,'100,000'),'slots':(5,'200,000'),'multiplier':(5,'200,000')}
    for i, lang in enumerate(('zh_cn','en_us')):
        for name, (level, cap) in requirements.items():
            path=book/lang/'entries'/f'{name}.json'; data=json.loads(path.read_text(encoding='utf-8'))
            crafting=[p for p in data['pages'] if p['type']=='patchouli:crafting']
            if name in rules:
                data['pages']=[data['pages'][0]]+[{'type':'patchouli:text','title':title[i],'text':pair[i]} for title,pair in zip([('操作与结算','How to play'),('事件概率','Event probabilities')],rules[name])]
            else:
                data['pages']=[p for p in data['pages'] if p['type']!='patchouli:crafting' and p.get('title') not in ('使用条件','Requirements')]
            extra = ('最高 ×40，到达后提取。离开机器、断线或卸载区块会自动结算已完成的跳跃。','At ×40, cash out. Leaving, disconnecting or unloading settles completed jumps.') if name=='penguin' else ('关闭界面、离开机器或断线不会暂停或自动提取。存活到 ×1,000 自动提取。','Closing, leaving or disconnecting does not pause or cash out. Surviving to ×1,000 automatically cashes out.') if name=='multiplier' else ('结果确定后自动结算一次。','The result settles automatically once.')
            body=(f'默认 Lv.{level}，初始单注上限 {cap} 点。钱包升级可提高上限。$(br2){extra[0]}$(br2)倍率包含本金。服主可修改规则，以当前界面为准。',f'Default Lv.{level}; initial stake cap {cap}. Wallet upgrades can raise it.$(br2){extra[1]}$(br2)Payouts include the stake. Server rules may differ; check the live interface.')[i]
            data['pages'].append({'type':'patchouli:text','title':('使用条件','Requirements')[i],'text':body})
            data['pages']+=crafting;write(path,data)
