# AMap Companion 项目生成日志

生成时间：2026-06-12 01:45（Asia/Shanghai）

合并更新时间：2026-06-12 02:05（Asia/Shanghai）

基础版本：`fd71eca feat: improve overlay contrast at low opacity`

## 本轮主要变更

### 导航协议与出口编号

- 解读并接入高德导航广播中的出口编号/名称信息，优先使用 `10001` 广播里的 `exit_name_info` 相关数据。
- 增加 `10001` 与 `12011` 出口信息的对比逻辑，用于记录两者不一致时的诊断日志。
- 在经典 UI、新 UI、卡片 UI、灵动岛等导航状态下，将出口编号/名称与绿色导航指示箭头交替显示。
- 修正出口编号与绿色导航箭头重叠、底框残留、字体过大和显示不全问题。
- 出口编号过长时采用右侧进入、左侧移出的跑马灯显示方式，完整展示后再切回绿色导航箭头。

### 路况光柱条

- 新增 `TmcProgressBar`，在悬浮窗上边缘平直段显示 TMC 路况光柱条。
- 光柱条厚度最终调整为 4dp，两端采用胶囊圆角。
- 当前位置使用 `navigation_widget_icon_car_position_blue.png`，并在光柱条厚度方向居中显示。
- 光柱条长度按各 UI 的上边缘平直段约束，避免过短或超出直线段。

### 悬浮窗布局与状态稳定性

- 调整悬浮窗宽度固定器逻辑，减少内容增减时的宽度抖动。
- 导航结束或手动退出导航后，清理残留电子监控标识。
- 删除“灵动岛（测试）”样式及对应选项，保留正式灵动岛样式。
- 修复灵动岛导航状态下指示箭头、待转入道路和 ETA 信息显示异常。
- 新 UI 横向留白收紧，减少内容元素两侧空白。

### 主界面设置 UI

- 主悬浮窗和副屏悬浮窗大小调节滑杆改为实时生效，移除额外应用按钮。
- 主背景透明度滑杆最大值从 90% 调整为 100%。
- 主背景透明度、主悬浮窗大小、副屏悬浮窗大小等滑杆说明加粗，并缩小说明与滑杆之间的距离。
- “副屏大小 **%”文案改为“副屏悬浮窗大小 **%”。
- 新增 8 个主背景色色块，第一种保留默认 `#111827`，并加入更明显的紫色、橙色、红色等颜色。
- 副屏悬浮窗位置调节由上下左右按钮改为摇杆式调节环。
- 调节环中心蓝色球体高光调整到中心，下方增加“副屏悬浮窗调节环”标注。

### 自定义悬浮窗内容

- 将“剩余里程/时间/到达时间”和“目的地地点”拆分为两个独立开关。
- “目的地地点”只在经典 UI 和新 UI 中显示。
- 灵动岛和卡片 UI 不显示“目的地地点”，仅保留紧凑导航/ETA 信息。
- 设置页预览与实际悬浮窗显示规则保持一致。
- “普通超速边框提醒”和“超速10%边框提醒”移动到“自定义悬浮窗内容”区域。

### 车道图标

- 保留 1-48 车道编号对 `lane_pdf_N` 图标的匹配。
- 车道编号 62 使用 `global_image_auto_landback_17.png`。
- 车道编号 85 使用 `global_image_auto_landback_150.png`。
- 车道编号 89 使用 `unlined.png`，并自适应透明边界调整大小。
- 车道编号 88 及其它超出既有映射的编号使用 `global_image_auto_landback_149.png`。

### 电子监控显示

- 电子限速数字字体最终调整为 11sp，并保持加粗。
- 修正电子监控图标在部分状态下闪烁、消失再出现的问题。

### 超速边框提醒

- 普通超速边框提醒采用“闪烁 5 秒 + 保留 20 秒”的循环方式。
- 超速 10% 边框提醒采用“闪烁 5 秒 + 保留 10 秒”的循环方式。
- 修正保留阶段的含义：保留时间内继续显示对应超速边框颜色，而不是恢复普通边框或隐藏颜色。
- 退出超速、未获取限速值或对应提醒关闭时，边框恢复普通状态。

## 关键文件

- `app/src/main/java/com/autonavi/companion/AppPrefs.java`
- `app/src/main/java/com/autonavi/companion/MainActivity.java`
- `app/src/main/java/com/autonavi/companion/OverlayService.java`
- `app/src/main/java/com/autonavi/companion/LaneBarView.java`
- `app/src/main/java/com/autonavi/companion/TmcProgressBar.java`
- `app/src/main/java/com/autonavi/companion/ClusterJoystickView.java`
- `app/src/main/java/com/autonavi/companion/OverlayUiStyles.java`
- `app/src/main/java/com/autonavi/companion/ServiceAreaParser.java`
- `app/src/main/res/drawable/global_image_auto_landback_17.png`
- `app/src/main/res/drawable/global_image_auto_landback_149.png`
- `app/src/main/res/drawable/global_image_auto_landback_150.png`
- `app/src/main/res/drawable/navigation_widget_icon_car_position_blue.png`
- `app/src/main/res/drawable/unlined.png`

## 构建验证

构建命令：

```powershell
$env:ANDROID_HOME='C:\Users\hjzuo\AppData\Local\Android\Sdk'; .\build.ps1
```

最近一次构建结果：

- APK：`C:\Users\hjzuo\Desktop\amap-companion\amap_companion_signed.apk`
- 生成时间：2026-06-12 02:00:09
- 签名校验：
  - v1：通过
  - v2：通过
  - v3：通过
- 编译警告：仅 Java 8 `source/target` 过时和引导类路径提示，不影响 APK 生成。

## 未提交资料说明

以下文件属于参考资料、临时诊断文件或构建产物，不建议提交到仓库：

- 根目录 APK / idsig 文件
- `Navi-Link-v1.8-release-202606101744.apk`
- `悬浮窗测试1.apk`
- 协议 PDF 文件
- 根目录 `TmcProgressBar.java`
- `TMC相关解析.txt`
- `amap_exit_diag.txt`
- `图标drawable.zip`
- 根目录 `图标drawable/`
