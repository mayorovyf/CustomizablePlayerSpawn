package com.mayo.customizableplayerspawn.structure;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

public record LoadedStructureTemplate(String description, StructureTemplate template, StructureTemplateBounds bounds) {
}
