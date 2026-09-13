# Export the SextantMark geometry to desktop and branding PNG assets.
# Coordinates use a 100-unit canvas with padding for launcher masks.
Add-Type -AssemblyName System.Drawing
$repoRoot = Split-Path $PSScriptRoot -Parent
function Export-Mark([string]$relativePath, [int]$size, [bool]$transparent = $false, [bool]$monochrome = $false, [bool]$backgroundOnly = $false) {
    $bitmap = [Drawing.Bitmap]::new($size * 4, $size * 4)
    $graphics = [Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear($(if ($transparent) { [Drawing.Color]::Transparent } else { [Drawing.ColorTranslator]::FromHtml('#F7F5F0') }))
    $graphics.ScaleTransform($size * 4 / 100.0, $size * 4 / 100.0)
    $color = if ($monochrome) { [Drawing.Color]::Black } else { [Drawing.ColorTranslator]::FromHtml('#A6532D') }
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
# 桌面图标与通知小图标共用同一套坐标，所以两者的留白比例完全一致。
# 通知栏小图标是 24dp：透明底、单色剪影。Android 只取它的 alpha 通道再自行上色，
# 所以黑色只是中性选择（与 android-icon-monochrome.png 一致），不是最终显示颜色。
@{mdpi=48; hdpi=72; xhdpi=96; xxhdpi=144; xxxhdpi=192}.GetEnumerator() | ForEach-Object {
    Export-Mark "android/app/src/main/res/mipmap-$($_.Key)/ic_launcher.png" $_.Value
}
@{mdpi=24; hdpi=36; xhdpi=48; xxhdpi=72; xxxhdpi=96}.GetEnumerator() | ForEach-Object {
    Export-Mark "android/app/src/main/res/drawable-$($_.Key)/ic_notification.png" $_.Value $true $true
}
