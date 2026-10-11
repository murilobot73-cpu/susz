package com.sussurros;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Todos os eventos de terror. Cada evento e uma entrada na tabela: id, estagio minimo, peso, recarga e condicao. */
public final class Events {
	private static final Random R = Director.RNG;
	private static final List<Def> ALL = new ArrayList<>();

	/** Contexto de um evento: quem, onde, em que estado. */
	public record Ctx(MinecraftServer server, ServerPlayer p, ServerLevel level, PlayerState st) {
		public boolean overworld() {
			return level.dimension() == Level.OVERWORLD;
		}

		public int light() {
			return Compat.light(level, p.blockPosition());
		}

		public boolean dark() {
			return light() <= 7;
		}

		public boolean underground() {
			return overworld() && p.getY() < 52 && light() <= 7;
		}

		public int playersOnline() {
			return server.getPlayerList().getPlayers().size();
		}

		/** Posicao a "dist" blocos do jogador, num angulo relativo ao olhar (0 = frente, 180 = atras). */
		public double[] rel(double dist, double angleDeg) {
			double yaw = Math.toRadians(p.getYRot() + angleDeg);
			return new double[] { p.getX() - Math.sin(yaw) * dist, p.getY() + 1.0, p.getZ() + Math.cos(yaw) * dist };
		}
	}

	public record Def(String id, int minStage, int weight, int cooldownSec, boolean sleepOnly, Predicate<Ctx> cond,
			Predicate<Ctx> run) {
	}

	private Events() {
	}

	public static List<Def> all() {
		return Collections.unmodifiableList(ALL);
	}

	public static List<String> ids() {
		List<String> out = new ArrayList<>();
		for (Def d : ALL) {
			out.add(d.id());
		}
		return out;
	}

	public static Def byId(String id) {
		for (Def d : ALL) {
			if (d.id().equals(id)) {
				return d;
			}
		}
		return null;
	}

	private static void add(String id, int minStage, int weight, int cooldownSec, Predicate<Ctx> cond, Predicate<Ctx> run) {
		ALL.add(new Def(id, minStage, weight, cooldownSec, false, cond, run));
	}

	private static void addSleep(String id, int weight, int cooldownSec, Predicate<Ctx> run) {
		ALL.add(new Def(id, 0, weight, cooldownSec, true, c -> true, run));
	}

	private static final Predicate<Ctx> ALWAYS = c -> true;
	private static final Predicate<Ctx> OVERWORLD = Ctx::overworld;
	private static final Predicate<Ctx> DARK = Ctx::dark;
	private static final Predicate<Ctx> CHAT = c -> HorrorConfig.fakeChat
			&& (c.playersOnline() <= 1 || HorrorConfig.fakeChatMultiplayer);
	private static final Predicate<Ctx> APPARITION = c -> HorrorConfig.apparitions && c.overworld()
			&& !Apparitions.has(c.p().getUUID());

