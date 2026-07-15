package com.example.village.command;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.gui.GuiManager;
import com.example.village.hook.VaultHook;
import com.example.village.hook.WorldGuardHook;
import com.example.village.listener.GuiClickListener;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillageMember;
import com.example.village.model.VillageRelationState;
import com.example.village.model.VillageRelationType;
import com.example.village.model.VillageRole;
import com.example.village.model.VillageBorder;
import com.example.village.service.BorderPreviewService;
import com.example.village.service.BorderService;
import com.example.village.service.BuildingService;
import com.example.village.service.BuildingConfigLoader;
import com.example.village.model.BuildingDefinition;
import com.example.village.service.LevelService;
import com.example.village.service.VillagerService;
import com.example.village.service.PathService;
import com.example.village.service.VillageLightService;
import com.example.village.service.VillageManager;
import com.example.village.service.CurrencyService;
import com.example.village.util.MessageUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;
import com.example.village.util.BorderGeometryUtil;
import com.example.village.model.VillageJoinRequest;

public final class VillageCommand implements CommandExecutor, TabCompleter {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final GuiManager guiManager;
    private final LevelService levelService;
    private final BorderPreviewService previewService;
    private final BuildingService buildingService;
    private final VillagerService villagerService;
    private final VaultHook vaultHook;
    private final VillageLightService lightService;
    private final BorderService borderService;
    private final WorldGuardHook worldGuardHook;
    private final GuiClickListener guiClickListener;
    private final CurrencyService currencyService;
    private final PathService pathService;
    private final Map<UUID, PendingTransfer> pendingVillageTransfers = new ConcurrentHashMap<>();

    public VillageCommand(VillagePlugin plugin, VillageConfigManager configManager, VillageManager villageManager,
                          GuiManager guiManager, LevelService levelService,
                          BorderPreviewService previewService, BuildingService buildingService,
                          VillagerService villagerService, VaultHook vaultHook, VillageLightService lightService,
                          BorderService borderService, WorldGuardHook worldGuardHook, GuiClickListener guiClickListener, PathService pathService, CurrencyService currencyService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.guiManager = guiManager;
        this.levelService = levelService;
        this.previewService = previewService;
        this.buildingService = buildingService;
        this.villagerService = villagerService;
        this.vaultHook = vaultHook;
        this.lightService = lightService;
        this.borderService = borderService;
        this.worldGuardHook = worldGuardHook;
        this.guiClickListener = guiClickListener;
        this.pathService = pathService;
        this.currencyService = currencyService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if (!(sender instanceof Player)) {
                switch (sub) {
                    case "reload" -> {
                        handleReload(sender);
                        return true;
                    }
                    case "admin" -> {
                        handleAdmin(sender, args);
                        return true;
                    }
                    case "delete", "loeschen" -> {
                        handleDelete(sender, args);
                        return true;
                    }
                    case "list", "liste" -> {
                        handleList(sender);
                        return true;
                    }
                    case "info" -> {
                        handleInfo(sender, args);
                        return true;
                    }
                    default -> {
                        sender.sendMessage(MessageUtil.color(configManager.message("player-only")));
                        return true;
                    }
                }
            }
        } else if (!(sender instanceof Player)) {
            sender.sendMessage(MessageUtil.color(configManager.message("player-only")));
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            handleNoArgs(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "info":
                handleInfo(player, args);
                break;
            case "list":
            case "liste":
                handleList(player);
                break;
            case "join":
            case "beitreten":
                handleJoin(player, args);
                break;
            case "leave":
            case "verlassen":
                handleLeave(player);
                break;
            case "invite":
            case "einladen":
                handleInvite(player, args);
                break;
            case "kick":
            case "entfernen":
                handleKick(player, args);
                break;
            case "promote":
            case "befoerdern":
                handlePromote(player, args);
                break;
            case "border":
            case "grenze":
                if (args.length >= 3 && "manage".equalsIgnoreCase(args[1])) {
                    String[] remapped = new String[args.length];
                    remapped[0] = "manage";
                    remapped[1] = "border";
                    System.arraycopy(args, 2, remapped, 2, args.length - 2);
                    handleManage(player, remapped);
                } else {
                    handleBorder(player);
                }
                break;
            case "preview":
            case "vorschau":
                handlePreview(player);
                break;
            case "building":
            case "gebaeude":
            case "gebäude":
                handleBuilding(player, args);
                break;
            case "schematic":
            case "schematics":
            case "schem":
                handleSchematic(player, args);
                break;
            case "tp":
                handleTp(player, args);
                break;
            case "spawn":
                handleSpawn(player, args);
                break;
            case "delete":
            case "loeschen":
                handleDelete(player, args);
                break;
            case "rename":
            case "umbenennen":
                handleRename(player, args);
                break;
            case "revive":
                handleRevive(player);
                break;
            case "menu":
            case "menue":
                handleMenu(player);
                break;
            case "reload":
                handleReload(player);
                break;
            case "admin":
                handleAdmin(player, args);
                break;
            case "help":
            case "hilfe":
                handleHelp(player);
                break;
            case "sendmoney":
            case "sm":
                handleVillageSendMoney(player, args);
                break;
            case "sendmoneyconfirm":
                handleVillageSendMoneyConfirm(player, args);
                break;
            case "balance":
                handleVillageBalance(player, args);
                break;
            case "balances":
                handleVillageBalances(player, args);
                break;
            case "manage":
            case "verwalten":
                handleManage(player, args);
                break;
            case "borderconfirm":
                handleBorderConfirm(player, args);
                break;
            case "borderundo":
                handleBorderUndo(player);
                break;
            case "bordercancel":
                handleBorderCancel(player);
                break;
            case "borderrestart":
                handleBorderRestart(player);
                break;
            case "buildconfirm":
                handleBuildConfirm(player, args);
                break;
            case "buildexpansionconfirm":
                handleBuildExpansionConfirm(player, args);
                break;
            case "path":
                handlePath(player, args);
                break;
            case "joinrequest":
                handleJoinRequest(player, args);
                break;
            case "signshowconfirm":
                handleSignShowConfirm(player, args);
                break;
            case "memberremoveconfirm":
                handleMemberRemoveConfirm(player, args);
                break;
            case "cancel":
            case "abbrechen":
            case "abort":
                handleCancel(player);
                break;
            default:
                handleHelp(player);
                break;
        }

        return true;
    }

    private void handleNoArgs(Player player) {
        Optional<Village> village = villageManager.getPlayerVillage(player.getUniqueId());
        if (village.isPresent()) {
            guiManager.openMainGui(player, village.get());
        } else {
            handleHelp(player);
        }
    }

    private void handleInfo(CommandSender player, String[] args) {
        if (!(player instanceof Player)) {
            if (args.length < 2) {
                msg(player, "&cBitte ein Dorf angeben: /village info <Dorf>");
                return;
            }
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            Village village = villageManager.getVillageByName(name).orElse(null);
            if (village == null) {
                msg(player, configManager.message("village-not-found"));
                return;
            }
            String foundedBy = Bukkit.getOfflinePlayer(village.getFounderId()).getName();
            msg(player, "&6Dorf: &e" + village.getName());
            msg(player, "&7Gruender: &e" + (foundedBy != null ? foundedBy : "?"));
            msg(player, "&7Level: &e" + village.getLevel() + "/" + configManager.getMaxLevel());
            msg(player, "&7Punkte: &e" + village.getPoints());
            msg(player, "&7Mitglieder: &e" + village.getMembers().size());
            msg(player, "&7Gebäude: &e" + village.getBuildings().size());
            return;
        }
        Player p = (Player) player;
        Village village;
        if (args.length >= 2) {
            String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            village = villageManager.getVillageByName(name).orElse(null);
            if (village == null) {
                msg(player, configManager.message("village-not-found"));
                return;
            }
            if (!village.isMember(p.getUniqueId())
                    && !player.hasPermission("village.admin")
                    && !villageManager.areVillagesFriendlyForPlayer(p.getUniqueId(), village)) {
                msg(player, configManager.message("no-permission"));
                return;
            }
        } else {
            village = villageManager.getPlayerVillage(p.getUniqueId()).orElse(null);
            if (village == null) {
                msg(player, "&cDu bist in keinem Dorf.");
                return;
            }
        }

        boolean isMember = village.isMember(p.getUniqueId());
        String foundedBy = Bukkit.getOfflinePlayer(village.getFounderId()).getName();
        msg(player, "&6Dorf: &e" + village.getName());
        msg(player, "&7Gruender: &e" + (foundedBy != null ? foundedBy : "?"));
        msg(player, "&7Level: &e" + village.getLevel() + "/" + configManager.getMaxLevel());
        msg(player, "&7Punkte: &e" + village.getPoints());
        sendClickableLine(p, "&7Mitglieder: &e" + village.getMembers().size() + " ", "&a[Zum Menü]",
                getVillageActionCommand(p, village, isMember), isMember ? "Öffne das Dorfmenü." : "Sende eine Beitrittsanfrage.");

        long completedBuildings = village.getBuildings().stream().filter(VillageBuilding::isCompleted).count();
        sendClickableLine(p, "&7Gebäude: &e" + village.getBuildings().size() + " (&a" + completedBuildings + " fertig&7) ", "&a[Zum Menü]",
                getVillageActionCommand(p, village, isMember), "Öffne das Gebäude-Menü des Dorfes.");

        long friendshipCount = village.getRelations().values().stream()
                .filter(r -> r.getType() == VillageRelationType.FRIENDSHIP && r.getState() == VillageRelationState.ACTIVE)
                .count();
        long tradeCount = village.getRelations().values().stream()
                .filter(r -> r.getType() == VillageRelationType.TRADE && r.getState() == VillageRelationState.ACTIVE)
                .count();
        long warCount = village.getRelations().values().stream()
                .filter(r -> r.getType() == VillageRelationType.WAR && r.getState() == VillageRelationState.ACTIVE)
                .count();
        int knownCount = village.getKnownVillageIds().size();
        sendClickableLine(p, "&7Relationen: &e" + knownCount + " bekannte Dörfer, &e" + friendshipCount + " Freundschaft, &e" + tradeCount + " Handel, &e" + warCount + " Krieg ", "&a[Zum Menü]",
                getVillageActionCommand(p, village, isMember), "Öffne die Dorfbeziehungen.");

        sendClickableLine(p, "&7Upgrades: &e" + village.getUpgrades().size() + " ", "&a[Zum Menü]",
                getVillageActionCommand(p, village, isMember), "Öffne das Upgrade-Menü.");

        sendClickableLine(p, "&7Dorfbewohner: &e" + village.getVillagers().size() + "/" + village.getMaxVillagers(configManager.getBaseMaxVillagers(), configManager.getUpgradeVillagersPerLevel()) + " ", "&a[Zum Menü]",
                getVillageActionCommand(p, village, isMember), "Öffne das Villager-Menü.");

        sendClickableLine(p, "&7Fläche: &e" + village.getTotalArea() + " Bloecke ", "&a[Zum Menü]",
                getVillageActionCommand(p, village, isMember), "Öffne das Grenzmenü.");

        sendClickableLine(p, "&7Offene Quests: &e? ", "&a[Zum Menü]",
                getVillageActionCommand(p, village, isMember), "Öffne das Quest-Menü.");

        if (!isMember && villageManager.getPlayerVillage(p.getUniqueId()).isEmpty()) {
            sendClickableLine(p, "&7Aktion: ", "&a[Beitreten]",
                    "/village join " + village.getName(), "Klicke zum Senden einer Beitrittsanfrage.");
        } else if ((villageManager.canManageVillage(village, p.getUniqueId()) || p.hasPermission("village.admin")) && isMember) {
            sendClickableLine(p, "&7Aktion: ", "&e[Umbenennen]",
                    "/village rename", "Klicke um den neuen Dorfnamen im Chat einzugeben.");
        }
    }

    private String getVillageActionCommand(Player player, Village village, boolean isMember) {
        if (isMember) {
            return "/village menu";
        }
        return "/village join " + village.getName();
    }

