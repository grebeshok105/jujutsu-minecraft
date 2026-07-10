$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function New-Brush([string]$hex) {
    return [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($hex))
}

$textureDir = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\assets\jujutsumod\textures\item'))
[System.IO.Directory]::CreateDirectory($textureDir) | Out-Null

$doll = [System.Drawing.Bitmap]::new(64, 64, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($doll)
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#A88F48'))

$strawDark = New-Brush '#746334'
$strawShadow = New-Brush '#8B763B'
$strawLight = New-Brush '#D3B866'
$strawTip = New-Brush '#E0C878'
$binding = New-Brush '#27332B'
$bindingEdge = New-Brush '#425046'
$cyan = New-Brush '#BDF9FF'
$cyanDark = New-Brush '#2FCFE4'

for ($y = 0; $y -lt 64; $y += 4) {
    $graphics.FillRectangle($strawShadow, 0, $y, 48, 1)
}
for ($x = 1; $x -lt 48; $x += 5) {
    $height = 8 + (($x * 7) % 17)
    $startY = (($x * 11) % (64 - $height))
    $graphics.FillRectangle($strawDark, $x, $startY, 1, $height)
    if (($x % 3) -eq 1) {
        $graphics.FillRectangle($strawLight, $x + 1, $startY + 2, 1, [Math]::Max(2, $height - 4))
    }
}
$graphics.FillRectangle($strawTip, 3, 2, 8, 2)
$graphics.FillRectangle($strawTip, 20, 19, 10, 2)
$graphics.FillRectangle($strawTip, 34, 38, 9, 2)

$graphics.FillRectangle($binding, 48, 0, 16, 16)
$graphics.FillRectangle($bindingEdge, 48, 0, 16, 2)
$graphics.FillRectangle($bindingEdge, 48, 7, 16, 1)
$graphics.FillRectangle($bindingEdge, 48, 14, 16, 2)
$graphics.FillRectangle($cyanDark, 48, 16, 16, 16)
$graphics.FillRectangle($cyan, 50, 18, 12, 2)
$graphics.FillRectangle($cyan, 54, 20, 4, 10)
$graphics.FillRectangle($binding, 48, 32, 16, 32)
$graphics.FillRectangle($binding, 44, 32, 20, 16)
$graphics.FillRectangle($bindingEdge, 44, 32, 20, 2)
$graphics.FillRectangle($bindingEdge, 44, 39, 20, 1)
$graphics.FillRectangle($bindingEdge, 44, 46, 20, 2)
$graphics.FillRectangle($bindingEdge, 50, 36, 12, 2)
$graphics.FillRectangle($bindingEdge, 50, 48, 12, 2)

$dollPath = Join-Path $textureDir 'straw_doll.png'
$doll.Save($dollPath, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$doll.Dispose()
$strawDark.Dispose()
$strawShadow.Dispose()
$strawLight.Dispose()
$strawTip.Dispose()
$binding.Dispose()
$bindingEdge.Dispose()
$cyan.Dispose()
$cyanDark.Dispose()

$remnant = [System.Drawing.Bitmap]::new(16, 16, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$graphics = [System.Drawing.Graphics]::FromImage($remnant)
$graphics.Clear([System.Drawing.Color]::Transparent)
$remnantShadow = New-Brush '#4F4630'
$remnantBase = New-Brush '#A88F55'
$remnantLight = New-Brush '#D6BC74'
$remnantBinding = New-Brush '#27332B'
$remnantCyan = New-Brush '#55E6F2'
$graphics.FillRectangle($remnantShadow, 4, 3, 8, 10)
$graphics.FillRectangle($remnantBase, 3, 5, 10, 6)
$graphics.FillRectangle($remnantLight, 5, 3, 5, 2)
$graphics.FillRectangle($remnantLight, 5, 11, 6, 2)
$graphics.FillRectangle($remnantBinding, 3, 7, 10, 2)
$graphics.FillRectangle($remnantCyan, 7, 6, 2, 4)
$graphics.FillRectangle($remnantCyan, 6, 7, 4, 2)
$graphics.FillRectangle($remnantShadow, 2, 6, 2, 4)
$graphics.FillRectangle($remnantShadow, 12, 6, 2, 4)
$remnantPath = Join-Path $textureDir 'resonance_remnant.png'
$remnant.Save($remnantPath, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$remnant.Dispose()
$remnantShadow.Dispose()
$remnantBase.Dispose()
$remnantLight.Dispose()
$remnantBinding.Dispose()
$remnantCyan.Dispose()

Write-Output $dollPath
Write-Output $remnantPath
