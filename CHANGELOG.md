# 更新日志 / Changelog

## 1.0.1 — 待发布 / Unreleased

- 自动售货机与 `/teamecon shop` 自动恢复每位玩家上次使用的页签，偏好保存在本机客户端，重启游戏后仍保留；首次使用打开「商品」页。盲盒机与无线终端的指定页面入口保持各自用途。
- 商店支持中文名称的全拼与拼音首字母搜索。例如「金锭」可输入 `jinding` 或 `jd`，同时支持简体、繁体以及 `lv` / `lü` 输入。名称、物品 ID 与 `#标签` 搜索继续可用。
- 盲盒奖品格右上角显示概率，下方以「×数量」标注奖品数量；默认奖池增加普通、喷溅及滞留药水，并保留具体效果。
- 新增 `/teamecon admin boxes` 管理面板，支持新增/编辑/删除箱种、设置名称和价格、搜索奖品与药水、调整数量及权重。
- 批量开启按实际结果发放，背包溢出奖品持久保存为待领取；可从盲盒机或 `/teamecon claimboxes` 领取，领完前不再开启新批次。
- 金属块、宝石块、红石块、煤炭块、青金石块与干草块按基础材料数量估价，不叠加可逆压缩的合成加价。默认金锭为 64 点，金块基础估价为 576 点；实际回收收入仍受市场需求影响。服主明确设置的方块基础价格优先。

- 新增 `/teamecon admin prices` 创造物品栏式定价面板，支持中文、物品 ID、全拼和首字母搜索；分别设置购买与回收价格，保存生效并可恢复默认。支持显式开启第三方普通物品回收。
- 内置任务奖励命令 `/teamecon reward` 和 `/teamecon_quest_reward`，提供团队钱包追加积分、持久化防重复领取及 Java API；沿用现有任务奖励领取记录。

### English

- Vending Machines and `/teamecon shop` restore each player's last tab, saved on the local client across game restarts. First-time users start on **Items**. Mystery Box Machines and Wireless Terminal shortcuts retain their designated pages.
- Shop searches accept full pinyin and initials for Chinese display names, including simplified/traditional text and `lv` / `lü` input. Name, item ID and `#tag` searches remain available.
- Mystery box tiles show prize chances and × counts. Default pools include normal, splash and lingering potions with their actual effects.
- Added `/teamecon admin boxes` to create, edit and delete box types, names, prices and weighted prizes using a searchable item/potion grid.
- Batch openings deliver the actual draws and persist overflow prizes for later collection through the box screen or `/teamecon claimboxes`; pending batches must be claimed before opening more.
- Metal and gem storage blocks, redstone/coal/lapis blocks and hay bales use the value of their base materials without reversible crafting markup. With defaults, a gold ingot is valued at 64 points and a gold block at 576 before demand adjustments. Explicit server base-price overrides take precedence.

- Added a creative-style operator pricing panel with name/ID/pinyin search, independent purchase and recycling overrides, instant save and reset, and explicit mod-item recycling.
- Added built-in quest reward commands and a Java API, with additive wallet credits and persistent per-wallet claims, including existing quest reward claim records.
