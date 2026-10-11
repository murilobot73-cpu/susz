package com.sussurros;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Configuracao simples em config/sussurros.properties (criada automaticamente na primeira execucao). */
public final class HorrorConfig {
	public static boolean enabled = true;
	public static String language = "pt";
	public static double intensity = 1.0;
	public static int graceMinutes = 4;
	public static boolean affectCreative = false;
	public static boolean fakeChat = true;
	public static boolean fakeChatMultiplayer = false;
	public static boolean worldTampering = true;
	public static boolean apparitions = true;
	public static boolean jumpscares = true;
	public static double sanityDrain = 1.0;
	public static boolean welcomeMessage = true;

	private static final String TEMPLATE = """
			# Sussurros do Vazio - configuracao
			# Apague este arquivo para restaurar os valores padrao. Use /horror reload para recarregar no jogo.

			# Liga/desliga o mod inteiro
			enabled=true

			# Idioma das mensagens falsas: pt ou en
			language=pt

			# Multiplicador da frequencia dos eventos (0.25 = bem raro, 1 = normal, 3 = intenso)
			intensity=1.0

			# Minutos sem nenhum evento depois de entrar no mundo
			grace_minutes=4

			# Eventos tambem no modo criativo/espectador
			affect_creative=false

			# Mensagens falsas no chat (jogadores falsos, mensagens com o seu nome, avancos falsos)
			fake_chat=true

			# Permitir mensagens falsas no chat quando ha mais de um jogador online
			fake_chat_multiplayer=false

			# Eventos que mexem no mundo (apagar tochas proximas, tempestade)
			world_tampering=true

			# Aparicoes (figuras que observam, aparecem atras de voce ou te seguem)
			apparitions=true

			# Sustos (som alto + escurecimento da tela)
			jumpscares=true

			# Multiplicador da velocidade com que a sanidade cai (0.5 = metade, 2 = dobro)
			sanity_drain=1.0

			# Mensagem discreta de aviso ao entrar no mundo
			welcome_message=true
			""";

	private HorrorConfig() {
	}

	public static void load() {
		try {
			Path file = FabricLoader.getInstance().getConfigDir().resolve("sussurros.properties");
			if (!Files.exists(file)) {
				Files.createDirectories(file.getParent());
				Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
			}
			Properties props = new Properties();
			try (InputStream in = Files.newInputStream(file)) {
				props.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
			}
			enabled = bool(props, "enabled", true);
			String lang = props.getProperty("language", "pt").trim().toLowerCase(Locale.ROOT);
			language = lang.startsWith("en") ? "en" : "pt";
			intensity = clamp(num(props, "intensity", 1.0), 0.25, 5.0);
			graceMinutes = (int) clamp(num(props, "grace_minutes", 4), 0, 120);
			affectCreative = bool(props, "affect_creative", false);
			fakeChat = bool(props, "fake_chat", true);
			fakeChatMultiplayer = bool(props, "fake_chat_multiplayer", false);
			worldTampering = bool(props, "world_tampering", true);
			apparitions = bool(props, "apparitions", true);
			jumpscares = bool(props, "jumpscares", true);
			sanityDrain = clamp(num(props, "sanity_drain", 1.0), 0.0, 5.0);
			welcomeMessage = bool(props, "welcome_message", true);
		} catch (IOException | RuntimeException e) {
			SussurrosMod.LOGGER.warn("Nao foi possivel ler a configuracao, usando valores padrao: {}", e.toString());
		}
	}

	private static boolean bool(Properties p, String key, boolean def) {
		String v = p.getProperty(key);
		if (v == null) {
			return def;
		}
		v = v.trim().toLowerCase(Locale.ROOT);
		if (v.equals("true")) {
			return true;
		}
		if (v.equals("false")) {
			return false;
		}
		return def;
	}

	private static double num(Properties p, String key, double def) {
		String v = p.getProperty(key);
		if (v == null) {
			return def;
		}
		try {
			return Double.parseDouble(v.trim().replace(',', '.'));
		} catch (NumberFormatException e) {
			return def;
		}
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
