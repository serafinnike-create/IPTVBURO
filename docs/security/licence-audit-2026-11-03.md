# Auditoria do licenciamento e do site de ativação — 2026-11-03

Escopo: Worker `services/license-server` (ativação, chaves, Stripe, Google Play, admin), cliente de
licença do desktop (`apps/desktop/.../license/`), cliente Android (`AndroidLicenseClient.kt`), páginas
de ativação servidas pelo Worker e o site público em `site/`.

Branch: `site/live-build-scroll`. Nada foi publicado: sem deploy do Worker, sem migração remota, sem
tag, sem push.

## Resultado dos testes

| Comando | Resultado |
| --- | --- |
| `node --test` em `services/license-server` | 284 testes, 284 passam, 0 falhas (eram 270 antes) |
| `./gradlew :apps:desktop:test --tests '*license*'` | BUILD SUCCESSFUL; 13 classes, 110 testes, 0 falhas |

Os 7 testes novos em `test/redemption-abuse.test.mjs` foram rodados também contra o `src/` do `HEAD`:
6 falham no código antigo (o sétimo protege um comportamento que já estava certo).

## Achados corrigidos

### 1. ALTA — Re-resgatar a própria chave estendia a licença sem limite e desfazia revogações

`redeemKey` aceitava a chave de novo quando `redeemed_by` era o próprio aparelho e refazia o resgate
inteiro: `expires_at = agora + grant_days` e `status = 'ACTIVE'`.

- Uma chave de 30 dias digitada de novo no dia 29 virava 59 dias, e assim para sempre.
- Um aparelho revogado à mão, ou marcado REFUNDED, voltava a ACTIVE só de digitar o código que já tinha.

Correção: o re-resgate pelo próprio dono não muda nada. Responde OK se o aparelho está ACTIVE e
`already_used` se não está. O dono continua sem ver "usada por outro aparelho".

### 2. MÉDIA — Uma chave substituía a licença em vigor, inclusive a de outra pessoa

O resgate sobrescrevia `expires_at` e apagava `stripe_session_id` e `google_purchase_token_hash`. Como
`/ativar` no site só pede o código público do aparelho (que aparece na tela e no QR):

- quem visse o código de um cliente podia gastar uma chave de 1 dia nele e reduzir os 2 anos pagos
  para 1 dia;
- o vínculo com a compra se perdia, o que quebrava a página `/obrigado` e a revogação por reembolso.

Correção: a chave soma ao prazo em vigor (`max(agora, validade atual) + dias`) e, nesse caso, mantém o
vínculo com a compra, para que um reembolso ainda encontre o aparelho. Aparelho expirado ou revogado
conta a partir de hoje, como antes. O lote só aplica se o aparelho ainda estiver no estado lido; se
uma compra ou revogação chegar no meio, a chave continua livre (`redemption_conflict`).

### 3. MÉDIA — O formulário `/ativar` era um oráculo ilimitado de chaves

As chaves têm 8 caracteres (cerca de 40 bits). `POST /ativar` não tinha rate limit, não exigia prova
de dispositivo e respondia "não existe", "já usada" ou "expirada". As falhas também não eram
registradas, então o alerta `REPEATED_INVALID_KEYS` nunca disparava por esse caminho.

Correção: `POST /ativar`, `/activate`, `/v1/redeem` e `/v1/key-info` usam o
`REGISTRATION_RATE_LIMITER` (20/min) com um bucket único por IP (`key-guess`), e as falhas do site são
registradas como as do app.

### 4. BAIXA — As janelas de tempo dos alertas comparavam formatos diferentes

`datetime('now', …)` do SQLite gera `AAAA-MM-DD HH:MM:SS`, e as colunas guardam ISO com `T`. Na
comparação como texto, qualquer horário do mesmo dia passava. Efeitos:

- "5 falhas em 15 minutos" (chaves e login admin) virava "desde a meia-noite UTC", gerando alertas
  falsos;
- a deduplicação de 24 h chegava a quase 48 h;
- o contador "expira em 7 dias" ficava deslocado.

Correção: os cortes agora estão no mesmo formato ISO (`strftime('%Y-%m-%dT%H:%M:%fZ', …)` ou data ISO
vinda do JavaScript).

### 5. UX — `/comprar` sem código era um beco sem saída

Abrir a página de compra sem `?device=` mostrava só uma frase. Agora ela mostra um formulário para
digitar o código do aparelho.

## Verificado e sem alteração

- **Webhook Stripe:** o HMAC-SHA256 é verificado antes de ler o corpo, com tolerância de 5 minutos,
  `livemode` exigido conforme `STRIPE_MODE`, e produto, moeda, valor e `metadata` conferidos contra o
  ledger. Há livro de idempotência por evento, e o webhook recusa tudo se o segredo faltar.
  Reembolso e disputa revogam pelo `stripe_session_id`.
- **Provas de dispositivo:** ECDSA P-256 sobre um texto canônico preso à ação. A chave pública fica
  fixada no registro, e os nonces são de uso único, gravados antes da resposta. Proof inválida não
  consome nonce.
- **Forjar device id:** o id é derivado de `SHA-256(chave pública + installationId)` e conferido no
  registro. Depois disso, só a chave fixada vale. Possuir o código público não autentica nada.
