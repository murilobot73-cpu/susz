package com.sussurros;

import java.lang.reflect.Method;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chamadas ao jogo feitas por reflexao, com valor reserva. Servem como rede de seguranca: se algum nome de
 * metodo mudar numa versao futura do Minecraft, o mod continua carregando e funcionando (so com um
 * comportamento um pouco mais simples) em vez de falhar.
 */
public final class Compat {
	private static Method lightMethod;
	private static boolean lightTried;
	private static Method occludeMethod;
	private static boolean occludeTried;

	private Compat() {
	}

	/** Nivel de luz (0-15) na posicao, considerando a escuridao da noite. 8 se nao for possivel ler. */
	public static int light(ServerLevel level, BlockPos pos) {
		try {
			if (!lightTried) {
				lightTried = true;
				lightMethod = Level.class.getMethod("getMaxLocalRawBrightness", BlockPos.class);
			}
			if (lightMethod != null) {
				return (Integer) lightMethod.invoke(level, pos);
			}
		} catch (Throwable ignored) {
			// usa o valor reserva
		}
		return 8;
	}

	/** True se o bloco bloqueia a visao (pedra, folhas...). Ar, grama alta, vidro e agua nao bloqueiam. */
	public static boolean occludes(BlockState state) {
		try {
			if (!occludeTried) {
				occludeTried = true;
				occludeMethod = BlockState.class.getMethod("canOcclude");
			}
			if (occludeMethod != null) {
				return (Boolean) occludeMethod.invoke(state);
			}
		} catch (Throwable ignored) {
			// usa o valor reserva
		}
		return !state.isAir();
	}
}
