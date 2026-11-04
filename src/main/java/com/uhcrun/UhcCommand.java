package com.uhcrun;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public final class UhcCommand {
    private UhcCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("uhc")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("start").executes(c -> {
                    String err = UhcGame.start(c.getSource());
                    if (err != null) {
                        c.getSource().sendFailure(Component.literal(err));
                        return 0;
                    }
                    return 1;
                }))
                .then(Commands.literal("stop").executes(c -> {
                    if (!UhcGame.running) {
                        c.getSource().sendFailure(Component.literal("No hay ninguna partida en curso."));
                        return 0;
                    }
                    UhcGame.stop(true);
                    return 1;
                }))
                .then(Commands.literal("estado").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal(UhcGame.status()), false);
                    return 1;
                }))
                .then(Commands.literal("cerrar").executes(c -> {
                    if (!UhcGame.forceShrink()) {
                        c.getSource().sendFailure(Component.literal("No hay ninguna partida en curso."));
                        return 0;
                    }
                    c.getSource().sendSuccess(() -> Component.literal("El borde se cerrará en el próximo segundo."), true);
                    return 1;
                }))
                .then(Commands.literal("config")
                        .executes(c -> {
                            StringBuilder sb = new StringBuilder("Opciones de UHC Run:");
                            for (String n : UhcSettings.names()) {
                                int[] r = UhcSettings.range(n);
                                sb.append("\n - ").append(n).append(" = ").append(UhcSettings.get(n))
                                        .append(" (").append(r[1]).append("-").append(r[2]).append(")");
                            }
                            String text = sb.toString();
                            c.getSource().sendSuccess(() -> Component.literal(text), false);
                            return 1;
                        })
                        .then(Commands.argument("opcion", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(UhcSettings.names(), b))
                                .then(Commands.argument("valor", IntegerArgumentType.integer())
                                        .executes(c -> {
                                            String name = StringArgumentType.getString(c, "opcion");
                                            int value = IntegerArgumentType.getInteger(c, "valor");
                                            String err = UhcSettings.set(name, value);
                                            if (err != null) {
                                                c.getSource().sendFailure(Component.literal(err));
                                                return 0;
                                            }
                                            c.getSource().sendSuccess(
                                                    () -> Component.literal(name + " = " + value), true);
                                            return 1;
                                        })))));
    }
}
