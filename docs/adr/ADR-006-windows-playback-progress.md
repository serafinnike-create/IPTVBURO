# ADR-006 — Persistência local de progresso no Windows

**Status:** aceita para o preview 0.2
**Data:** 2 de agosto de 2026

## Decisão

O Windows usa `java.util.prefs.Preferences` atrás de `PlaybackProgressRepository`. A chave física é um SHA-256 da identidade estável `(perfil, fonte, tipo, conteúdo)` e o valor possui apenas os campos do GDD 7.

URL resolvida de reprodução, host, usuário, senha, token, cookie e cabeçalhos não são aceitos pelo repositório. A sessão Xtream continua isolada no cofre DPAPI já existente.

## Motivos

- sobrevive ao encerramento e atualização do aplicativo sem exigir backend;
- está disponível no runtime empacotado do Windows (`java.prefs`);
- mantém UI, player e armazenamento separados pelo contrato compartilhado;
- permite trocar por SQLite ou sincronização futura sem alterar os composables.

## Regras operacionais

- checkpoint a cada 12 segundos durante reprodução, ao pausar, sair, liberar o player e terminar;
- somente filme e episódio; TV ao vivo não cria identidade de progresso;
- conclusão segue 90% ou últimos cinco minutos;
- revisão antiga não substitui revisão nova e conclusão não regride para incompleta;
- retomada é sempre uma escolha explícita na ficha.