    private void handleList(CommandSender player) {
        if (!(player instanceof Player)) {
            Collection<Village> villages = villageManager.getAllVillages();
            if (villages.isEmpty()) {
                msg(player, "&eEs gibt noch keine Doerfer.");
                return;
            }
            msg(player, "&6=== Doerfer ===");
            for (Village v : villages) {
                String founder = Bukkit.getOfflinePlayer(v.getFounderId()).getName();
                msg(player, "&a" + v.getName() + " &7(Level " + v.getLevel() + ", Gruender: " + (founder != null ? founder : "?") + ")");
            }
            return;
        }
        Player p = (Player) player;
        boolean canSeeAll = player.hasPermission("village.list.all") || player.hasPermission("village.admin");
        Collection<Village> villages = canSeeAll ? villageManager.getAllVillages()
                : villageManager.getKnownVillagesForPlayer(p.getUniqueId());

        if (villages.isEmpty()) {
            msg(player, canSeeAll ? "&eEs gibt noch keine Doerfer." : "&eDu hast keine bekannten Dörfer.");
            return;
        }

        msg(player, canSeeAll ? "&6=== Doerfer ===" : "&6=== Bekannte Doerfer ===");
        for (Village v : villages) {
            boolean known = villageManager.isVillageKnownToPlayer(p.getUniqueId(), v);
            String founder = Bukkit.getOfflinePlayer(v.getFounderId()).getName();
            
            // Village name with clickable teleport
            boolean canTp = player.hasPermission("village.admin") || player.hasPermission("village.tp.others");
            TextComponent nameComponent = new TextComponent(color((known ? "&a" : "&7") + v.getName()));
            if (canTp) {
                String tpCommand = "/village tp " + v.getName() + " " + player.getName();
                nameComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand));
                nameComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(color("&6Klicke zum Teleportieren.")).create()));
            }
            
            TextComponent levelComponent = new TextComponent(color(" &7(Level " + v.getLevel() + ", Gruender: "
                    + (founder != null ? founder : "?") + ") "));
            
            String actionLabel;
            String actionCommand;
            String hover;
            if (known || canSeeAll && player.hasPermission("village.admin")) {
                actionLabel = known ? "[Infos]" : "[Unbekannt]";
                actionCommand = "/village info " + v.getName();
                hover = known ? "Klicke fuer Dorfdetails." : "Unbekanntes Dorf (nur Admins).";
            } else {
                actionLabel = "[Beitreten]";
                actionCommand = "/village join " + v.getName();
                hover = "Klicke zum Beitritt.";
            }

            TextComponent action = new TextComponent(color("&e" + actionLabel));
            action.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, actionCommand));
            action.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(color(hover)).create()));
            
            p.spigot().sendMessage(nameComponent, levelComponent, action);
        }
    }

    private void handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&cBitte gib den Dorfnamen an: /village join <Name>");
            return;
        }

        if (villageManager.isInAnyVillage(player.getUniqueId())) {
            msg(player, configManager.message("already-in-village"));
            return;
        }

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Village village = villageManager.getVillageByName(name).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        if (village.isMember(player.getUniqueId())) {
            msg(player, "&cDu bist bereits Mitglied dieses Dorfes.");
            return;
        }

        if (village.hasJoinRequest(player.getUniqueId())) {
            msg(player, configManager.message("join-request-already-exists").replace("%name%", village.getName()));
            return;
        }

        if (!processJoinRequirements(player, village)) {
            return;
        }

        if (villageManager.addJoinRequest(village, player.getUniqueId())) {
            msg(player, configManager.message("join-request-created").replace("%name%", village.getName()));
            MessageUtil.notifyJoinRequestReviewers(configManager.getPrefix(), village, player);
        } else {
            msg(player, "&cBeitrittsanfrage konnte nicht gesendet werden.");
        }
    }

    private boolean processJoinRequirements(Player player, Village village) {
        double globalCost = configManager.getJoinGlobalCost();
        if (globalCost > 0 && !vaultHook.has(player, globalCost)) {
            msg(player, "&cDu hast nicht genug Geld, um eine Beitrittsanfrage zu senden.");
            return false;
        }
        if (globalCost > 0 && !vaultHook.withdraw(player, globalCost)) {
            msg(player, "&cDie Gebuehr fuer die Beitrittsanfrage konnte nicht abgebucht werden.");
            return false;
        }

        double localCost = configManager.getJoinLocalCost();
        if (localCost > 0) {
            String currencyId = currencyService.getVillageCurrencyId(village);
            if (currencyService.getBalance(player.getUniqueId(), currencyId) < localCost) {
                msg(player, "&cDu hast nicht genug Dorfwährung, um eine Beitrittsanfrage zu senden.");
                return false;
            }
            if (!currencyService.removeBalance(player.getUniqueId(), currencyId, localCost)) {
                msg(player, "&cDie Dorfwährung fuer die Beitrittsanfrage konnte nicht abgebucht werden.");
                return false;
            }
        }

        return true;
    }

    private void handleJoinRequest(Player player, String[] args) {
        if (args.length < 4) {
            msg(player, "&7/village joinrequest accept|deny <Dorf-UUID> <Spieler-UUID>");
            return;
        }
        String action = args[1].toLowerCase();
        if (!"accept".equals(action) && !"deny".equals(action) && !"ablehnen".equals(action) && !"annehmen".equals(action)) {
            msg(player, "&cUngültig. Nutze &eaccept &coder &edeny&c.");
            return;
        }
        boolean accept = "accept".equals(action) || "annehmen".equals(action);
        Village village;
        UUID requesterId;
        try {
            village = villageManager.getVillage(UUID.fromString(args[2])).orElse(null);
            requesterId = UUID.fromString(args[3]);
        } catch (IllegalArgumentException e) {

            msg(player, "&cUngültige UUID.");
            return;
        }
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        if (!player.hasPermission("village.admin") && !villageManager.canManageMembers(village, player.getUniqueId())) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        if (!village.hasJoinRequest(requesterId)) {
            msg(player, "&cFür diesen Spieler liegt keine offene Anfrage vor.");
            return;
        }

        Player requester = Bukkit.getPlayer(requesterId);
        String requesterName = requester != null ? requester.getName()
                : (Bukkit.getOfflinePlayer(requesterId).getName() != null
                ? Bukkit.getOfflinePlayer(requesterId).getName() : requesterId.toString());

        if (accept) {
            if (!villageManager.acceptJoinRequest(village, requesterId)) {
                msg(player, "&cAnfrage konnte nicht angenommen werden (Dorf voll oder Spieler schon in einem Dorf).");
                return;
            }
            msg(player, "&aBeitritt von &e" + requesterName + " &aangenommen.");
            if (requester != null && requester.isOnline()) {
                MessageUtil.send(requester, configManager.getPrefix(),
                        configManager.message("join-request-accepted").replace("%name%", village.getName()));
                if (lightService != null) {
                    lightService.refreshPlayer(requester);
                }
            }
        } else {
            if (!villageManager.declineJoinRequest(village, requesterId)) {
                msg(player, "&cAnfrage konnte nicht abgelehnt werden.");
                return;
            }
            msg(player, "&eBeitritt von &7" + requesterName + " &eabgelehnt.");
            if (requester != null && requester.isOnline()) {
                MessageUtil.send(requester, configManager.getPrefix(),
                        configManager.message("join-request-declined").replace("%name%", village.getName()));
            }
        }
    }

    private void handleSignShowConfirm(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&7Bitte &aja &7oder &cnein &7klicken.");
            return;
        }
        String choice = args[1].toLowerCase();
        boolean yes = "ja".equals(choice) || "yes".equals(choice);
        boolean no = "nein".equals(choice) || "no".equals(choice);
        if (!yes && !no) {
            msg(player, "&cUngueltig. Nur ja/nein.");
            return;
        }
        if (!buildingService.resolvePendingSignReveal(player, yes)) {
            msg(player, "&cKeine ausstehende Schild-Einblendung gefunden.");
            return;
        }
        if (!yes) {
            msg(player, "&eSchild-Einblendung abgebrochen.");
        }
    }

    private void handleMemberRemoveConfirm(Player player, String[] args) {
        if (args.length < 4) {
            msg(player, "&7Verwendung: /village memberremoveconfirm ja|nein <villageId> <memberId>");
            return;
        }
        String choice = args[1].toLowerCase();
        boolean yes = "ja".equals(choice) || "yes".equals(choice);
        if (!yes && !"nein".equals(choice) && !"no".equals(choice)) {
            msg(player, "&cUngueltig. Nutze ja oder nein.");
            return;
        }
        UUID villageId;
        UUID memberId;
        try {
            villageId = UUID.fromString(args[2]);
            memberId = UUID.fromString(args[3]);
        } catch (IllegalArgumentException e) {
            msg(player, "&cUngültige UUID.");
            return;
        }
        Village village = villageManager.getVillage(villageId).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }
        if (!player.hasPermission("village.admin") && !villageManager.canManageMembers(village, player.getUniqueId())) {
            msg(player, configManager.message("no-permission"));
            return;
        }
        if (!yes) {
            msg(player, "&eEntfernen abgebrochen.");
            return;
        }
        if (village.isFounder(memberId)) {
            msg(player, "&cDer Gruender kann nicht entfernt werden.");
            return;
        }
        if (!villageManager.removeMember(village, memberId)) {
            msg(player, "&cKonnte Mitglied nicht entfernen.");
            return;
        }
        String memberName = Bukkit.getOfflinePlayer(memberId).getName();
        if (memberName == null) memberName = memberId.toString().substring(0, 8);
        msg(player, "&aMitglied &e" + memberName + " &awurde entfernt.");
    }

    private void handleLeave(Player player) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        if (village.isFounder(player.getUniqueId())) {
            msg(player, "&cDu kannst dein eigenes Dorf nicht verlassen! Nutze /village delete.");
            return;
        }

        if (villageManager.removeMember(village, player.getUniqueId())) {
            msg(player, configManager.message("village-left").replace("%name%", village.getName()));
            if (lightService != null) {
                lightService.refreshPlayer(player);
            }
        } else {
            msg(player, "&cDu kannst das Dorf nicht verlassen.");
        }
    }

    private void handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&cBitte gib den Spielernamen an: /village invite <Spieler>");
            return;
        }

        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        VillageMember member = village.getMember(player.getUniqueId());
        if (member == null || member.getRole() == VillageRole.MEMBER) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            msg(player, "&cSpieler nicht gefunden oder offline.");
            return;
        }

        if (villageManager.addMember(village, target.getUniqueId())) {
            msg(player, "&a" + target.getName() + " wurde zum Dorf eingeladen.");
            MessageUtil.send(target, configManager.getPrefix(),
                    configManager.message("village-joined").replace("%name%", village.getName()));
        } else {
            msg(player, "&cKonnte den Spieler nicht hinzufuegen.");
        }
    }

    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&cBitte gib den Spielernamen an: /village kick <Spieler>");
            return;
        }

        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        VillageMember member = village.getMember(player.getUniqueId());
        if (member == null || member.getRole() == VillageRole.MEMBER) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        UUID targetId = target != null ? target.getUniqueId() : null;
        if (targetId == null) {
            msg(player, "&cSpieler nicht gefunden.");
            return;
        }

        if (villageManager.removeMember(village, targetId)) {
            msg(player, "&a" + args[1] + " wurde aus dem Dorf entfernt.");
            if (target != null) {
                MessageUtil.send(target, configManager.getPrefix(),
                        "&cDu wurdest aus dem Dorf " + village.getName() + " entfernt.");
                if (lightService != null) {
                    lightService.refreshPlayer(target);
                }
            }
        } else {
            msg(player, "&cKonnte den Spieler nicht entfernen.");
        }
    }

    private void handlePromote(Player player, String[] args) {
        if (args.length < 3) {
            msg(player, "&cVerwendung: /village promote <Spieler> <Rolle>");
            msg(player, "&7Rollen: MEMBER, HR, BAUMEISTER, BUILDER, HAENDLER, TRAINER");
            return;
        }

        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null || (!village.isFounder(player.getUniqueId()) && !player.hasPermission("village.admin"))) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            msg(player, "&cSpieler nicht gefunden.");
            return;
        }

        try {
            VillageRole role = VillageRole.valueOf(args[2].toUpperCase());
            if (villageManager.promoteMember(village, target.getUniqueId(), role)) {
                msg(player, "&a" + target.getName() + " ist jetzt " + role.getDisplayName() + ".");
            } else {
                msg(player, "&cKonnte den Spieler nicht befoerdern.");
            }
        } catch (IllegalArgumentException e) {
            msg(player, "&cUngueltige Rolle. Verfuegbar: MEMBER, HR, BAUMEISTER, BUILDER, HAENDLER, TRAINER");
        }
    }

    private void handleBorder(Player player) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        guiManager.openBorderSelectionGui(player, village);
    }

    private void handlePreview(Player player) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        // Show preview of all borders simultaneously
        previewService.showPreviewMultiple(player, village.getBorders(), player.getWorld());
        msg(player, configManager.message("border-preview-start"));
    }

    private void handleDelete(CommandSender player, String[] args) {
        Village village;
        if (args.length >= 2) {
            String villageName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            village = villageManager.getVillageByName(villageName).orElse(null);
            if (village == null) {
                msg(player, configManager.message("village-not-found"));
                return;
            }
        } else {
            if (!(player instanceof Player)) {
                msg(player, "&cBitte ein Dorf angeben: /village delete <Dorf>");
                return;
            }
            village = villageManager.getPlayerVillage(((Player) player).getUniqueId()).orElse(null);
        }

        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        if (!player.hasPermission("village.admin") && (player instanceof Player && !village.isFounder(((Player) player).getUniqueId()))) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        String villageName = village.getName();
        villagerService.replaceCitizensVillagersWithMinecraftVillagers(village);
        villageManager.deleteVillage(village.getId());
        msg(player, configManager.message("village-deleted").replace("%name%", villageName));
    }

    private void handleRename(Player player, String[] args) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        if (!player.hasPermission("village.admin")
                && !player.hasPermission(configManager.getRenamePermission())
                && !villageManager.canManageVillage(village, player.getUniqueId())) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        if (args.length < 2) {
            guiClickListener.getPendingRenames().put(player.getUniqueId(), village.getId());
            player.closeInventory();
            msg(player, "&7Gib den neuen Dorfnamen im Chat ein. &8(abbrechen mit 'abbrechen')");
            return;
        }

        String newName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        renameVillage(player, village, newName);
    }

    private void renameVillage(Player player, Village village, String newName) {
        if (newName.length() < 3 || newName.length() > 24) {
            msg(player, "&cDer Name muss zwischen 3 und 24 Zeichen lang sein!");
            return;
        }

        if (villageManager.getVillageByName(newName).isPresent()) {
            msg(player, "&cEin Dorf mit diesem Namen existiert bereits!");
            return;
        }

        if (!processRenameRequirements(player, village)) {
            return;
        }

        String oldName = village.getName();
        village.setName(newName);
        villageManager.saveVillage(village);
        msg(player, "&aDorf umbenannt: &e" + oldName + " &a-> &e" + newName);
    }

    private boolean processRenameRequirements(Player player, Village village) {
        double globalCost = configManager.getRenameGlobalCost();
        if (globalCost > 0 && !vaultHook.has(player, globalCost)) {
            msg(player, "&cDu hast nicht genug Geld für die Umbenennung.");
            return false;
        }

        if (globalCost > 0 && !vaultHook.withdraw(player, globalCost)) {
            msg(player, "&cDie Zahlung für die Umbenennung konnte nicht verarbeitet werden.");
            return false;
        }

        double localCost = configManager.getRenameLocalCost();
        if (localCost > 0) {
            String currencyId = currencyService.getVillageCurrencyId(village);
            if (currencyService.getBalance(player.getUniqueId(), currencyId) < localCost) {
                msg(player, "&cDu hast nicht genug Dorfwährung für die Umbenennung.");
                return false;
            }
            if (!currencyService.removeBalance(player.getUniqueId(), currencyId, localCost)) {
                msg(player, "&cDie Dorfwährung für die Umbenennung konnte nicht abgezogen werden.");
                return false;
            }
        }

        return true;
    }

    private void handleMenu(Player player) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        guiManager.openMainGui(player, village);
    }

    private void handleBuilding(Player player, String[] args) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }

        if (args.length < 2) {
            msg(player, "&6/village building list");
            msg(player, "&6/village building remove <Nr>");
            msg(player, "&6/village building movesign <Nr>");
            msg(player, "&6/village building upgrade <Nr>");
            msg(player, "&6/village building start <Nr>");
            msg(player, "&6/village building cancel <Nr>");
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "list":
                msg(player, "&6=== Gebaeude in " + village.getName() + " ===");
                if (village.getBuildings().isEmpty()) {
                    msg(player, "&eKeine Gebaeude im Dorf gefunden.");
                    return;
                }
                for (int i = 0; i < village.getBuildings().size(); i++) {
                    var building = village.getBuildings().get(i);
                    msg(player, "&7[&e" + (i + 1) + "&7] &e" + building.getTypeKey() + " "
                            + (building.isCompleted() ? "&aFertig" : "&cIm Bau")
                            + " &7Level: &e" + building.getLevel());
                }
                break;
            case "remove":
                if (args.length < 3) {
                    msg(player, "&cVerwendung: /village building remove <Nr>");
                    return;
                }
                if (!village.isFounder(player.getUniqueId()) && !player.hasPermission("village.admin")) {
                    msg(player, configManager.message("no-permission"));
                    return;
                }
                try {
                    int index = Integer.parseInt(args[2]) - 1;
                    if (index < 0 || index >= village.getBuildings().size()) {
                        msg(player, "&cUngueltige Gebaeude-Nummer.");
                        return;
                    }
                    java.util.UUID id = village.getBuildings().get(index).getId();
                    if (buildingService.removeCompletedBuilding(player, village, id)) {
                        msg(player, "&aGebaeude entfernt.");
                    } else {
                        msg(player, "&cGebaeude konnte nicht entfernt werden.");
                    }
                } catch (NumberFormatException e) {
                    msg(player, "&cBitte gib eine Nummer ein (z.B. 1, 2, 3).");
                }
                break;
            case "movesign":
            case "reposition":
                if (args.length < 3) {
                    msg(player, "&cVerwendung: /village building movesign <Nr>");
                    return;
                }
                if (!village.isFounder(player.getUniqueId()) && !player.hasPermission("village.admin")) {
                    msg(player, configManager.message("no-permission"));
                    return;
                }
                try {
                    int index = Integer.parseInt(args[2]) - 1;
                    if (index < 0 || index >= village.getBuildings().size()) {
                        msg(player, "&cUngueltige Gebaeude-Nummer.");
                        return;
                    }
                    java.util.UUID id = village.getBuildings().get(index).getId();
                    if (buildingService.moveBuildingSign(player, village, id)) {
                        msg(player, "&aGebaeude-Schild wurde verschoben.");
                    } else {
                        msg(player, "&cGebaeude nicht gefunden.");
                    }
                } catch (NumberFormatException e) {
                    msg(player, "&cBitte gib eine Nummer ein (z.B. 1, 2, 3).");
                }
                break;
            case "upgrade":
                if (args.length < 3) {
                    msg(player, "&cVerwendung: /village building upgrade <Nr>");
                    return;
                }
                if (!village.isFounder(player.getUniqueId()) && !player.hasPermission("village.admin")) {
                    msg(player, configManager.message("no-permission"));
                    return;
                }
                try {
                    int index = Integer.parseInt(args[2]) - 1;
                    if (index < 0 || index >= village.getBuildings().size()) {
                        msg(player, "&cUngueltige Gebaeude-Nummer.");
                        return;
                    }
                    java.util.UUID id = village.getBuildings().get(index).getId();
                    if (buildingService.upgradeBuilding(player, village, id)) {
                        msg(player, "&aGebaeude erfolgreich verbessert.");
                    } else {
                        msg(player, "&cUpgrade fehlgeschlagen. Pruefe Level, Kosten und Status.");
                    }
                } catch (NumberFormatException e) {
                    msg(player, "&cBitte gib eine Nummer ein (z.B. 1, 2, 3).");
                }
                break;
            case "start":
                if (args.length < 3) { msg(player, "&cVerwendung: /village building start <Nr>"); return; }
                if (!village.isFounder(player.getUniqueId()) && !player.hasPermission("village.admin")) {
                    msg(player, configManager.message("no-permission")); return;
                }
                try {
                    int index = Integer.parseInt(args[2]) - 1;
                    if (index < 0 || index >= village.getBuildings().size()) { msg(player, "&cUngueltige Gebaeude-Nummer."); return; }
                    java.util.UUID id = village.getBuildings().get(index).getId();
                    if (buildingService.startConstruction(player, village, id)) msg(player, "&aKonstruktion gestartet."); else msg(player, "&cKonnte Konstruktion nicht starten. Pruefe Kosten/Inventar.");
                } catch (NumberFormatException e) { msg(player, "&cBitte gib eine Nummer ein (z.B. 1, 2, 3)."); }
                break;
            case "cancel":
                if (args.length < 3) { msg(player, "&cVerwendung: /village building cancel <Nr>"); return; }
                if (!village.isFounder(player.getUniqueId()) && !player.hasPermission("village.admin")) {
                    msg(player, configManager.message("no-permission")); return;
                }
                try {
                    int index = Integer.parseInt(args[2]) - 1;
                    if (index < 0 || index >= village.getBuildings().size()) { msg(player, "&cUngueltige Gebaeude-Nummer."); return; }
                    java.util.UUID id = village.getBuildings().get(index).getId();
                    if (buildingService.cancelConstruction(id)) msg(player, "&aKonstruktion abgebrochen."); else msg(player, "&cKeine aktive Konstruktion gefunden.");
                } catch (NumberFormatException e) { msg(player, "&cBitte gib eine Nummer ein (z.B. 1, 2, 3)."); }
                break;
            default:
                msg(player, "&6/village building list");
                msg(player, "&6/village building remove <Nr>");
                msg(player, "&6/village building movesign <Nr>");
                msg(player, "&6/village building upgrade <Nr>");
                msg(player, "&6/village building start <Nr>");
                msg(player, "&6/village building cancel <Nr>");
                break;
        }
    }

    private void handlePath(Player player, String[] args) {
        if (args.length < 2) {
            sendClickableCommand(player, "&e/village path done", "&aAbschließen");
            sendClickableCommand(player, "&e/village path cancel", "&cAbbrechen");
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "done" -> {
                if (pathService.finalizePathSession(player)) {
                    msg(player, "&aPfad erfolgreich abgeschlossen.");
                } else {
                    msg(player, "&cKeine aktive Pfad-Session oder Pfad konnte nicht abgeschlossen werden.");
                    sendClickableCommand(player, "&e/village path done", "&aAktivieren");
                    sendClickableCommand(player, "&e/village path cancel", "&cAbbrechen");
                }
            }
            case "cancel" -> {
                pathService.cancelPathSession(player);
            }
            default -> {
                sendClickableCommand(player, "&e/village path done", "&aAbschließen");
                sendClickableCommand(player, "&e/village path cancel", "&cAbbrechen");
            }
        }
    }

    private void sendClickableCommand(Player player, String command, String description) {
        TextComponent cmdComponent = new TextComponent(command);
        cmdComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        cmdComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(description).create()));
        player.spigot().sendMessage(cmdComponent);
    }

    private void handleReload(CommandSender player) {
        if (!player.hasPermission("village.admin")) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        if (plugin.getDebugConfigManager() != null) {
            plugin.getDebugConfigManager().load();
        }
        configManager.loadAll();
        if (lightService != null) {
            lightService.reloadConfiguration();
        }
        msg(player, "&aKonfiguration neu geladen.");
    }

    private void handleAdmin(CommandSender player, String[] args) {
        if (!player.hasPermission("village.admin")) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        if (args.length < 2) {
            msg(player, "&6Admin-Befehle:");
            msg(player, "&e/village admin setlevel <Dorf> <Level>");
            msg(player, "&e/village admin addpoints <Dorf> <Punkte>");
            msg(player, "&e/village admin money add|remove <Player/all/village:Name> <currency> <Betrag>");
            msg(player, "&e/village admin bordernames true|false");
            msg(player, "&e/village admin joinrequests <Dorf>");
            msg(player, "&e/village admin menu <Dorf>");
            msg(player, "&e/village admin light addpoi|addregion|addpath ...");
            msg(player, "&e/village admin delete <Dorf>");
            msg(player, "&e/village admin saveall");
            msg(player, "&e/village admin resyncwg");
            return;
        }

        String adminSub = args[1].toLowerCase();
        switch (adminSub) {
            case "setlevel":
                if (args.length < 4) { msg(player, "&cVerwendung: /village admin setlevel <Dorf> <Level>"); return; }
                Village v = villageManager.getVillageByName(args[2]).orElse(null);
                if (v == null) { msg(player, configManager.message("village-not-found")); return; }
                try {
                    v.setLevel(Integer.parseInt(args[3]));
                    villageManager.saveVillage(v);
                    msg(player, "&aLevel von " + v.getName() + " auf " + args[3] + " gesetzt.");
                } catch (NumberFormatException e) {
                    msg(player, "&cUngueltige Zahl.");
                }
                break;
            case "addpoints":
                if (args.length < 4) { msg(player, "&cVerwendung: /village admin addpoints <Dorf> <Punkte>"); return; }
                Village v2 = villageManager.getVillageByName(args[2]).orElse(null);
                if (v2 == null) { msg(player, configManager.message("village-not-found")); return; }
                try {
                    int points = Integer.parseInt(args[3]);
                    levelService.addPoints(v2, points, "admin");
                    msg(player, "&a" + points + " Punkte zu " + v2.getName() + " hinzugefuegt.");
                } catch (NumberFormatException e) {
                    msg(player, "&cUngueltige Zahl.");
                }
                break;
            case "bordernames":
                if (args.length < 3) {
                    msg(player, "&cVerwendung: /village admin bordernames true|false");
                    return;
                }
                if (!"true".equalsIgnoreCase(args[2]) && !"false".equalsIgnoreCase(args[2])) {
                    msg(player, "&cBitte nutze true oder false.");
                    return;
                }
                boolean enabled = Boolean.parseBoolean(args[2]);
                configManager.setBorderEntryDebug(enabled);
                if (plugin.getVillageDiscoveryListener() != null) {
                    plugin.getVillageDiscoveryListener().clearBorderTracking();
                }
                msg(player, enabled
                        ? "&aBorder-Namen in der Actionbar aktiviert."
                        : "&cBorder-Namen in der Actionbar deaktiviert.");
                break;
            case "delete":
                if (args.length < 3) { msg(player, "&cVerwendung: /village admin delete <Dorf>"); return; }
                Village v3 = villageManager.getVillageByName(args[2]).orElse(null);
                if (v3 == null) { msg(player, configManager.message("village-not-found")); return; }
                villageManager.deleteVillage(v3.getId());
                msg(player, "&cDorf " + args[2] + " geloescht.");
                break;
            case "money":
                handleAdminMoney(player, args);
                break;
            case "joinrequests":
                if (args.length < 3) {
                    msg(player, "&cVerwendung: /village admin joinrequests <Dorf>");
                    return;
                }
                Village jrVillage = villageManager.getVillageByName(args[2]).orElse(null);
                if (jrVillage == null) {
                    msg(player, configManager.message("village-not-found"));
                    return;
                }
                showJoinRequestsOverview(player, jrVillage);
                break;
            case "menu":
                if (args.length < 3) {
                    msg(player, "&cVerwendung: /village admin menu <Dorf>");
                    return;
                }
                Village menuVillage = villageManager.getVillageByName(args[2]).orElse(null);
                if (menuVillage == null) {
                    msg(player, configManager.message("village-not-found"));
                    return;
                }
                if (!(player instanceof Player)) {
                    msg(player, "&cDas Menue kann nur von einem Spieler geoeffnet werden.");
                    return;
                }
                guiManager.openMainGui((Player) player, menuVillage);
                break;
            case "light":
                handleAdminLight(player, args);
                break;
            case "saveall":
                villageManager.saveAll();
                msg(player, "&aAlle Doerfer gespeichert.");
                break;
            case "resyncwg":
                if (worldGuardHook == null || !worldGuardHook.isAvailable()) {
                    msg(player, "&cWorldGuard ist nicht verfuegbar.");
                    return;
                }
                int synced = 0;
                for (Village village : villageManager.getAllVillages()) {
                    if (worldGuardHook.syncVillageRegions(village)) {
                        synced++;
                    }
                }
                msg(player, "&aWorldGuard-Regionen resynchronisiert: &e" + synced);
                break;
            default:
                msg(player, "&cUnbekannter Admin-Befehl.");
                break;
        }
    }

    private void handleAdminMoney(CommandSender player, String[] args) {
        if (args.length < 6) {
            msg(player, "&cVerwendung: /village admin money add|remove <Player/all/village:Name> <currency(global|Dorfname)> <Betrag>");
            return;
        }
        String mode = args[2].toLowerCase();
        String target = args[3];
        String currencyInput = args[4];
        String currencyId;
        if ("global".equalsIgnoreCase(currencyInput)) {
            currencyId = currencyService.getGlobalCurrencyId();
        } else {
            Village currencyVillage = villageManager.getVillageByName(currencyInput).orElse(null);
            currencyId = currencyVillage != null
                    ? currencyService.getVillageCurrencyId(currencyVillage)
                    : currencyInput;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[5]);
        } catch (Exception e) {
            msg(player, "&cUngueltiger Betrag.");
            return;
        }
        if (amount <= 0) {
            msg(player, "&cBetrag muss positiv sein.");
            return;
        }
        List<UUID> targets = new ArrayList<>();
        if ("all".equalsIgnoreCase(target)) {
            for (Player online : Bukkit.getOnlinePlayers()) targets.add(online.getUniqueId());
        } else if (target.toLowerCase().startsWith("village:")) {
            String villageName = target.substring("village:".length());
            Village village = villageManager.getVillageByName(villageName).orElse(null);
            if (village == null) {
                msg(player, "&cDorf nicht gefunden.");
                return;
            }
            targets.addAll(village.getMembers().keySet());
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(target);
            targets.add(off.getUniqueId());
        }
        int changed = 0;
        for (UUID id : targets) {
            if ("add".equals(mode)) {
                currencyService.addBalance(id, currencyId, amount);
                changed++;
            } else if ("remove".equals(mode)) {
                if (currencyService.removeBalance(id, currencyId, amount)) changed++;
            }
        }
        currencyService.save();
        msg(player, "&aAdmin-Money ausgefuehrt. Betroffene Konten: " + changed);
    }

    private void showJoinRequestsOverview(CommandSender player, Village village) {
        List<Map.Entry<UUID, VillageJoinRequest>> requests = new ArrayList<>(village.getJoinRequests().entrySet());
        if (requests.isEmpty()) {
            msg(player, "&7Keine offenen Beitrittsanfragen fuer &e" + village.getName() + "&7.");
            return;
        }

        requests.sort(java.util.Comparator.comparingLong(e -> e.getValue().getRequestedAt()));
        msg(player, "&6Offene Beitrittsanfragen fuer &e" + village.getName() + "&6:");
        for (Map.Entry<UUID, VillageJoinRequest> entry : requests) {
            UUID requesterId = entry.getKey();
            String requesterName = Bukkit.getOfflinePlayer(requesterId).getName();
            if (requesterName == null) requesterName = requesterId.toString().substring(0, 8);

            if (!(player instanceof Player)) {
                msg(player, "&7- &f" + requesterName + " &e[Annehmen: /village joinrequest accept " + village.getId() + " " + requesterId + "] [Ablehnen: /village joinrequest deny " + village.getId() + " " + requesterId + "]");
                continue;
            }

            TextComponent accept = new TextComponent(color("&a[Annehmen]"));
            accept.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/village joinrequest accept " + village.getId() + " " + requesterId));
            accept.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(color("&7Anfrage von " + requesterName + " annehmen")).create()));

            TextComponent deny = new TextComponent(color("&c[Ablehnen]"));
            deny.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                    "/village joinrequest deny " + village.getId() + " " + requesterId));
            deny.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(color("&7Anfrage von " + requesterName + " ablehnen")).create()));

            ((Player) player).spigot().sendMessage(
                    new TextComponent(color("&7- &f" + requesterName + " ")),
                    accept,
                    new TextComponent(color(" ")),
                    deny
            );
        }
    }

    private void handleAdminLight(CommandSender player, String[] args) {
        if (args.length < 3) {
            msg(player, "&cVerwendung: /village admin light addpoi|addregion|addpath ...");
            msg(player, "&7Beispiel: /village admin light addpoi spawn world 0 64 0 50");
            return;
        }

        String sub = args[2].toLowerCase();
        File file = new File(plugin.getDataFolder(), "config/light-limits.yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "light-limits.yml");
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        try {
            switch (sub) {
                case "addpoi" -> {
                    if (args.length < 9) {
                        msg(player, "&cVerwendung: /village admin light addpoi <key> <world> <x> <y> <z> <radius>");
                        return;
                    }
                    String key = args[3];
                    String world = args[4];
                    double x = Double.parseDouble(args[5]);
                    double y = Double.parseDouble(args[6]);
                    double z = Double.parseDouble(args[7]);
                    double radius = Double.parseDouble(args[8]);
                    cfg.set("light-control.points-of-interest." + key + ".world", world);
                    cfg.set("light-control.points-of-interest." + key + ".x", x);
                    cfg.set("light-control.points-of-interest." + key + ".y", y);
                    cfg.set("light-control.points-of-interest." + key + ".z", z);
                    cfg.set("light-control.points-of-interest." + key + ".radius", radius);
                    cfg.save(file);
                    if (lightService != null) lightService.reloadConfiguration();
                    msg(player, "&aLicht-POI &e" + key + " &ahinzugefuegt.");
                }
                case "addregion" -> {
                    if (args.length < 7) {
                        msg(player, "&cVerwendung: /village admin light addregion <key> <world> <regionId> <baseRadius>");
                        return;
                    }
                    String key = args[3];
                    String world = args[4];
                    String regionId = args[5];
                    double baseRadius = Double.parseDouble(args[6]);
                    cfg.set("light-control.worldguard-regions." + key + ".world", world);
                    cfg.set("light-control.worldguard-regions." + key + ".region-id", regionId);
                    cfg.set("light-control.worldguard-regions." + key + ".base-radius", baseRadius);
                    cfg.save(file);
                    if (lightService != null) lightService.reloadConfiguration();
                    msg(player, "&aLicht-Region &e" + key + " &ahinzugefuegt.");
                }
                case "addpath" -> {
                    if (args.length < 11) {
                        msg(player, "&cVerwendung: /village admin light addpath <key> <world> <fromX> <fromZ> <toX> <toZ> <radius>");
                        return;
                    }
                    String key = args[3];
                    String world = args[4];
                    double fromX = Double.parseDouble(args[5]);
                    double fromZ = Double.parseDouble(args[6]);
                    double toX = Double.parseDouble(args[7]);
                    double toZ = Double.parseDouble(args[8]);
                    double radius = Double.parseDouble(args[9]);
                    cfg.set("light-control.paths." + key + ".world", world);
                    cfg.set("light-control.paths." + key + ".from.x", fromX);
                    cfg.set("light-control.paths." + key + ".from.z", fromZ);
                    cfg.set("light-control.paths." + key + ".to.x", toX);
                    cfg.set("light-control.paths." + key + ".to.z", toZ);
                    cfg.set("light-control.paths." + key + ".radius", radius);
                    cfg.save(file);
                    if (lightService != null) lightService.reloadConfiguration();
                    msg(player, "&aLicht-Pfad &e" + key + " &ahinzugefuegt.");
                }
                default -> msg(player, "&cUnbekannter Light-Befehl.");
            }
        } catch (NumberFormatException e) {
            msg(player, "&cUngueltige Zahl.");
        } catch (IOException e) {
            msg(player, "&cKonnte light-limits.yml nicht speichern: " + e.getMessage());
        }
    }

    private void handleHelp(Player player) {
        msg(player, "&6=== Dorf-Hilfe ===");
        msg(player, "&e/village &7- Oeffnet das Dorf-Menue");
        msg(player, "&e/village info [Name] &7- Zeigt Dorf-Infos");
        msg(player, "&e/village list &7- Listet alle Doerfer");
        msg(player, "&e/village tp <village_id|Name> [Spieler=self] &7- Teleportiere dich oder einen Spieler zum Dorf");
        msg(player, "&e/village spawn [Spieler] &7- Teleportiert dich oder einen Spieler zum eigenen Dorf");
        msg(player, "&e/village join <Name> &7- Beitrittsanfrage senden");
        msg(player, "&e/village sendmoney|sm <Spieler> <Betrag> &7- Dorfwährung senden");
        msg(player, "&e/village balance [Spieler] &7- Dorfwährung anzeigen");
        msg(player, "&e/village balances [Dorf] &7- Dorfwährungsliste");
        msg(player, "&e/village joinrequest accept|deny <Dorf-UUID> <Spieler-UUID> &7- Anfrage im Chat bearbeiten");
        msg(player, "&e/village leave &7- Verlasse dein Dorf");
        msg(player, "&e/village invite <Spieler> &7- Lade einen Spieler ein");
        msg(player, "&e/village kick <Spieler> &7- Entferne einen Spieler");
        msg(player, "&e/village promote <Spieler> <Rolle> &7- Befoerdere einen Spieler");
        msg(player, "&e/village border &7- Grenzauswahl oeffnen");
        msg(player, "&e/village preview &7- Grenzvorschau anzeigen");
        msg(player, "&e/village path done &7- Schließe einen Pfad ab");
        msg(player, "&e/village path cancel &7- Brich einen Pfad ab");
        msg(player, "&e/village manage border info &7- Zeigt Grenzflächen-Infos");
        msg(player, "&e/village manage border delete [ID] &7- Löscht eine Grenzfläche");
        msg(player, "&e/village manage border fusion <ID1> <ID2> &7- Fusioniert zwei Grenzflächen");
        msg(player, "&e/village delete [Name] &7- Löscht dein oder ein benanntes Dorf");
        msg(player, "&e/village rename &7- Starte Dorf-Umbenennung per Chat");
        msg(player, "&e/village rename <Name> &7- Sofort umbenennen");
        msg(player, "&e/village revive &7- Den zuletzt gestorbenen Dorfbewohner wiederbeleben");
        msg(player, "&e/v abort &7- Bricht aktuelle Auswahl-/Abbruchaktionen ab");
        msg(player, "&e/village menu &7- Dorf-Menue oeffnen");
        msg(player, "&e/village building list &7- Zeigt Gebäude im Dorf");
        msg(player, "&e/village building remove <Nr> &7- Entfernt ein Gebäude");
        msg(player, "&e/village building movesign <Nr> &7- Verschiebt ein Gebäudeschild");
        msg(player, "&e/village building upgrade <Nr> &7- Startet ein Gebäude-Upgrade");
        msg(player, "&e/village building start <Nr> &7- Beginnt den Bau eines Gebäudes");
        msg(player, "&e/village building cancel <Nr> &7- Bricht einen Bauauftrag ab");
        msg(player, "&e/village schematic list <building_type> &7- Zeigt Schematic-Varianten und den Tool-Button");
        if (player.hasPermission("village.admin")) {
            msg(player, "&c/village admin &7- Admin-Befehle");
            msg(player, "&c/village reload &7- Konfig neu laden");
            msg(player, "&c/village admin bordernames true|false &7- Actionbar fuer Grenzregionen umschalten");
            msg(player, "&c/village admin joinrequests <Dorf> &7- Offene Beitrittsanfragen anzeigen");
            msg(player, "&c/village admin menu <Dorf> &7- Dorfmenü auch als Admin öffnen");
            msg(player, "&c/village admin light addpoi|addregion|addpath ... &7- Lichtquellen verwalten");
            msg(player, "&c/village schematic tool &7- Schematic-Werkzeug erhalten");
            msg(player, "&c/village schematic pos1 &7- Setzt Auswahl-Pos1");
            msg(player, "&c/village schematic pos2 &7- Setzt Auswahl-Pos2");
            msg(player, "&c/village schematic origin &7- Setzt den Ursprung");
            msg(player, "&c/village schematic save <name> &7- Speichert die Auswahl");
            msg(player, "&c/village schematic register <name> [name:<display>] [category:<cat>] [perm:<perm>] [icon:<ICON>] [level:<n>] &7- Registriert die Schematic");
        }
    }

    private void handleRevive(Player player) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }
        if (!village.isMember(player.getUniqueId()) && !player.hasPermission("village.admin")) {
            msg(player, configManager.message("no-permission"));
            return;
        }
        if (villagerService == null) {
            msg(player, "&cVillager-Service ist nicht verfuegbar.");
            return;
        }
        if (!configManager.isVillagerRevivalEnabled()) {
            msg(player, configManager.message("villager-revive-disabled"));
            return;
        }
        if (!villagerService.isRevivalAvailable(village)) {
            msg(player, configManager.message("villager-revive-none"));
            return;
        }

        long cooldownRemaining = villagerService.getRevivalCooldownRemainingMillis(village);
        if (cooldownRemaining > 0) {
            msg(player, configManager.message("villager-revive-cooldown")
                    .replace("%time%", formatDuration(cooldownRemaining)));
            return;
        }

        double cost = villagerService.getRevivalCost(village);
        String currencyType = configManager.getVillagerRevivalCurrencyType();
        boolean charged = true;
        if (cost > 0) {
            if ("local".equalsIgnoreCase(currencyType)) {
                if (currencyService == null) {
                    msg(player, configManager.message("villager-revive-no-economy"));
                    return;
                }
                String currencyId = currencyService.getVillageCurrencyId(village);
                if (currencyService.getBalance(player.getUniqueId(), currencyId) < cost) {
                    msg(player, configManager.message("villager-revive-no-money")
                            .replace("%cost%", formatMoney(cost)));
                    return;
                }
                charged = currencyService.removeBalance(player.getUniqueId(), currencyId, cost);
            } else {
                if (vaultHook == null || !vaultHook.isAvailable()) {
                    msg(player, configManager.message("villager-revive-no-economy"));
                    return;
                }
                if (!vaultHook.has(player, cost)) {
                    msg(player, configManager.message("villager-revive-no-money")
                            .replace("%cost%", formatMoney(cost)));
                    return;
                }
                charged = vaultHook.withdraw(player, cost);
            }
        }

        if (!charged) {
            msg(player, configManager.message("villager-revive-no-money")
                    .replace("%cost%", formatMoney(cost)));
            return;
        }

        if (villagerService.reviveLastDeadVillager(village)) {
            msg(player, configManager.message("villager-revive-success")
                    .replace("%cost%", formatMoney(cost)));
        } else {
            if (cost > 0) {
                if ("local".equalsIgnoreCase(currencyType) && currencyService != null) {
                    currencyService.addBalance(player.getUniqueId(), currencyService.getVillageCurrencyId(village), cost);
                } else if (vaultHook != null && vaultHook.isAvailable()) {
                    vaultHook.deposit(player, cost);
                }
            }
            msg(player, configManager.message("villager-revive-failed"));
        }
    }

    private String formatMoney(double amount) {
        if (vaultHook != null && vaultHook.isAvailable()) {
            return vaultHook.format(amount);
        }
        return String.format(java.util.Locale.US, "%.2f", amount);
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        if (minutes <= 0L) {
            return remainingSeconds + "s";
        }
        return minutes + "m " + remainingSeconds + "s";
    }

    private void handleVillageSendMoney(Player player, String[] args) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, "&cDu bist in keinem Dorf.");
            return;
        }
        if (args.length < 3) {
            msg(player, "&cVerwendung: /village sendmoney <Spieler> <Betrag>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            msg(player, "&cSpieler ist nicht online.");
            return;
        }
        if (!village.isMember(target.getUniqueId())) {
            msg(player, "&cDer Spieler ist nicht in deinem Dorf.");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            msg(player, "&cUngueltiger Betrag.");
            return;
        }
        if (amount <= 0) {
            msg(player, "&cBetrag muss positiv sein.");
            return;
        }
        String currencyId = currencyService.getVillageCurrencyId(village);
        if (currencyService.getBalance(player.getUniqueId(), currencyId) < amount) {
            msg(player, "&cNicht genug Dorfguthaben.");
            return;
        }
        pendingVillageTransfers.put(player.getUniqueId(), new PendingTransfer(target.getUniqueId(), currencyId, amount, village.getName()));
        MessageUtil.sendYesNoRunCommand(player, configManager.getPrefix(),
                "&e" + amount + " an " + target.getName() + " senden?",
                "/village sendmoneyconfirm ja",
                "/village sendmoneyconfirm nein");
    }

    private void handleVillageSendMoneyConfirm(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&cBitte ja oder nein.");
            return;
        }
        String choice = args[1].toLowerCase();
        PendingTransfer pending = pendingVillageTransfers.remove(player.getUniqueId());
        if (pending == null) {
            msg(player, "&cKeine ausstehende Ueberweisung.");
            return;
        }
        if (!"ja".equals(choice) && !"yes".equals(choice)) {
            msg(player, "&eUeberweisung abgebrochen.");
            return;
        }
        boolean success = currencyService.transfer(player.getUniqueId(), pending.targetId(), pending.currencyId(), pending.amount());
        if (!success) {
            msg(player, "&cUeberweisung fehlgeschlagen.");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(pending.targetId());
        msg(player, "&aGesendet: " + pending.amount() + " an " + (target.getName() != null ? target.getName() : pending.targetId()));
        if (target.isOnline() && target.getPlayer() != null) {
            msg(target.getPlayer(), "&aDu hast " + pending.amount() + " aus Dorf " + pending.villageName() + " erhalten.");
        }
        currencyService.save();
    }

    private void handleVillageBalance(Player player, String[] args) {
        UUID targetId = player.getUniqueId();
        if (args.length >= 2) {
            if (!player.hasPermission("village.admin")) {
                msg(player, "&cKeine Berechtigung.");
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            targetId = target.getUniqueId();
        }
        Village village = villageManager.getPlayerVillage(targetId).orElse(null);
        if (village == null) {
            msg(player, "&cSpieler hat kein Dorf.");
            return;
        }
        String currencyId = currencyService.getVillageCurrencyId(village);
        double bal = currencyService.getBalance(targetId, currencyId);
        String name = Bukkit.getOfflinePlayer(targetId).getName();
        msg(player, "&7Dorfwährung (" + village.getName() + ") von &e" + (name != null ? name : targetId) + "&7: &a" + fmt(bal));
    }

    private void handleVillageBalances(Player player, String[] args) {
        if (!player.hasPermission("village.admin")) {
            msg(player, "&cKeine Berechtigung.");
            return;
        }
        Village village;
        if (args.length >= 2) {
            village = villageManager.getVillageByName(String.join(" ", Arrays.copyOfRange(args, 1, args.length))).orElse(null);
        } else {
            village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        }
        if (village == null) {
            msg(player, "&cDorf nicht gefunden.");
            return;
        }
        msg(player, "&6=== Dorfbilanzen: " + village.getName() + " ===");
        for (Map.Entry<UUID, Double> entry : currencyService.getVillageMemberBalances(village).entrySet()) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            msg(player, "&e" + (name != null ? name : entry.getKey()) + "&7: &a" + fmt(entry.getValue()));
        }
    }

    private String fmt(double value) {
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private record PendingTransfer(UUID targetId, String currencyId, double amount, String villageName) {}

    private void handleBorderConfirm(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&7Bitte klicke &a[JA] &7oder &c[NEIN]&7.");
            return;
        }
        String choice = args[1].toLowerCase();
        boolean yes = "ja".equals(choice);
        boolean no = "nein".equals(choice);
        if (!yes && !no) {
            msg(player, "&7Ungueltig. Erlaubt: &aja &7oder &cnein&7.");
            return;
        }
        if (borderService == null) {
            msg(player, "&cKeine ausstehende Grenzsetzung gefunden.");
            return;
        }

        boolean hasNormal = borderService.hasPendingBorderConfirmation(player.getUniqueId());
        boolean hasAction = borderService.hasPendingBorderActionConfirmation(player.getUniqueId());
        if (!hasNormal && !hasAction) {
            msg(player, "&cKeine ausstehende Grenzsetzung gefunden.");
            return;
        }

        if (no) {
            if (hasAction) {
                var conf = borderService.removePendingBorderActionConfirmation(player.getUniqueId());
                if (conf != null) {
                    previewService.clearPreview(player);
                    guiManager.openBorderSelectionGui(player, conf.getVillage());
                }
            } else {
                var conf = borderService.removePendingBorderConfirmation(player.getUniqueId());
                if (conf != null) {
                    previewService.clearPreview(player);
                    guiManager.openBorderSelectionGui(player, conf.getVillage());
                }
            }
            msg(player, "&eGrenzsetzung abgebrochen.");
            return;
        }

        if (hasAction) {
            var conf = borderService.removePendingBorderActionConfirmation(player.getUniqueId());
            if (conf == null) {
                msg(player, "&cKeine ausstehende Grenzsetzung gefunden.");
                return;
            }
            Village village = conf.getVillage();
            borderService.setUndoState(player.getUniqueId(), village);
            if (conf.getTargetBorderId() != null) {
                int targetId = conf.getTargetBorderId();
                village.getBorders().removeIf(border -> border.getId() == targetId);
            }
            for (VillageBorder border : conf.getBorders()) {
                if (border.getId() <= 0) {
                    border.setId(village.nextBorderId());
                }
                village.addBorder(border);
            }
            villageManager.saveVillage(village);
            msg(player, configManager.message("border-set"));
            MessageUtil.sendRunCommand(player, configManager.getPrefix(), "&7Du kannst diese Aktion rückgängig machen:", "[RÜCKGÄNGIG]", "/village borderundo");
            guiManager.openBorderSelectionGui(player, village);
            return;
        }

        var conf = borderService.removePendingBorderConfirmation(player.getUniqueId());
        if (conf == null) {
            msg(player, "&cKeine ausstehende Grenzsetzung gefunden.");
            return;
        }
        borderService.setUndoState(player.getUniqueId(), conf.getVillage());
        conf.getVillage().addBorder(conf.getBorder());
        villageManager.saveVillage(conf.getVillage());
        msg(player, configManager.message("border-set"));
        MessageUtil.sendRunCommand(player, configManager.getPrefix(), "&7Du kannst diese Aktion rückgängig machen:", "[RÜCKGÄNGIG]", "/village borderundo");
        guiManager.openBorderSelectionGui(player, conf.getVillage());
    }

    private void handleBorderUndo(Player player) {
        if (borderService == null) return;
        var undoState = borderService.getAndRemoveUndoState(player.getUniqueId());
        if (undoState == null) {
            msg(player, "&cEs gibt keine Aktion, die du rückgängig machen kannst.");
            return;
        }
        Village village = undoState.getVillage();
        village.getBorders().clear();
        village.getBorders().addAll(undoState.getOldBorders());
        villageManager.saveVillage(village);
        
        if (previewService != null) {
            previewService.clearPreview(player);
            previewService.showPreviewMultipleForSeconds(player, village.getBorders(), player.getWorld(), 10);
        }
        
        msg(player, "&aDie letzte Grenzänderung wurde rückgängig gemacht.");
        guiManager.openBorderSelectionGui(player, village);
    }

    private void handleBorderCancel(Player player) {
        UUID pid = player.getUniqueId();
        boolean any = false;
        com.example.village.model.Village village = null;
        if (borderService.isInWalkSession(pid)) {
            BorderService.BorderWalkSession session = borderService.getWalkSession(pid);
            if (session != null) village = session.getVillage();
            borderService.cancelWalkSelection(pid);
            any = true;
        }
        if (borderService.isInCoordSession(pid)) {
            BorderService.CoordSession session = borderService.getCoordSession(pid);
            if (session != null) village = session.getVillage();
            borderService.cancelCoordSelection(pid);
            any = true;
            MessageUtil.send(player, configManager.getPrefix(), configManager.message("border-walk-stop"));
        }
        if (!any) {
            msg(player, "&cKeine laufende Grenzziehung gefunden.");
        } else if (village != null) {
            guiManager.openBorderSelectionGui(player, village);
        }
    }

    private void handleBorderRestart(Player player) {
        UUID pid = player.getUniqueId();
        if (borderService.isInCoordSession(pid)) {
            BorderService.CoordSession s = borderService.getCoordSession(pid);
            if (s != null) {
                Village v = s.getVillage();
                borderService.cancelCoordSelection(pid);
                borderService.startCoordSelection(player, v);
                MessageUtil.send(player, configManager.getPrefix(), "&aKoordinaten-Auswahl neu gestartet.");
                return;
            }
        }
        if (borderService.isInWalkSession(pid)) {
            BorderService.BorderWalkSession s = borderService.getWalkSession(pid);
            if (s != null) {
                Village v = s.getVillage();
                borderService.cancelWalkSelection(pid);
                borderService.startWalkSelection(player, v);
                MessageUtil.send(player, configManager.getPrefix(), "&aGrenzziehung neu gestartet.");
                return;
            }
        }
        msg(player, "&cKeine laufende Grenzziehung gefunden.");
    }

    private void handleBuildConfirm(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&7Bitte klicke &a[JA] &7oder &c[NEIN]&7.");
            return;
        }
        String choice = args[1].toLowerCase();
        boolean yes = "ja".equals(choice);
        boolean no = "nein".equals(choice);
        if (!yes && !no) {
            msg(player, "&7Ungueltig. Erlaubt: &aja &7oder &cnein&7.");
            return;
        }

        BuildingService.PendingBuildingConfirmation pending =
                buildingService.getPendingConfirmation(player.getUniqueId());
        if (pending == null) {
            msg(player, "&cKeine ausstehende Gebaeudeplatzierung gefunden.");
            return;
        }

        if (no) {
            buildingService.cancelBuildingPreview(player);
            MessageUtil.send(player, configManager.getPrefix(),
                    "&eGebäudeplatzierung abgebrochen. Rechtsklicke erneut, um einen neuen Standort auszuwählen.");
            return;
        }

        BuildingService.PlaceResult result = buildingService.confirmBuilding(player);
        if (result == BuildingService.PlaceResult.SUCCESS) {
            String name = buildingService.getDisplayNameForType(pending.getTypeKey());
            msg(player, configManager.message("building-placed").replace("%building%", name));
        } else if (result == BuildingService.PlaceResult.BORDER_EXPANSION_NEEDED) {
            // Dialog is shown by confirmBuilding(); wait for chat "ja/nein"
        } else if (result == BuildingService.PlaceResult.OVERLAPS_BUILDING) {
            String diag = buildingService.getLastPlacementDiagnostic(player.getUniqueId());
            String base = "&cDas Gebäude überschneidet sich mit einem bestehenden Gebäude oder dem Dorfbrunnen. Bitte wähle eine andere Position.";
            if (diag != null && !diag.isBlank()) base += " &7(" + diag + ")";
            msg(player, base);
            buildingService.clearLastPlacementDiagnostic(player.getUniqueId());
        } else if (result == BuildingService.PlaceResult.PARTIAL_OUTSIDE_BORDER) {
            msg(player, "&cDas Gebäude liegt teilweise auf einer Dorfgrenze. Bitte vollständig innerhalb des Dorfgebiets platzieren.");
        } else if (result == BuildingService.PlaceResult.ON_START_BORDER) {
            msg(player, "&cDas Gebaeude darf nicht auf dem Dorfbrunnen oder auf einem anderen Gebäude/Baustelle platziert werden.");
        } else if (result == BuildingService.PlaceResult.INSIDE_DEFAULT_BORDER) {
            msg(player, "&cDas Gebaeude darf nicht vollstaendig nur in der Standard-Grenze (ID 0) liegen,"
                    + " wenn bereits erweiterte Gebiete existieren. Bitte weiter draussen platzieren.");
        } else {
            msg(player, "&cGebaeude konnte nicht platziert werden: " + result);
        }
    }

    private void handleBuildExpansionConfirm(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&7Bitte klicke &a[JA] &7oder &c[NEIN]&7.");
            return;
        }
        String choice = args[1].toLowerCase();
        boolean yes = "ja".equals(choice);
        boolean no = "nein".equals(choice);
        if (!yes && !no) {
            msg(player, "&7Ungueltig. Erlaubt: &aja &7oder &cnein&7.");
            return;
        }

        if (!buildingService.getBorderExpansionConfirmations().containsKey(player.getUniqueId())) {
            msg(player, "&cKeine ausstehende Grenzerweiterung gefunden.");
            return;
        }

        BuildingService.PendingBuildingConfirmation pending =
                buildingService.getPendingConfirmation(player.getUniqueId());

        if (no) {
            buildingService.getBorderExpansionConfirmations().remove(player.getUniqueId());
            buildingService.cancelBuildingPreview(player);
            if (pending != null) {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&eGebäudeplatzierung abgebrochen. Rechtsklicke erneut, um einen neuen Standort auszuwählen.");
            }
            return;
        }

        BuildingService.PlaceResult result = buildingService.confirmBorderExpansion(player);
        if (result == BuildingService.PlaceResult.SUCCESS) {
            buildingService.getBorderExpansionConfirmations().remove(player.getUniqueId());
            if (pending != null) {
                String name = buildingService.getDisplayNameForType(pending.getTypeKey());
                msg(player, configManager.message("building-placed").replace("%building%", name));
            }
        } else if (result == BuildingService.PlaceResult.OVERLAPS_BUILDING) {
            String diag = buildingService.getLastPlacementDiagnostic(player.getUniqueId());
            String base = "&cDas Gebäude überschneidet sich mit einem bestehenden Gebäude oder dem Dorfbrunnen. Bitte wähle eine andere Position.";
            if (diag != null && !diag.isBlank()) base += " &7(" + diag + ")";
            msg(player, base);
            buildingService.clearLastPlacementDiagnostic(player.getUniqueId());
        } else if (result == BuildingService.PlaceResult.PARTIAL_OUTSIDE_BORDER) {
            msg(player, "&cDas Gebäude liegt teilweise auf einer Dorfgrenze. Bitte vollständig innerhalb des Dorfgebiets platzieren.");
        } else if (result == BuildingService.PlaceResult.ON_START_BORDER) {
            msg(player, "&cDas Gebaeude darf nicht auf dem Dorfbrunnen oder auf einem anderen Gebäude/Baustelle platziert werden.");
        } else if (result == BuildingService.PlaceResult.INSIDE_DEFAULT_BORDER) {
            msg(player, "&cDas Gebaeude darf nicht vollstaendig nur in der Standard-Grenze (ID 0) liegen,"
                    + " wenn bereits erweiterte Gebiete existieren. Bitte weiter draussen platzieren.");
        } else {
            msg(player, "&cGebaeude-Platzierung fehlgeschlagen: " + result);
        }
    }

    private void handleManage(Player player, String[] args) {
        if (args.length < 2) {
            msg(player, "&7Verwendung: /village manage border ...");
            return;
        }
        String type = args[1].toLowerCase();
        if ("border".equals(type) || "grenze".equals(type)) {
            handleManageBorder(player, args);
            return;
        }
        msg(player, "&cUnbekannter Manage-Typ.");
    }

    private void handleTp(Player sender, String[] args) {
        if (!sender.hasPermission("village.admin") && !sender.hasPermission("village.tp.others")) {
            msg(sender, configManager.message("no-permission"));
            return;
        }
        if (args.length < 2) {
            msg(sender, "&cVerwendung: /village tp <village_id|name> [<player>] ");
            return;
        }
        String idOrName = args[1];
        java.util.Optional<Village> opt = Optional.empty();
        try {
            java.util.UUID uuid = java.util.UUID.fromString(idOrName);
            opt = villageManager.getVillage(uuid);
        } catch (IllegalArgumentException ignored) {
            opt = villageManager.getVillageByName(idOrName);
        }
        if (opt.isEmpty()) {
            msg(sender, configManager.message("village-not-found"));
            return;
        }
        Village v = opt.get();
        org.bukkit.entity.Player targetPlayer = (sender instanceof org.bukkit.entity.Player) ? (org.bukkit.entity.Player) sender : null;
        if (args.length >= 3 && !"self".equalsIgnoreCase(args[2])) {
            org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(args[2]);
            if (off == null || off.getPlayer() == null) {
                msg(sender, "&cSpieler nicht gefunden oder nicht online.");
                return;
            }
            targetPlayer = off.getPlayer();
        }

        if (targetPlayer == null) {
            msg(sender, "&cNur Spieler können teleportiert werden.");
            return;
        }

        if (v.getBellLocation() == null) {
            msg(sender, "&cDas Dorf hat keinen Glocken-Standort gesetzt.");
            return;
        }
        targetPlayer.teleport(v.getBellLocation());
        msg(sender, "&aTeleportiert &e" + targetPlayer.getName() + " &azu Dorf &e" + v.getName());
    }

    private void handleSpawn(Player sender, String[] args) {
        Player target = sender;
        if (args.length >= 2 && !"self".equalsIgnoreCase(args[1])) {
            // allow admins to spawn other players
            if (!sender.hasPermission("village.admin")) {
                msg(sender, configManager.message("no-permission"));
                return;
            }
            org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
            if (off == null || off.getPlayer() == null) {
                msg(sender, "&cSpieler nicht gefunden oder nicht online.");
                return;
            }
            target = off.getPlayer();
        }

        java.util.Optional<Village> vopt = villageManager.getPlayerVillage(target.getUniqueId());
        if (vopt.isEmpty()) {
            msg(sender, "&cSpieler ist in keinem Dorf.");
            return;
        }
        Village v = vopt.get();
        if (v.getBellLocation() == null) {
            msg(sender, "&cDas Dorf hat keinen Glocken-Standort gesetzt.");
            return;
        }
        target.teleport(v.getBellLocation());
        msg(sender, "&aTeleportiert &e" + target.getName() + " &azu Dorfzentrum von &e" + v.getName());
    }

    private void handleManageBorder(Player player, String[] args) {
        Village village = villageManager.getPlayerVillage(player.getUniqueId()).orElse(null);
        if (village == null) {
            msg(player, configManager.message("village-not-found"));
            return;
        }
        if (!villageManager.canManageVillage(village, player.getUniqueId())
                && !player.hasPermission("village.admin")) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        if (args.length < 3) {
            msg(player, "&7Verwendung: /village manage border info|delete|fusion ...");
            return;
        }
        String action = args[2].toLowerCase();
        switch (action) {
            case "info" -> handleManageBorderInfo(player, village);
            case "delete" -> handleManageBorderDelete(player, village, args);
            case "deleteconfirm" -> handleManageBorderDeleteConfirm(player, args);
            case "fusion" -> handleManageBorderFusion(player, village, args);
            case "fusionfinish" -> handleManageBorderFusionFinish(player, village, args);
            case "split" -> handleManageBorderSplit(player, village, args);
            case "enlarge" -> handleManageBorderEnlarge(player, village, args);
            case "shrink" -> handleManageBorderShrink(player, village, args);
            case "cancel" -> handleCancel(player);
            default -> msg(player, "&cUnbekannte Aktion. Nutze: info, delete, fusion");
        }
    }

    private void handleCancel(Player player) {
        BorderService.PendingBorderSelection pending = null;
        if (borderService != null) {
            pending = borderService.getPendingBorderSelection(player.getUniqueId());
            borderService.removePendingBorderSelection(player.getUniqueId());
            borderService.setPendingBorderDeletion(player.getUniqueId(), null);
        }
        if (buildingService != null) {
            buildingService.cancelPlacement(player.getUniqueId());
            buildingService.cancelBuildingPreview(player);
        }
        if (previewService != null) {
            previewService.clearPreview(player);
        }
        if (guiClickListener != null) {
            guiClickListener.getPendingJobAssignments().remove(player.getUniqueId());
            guiClickListener.getPendingBedAssignments().remove(player.getUniqueId());
        }
        msg(player, "&eAktion abgebrochen.");
        if (pending != null && pending.getVillage() != null) {
            guiManager.openBorderSelectionGui(player, pending.getVillage());
        }
    }

    private void handleManageBorderFusionFinish(Player player, Village village, String[] args) {
        if (args.length < 4) {
            msg(player, "&7Bitte klicke &a[JA] &7oder &c[NEIN]&7.");
            return;
        }
        String choice = args[3].toLowerCase();
        boolean yes = "ja".equals(choice);
        boolean no = "nein".equals(choice);
        if (!yes && !no) {
            msg(player, "&7Ungueltig. Erlaubt: &aja &7oder &cnein&7.");
            return;
        }

        BorderService.PendingBorderSelection pending = borderService.getPendingBorderSelection(player.getUniqueId());
        if (pending == null || pending.getMode() != BorderService.BorderSelectionMode.FUSION) {
            msg(player, "&cKeine ausstehende Fusion-Auswahl gefunden.");
            return;
        }
        if (no) {
            msg(player, "&7Waehle weitere Flaechen durch Klick aus.");
            return;
        }

        java.util.List<Integer> ids = new java.util.ArrayList<>(pending.getSelectedBorderIds());
        ids.sort(Integer::compareTo);
        if (ids.size() < 2) {
            msg(player, "&cBitte waehle mindestens 2 Grenzflaechen aus.");
            return;
        }

        if (fuseBorderIds(player, village, ids)) {
            borderService.removePendingBorderSelection(player.getUniqueId());
            previewService.clearPreview(player);
            guiManager.openBorderSelectionGui(player, village);
        } else {
            guiManager.openBorderSelectionGui(player, village);
        }
    }

    private void handleManageBorderInfo(Player player, Village village) {
        village.ensureBorderIds();
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();
        var current = village.getBorderAt(x, z);

        msg(player, "&6=== Grenzflaechen: " + village.getName() + " ===");
        if (current != null) {
            msg(player, "&7Du stehst in Flaeche: &e" + current.getId());
        } else {
            msg(player, "&7Du stehst in Flaeche: &c(keine)");
        }

        for (var b : village.getBorders()) {
            int owners = b.getOwners().size();
            msg(player, "&7- &e" + b.getId()
                    + " &8| &7Punkte: &e" + b.getBorderPoints().size()
                    + " &8| &7Flaeche: &e" + b.calculateArea()
                    + " &8| &7Owner: &e" + owners);
        }
    }

    private void handleManageBorderDelete(Player player, Village village, String[] args) {
        village.ensureBorderIds();

        int id;
        if (args.length >= 4) {
            try {
                id = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                msg(player, "&cUngueltige ID.");
                return;
            }
        } else {
            int x = player.getLocation().getBlockX();
            int z = player.getLocation().getBlockZ();
            var b = village.getBorderAt(x, z);
            if (b == null) {
                msg(player, "&cDu stehst in keiner Grenzflaeche.");
                return;
            }
            id = b.getId();
        }

        if (id == 0) {
            msg(player, "&cDie Default-Grenzflaeche (ID 0) kann nicht geloescht werden.");
            return;
        }

        var target = village.getBorderById(id);
        if (target == null) {
            msg(player, "&cGrenzflaeche nicht gefunden: " + id);
            return;
        }

        // Determine which borders would become disconnected from the default border after deleting target.
        java.util.Set<Integer> deleteIds = computeBordersToDeleteAfterRemoval(village, id);
        if (deleteIds.isEmpty()) {
            msg(player, "&cInterner Fehler: keine Border-IDs berechnet.");
            return;
        }

        // Determine buildings on those borders.
        java.util.List<java.util.UUID> buildingIds = new java.util.ArrayList<>();
        java.util.List<String> buildingLabels = new java.util.ArrayList<>();
        for (var building : village.getBuildings()) {
            var loc = building.getLocation();
            if (loc == null) continue;
            var b = village.getBorderAt(loc.getBlockX(), loc.getBlockZ());
            if (b != null && deleteIds.contains(b.getId())) {
                buildingIds.add(building.getId());
                buildingLabels.add(building.getTypeKey() + "_" + building.getId().toString().substring(0, 8));
            }
        }

        java.util.List<Integer> deleteList = new java.util.ArrayList<>(deleteIds);
        deleteList.sort(Integer::compareTo);

        borderService.setPendingBorderDeletion(
                player.getUniqueId(),
                new BorderService.PendingBorderDeletion(village, deleteList, buildingIds)
        );

        String bordersStr = deleteList.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        String buildingsStr = buildingLabels.isEmpty()
                ? "(keine)"
                : String.join(", ", buildingLabels);

        MessageUtil.sendYesNoAbortRunCommand(
                player,
                configManager.getPrefix(),
                "&cWarnung: Wenn du Flaeche &e" + id + " &cloescht, werden auch diese Flaechen geloescht: &e" + bordersStr
                        + " &c| Gebaeude: &e" + buildingsStr + " &c. Fortfahren?",
                "/village manage border deleteconfirm ja",
                "/village manage border deleteconfirm nein",
                "/village manage border cancel"
        );
    }

    private void handleManageBorderDeleteConfirm(Player player, String[] args) {
        if (args.length < 4) {
            msg(player, "&7Bitte klicke &a[JA] &7oder &c[NEIN]&7.");
            return;
        }
        String choice = args[3].toLowerCase();
        boolean yes = "ja".equals(choice);
        boolean no = "nein".equals(choice);
        if (!yes && !no) {
            msg(player, "&7Ungueltig. Erlaubt: &aja &7oder &cnein&7.");
            return;
        }

        BorderService.PendingBorderDeletion pending = borderService.removePendingBorderDeletion(player.getUniqueId());
        if (pending == null) {
            msg(player, "&cKeine ausstehende Border-Loeschung gefunden.");
            return;
        }

        if (no) {
            msg(player, "&eLoeschung abgebrochen.");
            return;
        }

        Village village = pending.getVillage();
        village.ensureBorderIds();

        java.util.Set<Integer> deleteIds = new java.util.HashSet<>(pending.getBorderIdsToDelete());

        // Remove buildings (world blocks + sign) and remove from village list.
        if (!pending.getBuildingIdsToDelete().isEmpty()) {
            java.util.Set<java.util.UUID> buildingIdSet = new java.util.HashSet<>(pending.getBuildingIdsToDelete());
            java.util.List<com.example.village.model.VillageBuilding> toRemove = new java.util.ArrayList<>();
            for (var b : village.getBuildings()) {
                if (buildingIdSet.contains(b.getId())) {
                    toRemove.add(b);
                }
            }
            for (var b : toRemove) {
                buildingService.removeBuildingCompletely(b);
            }
            village.getBuildings().removeIf(b -> buildingIdSet.contains(b.getId()));
        }

        // Remove borders not connected to the default border component.
        village.getBorders().removeIf(b -> deleteIds.contains(b.getId()));

        villageManager.saveVillage(village);
        msg(player, "&aGrenzflaechen geloescht: &e" + pending.getBorderIdsToDelete().stream()
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")));
        // Temporarily show remaining borders after deletion
        if (previewService != null) {
            previewService.showPreviewMultipleForSeconds(player, village.getBorders(), player.getWorld(), 30);
        }
    }

    private void handleManageBorderSplit(Player player, Village village, String[] args) {
        if (args.length < 4) {
            msg(player, "&7Verwendung: /village manage border split <ID>");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            msg(player, "&cUngueltige ID.");
            return;
        }
        village.ensureBorderIds();
        var border = village.getBorderById(id);
        if (border == null) {
            msg(player, "&cGrenzflaeche nicht gefunden: " + id);
            return;
        }
        previewService.showPreview(player, border, player.getWorld());
        borderService.startBorderSplitWalk(player, village, id);
        MessageUtil.sendTwoRunCommands(player, configManager.getPrefix(),
                "&7Grenze teilen gestartet: Lauf von außerhalb in die gewählte Fläche und beende an der Grenze.",
                "[ABBRECHEN]", "/village bordercancel",
                "[VON VORN BEGINNEN]", "/village borderrestart");
    }
    private void handleManageBorderEnlarge(Player player, Village village, String[] args) {
        if (args.length < 4) {
            msg(player, "&7Verwendung: /village manage border enlarge <ID>");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            msg(player, "&cUngueltige ID.");
            return;
        }
        village.ensureBorderIds();
        var border = village.getBorderById(id);
        if (border == null) {
            msg(player, "&cGrenzflaeche nicht gefunden: " + id);
            return;
        }
        previewService.showPreview(player, border, player.getWorld());
        borderService.startBorderEnlargeWalk(player, village, id);
        MessageUtil.sendTwoRunCommands(player, configManager.getPrefix(),
                "&7Vergrösserung gestartet: Lauf von außerhalb der gewählten Grenze und beende am vorhandenen Gebiet.",
                "[ABBRECHEN]", "/village bordercancel",
                "[VON VORN BEGINNEN]", "/village borderrestart");
    }
    private void handleManageBorderShrink(Player player, Village village, String[] args) {
        if (args.length < 4) {
            msg(player, "&7Verwendung: /village manage border shrink <ID>");
            return;
        }
        int id;
        try {
            id = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            msg(player, "&cUngueltige ID.");
            return;
        }
        village.ensureBorderIds();
        var border = village.getBorderById(id);
        if (border == null) {
            msg(player, "&cGrenzflaeche nicht gefunden: " + id);
            return;
        }
        previewService.showPreview(player, border, player.getWorld());
        borderService.startBorderShrinkWalk(player, village, id);
        MessageUtil.sendTwoRunCommands(player, configManager.getPrefix(),
                "&7Verkleinerung gestartet: Lauf von außerhalb in die gewählte Fläche und beende an der Grenze.",
                "[ABBRECHEN]", "/village bordercancel",
                "[VON VORN BEGINNEN]", "/village borderrestart");
    }
    private java.util.Set<Integer> computeBordersToDeleteAfterRemoval(Village village, int removeId) {
        java.util.List<com.example.village.model.VillageBorder> remaining = new java.util.ArrayList<>();
        for (var b : village.getBorders()) {
            if (b.getId() != removeId) remaining.add(b);
        }
        if (remaining.isEmpty()) return java.util.Set.of(removeId);

        // default border is the first border (id=0 expected)
        com.example.village.model.VillageBorder root = remaining.get(0);
        for (var b : remaining) {
            if (b.getId() == 0) { root = b; break; }
        }

        java.util.Map<Integer, java.util.Set<Long>> edgeSets = new java.util.HashMap<>();
        for (var b : remaining) {
            java.util.Set<Long> s = new java.util.HashSet<>();
            for (int[] p : b.getEdgeBlocks()) s.add(packXZ(p[0], p[1]));
            edgeSets.put(b.getId(), s);
        }

        java.util.Set<Integer> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<Integer> dq = new java.util.ArrayDeque<>();
        dq.add(root.getId());
        visited.add(root.getId());

        while (!dq.isEmpty()) {
            int cur = dq.poll();
            for (var other : remaining) {
                int oid = other.getId();
                if (visited.contains(oid) || oid == cur) continue;
                if (areEdgeSetsAdjacent(edgeSets.get(cur), edgeSets.get(oid))) {
                    visited.add(oid);
                    dq.add(oid);
                }
            }
        }

        // Delete = removed border + all borders NOT in the connected component of the default border
        java.util.Set<Integer> delete = new java.util.HashSet<>();
        delete.add(removeId);
        for (var b : remaining) {
            if (!visited.contains(b.getId())) delete.add(b.getId());
        }
        return delete;
    }

    private boolean areEdgeSetsAdjacent(java.util.Set<Long> a, java.util.Set<Long> b) {
        if (a == null || b == null) return false;
        for (long key : a) {
            int x = (int) (key >> 32);
            int z = (int) key;
            if (b.contains(key)) return true;
            if (b.contains(packXZ(x + 1, z))) return true;
            if (b.contains(packXZ(x - 1, z))) return true;
            if (b.contains(packXZ(x, z + 1))) return true;
            if (b.contains(packXZ(x, z - 1))) return true;
        }
        return false;
    }

    private long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private void handleManageBorderFusion(Player player, Village village, String[] args) {
        if (args.length == 3) {
            borderService.startBorderFusionSelection(player.getUniqueId(), village);
            previewService.clearPreview(player);
            msg(player, "&7Klicke nacheinander 2 oder mehr Grenzflaechen an.");
            msg(player, "&7Nach jeder Auswahl kannst du &a[JA]&7/&c[NEIN]&7 fuer 'Fertig?' nutzen.");
            return;
        }
        if (args.length < 5) {
            msg(player, "&7Verwendung: /village manage border fusion <ID1> <ID2> [ID3 ...]");
            return;
        }
        java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
        for (int i = 3; i < args.length; i++) {
            try {
                ids.add(Integer.parseInt(args[i]));
            } catch (NumberFormatException e) {
                msg(player, "&cUngueltige ID: " + args[i]);
                return;
            }
        }
        if (ids.size() < 2) {
            msg(player, "&cBitte mindestens zwei unterschiedliche IDs angeben.");
            return;
        }
        java.util.List<Integer> ordered = new java.util.ArrayList<>(ids);
        ordered.sort(Integer::compareTo);
        fuseBorderIds(player, village, ordered);
    }

    private boolean fuseBorderIds(Player player, Village village, java.util.List<Integer> ids) {
        village.ensureBorderIds();
        java.util.List<com.example.village.model.VillageBorder> bordersToFuse = new java.util.ArrayList<>();
        for (Integer id : ids) {
            var border = village.getBorderById(id);
            if (border == null) {
                msg(player, "&cGrenzflaeche nicht gefunden: " + id);
                return false;
            }
            if (border.getId() == 0) {
                msg(player, "&cDie Startgrenze (ID 0) kann nicht vereint werden.");
                return false;
            }
            bordersToFuse.add(border);
        }

        java.util.Map<Integer, java.util.Set<Long>> edgeSets = new java.util.HashMap<>();
        for (var border : bordersToFuse) {
            java.util.Set<Long> edges = new java.util.HashSet<>();
            for (int[] p : border.getEdgeBlocks()) edges.add(packXZ(p[0], p[1]));
            edgeSets.put(border.getId(), edges);
        }
        java.util.Set<Integer> visited = new java.util.HashSet<>();
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        q.add(bordersToFuse.get(0).getId());
        visited.add(bordersToFuse.get(0).getId());
        while (!q.isEmpty()) {
            int current = q.poll();
            for (var border : bordersToFuse) {
                int otherId = border.getId();
                if (visited.contains(otherId) || otherId == current) continue;
                if (areEdgeSetsAdjacent(edgeSets.get(current), edgeSets.get(otherId))) {
                    visited.add(otherId);
                    q.add(otherId);
                }
            }
        }
        if (visited.size() != bordersToFuse.size()) {
            msg(player, "&cDie ausgewaehlten Grenzflaechen muessen zusammenhaengend sein.");
            return false;
        }

        int minY = bordersToFuse.get(0).getMinY();
        int maxY = bordersToFuse.get(0).getMaxY();
        boolean debug = configManager.isWalkBorderDebug();
        java.util.Set<java.util.UUID> owners = new java.util.HashSet<>();
        if (debug) Bukkit.getLogger().info("[Village][Fusion] Selected IDs: " + ids);
        for (var border : bordersToFuse) {
            minY = Math.min(minY, border.getMinY());
            maxY = Math.max(maxY, border.getMaxY());
            owners.addAll(border.getOwners());
            if (debug) {
                Bukkit.getLogger().info("[Village][Fusion] Border id=" + border.getId()
                        + " bounds=[" + border.getMinX() + "," + border.getMinZ() + "]- [" + border.getMaxX() + "," + border.getMaxZ() + "]"
                        + " edgeBlocks=" + border.getEdgeBlocks().size()
                        + " area=" + border.calculateArea());
            }
        }

        Geometry unionGeometry = BorderGeometryUtil.unionBorders(bordersToFuse);
        if (debug) {
            Bukkit.getLogger().info("[Village][Fusion] unionGeometry class=" + (unionGeometry == null ? "null" : unionGeometry.getClass().getSimpleName())
                    + " empty=" + (unionGeometry == null || unionGeometry.isEmpty())
                    + " numGeoms=" + (unionGeometry == null ? 0 : unionGeometry.getNumGeometries())
                    + " area=" + (unionGeometry == null ? 0.0 : unionGeometry.getArea())
                    + " bbox=" + (unionGeometry == null ? "null" : unionGeometry.getEnvelopeInternal()));
        }
        if (unionGeometry == null || unionGeometry.isEmpty()) {
            msg(player, "&cFusion fehlgeschlagen (Union-Operation fehlgeschlagen).");
            return false;
        }

        Polygon merged = BorderGeometryUtil.largestPolygon(unionGeometry);
        if (merged == null) {
            msg(player, "&cFusion fehlgeschlagen: Die ausgewählten Flächen sind nicht wirklich zusammenhängend.");
            return false;
        }

        if (unionGeometry.getNumGeometries() > 1) {
            if (debug) Bukkit.getLogger().info("[Village][Fusion] union produced multiple geometries; trying buffer(0) merge...");
            try {
                Geometry buf0 = unionGeometry.buffer(0);
                if (debug) Bukkit.getLogger().info("[Village][Fusion] after buffer(0): numGeoms=" + (buf0 == null ? 0 : buf0.getNumGeometries())
                        + " area=" + (buf0 == null ? 0.0 : buf0.getArea()));
                if (buf0 != null && !buf0.isEmpty() && buf0.getNumGeometries() == 1) {
                    merged = BorderGeometryUtil.largestPolygon(buf0);
                } else {
                    // Fallback: small positive buffer to bridge corner-touching polygons
                    double eps = 0.5;
                    if (debug) Bukkit.getLogger().info("[Village][Fusion] trying small positive buffer eps=" + eps);
                    Geometry bufPos = unionGeometry.buffer(eps);
                    if (debug) Bukkit.getLogger().info("[Village][Fusion] after buffer(" + eps + "): numGeoms=" + (bufPos == null ? 0 : bufPos.getNumGeometries()) + " area=" + (bufPos == null ? 0.0 : bufPos.getArea()));
                    if (bufPos == null || bufPos.isEmpty()) {
                        msg(player, "&cFusion fehlgeschlagen: Die ausgewählten Flächen sind nicht wirklich zusammenhängend.");
                        return false;
                    }
                    // pick largest component from buffered geometry, then try shrinking back a bit
                    Polygon cand = BorderGeometryUtil.largestPolygon(bufPos);
                    if (cand == null) {
                        msg(player, "&cFusion fehlgeschlagen: Die ausgewählten Flächen sind nicht wirklich zusammenhängend.");
                        return false;
                    }
                    try {
                        Geometry shrunk = cand.buffer(-eps + 0.001);
                        if (debug) Bukkit.getLogger().info("[Village][Fusion] after shrink: class=" + (shrunk == null ? "null" : shrunk.getClass().getSimpleName()) + " numGeoms=" + (shrunk == null ? 0 : shrunk.getNumGeometries()) + " area=" + (shrunk == null ? 0.0 : shrunk.getArea()));
                        Polygon finalPoly = BorderGeometryUtil.largestPolygon(shrunk == null ? cand : shrunk);
                        if (finalPoly != null) merged = finalPoly;
                    } catch (Exception sx) {
                        if (debug) Bukkit.getLogger().warning("[Village][Fusion] shrink failed: " + sx.getMessage());
                        merged = cand;
                    }
                }
                if (merged == null) {
                    msg(player, "&cFusion fehlgeschlagen: Die ausgewählten Flächen sind nicht wirklich zusammenhängend.");
                    return false;
                }
            } catch (Exception ex) {
                if (debug) Bukkit.getLogger().warning("[Village][Fusion] buffer attempts threw: " + ex.getMessage());
                msg(player, "&cFusion fehlgeschlagen: Die ausgewählten Flächen sind nicht wirklich zusammenhängend.");
                return false;
            }
        }

        // Prevent a fused region from absorbing remaining village borders.
        java.util.Set<Integer> removeIds = new java.util.HashSet<>(ids);
        java.util.List<com.example.village.model.VillageBorder> remainingBorders = new java.util.ArrayList<>();
        for (var border : village.getBorders()) {
            if (!removeIds.contains(border.getId())) {
                remainingBorders.add(border);
            }
        }
        Geometry remainingGeometry = BorderGeometryUtil.unionBorders(remainingBorders);
        if (remainingGeometry != null && !remainingGeometry.isEmpty()) {
            Geometry overlap = merged.intersection(remainingGeometry);
            if (overlap != null && overlap.getArea() > 0.0) {
                msg(player, "&cFusion fehlgeschlagen: Die neue Grenze überlappt benachbarte Bereiche.");
                return false;
            }
        }

        java.util.List<int[]> boundaryLoop = BorderGeometryUtil.extractBoundaryLoop(merged);
        if (boundaryLoop == null || boundaryLoop.size() < 3) {
            msg(player, "&cFusion fehlgeschlagen (Boundary nicht gefunden).");
            return false;
        }

        com.example.village.model.VillageBorder fused = new com.example.village.model.VillageBorder(boundaryLoop, minY, maxY);
        fused.setId(village.nextBorderId());
        fused.setOwners(owners);

        java.util.Set<Integer> removeIdsForDelete = new java.util.HashSet<>(ids);
        village.getBorders().removeIf(b -> removeIdsForDelete.contains(b.getId()));
        village.addBorder(fused);
        villageManager.saveVillage(village);
        msg(player, "&aFusion abgeschlossen. Neue Grenzflaeche ID: &e" + fused.getId());
        return true;
    }

    private java.util.Set<Long> raster(com.example.village.model.VillageBorder border) {
        java.util.Set<Long> set = new java.util.HashSet<>();
        for (int x = border.getMinX(); x <= border.getMaxX(); x++) {
            for (int z = border.getMinZ(); z <= border.getMaxZ(); z++) {
                if (border.contains(x, z) || border.isOnBorder(x, z)) {
                    set.add(packXZ(x, z));
                }
            }
        }
        return set;
    }

    private java.util.Set<Long> rasterUnion(com.example.village.model.VillageBorder a, com.example.village.model.VillageBorder b) {
        int minX = Math.min(a.getMinX(), b.getMinX());
        int maxX = Math.max(a.getMaxX(), b.getMaxX());
        int minZ = Math.min(a.getMinZ(), b.getMinZ());
        int maxZ = Math.max(a.getMaxZ(), b.getMaxZ());

        java.util.Set<Long> set = new java.util.HashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean inA = a.contains(x, z) || a.isOnBorder(x, z);
                boolean inB = b.contains(x, z) || b.isOnBorder(x, z);
                if (inA || inB) set.add(packXZ(x, z));
            }
        }
        return set;
    }

    private java.util.List<int[]> traceOuterBoundary(java.util.Set<Long> filled, int maxSteps) {
        if (filled == null || filled.isEmpty()) return null;

        // Find a starting boundary cell (min x then min z) that has an outside neighbor.
        long start = 0;
        boolean found = false;
        int startX = 0, startZ = 0;
        for (long k : filled) {
            int x = (int) (k >> 32);
            int z = (int) k;
            if (isBoundaryCell(filled, x, z)) {
                if (!found || x < startX || (x == startX && z < startZ)) {
                    start = k;
                    startX = x;
                    startZ = z;
                    found = true;
                }
            }
        }
        if (!found) return null;

        // Right-hand rule tracing on grid edges (Moore-neighborhood boundary tracing variant).
        int[][] dirs = new int[][]{{1,0},{0,1},{-1,0},{0,-1}};
        int dir = 0; // start heading east

        java.util.List<int[]> loop = new java.util.ArrayList<>();
        int cx = startX;
        int cz = startZ;
        loop.add(new int[]{cx, cz});

        int steps = 0;
        while (steps++ < maxSteps) {
            // Try to turn right as much as possible while staying on boundary.
            boolean moved = false;
            for (int i = 0; i < 4; i++) {
                int ndir = (dir + 3 + i) % 4; // right, straight, left, back
                int nx = cx + dirs[ndir][0];
                int nz = cz + dirs[ndir][1];
                if (filled.contains(packXZ(nx, nz)) && isBoundaryCell(filled, nx, nz)) {
                    cx = nx;
                    cz = nz;
                    dir = ndir;
                    loop.add(new int[]{cx, cz});
                    moved = true;
                    break;
                }
            }
            if (!moved) return null;
            if (cx == startX && cz == startZ && loop.size() > 4) {
                return loop;
            }
        }
        return null;
    }

    private Polygon buildPolygonForBorder(com.example.village.model.VillageBorder border) {
        GeometryFactory gf = new GeometryFactory();
        java.util.List<int[]> points = border.getBorderPoints();
        if (points.size() < 3) {
            return gf.createPolygon();
        }
        Coordinate[] coords = new Coordinate[points.size() + 1];
        for (int i = 0; i < points.size(); i++) {
            coords[i] = new Coordinate(points.get(i)[0], points.get(i)[1]);
        }
        coords[points.size()] = coords[0];
        LinearRing shell = gf.createLinearRing(coords);
        return gf.createPolygon(shell, null);
    }

    private java.util.List<int[]> extractBoundaryLoop(Polygon polygon) {
        if (polygon == null) return null;
        Coordinate[] ext = polygon.getExteriorRing().getCoordinates();
        if (ext.length < 4) return null;
        java.util.List<int[]> loop = new java.util.ArrayList<>();
        for (int i = 0; i < ext.length - 1; i++) {
            int vx = (int) Math.round(ext[i].x);
            int vz = (int) Math.round(ext[i].y);
            if (loop.isEmpty() || loop.get(loop.size() - 1)[0] != vx || loop.get(loop.size() - 1)[1] != vz) {
                loop.add(new int[]{vx, vz});
            }
        }
        return loop;
    }

    private boolean isBoundaryCell(java.util.Set<Long> filled, int x, int z) {
        if (!filled.contains(packXZ(x, z))) return false;
        return !filled.contains(packXZ(x + 1, z))
                || !filled.contains(packXZ(x - 1, z))
                || !filled.contains(packXZ(x, z + 1))
                || !filled.contains(packXZ(x, z - 1));
    }

    private void msg(CommandSender player, String message) {
        MessageUtil.send(player, configManager.getPrefix(), message);
    }

    private void sendClickableLine(Player player, String prefix, String label, String command, String hover) {
        TextComponent component = new TextComponent(color(configManager.getPrefix() + prefix));
        TextComponent action = new TextComponent(color(label));
        action.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        action.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(color(hover)).create()));
        player.spigot().sendMessage(component, action);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private void handleSchematic(Player player, String[] args) {
        if (!player.hasPermission("village.admin")) {
            msg(player, configManager.message("no-permission"));
            return;
        }

        if (args.length < 2) {
            msg(player, "&6=== Schematic Erstellung ===");
            msg(player, "&e/village schematic tool &7- Gibt dir das Auswahl-Werkzeug");
            msg(player, "&e/village schematic pos1 &7- Setzt Position 1 auf den anvisierten Block");
            msg(player, "&e/village schematic pos2 &7- Setzt Position 2 auf den anvisierten Block");
            msg(player, "&e/village schematic origin &7- Setzt den Ursprung auf deine aktuelle Position");
            msg(player, "&e/village schematic save <name> &7- Speichert die Auswahl");
            msg(player, "&e/village schematic register <name> [name:<display>] [category:<cat>] [perm:<perm>] [icon:<ICON>] [level:<n>] &7- Registriert die Schematic in config/buildings.yml");
            return;
        }

        String sub = args[1].toLowerCase();
        BuildingService.SchematicSelection selection = buildingService.getOrCreateSchematicSelection(player.getUniqueId());

        switch (sub) {
            case "tool":
                org.bukkit.inventory.ItemStack tool = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BLAZE_ROD);
                org.bukkit.inventory.meta.ItemMeta meta = tool.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§6Schematic Auswahl-Werkzeug");
                    java.util.List<String> lore = new java.util.ArrayList<>();
                    lore.add("§7Linksklick: §ePosition 1 setzen");
                    lore.add("§7Rechtsklick: §ePosition 2 setzen");
                    lore.add("§7Shift-Rechtsklick: §eUrsprung (Origin) setzen");
                    meta.setLore(lore);
                    tool.setItemMeta(meta);
                }
                player.getInventory().addItem(tool);
                player.sendMessage("§a[Schematic] Auswahl-Werkzeug erhalten. Verwende Linksklick und Rechtsklick zum Auswählen.");
                break;

            case "pos1":
                org.bukkit.block.Block targetBlock1 = player.getTargetBlockExact(5);
                if (targetBlock1 == null) {
                    msg(player, "&cBitte schaue auf einen Block in der Nähe.");
                    return;
                }
                selection.setPos1(targetBlock1.getLocation());
                msg(player, "&aPosition 1 gesetzt auf: &e" + targetBlock1.getX() + ", " + targetBlock1.getY() + ", " + targetBlock1.getZ());
                break;

            case "pos2":
                org.bukkit.block.Block targetBlock2 = player.getTargetBlockExact(5);
                if (targetBlock2 == null) {
                    msg(player, "&cBitte schaue auf einen Block in der Nähe.");
                    return;
                }
                selection.setPos2(targetBlock2.getLocation());
                msg(player, "&aPosition 2 gesetzt auf: &e" + targetBlock2.getX() + ", " + targetBlock2.getY() + ", " + targetBlock2.getZ());
                break;

            case "origin":
                selection.setOrigin(player.getLocation().getBlock().getLocation());
                msg(player, "&aUrsprung (Origin) auf deine aktuelle Position gesetzt: &e" + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ());
                break;

            case "save":
                if (args.length < 3) {
                    msg(player, "&cBitte gib einen Namen an: /village schematic save <name>");
                    return;
                }
                if (selection.getPos1() == null || selection.getPos2() == null) {
                    msg(player, "&cBitte setze zuerst Position 1 und Position 2.");
                    return;
                }
                if (selection.getOrigin() == null) {
                    selection.setOrigin(selection.getPos1());
                    msg(player, "&eKein Ursprung gesetzt. Verwende Position 1 als Ursprung.");
                }

                String baseName = args[2];
                if (baseName.endsWith(".schem")) baseName = baseName.substring(0, baseName.length() - 6);

                com.example.village.VillagePlugin plugin = com.example.village.VillagePlugin.getPlugin(com.example.village.VillagePlugin.class);
                java.io.File schemDir = new java.io.File(plugin.getDataFolder(), "schematics");
                if (!schemDir.exists()) schemDir.mkdirs();

                // Find next variant number for <baseName>_###.schem
                int max = 0;
                java.util.regex.Pattern p = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(baseName) + "_(\\d{3})\\.schem");
                for (java.io.File f : schemDir.listFiles(java.io.File::isFile)) {
                    java.util.regex.Matcher m = p.matcher(f.getName());
                    if (m.matches()) {
                        try { int n = Integer.parseInt(m.group(1)); if (n > max) max = n; } catch (Exception ignored) {}
                    }
                }
                int next = max + 1;
                String schemName = baseName + "_" + String.format(java.util.Locale.US, "%03d", next) + ".schem";
                java.io.File schemFile = new java.io.File(schemDir, schemName);
                boolean success = buildingService.getWorldEditHook().saveSchematic(
                        selection.getPos1(),
                        selection.getPos2(),
                        selection.getOrigin(),
                        schemFile
                );

                if (success) {
                    msg(player, "&aSchematic erfolgreich gespeichert: &e" + schemName);
                    buildingService.clearSchematicSelection(player.getUniqueId());
                    // Versuche eine Stub-Definition in config/buildings.yml anzulegen
                    try { addSchematicStubToConfig(schemName, player); }
                    catch (Exception e) {
                        plugin.getLogger().warning("[VillageCommand] Konnte Schematic-Stub nicht in config/buildings.yml eintragen: " + e.getMessage());
                        msg(player, "&eHinweis: Schematic gespeichert, aber konnte nicht automatisch in config/buildings.yml registrieren. Siehe Server-Log.");
                    }
                } else {
                    msg(player, "&cFehler beim Speichern der Schematic. Ist WorldEdit installiert?");
                }
                break;

            case "register":
                if (args.length < 3) {
                    msg(player, "&cBitte gib einen Schematic-Namen an: /village schematic register <name> [options]");
                    return;
                }
                String registerName = args[2];
                if (!registerName.endsWith(".schem")) {
                    registerName += ".schem";
                }

                com.example.village.VillagePlugin plugin2 = com.example.village.VillagePlugin.getPlugin(com.example.village.VillagePlugin.class);
                java.io.File registerFile = new java.io.File(plugin2.getDataFolder(), "schematics/" + registerName);
                if (!registerFile.exists()) {
                    msg(player, "&cSchematic-Datei nicht gefunden: &e" + registerName + " &c(in schematics/)");
                    return;
                }

                java.util.Map<String, Object> registerMeta = parseSchematicMeta(java.util.Arrays.copyOfRange(args, 3, args.length));
                addSchematicStubToConfig(registerName, player, registerMeta);
                break;

            case "list":
                if (args.length < 3) {
                    msg(player, "&cVerwendung: /village schematic list <building_type>");
                    return;
                }
                String typeKey = args[2];
                java.util.List<String> vars = buildingService.findSchematicVariations(typeKey);
                if (vars.isEmpty()) {
                    msg(player, "&eKeine Schematic-Varianten gefunden für: &f" + typeKey);
                    return;
                }
                msg(player, "&6Schematic-Varianten für: &e" + typeKey);
                TextComponent toolButton = new TextComponent(color("&a[Neues Schematic-Werkzeug]"));
                toolButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/village schematic tool"));
                toolButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        new ComponentBuilder(color("&7Klicke, um das Schematic-Werkzeug zu erhalten.")).create()));
                player.spigot().sendMessage(toolButton);
                for (String v : vars) {
                    msg(player, " - &e" + v);
                }
                break;

            default:
                msg(player, "&cUnbekannter Befehl. Verwende: /village schematic");
                break;
        }
    }

    private void addSchematicStubToConfig(String schemName, Player player) {
        try {
            com.example.village.VillagePlugin plugin = com.example.village.VillagePlugin.getPlugin(com.example.village.VillagePlugin.class);
            java.io.File cfgFile = new java.io.File(plugin.getDataFolder(), "config/buildings.yml");
            if (!cfgFile.exists()) {
                if (plugin.getResource("config/buildings.yml") != null) plugin.saveResource("config/buildings.yml", false);
                else cfgFile.getParentFile().mkdirs();
            }
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfgFile);

            if (cfg.getConfigurationSection("categories") == null) cfg.createSection("categories");
            if (cfg.getConfigurationSection("categories.admin_added") == null) {
                cfg.createSection("categories.admin_added");
                cfg.set("categories.admin_added.name", "Admin hinzugefügt");
                cfg.set("categories.admin_added.permission", "village.building.admin_added");
                cfg.set("categories.admin_added.icon", "CHEST");
            }

            String key = schemName.replaceAll("\\.schem$", "").toLowerCase().replaceAll("[^a-z0-9_\\-]","_");
            if (cfg.getConfigurationSection("categories.admin_added.buildings." + key) != null) {
                msg(player, "&eEinträge für diese Schematic existieren bereits in config/buildings.yml");
                cfg.save(cfgFile);
                return;
            }

            String base = "categories.admin_added.buildings." + key + ".";
            cfg.set(base + "name", key);
            cfg.set(base + "description", "Auto-generated schematic saved by admin: " + schemName);
            cfg.set(base + "permission", "village.building.admin_added." + key);
            cfg.set(base + "validation_mode", "schematic");
            cfg.set(base + "schematic", schemName);
            cfg.set(base + "icon", "CHEST");
            cfg.set(base + "requires_village_level", 1);

            cfg.save(cfgFile);
            msg(player, "&aSchematic wurde in config/buildings.yml als 'admin_added/" + key + "' registriert.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void addSchematicStubToConfig(String schemName, Player player, java.util.Map<String, Object> meta) {
        try {
            com.example.village.VillagePlugin plugin = com.example.village.VillagePlugin.getPlugin(com.example.village.VillagePlugin.class);
            java.io.File cfgFile = new java.io.File(plugin.getDataFolder(), "config/buildings.yml");
            if (!cfgFile.exists()) {
                if (plugin.getResource("config/buildings.yml") != null) plugin.saveResource("config/buildings.yml", false);
                else cfgFile.getParentFile().mkdirs();
            }
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(cfgFile);

            if (cfg.getConfigurationSection("categories") == null) cfg.createSection("categories");
            if (cfg.getConfigurationSection("categories.admin_added") == null) {
                cfg.createSection("categories.admin_added");
                cfg.set("categories.admin_added.name", "Admin hinzugefügt");
                cfg.set("categories.admin_added.permission", "village.building.admin_added");
                cfg.set("categories.admin_added.icon", "CHEST");
            }

            String key = schemName.replaceAll("\\.schem$", "").toLowerCase().replaceAll("[^a-z0-9_\\-]","_");
            if (cfg.getConfigurationSection("categories.admin_added.buildings." + key) != null) {
                msg(player, "&eEinträge für diese Schematic existieren bereits in config/buildings.yml");
                cfg.save(cfgFile);
                return;
            }

            String base = "categories.admin_added.buildings." + key + ".";
            // Defaults
            cfg.set(base + "name", meta.getOrDefault("name", key));
            cfg.set(base + "description", meta.getOrDefault("description", "Auto-generated schematic saved by admin: " + schemName));
            cfg.set(base + "permission", meta.getOrDefault("perm", meta.getOrDefault("permission", "village.building.admin_added." + key)));
            cfg.set(base + "validation_mode", "schematic");
            cfg.set(base + "schematic", schemName);
            cfg.set(base + "icon", meta.getOrDefault("icon", "CHEST"));
            cfg.set(base + "requires_village_level", Integer.parseInt(meta.getOrDefault("level", "1").toString()));

            // Optional category override
            String category = meta.getOrDefault("category", "admin_added").toString();
            if (!"admin_added".equals(category)) {
                // move node under custom category
                String targetBase = "categories." + category + ".buildings." + key + ".";
                cfg.set(targetBase + "name", cfg.get(base + "name"));
                cfg.set(targetBase + "description", cfg.get(base + "description"));
                cfg.set(targetBase + "permission", cfg.get(base + "permission"));
                cfg.set(targetBase + "validation_mode", cfg.get(base + "validation_mode"));
                cfg.set(targetBase + "schematic", cfg.get(base + "schematic"));
                cfg.set(targetBase + "icon", cfg.get(base + "icon"));
                cfg.set(targetBase + "requires_village_level", cfg.get(base + "requires_village_level"));
                // ensure category exists
                if (cfg.getConfigurationSection("categories." + category) == null) {
                    cfg.createSection("categories." + category);
                    cfg.set("categories." + category + ".permission", "village.building." + category);
                    cfg.set("categories." + category + ".name", category);
                }
            }

            cfg.save(cfgFile);
            msg(player, "&aSchematic wurde in config/buildings.yml als '" + categoryPath(meta) + "' registriert.");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.util.Map<String, Object> parseSchematicMeta(String[] args) {
        java.util.Map<String, Object> meta = new java.util.HashMap<>();
        for (String arg : args) {
            int idx = arg.indexOf(':');
            if (idx <= 0) continue;
            String key = arg.substring(0, idx).toLowerCase();
            String value = arg.substring(idx + 1);
            if (value.isBlank()) continue;
            switch (key) {
                case "name", "category", "perm", "permission", "icon", "description", "level" -> meta.put(key, value);
                default -> meta.put(key, value);
            }
        }
        return meta;
    }

    private String categoryPath(java.util.Map<String, Object> meta) {
        String category = meta.getOrDefault("category", "admin_added").toString();
        String key = meta.getOrDefault("name", "").toString();
        if (key.isBlank()) key = "<name>";
        return category + "/" + key;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "info", "list", "join", "joinrequest", "leave", "invite", "kick",
                    "promote", "border", "preview", "building", "delete", "rename", "revive", "menu", "help", "manage",
                    "sendmoney", "sm", "balance", "balances", "path", "abort"
            ));
            if (sender.hasPermission("village.admin")) {
                subs.add("admin");
                subs.add("reload");
                subs.add("schematic");
                subs.add("schematics");
                subs.add("schem");
                subs.add("tp");
            }
            String prefix = args[0].toLowerCase();
            for (String s : subs) {
                if (s.startsWith(prefix)) completions.add(s);
            }
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();

            switch (sub) {
                case "info":
                case "join":
                    for (Village v : villageManager.getAllVillages()) {
                        if (v.getName().toLowerCase().startsWith(prefix)) {
                            completions.add(v.getName());
                        }
                    }
                    break;
                case "invite":
                case "kick":
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(prefix)) {
                            completions.add(p.getName());
                        }
                    }
                    break;
                case "joinrequest":
                    for (String s : Arrays.asList("accept", "deny")) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "promote":
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(prefix)) {
                            completions.add(p.getName());
                        }
                    }
                    break;
                case "admin":
                    for (String s : Arrays.asList("setlevel", "addpoints", "delete", "saveall", "resyncwg", "money", "bordernames", "joinrequests", "menu", "light")) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "sendmoney":
                case "sm":
                    Optional<Village> ownVillage = villageManager.getPlayerVillage(((Player) sender).getUniqueId());
                    if (ownVillage.isPresent()) {
                        for (UUID memberId : ownVillage.get().getMembers().keySet()) {
                            String n = Bukkit.getOfflinePlayer(memberId).getName();
                            if (n != null && n.toLowerCase().startsWith(prefix)) completions.add(n);
                        }
                    }
                    break;
                case "building":
                    for (String s : Arrays.asList("list", "remove", "movesign", "upgrade", "start", "cancel")) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "manage":
                    for (String s : Arrays.asList("border", "grenze")) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "border":
                    if ("manage".startsWith(prefix)) completions.add("manage");
                    break;
                case "schematic":
                case "schematics":
                case "schem":
                    for (String s : Arrays.asList("tool", "pos1", "pos2", "origin", "save", "register", "list")) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "path":
                    for (String s : Arrays.asList("done", "cancel")) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                    break;
                case "tp":
                    for (Village v : villageManager.getAllVillages()) {
                        if (v.getName().toLowerCase().startsWith(prefix)) {
                            completions.add(v.getName());
                        }
                    }
                    break;
                case "spawn":
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.getName().toLowerCase().startsWith(prefix)) {
                            completions.add(p.getName());
                        }
                    }
                    if ("self".startsWith(prefix)) {
                        completions.add("self");
                    }
                    break;
                case "revive":
                    break;
            }
        } else if (args.length == 3) {
            String sub = args[0].toLowerCase();
            String cmdSub = args[1].toLowerCase();
            String prefix = args[2].toLowerCase();

            if ("promote".equals(sub)) {
                for (String r : Arrays.asList("MEMBER", "HR", "BAUMEISTER", "BUILDER", "HAENDLER", "TRAINER")) {
                    if (r.toLowerCase().startsWith(prefix)) completions.add(r);
                }
            } else if ("admin".equals(sub)) {
                if ("setlevel".equals(cmdSub) || "addpoints".equals(cmdSub) || "delete".equals(cmdSub)
                        || "joinrequests".equals(cmdSub) || "menu".equals(cmdSub)) {
                    for (Village v : villageManager.getAllVillages()) {
                        if (v.getName().toLowerCase().startsWith(prefix)) {
                            completions.add(v.getName());
                        }
                    }
                } else if ("money".equals(cmdSub)) {
                    for (String s : Arrays.asList("add", "remove")) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                } else if ("bordernames".equals(cmdSub)) {
                    for (String s : Arrays.asList("true", "false")) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                } else if ("light".equals(cmdSub)) {
                    for (String s : Arrays.asList("addpoi", "addregion", "addpath")) {
                        if (s.startsWith(prefix)) completions.add(s);
                    }
                }
            } else if ("tp".equals(sub)) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(prefix)) {
                        completions.add(p.getName());
                    }
                }
                if ("self".startsWith(prefix)) {
                    completions.add("self");
                }
            } else if ("building".equals(sub)) {
                if ("remove".equals(cmdSub) || "movesign".equals(cmdSub) || "upgrade".equals(cmdSub) || "start".equals(cmdSub) || "cancel".equals(cmdSub)) {
                    Optional<Village> playerVillage = villageManager.getPlayerVillage(((Player) sender).getUniqueId());
                    if (playerVillage.isPresent()) {
                        List<VillageBuilding> buildings = playerVillage.get().getBuildings();
                        for (int i = 0; i < buildings.size(); i++) {
                            String alias = (i + 1) + ": " + buildings.get(i).getTypeKey();
                            if (alias.startsWith(prefix)) {
                                completions.add(String.valueOf(i + 1));
                            }
                        }
                    }
                }
            } else if ("manage".equals(sub) && ("border".equals(cmdSub) || "grenze".equals(cmdSub))) {
                for (String s : Arrays.asList("info", "delete", "fusion")) {
                    if (s.startsWith(prefix)) completions.add(s);
                }
            } else if ("border".equals(sub) && "manage".equals(cmdSub)) {
                for (String s : Arrays.asList("info", "delete", "fusion")) {
                    if (s.startsWith(prefix)) completions.add(s);
                }
            } else if (("schematic".equals(sub) || "schem".equals(sub) || "schematics".equals(sub)) && "list".equals(cmdSub)) {
                // For /village schematic list <building_type>
                Set<String> buildingTypes = new HashSet<>();
                // Prefer configured building definitions from BuildingConfigLoader
                BuildingConfigLoader loader = plugin.getBuildingConfigLoader();
                if (loader != null) {
                    for (BuildingDefinition def : loader.getAll()) {
                        buildingTypes.add(def.getId());
                    }
                } else {
                    for (Village v : villageManager.getAllVillages()) {
                        for (VillageBuilding b : v.getBuildings()) {
                            buildingTypes.add(b.getTypeKey());
                        }
                    }
                }
                for (String type : buildingTypes) {
                    if (type.toLowerCase().startsWith(prefix)) completions.add(type);
                }
            }
        } else if (args.length == 4) {
            String sub = args[0].toLowerCase();
            String arg1 = args[1].toLowerCase();
            String arg2 = args[2].toLowerCase();
            String prefix = args[3].toLowerCase();
            if ("admin".equals(sub) && "money".equals(arg1)) {
                completions.add("all");
                for (Player online : Bukkit.getOnlinePlayers()) completions.add(online.getName());
                for (Village v : villageManager.getAllVillages()) completions.add("village:" + v.getName());
                return completions.stream().filter(s -> s.toLowerCase().startsWith(prefix)).toList();
            }
            boolean manageBorder = ("manage".equals(sub) && ("border".equals(arg1) || "grenze".equals(arg1)))
                    || ("border".equals(sub) && "manage".equals(arg1));
            if (manageBorder && ("delete".equals(arg2) || "fusion".equals(arg2)) && sender instanceof Player p) {
                Optional<Village> villageOpt = villageManager.getPlayerVillage(p.getUniqueId());
                if (villageOpt.isPresent()) {
                    villageOpt.get().ensureBorderIds();
                    for (var b : villageOpt.get().getBorders()) {
                        String id = String.valueOf(b.getId());
                        if (id.startsWith(prefix)) {
                            completions.add(id);
                        }
                    }
                }
            }
        } else if (args.length == 5) {
            String sub = args[0].toLowerCase();
            String arg1 = args[1].toLowerCase();
            String prefix = args[4].toLowerCase();
            if ("admin".equals(sub) && "money".equals(arg1)) {
                completions.add("global");
                for (Village v : villageManager.getAllVillages()) completions.add(v.getName());
                return completions.stream().filter(s -> s.toLowerCase().startsWith(prefix)).toList();
            }
        }

        return completions;
    }
}
