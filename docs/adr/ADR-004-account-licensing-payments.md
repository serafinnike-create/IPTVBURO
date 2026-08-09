# ADR-004 — Conta, dispositivo, licença e pagamentos

- Estado: aceito para implementação incremental
- Data: 2 de agosto de 2026

> Atualização: os termos comerciais de EUR 9,99 vitalícios foram substituídos
> pelo ADR-010. As decisões de identidade, assinatura e autoridade do backend
> permanecem válidas.

## Contexto

O produto oferece sete dias de avaliação sem cartão. O texto original deste ADR
previa uma compra vitalícia de EUR 9,99 por dispositivo; esse termo é apenas
histórico e foi substituído pelo preço e prazo definidos no ADR-010. Android
distribuído pelo Google Play e Windows/portal possuem regras de cobrança
diferentes. Uma credencial de playlist pertence ao provedor do usuário e nunca
pode servir como conta ou licença do IPTV BURO.

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

O domínio de licença, as identidades criptográficas Android/Windows, o Worker
Cloudflare e o Checkout Stripe do portal já existem. O backend só concede o
entitlement após webhook assinado e mantém preço, moeda e prazo como contrato do
servidor. A compra Android pelo Google Play, a conta do portal, restauração e
transferência entre dispositivos continuam pendentes. Nenhum botão pode simular
pagamento aprovado.

## Consequências

- Uma compra não é vinculada a MAC, IP ou credencial IPTV.
- Restauração e troca de aparelho são auditáveis.
- A mesma regra comercial alimenta Android, Windows e portal.
- Chaves do Stripe e credenciais de serviço do Google existem apenas no backend.
