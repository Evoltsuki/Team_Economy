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


根目录 `README.md` 为 GitHub 中文首页，`README_EN.md` 为英文版；两份文档独立维护，并一同进入公开源码包。完整构建方法见[手动打包指南](../docs/手动打包指南.md)。
