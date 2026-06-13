# 悬浮窗样式插件模板

编辑 `ui/overlay.json` 调整悬浮窗布局。安装后，该插件会作为独立项目出现在 App 的“悬浮窗样式”列表中。

此模板只声明 `overlayStyle`，不会替换主界面 UI。

从 `update_server` 目录打包：

```powershell
npm run plugin:pack -- ../plugin_examples/templates/overlay_style ../overlay-style-template.acplugin
```
