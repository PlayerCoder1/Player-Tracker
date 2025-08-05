package com.PlayerTracker;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

import javax.inject.Inject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

@PluginDescriptor(
        name = "Player Tracker",
        description = "Logs player data (location, activity, gear) to a custom API.",
        tags = {"player, tracker, logger, location, activity, equipment, telemetry, api, external"}
)
public class PlayerTracker extends Plugin
{
    @Inject private Client client;
    @Inject private ItemManager itemManager;
    @Inject private PlayerTrackerConfig config;

    private static final WorldArea[] WILDERNESS_AREAS = {
            new WorldArea(2940, 3647, 502, 334, 0),
            new WorldArea(2940, 3521, 183, 127, 0),
            new WorldArea(3151, 3520, 291, 127, 0),
            new WorldArea(3123, 3520, 28, 83, 0)
    };

    private static final int TICKS_BETWEEN_LOGS = 100; // 1 minutes
    private static final String API_URL = "https://playercount.live/api/update";

    private int tickCounter = 0;

    @Provides
    PlayerTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PlayerTrackerConfig.class);
    }

    private boolean isInWilderness(WorldPoint point)
    {
        for (WorldArea area : WILDERNESS_AREAS)
        {
            if (area.contains(point)) return true;
        }
        return false;
    }

    private String getActivityStatus(int animationId)
    {
        if (animationId == -1) return "Idle";

        switch (animationId)
        {
            // Mining
            case 625: case 626: case 627: case 3873: case 629: case 628: case 624:
            case 7139: case 642: case 8346: case 4482: case 7283: case 8347:
            case 6753: case 6754: case 6755: case 3866: case 6757: case 6756:
            case 6752: case 6758: case 335: case 8344: case 4481: case 7282: case 8345:
            return "Mining";

            // Woodcutting
            case 879: case 877: case 875: case 873: case 871: case 869:
            case 867: case 2846: case 2117: case 7264: case 8324:
            return "Woodcutting";

            // Fletching
            case 1248: case 6678: case 6684: case 6679: case 6685: case 6680:
            case 6686: case 6681: case 6687: case 6682: case 6688: case 6683:
            case 6689: case 8481: case 8480:
            return "Fletching";

            // Fishing
            case 620: case 621: case 623: case 619: case 618: case 5108: case 7401:
            case 7402: case 8336: case 622: case 1193: case 6709:
            return "Fishing";

            // Cooking
            case 897: case 896: case 7529:
            return "Cooking";

            // Smelting
            case 899: case 827:
            return "Smelting";

            // Alchemy
            case 713: case 711:
            return "Casting Alchemy";

            // Splashing
            case 1162: case 11423:
            return "Splashing";

            default:
                return "Other/Unknown";
        }
    }

    private void sendDataToServer(String username, int world, int x, int y, int plane,
                                  boolean wilderness, String activity, String equipment)
    {
        try {
            URL url = new URL(API_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String json = String.format(
                    "{" +
                            "\"username\":\"%s\"," +
                            "\"world\":%d," +
                            "\"x\":%d," +
                            "\"y\":%d," +
                            "\"z\":%d," +
                            "\"wilderness\":%b," +
                            "\"activity\":\"%s\"," +
                            "\"equipment\":\"%s\"" +
                            "}",
                    escapeJson(username), world, x, y, plane, wilderness, escapeJson(activity), escapeJson(equipment)
            );

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = json.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                System.err.println("HTTP Error: " + code);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String escapeJson(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        tickCounter++;
        if (tickCounter < TICKS_BETWEEN_LOGS)
            return;

        tickCounter = 0;

        Player player = client.getLocalPlayer();
        if (player == null) return;

        final String username = player.getName();
        final WorldPoint wp = player.getWorldLocation();
        final int world = client.getWorld();
        final int x = wp.getX();
        final int y = wp.getY();
        final int plane = wp.getPlane();
        final boolean wilderness = isInWilderness(wp);

        if (wilderness) return;

        final int animationId = player.getAnimation();
        final String activity = config.logActivity() ? getActivityStatus(animationId) : "";

        final StringBuilder equipmentBuilder = new StringBuilder();
        if (config.logEquipment()) {
            ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
            if (equipment != null) {
                for (Item item : equipment.getItems()) {
                    if (item != null && item.getId() > 0) {
                        equipmentBuilder.append(item.getId()).append(", ");
                    }
                }
            }
        }

        String equipmentStr = equipmentBuilder.toString().trim();
        if (equipmentStr.endsWith(",")) {
            equipmentStr = equipmentStr.substring(0, equipmentStr.length() - 1);
        }

        final String finalEquipment = equipmentStr;

        // ✅ Background thread with only final variables
        new Thread(() ->
                sendDataToServer(username, world, x, y, plane, wilderness, activity, finalEquipment)
        ).start();
    }

    @Override
    protected void shutDown() throws Exception {
        // Optional shutdown logic
    }
}
