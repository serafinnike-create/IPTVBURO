# ADR-010 — Prazo comercial e recuperação após disputa

- Estado: aceito
- Data: 8 de agosto de 2026
- Substitui: somente os termos comerciais de preço e duração do ADR-004

## Contexto

O ADR-004 e os GDDs iniciais descreviam uma licença vitalícia de EUR 9,99 por
dispositivo. A implementação comercial aprovada posteriormente passou a vender
um período fixo de dois anos. Manter as duas versões como fontes de verdade
criava risco jurídico, de suporte e de ativação incorreta.

O ciclo de chargeback também precisava de uma regra explícita. A Stripe envia
`charge.dispute.created` quando uma disputa abre e `charge.dispute.closed` quando
ela termina como `won`, `lost` ou `warning_closed`.

## Decisão comercial

1. O teste gratuito continua a durar sete dias e não exige cartão.
2. A compra é um pagamento único por dispositivo, sem renovação automática.
3. O entitlement pago dura exatamente 730 dias a partir da confirmação do
   pagamento pelo webhook. A compra não é vitalícia.
4. Os preços-base mantidos pelo servidor são EUR 9,90, USD 9,90 e BRL 99,90.
   Impostos legalmente exigidos podem ser acrescentados pelo Stripe Checkout.
5. Preço, moeda, produto e duração são definidos e validados pelo servidor. O
   cliente e a metadata recebida nunca podem alterar o contrato.

## Política de reembolso e disputa

1. Reembolso parcial é auditado e não revoga automaticamente uma licença
   indivisível. Reembolso integral confirmado revoga o entitlement da compra.
2. `charge.dispute.created` suspende o entitlement somente quando a compra
   disputada é a compra corrente do dispositivo.
3. Cada disputa é persistida separadamente. Isso impede que o encerramento de
   uma disputa restaure acesso enquanto outra disputa da mesma compra continua
   aberta ou foi perdida.
4. `charge.dispute.closed` com `won` ou `warning_closed` restaura a compra apenas
   quando:
   - não existe outra disputa bloqueadora para a mesma compra;
   - a compra não foi integralmente reembolsada;
   - ela ainda é a compra corrente do dispositivo;
   - a suspensão foi causada pelo fluxo de disputa, sem revogação administrativa
     posterior.
5. Se o prazo tiver terminado enquanto a disputa estava aberta, o dispositivo
   passa para `EXPIRED`, não para `ACTIVE`.
6. `charge.dispute.closed` com `lost` mantém o pagamento em `DISPUTED` e o
   dispositivo revogado.
7. Eventos duplicados ou fora de ordem não podem regredir um estado terminal nem
   substituir uma compra mais recente.

## Validação operacional

- O fluxo deve ser validado primeiro no modo de teste/sandbox da Stripe, sem
  misturar segredos ou dados D1 de produção.
- Um teste com dinheiro real requer uma pessoa a confirmar o meio de pagamento e
  deve usar o menor valor permitido pela Stripe ou o produto comercial normal,
  seguido de reembolso documentado.
- Segredos, payloads financeiros e dados de cartão nunca entram no Git ou nos
  logs do Worker.

## Consequências

- Comunicação, telas, recibos e suporte devem dizer “2 anos” e nunca “vitalício”.
- Alterar preço ou duração exige novo ADR e testes do contrato do Checkout.
- O endpoint Stripe precisa escutar `charge.dispute.closed` além dos eventos de
  pagamento, reembolso e abertura de disputa.
- Restore de compra e transferência de dispositivo continuam requisitos
  separados; ganhar uma disputa não equivale a transferir uma licença.
