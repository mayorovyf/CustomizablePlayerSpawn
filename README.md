# Customizable Player Spawn

Это мод для Forge, который меняет стартовую точку игрока. Вместо обычного спавна мод может поставить готовую структуру и поселить игрока прямо внутри нее, либо просто перенести игрока в нужное измерение без генерации структуры. Подходит для стартовых островов, домов, лобби и любых других заготовок, которые лежат в `.nbt`.

Мод читает настройки из конфига и берет оттуда измерение, список биомов, путь к структуре и точку, к которой нужно привязать спавн. Структура может быть ванильной, может лежать в вашем моде, а может приходить из другого мода или датапака. После первого входа в мир мод один раз создает стартовую точку, сохраняет найденные координаты и потом использует их как общий стартовый спавн.

## Как пользоваться

Начиная с `2.0.0`, основной способ настройки это профили в `config/customizableplayerspawn/profiles/*.toml`. Первый включенный профиль по имени файла становится активным. Если папки профилей нет или в ней нет включенных профилей, мод использует старый `config/customizableplayerspawn-common.toml`, поэтому существующие сборки продолжают работать.

В профиле укажите нужную структуру через `structure`. Для структуры из ресурсов, датапака или другого мода используйте id вида `modid:path`. Для loose `.nbt` рядом с конфигом используйте `config:<file>.nbt`; файл должен лежать в `config/customizableplayerspawn/structures/`.

Старый файл `config/customizableplayerspawn-common.toml` по-прежнему поддерживается. В нем структура задается через `structureTemplate` или `externalStructureFile`. Если у вас своя `.nbt`, положите ее в `data/<namespace>/structure/<path>.nbt`, в сохранение мира по пути `generated/<namespace>/structures/<path>.nbt`, либо в `config/customizableplayerspawn/structures/`.

Для модпаков доступен отдельный вариант без датапаков и без пересборки `.jar`: положите файл в `config/customizableplayerspawn/structures/<name>.nbt` и укажите его через `externalStructureFile`. Такой файл будет доступен для любого нового мира сразу после установки сборки, игроку ничего вручную добавлять не нужно.

Если структура не нужна совсем, оставьте одновременно `structureTemplate = ""` и `externalStructureFile = ""`. Тогда мод просто найдет стартовую позицию в `targetDimension`, применит `placementY`, `surfaceYOffset` и `spawnOffset*`, после чего телепортирует игрока туда без размещения `.nbt`.

Теперь задайте точку спавна. Самый простой вариант это поставить внутри структуры блок и указать его id в `spawnMarkerBlock`. Если вы не хотите использовать блок, можно оставить внутри шаблона `structure_block` с режимом `DATA` и задать имя маркера через `dataMarker`. Если блок нужен только как отметка, мод может удалить его после размещения.

Высота теперь может определяться в трех режимах через `surfaceSearchMode`:

- `HEIGHTMAP` использует старое поведение через `level.getHeight(...)`.
- `SMART` сканирует колонку сверху вниз, ищет реальную опору, проверяет место для ног и головы, а затем дополнительно валидирует весь footprint структуры.
- `ABSOLUTE_Y` берет фиксированную высоту из `absoluteY`.

В режиме `SMART` мод также учитывает `maxSurfaceStep`, `allowFluidsBelow`, `allowReplaceableAtFeet`, `allowReplaceableAtHead` и `forbiddenSurfaceBlocks`. Это полезно для пещерных измерений, кастомных миров, биомов с листвой и миров без нормальной верхней поверхности.

Параметр `placementY` считается как смещение от найденной базовой высоты. В `SMART` это найденная площадка, в `HEIGHTMAP` это высота heightmap, в `ABSOLUTE_Y` это значение `absoluteY`. `0` ставит структуру на найденный уровень, положительные значения поднимают ее выше, отрицательные опускают ниже.

