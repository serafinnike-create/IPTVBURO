# ADR-008 — Download de VOD sem as condições do GDD 6

- Data: 3 de agosto de 2026
- Status: aceito
- Decisor: proprietário do produto (Lucas Serafin)
- Executor: implementação assistida

## Contexto

O [`GDD 6`](../GDD_6_BURO_OFFLINE_VAULT.md) condiciona o botão `Baixar` a seis
critérios simultâneos, entre eles *"a autorização permite uso offline"*, e lista
como proibido *"transformar o app em downloader genérico"*.

O [`CURRENT_IMPLEMENTATION.md`](../status/CURRENT_IMPLEMENTATION.md) registrava
que o downloader genérico do Windows havia sido **removido de propósito**, e que
Download permaneceria oculto até que fonte/backend autorizassem o item.

## Decisão

O proprietário decidiu, de forma explícita, liberar o download de VOD sem as
condições de autorização do GDD 6. Esta ADR existe para que essa divergência seja
rastreável, e não pareça descuido de implementação para quem ler o código depois.

## Consequências

O botão `Baixar` aparece para filmes independentemente de declaração de
autorização offline pela fonte.

Três restrições foram **mantidas**, por não serem estéticas:

1. **TV ao vivo é recusada.** Um stream ao vivo não termina; baixá-lo cresceria
   até encher o disco. É limite técnico, não de política.
2. **Nenhuma URL ou credencial vai para o disco.** A URL assinada é resolvida em
   memória por requisição e nunca é gravada, registrada em log ou usada no nome
   do arquivo. O nome vem da identidade de conteúdo. Isso preserva a regra de
   segredos que o resto do aplicativo já segue.
3. **Nada de remover proteção.** Conteúdo protegido é gravado como recebido. O
   aplicativo não tenta contornar criptografia — o que, além de proibido, não
   funcionaria.

O arquivo é escrito como `.part` e movido para o destino final somente ao
concluir, para que uma interrupção não deixe um arquivo truncado que depois
pareça completo.

## Risco assumido

A responsabilidade legal pelo uso de download sem autorização declarada pela
fonte passa a ser do operador do aplicativo. O GDD 6 permanece no repositório
como especificação original; esta ADR é a exceção registrada.
