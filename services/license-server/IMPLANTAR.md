# Pôr o servidor de licenças no ar

Sete passos. Precisam de si porque envolvem login no navegador e segredos que só devem existir na
sua máquina e no Cloudflare — nunca neste repositório, que é público.

Tempo estimado: dez minutos.

---

## Antes de começar

Precisa de:

- **Node.js** — já instalado nesta máquina (v24).
- **Uma conta Cloudflare** — gratuita, sem cartão: <https://dash.cloudflare.com/sign-up>
- **A sua conta Stripe** — já a tem.
- **Uma conta Google Play Console e um projeto Google Cloud** — necessários para a compra Android.

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

## 5. Ligar o Google Play

No Play Console, para o pacote `com.lucasserafin94.iptvburo`:

1. envie primeiro um AAB assinado para uma faixa de teste fechada;
2. crie o produto único `iptvburo_730_days`;
3. crie e ative a opção de compra `rent_730_days`, do tipo aluguel, duração `P730D` e quantidade
   máxima um;
4. ligue o aplicativo ao projeto Google Cloud, habilite a Google Play Android Developer API e dê à
   conta de serviço somente as permissões necessárias para consultar e reconhecer compras;
5. não reutilize a conta de serviço em outros produtos.

Entregue as credenciais ao Worker. O e-mail pode ficar como segredo para manter toda a identidade
operacional no mesmo cofre:

```powershell
npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_EMAIL
npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_PRIVATE_KEY
```

No segundo comando, cole somente o valor `private_key` do JSON da conta de serviço. Não copie o
arquivo JSON para esta pasta e apague qualquer download temporário depois de o guardar no cofre de
segredos da organização.

Crie uma chave independente de 32 bytes para cifrar os tokens de compra no D1, sem imprimi-la no
terminal nem colocá-la na linha de comando:

```powershell
node -e "process.stdout.write(require('crypto').randomBytes(32).toString('base64'))" |
  npx wrangler secret put GOOGLE_TOKEN_ENCRYPTION_KEY
```

O `wrangler.toml` recusa compras de teste em produção e executa reconciliação horária. Para testar
cartões de licença, use outro Worker, outro D1 e segredos sandbox; nunca mude
`GOOGLE_PLAY_ACCEPT_TEST_PURCHASES` para `true` no ambiente que atende clientes reais.

Antes de liberar a faixa pública, valide: compra aprovada, compra pendente, cancelamento da
pendência, reinstalação no mesmo aparelho, reembolso integral, revogação no próximo ciclo e nova
compra após o término do aluguel. O botão Android chama a Billing Library; ele não deve abrir o
Checkout Stripe.

---

## 6. Proteger a administração

O token administrativo é a última barreira dentro do Worker, não a primeira. Publique o Worker em
um domínio controlado e crie uma aplicação **Cloudflare Access / Self-hosted** para `/admin*`, com:

- somente a identidade do administrador;
- MFA obrigatório;
- sessão curta;
- nenhum bypass público ou por país;
- logs de acesso habilitados.

Teste em janela anônima: `/admin`, `/admin/summary`, `/admin/grant`, `/admin/revoke` e
`/admin/keys` devem ser bloqueados pelo Access antes de chegarem ao formulário/token. Enquanto essa
política não existir, o painel não está aprovado para operação comercial, mesmo que os endpoints
ainda exijam `ADMIN_TOKEN` e tenham limitação de tentativas.

---

## 7. Verificar e publicar

Antes de publicar, a suíte local deve estar verde:

```powershell
node --check src/index.js
node --check src/checkout.js
node --check src/pages.js
node --test test/*.test.mjs
npx wrangler deploy --dry-run
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

Depois, repita no ambiente fechado do Google Play e confirme no D1 que nenhum token aparece em
texto aberto, a compra pendente não ativa e o reembolso muda compra e dispositivo para estado
revogado. A migration `0004_google_play_purchase_ledger.sql` precisa estar aplicada antes desse
teste.

---

## O que nunca deve fazer

- **Não coloque a chave privada de assinatura em nenhum ficheiro deste repositório.** Ele é
  público. Ela vive nos segredos do Cloudflare e no seu gestor de senhas.
- **Não partilhe o `ADMIN_TOKEN`.** Ele abre o painel que liberta licenças de graça.
- **Não guarde o JSON da conta de serviço Google no repositório ou em `.dev.vars`.**
- **Não me envie nenhum destes valores por mensagem.** Eu não preciso deles para trabalhar, e uma
  conversa é um sítio onde as coisas ficam guardadas.

---

## Gates antes de produção

1. Migrações D1 aplicadas e Worker publicado nessa ordem; migration 0004 confirmada.
2. Os cinco eventos Stripe selecionados no endpoint correto.
3. Compra, webhook, validação do aplicativo, reembolso parcial, reembolso total e disputa testados.
4. O painel administrativo protegido por Cloudflare Access/MFA; o token isolado não é suficiente
   para uma operação comercial definitiva.
5. Produto/opção de aluguel Google ativos e E2E fechado aprovado, inclusive reembolso/restauração.
6. Android release assinado e Windows launcher/MSI assinados e verificados pelo sistema operacional.
7. Nenhum segredo, cache `.wrangler`, `.dev.vars` ou token local incluído num commit.
