package com.sussurros;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Aparicoes: figuras silenciosas (Enderman ou aldeao sem IA) que existem so para assustar.
 *
 * STARE    - fica parada ao longe te encarando; some quando voce a encara ou chega perto.
 * AMBUSH   - aparece colada atras de voce; some (com um susto) assim que voce se vira.
 * FOLLOWER - so se mexe quando voce NAO esta olhando (estilo "estatua"); ao chegar, da um susto.
 *
 * Elas sao invulneraveis, mudas, sem IA e nao atacam: o dano e todo psicologico.
 */
public final class Apparitions {
	public enum Kind { STARE, AMBUSH, FOLLOWER }

	private enum End { SILENT, SOFT, SCARE }

	private static final class Apparition {
		final UUID player;
		final UUID entity;
		final Kind kind;
		double x;
		double y;
		double z;
		int age;
		int seen;
		int stepTimer;

		Apparition(UUID player, UUID entity, Kind kind, double x, double y, double z) {
			this.player = player;
			this.entity = entity;
			this.kind = kind;
			this.x = x;
			this.y = y;
			this.z = z;
		}
	}

	private static final List<Apparition> LIST = new ArrayList<>();
	private static final Random R = Director.RNG;
	private static final String TAG = "sussurros_apparition";

	private Apparitions() {
	}

