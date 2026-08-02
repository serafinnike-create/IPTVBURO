# Auditoria de falhas de reprodução

- Data: 2 de agosto de 2026
- Escopo: Android adaptativo e preview Windows

| Caso | Android | Windows | Estado |
|---|---|---|---|
| URL resolvida tardiamente | Xtream em memória | Xtream em memória | existente |
| loading/primeiro frame | eventos Media3 | eventos JavaFX | existente |
| play, pause e volume | real | real | existente |
| seek e velocidade | conforme mídia | seek; velocidade limitada pelo backend | parcial |
| áudio e legenda | seletor Media3 quando há faixas | não há seletor amplo | parcial/bloqueante |
| orientação/PiP | mobile adaptativo e PiP | não aplicável | existente |
| HDR/SDR | capacidade do decoder/track | não comprovado | não expor toggle |
| HEVC/Dolby Vision | depende do dispositivo | fora da garantia JavaFX | bloqueante Windows |
| retry/circuit breaker | mensagem normalizada básica | mensagem básica | GDD 4 pendente |
| segredo em diagnóstico | URL não exibida | URI não registrada | existente, auditar sempre |

Conclusão: Android possui um vertical reproduzível, mas a matriz completa ainda
precisa de hardware e amostras públicas. Windows não deve ser declarado player
AAA enquanto o adapter nativo e a seleção de faixas não estiverem concluídos.
