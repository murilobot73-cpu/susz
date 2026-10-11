package com.sussurros;

import java.util.HashMap;
import java.util.Map;

/** Estado de medo de um jogador (fica na memoria enquanto o mundo/servidor esta aberto). */
public final class PlayerState {
	/** 0 a 100. Cai no escuro, em cavernas e depois de eventos; sobe com luz, dia e dormindo. */
	public double sanity = 100.0;
	public boolean enabled = true;
	public long graceUntilTick;
	public long nextEventTick;
	public String lastEvent = "";
	public int eventsFired;
	public final Map<String, Long> cooldownUntil = new HashMap<>();
}
