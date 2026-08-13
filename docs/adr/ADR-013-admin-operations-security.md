# ADR-013 — Administração operacional, MFA e recuperação

- Estado: implementado
- Data: 13 de agosto de 2026

## Contexto

O painel administrava licenças, mas não reunia atendimento, risco, auditoria e finanças. O mesmo
token era enviado em todas as operações e não havia segundo fator. A mudança precisa continuar
compatível com os 30 dispositivos existentes e não pode transformar dados de suporte em parte da
decisão de entitlement.

## Arquitetura

```text
Aplicativos autenticados ──prova P-256──> Worker ──> D1 (licenças)
          │                                │
          └─ perfil/país aproximado ───────┼──> alertas sem IP
                                           │
Administrador ──token + TOTP──> sessão 8 h ├──> auditoria imutável
                                           ├──> suporte opcional
                                           ├──> visão financeira sanitizada
                                           └──> CSV / backup JSON sanitizado

D1 Time Travel ──bookmark nativo──> restauração operacional separada do painel
```

## Decisões

1. O MFA usa TOTP padrão e só passa a ser obrigatório depois que um código válido confirma o
   cadastro. Isso evita bloquear o único administrador no meio da configuração.
2. O segredo TOTP fica cifrado com AES-GCM. `ADMIN_MFA_ENCRYPTION_KEY` é um Worker secret e nunca
   fica no Git ou no D1 em texto legível.
3. Depois do MFA, o navegador usa uma sessão aleatória de oito horas. O D1 retém somente SHA-256 do
   token da sessão; sessões vencidas são removidas pelo cron horário.
4. Nome do aparelho, cliente, e-mail, pedido e nota são opcionais e nunca alteram a licença.
5. Mudança rápida de país e cinco chaves inválidas em quinze minutos geram alerta para revisão, mas
   não bloqueiam automaticamente. VPN, viagem e redes móveis produzem falsos positivos.
6. Stripe usa valores e moedas do ledger validado. Google Play não fornece preço ao Worker; o painel
   mostra o estado e encaminha a receita ao Play Console em vez de inventar um total.
7. Exportações excluem chave pública fixada, machine anchor, token de compra, token cifrado e códigos
   de ativação não usados.
8. A recuperação completa usa D1 Time Travel. O backup JSON do painel é uma cópia sanitizada para
   suporte e auditoria, não substitui a restauração do banco.

## Operação de recuperação

Consultar o bookmark atual:

```powershell
cd services/license-server
npx wrangler d1 time-travel info iptvburo-licencas --json
```

Antes de restaurar, registrar o horário, exportar o backup sanitizado pelo painel e obter um bookmark
do estado atual. A restauração é uma operação destrutiva e só deve ser executada após confirmação
explícita do responsável:

```powershell
npx wrangler d1 time-travel restore iptvburo-licencas --bookmark <BOOKMARK_CONFIRMADO>
```

## Limites e evolução

- A identidade do ator é o nome informado na sessão. Quando Cloudflare Access estiver ativado, deve
  ser substituída pelo e-mail validado no JWT do Access.
- As versões mínimas atuais são Windows `2.0.0-alpha.5` e Android `0.2.0-alpha.9`; devem acompanhar
  a política de release.
- Se a equipe crescer, sessões e auditoria devem receber papéis (leitura, suporte, financeiro e
  administrador) em vez de uma única função administrativa.