	public static boolean has(UUID player) {
		for (Apparition a : LIST) {
			if (a.player.equals(player)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- criar

	public static boolean spawn(Events.Ctx c, Kind kind) {
		ServerPlayer p = c.p();
		ServerLevel level = c.level();
		MinecraftServer server = c.server();
		if (has(p.getUUID())) {
			return false;
		}

		double[] pos = null;
		for (int attempt = 0; attempt < 14 && pos == null; attempt++) {
			double dist;
			double angle;
			switch (kind) {
				case AMBUSH -> {
					dist = 3.0 + R.nextDouble() * 0.6;
					angle = 180 + (R.nextDouble() - 0.5) * 50;
				}
				case FOLLOWER -> {
					dist = 25 + R.nextDouble() * 6;
					angle = 100 + R.nextDouble() * 160;
				}
				default -> {
					dist = 22 + R.nextDouble() * 9;
					angle = (R.nextBoolean() ? 1 : -1) * (65 + R.nextDouble() * 75);
				}
			}
			double[] around = c.rel(dist, angle);
			double gy = findGround(level, around[0], p.getY(), around[2]);
			if (Double.isNaN(gy) || Math.abs(gy - p.getY()) > 8) {
				continue;
			}
			pos = new double[] { around[0], gy, around[2] };
		}
		if (pos == null) {
			return false;
		}

		boolean villager = kind != Kind.AMBUSH && R.nextInt(100) < 20;
		String type = villager ? "minecraft:villager" : "minecraft:enderman";
		UUID id = UUID.randomUUID();
		long most = id.getMostSignificantBits();
		long least = id.getLeastSignificantBits();
		String nbt = "{NoAI:1b,Silent:1b,Invulnerable:1b,NoGravity:1b,Tags:[\"" + TAG + "\"],DeathLootTable:\"minecraft:empty\","
				+ "UUID:[I;" + (int) (most >> 32) + "," + (int) most + "," + (int) (least >> 32) + "," + (int) least + "]}";
		Cmd.as(server, p, "summon " + type + " " + Cmd.n(pos[0]) + " " + Cmd.n(pos[1]) + " " + Cmd.n(pos[2]) + " " + nbt);

		Entity e = level.getEntity(id);
		if (e == null) {
			// o comando nao criou a figura com o UUID esperado: limpa qualquer sobra e desiste
			Cmd.run(server, "execute as " + p.getUUID() + " at @s positioned " + Cmd.n(pos[0]) + " " + Cmd.n(pos[1]) + " "
					+ Cmd.n(pos[2]) + " run kill @e[tag=" + TAG + ",distance=..3]");
			return false;
		}

		Apparition a = new Apparition(p.getUUID(), id, kind, pos[0], pos[1], pos[2]);
		face(e, p, a);
		LIST.add(a);

		if (kind == Kind.AMBUSH) {
			// um sinal sutil: uma respiracao bem perto, para quem estiver atento
			Cmd.sound(server, p, "sussurros:breath", "ambient", a.x, a.y + 1.5, a.z, 1.0, 1.0);
		}
		return true;
	}

	/** Procura o chao mais proximo da altura de referencia: 3 blocos de ar livres em cima de um bloco solido, sem agua. */
	static double findGround(ServerLevel level, double x, double refY, double z) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int ref = (int) Math.floor(refY);
		for (int k = 0; k <= 10; k++) {
			for (int sign = 0; sign < 2; sign++) {
				if (k == 0 && sign == 1) {
					continue;
				}
				int y = sign == 0 ? ref + k : ref - k;
				BlockPos feet = new BlockPos(bx, y, bz);
				if (level.getBlockState(feet).isAir()
						&& level.getBlockState(feet.above()).isAir()
						&& level.getBlockState(feet.above(2)).isAir()
						&& !level.getBlockState(feet.below()).isAir()
						&& level.getBlockState(feet.below()).getFluidState().isEmpty()) {
					return y;
				}
			}
		}
		return Double.NaN;
	}

	// ---------------------------------------------------------------- ciclo

	public static void tick(MinecraftServer server) {
		if (LIST.isEmpty()) {
			return;
		}
		for (Iterator<Apparition> it = LIST.iterator(); it.hasNext();) {
			Apparition a = it.next();
			boolean keep;
			try {
				keep = step(server, a);
			} catch (Throwable t) {
				SussurrosMod.LOGGER.warn("Aparicao falhou (removida): {}", t.toString());
				discard(server, a.entity);
				keep = false;
			}
			if (!keep) {
				it.remove();
			}
		}
	}

	/** Retorna false quando a aparicao terminou (e a entidade ja foi removida). */
	private static boolean step(MinecraftServer server, Apparition a) {
		ServerPlayer p = Director.playerById(server, a.player);
		if (!Director.valid(p)) {
			discard(server, a.entity);
			return false;
		}
		ServerLevel level = (ServerLevel) p.level();
		Entity e = level.getEntity(a.entity);
		if (e == null) {
			// sumiu sozinha ou o jogador mudou de dimensao
			discard(server, a.entity);
			return false;
		}
		a.age += 2;

		double ex = p.getX();
		double ey = p.getEyeY();
		double ez = p.getZ();
		double tx = a.x;
		double ty = a.y + 1.8;
		double tz = a.z;
		double dx = tx - ex;
		double dy = ty - ey;
		double dz = tz - ez;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double hdist = Math.sqrt(dx * dx + dz * dz);

		double yaw = Math.toRadians(p.getYRot());
		double pitch = Math.toRadians(p.getXRot());
		double lx = -Math.sin(yaw) * Math.cos(pitch);
		double ly = -Math.sin(pitch);
		double lz = Math.cos(yaw) * Math.cos(pitch);
		double dot = dist < 0.001 ? 1.0 : (lx * dx + ly * dy + lz * dz) / dist;

		boolean clear = dot > 0.5 && los(level, ex, ey, ez, tx, ty, tz, dist);
		boolean staring = clear && dot > 0.8;   // olhando de frente
		boolean peripheral = clear && dot > 0.6; // pelo canto do olho

		switch (a.kind) {
			case STARE -> {
				if (hdist < 8) {
					vanish(server, p, a, e, End.SOFT);
					return false;
				}
				if (staring) {
					a.seen += 2;
					if (a.seen >= 30) {
						vanish(server, p, a, e, End.SOFT);
						return false;
					}
				} else if (a.seen > 0) {
					a.seen--;
				}
				if (a.age > 20 * 50) {
					vanish(server, p, a, e, End.SILENT);
					return false;
				}
				if (a.age % 10 == 0) {
					face(e, p, a);
				}
			}
			case AMBUSH -> {
				if (peripheral) {
					vanish(server, p, a, e, End.SCARE);
					return false;
				}
				if (a.age > 20 * 7) {
					vanish(server, p, a, e, End.SOFT);
					return false;
				}
			}
			case FOLLOWER -> {
				if (a.age > 20 * 100) {
					vanish(server, p, a, e, End.SILENT);
					return false;
				}
				if (hdist < 2.4) {
					vanish(server, p, a, e, End.SCARE);
					return false;
				}
				if (peripheral) {
					// congelada enquanto voce olha; se voce a encara de perto por muito tempo, ela desiste
					a.seen += 2;
					if (hdist < 14 && a.seen > 90) {
						vanish(server, p, a, e, End.SOFT);
						return false;
					}
				} else {
					double speed = 0.20 + (Director.stage(Director.state(p).sanity) >= 3 ? 0.08 : 0.0);
					// dx/dz apontam do jogador para a figura; subtrair aproxima a figura do jogador
					double nx = a.x - (dx / hdist) * speed;
					double nz = a.z - (dz / hdist) * speed;
					double gy = findGround(level, nx, a.y, nz);
					if (!Double.isNaN(gy)) {
						a.y = gy;
					}
					a.x = nx;
					a.z = nz;
					e.setPos(a.x, a.y, a.z);
					face(e, p, a);
					a.stepTimer += 2;
					if (a.stepTimer >= 8) {
						a.stepTimer = 0;
						if (hdist > 14) {
							return true; // longe demais para ouvir; evita comandos de som sem alcance
						}
						String step = R.nextBoolean() ? "block.grass.step" : "block.stone.step";
						Cmd.sound(server, p, step, "hostile", a.x, a.y, a.z, 0.8, 0.8 + R.nextDouble() * 0.2);
					}
				}
			}
		}
		return true;
	}

	private static void vanish(MinecraftServer server, ServerPlayer p, Apparition a, Entity e, End how) {
		Cmd.particle(server, p, "minecraft:large_smoke", a.x, a.y + 1.2, a.z, 0.3, 0.9, 0.3, 0.01, 18);
		End end = how;
		if (end == End.SCARE && !HorrorConfig.jumpscares) {
			end = End.SOFT;
		}
		switch (end) {
			case SOFT -> Cmd.sound(server, p, "sussurros:whisper", "ambient", a.x, a.y + 1.5, a.z, 3.0, 1.0);
			case SCARE -> {
				Cmd.sound(server, p, "sussurros:sting", "master", p.getX(), p.getY(), p.getZ(), 1.0, 1.0);
				Cmd.effect(server, p, "darkness", 6, 0);
				Cmd.effect(server, p, "blindness", 1, 0);
				Director.state(p).sanity = Math.max(0.0, Director.state(p).sanity - 4.0);
				Director.later(14, () -> {
					if (Director.valid(p)) {
						Cmd.sound(server, p, "sussurros:heartbeat", "ambient", p.getX(), p.getY(), p.getZ(), 1.0, 1.0);
					}
				});
			}
			default -> {
			}
		}
		e.discard();
	}

	/** Gira a figura para olhar para o jogador. */
	private static void face(Entity e, ServerPlayer p, Apparition a) {
		double dx = p.getX() - a.x;
		double dz = p.getZ() - a.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		e.setYRot(yaw);
		e.setXRot(0.0F);
		if (e instanceof LivingEntity living) {
			living.setYHeadRot(yaw);
			living.setYBodyRot(yaw);
		}
	}

	/** Linha de visao simples: blocos que bloqueiam a vista entre o jogador e a figura. */
	private static boolean los(ServerLevel level, double ex, double ey, double ez, double tx, double ty, double tz, double dist) {
		int steps = (int) (dist / 1.5);
		for (int i = 1; i < steps; i++) {
			double f = (double) i / steps;
			BlockPos bp = BlockPos.containing(ex + (tx - ex) * f, ey + (ty - ey) * f, ez + (tz - ez) * f);
			if (Compat.occludes(level.getBlockState(bp))) {
				return false;
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- limpeza

	/** Remove a entidade em qualquer dimensao. */
	private static void discard(MinecraftServer server, UUID entityId) {
		ServerLevel[] levels = { server.getLevel(Level.OVERWORLD), server.getLevel(Level.NETHER), server.getLevel(Level.END) };
		for (ServerLevel level : levels) {
			if (level == null) {
				continue;
			}
			try {
				Entity e = level.getEntity(entityId);
				if (e != null) {
					e.discard();
				}
			} catch (Throwable ignored) {
				// ja removida
			}
		}
	}

	public static void clearFor(MinecraftServer server, UUID player) {
		for (Iterator<Apparition> it = LIST.iterator(); it.hasNext();) {
			Apparition a = it.next();
			if (a.player.equals(player)) {
				discard(server, a.entity);
				it.remove();
			}
		}
	}

	public static void clearAll(MinecraftServer server) {
		for (Apparition a : LIST) {
			discard(server, a.entity);
		}
		LIST.clear();
	}
}
