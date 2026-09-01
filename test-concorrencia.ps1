$url = "http://localhost:8080/vendas/pedidos"
$body = @{
    items = @(@{ sku = "ABC-123"; quantidade = 10; valor = 199.90 })
    cepDestino = "01310-100"
} | ConvertTo-Json -Depth 3

Write-Host "Enviando 6 requisicoes simultaneas (estoque=20, cada reserva=10)..."
Write-Host ""

$jobs = 1..6 | ForEach-Object {
    $i = $_
    Start-Job -ScriptBlock {
        param($url, $body, $i)
        try {
            $resp = Invoke-RestMethod -Uri $url -Method Post -ContentType "application/json" -Body $body -ErrorAction Stop
            "[Req $i] HTTP 200 - Pedido $($resp.pedidoId)"
        } catch {
            $code = $_.Exception.Response.StatusCode.value__
            "[Req $i] HTTP $code - $($_.ErrorDetails.Message)"
        }
    } -ArgumentList $url, $body, $i
}

$jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