	static {
		// ---- estagio 0: sutil (som e texto) ----
		add("whisper", 0, 10, 45, ALWAYS, Events::whisper);
		add("breath", 0, 6, 90, ALWAYS, Events::breath);
		add("footsteps", 0, 8, 120, ALWAYS, Events::footsteps);
		add("knock", 0, 7, 90, ALWAYS, Events::knock);
		add("creak", 0, 7, 80, ALWAYS, Events::creak);
		add("scratch", 0, 5, 120, ALWAYS, Events::scratch);
		add("mining_walls", 0, 6, 150, c -> c.underground(), Events::miningWalls);
		add("cave_drone", 0, 6, 100, DARK, Events::caveDrone);
		add("thunder_far", 0, 4, 200, OVERWORLD, Events::thunderFar);
		add("whisper_text", 0, 8, 60, ALWAYS, Events::whisperText);
		add("fake_chat", 0, 6, 180, CHAT, Events::fakeChat);
		addSleep("sleep_whispers", 10, 40, Events::sleepWhispers);

		// ---- estagio 1: inquieto (sons que nao deviam existir, mundo mexendo) ----
		add("musicbox", 1, 4, 300, ALWAYS, Events::musicBox);
		add("distant_cry", 1, 5, 240, ALWAYS, Events::distantCry);
		add("growl", 1, 4, 200, DARK, Events::growl);
		add("fake_creeper", 1, 4, 200, ALWAYS, Events::fakeCreeper);
		add("bell", 1, 3, 300, OVERWORLD, Events::bell);
		add("explosion_far", 1, 3, 300, OVERWORLD, Events::explosionFar);
		add("fake_join", 1, 4, 300, CHAT, Events::fakeJoin);
		add("fake_advancement", 1, 3, 600, CHAT, Events::fakeAdvancement);
		add("blink", 1, 4, 180, ALWAYS, Events::blink);
		add("darkness_pulse", 1, 4, 180, ALWAYS, Events::darknessPulse);
		add("torch_snuff", 1, 5, 240, c -> HorrorConfig.worldTampering, Events::torchSnuff);
		add("watcher", 1, 6, 240, APPARITION, c -> Apparitions.spawn(c, Apparitions.Kind.STARE));

		// ---- estagio 2: assustado (aparicoes, efeitos fisicos) ----
		add("chorus", 2, 4, 240, ALWAYS, Events::chorus);
		add("static", 2, 4, 240, ALWAYS, Events::staticNoise);
		add("nausea", 2, 3, 240, ALWAYS, Events::nausea);
		add("paralysis", 2, 3, 300, ALWAYS, Events::paralysis);
		add("self_chat", 2, 4, 300, CHAT, Events::selfChat);
		add("fake_death", 2, 2, 900, CHAT, Events::fakeDeath);
		add("storm", 2, 2, 900, c -> c.overworld() && HorrorConfig.worldTampering, Events::storm);
		add("heartbeat", 2, 5, 150, ALWAYS, Events::heartbeat);
		add("ambush", 2, 4, 420, APPARITION, c -> Apparitions.spawn(c, Apparitions.Kind.AMBUSH));
		add("follower", 2, 3, 600, APPARITION, c -> Apparitions.spawn(c, Apparitions.Kind.FOLLOWER));

		// ---- estagio 3: panico ----
		add("dread_title", 3, 4, 240, ALWAYS, Events::dreadTitle);
		add("jumpscare", 3, 5, 300, c -> HorrorConfig.jumpscares, Events::jumpscare);
	}

	// ================================================================ utilidades

	private static double rnd(double lo, double hi) {
		return lo + R.nextDouble() * (hi - lo);
	}

	private static String pick(String... options) {
		return options[R.nextInt(options.length)];
	}

	private static void snd(Ctx c, String id, String category, double[] pos, double volume, double pitch) {
		Cmd.sound(c.server(), c.p(), id, category, pos[0], pos[1], pos[2], volume, pitch);
	}

	private static void sndSelf(Ctx c, String id, String category, double volume, double pitch) {
		Cmd.sound(c.server(), c.p(), id, category, c.p().getX(), c.p().getY() + 1.0, c.p().getZ(), volume, pitch);
	}

	// ================================================================ estagio 0

	private static boolean whisper(Ctx c) {
		snd(c, "sussurros:whisper", "ambient", c.rel(rnd(1.2, 3.5), rnd(120, 240)), 1.3, rnd(0.9, 1.1));
		return true;
	}

	private static boolean breath(Ctx c) {
		snd(c, "sussurros:breath", "ambient", c.rel(0.9, rnd(150, 210)), 1.0, 1.0);
		return true;
	}

