# IPTV BURO — GDD 5.0: Universal Multiplatform Delivery

**Versão:** 5.0  
**Data:** 31 de julho de 2026  
**Status:** extensão obrigatória dos GDDs 1.0 a 4.0  
**Objetivo:** transformar o IPTV BURO em um único produto consistente, entregue por aplicações adequadas a cada família de sistema.

---

## 1. Regra de produto

O IPTV BURO não é exclusivamente Android TV.

O escopo comercial final inclui:

- Android TV e Google TV;
- televisores Sony e Philips que utilizam Android/Google TV;
- Amazon Fire TV;
- celulares e tablets Android;
- Apple TV, iPhone, iPad e macOS;
- Samsung Smart TV com Tizen;
- LG Smart TV com webOS;
- Philips e parceiros com Titan OS;
- Windows;
- portal web para ativação, licença, dispositivos e gerenciamento.

O usuário deve reconhecer o mesmo produto em todos esses aparelhos: mesma marca, catálogo, perfis, regras de organização, mensagens, configurações e modelo de licença.

> Um único produto não significa um único binário. Cada ecossistema exige UI, player, armazenamento, ciclo de vida, pacote e distribuição próprios.

---

## 2. Princípios obrigatórios

1. **Uma especificação:** todos os aplicativos obedecem aos mesmos GDDs.
2. **Domínio compartilhado:** catálogo, datas, falhas, perfis e licenças não podem divergir silenciosamente.
3. **Player nativo:** usar o mecanismo adequado a cada plataforma.
4. **Design consistente, não clonagem:** preservar a identidade BURO sem copiar trade dress de Netflix, Apple TV, Prime Video ou HBO.
5. **Paridade verificável:** toda diferença precisa estar registrada como capability ou limitação.
6. **Evolução incremental:** Android TV é a referência inicial, não o limite final.
7. **Verdade operacional:** diretório criado não significa aplicação pronta.
8. **Legalidade e privacidade:** nenhum adapter pode contornar DRM, autorização, TLS ou restrições de origem.

---

## 3. Arquitetura de aplicações

```text
apps/
├─ android-tv/       # Android TV, Google TV e base Fire TV
├─ android-mobile/   # Android celular e tablet
├─ apple/            # tvOS, iOS, iPadOS e macOS
├─ samsung-tizen/    # Samsung Smart TV
├─ lg-webos/         # LG Smart TV
├─ titan-tv/         # Philips Titan OS
├─ windows/          # Windows
└─ web-portal/       # ativação e gerenciamento

packages/
├─ contracts/
├─ design-tokens/
├─ schemas/
├─ fixtures/
├─ localization/
├─ conformance-tests/
└─ release-manifest/
```

A estrutura pode evoluir por ADR, mas a separação entre domínio compartilhado e integrações nativas deve ser preservada.

---

## 4. Matriz técnica inicial

| Plataforma | UI | Player | Distribuição |
|---|---|---|---|
| Android TV / Google TV | Kotlin + Compose for TV | Media3/ExoPlayer | Google Play |
| Sony/Philips Android TV | mesma aplicação, validada por modelo | Media3/ExoPlayer | Google Play |
| Fire TV | variante Android TV | Media3 validado no Fire OS | Amazon Appstore |
| Android mobile | Kotlin + Compose | Media3/ExoPlayer | Google Play |
| Apple TV | SwiftUI tvOS | AVPlayer/AVFoundation | App Store |
| iPhone/iPad | SwiftUI | AVPlayer/AVFoundation | App Store |
| macOS | SwiftUI/AppKit quando necessário | AVPlayer/AVFoundation | Mac App Store/distribuição assinada |
| Samsung Tizen | TypeScript/HTML/CSS ou stack oficial | Samsung AVPlay | Samsung Apps TV |
| LG webOS | stack oficial webOS | pipeline webOS/HTML5 conforme capability | LG Content Store |
| Philips Titan OS | stack compatível com SDK oficial | player da plataforma | distribuição Titan |
| Windows | decisão por ADR e spike | PlayerAdapter nativo | Microsoft Store/instalador assinado |
| Portal web | TypeScript | gerenciamento, sem obrigação de playback | web |

Uma mudança de tecnologia exige ADR, protótipo e evidência de que melhora portabilidade, reprodução ou manutenção.

---

## 5. Paridade funcional obrigatória

Quando tecnicamente permitido, toda plataforma final deve incluir:

- onboarding, ativação e licença;
- perfis e preferências;
- fontes autorizadas pelo usuário;
- Home cinematográfica;
- TV ao vivo;
- filmes, séries, temporadas e episódios;
- busca;
- favoritos e Minha BURO;
- continuar assistindo;
- BURO Catalog Brain;
- BURO Temporal Intelligence;
- BURO Resilience Engine;
- seleção de áudio, legenda e qualidade;
- controle parental e Kids;
- configurações e acessibilidade;
- diagnóstico seguro;
- sincronização opcional;
- mesma política de classificação e privacidade.

### 5.1 Capabilities adaptativas

Devem ser declaradas por plataforma:

- Picture in Picture;
- multiview;
- DVR/timeshift;
- downloads offline;
- reprodução em background;
- integração com a Home do sistema;
- voz;
- HDR e áudio avançado;
- troca automática de refresh rate;
- thumbnails durante seek;
- controle por celular;
- downloads externos ou armazenamento removível.

