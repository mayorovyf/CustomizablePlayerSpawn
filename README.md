# Customizable Player Spawn

Это мод для NeoForge, который меняет стартовую точку игрока. Вместо обычного спавна мод может поставить готовую структуру и поселить игрока прямо внутри нее. Подходит для стартовых островов, домов, лобби и любых других заготовок, которые лежат в `.nbt`.

Мод читает настройки из конфига и берет оттуда измерение, список биомов, путь к структуре и точку, к которой нужно привязать спавн. Структура может быть ванильной, может лежать в вашем моде, а может приходить из другого мода или датапака. После первого входа в мир мод один раз ставит структуру, сохраняет найденную точку и потом использует ее как общий стартовый спавн.

## Как пользоваться

Откройте файл `config/customizableplayerspawn-common.toml` и укажите нужную структуру в параметре `structureTemplate`. Если у вас своя `.nbt`, положите ее в `data/<namespace>/structure/<path>.nbt` или в сохранение мира по пути `generated/<namespace>/structures/<path>.nbt`. Если структура приходит из другого мода, достаточно прописать ее id в формате `modid:path`.

Для модпаков доступен отдельный вариант без датапаков и без пересборки `.jar`: положите файл в `config/customizableplayerspawn/structures/<name>.nbt` и укажите его через `externalStructureFile`. Такой файл будет доступен для любого нового мира сразу после установки сборки, игроку ничего вручную добавлять не нужно.

Теперь задайте точку спавна. Самый простой вариант это поставить внутри структуры блок и указать его id в `spawnMarkerBlock`. Если вы не хотите использовать блок, можно оставить внутри шаблона `structure_block` с режимом `DATA` и задать имя маркера через `dataMarker`. Если блок нужен только как отметка, мод может удалить его после размещения.

Когда игрок впервые зайдет в мир, мод найдет подходящее место, поставит структуру и перенесет его в сохраненную точку. Для dev-запуска сейчас по умолчанию включен тестовый вариант с кораблем Края и спавном рядом с рамкой, где лежат элитры.

## Примеры

Если вам нужен обычный стартовый дом в верхнем мире, конфиг может выглядеть так:

```toml
targetDimension = "minecraft:overworld"
allowedBiomes = ["minecraft:plains", "minecraft:forest"]
structureTemplate = "mymod:start_house"
externalStructureFile = ""
spawnMarkerBlock = "customizableplayerspawn:player_spawn_marker"
removeSpawnMarkerBlock = true
dataMarker = ""
searchRadius = 2048
searchAttempts = 128
placementY = -1
surfaceYOffset = 0
spawnOffsetX = 0
spawnOffsetY = 0
spawnOffsetZ = 0
spawnAngle = 0
```

Если структура приходит из другого мода и вы хотите использовать его собственный блок как точку спавна, можно настроить так:

```toml
targetDimension = "minecraft:overworld"
allowedBiomes = []
structureTemplate = "othermod:starter_camp"
externalStructureFile = ""
spawnMarkerBlock = "othermod:camp_spawn_marker"
removeSpawnMarkerBlock = false
dataMarker = ""
searchRadius = 0
searchAttempts = 1
placementY = 100
surfaceYOffset = 0
spawnOffsetX = 0
spawnOffsetY = 0
spawnOffsetZ = 0
spawnAngle = 90
```

Если в шаблоне уже есть `DATA` marker и отдельный блок ставить не хочется, подойдет такой вариант:

```toml
targetDimension = "minecraft:the_end"
allowedBiomes = []
structureTemplate = "minecraft:end_city/ship"
externalStructureFile = ""
spawnMarkerBlock = ""
removeSpawnMarkerBlock = true
dataMarker = "Elytra"
searchRadius = 0
searchAttempts = 1
placementY = 80
surfaceYOffset = 0
spawnOffsetX = 0
spawnOffsetY = -1
spawnOffsetZ = 1
spawnAngle = 180
```

Если вы собираете модпак и хотите положить структуру рядом с конфигом, настройка может выглядеть так:

```toml
targetDimension = "minecraft:overworld"
allowedBiomes = ["minecraft:plains"]
structureTemplate = "minecraft:end_city/ship"
externalStructureFile = "spawn.nbt"
spawnMarkerBlock = "customizableplayerspawn:player_spawn_marker"
removeSpawnMarkerBlock = true
dataMarker = ""
searchRadius = 1024
searchAttempts = 64
placementY = -1
surfaceYOffset = 0
spawnOffsetX = 0
spawnOffsetY = 0
spawnOffsetZ = 0
spawnAngle = 0
```

В этом случае файл нужно положить по пути `config/customizableplayerspawn/structures/spawn.nbt`. Параметр `externalStructureFile` имеет приоритет над `structureTemplate`.