	/** Passos que se aproximam por tras e param de repente. */
	private static boolean footsteps(Ctx c) {
		final double yaw = Math.toRadians(c.p().getYRot() + 180 + (R.nextDouble() - 0.5) * 50);
		final double dirX = -Math.sin(yaw);
		final double dirZ = Math.cos(yaw);
		final String step = pick("block.grass.step", "block.stone.step", "block.gravel.step", "block.sand.step");
		final int steps = 6 + R.nextInt(4);
		int t = 0;
		for (int i = 0; i < steps; i++) {
			final double dist = 10.0 - i;
			t += 8 + R.nextInt(4);
			Director.later(t, () -> {
				ServerPlayer p = c.p();
				Cmd.sound(c.server(), p, step, "hostile", p.getX() + dirX * dist, p.getY(), p.getZ() + dirZ * dist, 1.0,
						rnd(0.85, 1.05));
			});
		}
		if (R.nextInt(100) < 40) {
			Director.later(t + 30, () -> snd(c, "sussurros:breath", "ambient", c.rel(1.2, 180), 1.0, 1.0));
		}
		return true;
	}

	private static boolean knock(Ctx c) {
		snd(c, "sussurros:knock", "block", c.rel(rnd(3, 6), rnd(0, 360)), 1.6, 1.0);
		return true;
	}

	/** Uma porta, bau ou alcapao abre sozinho em algum lugar perto (e as vezes fecha de novo). */
	private static boolean creak(Ctx c) {
		final String open = pick("block.wooden_door.open", "block.wooden_trapdoor.open", "block.chest.open",
				"block.iron_door.open", "block.barrel.open", "block.fence_gate.open");
		final double[] pos = c.rel(rnd(5, 11), rnd(0, 360));
		snd(c, open, "block", pos, 1.4, rnd(0.8, 0.95));
		if (R.nextInt(100) < 55) {
			final String close = open.replace(".open", ".close");
			Director.later(30 + R.nextInt(60), () -> snd(c, close, "block", pos, 1.4, 0.85));
		}
		return true;
	}

	private static boolean scratch(Ctx c) {
		snd(c, "sussurros:scratch", "ambient", c.rel(rnd(2, 4), rnd(0, 360)), 1.2, 1.0);
		return true;
	}

	/** Alguem cavando dentro da pedra, perto de voce. */
	private static boolean miningWalls(Ctx c) {
		final double[] pos = c.rel(rnd(4, 8), rnd(0, 360));
		final String hit = c.p().getY() < 0 ? "block.deepslate.break" : "block.stone.break";
		final int hits = 3 + R.nextInt(4);
		int t = 0;
		for (int i = 0; i < hits; i++) {
			t += 10 + R.nextInt(14);
			Director.later(t, () -> snd(c, hit, "block", pos, 1.0, rnd(0.75, 0.95)));
		}
		return true;
	}

	private static boolean caveDrone(Ctx c) {
		sndSelf(c, "sussurros:drone", "ambient", 0.9, 1.0);
		return true;
	}

	private static boolean thunderFar(Ctx c) {
		snd(c, "minecraft:entity.lightning_bolt.thunder", "weather", c.rel(rnd(45, 70), rnd(0, 360)), 12.0, rnd(0.55, 0.8));
		return true;
	}

	private static boolean whisperText(Ctx c) {
		Cmd.actionbar(c.server(), c.p(), Lines.whisperText(), "gray");
		if (R.nextInt(100) < 40) {
			Director.later(10, () -> snd(c, "sussurros:whisper", "ambient", c.rel(2.0, 180), 0.8, 1.0));
		}
		return true;
	}

	/** Mensagem de um "jogador" que nao existe. */
	private static boolean fakeChat(Ctx c) {
		final String name = Lines.fakeName();
		Cmd.plainChat(c.server(), c.p(), "<" + name + "> " + Lines.fakeChat());
		if (R.nextInt(100) < 35) {
			Director.later(40 + R.nextInt(80), () -> Cmd.plainChat(c.server(), c.p(), "<" + name + "> " + Lines.fakeChat()));
		}
		return true;
	}

