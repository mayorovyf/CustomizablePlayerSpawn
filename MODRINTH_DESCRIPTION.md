# Modrinth Description

## English

Customizable Player Spawn changes the way players first appear in a world. With this mod, you can choose a custom starting location instead of using the default spawn.

The mod can place a prepared `.nbt` structure and use a marker inside it as the real spawn point. This is useful for starter houses, islands, lobbies, map introductions, and modpacks where the first moments of the game should happen in a specific place.

It can load structures from your mod resources, datapacks, other mods, world files, or external `.nbt` files in the config folder. If you do not want to place any structure, the mod can simply send players to a chosen dimension and create a start position there.

Everything is controlled through the config. You can choose the target dimension, limit the search to specific biomes, adjust the search range, change height and spawn offsets, and set the player rotation. After the spawn position is found, the mod saves it and keeps using the same location for future joins and respawns.

This mod is meant for worlds and modpacks that need a controlled starting experience. If structure placement is enabled, the template must contain a valid marker block or a matching `DATA` marker. If both `structureTemplate` and `externalStructureFile` are empty, the mod switches to spawn mode without a structure.

Suggestions for future development and bug reports can be submitted in the issue tracker: https://github.com/mayorovyf/CustomizablePlayerSpawn/issues

## Русский

Customizable Player Spawn меняет то, как игроки впервые появляются в мире. С этим модом можно задать собственную стартовую точку вместо обычного спавна.

Мод умеет размещать подготовленную `.nbt` структуру и использовать маркер внутри неё как настоящую точку спавна. Это удобно для стартовых домов, островов, лобби, вступительных зон на картах и сборок, где важно, чтобы первые минуты игры проходили в заранее выбранном месте.

Он может загружать структуры из ресурсов мода, датапаков, других модов, файлов мира или внешних `.nbt` файлов в папке конфига. Если структура не нужна, мод может просто отправить игроков в выбранное измерение и создать стартовую точку там.

Все настройки задаются через конфиг. Можно выбрать целевое измерение, ограничить поиск нужными биомами, настроить радиус поиска, смещения по высоте и координатам, а также угол поворота игрока. После того как точка спавна найдена, мод сохраняет её и использует ту же позицию при следующих входах и респавнах.

Этот мод подходит для миров и сборок, где нужен контролируемый старт. Если включено размещение структуры, в шаблоне должен быть корректный блок-маркер или подходящий `DATA` marker. Если `structureTemplate` и `externalStructureFile` пустые, мод автоматически переходит в режим спавна без структуры.

Если у вас есть предложения по развитию мода или вы нашли ошибку, оставьте сообщение в issue tracker: https://github.com/mayorovyf/CustomizablePlayerSpawn/issues
