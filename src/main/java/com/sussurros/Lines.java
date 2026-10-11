package com.sussurros;

/** Todos os textos do mod (portugues e ingles). */
public final class Lines {
	private Lines() {
	}

	private static boolean en() {
		return "en".equals(HorrorConfig.language);
	}

	private static final String[] FAKE_NAMES = { "Steve", "Alex", "Herobrine", "null", "Entity303", "Visitante", "???" };

	private static final String[] FAKE_CHAT_PT = {
			"você também ouviu isso?", "não olhe para trás", "estou vendo você", "por que você está sozinho aqui?",
			"ele sabe o seu nome", "as luzes não vão te proteger", "eu estive aqui o tempo todo", "para de cavar",
			"volte para casa", "você deixou a porta aberta", "atrás de você", "você não devia ter vindo aqui",
			"eu fui o primeiro a morrer neste mundo", "ele está na parede", "olha pra cima", "escuta."
	};
	private static final String[] FAKE_CHAT_EN = {
			"did you hear that too?", "don't look behind you", "i can see you", "why are you alone out here?",
			"he knows your name", "the lights won't protect you", "i've been here the whole time", "stop digging",
			"go home", "you left the door open", "behind you", "you shouldn't have come here",
			"i was the first to die in this world", "he's in the wall", "look up", "listen."
	};

	private static final String[] SELF_CHAT_PT = {
			"não fui eu que escrevi isso", "tem alguém dentro da minha cabeça", "estou com medo", "quem está digitando por mim?",
			"ele está perto", "eu já morri aqui antes", "me tira daqui", "não me deixe sozinho"
	};
	private static final String[] SELF_CHAT_EN = {
			"i didn't write that", "there's someone inside my head", "i'm scared", "who is typing for me?",
			"he's close", "i've died here before", "get me out of here", "don't leave me alone"
	};

	private static final String[] WHISPER_TEXT_PT = {
			"Você tem a sensação de que está sendo observado.", "O silêncio está fingindo.", "Algo se moveu atrás de você.",
			"Você jura que ouviu o seu nome.", "O ar ficou pesado.", "Não está tão vazio quanto parece.",
			"Você se lembra de ter deixado aquela porta fechada?", "Alguém andou por aqui há pouco.",
			"A sua sombra demorou para te seguir.", "Não olhe por muito tempo para a escuridao."
	};
	private static final String[] WHISPER_TEXT_EN = {
			"You feel like you are being watched.", "The silence is pretending.", "Something moved behind you.",
			"You swear you heard your name.", "The air grew heavy.", "It is not as empty as it seems.",
			"Do you remember leaving that door closed?", "Someone walked through here a moment ago.",
			"Your shadow was slow to follow you.", "Do not stare into the dark for too long."
	};

	private static final String[] ADVANCEMENTS_PT = { "[Atrás de Você]", "[Ele Está Aqui]", "[Não Olhe]", "[Você Foi Avisado]", "[Alguém Está Olhando]" };
	private static final String[] ADVANCEMENTS_EN = { "[Behind You]", "[He Is Here]", "[Do Not Look]", "[You Were Warned]", "[Someone Is Watching]" };

	private static final String[] GREED_PT = { "A cobiça tem um preço.", "Ele gosta quando você cava.", "Cada pedra que você quebra, alguém sente." };
	private static final String[] GREED_EN = { "Greed has a price.", "He likes it when you dig.", "Every stone you break, someone feels it." };

	private static final String[][] TITLES_PT = {
			{ "VOCÊ NÃO ESTÁ SOZINHO", "" }, { "ELE ESTÁ AQUI", "olhe para trás" }, { "NÃO OLHE", "" }, { "LEMBRE-SE", "ele nunca esqueceu você" }
	};
	private static final String[][] TITLES_EN = {
			{ "YOU ARE NOT ALONE", "" }, { "HE IS HERE", "look behind you" }, { "DO NOT LOOK", "" }, { "REMEMBER", "he never forgot you" }
	};

	public static String fakeName() {
		return FAKE_NAMES[Director.RNG.nextInt(FAKE_NAMES.length)];
	}

	public static String fakeChat() {
		return pick(en() ? FAKE_CHAT_EN : FAKE_CHAT_PT);
	}

	public static String selfChat() {
		return pick(en() ? SELF_CHAT_EN : SELF_CHAT_PT);
	}

	public static String whisperText() {
		return pick(en() ? WHISPER_TEXT_EN : WHISPER_TEXT_PT);
	}

	public static String advancement() {
		return pick(en() ? ADVANCEMENTS_EN : ADVANCEMENTS_PT);
	}

	public static String greed() {
		return pick(en() ? GREED_EN : GREED_PT);
	}

	public static String[] title() {
		String[][] all = en() ? TITLES_EN : TITLES_PT;
		return all[Director.RNG.nextInt(all.length)];
	}

	public static String welcome() {
		return en()
				? "[Sussurros do Vazio] The mod is active. You are not alone. (/horror list to try the events)"
				: "[Sussurros do Vazio] O mod está ativo. Você não está sozinho. (/horror list para testar os eventos)";
	}

	private static String pick(String[] arr) {
		return arr[Director.RNG.nextInt(arr.length)];
	}
}
