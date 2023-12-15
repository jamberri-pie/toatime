package com.toatime;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;
import javax.inject.Inject;
import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Toa Time"
)
public class ToatimePlugin extends Plugin
{
	public Clip clip;
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ToatimeConfig config;

	@Getter
	@VisibleForTesting
	private boolean inToaRaid;
	private String callout = null;

	private static final int SKULL_1_GRAPHICS_OBJECT_ID = 2134;
	private static final int SKULL_2_GRAPHICS_OBJECT_ID = 2135;
	private static final int SIGHT_SOUND_EFFECT_ID = 6574;
	private static final int PLAYER_SIGHT_ANIMATION_ID = 2132;
	private static final int PLAYER_DD_ID = 2137;
	private static final int PLAYER_GIVEN_SIGHT_ID = 2132;
	private static final int PLAYER_REMOVE_SIGHT_ID = 2133;
	private static final int TOA_APMEKEN_ID = 15186;
	private static final int TOA_LOBBY_ID = 13454;
	private static final int TOA_NEXUS_ID = 14160;
	private static final LocalPoint PILLAR_LOCATION_POINT = new LocalPoint(7744, 7232);
	private static final LocalPoint VENT_LOCATION_POINT = new LocalPoint(7232, 5696);

	public ToatimePlugin() {}

	@Override
	protected void startUp() throws Exception
	{
		inToaRaid = false;

		// if the plugin was turned on inside the raid, check if inside the raid
		clientThread.invokeLater(this::checkInRaid);
	}

	@Override
	protected void shutDown() throws Exception
	{
		inToaRaid = false;
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed soundEffectPlayed) {
		if(!inToaRaid) return;
		if (soundEffectPlayed.getSoundId() == SIGHT_SOUND_EFFECT_ID) {
			checkSightTiles();
			log.info(callout);
			switch (callout) {
				case "PILLARS": 		playSound("pillars");
				client.addChatMessage(ChatMessageType.PUBLICCHAT, "Craig King", "Pillars, pillars, pillars.", null);
										break;
				case "VENTS": 			playSound("vents");
				client.addChatMessage(ChatMessageType.PUBLICCHAT, "Craig King", "Vents, vents, vents.", null);
										break;
				case "DD": 				playSound("DD");
										break;
				case "SIGHT_CHANGE": 	//playSound("sight_change");
										break;
				default: 				break;
			}
		}
	}

	/**
	 * Listener for events to see if in raid
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged varbitValueChanged) {
		checkInRaid();
	}

	/**
	 * checkInRaid() checks whether the player is or is not in a raid, in which case
	 * the plugin will not be active for the sound effects.
	 */
	private void checkInRaid() {
		if(client.getGameState() != GameState.LOGGED_IN) return;
		if(getRegion() == TOA_LOBBY_ID) resetState();
		if (getRegion() == TOA_NEXUS_ID || getRegion() == TOA_APMEKEN_ID) {
			inToaRaid = true;
		}
		else resetState();
	}

	/**
	 * resetState() resets the raid state by setting their in raid status to false and the invocation level to 0.
	 */
	private void resetState() {
		inToaRaid = false;
		callout = null;
	}

	/**
	 * Gets the map region ID of the local player's location and returns it.
	 * @return Map region ID.
	 */
	private int getRegion() {
		LocalPoint localPoint = client.getLocalPlayer().getLocalLocation();
		return WorldPoint.fromLocalInstance(client, localPoint).getRegionID();
	}

	/**
	 * Gets a list of all currently drawn GraphicObjects. If Sight Skull is drawn, checks the location of it to
	 * determine whether it is pillars or vents. If no Sight Skull, checks if all raiding player's Spot Animation has
	 * changed to either DD or Sight Change
	 */
	private void checkSightTiles() {
		log.info("Checking Sight Tiles");
		Deque<GraphicsObject> graphicObjects = client.getGraphicsObjects();
		graphicObjects.forEach(graphicsObject -> {
			log.info("GraphicObjectID: " + graphicsObject.getId());
			if (graphicsObject.getId() == SKULL_1_GRAPHICS_OBJECT_ID
					|| graphicsObject.getId() == SKULL_2_GRAPHICS_OBJECT_ID) {
				log.info("Recognized pillar/vent skulls at location: " + graphicsObject.getLocation());
				if (graphicsObject.getLocation().equals(PILLAR_LOCATION_POINT)) {
					log.info("New callout: PILLARS");
					callout = "PILLARS";
					return;
				} else if (graphicsObject.getLocation().equals(VENT_LOCATION_POINT)) {
					log.info("New callout: VENTS");
					callout = "VENTS";
					return;
				}
			}
		});
		//Check if DD if not pillars or vents
		for(Player player : client.getPlayers()) {
			if(player.hasSpotAnim(PLAYER_DD_ID)) {
				callout = "DD";
				return;
			}
			if(player.hasSpotAnim(PLAYER_SIGHT_ANIMATION_ID)) {
				callout = "SIGHT_CHANGE";
				return;
			}
		}
	}

	private void playSound(String audio) {
		String soundFile = "/" + audio + ".wav";
		if(clip != null) clip.close();

		AudioInputStream soundInputStream = null;
		try {
			InputStream input = ToatimePlugin.class.getResourceAsStream(soundFile);
			InputStream bufferedInput = new BufferedInputStream(input);  // support the optional mark/reset for AudioInputStream
			soundInputStream = AudioSystem.getAudioInputStream(bufferedInput);
		} catch (UnsupportedAudioFileException e) {
			log.warn("The specified audio file is not supported.");
			e.printStackTrace();
		} catch (IOException e) {
			log.warn("Failed to load sound.");
			e.printStackTrace();
		} catch (NullPointerException e) {
			log.warn("Audio file not found.");
			e.printStackTrace();
		}

		if(soundInputStream == null) return;
		if(!tryToLoadFile(soundInputStream)) return;

		// volume
		FloatControl volume = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
		float volumeValue = config.volume() - 100;

		volume.setValue(volumeValue);
		clip.loop(0);
	}

	private boolean tryToLoadFile(AudioInputStream sound) {
		try	{
			clip = AudioSystem.getClip();
			clip.open(sound);
			return true;
		} catch (LineUnavailableException | IOException e) {log.warn("Could not load the file: ", e);}
		return false;
	}


	@Provides
	ToatimeConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ToatimeConfig.class);
	}
}
