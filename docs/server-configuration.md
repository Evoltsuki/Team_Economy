# 商店与盲盒配置 / Shop and mystery box configuration

服主可自定义商店上架内容、售价以及盲盒奖品、数量与权重。首次启动服务器或进入单人世界后，文件生成在游戏目录的 `config/` 中。配置由服务端读取，客户端打开商店时获取实际目录和奖池。

## 修改与重载

1. 备份要修改的 JSON 文件。
2. 用 UTF-8 保存；JSON 不支持 `//` 注释或末尾多余逗号，可用 `_comment` 字段写说明。
3. 执行 `/teamecon admin reload`（需要 OP 2），或重启服务器。已打开的商店也会更新目录。
4. 查看服务端日志，并在游戏中核对目录、价格及奖池悬停提示。非法商店配置会关闭物品购买，非法奖池会被禁用；修正后再次重载。

这些 JSON 位于游戏目录，而非每个存档目录；同一客户端实例的单人世界共用它们。起始余额、购买加价等 TOML 设置位于 `存档/serverconfig/teamecon-server.toml`，建议停服修改。不会自动覆盖服主已有的自定义 JSON。

## 自定义商店

文件：`config/teamecon_shop_catalog.json`。下面的完整例子只出售面包、铁锭和橡木原木：

```json
{
  "version": 1,
  "includeDefaultItems": false,
  "disabledItems": [],
  "items": [
    {"item": "minecraft:bread", "price": 40},
    {"item": "minecraft:iron_ingot", "price": 24},
    {"item": "minecraft:oak_log", "price": 8}
  ]
}
```

| 字段 | 含义 |
|---|---|
| `includeDefaultItems` | `true` 保留默认物品目录并叠加自定义项；`false` 仅上架 `items` 中的物品 |
| `disabledItems` | 精确物品 ID 数组，例如 `["minecraft:diamond"]`；优先于自定义项，仅限制购买，不禁止回收 |
| `items[].item` | 物品注册 ID，例如 `minecraft:bread`；也支持已安装的第三方模组物品 |
| `items[].price` | 每件购买价格，必须是正整数 |
| `items[].stage` | 可选的 FTB 阶段名称，默认为空；设置后需要阶段提供方确认解锁 |

默认 `includeDefaultItems=true`、两数组为空。删除自定义项后重载，该项恢复默认目录行为；要确保下架，应加入 `disabledItems` 或使用仅自定义模式。数组各最多 4,096 项，不支持通配符或标签；同一商品不能重复。

例如，安装相应模组后可添加 `{"item":"create:iron_sheet","price":80}`。没有安装的物品会记录日志且不会显示。第三方物品只按明确的购买配置上架，**不参与本模组回收**。

自定义价格覆盖普通商店的稀有度购买底价，但仍不低于物品回收估价 × `shop.buyMarkup`。游戏机与终端仍受 `teamecon_casino_levels.json` 中的设备底价限制。价格和目录不会解除原有进度、钱包等级与 `sellOnly` 规则；这些在 `teamecon_progression.json` / `teamecon_casino_levels.json` 中分别配置。普通商店、终端及 `/teamecon buy` 共用同一目录和价格。

普通物品目录不负责附魔书或刮卡：附魔书使用 `teamecon_enchants.json`；八种票卡由专用购卡页生成有效票据。命令方块等受限原版物品、原版刷怪蛋和本模组内部物品不能通过目录放行。不支持自定义 NBT 或数据组件。

## 自定义盲盒

文件：`config/teamecon_blindbox.json`。下面完整例子创建一个建材箱，每次抽到一个奖项，原木概率 75%，铁锭概率 25%：

```json
{
  "version": 1,
  "pools": [
    {
      "id": "building",
      "price": 64,
      "enabled": true,
      "stage": "",
      "allowModdedItems": false,
      "enforceValueCap": true,
      "entries": [
        {"item": "minecraft:oak_log", "count": 16, "weight": 3},
        {"item": "minecraft:iron_ingot", "count": 4, "weight": 1}
      ]
    }
  ]
}
```

