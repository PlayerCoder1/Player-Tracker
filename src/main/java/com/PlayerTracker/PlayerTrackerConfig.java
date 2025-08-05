package com.PlayerTracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("worldlocation")
public interface PlayerTrackerConfig extends Config
{
    @ConfigItem(
            keyName = "logActivity",
            name = "Log Activity",
            description = "Logs player animation/activity status"
    )
    default boolean logActivity()
    {
        return true;
    }

    @ConfigItem(
            keyName = "logEquipment",
            name = "Log Equipment",
            description = "Logs player's worn gear"
    )
    default boolean logEquipment()
    {
        return true;
    }
}
