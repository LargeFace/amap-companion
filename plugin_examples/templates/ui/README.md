# 主界面 UI 插件模板

编辑 `ui/main.json` 调整主界面声明式布局。此模板只声明 `ui`，不会出现在悬浮窗样式列表中。

常用组件包括 `row`、`column`、`text`、`badge`、`turnIcon`、`trafficLights`、`laneBar`、`edog`、`image` 和 `spacer`。

常用绑定包括 `mode`、`roadName`、`turnRoad`、`turnDistance`、`turnIcon`、`eta`、`alert`、`detail`、`limitSpeed` 和 `currentSpeed`。

从 `update_server` 目录打包：

```powershell
npm run plugin:pack -- ../plugin_examples/templates/ui ../ui-template.acplugin
```
