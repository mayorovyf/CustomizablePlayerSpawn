package com.mayo.customizableplayerspawn.command;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnCommon;
import com.mayo.customizableplayerspawn.SpawnProfile;
import com.mayo.customizableplayerspawn.SpawnProfiles;
import com.mayo.customizableplayerspawn.SpawnStructureInstance;
import com.mayo.customizableplayerspawn.SpawnStructureSavedData;
import com.mayo.customizableplayerspawn.config.CommonConfigFiles;
import com.mayo.customizableplayerspawn.profile.SpawnProfileValidationResult;
import com.mayo.customizableplayerspawn.profile.SpawnProfileValidator;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CustomizablePlayerSpawnCommands {
    private CustomizablePlayerSpawnCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cps")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("validate").executes(context -> validate(context.getSource())))
                .then(Commands.literal("list").executes(context -> list(context.getSource())))
                .then(Commands.literal("reset")
                        .executes(context -> resetAll(context.getSource()))
                        .then(Commands.literal("all").executes(context -> resetAll(context.getSource())))
                        .then(Commands.literal("player")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> resetPlayer(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("tp")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> teleportPlayer(context.getSource(), EntityArgument.getPlayer(context, "player"))))));
    }

    private static int reload(CommandSourceStack source) {
        SpawnProfiles.reload();
        SpawnProfiles.active();
        source.sendSuccess(() -> Component.literal("Customizable Player Spawn configs reloaded."), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        SpawnProfile profile = SpawnProfiles.active();
        SpawnStructureSavedData data = SpawnStructureSavedData.get(server);
        source.sendSuccess(() -> Component.literal("CPS profile=%s mode=%s protection=%s".formatted(
                profile.id(),
                profile.spawnPolicy().mode().name().toLowerCase(),
                profile.protection().enabled()
        )), false);
        source.sendSuccess(() -> Component.literal("instances=%s assignments=%s shared=%s".formatted(
                data.instanceCount(),
                data.assignmentCount(),
                data.sharedInstanceId().isBlank() ? "<none>" : data.sharedInstanceId()
        )), false);
        return data.instanceCount();
    }

    private static int validate(CommandSourceStack source) {
        SpawnProfiles.reload();
        SpawnProfile profile = SpawnProfiles.active();
        SpawnProfileValidationResult validation = SpawnProfileValidator.validate(profile);
        validation.warnings().forEach(warning -> source.sendSuccess(() -> Component.literal("Warning: " + warning), false));
        validation.errors().forEach(error -> source.sendFailure(Component.literal("Error: " + error)));
        if (validation.valid()) {
            source.sendSuccess(() -> Component.literal("Profile %s is valid. Config dir: %s".formatted(profile.id(), CommonConfigFiles.modConfigDirectory())), false);
            return 1;
        }

        return 0;
    }

    private static int list(CommandSourceStack source) {
        SpawnStructureSavedData data = SpawnStructureSavedData.get(source.getServer());
        int shown = 0;
        for (SpawnStructureInstance instance : data.instances()) {
            if (shown >= 10) {
                break;
            }

            source.sendSuccess(() -> Component.literal("%s owner=%s dim=%s spawn=%s origin=%s profile=%s".formatted(
                    instance.id(),
                    instance.ownerUuid() == null ? "<shared>" : instance.ownerUuid(),
                    instance.dimensionId(),
                    instance.spawnPos(),
                    instance.origin(),
                    instance.profileName()
            )), false);
            shown++;
        }

        if (data.instanceCount() > shown) {
            int remaining = data.instanceCount() - shown;
            source.sendSuccess(() -> Component.literal("...and " + remaining + " more instances."), false);
        }

        if (shown == 0) {
            source.sendSuccess(() -> Component.literal("No saved spawn structure instances."), false);
        }

        return data.instanceCount();
    }

    private static int resetAll(CommandSourceStack source) {
        SpawnStructureSavedData.get(source.getServer()).resetAll();
        source.sendSuccess(() -> Component.literal("All CPS spawn instances and assignments were reset."), true);
        return 1;
    }

    private static int resetPlayer(CommandSourceStack source, ServerPlayer player) {
        SpawnStructureSavedData.get(source.getServer()).unassignPlayer(player.getUUID());
        source.sendSuccess(() -> Component.literal("CPS assignment reset for " + player.getGameProfile().getName() + "."), true);
        return 1;
    }

    private static int teleportPlayer(CommandSourceStack source, ServerPlayer player) {
        boolean teleported = CustomizablePlayerSpawnCommon.spawnStructureManager().teleportToAssignedSpawn(player);
        if (!teleported) {
            source.sendFailure(Component.literal("No assigned CPS spawn exists for " + player.getGameProfile().getName() + "."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Teleported " + player.getGameProfile().getName() + " to assigned CPS spawn."), true);
        return 1;
    }
}
