-- O nome que a lista vai ter no aplicativo do cliente.
--
-- Sem isto o aplicativo deriva o nome do endereço — "cb.visualplay.online" — que
-- é como o servidor se chama e não como quem vendeu quer que o cliente veja a
-- lista. Quem vende agora escolhe o nome no mesmo formulário.
--
-- Coluna própria porque o painel precisa mostrar o que foi enviado depois de o
-- aplicativo confirmar, e nesse momento o payload cifrado já foi esvaziado. Um
-- rótulo não é segredo: aparece na tela do cliente, ao contrário da senha e das
-- chaves, que continuam existindo apenas dentro do payload.

ALTER TABLE device_provisioning ADD COLUMN list_label TEXT;