A interface nunca deve exibir uma ação que a plataforma declarou como indisponível.

---

## 6. Contratos compartilhados

Cada implementação deve fornecer adapters equivalentes para:

```text
PlayerAdapter
PlatformCapabilities
CatalogRepository
TemporalClassifier
FailureNormalizer
RecoveryPlanner
LicenseClient
ProfileRepository
SyncClient
SecureCredentialStore
TelemetrySink
NavigationAdapter
PurchaseAdapter
DeepLinkAdapter
```

Os contratos precisam de:

- versão explícita;
- códigos estáveis;
- schemas neutros;
- fixtures compartilhadas;
- testes de conformidade;
- política de compatibilidade;
- documentação de diferenças.

A mesma fixture deve produzir resultados de domínio equivalentes em Kotlin, Swift e TypeScript.

---

## 7. Design system

A identidade BURO é compartilhada por tokens e regras, não pela cópia cega de widgets.

Fonte de verdade planejada:

```text
packages/design-tokens/tokens.json
```

Deve cobrir:

- cores semânticas;
- tipografia;
- espaçamento;
- raios;
- opacidade;
- escalas de foco;
- animações;
- performance tiers;
- alto contraste;
- redução de movimento e transparência;
- tamanhos mínimos por distância de visualização.

A implementação pode adaptar controles nativos, safe areas, gesto de voltar, foco, toque, mouse e teclado sem perder a identidade central.

---

## 8. Release manifest

O estado verificável das plataformas deve ficar em:

```text
packages/release-manifest/platforms.json
```

Estados permitidos:

```text
NOT_STARTED
SCAFFOLDED
BUILDING
TESTING
HARDWARE_VALIDATION
STORE_PREPARATION
RELEASE_CANDIDATE
RELEASE_READY
PUBLISHED
BLOCKED
```

Uma plataforma só pode avançar quando houver evidência correspondente. O manifesto deve registrar build, testes, hardware, loja, versão e limitações conhecidas.

---

## 9. CI/CD

Pipelines planejados:

```text
Android → Linux + JDK + Android SDK
Apple → macOS + Xcode
Samsung → Tizen Studio/CLI
LG → webOS CLI
Titan → toolchain oficial
Windows → Windows runner
Portal → Node.js
Contratos → testes neutros
```

Cada pipeline deve executar, quando aplicável:

- lint;
- testes unitários;
- testes de contrato;
- build de desenvolvimento;
- varredura de segredos;
- relatório de capabilities;
- assinatura somente com segredos protegidos;
- publicação somente após gates de release.

---

## 10. Validação em hardware

Emulador não é suficiente para `RELEASE_READY`.

Matriz mínima:

- Android TV de entrada, intermediária e premium;
- Sony Android/Google TV;
- Philips Android/Google TV;
- Fire TV de entrada;
- Samsung Tizen de mais de uma geração;
- LG webOS de mais de uma geração;
- Apple TV física;
- iPhone e iPad físicos;
- Mac Apple Silicon e Intel enquanto suportado;
- Windows com GPU integrada e dedicada;
- Titan OS quando o ambiente oficial estiver disponível.

Testes mínimos:

- instalação e atualização;
- foco, toque, mouse, teclado e controle remoto;
- sessão longa de reprodução;
- troca rápida de conteúdo;
- background/foreground;
- perda e retorno de rede;
- áudio, legendas e HDR;
- memória e temperatura;
- logout, revogação e restauração;
- recuperação após falha.

---

## 11. Ordem de entrega

1. **Referência Android TV:** consolidar player, catálogo, UX e testes.
2. **Ecossistema Android:** Android mobile e Fire TV.
3. **Ecossistema Apple:** Apple TV, iPhone, iPad e macOS.
4. **Fabricantes de TV:** Samsung, LG e Titan OS.
5. **Desktop e operação:** Windows, portal, licenças e releases.

A ordem reduz risco, mas não altera o escopo final.

---

## 12. Definition of Final Product

O IPTV BURO não pode ser chamado de produto final apenas porque a versão Android TV funciona.

A versão comercial completa exige:

1. pacotes instaláveis para todas as plataformas declaradas como suportadas;
2. paridade das funções essenciais;
3. players validados por ecossistema;
4. pipelines independentes;
5. testes em hardware real;
6. requisitos das lojas atendidos;
7. conta/licença consistente;
8. atualização e rollback;
9. relatório de capabilities;
10. segurança e privacidade aprovadas.

---

## 13. Regras para Codex e outros agentes

- continuar o código existente; nunca reiniciar sem auditoria e ADR;
- não converter o produto em app exclusivamente Android;
- não usar WebView como solução universal automática;
- não compartilhar um player inadequado apenas para reduzir código;
- não duplicar regras de catálogo, datas ou falhas;
- não marcar plataforma como pronta sem evidências;
- não esconder limitações;
- preservar builds funcionais;
- implementar uma vertical slice por vez;
- atualizar o release manifest somente com fatos.

---

## 14. Critérios de aceitação

O GDD 5.0 estará aplicado quando:

- a arquitetura multiplataforma estiver registrada por ADR;
- contratos e schemas compartilhados existirem;
- o release manifest representar o estado real;
- o Android TV continuar compilando;
- novas funções indicarem suporte por plataforma;
- o Codex receber um prompt de continuidade universal;
- nenhuma plataforma futura precisar reescrever as regras centrais de catálogo, datas e falhas.
