package net.runelite.client.plugins.dncollect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.WorldLocation;


@Getter
public enum Store
{
	UGLUG(861,
	      new WorldArea(new WorldPoint(2436, 3044, 0), new WorldPoint(2448, 3060, 0)),
	      WorldLocation.CASTLE_WARS_BANK, ObjectID.BANK_CHEST_4483,
	      List.of(9775, 9776));
	
	private final int npcId;
	private final WorldArea storeLocation;
	private final WorldLocation bankLocation;
	private final Integer bankId;
	private final List<Integer> regionIds;
	
	Store(int npcId, WorldArea storeLocation, WorldLocation bankLocation, Integer bankId, List<Integer> regionIds)
	{
		this.npcId = npcId;
		this.storeLocation = storeLocation;
		this.bankLocation = bankLocation;
		this.bankId = bankId;
		this.regionIds = regionIds;
	}

	public static Map<Integer, Store> getStores()
	{
		Map<Integer, Store> stores = new HashMap<>();
		for (Store store : values())
		{
			stores.put(store.npcId, store);
		}

		return stores;
	}

	static enum Path
	{
		UGLUG();

		private final WorldPoint[] points;

		Path(WorldPoint... points)
		{
			this.points = points;
		}
	}
}
