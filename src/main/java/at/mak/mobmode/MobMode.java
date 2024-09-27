package at.mak.mobmode;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.boss.BossBar;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MobMode extends JavaPlugin implements Listener {

    private HashMap<UUID, Integer> playerMoney = new HashMap<>();
    private HashMap<String, Integer> baseHealth = new HashMap<>();
    private HashMap<UUID, Boolean> redTeamCooldown = new HashMap<>();
    private HashMap<UUID, Boolean> blueTeamCooldown = new HashMap<>();
    private HashMap<String, Boolean> teamZombieUpgrade = new HashMap<>();
    private HashMap<UUID, String> playerTeams = new HashMap<>();

    private Location redBase;
    private Location blueBase;
    private Villager redShopkeeper;
    private Villager blueShopkeeper;
    private Location redZombieSpawn;
    private Location blueZombieSpawn;
    private Location skeletonSpawn1;
    private Location skeletonSpawn2;
    private BossBar redBaseBar;
    private BossBar blueBaseBar;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        baseHealth.put("red", 100);
        baseHealth.put("blue", 100);
        teamZombieUpgrade.put("red", false);
        teamZombieUpgrade.put("blue", false);

        redBase = new Location(Bukkit.getWorld("world"), 497, 89, 275);
        blueBase = new Location(Bukkit.getWorld("world"), 433, 89, 275);

        redZombieSpawn = new Location(Bukkit.getWorld("world"), 443, 89, 275);
        blueZombieSpawn = new Location(Bukkit.getWorld("world"), 487, 89, 275);

        skeletonSpawn1 = new Location(Bukkit.getWorld("world"), 465, 89, 285);
        skeletonSpawn2 = new Location(Bukkit.getWorld("world"), 465, 89, 265);

        redShopkeeper = spawnShopkeeper(new Location(Bukkit.getWorld("world"), 502, 89, 275), "Магазин Красной Команды");
        blueShopkeeper = spawnShopkeeper(new Location(Bukkit.getWorld("world"), -427, 89, 275), "Магазин Синей Команды");

        redBaseBar = Bukkit.createBossBar("Красная база", BarColor.RED, BarStyle.SEGMENTED_10);
        blueBaseBar = Bukkit.createBossBar("Синяя база", BarColor.BLUE, BarStyle.SEGMENTED_10);

        new BukkitRunnable() {
            @Override
            public void run() {
                spawnSkeletons(skeletonSpawn1);
                spawnSkeletons(skeletonSpawn2);
            }
        }.runTaskTimer(this, 0, 2400);

        new BukkitRunnable() {
            @Override
            public void run() {
                Bukkit.getWorld("world").setTime(18000);
            }
        }.runTaskTimerAsynchronously(this, 0, 100);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        assignTeam(player);
        displayTeamName(player);

        String team = playerTeams.get(player.getUniqueId());
        if (team.equals("red")) {
            redBaseBar.addPlayer(player);
        } else if (team.equals("blue")) {
            blueBaseBar.addPlayer(player);
        }
    }

    private void assignTeam(Player player) {
        UUID playerUUID = player.getUniqueId();
        if (playerTeams.size() < 2) {
            String team = ThreadLocalRandom.current().nextBoolean() ? "red" : "blue";

            playerTeams.put(playerUUID, team);
            player.sendMessage(ChatColor.WHITE + "Вам назначена команда " + (team.equals("red") ? ChatColor.RED + "Красная" : ChatColor.BLUE + "Синяя"));
        }
    }

    private void displayTeamName(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager.getNewScoreboard();

        Objective objective = scoreboard.registerNewObjective("team", "dummy", ChatColor.WHITE + "Ваша команда:");
        objective.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);

        String team = playerTeams.get(player.getUniqueId());
        objective.getScore(team.equals("red") ? ChatColor.RED + "Красная" : ChatColor.BLUE + "Синяя").setScore(1);

        player.setScoreboard(scoreboard);
    }

    public void damageBase(String team, int damage) {
        int currentHP = baseHealth.getOrDefault(team, 100);
        int newHP = Math.max(0, currentHP - damage);
        baseHealth.put(team, newHP);

        if (team.equals("red")) {
            redBaseBar.setProgress(newHP / 100.0);
        } else if (team.equals("blue")) {
            blueBaseBar.setProgress(newHP / 100.0);
        }
    }

    @Override
    public void onDisable() {
        redBaseBar.removeAll();
        blueBaseBar.removeAll();
    }

    private Villager spawnShopkeeper(Location location, String name) {
        Villager shopkeeper = location.getWorld().spawn(location, Villager.class);
        shopkeeper.setCustomName(name);
        shopkeeper.setCustomNameVisible(true);
        shopkeeper.setProfession(Villager.Profession.ARMORER);
        return shopkeeper;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEvent event) {
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            Material clickedBlock = event.getClickedBlock().getType();

            if (clickedBlock == Material.DIAMOND_BLOCK) {
                if (player.getLocation().distance(redBase) < 10) {
                    spawnZombie(redZombieSpawn, blueBase);
                }
            } else if (clickedBlock == Material.GOLD_BLOCK) {
                if (player.getLocation().distance(blueBase) < 10) {
                    spawnZombie(blueZombieSpawn, redBase);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked() instanceof Villager) {
            Villager villager = (Villager) event.getRightClicked();
            Player player = event.getPlayer();
            if (villager.equals(redShopkeeper) || villager.equals(blueShopkeeper)) {
                openShop(player, villager);
            }
        }
    }

    public class BaseHealthBars {

        private BossBar redBaseBar;
        private BossBar blueBaseBar;
        private int redBaseHP = 100;
        private int blueBaseHP = 100;
        private final JavaPlugin plugin;

        private HashMap<Player, String> playerTeams = new HashMap<>();

        public BaseHealthBars(JavaPlugin plugin) {
            this.plugin = plugin;
            createHealthBars();
        }

        private void createHealthBars() {
            redBaseBar = Bukkit.createBossBar("Красная база", BarColor.RED, BarStyle.SEGMENTED_10);
            blueBaseBar = Bukkit.createBossBar("Синяя база", BarColor.BLUE, BarStyle.SEGMENTED_10);
        }

        public void assignPlayerToTeam(Player player) {
            String team = (Math.random() < 0.5) ? "red" : "blue";
            playerTeams.put(player, team);

            addPlayerToTeam(player, team);
        }

        public void addPlayerToTeam(Player player, String team) {
            if (team.equalsIgnoreCase("red")) {
                redBaseBar.addPlayer(player);
            } else if (team.equalsIgnoreCase("blue")) {
                blueBaseBar.addPlayer(player);
            }
        }

        public void removePlayerFromTeam(Player player, String team) {
            if (team.equalsIgnoreCase("red")) {
                redBaseBar.removePlayer(player);
            } else if (team.equalsIgnoreCase("blue")) {
                blueBaseBar.removePlayer(player);
            }
        }

        public void updateBaseHP(String team, int newHP) {
            if (team.equalsIgnoreCase("red")) {
                redBaseHP = newHP;
                redBaseBar.setProgress(redBaseHP / 100.0);
            } else if (team.equalsIgnoreCase("blue")) {
                blueBaseHP = newHP;
                blueBaseBar.setProgress(blueBaseHP / 100.0);
            }
        }

        public void onGameStart() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                assignPlayerToTeam(player);
            }
        }

        public void onGameEnd() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                String team = getPlayerTeam(player);
                removePlayerFromTeam(player, team);
            }
            playerTeams.clear();
        }

        private String getPlayerTeam(Player player) {
            return playerTeams.getOrDefault(player, "none");
        }
    }

    private void openShop(Player player, Villager shopkeeper) {
        Inventory shop = Bukkit.createInventory(null, 9, ChatColor.GREEN + "Магазин");

        ItemStack sword = new ItemStack(Material.STONE_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.setDisplayName(ChatColor.YELLOW + "Каменный меч (50 монет)");
        sword.setItemMeta(swordMeta);

        ItemStack sword1 = new ItemStack(Material.IRON_SWORD);
        ItemMeta swordMeta1 = sword1.getItemMeta();
        swordMeta1.setDisplayName(ChatColor.YELLOW + "Железный меч (250 монет)");
        sword1.setItemMeta(swordMeta);

        ItemStack sword2 = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta swordMeta2 = sword2.getItemMeta();
        swordMeta2.setDisplayName(ChatColor.YELLOW + "Алмазный меч (1000 монет)");
        sword2.setItemMeta(swordMeta);

        ItemStack sword3 = new ItemStack(Material.DIAMOND_SWORD);
        ItemMeta swordMeta3 = sword3.getItemMeta();
        swordMeta3.setDisplayName(ChatColor.YELLOW + "Незеритовый меч (2000 монет)");
        sword3.setItemMeta(swordMeta);

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemMeta chestMeta = chest.getItemMeta();
        chestMeta.setDisplayName(ChatColor.YELLOW + "Кожанный сет (150 монет)");
        chest.setItemMeta(swordMeta);

        ItemStack chest1 = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta chestMeta1 = chest1.getItemMeta();
        chestMeta1.setDisplayName(ChatColor.YELLOW + "Железный сет (350 монет)");
        chest1.setItemMeta(swordMeta);

        ItemStack chest2 = new ItemStack(Material.DIAMOND_CHESTPLATE);
        ItemMeta chestMeta2 = chest2.getItemMeta();
        chestMeta2.setDisplayName(ChatColor.YELLOW + "Алмазный сет (1200 монет)");
        chest2.setItemMeta(swordMeta);

        ItemStack zombieUpgrade = new ItemStack(Material.CHAINMAIL_CHESTPLATE);
        ItemMeta upgradeMeta = zombieUpgrade.getItemMeta();
        upgradeMeta.setDisplayName(ChatColor.RED + "Улучшение зомби x1 (200 монет)");
        zombieUpgrade.setItemMeta(upgradeMeta);

        ItemStack zombieUpgrade1 = new ItemStack(Material.GOLD_INGOT);
        ItemMeta upgradeMeta1 = zombieUpgrade1.getItemMeta();
        upgradeMeta1.setDisplayName(ChatColor.RED + "Улучшение зомби x2 (600 монет)");
        zombieUpgrade1.setItemMeta(upgradeMeta);

        ItemStack zombieUpgrade2 = new ItemStack(Material.NETHER_STAR);
        ItemMeta upgradeMeta2 = zombieUpgrade2.getItemMeta();
        upgradeMeta2.setDisplayName(ChatColor.RED + "Улучшение зомби x3 (2000 монет)");
        zombieUpgrade2.setItemMeta(upgradeMeta);

        shop.addItem(sword);
        shop.addItem(sword1);
        shop.addItem(sword2);
        shop.addItem(sword3);

        shop.addItem(chest);
        shop.addItem(chest1);
        shop.addItem(chest2);

        shop.addItem(zombieUpgrade);
        shop.addItem(zombieUpgrade1);
        shop.addItem(zombieUpgrade2);

        player.openInventory(shop);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.GREEN + "Магазин")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            UUID playerUUID = player.getUniqueId();
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem == null || !clickedItem.hasItemMeta()) return;

            if (clickedItem.getType() == Material.STONE_SWORD) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 50) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 50);
                    player.getInventory().addItem(new ItemStack(Material.STONE_SWORD));
                    player.sendMessage(ChatColor.GREEN + "Вы купили каменный меч!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }

            if (clickedItem.getType() == Material.IRON_SWORD) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 250) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 250);
                    player.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
                    player.sendMessage(ChatColor.GREEN + "Вы купили железный меч!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }

            if (clickedItem.getType() == Material.DIAMOND_SWORD) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 1000) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 1000);
                    player.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
                    player.sendMessage(ChatColor.GREEN + "Вы купили алмазный меч!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }

            if (clickedItem.getType() == Material.NETHERITE_SWORD) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 2000) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 2000);
                    player.getInventory().addItem(new ItemStack(Material.DIAMOND_SWORD));
                    player.sendMessage(ChatColor.GREEN + "Вы купили незеритовый меч!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }

            if (clickedItem.getType() == Material.LEATHER_CHESTPLATE) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 150) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 150);
                    player.getInventory().addItem(new ItemStack(Material.LEATHER_HELMET));
                    player.getInventory().addItem(new ItemStack(Material.LEATHER_CHESTPLATE));
                    player.getInventory().addItem(new ItemStack(Material.LEATHER_LEGGINGS));
                    player.getInventory().addItem(new ItemStack(Material.LEATHER_BOOTS));
                    player.sendMessage(ChatColor.GREEN + "Вы купили кожанный сет!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }

            if (clickedItem.getType() == Material.IRON_CHESTPLATE) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 350) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 350);
                    player.getInventory().addItem(new ItemStack(Material.IRON_HELMET));
                    player.getInventory().addItem(new ItemStack(Material.IRON_CHESTPLATE));
                    player.getInventory().addItem(new ItemStack(Material.IRON_LEGGINGS));
                    player.getInventory().addItem(new ItemStack(Material.IRON_BOOTS));
                    player.sendMessage(ChatColor.GREEN + "Вы купили железный сет!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }

            if (clickedItem.getType() == Material.DIAMOND_CHESTPLATE) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 1200) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 1200);
                    player.getInventory().addItem(new ItemStack(Material.DIAMOND_HELMET));
                    player.getInventory().addItem(new ItemStack(Material.DIAMOND_CHESTPLATE));
                    player.getInventory().addItem(new ItemStack(Material.DIAMOND_LEGGINGS));
                    player.getInventory().addItem(new ItemStack(Material.DIAMOND_BOOTS));
                    player.sendMessage(ChatColor.GREEN + "Вы купили алмазный сет!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }

            if (clickedItem.getType() == Material.CHAINMAIL_CHESTPLATE) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 200) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 50);

                    String team = redShopkeeper.getLocation().distance(player.getLocation()) < blueShopkeeper.getLocation().distance(player.getLocation()) ? "red" : "blue";
                    teamZombieUpgrade.put(team, true);
                    player.sendMessage(ChatColor.GREEN + "Зомби вашей команды теперь будут спавниться в кольчужной броне!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }

            if (clickedItem.getType() == Material.GOLD_INGOT) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 600) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 600);

                    String team = redShopkeeper.getLocation().distance(player.getLocation()) < blueShopkeeper.getLocation().distance(player.getLocation()) ? "red" : "blue";
                    teamZombieUpgrade.put(team, true);
                    player.sendMessage(ChatColor.GREEN + "Зомби вашей команды теперь будут спавниться с алмазными мечами!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }

            if (clickedItem.getType() == Material.NETHER_STAR) {
                if (playerMoney.getOrDefault(playerUUID, 0) >= 2000) {
                    playerMoney.put(playerUUID, playerMoney.get(playerUUID) - 2000);

                    String team = redShopkeeper.getLocation().distance(player.getLocation()) < blueShopkeeper.getLocation().distance(player.getLocation()) ? "red" : "blue";
                    teamZombieUpgrade.put(team, true);
                    player.sendMessage(ChatColor.GREEN + "Зомби вашей команды теперь будут спавниться с незеритовыми мечами и в алмазной броне!");
                } else {
                    player.sendMessage(ChatColor.RED + "У вас недостаточно монет!");
                }
            }
        }
    }

    private void spawnZombie(Location spawnLocation, Location targetBase) {
        Zombie zombie = spawnLocation.getWorld().spawn(spawnLocation, Zombie.class);
        String team = spawnLocation.equals(redZombieSpawn) ? "red" : "blue";
        zombie.setCustomName("Зомби игрока " + team);

        if (teamZombieUpgrade.get(team)) {
            zombie.getEquipment().setHelmet(new ItemStack(Material.CHAINMAIL_HELMET));
            zombie.getEquipment().setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
            zombie.getEquipment().setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
            zombie.getEquipment().setBoots(new ItemStack(Material.CHAINMAIL_BOOTS));
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (zombie.getLocation().distance(targetBase) <= 2) {
                    String targetTeam = targetBase.equals(redBase) ? "red" : "blue";
                    baseHealth.put(targetTeam, baseHealth.get(targetTeam) - 1);
                    if (baseHealth.get(targetTeam) <= 0) {
                        Bukkit.broadcastMessage("Игрок " + (team.equals("red") ? "красной" : "синей") + " команды победил!");
                        this.cancel();
                    }
                    zombie.remove();
                }
            }
        }.runTaskTimer(this, 0, 60);
    }

    private void spawnSkeletons(Location spawnLocation) {
        for (int i = 0; i < 3; i++) {
            Skeleton skeleton = spawnLocation.getWorld().spawn(spawnLocation, Skeleton.class);
            skeleton.setCustomName("Скелет");
            skeleton.setTarget(null);
        }
    }

    @EventHandler
    public void onZombieKill(EntityDeathEvent event) {
        if (event.getEntity() instanceof Zombie) {
            if (event.getEntity().getCustomName() != null && event.getEntity().getCustomName().contains("Зомби игрока")) {
                Player killer = event.getEntity().getKiller();
                if (killer != null) {
                    UUID playerUUID = killer.getUniqueId();
                    int money = playerMoney.getOrDefault(playerUUID, 0);
                    playerMoney.put(playerUUID, money + 10);
                    killer.sendMessage("Вы получили 10 монет!");
                }
            }
        }

        if (event.getEntity() instanceof Skeleton) {
            if (event.getEntity().getCustomName() != null && event.getEntity().getCustomName().contains("Скелет")) {
                Player killer = event.getEntity().getKiller();
                if (killer != null) {
                    UUID playerUUID = killer.getUniqueId();
                    int money = playerMoney.getOrDefault(playerUUID, 0);
                    playerMoney.put(playerUUID, money + 40);
                    killer.sendMessage("Вы получили 40 монет!");
                }
            }
        }
    }
}