package com.sussurros;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.server.level.ServerPlayer;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Sussurros do Vazio - mod de terror psicologico.
 *
 * Tudo roda no lado do servidor (funciona em mundo single player e em servidor dedicado),
 * sem mixins e sem registros novos: isso e de proposito, para o mod carregar no mundo sem erro.
 */
public class SussurrosMod implements ModInitializer {
	public static final String MOD_ID = "sussurros";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		HorrorConfig.load();

		ServerTickEvents.END_SERVER_TICK.register(Director::onTick);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			try {
				Director.onJoin(server, handler.getPlayer());
			} catch (Throwable t) {
				LOGGER.warn("Erro ao entrar no mundo (ignorado): {}", t.toString());
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			try {
				Director.onLeave(server, handler.getPlayer());
			} catch (Throwable t) {
				LOGGER.warn("Erro ao sair do mundo (ignorado): {}", t.toString());
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(Apparitions::clearAll);

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			try {
				if (player instanceof ServerPlayer serverPlayer) {
					Events.onBlockBroken(serverPlayer, state);
				}
			} catch (Throwable t) {
				LOGGER.warn("Erro no evento de quebra de bloco (ignorado): {}", t.toString());
			}
		});

		HorrorCommand.register();

		LOGGER.info("Sussurros do Vazio carregado. Voce nao esta sozinho.");
	}
}
