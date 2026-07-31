# IPTV BURO — GDD 6.0: BURO Offline Vault

**Versão:** 6.0  
**Data:** 31 de julho de 2026  
**Status:** extensão obrigatória dos GDDs 1.0 a 5.0  
**Escopo P0:** Android mobile/tablet e iPhone/iPad

## 1. Objetivo

Permitir que o usuário baixe filmes, episódios e temporadas elegíveis para assistir sem internet, dentro do armazenamento privado do aplicativo.

O Offline Vault é uma biblioteca offline controlada pelo IPTV BURO. Não é um extrator de mídia nem um downloader genérico.

## 2. Plataformas

Implementação P0 obrigatória:

- `apps/android-mobile`;
- targets iOS e iPadOS em `apps/apple`.

Não exibir o botão de download inicialmente em Android TV, Fire TV, Apple TV, Samsung, LG, Titan OS ou portal web. Windows e macOS dependem de ADR futuro.

## 3. Elegibilidade e limites

O botão `Baixar` só aparece quando:

1. a fonte foi configurada legalmente pelo usuário;
2. o item é VOD armazenável;
3. a plataforma oferece API apropriada;
4. a autorização permite uso offline;
5. existe espaço suficiente;
6. não é necessário contornar proteção, autenticação ou expiração.

É proibido:

- remover proteção ou criptografia;
- ignorar validade de licença;
- capturar tela como fallback;
- exportar ou compartilhar arquivos;
- escrever conteúdo em pasta pública;
- registrar URL, senha, token, cookie ou chave;
- tratar TV ao vivo como download;
- transformar o app em downloader genérico.

Mensagem padrão:

```text
Este conteúdo está disponível somente para reprodução online.
```

## 4. Casos de uso

### Filme

```text
[Assistir] [Baixar] [+ Minha BURO]
```

A confirmação mostra qualidade, tamanho estimado, áudio, legenda, espaço livre, política de rede e eventual validade.

### Episódio

Estados visuais:

```text
Disponível
Na fila
Baixando
Pausado
Concluído
Expirando
Falhou
Indisponível
```

### Temporada

`Baixar temporada` cria um job independente por episódio. O sistema deve:

- identificar episódios elegíveis;
- estimar tamanho total;
- avisar quais episódios não podem ser baixados;
- limitar concorrência;
- pausar ou cancelar por episódio ou lote;
- retomar somente o que falta;
- impedir duplicados.

### Smart Downloads

Recurso opt-in e desligado por padrão:

- baixar próximo episódio;
- Wi-Fi por padrão;
- manter limite configurável;
- remover assistidos apenas com política aceita;
- respeitar perfil, Kids, espaço e licença.

## 5. Biblioteca Downloads

Filtros:

```text
Baixando
Concluídos
Pausados
Com problema
Expirando
```

Cada item mostra capa, título, episódio, progresso, tamanho, qualidade, faixas, estado e ações.

Sem rede, o app deve abrir a biblioteca imediatamente, reproduzir itens válidos, manter progresso local e sincronizar quando a conexão voltar.

## 6. Máquina de estados

```text
CREATED
ELIGIBILITY_CHECKING
READY
QUEUED
WAITING_FOR_NETWORK
WAITING_FOR_STORAGE
WAITING_FOR_LICENSE
DOWNLOADING
PAUSED
VERIFYING
COMPLETED
EXPIRING
EXPIRED
FAILED_RETRYABLE
FAILED_FINAL
CANCELLED
REMOVING
REMOVED
```

Jobs persistem após encerramento do processo ou reinício do aparelho.

## 7. Componentes

```text
OfflineEligibilityResolver
OfflineMediaAdapter
OfflineDownloadCoordinator
OfflineDownloadRepository
OfflineLicenseManager
OfflineStorageManager
OfflineTrackSelector
OfflineNetworkPolicy
OfflineQueueScheduler
OfflinePlaybackResolver
OfflineIntegrityVerifier
OfflineProgressSynchronizer
SeasonDownloadPlanner
SmartEpisodeDownloads
```

O domínio não depende de Activity, ViewController ou tela.

## 8. Android mobile

Base técnica:

- Media3 `DownloadService`;
- Media3 `DownloadManager` e `DownloadIndex`;
- Room para metadados;
- serviço em foreground quando exigido;
- armazenamento privado;
- Keystore para segredos;
- constraints de rede;
- restauração automática de jobs;
- integração com `ConnectionBudgetManager`.

Regras:

