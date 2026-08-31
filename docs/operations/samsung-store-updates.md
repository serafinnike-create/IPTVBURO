# Atualizar o aplicativo depois de publicado na loja Samsung

Este documento responde à pergunta feita em 2026-08-29: *é possível atualizar o
app depois de postá-lo na loja da Samsung?*

Sim. Mas há três coisas que mudam decisões de produto, e uma delas contraria o
que se costuma supor.

**O que está aqui foi lido da documentação oficial da Samsung**, com as fontes no
fim. Nada foi verificado no portal da conta — quando o portal disser outra coisa,
o portal está certo e este documento está velho.

---

## A TV não atualiza sozinha

Esta é a parte que importa mais, e é contraintuitiva: **o cliente recebe um aviso
e escolhe se atualiza**. Não há instalação silenciosa. Quem recusar continua na
versão antiga por tempo indeterminado.

Isso tem uma consequência direta no desenho do produto: **uma correção não chega
a quem não quiser recebê-la**, a menos que se use o mecanismo abaixo.

### Atualização obrigatória

A Samsung permite marcar uma versão como obrigatória: o aplicativo **não abre**
até o cliente aceitar. A própria documentação recomenda usá-la com parcimônia, e
a razão é evidente — é a diferença entre um aviso e uma porta trancada.

Quando faz sentido usar: um defeito que corrompe dados, um problema de segurança,
ou uma quebra de compatibilidade com o servidor de licença. Quando não faz: uma
melhoria de interface, por mais desejável que pareça.

---

## O que o pacote precisa ter

Três coisas precisam bater com a versão publicada, e cada uma sozinha impede o
registo:

**O mesmo certificado de autor.** Um pacote assinado com outro certificado não é
tratado como uma atualização — é recusado. Perder o certificado significa não
poder mais atualizar aquele aplicativo, o que faz do backup dele um assunto sério.

**O mesmo Tizen ID / Package ID.** É a identidade do aplicativo na loja.

**Uma `version` maior**, em `config.xml`. Não pode repetir nem baixar.

---

## O caminho, do pacote ao ar

1. Empacotar a versão nova
2. Subir em *Applications > App Package* — pré-testes automáticos rodam aqui
3. Ajustar o que mudou: imagens, descrição, funcionalidades
4. Pedir a publicação em *Applications > Distribute* — pré-testes de novo
5. **Revisão e teste de verificação da Samsung**
6. Publicação para os grupos de modelos escolhidos

**Não é possível submeter uma versão nova enquanto o serviço do aplicativo estiver
parado.**

### Quanto tempo leva

**A documentação não diz.** Ela descreve as etapas e menciona que pedidos de
encerramento levam 2 a 3 dias, mas não dá prazo para a revisão de uma
atualização.

Isso significa planejar sem depender de uma data: uma correção urgente não tem
prazo garantido, e a única coisa sob controle é submetê-la cedo.

---

## Quando uma versão sai com defeito grave

**Não há um caminho de emergência documentado.** O que existe é pedir um
*downgrade* por chamado de suporte, que a documentação estima em cerca de **dois
dias**.

Dois dias com um defeito grave no ar é muito tempo. A conclusão prática é que a
prevenção vale mais do que o remédio aqui: **testar numa TV física antes de
submeter** é a única coisa que evita esse cenário, e é justamente o que ainda não
foi feito neste projeto (ver `packages/platform-capabilities/samsung-tizen.json`,
que registra que nenhuma capability foi validada em hardware real).

---

## O que este documento não responde

- **Prazo real de revisão.** Só a conta do vendedor mostra, e varia.
- **Se existe caminho acelerado** para defeito crítico. Não está documentado
  publicamente; se existir, é por chamado.
- **Comportamento exato do aviso ao cliente** — com que frequência reaparece se
  ele recusar.

Estas três só se respondem com uma submissão real ou com o suporte da Samsung.

---

## Fontes

- [Application Update Q&A](https://developer.samsung.com/tv-seller-office/faq/application-update.html)
- [Application Publication Process](https://developer.samsung.com/tv-seller-office/application-publication-process.html)
- [Distributing Applications](https://developer.samsung.com/tv-seller-office/guides/applications/distributing-application.html)
- [Registering Applications](https://developer.samsung.com/tv-seller-office/guides/applications/registering-application.html)

Lido em 2026-08-30.
