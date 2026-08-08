# Pôr o servidor de licenças no ar

Cinco passos. Precisam de si porque envolvem login no navegador e segredos que só devem existir na
sua máquina e no Cloudflare — nunca neste repositório, que é público.

Tempo estimado: dez minutos.

---

## Antes de começar

Precisa de:

- **Node.js** — já instalado nesta máquina (v24).
- **Uma conta Cloudflare** — gratuita, sem cartão: <https://dash.cloudflare.com/sign-up>
- **A sua conta Stripe** — já a tem.

Todos os comandos correm a partir desta pasta:

```powershell
cd "d:\CURSOR APPS\IPTVBURO\services\license-server"
```

---

## 1. Entrar no Cloudflare

```powershell
npx wrangler login
```

Abre o navegador e pede autorização. É a única vez que precisa do navegador.

---

## 2. Criar ou atualizar a base de dados

Na primeira instalação:

```powershell
npx wrangler d1 create iptvburo-licencas
```

Vai imprimir algo assim:

```
[[d1_databases]]
binding = "DB"
database_name = "iptvburo-licencas"
database_id = "a1b2c3d4-...."
```

**Copie esse `database_id`** para o ficheiro `wrangler.toml`.

Depois crie as tabelas:

```powershell
npx wrangler d1 execute iptvburo-licencas --file=schema.sql --remote
```

Numa base existente, aplique as migrations antes de publicar código novo:

```powershell
npx wrangler d1 migrations apply iptvburo-licencas --remote
```

Se uma migration falhar, pare. Não publique o Worker novo contra o schema antigo.

---

## 3. Gerar a chave de assinatura

```powershell
node generate-keys.mjs
```

Imprime duas chaves. Trate-as de forma diferente:

**A privada** — é o produto. Quem a tiver emite licenças que todos os clientes aceitam. Guarde-a
no seu gestor de senhas e entregue-a ao Worker:

```powershell
npx wrangler secret put SIGNING_KEY
```

Cola a chave privada quando pedir. Ela nunca fica em ficheiro nenhum desta pasta.

**A pública** — vai para dentro do aplicativo. Abra:

```
apps/desktop/src/main/kotlin/com/lucasserafin94/iptvburo/desktop/license/LicenseEndpoints.kt
```

e cole na constante `SERVER_PUBLIC_KEY`.

> **Não a perca.** Trocar a chave depois invalida todas as licenças já emitidas, porque os
> aplicativos instalados verificam contra a chave com que foram compilados. Gerar uma vez e
> guardar bem é o caminho; rodar é um lançamento com migração, não uma manutenção.

---

## 4. Ligar o Stripe

No painel do Stripe, em **Developers → Webhooks**, crie um endpoint:

- **URL:** `https://iptvburo.iptvburo.workers.dev/v1/stripe-webhook`
- **Eventos a enviar:**
  - `checkout.session.completed`
  - `checkout.session.async_payment_succeeded`
  - `charge.refunded`
  - `charge.dispute.created`
  - `charge.dispute.closed`

O Stripe mostra um **signing secret** que começa por `whsec_`. Entregue-o ao Worker:

```powershell
npx wrangler secret put STRIPE_WEBHOOK_SECRET
```

E a chave secreta da API, que a página de compra usa para criar a sessão de pagamento
(**Developers → API keys → Secret key**, começa por `sk_`):

```powershell
npx wrangler secret put STRIPE_SECRET_KEY
```

Por fim, uma senha à sua escolha para o painel de administração — qualquer texto longo e
aleatório, que só você saiba:

```powershell
npx wrangler secret put ADMIN_TOKEN
```

---

## 5. Verificar e publicar

Antes de publicar, a suíte local deve estar verde:

```powershell
node --check src/index.js
node --check src/checkout.js
node --check src/pages.js
node --test test/*.test.mjs
```

Depois das migrations e dos testes:

```powershell
npx wrangler deploy
```

O endereço final deste projeto é `https://iptvburo.iptvburo.workers.dev`.

Confirme que está vivo:

```powershell
curl https://iptvburo.iptvburo.workers.dev/health
```

Deve responder `{"ok":true,"time":"..."}`.

O `/health` só prova que o processo responde. Antes de aceitar clientes, faça uma compra no modo de
teste do Stripe e confirme o fluxo completo: Checkout pago → webhook 200 → dispositivo ativo →
reembolso integral/disputa revoga → disputa ganha restaura quando não existe outro bloqueio. A
página `/obrigado` não é prova de pagamento por si só.

---

## O que nunca deve fazer

- **Não coloque a chave privada de assinatura em nenhum ficheiro deste repositório.** Ele é
  público. Ela vive nos segredos do Cloudflare e no seu gestor de senhas.
- **Não partilhe o `ADMIN_TOKEN`.** Ele abre o painel que liberta licenças de graça.
- **Não me envie nenhum destes valores por mensagem.** Eu não preciso deles para trabalhar, e uma
  conversa é um sítio onde as coisas ficam guardadas.

---

## Gates antes de produção

1. Migrações D1 aplicadas e Worker publicado nessa ordem.
2. Os cinco eventos Stripe selecionados no endpoint correto.
3. Compra, webhook, validação do aplicativo, reembolso parcial, reembolso total e disputa testados.
4. O painel administrativo protegido por Cloudflare Access/MFA; o token isolado não é suficiente
   para uma operação comercial definitiva.
5. Nenhum segredo, cache `.wrangler`, `.dev.vars` ou token local incluído num commit.
