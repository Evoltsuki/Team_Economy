# 工具入口

所有命令从项目根目录执行。NeoForge 单独构建需要 Java 21；Forge 还需要 Python 3.11+，其中 1.20.1 使用 JDK 17。

| 工具 | 用途 |
|---|---|
| `package_release.py` | 构建并导出公开 GitHub 附件；`--offline` 使用 Gradle 缓存，`--stage` 只导出源码 |
| `prepare_forge.py` | 从共享源码生成 Forge 平台代码与对应版本的数据资源，输出仅在 `build/forge-*/generated/` |
| `verify_release.py` | 核对各目标 JAR 的加载器元数据、版本、Java 字节码与资源 |
| `verify_assets.py` | 核验模型、纹理、语言、配方及 JAR 中资源是否与源码一致 |
| `gen_machine_assets.py` | 全部游戏资源生成入口；会顺序调用其他资源生成模块 |
| `gen_guide.py`、`gen_guide_images.py` | 游戏内图文指南内容与图片 |
| `gen_gameplay_revision.py` | 由发布资源生成器调用，统一机器概率页、盲盒说明和换行标记 |
| `localize_progression.py` | 游戏进度相关文本本地化 |
| `gen_redesign_assets.py`、`gen_todo_assets.py`、`gen_completion_assets.py`、`gen_vending_assets.py`、`gen_revision_assets.py`、`gen_release_assets.py` | 主生成器调用的模型、材质与文本阶段；互相依赖，名称较旧但仍有效 |

重新生成资源需 `py -m pip install -r requirements.txt`。美术工具默认使用 Windows 字体。


根目录 `README.md` 为 GitHub 中文首页，`README_EN.md` 为英文版；两份文档独立维护，并一同保留在仓库中。完整构建方法见[手动打包指南](../docs/手动打包指南.md)。

## 发布凭据与提交检查

作者 API 凭据使用 `publish_credentials.py` 保存到当前 Windows 用户的 `%LOCALAPPDATA%/TeamEconomy/credentials/`，位于仓库外，以 Windows DPAPI 加密。只有原 Windows 用户环境可解密；不提供打印令牌的命令。令牌须先从对应平台的作者设置中创建，勿写入聊天、源码或配置示例。

```powershell
py tools/publish_credentials.py set modrinth
py tools/publish_credentials.py set curseforge
py tools/publish_credentials.py status
git config core.hooksPath .githooks
py tools/check_secrets.py
```

`set` 通过隐藏输入读取令牌。发布脚本可导入 `publish_credentials.load(provider)`，仅在内存中使用。GitHub 身份认证继续由 Git Credential Manager 管理。

`.gitignore` 排除凭据文件，提交钩子检查暂存区；GitHub Actions 在推送和 PR 中检查已跟踪文件，公开源码导出也会扫描。扫描只报告位置与类型，不打印匹配值。可检测已知令牌格式、明文 API 凭据赋值和敏感文件名；不能保证识别任意无特征字符串。新克隆需执行上述 `core.hooksPath` 命令，仓库管理员应在 GitHub 设置中启用 Secret scanning 和 Push protection。
