package com.sussurros;

import java.lang.reflect.Method;
import java.util.Locale;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Executa comandos vanilla em nome do servidor (sons, particulas, efeitos, texto).
 *
 * Usar comandos em vez de classes internas do jogo e proposital: a sintaxe dos comandos quase nao muda
 * entre versoes, entao isso reduz muito a chance de erro ao carregar o mod.
 * Qualquer falha vira apenas um aviso no log, nunca derruba o jogo.
 */
public final class Cmd {
	private static Method suppressOutput;
	private static boolean suppressTried;

	private Cmd() {
	}

	/** Numero com ponto decimal, independente do idioma do sistema (pt-BR usa virgula!). */
	public static String n(double v) {
		return String.format(Locale.ROOT, "%.2f", v);
	}

	/** Texto entre aspas, escapado, para usar dentro de um componente de texto. */
	public static String q(String s) {
		return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private static CommandSourceStack source(MinecraftServer server) {
		CommandSourceStack src = server.createCommandSourceStack();
		try {
			if (!suppressTried) {
				suppressTried = true;
				suppressOutput = CommandSourceStack.class.getMethod("withSuppressedOutput");
			}
			if (suppressOutput != null) {
				src = (CommandSourceStack) suppressOutput.invoke(src);
			}
		} catch (Throwable ignored) {
			// sem supressao de saida: o comando ainda funciona
		}
		return src;
	}

	/** Roda um comando como servidor. */
	public static boolean run(MinecraftServer server, String command) {
		try {
			server.getCommands().performPrefixedCommand(source(server), command);
			return true;
		} catch (Throwable t) {
			SussurrosMod.LOGGER.debug("Comando falhou ({}): {}", command, t.toString());
			return false;
		}
	}

	/** Roda um comando na posicao/dimensao do jogador, com @s sendo o jogador. */
	public static void as(MinecraftServer server, ServerPlayer p, String command) {
		run(server, "execute as " + p.getUUID() + " at @s run " + command);
	}

	public static void tellraw(MinecraftServer server, ServerPlayer p, String componentJson) {
		as(server, p, "tellraw @s " + componentJson);
	}

	public static void plainChat(MinecraftServer server, ServerPlayer p, String text) {
		tellraw(server, p, "{\"text\":" + q(text) + "}");
	}

	public static void actionbar(MinecraftServer server, ServerPlayer p, String text, String color) {
		as(server, p, "title @s actionbar {\"text\":" + q(text) + ",\"color\":\"" + color + "\",\"italic\":true}");
	}

	public static void effect(MinecraftServer server, ServerPlayer p, String effect, int seconds, int amplifier) {
		as(server, p, "effect give @s minecraft:" + effect + " " + seconds + " " + amplifier + " true");
	}

	public static void particle(MinecraftServer server, ServerPlayer p, String id, double x, double y, double z,
			double spreadX, double spreadY, double spreadZ, double speed, int count) {
		as(server, p, "particle " + id + " " + n(x) + " " + n(y) + " " + n(z) + " " + n(spreadX) + " " + n(spreadY) + " "
				+ n(spreadZ) + " " + n(speed) + " " + count + " normal @s");
	}

	/** Toca um som so para esse jogador, em uma posicao absoluta do mundo. */
	public static void sound(MinecraftServer server, ServerPlayer p, String id, String category, double x, double y,
			double z, double volume, double pitch) {
		double pt = Math.max(0.0, Math.min(2.0, pitch));
		as(server, p, "playsound " + id + " " + category + " @s " + n(x) + " " + n(y) + " " + n(z) + " " + n(volume) + " "
				+ n(pt) + " 0");
	}
}
