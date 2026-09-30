"""Storefront and casino progression translations; also run by the asset generator."""
from pathlib import Path
import json

PAIRS = {
    'gui.teamecon.shop.title': ('积分商店', 'POINTS EXCHANGE'),
    'gui.teamecon.shop.tab.machines': ('机器', 'Machines'),
    'gui.teamecon.shop.tab.cards': ('刮卡', 'Tickets'),
    'gui.teamecon.shop.tab.levels': ('升级', 'Levels'),
    'gui.teamecon.shop.available': ('已解锁', 'Available'),
    'gui.teamecon.shop.wallet': ('钱包 · %s', 'Wallet · %s'),
    'gui.teamecon.shop.points': ('可用积分', 'Available points'),
    'gui.teamecon.shop.price': ('%s 积分', '%s points'),
    'gui.teamecon.shop.total': ('合计 %s 积分', 'Total %s points'),
    'gui.teamecon.shop.quantity': ('购买数量', 'Quantity'),
    'gui.teamecon.shop.delivery': ('购买后直接放入背包', 'Delivered to your inventory'),
    'gui.teamecon.shop.footer': ('选择商品查看详情 · 悬停查看解锁条件', 'Select a product · Hover for unlock conditions'),
    'gui.teamecon.shop.machine_level': ('设备等级 Lv.%s', 'Machine level %s'),
    'gui.teamecon.shop.return': ('返奖率 %s', 'Return %s'),
    'gui.teamecon.shop.return_compact': ('返奖 %s', 'RTP %s'),
    'gui.teamecon.shop.bet_limit': ('单注上限 %s', 'Max stake %s'),
    'gui.teamecon.shop.maximum': ('最高 ×%s', 'Up to ×%s'),
    'gui.teamecon.shop.return_hint': ('返奖含本金；理论值未计整数取整。闯关为每步返奖率。', 'Returns include stake, before rounding. Run games quote return per step.'),
    'gui.teamecon.shop.terminal_detail': ('后期兑换道具；购买与使用均需终端等级和进度条件。', 'Late-game exchange item; purchase and use require its level and advancement.'),
    'gui.teamecon.shop.upgrade': ('购买等级', 'Buy level'),
    'gui.teamecon.shop.owned': ('已解锁', 'Unlocked'),
    'gui.teamecon.shop.level_name': ('Lv.%s · %s', 'Lv.%s · %s'),
    'gui.teamecon.shop.level.1': ('入门', 'Starter'),
    'gui.teamecon.shop.level.2': ('进阶', 'Intermediate'),
    'gui.teamecon.shop.level.3': ('熟练', 'Skilled'),
    'gui.teamecon.shop.level.4': ('专家', 'Expert'),
    'gui.teamecon.shop.level.5': ('大师', 'Master'),
    'gui.teamecon.shop.shared_levels': ('按级购买 · 等级归属当前钱包', 'Buy in order · Levels belong to this wallet'),
    'gui.teamecon.shop.card_unlocks': ('另解锁 %s 种刮卡', 'Plus %s ticket types'),
    'gui.teamecon.shop.wallet_exact': ('钱包：%s\n余额：%s 积分\n等级：Lv.%s', 'Wallet: %s\nBalance: %s points\nLevel: %s'),
    'casino.teamecon.requires_level': ('需先在商店购买至 Lv.%s', 'Buy level %s in the shop first'),
    'message.teamecon.requires_level': ('需先在商店购买至 Lv.%s', 'Buy level %s in the shop first'),
    'casino.teamecon.card_locked': ('该档刮卡尚未解锁，请先购买对应等级', 'Buy the required level before purchasing this ticket'),
    'casino.teamecon.upgrade_changed': ('等级已变化，请选择下一级；未重复扣费', 'Level changed. Select the next level; no duplicate charge'),
    'casino.teamecon.upgraded': ('当前钱包已升级至 Lv.%s', 'This wallet is now level %s'),
    'shop.teamecon.lock.casino_config': ('等级配置有误，暂不可开始游戏或升级', 'Level config needs repair; new games and upgrades are locked'),
    'shop.teamecon.lock.game_disabled': ('该玩法已被服务器停用', 'This game is disabled by the server'),
    'shop.teamecon.lock.terminal_exchange': ('无线终端需在机器页单独用积分兑换', 'Exchange points for the terminal on the Machines page'),
    'machine.teamecon.level_info': ('Lv.%s · 返奖率 %s · 单注上限 %s', 'Lv.%s · Return %s · Max stake %s'),
    'machine.teamecon.level_locked': ('当前 Lv.%s；需先购买 Lv.%s', 'Current level %s; buy level %s first'),
    'machine.teamecon.color_odds_one': ('灰 ×0：57.5% · 绿 ×%s：22.5% · 蓝 ×%s：12.5%', 'Grey ×0: 57.5% · Green ×%s: 22.5% · Blue ×%s: 12.5%'),
    'machine.teamecon.color_odds_two': ('紫 ×%s：5% · 金 ×%s：2.5%（含本金）', 'Purple ×%s: 5% · Gold ×%s: 2.5% (stake included)'),
    'gui.teamecon.odds.roulette.outside': ('红黑/单双/大小：18/37 · ×%s', 'Outside: 18/37 · ×%s'),
    'gui.teamecon.odds.roulette.straight': ('单号：1/37 · ×%s', 'Straight: 1/37 · ×%s'),
    'gui.teamecon.odds.roulette.return': ('理论返奖率 %s', 'Theoretical return %s'),
    'item.teamecon.terminal.tooltip': ('后期积分兑换道具；需满足终端等级与进度，支持无线购卡和回收', 'Late-game points exchange; requires terminal level and advancement. Wireless tickets and recycling'),
    'scratch.teamecon.blank': ('空白样品；有效刮刮卡请在商店或无线终端购买', 'Blank sample. Buy valid tickets in the shop or wireless terminal'),
    'command.teamecon.admin_machine': ('已给予七台机器；终端需在商店兑换', 'Granted seven machines; exchange points for the terminal in the shop'),
}

def main():
    folder = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/teamecon/lang'
    for index, language in enumerate(('zh_cn', 'en_us')):
        path = folder / (language + '.json')
        data = json.loads(path.read_text(encoding='utf-8'))
        data.update({key: value[index] for key, value in PAIRS.items()})
        path.write_text(json.dumps(dict(sorted(data.items())), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
