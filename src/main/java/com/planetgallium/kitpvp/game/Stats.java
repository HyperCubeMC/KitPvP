package com.planetgallium.kitpvp.game;

import com.cryptomorin.xseries.XSound;
import com.planetgallium.kitpvp.Game;
import com.planetgallium.kitpvp.api.PlayerLevelDownEvent;
import com.planetgallium.kitpvp.api.PlayerLevelUpEvent;
import com.planetgallium.kitpvp.util.*;
import com.zp4rker.localdb.DataType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

public class Stats {

    private final Infobase database;
    private final Resources resources;
    private final Resource levels;
    private final Leaderboards leaderboards;

    public Stats(Game plugin, Arena arena) {
        this.database = plugin.getDatabase();
        this.resources = plugin.getResources();
        this.levels = plugin.getResources().getLevels();
        this.leaderboards = arena.getLeaderboards();
    }

    public void createPlayer(Player p) {
        CacheManager.getUUIDCache().put(p.getName(), p.getUniqueId().toString());
        if (!isPlayerRegistered(p.getName())) {
            database.createPlayerStats(p);
        }
    }

    public boolean isPlayerRegistered(String username) {
        return database.databaseTableContainsPlayer("stats", username);
    }

    public double getKDRatio(String username) {
        if (getStat("deaths", username) != 0) {
            double divided = (double) getStat("kills", username) / getStat("deaths", username);
            return Toolkit.round(divided, 2);
        }
        return 0.00;
    }

    public void removeExperience(Player p, int amount) {
        String username = p.getName();

        if (levels.getBoolean("Levels.Levels.Enabled") && isPlayerRegistered(username)) {
            int currentExperience = getStat("experience", username);

            if (levels.getBoolean("Levels.Options.Take-Levels-At-Zero-Experience") && currentExperience <= 0) {
                levelDown(p);
                Bukkit.getPluginManager().callEvent(new PlayerLevelDownEvent(p, getStat("level", p.getName())));

                return;
            }

            setStat("experience", username, currentExperience >= amount ? currentExperience - amount : 0);
        }
    }

    public void addExperience(Player p, int experienceToAdd) {
        if (levels.getBoolean("Levels.Levels.Enabled")) {
            int currentExperience = getStat("experience", p.getName());
            setStat("experience", p.getName(), currentExperience + experienceToAdd);
            if (getStat("experience", p.getName()) >= getRegularOrRelativeNeededExperience(p.getName())) {
                levelUp(p);
                Bukkit.getPluginManager().callEvent(new PlayerLevelUpEvent(p, getStat("level", p.getName())));
            }
        }
    }

    public void levelUp(Player p) {

        String username = p.getName();

        if (getStat("level", username) < levels.getInt("Levels.Options.Maximum-Level")) {

            int newLevel = getStat("level", username) + 1;
            setStat("level", username, newLevel);
            setStat("experience", username, 0);

            List<String> levelUpCommands = levels.getStringList("Levels.Commands-On-Level-Up");
            Toolkit.runCommands(p, levelUpCommands, "%level%", String.valueOf(newLevel));

            if (levels.contains("Levels.Levels." + newLevel + ".Commands-On-Level-Up")) {
                List<String> commandsList = levels.getStringList("Levels.Levels." + newLevel + ".Commands-On-Level-Up");
                Toolkit.runCommands(p, commandsList, "%level%", String.valueOf(newLevel));
            }

            p.sendMessage(resources.getMessages().getString("Messages.Other.Level-Up")
                                  .replace("%level%", String.valueOf(newLevel)));
            XSound.play(p, "ENTITY_PLAYER_LEVELUP, 1, 1");

        } else {
            setStat("experience", username, 0);
        }

    }

    public void levelDown(Player p) {

        String username = p.getName();

        if (getStat("level", username) > levels.getInt("Levels.Options.Minimum-Level")) {

            int newLevel = getStat("level", username) - 1;
            setStat("level", username, newLevel);
            setStat("experience", username, getRegularOrRelativeNeededExperience(username));

            List<String> levelDownCommands = levels.getStringList("Levels.Commands-On-Lose-Level");
            Toolkit.runCommands(p, levelDownCommands, "%level%", String.valueOf(newLevel));

            if (levels.contains("Levels.Levels." + newLevel + ".Commands-On-Lose-Level")) {
                List<String> commandsList = levels.getStringList("Levels.Levels." + newLevel + ".Commands-On-Lose-Level");
                Toolkit.runCommands(p, commandsList, "%level%", String.valueOf(newLevel));
            }

            p.sendMessage(resources.getMessages().getString("Messages.Other.Level-Down")
                    .replace("%level%", String.valueOf(newLevel)));
            XSound.play(p, "ENTITY_PLAYER_ATTACK_CRIT, 1, 1");

        }

    }

    public void addToStat(String identifier, String username, int amount) {
        int updatedAmount = getStat(identifier, username) + amount;
        setStat(identifier, username, updatedAmount);
    }

    public void setStat(String identifier, String username, int data) {
        if (isPlayerRegistered(username)) {
            PlayerData playerData = getOrCreateStatsCache(username);
            playerData.setDataByIdentifier(identifier, data);

            database.setData("stats", identifier, data, DataType.INTEGER, username);
            leaderboards.updateCache(identifier, new PlayerEntry(username, data));
        } else {
            Toolkit.printToConsole(String.format("&7[&b&lKIT-PVP&7] &cFailed to set stats of player %s; not in database.", username));
        }
    }

    public int getStat(String identifier, String username) {
        return getOrCreateStatsCache(username).getDataByIdentifier(identifier);
    }

    private PlayerData getOrCreateStatsCache(String username) {
        if (!CacheManager.getStatsCache().containsKey(username)) {
            int kills = (int) database.getData("stats", "kills", username);
            int deaths = (int) database.getData("stats", "deaths", username);
            int experience = (int) database.getData("stats", "experience", username);
            int level = (int) database.getData("stats", "level", username);
            PlayerData playerData = new PlayerData(kills, deaths, experience, level);

            CacheManager.getStatsCache().put(username, playerData);
        }
        return CacheManager.getStatsCache().get(username);
    }

    public int getRegularOrRelativeNeededExperience(String username) {

        int level = getStat("level", username);

        if (levels.contains("Levels.Levels." + level + ".Experience-To-Level-Up")) {
            return levels.getInt("Levels.Levels." + level + ".Experience-To-Level-Up");
        }
        return levels.getInt("Levels.Options.Experience-To-Level-Up");

    }

}
