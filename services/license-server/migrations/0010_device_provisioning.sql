-- Configurar a lista de um cliente de longe, pelo painel.
--
-- O problema é de suporte, não técnico. Quem vende IPTV vende para gente que
-- muitas vezes não sabe cadastrar um servidor Xtream — três campos, um deles uma
-- senha, digitados no controle remoto de uma televisão. Não dá para ir à casa da
-- pessoa, e ditar uma senha por telefone termina em erro de digitação.
--
-- O fluxo é o que o dono do produto descreveu: o cliente abre o aplicativo, lê o
-- código do aparelho na tela e manda por mensagem; o painel busca esse código,
-- preenche endereço, usuário e senha, e aplica; o cliente fecha e abre, e a lista
-- está lá. Quando o endereço do provedor cai, repete-se o mesmo gesto.
--
-- Por que o código importa mais do que parece: ele é o consentimento. Só é
-- alcançado o aparelho cuja pessoa leu o código na própria tela e o enviou. Quem
-- usa o aplicativo com outra operadora nunca aparece aqui, e não é preciso
-- inventar uma tela de vínculo para garantir isso.
--
-- O que esta tabela deliberadamente NÃO é: um cofre de credenciais de clientes.
-- A senha continua vivendo no KeyManager da televisão, que é de onde ela nunca
-- saiu. Aqui ela fica de passagem, entre o "aplicar" do painel e a próxima
-- abertura do aplicativo, e é apagada quando chega. O painel escreve e nunca lê:
-- para trocar um endereço que caiu não é preciso recuperar o antigo. Assim um
-- vazamento deste banco continua não entregando lista de ninguém.

CREATE TABLE IF NOT EXISTS device_provisioning (
  device_id TEXT PRIMARY KEY,

  -- O que a televisão vai aplicar, cifrado com a mesma AES-GCM do pareamento.
  -- Guardado cifrado porque fica em repouso por algum tempo: entre o painel e a
  -- próxima abertura podem passar dias, se a pessoa não abrir o aplicativo.
  payload TEXT NOT NULL,
  payload_nonce TEXT NOT NULL,

  -- O endereço e o usuário, em claro, só para o painel poder mostrar o que foi
  -- aplicado sem decifrar nada. A senha não tem coluna própria de propósito: ela
  -- existe apenas dentro de `payload`, e sai de lá para a televisão.
  server_label TEXT,
  username_label TEXT,

  -- PENDING enquanto espera a televisão; APPLIED quando ela confirmou. Um
  -- registro APPLIED mantém apenas os rótulos: o payload é esvaziado na
  -- confirmação, porque a partir dali ele só seria risco.
  state TEXT NOT NULL DEFAULT 'PENDING',

  created_at TEXT NOT NULL,
  created_by TEXT NOT NULL,
  applied_at TEXT,

  -- Uma tentativa que falha não pode virar um laço: a televisão que não consegue
  -- aplicar tenta de novo na próxima abertura, e depois de algumas vezes o
  -- painel precisa saber que algo está errado com aquele endereço.
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT
);

-- O painel lista o que está pendente para saber a quem cobrar uma reabertura.
CREATE INDEX IF NOT EXISTS device_provisioning_by_state
  ON device_provisioning (state, created_at);
