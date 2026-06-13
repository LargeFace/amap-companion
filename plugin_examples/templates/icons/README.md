# 图标插件模板

把 PNG、WebP、JPG 或 JPEG 图片放到 `icons/`，并在 `icons/icons.json` 中建立逻辑名称到文件路径的映射。

模板列出了常用名称：

- `turn_2`：左转
- `turn_3`：右转
- `turn_9`：直行
- `edog_camera`：摄像头
- `edog_limit_speed`：限速
- `edog_traffic_light`：红绿灯

删除不需要的映射，补齐保留映射对应的图片后，从 `update_server` 目录打包：

```powershell
npm run plugin:pack -- ../plugin_examples/templates/icons ../icons-template.acplugin
```
