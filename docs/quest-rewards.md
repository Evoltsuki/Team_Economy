# 任务奖励接入 / Quest rewards

团队经济主模组提供任务奖励命令与 Java API。任务内容、完成条件、奖励 ID 和点数由任务模组的配置维护；不需要额外的任务奖励 JAR，也不强制安装 FTB Quests。钱包有 FTB Teams 队伍时归当前队伍，否则归个人。

## 命令接口

```text
/teamecon reward yourpack:chapter1/start 100 [玩家]
/teamecon_quest_reward 7445429B27FE4CD5 25 [玩家]
```

两条命令都需要 OP 2。可选参数 `[玩家]` 指一个在线玩家；省略时必须由玩家实体执行，发给该玩家当前钱包。服务器控制台或命令方块调用时需提供目标，例如 `teamecon reward yourpack:chapter1/start 100 PlayerName`。

通用命令使用小写 `命名空间:路径` 作为奖励 ID，推荐由整合包名、章节和奖励用途构成。同一 ID 在同一个钱包中只能领取一次，金额追加到现有余额；不因金额修改而再次发放。不同钱包各有独立的领取记录。玩家换队后按新钱包判断，不是全服每人一次。

`teamecon_quest_reward` 接受首位为 0～7 的 16 位十六进制 FTB 奖励 ID，字母大小写均可。该命令按相同的钱包领取规则执行。

点数为 1～1,000,000,000 的整数。成功时命令返回实际发放的点数；已经领取、余额上限或参数错误时不发放，返回 0。余额上限导致的失败不会消耗本模组的领取资格。任务框架是否允许重试领取由其自身规则决定，作者应配置适当的失败提示或重试流程。数据在正常存档时持久化；管理员回档也会回退对应记录。

## FTB Quests 示例

在任务编辑器中创建「命令奖励」，填写：

```text
teamecon reward yourpack:chapter1/start 100
```

使用领取玩家作为执行实体，`permission_level` 设为 `2`，`team_reward` 设为 `true`。无需给普通玩家 OP。SNBT 奖励配置的相关字段例如：

```snbt
{
    id: "7445429B27FE4CD5"
    type: "command"
    command: "teamecon reward yourpack:chapter1/start 100"
    permission_level: 2
    team_reward: true
}
```

也可以将 `command` 写为 `teamecon_quest_reward 7445429B27FE4CD5 100`。每项奖励使用稳定、唯一的 ID；不要在不同任务中重复使用相同 ID。接口适用于一次性奖励，可重复任务应由任务系统为各次发放生成不同的稳定 ID，并负责相应的次数或冷却限制。

## Java API

服务端模组或脚本的 Java 桥接可调用：

```java
import com.evolt.teamecon.api.QuestRewards;

// Run on the player's Minecraft server thread.
QuestRewards.Result result = QuestRewards.grant(player, "yourpack:chapter1/start", 100);
if (result.status() == QuestRewards.Status.AWARDED) {
    long added = result.credited();
}
```

`player` 为 `ServerPlayer`。结果状态为 `AWARDED`、`ALREADY_CLAIMED`、`WALLET_FULL` 或 `UNAVAILABLE`。非法 ID、金额或非服务端线程调用抛出异常。接口本身不判断任务是否完成，调用方必须在服务端验证完成条件；不要把客户端提交的 ID 和点数直接转发给接口。

## English

Use `/teamecon reward yourpack:chapter1/start 100 [player]` with permission level 2. The optional argument is one online player and is required for console calls. Otherwise the executing entity must be the claiming player. `/teamecon_quest_reward <16-digit FTB reward ID> <amount> [player]` is also available; its first hex digit must be 0–7.

Amounts are positive integers up to 1,000,000,000. Credits are additive and each reward ID can be claimed once per current wallet, with claims saved in the world. Another wallet has an independent claim record. Full-wallet failures do not mark the reward as claimed; the quest framework controls whether and how to retry. Successful commands return the credited amount, otherwise 0.

FTB Quests command rewards should execute as the claiming player with `permission_level: 2` and `team_reward: true`; players do not need operator status. Keep quest requirements and amounts in the quest configuration. For Java integrations, call `QuestRewards.grant(ServerPlayer, String, long)` on the server thread after validating completion server-side. FTB Quests and a separate reward add-on are not required by this API.
