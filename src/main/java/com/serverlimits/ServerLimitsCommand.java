package com.serverlimits;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.serverlimits.config.ServerLimitsConfig;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class ServerLimitsCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                literal("serverlimits").requires(src -> src.hasPermissionLevel(2))

                    // /serverlimits reload
                    .then(literal("reload")
                        .executes(ServerLimitsCommand::reload))

                    // /serverlimits explosion <source> enabled <true|false>
                    // /serverlimits explosion <source> maxpower <value>
                    .then(literal("explosion")
                        .then(argument("source", StringArgumentType.word())
                            .then(literal("enabled")
                                .then(argument("value", BoolArgumentType.bool())
                                    .executes(ServerLimitsCommand::explosionEnabled)))
                            .then(literal("maxpower")
                                .then(argument("value", FloatArgumentType.floatArg(0f))
                                    .executes(ServerLimitsCommand::explosionMaxPower)))))

                    // /serverlimits item set <item_id> <limit>
                    // /serverlimits item remove <item_id>
                    .then(literal("item")
                        .then(literal("set")
                            .then(argument("item", IdentifierArgumentType.identifier())
                                .then(argument("limit", IntegerArgumentType.integer(1))
                                    .executes(ServerLimitsCommand::itemSet))))
                        .then(literal("remove")
                            .then(argument("item", IdentifierArgumentType.identifier())
                                .executes(ServerLimitsCommand::itemRemove))))
            )
        );
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        ServerLimitsMod.CONFIG = ServerLimitsConfig.load();
        ctx.getSource().sendFeedback(
            () -> Text.literal("[ServerLimits] Config reloaded from disk."), true);
        return 1;
    }

    private static int explosionEnabled(CommandContext<ServerCommandSource> ctx) {
        String source = StringArgumentType.getString(ctx, "source");
        boolean value = BoolArgumentType.getBool(ctx, "value");

        ServerLimitsConfig cfg = ServerLimitsMod.CONFIG;
        ServerLimitsConfig.ExplosionSourceConfig src = cfg.explosionSources.computeIfAbsent(
            source, k -> new ServerLimitsConfig.ExplosionSourceConfig(true, 4.0f));
        src.enabled = value;
        cfg.save();
        ctx.getSource().sendFeedback(
            () -> Text.literal("[ServerLimits] explosion." + source + ".enabled = " + value), true);
        return 1;
    }

    private static int explosionMaxPower(CommandContext<ServerCommandSource> ctx) {
        String source = StringArgumentType.getString(ctx, "source");
        float value = FloatArgumentType.getFloat(ctx, "value");

        ServerLimitsConfig cfg = ServerLimitsMod.CONFIG;
        ServerLimitsConfig.ExplosionSourceConfig src = cfg.explosionSources.computeIfAbsent(
            source, k -> new ServerLimitsConfig.ExplosionSourceConfig(true, 4.0f));
        src.maxPower = value;
        cfg.save();
        ctx.getSource().sendFeedback(
            () -> Text.literal("[ServerLimits] explosion." + source + ".maxPower = " + value), true);
        return 1;
    }

    private static int itemSet(CommandContext<ServerCommandSource> ctx) {
        String item = IdentifierArgumentType.getIdentifier(ctx, "item").toString();
        int limit = IntegerArgumentType.getInteger(ctx, "limit");
        ServerLimitsMod.CONFIG.itemLimits.put(item, limit);
        ServerLimitsMod.CONFIG.save();
        ctx.getSource().sendFeedback(
            () -> Text.literal("[ServerLimits] item." + item + " limit = " + limit), true);
        return 1;
    }

    private static int itemRemove(CommandContext<ServerCommandSource> ctx) {
        String item = IdentifierArgumentType.getIdentifier(ctx, "item").toString();
        boolean had = ServerLimitsMod.CONFIG.itemLimits.remove(item) != null;
        ServerLimitsMod.CONFIG.save();
        ctx.getSource().sendFeedback(
            () -> Text.literal("[ServerLimits] " + item + (had ? " removed." : " was not in item limits.")), true);
        return 1;
    }
}
