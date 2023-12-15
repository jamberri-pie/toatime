package com.toatime;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("Toa Time")
public interface ToatimeConfig extends Config
{
	@ConfigItem(
		keyName = "Volume",
		name = "Volume",
		description = "The volume you play the sound effect."
	)
	default int volume()
	{
		return 100;
	}
}
