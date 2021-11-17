/*
 * Copyright (c) 2018, SomeoneWithAnInternetConnection
 * Copyright (c) 2018, oplosthee <https://github.com/oplosthee>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.dncollect;

import com.google.inject.Provides;
import com.owain.chinbreakhandler.ChinBreakHandler;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemDefinition;
import net.runelite.api.ItemID;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuOpcode;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.QueryResults;
import net.runelite.api.Varbits;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ConfigButtonClicked;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.queries.BankItemQuery;
import net.runelite.api.queries.GameObjectQuery;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemMapping;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginType;
import net.runelite.client.plugins.iutils.BankUtils;
import net.runelite.client.plugins.iutils.CalculationUtils;
import net.runelite.client.plugins.iutils.InventoryUtils;
import net.runelite.client.plugins.iutils.MenuUtils;
import net.runelite.client.plugins.iutils.MouseUtils;
import net.runelite.client.plugins.iutils.NPCUtils;
import net.runelite.client.plugins.iutils.ObjectUtils;
import net.runelite.client.plugins.iutils.iUtils;
import net.runelite.client.ui.overlay.OverlayManager;
import org.apache.commons.lang3.ArrayUtils;
import org.pf4j.Extension;

@Extension
@PluginDependency(iUtils.class)
@PluginDescriptor(
	name = "DN Collect",
	description = "DN Shop Collector",
	enabledByDefault = false,
	tags = {"shop", "store", "dn"},
	type = PluginType.UTILITY
)
@Slf4j
public class DNCollectPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private DNCollectConfig config;

	@Inject
	private ScheduledExecutorService scheduledExecutorService;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ChinBreakHandler chinBreakHandler;
	
	@Inject
	private BankUtils bank;

	@Inject
	private CalculationUtils calc;

	@Inject
	private InventoryUtils inventory;
	
	@Inject
	private iUtils utils;
	
	@Inject
	private MenuUtils menu;
	
	@Inject
	private MouseUtils mouse;
	
	@Inject
	private NPCUtils npc;

	public Instant startTime;

	private boolean enabled;
	private long sleepDelay;
	private State state;
	private int ringsChargesUsed;
	private MenuEntry menuEntry;
	private Store store;
	private LocalPoint prevLocal = new LocalPoint(0, 0);
	private List<Item> withdrawList = new ArrayList<>();
	private Integer depositItem = 0;
	private int purchased;
	private int startingGP;
	private static Map<Integer, Store> stores = Store.getStores();
	private static final List<Integer> ringOfDuelingList = Arrays.stream(ArrayUtils.addAll(
			ItemMapping.ITEM_RING_OF_DUELING.getUntradableItems(),
			ItemMapping.ITEM_RING_OF_DUELING.getTradeableItem())
		).boxed().collect(Collectors.toList()); // TODO: not this.

	@Provides
	DNCollectConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DNCollectConfig.class);
	}
	
	@Override
	protected void startUp()
	{
		stopState();
	}

	@Override
	protected void shutDown()
	{
		stopState();
		chinBreakHandler.unregisterPlugin(this);
	}

	private void startState()
	{
		chinBreakHandler.registerPlugin(this);
		enabled = true;
		startTime = Instant.now();
		sleepDelay = 0;
		store = Store.UGLUG; // config.getStore();
		determineStartState();
	}

	private void stopState()
	{
		prevLocal = new LocalPoint(0, 0);
		enabled = false;
		purchased = 0;
		startingGP = 0;
		ringsChargesUsed = 0;
	}
	
	@Subscribe
	private void onConfigButtonPressed(ConfigButtonClicked event)
	{
		if (!event.getGroup().equals("DN Collect")
			|| !event.getKey().equals("startButton")
			|| !(client.getLocalPlayer() == null && client.getGameState() != GameState.LOGGED_IN))
			return;

		if (!enabled)
			startState();

		else
			stopState();

	}
	
	@Subscribe
	private void onGameTick(GameTick event)
	{
		if (!enabled || chinBreakHandler.isBreakActive(this))
			return;

	}
	
	@Subscribe
	private void onGameStateChanged(GameStateChanged event)
	{
		if (!enabled || chinBreakHandler.isBreakActive(this))
			return;
		
		if (event.getGameState() != GameState.LOGGED_IN)
		{
			// if (
		}
	}
	
	@Subscribe
	private void onInteractChanged(InteractChanged event)
	{
		if (!enabled || chinBreakHandler.isBreakActive(this))
			return;

	}

	@Subscribe
	private void onWidgetLoaded(WidgetLoaded event)
	{
		if (!enabled || chinBreakHandler.isBreakActive(this))
			return;

		assert client.isClientThread();

		if (event.getGroupId() == 270);

	}

	private void determineStartState()
	{
		assert client.getLocalPlayer() != null;

		if (!Store.UGLUG.getBankLocation().getWorldArea().canMelee(
				client,
				client.getLocalPlayer().getWorldLocation().toWorldArea()))
			return;

		if (!isWearingRing())
			if (inventory.getAllItemIDs().removeIf(ringOfDuelingList::contains))
				log.info("he do contain it."); // equipRingOfDueling()

			else
				withdrawList.add(new Item(ItemID.RING_OF_DUELING8, 1));

		if (!inventory.containsStackAmount(ItemID.COINS_995, startingGP))
			withdrawList.add(new Item(ItemID.COINS_995, startingGP));

		GameObject bank = new GameObjectQuery().idEquals(store.getBankId()).result(client).first();
		assert bank != null;

		menuEntry = new MenuEntry("Use", "<col=ffff>Bank chest",
		                          store.getBankId(),
		                          MenuOpcode.GAME_OBJECT_FIRST_OPTION.getId(),
		                          bank.getSceneMinLocation().getX(),
		                          bank.getSceneMinLocation().getY(), false);
		menu.setEntry(menuEntry);
		mouse.delayMouseClick(bank.getConvexHull().getBounds(), sleepDelay());
	}

	private boolean isWearingRing()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);

		if (equipment == null)
			return false;

		for (Item item : equipment.getItems())
		{
			if (ringOfDuelingList.contains(item.getId()))
				return true;

		}

		return false;
	}

	private final Runnable setViewAllTabs = () ->
	{
		if (!bank.isOpen())
			return;

		state = State.BANK_VIEW_ALL_TABS;

		if (inventory.isEmpty())
		{
			WidgetItem bankItem = new BankItemQuery()
				.filter((item) -> item.getQuantity() > 0).result(client).first();

			if (bankItem != null)
				bank.withdrawItem(bankItem.getId()); // #TODO queue this item for being redeposited
		}
		else
		{
			for (WidgetItem item : inventory.getAllItems())
			{
				if (item.getId() == ItemID.COINS_995)
				{
					bank.depositOneOfItem(item.getId()); // deposit 1 coin? i hope?
					depositItem = ItemID.COINS_995;
					break;
				}
				else
				{
					bank.depositOneOfItem(item.getId());
					depositItem = item.getId();
					break;
				}
			}

			if (depositItem == 0)
			{
				enabled = false;
				log.info("Man has no money, bank items, or inventory items. He can go grab some bones");
			}
		}

		client.setVarbit(Varbits.CURRENT_BANK_TAB, 0);
	};

	private long sleepDelay()
	{
		sleepDelay = (calc.randomDelay(
			config.sleepWeightedDistribution(),
			config.sleepMin(),
			config.sleepMax(),
			config.sleepDeviation(),
			config.sleepTarget())) + 600;
		return sleepDelay;
	}

	enum State
	{
		BANK_OPENING,
		BANK_FIND_RING_OF_DUELING,
		BANK_VIEW_ALL_TABS,
		BANK_WITHDRAW_RING_OF_DUELING,
		TRAVELING,
		SHOP_OPENING,
		SHOP_PURCHASING,
		HOPPING,
		TELEPORTING
	}
}