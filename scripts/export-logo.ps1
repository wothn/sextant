# Export the SextantMark geometry to desktop and branding PNG assets.
# Coordinates use a 100-unit canvas with padding for launcher masks.
Add-Type -AssemblyName System.Drawing
$repoRoot = Split-Path $PSScriptRoot -Parent
function Export-Mark([string]$relativePath, [int]$size, [bool]$transparent = $false, [bool]$monochrome = $false, [bool]$backgroundOnly = $false, [bool]$white = $false, [double]$scale = 1.0) {
    $bitmap = [Drawing.Bitmap]::new($size * 4, $size * 4)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear($(if ($transparent) { [Drawing.Color]::Transparent } else { [Drawing.ColorTranslator]::FromHtml('#F7F5F0') }))
    $graphics.ScaleTransform($size * 4 / 100.0, $size * 4 / 100.0)
    # 绕 100 单位画布的中心缩放图形（Translate/Scale/Translate 三次变换按 Prepend 语义
    # 复合结果就是"以 (50,50) 为中心缩放"）。$scale = 1 表示按设计稿原始比例。
    if ($scale -ne 1.0) {
        $graphics.TranslateTransform([single]50, [single]50)
        $graphics.ScaleTransform([single]$scale, [single]$scale)
        $graphics.TranslateTransform([single]-50, [single]-50)
    }
    $color = if ($white) { [Drawing.Color]::White }
        elseif ($monochrome) { [Drawing.Color]::Black }
        else { [Drawing.ColorTranslator]::FromHtml('#A6532D') }
    $pen = [Drawing.Pen]::new($color, 4.8)
    $brush = [Drawing.SolidBrush]::new($color)
    if (-not $backgroundOnly) {
        $graphics.DrawArc($pen, [single]21.2, [single]14, [single]57.6, [single]61.2, [single]25, [single]130)
        $graphics.DrawLine($pen, [single]50, [single]24.8, [single]28.4, [single]75.92)
        $graphics.DrawLine($pen, [single]50, [single]24.8, [single]71.6, [single]75.92)
        $graphics.FillEllipse($brush, [single]45.2, [single]20, [single]9.6, [single]9.6)
    }
    $output = [Drawing.Bitmap]::new($size, $size)
    $resize = [Drawing.Graphics]::FromImage($output)
    $resize.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $resize.DrawImage($bitmap, 0, 0, $size, $size)
    $target = Join-Path $repoRoot $relativePath
    # 目标目录可能还不存在（例如新加的 drawable-*dpi），先建出来再写。
    [System.IO.Directory]::CreateDirectory((Split-Path $target -Parent)) | Out-Null
    $output.Save($target, [Drawing.Imaging.ImageFormat]::Png)
    $resize.Dispose(); $output.Dispose(); $brush.Dispose(); $pen.Dispose(); $graphics.Dispose(); $bitmap.Dispose()
}
Export-Mark 'assets/branding/sextant-logo.png' 1024
Export-Mark 'assets/icon.png' 1024
Export-Mark 'assets/splash-icon.png' 512
Export-Mark 'assets/favicon.png' 48
Export-Mark 'assets/android-icon-foreground.png' 512 $true
Export-Mark 'assets/android-icon-monochrome.png' 432 $true $true
Export-Mark 'assets/android-icon-background.png' 512 $false $false $true
# 通知栏小图标是 24dp：透明底、**纯白**剪影。Android 只取它的 alpha 通道再自行上色，所以
# 白色不是最终显示颜色，而是 Android 的约定——lint 的 IconColors 会检查「通知图标必须全白」，
# 黑色会直接报 warning（它抓不到 android.R.drawable 那种框架资源，但抓得到应用自己的）。
# 几何与桌面图标共用同一套坐标，所以留白比例一致。
@{mdpi=24; hdpi=36; xhdpi=48; xxhdpi=72; xxxhdpi=96}.GetEnumerator() | ForEach-Object {
    Export-Mark "android/app/src/main/res/drawable-$($_.Key)/ic_notification.png" $_.Value -transparent $true -white $true
}
# adaptive icon 的三个图层，画布 108dp。系统**只显示中间 72dp**，外面每边 18dp 留给启动器
# 做视差/缩放（图形位移或放大时不露出空白）。
#
# 所以这里要把标记反过来缩小：设计稿里它占画布 57%，直接放进 108dp 画布的话在 72dp 可见区里
# 会变成 57% × 108/72 ≈ 86%，看起来比其它表面满一圈。乘上 72/108 之后画布内占比约 38%，
# 可见区里才回到 57% —— 与通知小图标、导出的 PNG 保持同一个观感。
#
# 注意这个系数只属于 adaptive icon。通知栏图标没有裁切，它就该是 57%，缩了反而不一致。
$adaptiveForegroundScale = 72.0 / 108.0
#
# 这里不再导出 mipmap-*dpi/ic_launcher.png：minSdk 是 26，mipmap-anydpi/ic_launcher.xml
# 在任何受支持的设备上都会命中，那些方形 PNG 永远轮不到，留着只会让 lint 报
# IconLauncherShape（「图标填满了整个方形区域」）。要支持 API 26 以下时再恢复这一组。
@{mdpi=108; hdpi=162; xhdpi=216; xxhdpi=324; xxxhdpi=432}.GetEnumerator() | ForEach-Object {
    $size = $_.Value
    $dir = "android/app/src/main/res/mipmap-$($_.Key)"
    Export-Mark "$dir/ic_launcher_foreground.png" $size -transparent $true -scale $adaptiveForegroundScale
    Export-Mark "$dir/ic_launcher_monochrome.png" $size -transparent $true -monochrome $true -scale $adaptiveForegroundScale
    Export-Mark "$dir/ic_launcher_background.png" $size -backgroundOnly $true
}
