# 信号轨道外观

这是一个只声明 `overlayStyle` 的悬浮窗样式插件。

安装后打开 AMap Companion 主界面，在“悬浮窗样式”列表中会新增：

```text
插件：信号轨道外观（1.0.0）
```

选择后，悬浮窗使用深色横向仪表布局。该插件不会启用或替换“全局界面”。

从 `update_server` 目录打包：

```powershell
npm run plugin:pack -- ../plugin_examples/signal_rail_appearance ../plugin_examples/dist/example.overlay.signal.rail-1.acplugin
```
