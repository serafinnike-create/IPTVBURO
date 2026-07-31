# Codex — implementar GDD 6.0

Continue o repositório atual sem reiniciar o projeto.

Leia `docs/GDD_IPTV_BURO.md`, `docs/GDD_6_BURO_OFFLINE_VAULT.md`, os contratos em `packages/contracts/` e `docs/status/CURRENT_IMPLEMENTATION.md`.

Implemente o BURO Offline Vault somente para Android mobile/tablet e iPhone/iPad nesta etapa.

Ordem:

1. auditar o código e registrar decisões de armazenamento e lifecycle;
2. criar domínio compartilhado, estados, schemas, fixtures e testes;
3. entregar um download de filme funcional no Android mobile;
4. adicionar biblioteca offline, pausa, retomada, remoção e modo avião;
5. adicionar episódios e temporadas como fila;
6. criar a mesma vertical slice para iPhone/iPad;
7. atualizar o release manifest somente com resultados comprovados.

Regras:

- preservar a aplicação Android TV atual;
- usar armazenamento privado;
- não criar exportação de arquivos;
- não adicionar download às TVs;
- limitar concorrência e tentativas;
- executar I/O fora da thread principal;
- validar em CI e aparelhos físicos;
- informar claramente o que funciona e o que ainda é scaffold.
