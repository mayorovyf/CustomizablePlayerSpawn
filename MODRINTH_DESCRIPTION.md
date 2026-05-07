# Modrinth Description

## English

Customizable Player Spawn is a config-driven spawn control mod for Forge, NeoForge and Fabric.

It is made for worlds and modpacks where the first spawn should happen in a prepared place: a starter ruin, house, island, lobby, intro area, or any other `.nbt` structure. The mod can place that structure, find the real spawn point inside it, save the result, and keep using the same assigned spawn later.

### What it can do

- place structures from mod resources, datapacks, other mods, world files, or loose `.nbt` files in `config/customizableplayerspawn/structures`
- use a marker block, a vanilla `DATA` structure marker, a relative position, or the structure center as the spawn anchor
- create one shared spawn for everyone or separate per-player spawn structures
- choose the target dimension and allowed biomes
- search by heightmap, by smart surface validation, or by fixed Y level
- check the structure footprint, dangerous blocks, fluids, tree overlap, and surface height difference
- clear the placement volume, fill support gaps, refresh lighting, and process markers after placement
- optionally protect generated spawn structures from blocks, placement, containers, explosions, fire, and fluids
- reload, validate, list, reset, and teleport to assigned spawns with `/cps`
- keep old `config/customizableplayerspawn-common.toml` setups working as a legacy fallback

### Profiles

The recommended setup is a profile file:

`config/customizableplayerspawn/profiles/<profile>.toml`

Loose structure files go here:

`config/customizableplayerspawn/structures/<structure>.nbt`

Minimal example:

```toml
schemaVersion = 2
id = "starter_ruin"
enabled = true
priority = 100
dimension = "minecraft:overworld"
structure = "config:starter_ruin.nbt"

[anchor]
mode = "MARKER_BLOCK"
markerBlock = "customizableplayerspawn:player_spawn_marker"
removeMarkerBlock = true
offsetX = 0
offsetY = 0
offsetZ = 0
angle = 0
fallback = "SAFE_NEARBY"

[placement]
strategy = "FLAT_FOOTPRINT"
surfaceSearchMode = "SMART"
searchRadius = 2048
searchAttempts = 128
maxSurfaceStep = 3
allowFluidsBelow = false
allowReplaceableAtFeet = true
allowReplaceableAtHead = true
forbiddenSurfaceBlocks = ["minecraft:lava", "minecraft:magma_block"]
```

If `config/customizableplayerspawn/settings.toml` has `selectedProfile`, that profile is used. If it is empty, the enabled valid profile with the highest `priority` is selected.

Issues and suggestions: https://github.com/mayorovyf/CustomizablePlayerSpawn/issues

## Русский

Customizable Player Spawn это конфигурируемый мод для Forge, NeoForge и Fabric, который управляет стартовым спавном игрока.

Он нужен для миров и сборок, где игрок должен появиться не на обычном ванильном спавне, а в подготовленном месте: руине, стартовом доме, острове, лобби, вступительной зоне или любой другой `.nbt` структуре. Мод ставит структуру, находит внутри неё реальную точку спавна, сохраняет результат и дальше использует уже назначенную позицию.

### Что умеет мод

- размещать структуры из ресурсов мода, датапаков, других модов, файлов мира или loose `.nbt` из `config/customizableplayerspawn/structures`
- брать точку спавна из блока-маркера, `DATA` marker, относительной позиции или центра структуры
- создавать одну общую структуру для всех игроков или отдельные структуры для каждого игрока
- выбирать измерение и список разрешённых биомов
- искать поверхность через heightmap, умную проверку площадки или фиксированную Y-высоту
- проверять footprint структуры, опасные блоки, жидкости, деревья и перепад высот
- очищать область размещения, заполнять пустоты под опорами, обновлять свет и обрабатывать маркеры после размещения
- включать защиту стартовых структур от ломания, установки блоков, контейнеров, взрывов, огня и жидкостей
- управлять модом командами `/cps`
- сохранять совместимость со старым `config/customizableplayerspawn-common.toml`

### Профили

Основной способ настройки:

`config/customizableplayerspawn/profiles/<profile>.toml`

Loose `.nbt` структуры кладутся сюда:

`config/customizableplayerspawn/structures/<structure>.nbt`

Минимальный пример:

```toml
schemaVersion = 2
id = "starter_ruin"
enabled = true
priority = 100
dimension = "minecraft:overworld"
structure = "config:starter_ruin.nbt"

[anchor]
mode = "MARKER_BLOCK"
markerBlock = "customizableplayerspawn:player_spawn_marker"
removeMarkerBlock = true
offsetX = 0
offsetY = 0
offsetZ = 0
angle = 0
fallback = "SAFE_NEARBY"

[placement]
strategy = "FLAT_FOOTPRINT"
surfaceSearchMode = "SMART"
searchRadius = 2048
searchAttempts = 128
maxSurfaceStep = 3
allowFluidsBelow = false
allowReplaceableAtFeet = true
allowReplaceableAtHead = true
forbiddenSurfaceBlocks = ["minecraft:lava", "minecraft:magma_block"]
```

Если в `config/customizableplayerspawn/settings.toml` указан `selectedProfile`, мод использует его. Если поле пустое, выбирается включённый валидный профиль с самым большим `priority`.

Ошибки и предложения: https://github.com/mayorovyf/CustomizablePlayerSpawn/issues
