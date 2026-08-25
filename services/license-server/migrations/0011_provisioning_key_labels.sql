-- Se o envio levou chaves de API, para o painel poder dizer que levou.
--
-- Rótulos, não valores. As chaves vivem apenas dentro do payload cifrado e saem
-- de lá para o aplicativo; o painel escreve e nunca lê, pelo mesmo motivo da
-- senha. Estas colunas guardam só um sim ou não, de modo que quem vendeu consiga
-- responder "sim, mandei a chave junto" sem que o banco passe a ser um cofre de
-- chaves de clientes.
--
-- Precisa existir como coluna porque o payload é esvaziado quando o aplicativo
-- confirma: depois disso não haveria de onde deduzir o que foi enviado.

ALTER TABLE device_provisioning ADD COLUMN has_metadata_key INTEGER NOT NULL DEFAULT 0;
ALTER TABLE device_provisioning ADD COLUMN has_critics_key INTEGER NOT NULL DEFAULT 0;