Когда игрок впервые зайдет в мир, мод найдет подходящее место, поставит структуру или сохранит обычную точку спавна, а затем перенесет его в сохраненную позицию. Для dev-запуска сейчас по умолчанию включен тестовый вариант с кораблем Края и спавном рядом с рамкой, где лежат элитры.

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
surfaceSearchMode = "SMART"
absoluteY = 96
placementY = 0
surfaceYOffset = 0
smartSearchTopOffset = 0
smartSearchBottomOffset = 0
maxSurfaceStep = 3
allowFluidsBelow = false
allowReplaceableAtFeet = true
allowReplaceableAtHead = true
forbiddenSurfaceBlocks = ["minecraft:lava", "minecraft:magma_block"]
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
surfaceSearchMode = "SMART"
absoluteY = 96
placementY = 0
surfaceYOffset = 0
smartSearchTopOffset = 0
smartSearchBottomOffset = 0
maxSurfaceStep = 3
allowFluidsBelow = false
allowReplaceableAtFeet = true
allowReplaceableAtHead = true
forbiddenSurfaceBlocks = ["minecraft:lava", "minecraft:magma_block"]
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
surfaceSearchMode = "ABSOLUTE_Y"
absoluteY = 96
placementY = 32
surfaceYOffset = 0
smartSearchTopOffset = 0
smartSearchBottomOffset = 0
maxSurfaceStep = 3
allowFluidsBelow = false
allowReplaceableAtFeet = true
allowReplaceableAtHead = true
forbiddenSurfaceBlocks = ["minecraft:lava", "minecraft:magma_block"]
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
surfaceSearchMode = "SMART"
absoluteY = 96
placementY = 0
surfaceYOffset = 0
smartSearchTopOffset = 0
smartSearchBottomOffset = 0
maxSurfaceStep = 3
allowFluidsBelow = false
allowReplaceableAtFeet = true
allowReplaceableAtHead = true
forbiddenSurfaceBlocks = ["minecraft:lava", "minecraft:magma_block"]
spawnOffsetX = 0
spawnOffsetY = 0
spawnOffsetZ = 0
spawnAngle = 0
```

В этом случае файл нужно положить по пути `config/customizableplayerspawn/structures/spawn.nbt`. Параметр `externalStructureFile` имеет приоритет над `structureTemplate`.

Если нужно просто отправить игрока в другое измерение без структуры, можно использовать такой конфиг:

```toml
targetDimension = "minecraft:the_nether"
allowedBiomes = []
structureTemplate = ""
externalStructureFile = ""
spawnMarkerBlock = ""
removeSpawnMarkerBlock = true
dataMarker = ""
searchRadius = 512
searchAttempts = 64
surfaceSearchMode = "SMART"
absoluteY = 96
placementY = 0
surfaceYOffset = 0
smartSearchTopOffset = 0
smartSearchBottomOffset = 0
maxSurfaceStep = 3
allowFluidsBelow = false
allowReplaceableAtFeet = true
allowReplaceableAtHead = true
forbiddenSurfaceBlocks = ["minecraft:lava", "minecraft:magma_block"]
spawnOffsetX = 0
spawnOffsetY = 0
spawnOffsetZ = 0
spawnAngle = 0
```

В этом режиме `spawnMarkerBlock` и `dataMarker` не используются, потому что точка спавна берется напрямую из найденной позиции.

## Профили 2.0

Минимальный профиль выглядит так:

```toml
schemaVersion = 2
id = "starter_house"
enabled = true
priority = 100
dimension = "minecraft:overworld"
structure = "config:starter_house.nbt"

[anchor]
mode = "MARKER_BLOCK"
markerBlock = "customizableplayerspawn:player_spawn_marker"
removeMarkerBlock = true
dataMarker = ""
offsetX = 0
offsetY = 0
offsetZ = 0
angle = 0
fallback = "SAFE_NEARBY"

[placement]
strategy = "FLAT_FOOTPRINT"
allowedBiomes = ["minecraft:plains", "minecraft:forest"]
searchRadius = 2048
searchAttempts = 128
surfaceSearchMode = "SMART"
absoluteY = 96
placementY = 0
surfaceYOffset = 0
smartSearchTopOffset = 0
smartSearchBottomOffset = 0
maxSurfaceStep = 3
allowFluidsBelow = false
allowReplaceableAtFeet = true
allowReplaceableAtHead = true
forbiddenSurfaceBlocks = ["minecraft:lava", "minecraft:magma_block"]
minLandRatio = 0.85
maxTreeOverlap = 0
avoidFluids = true

[terrain]
mode = "NONE"
padding = 10
topBlock = "minecraft:grass_block"
fillBlock = "minecraft:dirt"
coreBlock = "minecraft:stone"
edgeNoise = true
clearStructureVolume = true
clearVolumePadding = 0
fillSupportVoids = true
supportVoidMaxFillDepth = 8
islandMaxDrop = 8
islandEdgeFalloff = 6

[postprocess]
refreshLighting = true
refreshLightingDelays = [5, 20, 60]
suppressDrops = true
stabilizeBlocks = false
updateNeighborShapes = false
processDataMarkers = true
```

Профили нужны для модпаков: настройки структуры, якоря, поиска поверхности, подготовки земли и пост-обработки теперь находятся в одном переносимом файле. Legacy-конфиг остается только слоем совместимости.

Выбор профиля:

- если в `customizableplayerspawn-common.toml` задан `selectedProfile`, мод пытается использовать именно этот профиль;
- если `selectedProfile` пустой, выбирается включенный валидный профиль с самым большим `priority`;
- если валидных включенных профилей нет, используется legacy-конфиг.

Доступные `placement.strategy`: `DIRECT`, `SURFACE`, `FLAT_FOOTPRINT`, `EMBEDDED`, `ISLAND`, `AUTO`.
Доступные `anchor.mode`: `AUTO`, `MARKER_BLOCK`, `DATA_MARKER`, `RELATIVE`, `CENTER`.
Доступные `anchor.fallback`: `FAIL`, `CENTER`, `SAFE_NEARBY`.

## Поиск поверхности

Если нужно просто поставить структуру на реальную поверхность, обычно достаточно такого набора:

```toml
surfaceSearchMode = "SMART"
placementY = 0
surfaceYOffset = 0
smartSearchTopOffset = 0
smartSearchBottomOffset = 0
maxSurfaceStep = 3
allowFluidsBelow = false
allowReplaceableAtFeet = true
allowReplaceableAtHead = true
forbiddenSurfaceBlocks = ["minecraft:lava", "minecraft:magma_block"]
```

Такой конфиг оставляет старую совместимость, но заставляет мод искать именно пригодную площадку вместо простой высоты из heightmap. Если структура все равно стоит чуть выше или ниже, обычно проблема уже не в поиске поверхности, а в том, где у самого `.nbt` находится нижняя опорная плоскость.
