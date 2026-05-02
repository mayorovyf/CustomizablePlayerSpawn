# Customizable Player Spawn

Это мод для Forge, который меняет стартовую точку игрока. Вместо обычного спавна мод может поставить готовую структуру и поселить игрока прямо внутри нее, либо просто перенести игрока в нужное измерение без генерации структуры. Подходит для стартовых островов, домов, лобби и любых других заготовок, которые лежат в `.nbt`.

Мод читает настройки из конфига и берет оттуда измерение, список биомов, путь к структуре и точку, к которой нужно привязать спавн. Структура может быть ванильной, может лежать в вашем моде, а может приходить из другого мода или датапака. После первого входа в мир мод один раз создает стартовую точку, сохраняет найденные координаты и потом использует их как общий стартовый спавн.

## Как пользоваться

Откройте файл `config/customizableplayerspawn-common.toml` и укажите нужную структуру в параметре `structureTemplate`. Если у вас своя `.nbt`, положите ее в `data/<namespace>/structure/<path>.nbt` или в сохранение мира по пути `generated/<namespace>/structures/<path>.nbt`. Если структура приходит из другого мода, достаточно прописать ее id в формате `modid:path`.

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
