# ADR-012 — Arquitetura de paridade Samsung Tizen

**Status:** Aceito  
**Data:** 13 de agosto de 2026  
**Escopo:** `apps/samsung-tizen`

## Contexto

O aplicativo Android é a referência de produto para TV, mas Compose, Room,
Hilt, Media3 e o domínio Kotlin não executam em televisores Samsung. A versão
Tizen precisa preservar os mesmos fluxos, identidades, regras de segurança e
linguagem visual usando o runtime Web da TV e Samsung AVPlay.

O app deve continuar utilizável em Tizen 6.0 ou superior, manter navegação por
D-pad e evitar frameworks pesados. Credenciais e URLs autenticadas não podem
ser persistidas junto do catálogo nem aparecer em logs.

## Decisão

```text
HTML/CSS BURO + D-pad
          |
ApplicationStore + Router + I18n
       /                    \
CatalogDatabase          SecureCredentialStore
(IndexedDB)              (Tizen KeyManager)
       \                    /
       MediaSourceAdapter registry
       M3U | Xtream | Stalker
                 |
        PlaybackResolver (memória)
                 |
             AVPlayAdapter
```

### Camadas

1. **Domínio neutro:** modelos, identidades e capabilities em JavaScript ES5,
   testados contra as mesmas regras conceituais do domínio Kotlin.
2. **Application store:** único estado observável da UI. Telas não acessam
   rede, IndexedDB ou KeyManager diretamente.
3. **Persistência:** IndexedDB guarda perfis, fontes sem segredos, categorias,
   itens, favoritos e progresso. `localStorage` guarda somente preferências
   pequenas e não sensíveis.
4. **Credenciais:** Tizen KeyManager guarda URL base, usuário, senha, MAC,
   headers e demais segredos. Nenhum fallback persistente em texto claro é
   permitido; fora de uma TV/emulador, o adapter informa indisponibilidade.
5. **Source adapters:** cada fonte valida, autentica, lista catálogo e resolve
   playback por contrato. A UI vê metadados neutros, nunca credenciais.
6. **Playback:** a URL final é resolvida apenas após ação explícita do usuário,
   mantida em memória e entregue ao AVPlay. Erros e telemetria são redigidos.
7. **UI:** identidade BURO Nocturne, layout lógico 1920×1080, safe area e foco
   manual. A referência funcional é Android TV; diferenças ficam registradas
   como capabilities.

## Dados

Stores IndexedDB iniciais:

- `profiles` — perfil, avatar, Kids e fonte preferida;
- `sources` — identidade, tipo, nome e contagens, sem conexão privada;
- `categories` — tipo de conteúdo, ordem e id do provedor;
- `items` — metadados navegáveis e locator opaco;
- `favorites` — chave composta por perfil e identidade do item;
- `progress` — posição, duração, conclusão, revisão e timestamps.

Credenciais usam aliases `iptvburo.source.<sourceId>` no KeyManager. O valor é
JSON, mas nunca é retornado por `toString`, log ou estado de UI.

## Rede e memória

- Xtream carrega categorias primeiro e itens por categoria sob demanda;
- M3U possui limites defensivos de bytes, linhas e itens;
- nenhum catálogo completo é duplicado no DOM;
- listas renderizam janelas limitadas e liberam artwork fora da tela;
- respostas transitórias admitem uma repetição limitada; 401/403 não repetem;
- Stalker permanece atrás do mesmo contrato e só é habilitado após seus testes.

## Compatibilidade e trade-offs

- ES5 reduz ergonomia, mas evita tela preta por sintaxe em engines antigas;
- IndexedDB é assíncrono e mais complexo que `localStorage`, mas suporta
  catálogos maiores que o limite de 5 MB do Web Storage;
- KeyManager impede preview completo num navegador comum. Testes usam um
  adapter apenas em memória, nunca incluído no pacote de produção;
- o emulador valida APIs e fluxo, mas codecs, HDR, áudio e desempenho exigem TV
  física de mais de uma geração;
- pixel idêntico não substitui comportamento nativo: teclado virtual, retorno,
  player e loja seguem as convenções Samsung.

## Gates

Cada fatia precisa manter:

1. testes Node aprovados;
2. pacote `.wgt` assinado sem arquivos de desenvolvimento;
3. instalação e abertura no emulador;
4. zero credenciais em Git, logs e modelos persistidos;
5. `packages/platform-capabilities/samsung-tizen.json` coerente com o que foi
   realmente exercitado.

## Revisitar

- geração automática de contratos TypeScript/Kotlin quando a duplicação crescer;
- paginação/worker após medição com catálogo real grande;
- sincronização opcional e compras Samsung somente após a vertical local estar
  estável e os requisitos da loja serem conhecidos.
