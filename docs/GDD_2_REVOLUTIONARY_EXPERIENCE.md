# IPTV BURO — GDD 2.0: Revolutionary Entertainment Experience

**Versão:** 2.0  
**Data:** 30 de julho de 2026  
**Status:** extensão obrigatória do GDD 1.0 para continuidade do Codex  
**Objetivo:** transformar o IPTV BURO de um player funcional em uma plataforma premium de entretenimento para TV, celular e desktop.

---

## 1. Relação com o GDD 1.0

Este documento **não manda reiniciar o projeto** e não invalida a fundação já criada pelo Codex.

O GDD 1.0 continua sendo a fonte de verdade para:

- legalidade e escopo do produto;
- arquitetura local-first;
- segurança e privacidade;
- fontes M3U/M3U8, Xtream e XMLTV;
- `PlayerAdapter` por plataforma;
- Stream Health Engine;
- trial, ativação e licenciamento;
- backend, portal e expansão multiplataforma.

O GDD 2.0 **substitui e amplia** as decisões anteriores relacionadas a:

- identidade visual;
- design system;
- navegação;
- tela inicial;
- descoberta de conteúdo;
- TV ao vivo;
- player e controles;
- perfis e personalização;
- busca inteligente;
- continuidade entre dispositivos;
- métricas de experiência;
- critérios de qualidade visual e de interação.

Em caso de conflito:

1. legalidade, segurança e privacidade do GDD 1.0 vencem;
2. arquitetura-base e contratos de domínio do GDD 1.0 permanecem;
3. design, UX, descoberta e experiência do GDD 2.0 vencem;
4. nenhuma implementação existente deve ser apagada sem inventário, justificativa e ADR.

---

## 2. Tese do produto

> O IPTV BURO não é uma lista de canais com um player. É um sistema operacional pessoal de entretenimento que organiza fontes autorizadas, transforma catálogos desorganizados em uma experiência cinematográfica e leva o usuário ao conteúdo certo com o menor esforço possível.

### 2.1 North Star

O usuário deve abrir o IPTV BURO e sentir que está entrando em um serviço premium comparável aos melhores aplicativos de streaming, mas com vantagens que os serviços fechados não oferecem:

- catálogo unificado de fontes autorizadas pelo próprio usuário;
- TV ao vivo integrada a filmes e séries;
- organização inteligente de playlists imperfeitas;
- qualidade de reprodução adaptativa e explicável;
- personalização controlável, sem aprisionar o usuário em um algoritmo;
- continuidade entre TV, celular, navegador e desktop;
- interface rápida mesmo em hardware de TV limitado.

### 2.2 Diferencial central

Outros players mostram o que existe na playlist. O IPTV BURO deve **entender, organizar, apresentar e proteger a experiência**.

O produto será construído sobre cinco pilares:

1. **Cinema:** apresentação visual premium, imersiva e legível a três metros.
2. **Velocidade:** interação instantânea e reprodução resiliente.
3. **Inteligência:** organização, busca e recomendações úteis, preferencialmente locais.
4. **Controle:** o usuário entende por que algo foi recomendado e pode ajustar tudo.
5. **Continuidade:** o entretenimento acompanha o usuário entre dispositivos.

---

## 3. Documentos obrigatórios

1. [Tese, princípios, público e métricas](gdd-v2/01-product-thesis-and-principles.md)
2. [Design system cinematográfico e especificação das telas](gdd-v2/02-cinematic-design-system.md)
3. [Funções revolucionárias e jornadas do usuário](gdd-v2/03-revolutionary-features-and-flows.md)
4. [Arquitetura de experiência, desempenho e dados](gdd-v2/04-experience-architecture-and-performance.md)
5. [Roadmap, backlog, testes e critérios de aceitação](gdd-v2/05-roadmap-backlog-and-acceptance.md)

Para iniciar ou continuar a implementação, usar também:

- [Prompt de continuação para o Codex](PROMPT_CODEX_CONTINUE_GDD2.md)

---

## 4. Regra de identidade

O IPTV BURO pode estudar padrões de Netflix, Apple TV, Prime Video, Max, Disney+, Google TV e aplicativos IPTV líderes, mas **não deve copiar**:

- logotipos;
- nomes de recursos protegidos;
- combinações visuais idênticas;
- disposição de tela que gere confusão de marca;
- assets, trailers, posters ou metadados sem licença;
- animações, ícones ou sons proprietários.

A meta é alcançar o mesmo nível de qualidade percebida por meio de uma identidade própria chamada, neste documento, de **BURO Cinematic System**.

---

## 5. Definição de sucesso

O GDD 2.0 estará implementado quando o usuário perceber, sem explicação, que:

- não está usando um player IPTV genérico;
- encontra algo para assistir rapidamente;
- TV ao vivo e catálogo parecem parte do mesmo produto;
- o controle remoto responde de forma previsível;
- o app sabe continuar de onde ele parou;
- problemas de stream são tratados de forma inteligente;
- o design permanece bonito e rápido em TVs modestos;
- a personalização ajuda, mas nunca remove o controle do usuário.
