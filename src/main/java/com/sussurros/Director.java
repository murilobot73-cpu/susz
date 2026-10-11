package com.sussurros;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Cerebro do mod: sanidade, temporizador de eventos e agenda de tarefas atrasadas. */
public final class Director {
	public static final Random RNG = new Random();

	/** Contador de ticks do servidor (20 ticks = 1 segundo). */
	public static long tick = 0;

	private static final Map<UUID, PlayerState> STATES = new HashMap<>();
	private static final List<Task> TASKS = new ArrayList<>();

	private record Task(long at, Runnable run) {
	}

	private Director() {
	}

	// ---------------------------------------------------------------- agenda

	/** Executa o codigo depois de N ticks. */
	public static void later(int delayTicks, Runnable run) {
		TASKS.add(new Task(tick + Math.max(1, delayTicks), run));
	}

	public static boolean valid(ServerPlayer p) {
		return p != null && !p.isRemoved() && p.isAlive();
	}

	// ---------------------------------------------------------------- estado

	public static PlayerState state(ServerPlayer p) {
		return STATES.computeIfAbsent(p.getUUID(), id -> new PlayerState());
	}

	public static ServerPlayer playerById(MinecraftServer server, UUID id) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.getUUID().equals(id)) {
				return p;
			}
		}
		return null;
	}

	/** 0 = sutil, 1 = inquieto, 2 = assustado, 3 = em panico. */
	public static int stage(double sanity) {
		if (sanity >= 75) {
			return 0;
		}
		if (sanity >= 50) {
			return 1;
		}
		if (sanity >= 25) {
			return 2;
		}
		return 3;
	}

	// ---------------------------------------------------------------- entrada/saida

	public static void onJoin(MinecraftServer server, ServerPlayer p) {
		PlayerState st = state(p);
		st.graceUntilTick = tick + HorrorConfig.graceMinutes * 60L * 20L;
		st.nextEventTick = st.graceUntilTick + 20L * (20 + RNG.nextInt(60));
		// Remove figuras que ficaram salvas no mundo se o jogo foi fechado no meio de uma aparicao.
		Cmd.run(server, "execute if entity @e[tag=sussurros_apparition] run kill @e[tag=sussurros_apparition]");
		if (HorrorConfig.welcomeMessage && HorrorConfig.enabled) {
			Cmd.tellraw(server, p, "{\"text\":" + Cmd.q(Lines.welcome()) + ",\"color\":\"dark_gray\",\"italic\":true}");
		}
	}

	public static void onLeave(MinecraftServer server, ServerPlayer p) {
		Apparitions.clearFor(server, p.getUUID());
	}

	// ---------------------------------------------------------------- tick

	public static void onTick(MinecraftServer server) {
		tick++;

		if (!TASKS.isEmpty()) {
			List<Task> due = new ArrayList<>();
			for (Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
				Task t = it.next();
				if (t.at() <= tick) {
					due.add(t);
					it.remove();
				}
			}
			for (Task t : due) {
				try {
					t.run().run();
				} catch (Throwable e) {
					SussurrosMod.LOGGER.warn("Tarefa de evento falhou (ignorada): {}", e.toString());
				}
			}
		}

		if (tick % 2 == 0) {
			Apparitions.tick(server);
		}

		if (tick % 20 == 0 && HorrorConfig.enabled) {
			for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) {
				try {
					secondTick(server, p);
				} catch (Throwable e) {
					SussurrosMod.LOGGER.warn("Erro no ciclo de medo (ignorado): {}", e.toString());
				}
			}
		}
	}

	public static boolean exempt(ServerPlayer p) {
		return !HorrorConfig.affectCreative && (p.isCreative() || p.isSpectator());
	}

	private static void secondTick(MinecraftServer server, ServerPlayer p) {
		if (!valid(p)) {
			return;
		}
		PlayerState st = state(p);
		ServerLevel level = (ServerLevel) p.level();
		updateSanity(level, p, st);

		if (!st.enabled || exempt(p)) {
			return;
		}
		if (tick < st.graceUntilTick || tick < st.nextEventTick) {
			return;
		}
		Events.Ctx ctx = new Events.Ctx(server, p, level, st);
		boolean fired = fire(ctx);
		// Se nada pode acontecer agora (condicoes do mundo), tenta de novo em alguns segundos.
		st.nextEventTick = tick + (fired ? interval(st.sanity) : 20L * (8 + RNG.nextInt(8)));
	}

	private static void updateSanity(ServerLevel level, ServerPlayer p, PlayerState st) {
		int light = Compat.light(level, p.blockPosition());
		boolean overworld = level.dimension() == Level.OVERWORLD;
		double d;
		if (p.isSleeping()) {
			d = 0.25;
		} else if (light <= 3) {
			d = -0.16;
		} else if (light <= 7) {
			d = -0.05;
		} else if (light >= 12) {
			d = 0.10;
		} else {
			d = 0.02;
		}
		if (!overworld) {
			d -= 0.04;
		} else if (p.getY() < 0) {
			d -= 0.05;
		}
		if (d < 0) {
			d *= HorrorConfig.sanityDrain;
		}
		st.sanity = Math.max(0.0, Math.min(100.0, st.sanity + d));
	}

	/** Ticks ate o proximo evento: ~3,5 min com a mente sa, ~40 s no limite da loucura. */
	public static long interval(double sanity) {
		double base = 40.0 + (210.0 - 40.0) * (sanity / 100.0);
		double jitter = 0.6 + RNG.nextDouble() * 0.8;
		double seconds = base * jitter / HorrorConfig.intensity;
		return (long) (Math.max(8.0, seconds) * 20.0);
	}

	// ---------------------------------------------------------------- escolha do evento

	/** Sorteia e executa um evento adequado ao momento. Retorna true se algum rodou. */
	public static boolean fire(Events.Ctx c) {
		PlayerState st = c.st();
		int stage = stage(st.sanity);
		boolean sleeping = c.p().isSleeping();

		List<Events.Def> pool = new ArrayList<>();
		for (Events.Def d : Events.all()) {
			if (d.sleepOnly() != sleeping || d.minStage() > stage) {
				continue;
			}
			Long cd = st.cooldownUntil.get(d.id());
			if ((cd != null && cd > tick) || d.id().equals(st.lastEvent)) {
				continue;
			}
			try {
				if (!d.cond().test(c)) {
					continue;
				}
			} catch (Throwable e) {
				continue;
			}
			pool.add(d);
		}

		for (int attempt = 0; attempt < 4 && !pool.isEmpty(); attempt++) {
			Events.Def d = weighted(pool);
			pool.remove(d);
			boolean ok = false;
			try {
				ok = d.run().test(c);
			} catch (Throwable e) {
				SussurrosMod.LOGGER.warn("Evento '{}' falhou (ignorado): {}", d.id(), e.toString());
			}
			if (ok) {
				markFired(st, d);
				return true;
			}
		}
		return false;
	}

	public static void markFired(PlayerState st, Events.Def d) {
		st.lastEvent = d.id();
		st.eventsFired++;
		st.cooldownUntil.put(d.id(), tick + d.cooldownSec() * 20L);
		// cada susto deixa o jogador um pouco mais paranoico
		st.sanity = Math.max(0.0, st.sanity - 0.6);
	}

	private static Events.Def weighted(List<Events.Def> pool) {
		int total = 0;
		for (Events.Def d : pool) {
			total += d.weight();
		}
		int r = RNG.nextInt(Math.max(1, total));
		for (Events.Def d : pool) {
			r -= d.weight();
			if (r < 0) {
				return d;
			}
		}
		return pool.get(pool.size() - 1);
	}
}
