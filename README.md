# Sussurros do Vazio

Mod de **terror psicológico** para **Minecraft Java 26.3** com **Fabric**.

Não tem monstros novos nem dano direto. O medo vem de um **sistema de sanidade** e de **36 eventos** que fazem você duvidar do que vê e do que ouve: passos atrás de você, portas que abrem sozinhas, tochas que se apagam, mensagens de jogadores que não existem e figuras que só se mexem quando você não está olhando.

## Como instalar (jogar)

1. Instale o **Fabric Loader 0.19.5 ou mais novo** para o Minecraft **26.3** (https://fabricmc.net/use/installer/).
2. Coloque na pasta `mods` do jogo:
   - o **Fabric API** `0.162.0+26.3`
   - o jar deste mod: `sussurros-1.0.0.jar`
3. Precisa de **Java 25** (o launcher oficial já usa).

Funciona em mundo single player e em servidor dedicado. No servidor, quem quiser ouvir os sons customizados também precisa ter o mod instalado; os eventos com sons do jogo funcionam mesmo sem ele.

## Como gerar o jar (este pacote é o código-fonte)

**Opção A, no seu PC**
1. Instale o **JDK 25** (por exemplo Temurin ou Microsoft Build of OpenJDK 25).
2. Abra um terminal nesta pasta e rode:
   - Windows: `gradlew.bat build`
   - Linux/Mac: `./gradlew build`
3. O jar fica em `build/libs/sussurros-1.0.0.jar`. Use o arquivo **sem** `-sources` no nome.

**Opção B, sem instalar nada: o GitHub compila para você**
1. Crie uma conta e um repositório novo no GitHub (botão **New repository**, pode ser privado).
2. Envie **todo o conteúdo** desta pasta para o repositório (não o zip fechado). Pelo navegador: **Add file > Upload files** e arraste os arquivos e pastas já extraídos. Com o git instalado, é só:
   ```
   git init
   git add .
   git commit -m "Sussurros do Vazio"
   git branch -M main
   git remote add origin https://github.com/SEU_USUARIO/SEU_REPOSITORIO.git
   git push -u origin main
   ```
3. Abra a aba **Actions**. A compilação começa sozinha (ou use **build > Run workflow**) e leva alguns minutos na primeira vez.
4. Quando ficar verde, clique na execução, desça até **Artifacts** e baixe **sussurros-mod**. Dentro do zip está o `sussurros-1.0.0.jar`.

Se a aba Actions não mostrar nenhum workflow, a pasta oculta `.github` não foi enviada. Crie o arquivo pelo site: **Add file > Create new file**, digite `.github/workflows/build.yml` no nome e cole o conteúdo do `build.yml` deste pacote.

Se a compilação ficar vermelha, abra a execução, clique no passo **build** e copie as linhas que começam com `error:`.

## Como o terror funciona

- **Sanidade (0 a 100).** Cai no escuro, em cavernas fundas e no Nether/End. Sobe com luz, durante o dia e dormindo. Cada evento também deixa você um pouco mais paranoico.
- **Estágios.** Quanto menor a sanidade, mais pesados os eventos e menor o intervalo entre eles (cerca de 3,5 min com a mente sã, cerca de 40 s no limite).
- **Tempo de graça.** Nos primeiros 4 minutos depois de entrar no mundo não acontece nada.
- **Criativo e espectador** não são afetados (dá para mudar na config).

### Os 36 eventos

| Estágio | Eventos |
|---|---|
| 0, sutil | `whisper`, `breath`, `footsteps`, `knock`, `creak`, `scratch`, `mining_walls`, `cave_drone`, `thunder_far`, `whisper_text`, `fake_chat`, `sleep_whispers` |
| 1, inquieto | `musicbox`, `distant_cry`, `growl`, `fake_creeper`, `bell`, `explosion_far`, `fake_join`, `fake_advancement`, `blink`, `darkness_pulse`, `torch_snuff`, `watcher` |
| 2, assustado | `chorus`, `static`, `nausea`, `paralysis`, `self_chat`, `fake_death`, `storm`, `heartbeat`, `ambush`, `follower` |
| 3, pânico | `dread_title`, `jumpscare` |

Além disso, **minerar diamante, esmeralda ou detrito antigo** às vezes chama a atenção de algo ("A cobiça tem um preço.").

Destaques:
- **watcher**: um Enderman ou aldeão parado ao longe te encarando. Some se você o encara ou chega perto.
- **ambush**: aparece colado atrás de você. Some, com um susto, assim que você se vira.
- **follower**: só se mexe quando você **não** está olhando. Se chegar até você, susto.
- **torch_snuff**: as tochas ao redor se apagam uma a uma, da mais próxima para a mais distante.
- **self_chat**: uma mensagem no chat com o **seu** nome, que você não escreveu.
- **fake_join / fake_advancement / fake_death**: avisos falsos de um jogador que não existe.

As figuras são invulneráveis, mudas, sem IA e **não atacam**. Elas só existem alguns segundos e são removidas sozinhas.

Os 14 sons customizados (sussurros, respiração, batimentos, batidas, caixinha de música, estática, drone, choro, riscos, rosnado e susto) foram sintetizados para o mod e já estão dentro do jar.

## Comandos de teste

Todos afetam só quem digita.

| Comando | O que faz |
|---|---|
| `/horror status` | mostra a sanidade, o estágio e o próximo evento |
| `/horror list` | lista os eventos |
| `/horror event <id>` | dispara um evento agora (`random` sorteia um) |
| `/horror sanity <0-100>` | define a sua sanidade |
| `/horror calm` | restaura a sanidade e remove aparições |
| `/horror on` / `off` | liga ou desliga os eventos só para você |
| `/horror cleanup` | remove figuras que tenham sobrado no mundo |
| `/horror reload` | recarrega a configuração |

Dica para ver tudo rápido: `/horror sanity 20` e depois `/horror event follower`.

## Configuração

Arquivo `config/sussurros.properties`, criado na primeira execução. Dá para ajustar:
- `intensity`: frequência dos eventos
- `grace_minutes`: tempo sem eventos ao entrar no mundo
- `language`: `pt` ou `en`
- `fake_chat`, `world_tampering`, `apparitions`, `jumpscares`: ligam e desligam cada tipo
- `sanity_drain`: velocidade com que a sanidade cai
- `enabled`: desliga o mod inteiro

Se algo te incomodar, `jumpscares=false` e `apparitions=false` deixam o mod só com sons e texto.

## Por que ele foi feito para não dar erro ao carregar

- **Sem mixins e sem registros novos** (nenhum bloco, item ou entidade): o jogo não tem nada para recusar no carregamento do mundo.
- Quase tudo usa **comandos vanilla** (`playsound`, `effect`, `tellraw`, `summon`...), que quase não mudam entre versões.
- Poucas chamadas internas, só as verificadas no código do Fabric API 26.3, e as duas menos certas são feitas por reflexão com valor reserva.
- **Cada evento roda dentro de um `try/catch`**: se um falhar, vira só um aviso no log e o jogo segue.
- Sons ausentes ou comandos que falham nunca derrubam o jogo.

Versões usadas (do `fabric-example-mod`, branch 26.3): Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18-SNAPSHOT, Gradle 9.7.1, Java 25.
