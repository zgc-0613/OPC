$ErrorActionPreference = 'Stop'
$outputDir = Join-Path $PSScriptRoot '..\outputs\policy-full-texts-20260826'
$items = @(
    @{ Id = 26; Url = 'https://gxt.jiangxi.gov.cn/jxsgyhxxht/dsgx/content/content_2036257682931310592.html' },
    @{ Id = 77; Url = 'https://gxj.sz.gov.cn/gkmlpt/content/12/12602/mpost_12602272.html#3115' },
    @{ Id = 78; Url = 'https://www.szlh.gov.cn/zwgk/zcjd/2026/jdlhqzcrgznopcfzrgcssx/zcyw/content/post_12768711.html' }
)
$result = @()
foreach ($item in $items) {
    $response = Invoke-WebRequest -Uri $item.Url -UseBasicParsing -Headers @{
        'User-Agent' = 'Mozilla/5.0'
        'Accept-Language' = 'zh-CN,zh;q=0.9'
    } -TimeoutSec 30
    $content = [System.Net.WebUtility]::HtmlDecode($response.Content)
    $content = [regex]::Replace($content, '<(script|style|noscript|svg|nav|footer)[^>]*>.*?</\1>', ' ', 'IgnoreCase,Singleline')
    $content = [regex]::Replace($content, '<br\s*/?>|</(p|div|li|tr|h[1-6])>', "`n", 'IgnoreCase')
    $content = [regex]::Replace($content, '<[^>]+>', ' ')
    $lines = $content -split "`r?`n" | ForEach-Object { [regex]::Replace($_, '\s+', ' ').Trim() } | Where-Object { $_ }
    $text = $lines -join "`n"
    [System.IO.File]::WriteAllText((Join-Path $outputDir ($item.Id.ToString() + '.txt')), $text, [System.Text.UTF8Encoding]::new($false))
    $result += [pscustomobject]@{ id = $item.Id; status = $response.StatusCode; textLength = $text.Length }
}
$result | ConvertTo-Json -Compress
