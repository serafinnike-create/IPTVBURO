# ADR-011 — Google Play: aluguel de 730 dias e verificação no servidor

- Estado: aceito; implantação externa pendente
- Data: 10 de agosto de 2026
- Substitui: somente a mecânica Android de “produto não consumível” do ADR-004

## Contexto

O prazo comercial vigente é de 730 dias, conforme o ADR-010. Um produto
permanente não consumível continuaria pertencendo à conta depois desse prazo e
impediria uma nova compra normal. O catálogo atual do Google Play oferece opção
de compra do tipo aluguel para produtos únicos, com duração declarada.

O cliente também não pode decidir que um recibo é válido, reconhecer a compra
antes da concessão ou guardar credenciais da conta de serviço. Essas operações
pertencem ao backend.

## Decisão

1. O Android usa o produto único `iptvburo_730_days`, opção de compra
   `rent_730_days`, aluguel `P730D`, quantidade exatamente um.
2. A Billing Library apenas abre o fluxo, recebe o token opaco e o envia ao
   Worker. O app nunca concede nem reconhece uma compra sozinho.
3. O Worker consulta `purchases.productsv2`, exige pacote, produto, opção,
   aluguel, quantidade, conta ofuscada e estado esperados, concede 730 dias a
   partir de `purchaseCompletionTime` e somente então reconhece a entrega pela
   API do Google.
4. O identificador de conta enviado ao Play é SHA-256 do `ANDROID_ID`, escopado
   pelo identificador do aplicativo de produção. O valor bruto não sai do
   aparelho. O Worker exige o mesmo hash devolvido pelo Google.
5. O pedido do app inclui prova P-256 da identidade da instalação sobre nonce,
   hash do token e conta ofuscada. Nonces são de uso único.
6. O token é identificado por SHA-256 e armazenado cifrado com AES-GCM. O
   segredo de cifragem e a conta de serviço existem apenas no Worker.
7. Compra pendente não libera acesso. A consulta ao Play na retomada do app e a
   reconciliação horária do Worker concluem a concessão quando o pagamento muda
   para `PURCHASED`.
8. `CANCELLED` ou quantidade reembolsável zero revogam a concessão. A
   reconciliação é limitada, idempotente e reutiliza uma credencial OAuth por
   lote; erro de rede nunca revoga uma compra válida por suposição.
9. Uma reinstalação no mesmo aparelho/conta ofuscada pode mover a compra para a
   nova identidade. A identidade anterior é revogada na mesma operação, de modo
   que um token não licencia duas instalações.
10. Troca para outro aparelho continua a exigir um fluxo explícito de
    transferência/portal ou suporte. Não se reduz essa proteção para tratar
    aparelhos diferentes como a mesma identidade.

## Consequências

- O produto e a opção precisam existir e estar ativos no Play Console antes de
  uma build de produção.
- A conta de serviço precisa de acesso mínimo à Google Play Developer API.
- Compras de teste são recusadas em produção; habilitá-las exige ambiente de
  sandbox separado.
- A mesma compra não pode ser simultaneamente origem Stripe e Google Play no
  dispositivo; uma concessão nova limpa a referência da origem anterior.
- O release Android permanece bloqueado até compra, pendência, restauração,
  reembolso e recompra após término serem exercitados numa faixa de teste do
  Google Play.

