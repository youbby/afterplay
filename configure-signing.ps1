param(
    [Parameter(Mandatory = $true)][string]$KeystorePath,
    [Parameter(Mandatory = $true)][string]$KeyAlias
)
$ErrorActionPreference = 'Stop'
$resolvedKeystore = (Resolve-Path -LiteralPath $KeystorePath).Path
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
[System.Windows.Forms.Application]::EnableVisualStyles()
$form = New-Object System.Windows.Forms.Form
$form.Text = 'AfterPlay - Save release signing password'
$form.ClientSize = New-Object System.Drawing.Size(540, 275)
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.TopMost = $true
$info = New-Object System.Windows.Forms.Label
$info.Location = New-Object System.Drawing.Point(18, 15)
$info.Size = New-Object System.Drawing.Size(505, 68)
$info.Text = "Keystore: $resolvedKeystore`r`nAlias: $KeyAlias`r`nSaved encrypted for this Windows account on this PC only."
$form.Controls.Add($info)
$storeLabel = New-Object System.Windows.Forms.Label
$storeLabel.Text = 'Keystore password'
$storeLabel.Location = New-Object System.Drawing.Point(18, 94)
$storeLabel.AutoSize = $true
$form.Controls.Add($storeLabel)
$storeInput = New-Object System.Windows.Forms.TextBox
$storeInput.Location = New-Object System.Drawing.Point(185, 90)
$storeInput.Width = 330
$storeInput.UseSystemPasswordChar = $true
$form.Controls.Add($storeInput)
$keyLabel = New-Object System.Windows.Forms.Label
$keyLabel.Text = 'Key password (optional)'
$keyLabel.Location = New-Object System.Drawing.Point(18, 135)
$keyLabel.AutoSize = $true
$form.Controls.Add($keyLabel)
$keyInput = New-Object System.Windows.Forms.TextBox
$keyInput.Location = New-Object System.Drawing.Point(185, 131)
$keyInput.Width = 330
$keyInput.UseSystemPasswordChar = $true
$form.Controls.Add($keyInput)
$hint = New-Object System.Windows.Forms.Label
$hint.Text = 'Leave key password empty if it is the same as the keystore password.'
$hint.Location = New-Object System.Drawing.Point(18, 173)
$hint.Size = New-Object System.Drawing.Size(505, 30)
$form.Controls.Add($hint)
$save = New-Object System.Windows.Forms.Button
$save.Text = 'Save'
$save.Location = New-Object System.Drawing.Point(330, 222)
$save.Width = 85
$save.Add_Click({
    if ($storeInput.Text.Length -eq 0) { $storeInput.Focus(); return }
    $form.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $form.Close()
})
$form.Controls.Add($save)
$cancel = New-Object System.Windows.Forms.Button
$cancel.Text = 'Cancel'
$cancel.Location = New-Object System.Drawing.Point(430, 222)
$cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$form.Controls.Add($cancel)
$form.AcceptButton = $save
$form.CancelButton = $cancel
$form.Add_Shown({ $form.Activate(); $storeInput.Focus() })
try {
    if ($form.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { exit 1 }
    $storeSecret = ConvertTo-SecureString -String $storeInput.Text -AsPlainText -Force
    $keySecret = if ($keyInput.Text.Length -eq 0) { $storeSecret } else {
        ConvertTo-SecureString -String $keyInput.Text -AsPlainText -Force
    }
    $storeInput.Clear()
    $keyInput.Clear()
    $privateDirectory = Join-Path $PSScriptRoot '.signing'
    New-Item -ItemType Directory -Path $privateDirectory -Force | Out-Null
    [pscustomobject]@{
        KeystorePath = $resolvedKeystore
        KeyAlias = $KeyAlias
        StorePassword = $storeSecret
        KeyPassword = $keySecret
        ExpectedSignerSha256 = '6a41d26bde88baf8aefad32459253010785e424ece326227e20a726f19c87f83'
    } | Export-Clixml -LiteralPath (Join-Path $privateDirectory 'release.clixml')
    Write-Host 'Release signing configuration saved.'
} finally {
    $storeInput.Clear()
    $keyInput.Clear()
    $form.Dispose()
}