	/** Enquanto voce dorme: sussurros bem colados no ouvido. */
	private static boolean sleepWhispers(Ctx c) {
		snd(c, "sussurros:whisper", "ambient", c.rel(rnd(0.8, 1.6), rnd(0, 360)), 1.2, rnd(0.85, 1.0));
		if (R.nextInt(100) < 40) {
			Director.later(40, () -> sndSelf(c, "sussurros:heartbeat", "ambient", 0.6, 1.0));
		}
		return true;
	}

	// ================================================================ estagio 1

	private static boolean musicBox(Ctx c) {
		snd(c, "sussurros:musicbox", "ambient", c.rel(rnd(9, 16), rnd(0, 360)), 2.0, 1.0);
		return true;
	}

	private static boolean distantCry(Ctx c) {
		snd(c, "sussurros:cry", "ambient", c.rel(rnd(24, 34), rnd(0, 360)), 4.0, rnd(0.9, 1.05));
		return true;
	}

	private static boolean growl(Ctx c) {
		snd(c, "sussurros:growl", "hostile", c.rel(rnd(8, 14), rnd(0, 360)), 2.5, rnd(0.85, 1.05));
		return true;
	}

	private static boolean fakeCreeper(Ctx c) {
		snd(c, "minecraft:entity.creeper.primed", "hostile", c.rel(rnd(2.5, 3.5), rnd(140, 220)), 1.2, 1.0);
		return true;
	}

	/** Um sino toca tres vezes muito longe. */
	private static boolean bell(Ctx c) {
		final double[] pos = c.rel(rnd(40, 60), rnd(0, 360));
		for (int i = 0; i < 3; i++) {
			Director.later(1 + i * 70, () -> snd(c, "minecraft:block.bell.use", "ambient", pos, 8.0, 0.5));
		}
		return true;
	}

	private static boolean explosionFar(Ctx c) {
		snd(c, "minecraft:entity.generic.explode", "block", c.rel(rnd(40, 55), rnd(0, 360)), 6.0, rnd(0.55, 0.75));
		return true;
	}

	/** "Fulano entrou no jogo" - e algum tempo depois "Fulano saiu do jogo". */
	private static boolean fakeJoin(Ctx c) {
		final String name = Lines.fakeName();
		Cmd.tellraw(c.server(), c.p(),
				"{\"translate\":\"multiplayer.player.joined\",\"with\":[" + Cmd.q(name) + "],\"color\":\"yellow\"}");
		if (R.nextInt(100) < 70) {
			Director.later(20 * (15 + R.nextInt(45)), () -> Cmd.tellraw(c.server(), c.p(),
					"{\"translate\":\"multiplayer.player.left\",\"with\":[" + Cmd.q(name) + "],\"color\":\"yellow\"}"));
		}
		return true;
	}

	private static boolean fakeAdvancement(Ctx c) {
		Cmd.tellraw(c.server(), c.p(), "{\"translate\":\"chat.type.advancement.task\",\"with\":[" + Cmd.q(Lines.fakeName())
				+ ",{\"text\":" + Cmd.q(Lines.advancement()) + ",\"color\":\"green\"}]}");
		return true;
	}

	/** A tela escurece por um instante, como um piscar de olhos longo demais. */
	private static boolean blink(Ctx c) {
		Cmd.effect(c.server(), c.p(), "blindness", 2, 0);
		sndSelf(c, "sussurros:heartbeat", "ambient", 0.8, 1.1);
		return true;
	}

	private static boolean darknessPulse(Ctx c) {
		Cmd.effect(c.server(), c.p(), "darkness", 10, 0);
		sndSelf(c, "sussurros:drone", "ambient", 0.8, 1.0);
		return true;
	}

