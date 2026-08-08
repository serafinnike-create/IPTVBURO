# ADR-004 — Conta, dispositivo, licença e pagamentos

- Estado: aceito para implementação incremental
- Data: 2 de agosto de 2026

> Atualização: os termos comerciais de EUR 9,99 vitalícios foram substituídos
> pelo ADR-010. As decisões de identidade, assinatura e autoridade do backend
> permanecem válidas.

## Contexto

O produto oferece sete dias de avaliação sem cartão e compra única de EUR 9,99
por dispositivo. Android distribuído pelo Google Play e Windows/portal possuem
regras de cobrança diferentes. Uma credencial de playlist pertence ao provedor
do usuário e nunca pode servir como conta ou licença do IPTV BURO.

## Decisão

1. O aplicativo cria uma identidade de instalação: UUID aleatório mais par de
   chaves EC protegido pela plataforma. O código curto exibido ao usuário é
   derivado da chave pública e do UUID. Não usamos o endereço MAC real.
2. A avaliação e a licença são entitlements assinados pelo backend. O cliente
   nunca se autolicencia e não decide sozinho se um recibo é válido.
3. No Google Play, a compra será um produto único não consumível, validado e
   reconhecido pelo backend com a API oficial de faturamento.
4. No Windows e no portal, a compra será um Checkout de pagamento único. O
   backend concede o entitlement somente após webhook assinado e verificado.
5. O portal permite entrar, pagar, ativar por código, restaurar compra, consultar
   dispositivos e transferir/desativar uma licença conforme a política comercial.
6. Perfis de família são dados de experiência dentro de uma instalação: até
   cinco perfis, incluindo Kids. Eles não substituem conta, compra ou dispositivo.
7. O estado offline usa uma concessão assinada, limitada no tempo. Falha de rede
   não pode fabricar compra; falha temporária após uma validação legítima usa uma
   janela de tolerância explicitamente definida pelo backend.

## Contrato de estado

`TRIAL`, `ACTIVE`, `GRACE`, `EXPIRED`, `REVOKED` e `UNKNOWN` são estados de
domínio compartilhados. Cada resposta inclui produto, dispositivo, emissão,
validade, origem da compra e assinatura. Tokens, recibos e segredos não entram
em logs.

## Situação atual

O domínio de licença e a identidade Android já existem. A interface exibe o
código do dispositivo e informa corretamente que o servidor comercial ainda
não está ativo. Cobrança real está bloqueada até existirem conta Stripe, produto
no Play Console, domínio HTTPS, backend implantado e políticas comerciais
aprovadas. Nenhum botão deve simular pagamento aprovado.

## Consequências

- Uma compra não é vinculada a MAC, IP ou credencial IPTV.
- Restauração e troca de aparelho são auditáveis.
- A mesma regra comercial alimenta Android, Windows e portal.
- Chaves do Stripe e credenciais de serviço do Google existem apenas no backend.
