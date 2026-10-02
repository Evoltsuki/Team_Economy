# Team Economy

[简体中文](README.md)

[Download 1.0.0](https://github.com/Evoltsuki/Team_Economy/releases/tag/v1.0.0) · [Source code](https://github.com/Evoltsuki/Team_Economy) · [Report an issue](https://github.com/Evoltsuki/Team_Economy/issues)

![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1%20%7C%201.21%20%7C%201.21.1-5C913B) ![Loaders](https://img.shields.io/badge/Loaders-NeoForge%20%7C%20Forge-D97834) ![Version](https://img.shields.io/badge/Version-1.0.0-3979A8)

**Turn spare materials into points, and build an arcade at your survival base.**

Team Economy adds material recycling, a points shop, shared team wallets and six playable game machines to Minecraft. Sell surplus supplies, buy what you need, unlock equipment and play directly on machines placed in your world. Install FTB Teams to share your wallet and equipment level with friends.

[Installation](#installation) · [Getting started](#getting-started) · [Commands](#commands) · [Configuration](#configuration) · [Building from source](#building-from-source)

![Game machines in a white showroom](docs/images/screenshots/image-20260930001256496.png)

## From your storage room to an arcade

### Recycle materials and shop for supplies

The shop's **Sell** tab has a **27-slot staging area**. Add materials, review the quote for the entire batch, then confirm the sale. Points go directly into your wallet. Spend them on vanilla items and enchanted books, with category filters and searches by name, item ID or `#tag`.

Selling more of the same material lowers demand and its recycling value. Purchase prices are calculated separately. Default advancement requirements keep exploration and resource gathering part of survival progression.

![The recycling inventory and batch quote](docs/images/screenshots/image-20260930001829218.png)

### Save and unlock equipment together

With **FTB Teams**, teammates share their points and **five equipment levels**. Recycling income, purchases, upgrades and games all use the team wallet. The sidebar shows the team balance and members' net contributions.

Without FTB Teams, personal wallets and all six games remain available. Equipment access follows the wallet's level; vanilla advancement requirements for purchases are checked for the player making the purchase. Joining a team does not merge your personal balance into its wallet.

![Five equipment levels in the shop](docs/images/screenshots/image-20260930001906620.png)

### Place the games in your world

Place a machine, stand in front of it, and right-click its buttons to choose a game option, adjust the stake and start a round.

| Game | Default unlock | How to play |
|---|---|---|
| High / Low Machine | Lv.1 | Choose low or high, then reveal a number from 1 to 10 |
| Slime Ice Hop Machine | Lv.2 | Jump to the next ice block; after a successful jump, continue or cash out |
| Lucky Wheel | Lv.3 | Spin the colored wheel and receive the payout for the selected segment |
| Red & Black Roulette | Lv.4 | Choose red, black or a single number and wait for the wheel to stop |
| Slot Machine | Lv.5 | Match symbols on three reels to receive a payout |
| Multiplier Machine | Lv.5 | Cash out while the multiplier rises; a crash ends the round with no payout |

One player can run several physical machines at once. Each machine has its own timer, controls and settlement, and the Wireless Terminal has a separate round. A machine stays occupied by its current player until the round ends. Stakes are charged at the start; payouts return to the wallet that funded the round. Pending settlements are retained when players disconnect or machines unload.

Upgrading unlocks permission to use equipment. Machines must still be crafted or purchased separately. Recycling can fund upgrades; game payouts depend on each round's outcome.

![High / Low Machine and its buttons](docs/images/screenshots/image-20260930001932113.png)

![Slime Ice Hop Machine](docs/images/screenshots/image-20260930001955386.png)

![Red & Black Roulette with a low playing surface](docs/images/screenshots/image-20260930002025700.png)

### Scratch tickets, mystery boxes and a portable shop

**Eight physical scratch tickets** unlock as you progress, from Lucky Numbers to Crown Jackpot. Hold a purchased ticket in your main hand, left-click to open it, and drag to scratch. A fully revealed ticket settles automatically.

The separate **Mystery Box Machine** shows its prize pools before you buy. Open 1, 10 or 64 boxes at a time and review the rewards. Pools include materials, valuable items and friendly or neutral mob spawn eggs available in your Minecraft version. Each draw uses the displayed pool; opening a batch does not increase an individual draw's odds.

The late-game **Wireless Terminal** provides portable access to the shop, recycling, upgrades, games and ticket purchases. Mystery boxes are opened at their own machine. Shop, box and terminal screens use compact, centered windows, including at automatic GUI scale.

### Keep the rules and recipes with you

Install **Patchouli** to read the Team Economy Handbook in-game. A handbook is given on first entry and can also be crafted shapelessly with **one book and one iron ingot**. Hold it and right-click to read.

Entries cover getting started, the economy, equipment, mystery boxes and teams. Machine entries explain acquisition, controls, rewards, event probabilities and crafting recipes. The interface and handbook support **English, Simplified Chinese and Traditional Chinese**. Screenshots here show the author's Chinese-language game.

If Patchouli is absent, use the [web gameplay guide (Chinese)](docs/试玩指南.md). The documented Forge 1.21.1 setup uses the web guide because a matching Patchouli release is not listed for that target.

![Handbook contents](docs/images/screenshots/image-20260930002043789.png)

![A crafting recipe in the handbook](docs/images/screenshots/image-20260930002114740.png)

![Slime Ice Hop rules in the handbook](docs/images/screenshots/image-20260930002146387.png)

## Installation

Choose **one** JAR matching your Minecraft version and loader. Place it in your instance's `mods/` folder. Multiplayer requires the same Minecraft version, loader and Team Economy JAR on both the client and server.

| Minecraft | Loader version | Java | File |
|---|---|---|---|
| 1.21.1 | NeoForge 21.1.250 | 21 | `teamecon-neoforge-1.21.1-1.0.0.jar` |
| 1.21 | NeoForge 21.0.167 | 21 | `teamecon-neoforge-1.21-1.0.0.jar` |
| 1.20.1 | Forge 47.4.10 | 17 | `teamecon-forge-1.20.1-1.0.0.jar` |
| 1.21.1 | Forge 52.1.0 | 21 | `teamecon-forge-1.21.1-1.0.0.jar` |

**Optional integrations:** FTB Teams enables shared wallets; Patchouli enables the in-game handbook. When installing either, also install the dependencies required by that mod's matching release. For NeoForge 1.21.1, the documented combination is FTB Teams 2101.1.11, FTB Library 2101.1.30, Architectury API 13.0.8 and Patchouli 1.21.1-93-NEOFORGE. Other targets require their own matching releases.

Team Economy works without these optional mods. Fabric and other Minecraft/loader combinations are not included in this release. The 1.20.1 catalog and prize pools use content available in 1.20.1.

Back up your world and configuration before replacing the mod or changing loaders. Remove the previous JAR when installing a replacement.

## Getting started

The following amounts use the **1.0.0 defaults**; servers may customize them.

1. Run `/teamecon shop`. A new personal wallet starts at **Lv.1 with 200 points**.
2. Open **Sell**, Shift-click spare vanilla materials into the staging area, review the quote and sell enough to reach **300 points**.
3. Buy a **High / Low Machine for 200 points** from the Machines tab. Leave a space **2 blocks wide, 3 high and 2 deep** when placing it.
4. Set a **20-point stake**, choose low or high, then start. Numbers 1–5 are low and 6–10 are high; either choice has a **50% chance**. A correct guess credits **36 points, including the stake**, with the default payout.

Next, save **1,200 points** to upgrade to Lv.2. Craft a Slime Ice Hop Machine or buy one for **1,500 points**, keeping additional points for playing.

The handbook explains individual winning conditions and probabilities. Previous losses do not improve the odds of a later independent round.

## Commands

### Player commands

These commands do not require operator permissions. Replace `<item_id>` and `<amount>` with actual values; do not type the angle brackets.

| Command | Purpose |
|---|---|
| `/teamecon` | Show brief help |
| `/teamecon shop` | Open the shop for purchases, recycling and upgrades |
| `/teamecon balance` | Show the current personal or team wallet balance |
| `/teamecon price <item_id>` | Look up an item's estimated value, e.g. `/teamecon price minecraft:diamond`; recycling income also depends on demand |
| `/teamecon sell` | **Immediately sell the entire stack in your main hand**; use the shop's Sell tab to review a quote first |
| `/teamecon buy <item_id> <amount>` | Buy a quantity, e.g. `/teamecon buy minecraft:bread 16`; funds, inventory space and purchase requirements still apply |

### Administrator commands

These require **operator permission level 2 or higher**. Enable cheats to use them in singleplayer. `[player]` is an optional online player name and defaults to yourself. When using a command with a player argument from the server console, supply that name.

| Command | Purpose |
|---|---|
| `/teamecon admin kit` | Give yourself six game machines, a Vending Machine and a Mystery Box Machine; `admin machine` is an alias |
| `/teamecon admin balance <points>` | **Set** your current wallet balance, rather than add to it |
| `/teamecon admin unlock [player]` | Set the target's current wallet to Lv.5 and enable their personal Team Economy advancement bypass |
| `/teamecon admin level <level> [player]` | Set the target's current wallet level to 1–5, retaining their personal bypass setting |
| `/teamecon admin bypass <true/false> [player]` | Enable or disable the personal advancement bypass without changing wallet level |
| `/teamecon admin status [player]` | Show the target's wallet level, balance and personal bypass status |
| `/teamecon admin reload` | Reload the mod's JSON configuration |

**The bypass applies only to Team Economy and does not grant or remove vanilla advancements.** It persists per player and is not shared with teammates. Wallet level and balance belong to the current personal or team wallet, so changing a team wallet affects its members. Purchases still cost points, and item bans and trading restrictions still apply. `/teamecon admin advancements [player]` is an alias for enabling the bypass.

To try all machines in a world with cheats enabled:

```text
/teamecon admin kit
/teamecon admin unlock
/teamecon admin balance 1000000
/teamecon shop
```

This gives equipment, unlocks the mod's level and advancement requirements, and sets your current wallet to 1,000,000 points. To restore normal advancement requirements and Lv.1:

```text
/teamecon admin bypass false
/teamecon admin level 1
```

Your balance, items and existing vanilla advancements are retained.

## Configuration

Server owners can customize prices, market demand, progression, equipment access and prize pools. After editing JSON files, run `/teamecon admin reload`.

| File | Purpose |
|---|---|
| `<world>/serverconfig/teamecon-server.toml` | Starting balance, global stake limits, recycling demand and purchase markup |
| `config/teamecon_base_prices.json` | Base valuation of vanilla materials |
| `config/teamecon_shop_prices.json` | Purchase price floors for rare items and enchantments |
| `config/teamecon_progression.json` | Advancement requirements and items that can be recycled but not purchased |
| `config/teamecon_casino_levels.json` | Level costs, machine limits, terminal access and ticket levels |
| `config/teamecon_enchants.json` | Enchanted book catalog and prices |
| `config/teamecon_blindbox.json` | Mystery box prices and prize pools |
| `config/teamecon_slots.json` | Slot reel weights and payouts |
| `config/teamecon_risk_tiers.json` | Risk tier settings |

See the [configuration guide (Chinese)](docs/平衡配置指南.md) for detailed settings.

## Common questions

**What kind of economy is this?** Points are stored in personal or FTB team wallets and used for the system shop and games. The shop follows server rules; it does not provide player-managed listings, prices or stock.

**Can I trade modded items?** The general trading catalog supports vanilla items and vanilla enchantments. Third-party items and enchantments are excluded. Team Economy equipment, terminals and tickets use their own acquisition menus and cannot be recycled.

**Can points buy every item?** Some purchases require the player's vanilla advancements. Key loot such as elytra and nether stars is unavailable for purchase by default. Mystery boxes use their own displayed prize pools.

**Can I include this in a modpack?** Yes. The MIT License permits modpack inclusion, modifications and commercial use. Retain the copyright and permission notices; see [LICENSE](LICENSE).

## Documentation and feedback

- [Gameplay guide, rules and recipes (Chinese)](docs/试玩指南.md)
- [Server configuration guide (Chinese)](docs/平衡配置指南.md)
- [Build and packaging guide (Chinese)](docs/手动打包指南.md)

Report issues through the repository's Issues page using the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md). Include your Minecraft, loader and mod versions, singleplayer/server setup, reproduction steps, expected and actual behavior, relevant logs and screenshots. For modpacks, include the mod list.

## Credits and license

**Created by 洁柔厨.** Code, documentation and some artwork were made with AI assistance. Thanks to the developers of Forge, NeoForge, FTB Teams, FTB Library, Architectury and Patchouli. See [CREDITS.md](CREDITS.md) and [NOTICE.md](NOTICE.md) for attribution and third-party materials.

**[MIT License](LICENSE).** You may use, modify, distribute and use the project commercially, provided that the copyright and permission notices are retained. Third-party materials remain under their respective licenses; see [NOTICE.md](NOTICE.md).

Points have no real-world monetary value. The mod provides no real-money purchases, cash withdrawals or real-world prizes. This is an unofficial Minecraft project, unaffiliated with Mojang or Microsoft.

## Building from source

<details>
<summary>Build commands and release packaging</summary>

The default target is **NeoForge 1.21.1**, requiring **JDK 21**. On Windows, run:

```powershell
.\build-mod.bat
```

On Linux or macOS:

```bash
bash ./gradlew releaseMod
```

The first build needs network access; add `--offline` when all dependencies are cached. `releaseMod` writes the JAR and checksum to `dist/`. A regular Gradle `build` writes to `build/libs/`. Resources are included, and building the NeoForge target does not require Python or launch the game.

To build all four targets and prepare the five GitHub Release attachments, install Python 3.11+, the dependencies in `requirements.txt`, JDK 17 and JDK 21, then run:

```powershell
py -3.12 tools/package_release.py --offline
```

Remove `--offline` if dependencies need downloading. See the [packaging guide (Chinese)](docs/手动打包指南.md) for target-specific commands and JDK selection.

`release/1.0.0/` contains four player JARs and `SHA256SUMS.txt`. Source code is available in the [GitHub repository](https://github.com/Evoltsuki/Team_Economy), with independently maintained `README.md` and `README_EN.md` files. The version remains **1.0.0** until the author explicitly requests a change; build and packaging commands never increment it.

</details>

<details>
<summary>Project structure</summary>

| Directory | Purpose |
|---|---|
| `src/main/java/` | Gameplay, interfaces, networking and server implementation |
| `src/main/resources/` | Textures, models, languages, recipes and the in-game handbook |
| `src/main/templates/` | Mod metadata templates filled during the build |
| `src/test/`, `src/qa/`, `src/portqa/`, `src/teamtest/` | Unit tests, visual/game checks and platform or team integration checks |
| `src/generated/` | Generated data, when needed |
| `versions/forge/` | Forge build entry point, shared-source conversion and version adapters |
| `docs/` | Gameplay, configuration and build guides |
| `docs/images/`, `docs/images/screenshots/` | Screenshot source list and gameplay images |
| `tools/` | Asset generators, verification and release scripts; see [tools/README.md](tools/README.md) |
| `gradle/`, `gradle/wrapper/` | Gradle Wrapper files required for source builds |
| `.github/` | Build workflow and issue template |
| `dist/` | Local player JARs and checksums |
| `release/` | Versioned GitHub Release attachments |
| `build/` | Build output and temporary files |
| `.gradle/` | Gradle cache |
| `logs/` | Local runtime logs |
| `run/` | Development game configuration and saved worlds |

Root Gradle files, the Wrapper and `build-mod.bat` support builds. `requirements.txt` lists Python tool dependencies. `README.md` and `README_EN.md` are independently maintained project documentation, alongside the license and attribution files.

`dist/`, `release/`, `build/`, `.gradle/`, `logs/`, `run/`, `internal/`, `PROGRESS.md` and the local task file are excluded from the repository. Local-only directories may be absent from a clean source download.

</details>
