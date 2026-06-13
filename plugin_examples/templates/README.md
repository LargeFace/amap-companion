# 单能力插件模板

每个目录只声明一种 capability，适合从最小结构开始开发：

| 目录 | capability | 入口 |
| --- | --- | --- |
| `font/` | `font` | `fonts/main.ttf` |
| `icons/` | `icons` | `icons/icons.json` |
| `ui/` | `ui` | `ui/main.json` |
| `overlay_style/` | `overlayStyle` | `ui/overlay.json` |

打包时必须压缩目录内的文件，而不是把模板目录本身作为 ZIP 根目录。最终 `.acplugin` 的根目录必须直接包含 `plugin.json`。

各模板中的插件 ID、名称、版本、开发者和说明都需要在发布前修改。
