# IPTV BURO — GDD / PRD Técnico

## 24. FLUXOS PRINCIPAIS

### 24.1 Primeiro uso

1. abrir;
2. selecionar idioma;
3. aceitar termos;
4. gerar Device ID;
5. iniciar trial;
6. escolher fonte;
7. validar;
8. importar;
9. criar perfil;
10. abrir Home.

### 24.2 Compra

1. usuário vê dias restantes;
2. seleciona “Ativar permanentemente”;
3. escolhe método permitido pela plataforma;
4. paga;
5. backend valida;
6. entitlement atualizado;
7. app recebe atualização;
8. mensagem de sucesso;
9. restauração disponível.

### 24.3 VOD

1. selecionar filme;
2. abrir detalhes;
3. reproduzir;
4. player detecta capabilities;
5. salvar progresso;
6. sair;
7. item aparece em Continuar assistindo.

### 24.4 TV ao vivo

1. abrir TV;
2. carregar último canal ou guia;
3. selecionar canal;
4. iniciar vídeo;
5. exibir EPG;
6. zapping;
7. salvar recente.

### 24.5 Falha de stream

1. detectar;
2. classificar;
3. retentar;
4. trocar estratégia;
5. informar usuário;
6. oferecer detalhes;
7. nunca entrar em loop.

---
## 25. CONFIGURAÇÕES

### Geral

- idioma;
- tema;
- inicialização;
- perfil padrão;
- atualização;
- economia de dados;
- redução de movimento.

### Playback

- buffer;
- decoder;
- auto frame rate;
- qualidade;
- áudio;
- legenda;
- proporção;
- seek step;
- player alternativo;
- timeout;
- reconexão.

### TV

- ordem de canais;
- grupos;
- números;
- EPG;
- zapping;
- último canal;
- confirmação de canal adulto.

### VOD

- autoplay;
- próximo episódio;
- trailer;
- continuar;
- pular abertura;
- créditos.

### Privacidade

- telemetria;
- limpar histórico;
- limpar cache;
- exportar dados;
- apagar conta;
- sync.

---
## 26. RECURSOS FORA DO MVP

Não implementar antes de estabilizar o núcleo:

- chatbot;
- rede social;
- comentários;
- compartilhamento de playlists;
- marketplace de provedores;
- VPN embutida;
- proxy de vídeo;
- transcodificação;
- DRM próprio;
- publicidade;
- sistema de afiliados;
- gravação cloud;
- IA generativa cloud;
- suporte a todos os sistemas de TV simultaneamente.

---
## 27. TESTES

### 27.1 Unitários

- parser M3U;
- parser XMLTV;
- normalização;
- metadata matching;
- regras parentais;
- licença;
- trial;
- progress;
- deduplicação;
- migrações.

### 27.2 Integração

- fonte grande;
- fonte lenta;
- URL expirada;
- autenticação incorreta;
- EPG inválido;
- mudança de playlist;
- webhook duplicado;
- refund;
- restore;
- transferência.

### 27.3 Playback

- HLS live;
- HLS VOD;
- MP4 progressivo;
- MPEG-TS;
- áudio múltiplo;
- legenda;
- HDR;
- 4K;
- stream sem seek;
- janela DVR;
- rede instável;
- Wi-Fi desconectado;
- servidor 401/403/404/429/5xx.

### 27.4 TV UX

- D-pad;
- foco;
- voltar;
- rolagem longa;
- controle sem touch;
- overscan;
- fontes;
- acessibilidade;
- memória;
- suspensão;
- retomada;
- troca de perfil.

### 27.5 Matriz de hardware

- box Android TV fraco;
- Chromecast/Google TV;
- Sony BRAVIA;
- Fire TV;
- Samsung Tizen antigo suportado;
- Samsung Tizen recente;
- LG webOS;
- Windows Intel/AMD/NVIDIA;
- macOS Intel/Apple Silicon;
- iPhone/iPad;
- Apple TV.

---
## 28. CRITÉRIOS DE ACEITAÇÃO DO MVP

O MVP só pode ser chamado de pronto quando:

- uma playlist M3U válida é importada;
- uma conta Xtream válida é importada;
- catálogo abre do cache;
- EPG funciona;
- TV ao vivo reproduz;
- VOD reproduz;
- seek funciona quando a fonte é seekable;
- controle informa quando seek não é possível;
- áudio e legenda podem ser selecionados quando disponíveis;
- favoritos persistem;
- progresso persiste;
- perfis funcionam;
- perfil infantil oculta conteúdo;
- trial funciona;
- pagamento único funciona;
- restore funciona;
- credenciais não aparecem em logs;
- app funciona integralmente por D-pad;
- há testes automatizados;
- há relatório de falha de playback;
- não existe conteúdo pré-carregado.