| 字段 | 含义 |
|---|---|
| `id` | 唯一箱子标识，1–32 位小写英文字母、数字或下划线；自定义箱以此标识显示 |
| `price` | 开启一个箱子的价格，必须是正整数 |
| `enabled` | 是否启用该箱，默认 `true` |
| `stage` | 可选阶段要求，默认空字符串；不要求原版进度或钱包等级 |
| `allowModdedItems` | 默认 `false`；显式设为 `true` 后允许已安装第三方模组的普通注册物品进入该奖池 |
| `enforceValueCap` | 默认 `true`，检查奖品按回收价计算的加权价值是否超过配置阈值；活动赠送箱可设为 `false`，不影响其他小游戏的阈值 |
| `entries[].item` | 奖品注册 ID；`minecraft:air` 表示空奖 |
| `entries[].count` | 一次抽中交付的数量，默认 1，范围 1–2,304 |
| `entries[].weight` | 正整数权重，默认 1，最大 1,000,000；概率 = 当前权重 ÷ 本箱权重总和 |

最多 64 个箱子，每箱最多 128 个奖项。相同物品可配置不同数量及权重；相同物品与数量的概率会在界面中合并显示。数量、价格和权重必须为 JSON 整数，不能填小数或带引号的数字。

设置 `allowModdedItems=true` 不会自动加入任何商品，也不会开启第三方物品回收。需要在 `entries` 中逐项写入奖品。默认友好／中立原版生物刷怪蛋可作奖品；受限原版物品、敌对原版刷怪蛋、本模组内部物品和空白票卡不可作奖品。物品必须在当前游戏版本及模组组合中存在。

缺失物品、负数、零权重或非法条目会禁用对应整箱，避免悄悄删除某个奖项而改变概率；重复箱子 ID 会禁用整个文件。不会扣费发放无效奖品。空 `pools` 数组可关闭全部盲盒。原有顶层数组格式仍可读取，无需强制转换。

批量开启仍支持 1／10／64 个；先检查所有可能奖品组合的背包空间，再统一扣款与交付。配置大量物品时，即使某个奖项概率很低，也可能需要足够空间才能开启。

## 附魔书和其他配置

`teamecon_enchants.json` 为附魔书数组：`id`（唯一标识）、`enchantment`（原版附魔 ID）、`level`、`price`、`stage`。删除条目即可下架；`[]` 关闭附魔书目录。等级不能超过该附魔原版上限，售价还受普通附魔书回收底价和 `teamecon_shop_prices.json` 约束。第三方附魔暂不支持。

`teamecon_shop_prices.json` 的 `minimumItemPrices` 与 `minimumEnchantmentPrices` 调整默认购买底价。删除自定义键并重载后恢复内置底价；将某键设为 `0` 可明确移除其稀有度底价，仍遵守基本买卖差价。

完整的经济、需求、设备及等级配置见[平衡配置指南](平衡配置指南.md)。

## English quick reference

Server-side files live in the game's `config/` directory. Edit UTF-8 JSON, then run `/teamecon admin reload` as OP 2. Open menus receive the updated catalogue. Back up edited files and check the server log for rejected entries.

- `teamecon_shop_catalog.json`: set `includeDefaultItems=false` for a custom-only item shop; list exact IDs in `disabledItems` to hide them. Add `{ "item": "namespace:item", "price": 80 }` to `items` for an explicit offer. An optional `stage` adds an FTB stage requirement. Installed third-party items are supported by explicit offers and remain non-recyclable. Purchase prices retain the resale-price × markup floor and equipment floors; progression and sell-only rules still apply.
- `teamecon_blindbox.json`: use the complete `version` / `pools` example above. Each opening draws one `entries` row. `count` controls quantity and `weight / total weight` determines probability. `enabled=false` hides a pool. `allowModdedItems=true` permits explicitly listed installed third-party rewards. `enforceValueCap=false` opts that pool out of the weighted resale-value check. The legacy top-level array remains supported.
- Malformed item catalogues close item purchases; invalid reward pools are disabled without charging players. Missing mod items are logged. Duplicate pool IDs disable the entire pool file. Correct the file and reload to recover. No custom NBT/components are supported.
