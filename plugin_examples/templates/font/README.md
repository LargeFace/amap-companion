# 字体插件模板

将有合法分发授权的字体文件放到：

```text
fonts/main.ttf
```

入口文件必须是真实可用的 TTF 字体。模板不附带字体文件，避免把系统字体或授权不明确的素材提交到仓库。

添加字体后，从 `update_server` 目录打包：

```powershell
npm run plugin:pack -- ../plugin_examples/templates/font ../font-template.acplugin
```

发布前至少修改 `plugin.json` 中的 `id`、`name`、版本和开发者信息。
