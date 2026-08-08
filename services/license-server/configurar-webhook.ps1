# Configura o segredo do webhook do Stripe, aplica as migrations e publica.
#
# Corre assim, na pasta services/license-server:
#
#   .\configurar-webhook.ps1
#
# Nada aqui e guardado em ficheiro. O valor vive so na memoria desta janela e desaparece quando ela
# fechar, que e o unico sitio onde um segredo do Stripe deve estar de passagem.

$ErrorActionPreference = 'Stop'

Write-Host ""
Write-Host "  Segredo do webhook do Stripe" -ForegroundColor Yellow
Write-Host ""
Write-Host "  Vai buscar em: Stripe -> Developers -> Webhooks -> o teu endpoint"
Write-Host "  O valor comeca por whsec_"
Write-Host ""

$entrada = Read-Host "  Cola aqui"
$segredo = $entrada.Trim()

# O erro que nos custou uma hora com o ADMIN_TOKEN: um espaco invisivel colado ao valor. O servidor
# recebe algo diferente do que se escreveu e responde como se a senha estivesse errada.
if ($segredo.Length -ne $entrada.Length) {
    Write-Host "  (removidos espacos nas pontas)" -ForegroundColor DarkGray
}

if (-not $segredo.StartsWith('whsec_')) {
    Write-Host ""
    Write-Host "  Isso nao parece um segredo de webhook." -ForegroundColor Red
    Write-Host "  Deve comecar por whsec_ - se comeca por sk_ e a chave secreta, que ja esta posta."
    Write-Host ""
    exit 1
}

if ($segredo.Length -lt 30) {
    Write-Host ""
    Write-Host "  Curto de mais ($($segredo.Length) caracteres) - a colagem ficou truncada." -ForegroundColor Red
    Write-Host "  Abre o Stripe, carrega em 'Reveal', e copia tudo."
    Write-Host ""
    exit 1
}

Write-Host ""
Write-Host "  A guardar no Worker..." -ForegroundColor DarkGray
$segredo | npx wrangler secret put STRIPE_WEBHOOK_SECRET
if ($LASTEXITCODE -ne 0) { Write-Host "  Falhou a guardar." -ForegroundColor Red; exit 1 }

Write-Host ""
Write-Host "  A aplicar migrations da base de dados..." -ForegroundColor DarkGray
npx wrangler d1 migrations apply iptvburo-licencas --remote
if ($LASTEXITCODE -ne 0) {
    Write-Host "  Falhou a migration. O Worker antigo continua publicado." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "  A publicar..." -ForegroundColor DarkGray
npx wrangler deploy
if ($LASTEXITCODE -ne 0) { Write-Host "  Falhou a publicar." -ForegroundColor Red; exit 1 }

Remove-Variable segredo, entrada

Write-Host ""
Write-Host "  Pronto." -ForegroundColor Green
Write-Host ""
Write-Host "  Confirma no Stripe que o endpoint ouve estes quatro eventos:"
Write-Host "    checkout.session.completed"
Write-Host "    checkout.session.async_payment_succeeded"
Write-Host "    charge.refunded"
Write-Host "    charge.dispute.created"
Write-Host ""
Write-Host "  Depois faz uma compra real de teste e usa 'Resend' no evento se precisares."
Write-Host "  O webhook deve responder 200 e a pagina /obrigado so confirma depois do evento assinado."
Write-Host ""
