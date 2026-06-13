# Clean Dashboard Plugin Example

This directory is a source template for an `.acplugin` package.
It declares both `ui` (global UI template) and `overlayStyle` (a selectable item in the main "悬浮窗样式" list).

To package it, zip the contents of this directory, not the directory itself:

```powershell
Compress-Archive -Path plugin.json,ui,icons -DestinationPath clean-dashboard.acplugin -Force
```

The sample intentionally omits binary icon/font files. Add images under `icons/`
and update `icons/icons.json` when creating a real plugin.
