# Sextant Logo

正式 logo 由两条支线、一段弧线和顶部实心定位点构成，与应用内 `SextantMark` 使用相同几何图形。

- `sextant-logo.svg` 为矢量源文件，`sextant-logo.png` 为 1024px PNG。
- 桌面版使用暖白色 `#F7F5F0` 背景、铜橙色 `#A6532D` 图形，线宽为画布的 4.8%。
- `../icon.png`、`../splash-icon.png`、`../favicon.png` 为对应尺寸导出资源。
- `../android-icon-foreground.png`、`../android-icon-monochrome.png`、`../android-icon-background.png` 为透明前景、单色前景和背景资源。
- Android 桌面图标是 adaptive icon：`res/mipmap-anydpi/ic_launcher.xml`（与 `ic_launcher_round.xml`）引用 `mipmap-*/ic_launcher_{background,foreground,monochrome}.png` 三个图层，画布 108dp、系统只显示中间 72dp。因为 minSdk 是 26，`anydpi` 在所有受支持的设备上都会命中，所以不再需要 `mipmap-*/ic_launcher.png` 那组方形 PNG。
- 通知栏小图标使用 `res/drawable-*/ic_notification.png`（24dp，透明底 + 纯白剪影；`IconColors` 检查要求全白，系统只取 alpha 再自行上色）。
- 以上所有导出资源与 1024px 的 `sextant-logo.png` 共用同一套坐标和留白比例，只是尺寸与配色不同。
- 应用内 `SextantMark` 使用 24dp 画布、1.5dp 线宽及主题强调色。
- 在仓库根目录运行 `./scripts/export-logo.ps1` 可重新导出 PNG；调整几何时同步修改 SVG 和导出脚本。
