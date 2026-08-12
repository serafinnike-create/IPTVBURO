# Perfil de partida (Baseline Profile)

Gera `apps/android-tv/src/release/generated/baselineProfiles/` — a lista de classes e métodos que a
partida do app realmente toca, para o dispositivo compilá-los na instalação em vez de interpretá-los
na primeira execução.

Este módulo **não entra no APK**. Ele existe só para dirigir o app num aparelho real e gravar o que
a execução toca.

## Quando regerar

O perfil envelhece junto com o código. Vale regerar quando:

- a partida mudar de forma relevante (boot, home, splash, licenciamento);
- uma biblioteca do caminho de partida for atualizada (Compose, Media3, Room, Hilt);
- antes de uma release que valha a pena medir.

Não é preciso regerar a cada commit. Um perfil desatualizado não quebra nada — ele apenas deixa de
cobrir o que mudou.

## Como gerar

Com **um** aparelho conectado (não emulador, e sem outros dispositivos no `adb devices`):

```powershell
./gradlew :apps:android-tv:generateBaselineProfile
```

Leva cerca de 6 minutos: compila uma variante de release sem R8, instala, executa o percurso e
grava o resultado dentro de `apps/android-tv/src/release/`. O arquivo gerado **é versionado** — é
ele que viaja no APK, e a geração não roda no CI.

## Como medir se valeu

Comparar o mesmo APK com e sem o perfil aplicado, no aparelho:

```powershell
$pkg = "com.lucasserafin94.iptvburo"
adb install -r apps/android-tv/build/outputs/apk/nonMinifiedRelease/android-tv-nonMinifiedRelease.apk

# sem perfil
adb shell cmd package compile -f -m verify $pkg
adb shell am force-stop $pkg; adb shell am start -W -n "$pkg/com.lucasserafin94.iptvburo.MainActivity"

# com perfil
adb shell cmd package compile -f -m speed-profile $pkg
adb shell am force-stop $pkg; adb shell am start -W -n "$pkg/com.lucasserafin94.iptvburo.MainActivity"
```

Medido no Xiaomi 25028RN03Y em 12 de agosto de 2026, três execuções de cada:

| | sem perfil | com perfil |
| --- | --- | --- |
| execuções (ms) | 548 / 401 / 379 | 379 / 377 / 384 |
| mediana | 401 ms | 379 ms |
| primeira partida | 548 ms | 379 ms |
| frames pulados | — | nenhum |

O ganho na mediana é modesto; o ganho real está na **primeira** partida depois de instalar, que é
justamente quando nada está compilado — 548 ms para 379 ms.

## Um número que enganou

Antes disso, a partida foi medida em ~3.000 ms com `classloader create took 1152ms` e sequências de
120 frames pulados. **Era build debug.** No release esses dois sintomas desaparecem: a partida fica
em ~380 ms e o log não reporta frame pulado nenhum.

Debug não serve para medir partida. O APK não passa por R8, carrega classes de instrumentação e não
recebe o perfil. Qualquer medição de desempenho deve usar `nonMinifiedRelease` ou `release`.