	private static boolean isTorch(BlockState s) {
		return s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.SOUL_TORCH) || s.is(Blocks.SOUL_WALL_TORCH);
	}

	/** As tochas ao redor se apagam uma a uma, da mais proxima para a mais distante. */
	private static boolean torchSnuff(Ctx c) {
		if (!HorrorConfig.worldTampering) {
			return false;
		}
		final ServerLevel level = c.level();
		final BlockPos origin = c.p().blockPosition();
		List<BlockPos> found = new ArrayList<>();
		for (int dx = -9; dx <= 9; dx++) {
			for (int dy = -4; dy <= 5; dy++) {
				for (int dz = -9; dz <= 9; dz++) {
					BlockPos bp = new BlockPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
					if (isTorch(level.getBlockState(bp))) {
						found.add(bp);
					}
				}
			}
		}
		if (found.isEmpty()) {
			return false;
		}
		found.sort(Comparator.comparingDouble(b -> {
			double ddx = b.getX() - origin.getX();
			double ddy = b.getY() - origin.getY();
			double ddz = b.getZ() - origin.getZ();
			return ddx * ddx + ddy * ddy + ddz * ddz;
		}));
		snd(c, "sussurros:whisper", "ambient", c.rel(2.0, 180), 1.0, 0.9);
		int t = 8;
		for (int i = 0; i < Math.min(found.size(), 9); i++) {
			final BlockPos b = found.get(i);
			t += 6 + R.nextInt(8);
			Director.later(t, () -> {
				if (isTorch(level.getBlockState(b))) {
					Cmd.as(c.server(), c.p(), "setblock " + b.getX() + " " + b.getY() + " " + b.getZ() + " minecraft:air");
					Cmd.sound(c.server(), c.p(), "minecraft:block.fire.extinguish", "block", b.getX() + 0.5, b.getY() + 0.5,
							b.getZ() + 0.5, 1.2, rnd(0.7, 0.9));
					Cmd.particle(c.server(), c.p(), "minecraft:smoke", b.getX() + 0.5, b.getY() + 0.7, b.getZ() + 0.5, 0.08,
							0.1, 0.08, 0.02, 8);
				}
			});
		}
		return true;
	}

	// ================================================================ estagio 2

	/** Vozes vindas de todos os lados ao mesmo tempo. */
	private static boolean chorus(Ctx c) {
		for (int i = 0; i < 5; i++) {
			final double angle = i * 72 + rnd(-15, 15);
			Director.later(1 + i * 8, () -> snd(c, "sussurros:whisper", "ambient", c.rel(rnd(2.5, 4.0), angle), 1.2, rnd(0.85, 1.1)));
		}
		return true;
	}

	private static boolean staticNoise(Ctx c) {
		sndSelf(c, "sussurros:static", "ambient", 1.0, 1.0);
		return true;
	}

	private static boolean nausea(Ctx c) {
		Cmd.effect(c.server(), c.p(), "nausea", 8, 0);
		sndSelf(c, "sussurros:whisper", "ambient", 1.0, 0.9);
		return true;
	}

	/** O corpo trava por alguns segundos enquanto o coracao dispara. */
	private static boolean paralysis(Ctx c) {
		Cmd.effect(c.server(), c.p(), "slowness", 4, 3);
		Cmd.effect(c.server(), c.p(), "darkness", 4, 0);
		sndSelf(c, "sussurros:heartbeat", "ambient", 1.0, 1.0);
		return true;
	}

	/** Uma mensagem no chat com o SEU nome, que voce nao escreveu. */
	private static boolean selfChat(Ctx c) {
		Cmd.plainChat(c.server(), c.p(), "<" + c.p().getName().getString() + "> " + Lines.selfChat());
		return true;
	}

	private static boolean fakeDeath(Ctx c) {
		final String name = Lines.fakeName();
		String json = switch (R.nextInt(3)) {
			case 0 -> "{\"translate\":\"death.attack.mob\",\"with\":[" + Cmd.q(name) + "," + Cmd.q("???") + "]}";
			case 1 -> "{\"translate\":\"death.fell.accident.generic\",\"with\":[" + Cmd.q(name) + "]}";
			default -> "{\"translate\":\"death.attack.drown\",\"with\":[" + Cmd.q(name) + "]}";
		};
		Cmd.tellraw(c.server(), c.p(), json);
		return true;
	}

	private static boolean storm(Ctx c) {
		Cmd.run(c.server(), "weather thunder 3000");
		snd(c, "minecraft:entity.lightning_bolt.thunder", "weather", c.rel(rnd(40, 60), rnd(0, 360)), 12.0, 0.6);
		return true;
	}

	/** Batimentos que disparam. As vezes, uma figura aparece atras de voce logo depois. */
	private static boolean heartbeat(Ctx c) {
		sndSelf(c, "sussurros:heartbeat", "ambient", 1.0, c.st().sanity < 25 ? 1.2 : 1.0);
		if (R.nextInt(100) < 25) {
			Director.later(100, () -> {
				ServerPlayer p = c.p();
				if (Director.valid(p) && HorrorConfig.apparitions && !Apparitions.has(p.getUUID())) {
					Ctx fresh = new Ctx(c.server(), p, (ServerLevel) p.level(), c.st());
					if (fresh.overworld()) {
						Apparitions.spawn(fresh, Apparitions.Kind.AMBUSH);
					}
				}
			});
		}
		return true;
	}

	// ================================================================ estagio 3

	private static boolean dreadTitle(Ctx c) {
		String[] t = Lines.title();
		Cmd.as(c.server(), c.p(), "title @s times 10 50 25");
		Cmd.as(c.server(), c.p(), "title @s subtitle {\"text\":" + Cmd.q(t[1]) + ",\"color\":\"dark_red\"}");
		Cmd.as(c.server(), c.p(), "title @s title {\"text\":" + Cmd.q(t[0]) + ",\"color\":\"red\",\"bold\":true}");
		sndSelf(c, "sussurros:static", "ambient", 0.8, 0.8);
		return true;
	}

	/** Som alto + escuridao total por um instante, seguido de batimentos e sussurros. */
	private static boolean jumpscare(Ctx c) {
		if (!HorrorConfig.jumpscares) {
			return false;
		}
		sndSelf(c, "sussurros:sting", "master", 1.0, 1.0);
		Cmd.effect(c.server(), c.p(), "blindness", 2, 0);
		Cmd.effect(c.server(), c.p(), "darkness", 8, 0);
		Director.later(20, () -> sndSelf(c, "sussurros:heartbeat", "ambient", 1.0, 1.1));
		Director.later(45, () -> snd(c, "sussurros:whisper", "ambient", c.rel(1.5, 180), 1.3, 0.9));
		c.st().sanity = Math.max(0.0, c.st().sanity - 3.0);
		return true;
	}

	// ================================================================ gatilhos externos

	/** Minerar algo valioso as vezes chama atencao. */
	public static void onBlockBroken(ServerPlayer p, BlockState state) {
		if (!HorrorConfig.enabled) {
			return;
		}
		boolean valuable = state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
				|| state.is(Blocks.ANCIENT_DEBRIS) || state.is(Blocks.EMERALD_ORE) || state.is(Blocks.DEEPSLATE_EMERALD_ORE);
		if (!valuable) {
			return;
		}
		PlayerState st = Director.state(p);
		if (!st.enabled || Director.exempt(p) || Director.tick < st.graceUntilTick) {
			return;
		}
		Long cd = st.cooldownUntil.get("greed");
		if (cd != null && cd > Director.tick) {
			return;
		}
		if (R.nextInt(100) >= 22) {
			return;
		}
		MinecraftServer server = p.level().getServer();
		if (server == null) {
			return;
		}
		st.cooldownUntil.put("greed", Director.tick + 20L * 240);
		st.sanity = Math.max(0.0, st.sanity - 1.5);
		Ctx c = new Ctx(server, p, (ServerLevel) p.level(), st);
		snd(c, "sussurros:whisper", "ambient", c.rel(rnd(1.5, 2.5), 180), 1.3, 0.9);
		Director.later(15, () -> Cmd.actionbar(server, p, Lines.greed(), "dark_red"));
	}
}
