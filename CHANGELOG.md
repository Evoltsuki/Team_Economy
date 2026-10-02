# 更新日志 / Changelog

## 1.0.1 — 待发布 / Unreleased

以下为相较 **1.0.0** 的变化。

### 新增

- 新增物品定价面板 `/teamecon admin prices`，支持按分类浏览及名称、物品 ID、拼音搜索，分别设置购买价与回收价，并可为第三方模组物品启用回收。
- 新增盲盒管理面板 `/teamecon admin boxes`，支持自定义箱种、名称、价格和奖池；可从背包拖放奖品，设置数量及中奖百分比。
- 将任务奖励功能合并至主模组，提供 `/teamecon reward`、`/teamecon_quest_reward` 命令及 Java API，无需单独安装任务奖励附属模组。

### 改进

- 自动售货机与 `/teamecon shop` 记忆每位玩家上次打开的页签，重启游戏后自动恢复。
- 商店新增中文全拼与拼音首字母搜索，支持简体、繁体及 `lv` / `lü` 输入。
- 盲盒奖池预览新增中奖概率，奖品数量改用「×N」标注。
- 默认盲盒奖池新增多种普通、喷溅和滞留药水。

### 修复

- 修复批量开启盲盒因背包容量不足而受阻的问题。超出背包容量的奖品保存为待领取，可通过盲盒机或 `/teamecon claimboxes` 领取。
- 修正金属块、宝石块、红石块、煤炭块、青金石块和干草块的基础估价，移除可逆压缩配方的额外合成加价。默认金锭基础价为 64 点，金块为 576 点。

### English

Changes since **1.0.0**.

#### Added

- Added `/teamecon admin prices`, an item pricing panel with category browsing and name, item ID and pinyin search. Purchase and recycling prices can be set independently, including recycling prices for modded items.
- Added `/teamecon admin boxes` to manage box types, names, prices and prize pools. Prizes can be dragged from the inventory and assigned quantities and percentage chances.
- Integrated quest rewards into the main mod, with `/teamecon reward`, `/teamecon_quest_reward` and a Java API. The separate quest reward add-on is no longer required.

#### Changed

- Vending Machines and `/teamecon shop` remember each player's last tab across game restarts.
- Added full pinyin and initials search for Chinese item names, including simplified/traditional text and `lv` / `lü` input.
- Mystery box previews now display prize probabilities and mark item quantities with ×N.
- Added normal, splash and lingering potions to the default mystery box pools.

#### Fixed

- Fixed batch mystery box openings being blocked by insufficient inventory space. Overflow prizes are saved for collection through the box machine or `/teamecon claimboxes`.
- Corrected base values for metal and gem storage blocks, redstone blocks, coal blocks, lapis blocks and hay bales by removing the reversible crafting markup. Default base values are 64 points for a gold ingot and 576 for a gold block.