- **Adulterar a licença no cliente:** a licença é assinada com Ed25519 (ECDSA para TVs). O cliente
  confere `deviceId` e o nonce da requisição ao vivo. A janela offline conta a partir do `serverTime`
  assinado, e relógio atrasado bloqueia. O comportamento é igual no desktop e no Android.
- **Apagar arquivos para ganhar outro teste:** o `machine_anchor` (MachineGuid no Windows,
  IOPlatformUUID no Mac) devolve o mesmo início de teste. As três marcas locais e o `firstSeen` só
  encurtam o teste.
- **Uma chave em vários aparelhos:** a chave é de uso único e fica presa ao primeiro aparelho, com
  atualização condicional atômica. Cada compra Stripe vale para um aparelho. O token do Google Play
  fica preso à conta, e ao mudar de aparelho o anterior é revogado.
- **Admin:** todas as rotas de dados exigem sessão. A sessão exige o token e o TOTP quando o MFA está
  ativo. As respostas têm `no-store`, e o CSV é protegido contra fórmulas.
- **Checkout:** preço e moeda são decididos no servidor, e só um aparelho já registrado, com chave
  fixada, pode pagar.
- **Painel:** uma ativação por chave aparece com origem `ACTIVATION_KEY`. Um aparelho Mac aparece como
  "Computador Mac" (`MAC_COMPUTER`/`MACOS`).

## Pendências que dependem do dono

1. **Deploy do Worker** (`npx wrangler deploy`), depois de revisar. Estas mudanças não alteram o
   schema e não precisam de migração. Continuam pendentes, como já se sabia, as migrações 0013/0014 e
   o segredo `SYNC_ENCRYPTION_KEY`.
2. **MFA do admin — MÉDIA se ainda não estiver ativo.** Até o MFA ser ativado, o `ADMIN_TOKEN` sozinho
   abre uma sessão completa, e quem tiver o token pode ativar o MFA dele primeiro. Confira no painel,
   em Segurança, se aparece "MFA ATIVO" e se `ADMIN_MFA_ENCRYPTION_KEY` está configurado. Não mudei o
   código aqui: obrigar o MFA sem o segredo configurado trancaria você fora do painel.
3. **Stripe:** confirme que o endpoint do webhook assina os eventos `checkout.session.completed`,
   `checkout.session.async_payment_succeeded`, `charge.refunded`, `charge.dispute.created` e
   `charge.dispute.closed`, e que `STRIPE_MODE = "live"` corresponde à chave em uso.
4. **Teste real ponta a ponta em produção:** eu não fiz nenhum pagamento. O fluxo foi exercitado pelos
   testes do Worker (handler real, SQLite local): teste grátis, checkout com ledger, webhook assinado,
   `/obrigado`, resgate, limites e listagem no admin.

## Riscos restantes (não corrigidos)

| Severidade | Risco | O que resolveria |
| --- | --- | --- |
| MÉDIA (inerente) | Um cliente modificado ou um script pode gerar um par de chaves e um `installationId` aleatório a cada registro e ganhar um teste novo. O anchor só detém quem apaga arquivos. | Play Integrity no Android; teto de testes por hash diário de IP ou ASN (decisão de privacidade); limitador de registro mais apertado. |
| BAIXA | As provas não têm carimbo de tempo, e os nonces são esquecidos após 30 dias. Uma requisição capturada (exige quebrar o TLS) pode ser reenviada depois disso. Isso vale também para `/v1/provisioning/claim`, que devolve credenciais. | Protocolo v2 com timestamp assinado ou desafio do servidor, nos três clientes. |
| BAIXA | Pareamento: `/v1/pair/claim` só exige o código de 6 dígitos, e o contador de tentativas não conta códigos errados. O atacante precisa acertar o código nos segundos entre o envio do celular e a coleta da TV. | Um `claimToken` devolvido por `/v1/pair/start` e exigido no claim, com atualização dos clientes de TV. |
| BAIXA | Chaves de 8 caracteres (40 bits). Com o novo limitador, o risco é aceitável. | Gerar chaves mais longas. |
| INFO | Uma nova compra Stripe antes do vencimento conta 730 dias a partir do pagamento e descarta os dias restantes. | Decisão de produto. |

## Site

- **Páginas de ativação do Worker** (`/comprar`, `/ativar`, `/obrigado`, `/parear`, `/` do Worker):
  redesenhadas em PT-BR, EN, DE e IT.
  - Barra superior com a marca e seletor de idioma que preserva o aparelho, o código de pareamento e
    a sessão de pagamento.
  - Página de compra em duas colunas, com estado do aparelho em destaque e passos ao lado.
  - Formulário de código em `/comprar`, e um link discreto entre comprar e usar chave.
  - Sem scripts e sem nenhum recurso externo (a CSP continua `default-src 'none'`).
  - Conferido no navegador a 1440 px e 375 px, sem rolagem horizontal.
- **Site público `site/`:** não foi redesenhado. O `site.js` e o `styles.css` recebiam ao mesmo tempo
  a sua animação "live build" (agora no commit 932f0c6). Refazer a página por cima dela atropelaria
  esse trabalho, então os dois arquivos ficaram intocados.
