# Navegação por D-pad

## Mapa da Sprint 1

```text
Onboarding
   └─ Aceitar → Home

Menu lateral
   ├─ Início
   ├─ TV ao vivo
   ├─ Fontes
   └─ Configurações

Fontes
   └─ Fonte → Categorias → Canal → Player
```

## Regras

- o primeiro controle acionável recebe foco;
- todo foco é visível por escala, borda e contraste;
- `Back` fecha o player antes de sair da tela;
- setas nunca exigem toque;
- listas usam rolagem preguiçosa e mantêm o item focado visível;
- ações destrutivas não existem nesta Sprint;
- loading, vazio e erro preservam um caminho de volta;
- controles de seek só aparecem quando Media3 informa que o item é seekable.

## Smoke test manual

1. iniciar sem tocar na tela;
2. aceitar o aviso com centro/Enter;
3. navegar até Fontes usando apenas setas;
4. abrir o seletor de arquivo;
5. selecionar uma M3U;
6. escolher categoria e canal;
7. pausar/reproduzir e voltar;
8. confirmar que o foco retorna ao catálogo.
