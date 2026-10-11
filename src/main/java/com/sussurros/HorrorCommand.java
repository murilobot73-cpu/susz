package com.sussurros;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * /horror - comandos para testar o mod. Todos afetam somente quem executa o comando.
 *
 *   /horror status            mostra sanidade e estagio
 *   /horror list              lista os eventos
 *   /horror event <id>        dispara um evento agora (ou "random")
 *   /horror sanity <0-100>    define a sua sanidade
 *   /horror calm              restaura a sanidade e remove aparicoes
 *   /horror on | off          liga/desliga os eventos so para voce
 *   /horror cleanup           remove figuras que tenham sobrado no mundo
 *   /horror reload            recarrega config/sussurros.properties
 */
public final class HorrorCommand {
	private HorrorCommand() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> dispatcher.register(
				literal("horror")
						.executes(HorrorCommand::status)
						.then(literal("status").executes(HorrorCommand::status))
						.then(literal("list").executes(HorrorCommand::list))
						.then(literal("event")
								.then(argument("id", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(eventSuggestions(), builder))
										.executes(HorrorCommand::event)))
						.then(literal("sanity")
								.then(argument("value", IntegerArgumentType.integer(0, 100)).executes(HorrorCommand::sanity)))
						.then(literal("calm").executes(HorrorCommand::calm))
						.then(literal("on").executes(ctx -> toggle(ctx, true)))
						.then(literal("off").executes(ctx -> toggle(ctx, false)))
						.then(literal("cleanup").executes(HorrorCommand::cleanup))
						.then(literal("reload").executes(HorrorCommand::reload))));
	}

	private static java.util.List<String> eventSuggestions() {
		java.util.List<String> out = new java.util.ArrayList<>(Events.ids());
		out.add("random");
		return out;
	}

	private static void say(CommandContext<CommandSourceStack> ctx, String text) {
		ctx.getSource().sendSuccess(() -> Component.literal(text), false);
	}

	private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		PlayerState st = Director.state(p);
		long secs = Math.max(0L, (Math.max(st.nextEventTick, st.graceUntilTick) - Director.tick) / 20L);
		say(ctx, "Sanidade: " + Math.round(st.sanity) + "/100 (estágio " + Director.stage(st.sanity) + " de 3)");
		say(ctx, "Eventos nesta sessão: " + st.eventsFired + " | último: " + (st.lastEvent.isEmpty() ? "-" : st.lastEvent));
		say(ctx, "Próximo evento em ~" + secs + " s | Eventos para você: " + (st.enabled ? "ligados" : "desligados")
				+ " | Mod: " + (HorrorConfig.enabled ? "ligado" : "desligado (config)"));
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		say(ctx, "Eventos (use /horror event <id>):");
		StringBuilder line = new StringBuilder();
		for (Events.Def d : Events.all()) {
			String item = d.id() + "[" + d.minStage() + "]";
			if (line.length() + item.length() > 90) {
				say(ctx, line.toString());
				line.setLength(0);
			}
			line.append(item).append("  ");
		}
		if (line.length() > 0) {
			say(ctx, line.toString());
		}
		say(ctx, "O número entre colchetes é o estágio mínimo de medo em que o evento acontece sozinho.");
		return 1;
	}

	private static int event(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		String id = StringArgumentType.getString(ctx, "id");
		PlayerState st = Director.state(p);
		Events.Ctx c = new Events.Ctx(ctx.getSource().getServer(), p, (ServerLevel) p.level(), st);

		if (id.equals("random")) {
			boolean ok = Director.fire(c);
			say(ctx, ok ? "Evento aleatório disparado." : "Nenhum evento possível agora.");
			return ok ? 1 : 0;
		}
		Events.Def d = Events.byId(id);
		if (d == null) {
			ctx.getSource().sendFailure(Component.literal("Evento desconhecido: " + id + " (veja /horror list)"));
			return 0;
		}
		boolean ok;
		try {
			ok = d.run().test(c);
		} catch (Throwable t) {
			SussurrosMod.LOGGER.warn("Evento '{}' falhou: {}", id, t.toString());
			ok = false;
		}
		if (ok) {
			Director.markFired(st, d);
			say(ctx, "Evento disparado: " + id);
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal(
				"Não foi possível executar '" + id + "' agora (falta espaço livre, tochas por perto, ou o recurso está desligado na config)."));
		return 0;
	}

	private static int sanity(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		int value = IntegerArgumentType.getInteger(ctx, "value");
		Director.state(p).sanity = value;
		say(ctx, "Sanidade definida para " + value + " (estágio " + Director.stage(value) + ").");
		return 1;
	}

	private static int calm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		Director.state(p).sanity = 100.0;
		Apparitions.clearFor(ctx.getSource().getServer(), p.getUUID());
		say(ctx, "Tudo está calmo. Por enquanto.");
		return 1;
	}

	private static int toggle(CommandContext<CommandSourceStack> ctx, boolean on) throws CommandSyntaxException {
		ServerPlayer p = ctx.getSource().getPlayerOrException();
		Director.state(p).enabled = on;
		if (!on) {
			Apparitions.clearFor(ctx.getSource().getServer(), p.getUUID());
		}
		say(ctx, on ? "Eventos de terror ligados para você." : "Eventos de terror desligados para você.");
		return 1;
	}

	private static int cleanup(CommandContext<CommandSourceStack> ctx) {
		Apparitions.clearAll(ctx.getSource().getServer());
		Cmd.run(ctx.getSource().getServer(), "execute if entity @e[tag=sussurros_apparition] run kill @e[tag=sussurros_apparition]");
		say(ctx, "Figuras removidas.");
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> ctx) {
		HorrorConfig.load();
		say(ctx, "Configuração recarregada.");
		return 1;
	}
}