- I/O, hashing e parsing fora da main thread;
- Wi-Fi por padrão;
- concorrência conservadora;
- espaço reservado antes do início;
- parciais cancelados são limpos;
- playback ativo tem prioridade.

## 9. iPhone e iPad

Base técnica:

- `AVAssetDownloadURLSession`;
- `AVAssetDownloadTask` ou API oficial equivalente;
- AVFoundation/AVPlayer;
- background session;
- Keychain;
- armazenamento administrado pelo app/sistema;
- restauração de tasks após relançamento.

Usar somente formatos oficialmente elegíveis e licenças offline autorizadas.

## 10. Qualidade, áudio e legenda

Políticas:

```text
AUTO_RECOMMENDED
ECONOMY
HD
FULL_HD
BEST_AVAILABLE
CUSTOM_TRACKS
```

O tamanho estimado considera duração, bitrate, vídeo, áudio, legendas e margem de segurança. O app não pode trocar silenciosamente o idioma escolhido.

## 11. Armazenamento

Configurações:

- limite do Vault;
- reserva mínima de espaço;
- somente Wi-Fi;
- qualidade padrão;
- apagar assistidos;
- Smart Downloads;
- consumo por perfil e série.

Ordem sugerida de limpeza automática:

1. parciais cancelados;
2. itens expirados;
3. episódios assistidos cobertos pela política;
4. Smart Downloads antigos.

Downloads manuais não são removidos sem consentimento ou política previamente aceita.

## 12. Integridade e playback

Antes de `COMPLETED`, verificar:

- componentes presentes;
- tamanho plausível;
- manifestos/segmentos;
- áudio e legenda solicitados;
- abertura local;
- licença quando aplicável.

Asset corrompido é isolado e pode ser baixado novamente de forma autorizada.

## 13. Falhas

Códigos mínimos:

```text
OFFLINE_SOURCE_EXPIRED
OFFLINE_NOT_ELIGIBLE
OFFLINE_STORAGE_LOW
OFFLINE_STORAGE_FULL
OFFLINE_NETWORK_POLICY_BLOCKED
OFFLINE_LICENSE_REQUIRED
OFFLINE_LICENSE_EXPIRED
OFFLINE_SEGMENT_MISSING
OFFLINE_LOCAL_ASSET_CORRUPT
OFFLINE_TRACK_UNAVAILABLE
OFFLINE_PLATFORM_REJECTED
```

Aplicar retry limitado, backoff e integração com o GDD 4.0. Nunca usar tentativa infinita nem bombardear a fonte.

## 14. Perfis e Kids

- downloads pertencem a um perfil;
- Kids continua aplicando classificação e PIN offline;
- logout não expõe conteúdo de outro usuário;
- progresso é local e sincroniza depois;
- remover perfil exige decisão sobre os downloads associados.

## 15. Privacidade

Telemetria pode registrar apenas formato, estado, bytes, duração, plataforma e código normalizado. Nunca registrar URLs, credenciais, headers, playlists, chaves ou licenças.

## 16. Fixtures e testes

Fixtures próprias, públicas ou autorizadas:

```text
progressive-small
hls-vod-multivariant
hls-vod-multiaudio
hls-vod-subtitles
expired-source
truncated-manifest
unavailable-episode
simulated-license-valid
simulated-license-expired
corrupt-local-asset
```

Testes obrigatórios:

- download completo;
- pausa e retomada;
- fechar/reabrir app;
- reiniciar aparelho;
- alternar Wi-Fi e dados;
- modo avião;
- espaço insuficiente;
- temporada parcialmente elegível;
- áudio e legenda;
- expiração e corrupção;
- troca de perfil;
- redaction de segredos;
- Smart Downloads.

## 17. Roadmap

1. auditoria de capabilities, segurança, storage e lifecycle;
2. vertical Android com um filme e modo avião;
3. episódios, temporadas, faixas e Smart Downloads no Android;
4. paridade iPhone/iPad;
5. licenças autorizadas, testes de caos, acessibilidade e otimização.

## 18. Definition of Done

O P0 estará pronto quando Android mobile e iPhone/iPad:

- compilarem em CI;
- baixarem, pausarem, retomarem e removerem conteúdo elegível;
- tratarem temporada como fila;
- reproduzirem em modo avião;
- restaurarem jobs após encerramento;
- respeitarem rede, espaço, perfil e Kids;
- não exportarem arquivos nem persistirem segredos indevidamente;
- passarem testes de conformidade e validação em hardware real;
- atualizarem o release manifest com evidências.
