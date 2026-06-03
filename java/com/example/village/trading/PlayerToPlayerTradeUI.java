package com.example.village.trading;

import com.example.village.currency.GlobalCurrencyManager;
import com.example.village.model.TradeOffer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerToPlayerTradeUI implements TradingUI {

    public static final int OWN_START = 0;
    public static final int OWN_END = 17;
    public static final int ACCEPT_START = 18;
    public static final int ACCEPT_END = 26;
    public static final int CANCEL_START = 27;
    public static final int CANCEL_END = 35;
    public static final int OTHER_START = 36;
    public static final int OTHER_END = 53;

    private final GlobalCurrencyManager globalCurrencyManager;
    private final Logger logger;
    private final Path tradeLogPath;

    private final Map<UUID, Inventory> tradeInventories = new HashMap<>();
    private final Map<UUID, Double> offeredCurrency = new HashMap<>();

    private Player initiator;
    private Player target;
    private boolean initiatorAccepted;
    private boolean targetAccepted;

    public PlayerToPlayerTradeUI(GlobalCurrencyManager globalCurrencyManager, Logger logger, File dataFolder) {
        this.globalCurrencyManager = Objects.requireNonNull(globalCurrencyManager);
        this.logger = Objects.requireNonNull(logger);
        File logDir = new File(dataFolder, "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        this.tradeLogPath = new File(logDir, "playertrades.log").toPath();
    }

    public void startTrade(Player initiator, Player target) {
        this.initiator = Objects.requireNonNull(initiator);
        this.target = Objects.requireNonNull(target);
        this.initiatorAccepted = false;
        this.targetAccepted = false;
        tradeInventories.put(initiator.getUniqueId(), Bukkit.createInventory(null, 18));
        tradeInventories.put(target.getUniqueId(), Bukkit.createInventory(null, 18));
        openTradingUI(initiator);
        openTradingUI(target);
    }

    @Override
    public void openTradingUI(Player player) {
        Player other = getOther(player);
        if (other == null) {
            return;
        }
        Inventory view = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Handel mit " + other.getName());

        Inventory ownTrade = getTradeInventory(player);
        Inventory otherTrade = getTradeInventory(other);

        for (int i = 0; i < 18; i++) {
            ItemStack own = ownTrade.getItem(i);
            view.setItem(OWN_START + i, own == null ? placeholder(ChatColor.DARK_GRAY + "Eigener Slot") : decorateOwn(own));
        }

        for (int slot = ACCEPT_START; slot <= ACCEPT_END; slot++) {
            view.setItem(slot, action(Material.LIME_STAINED_GLASS_PANE, ChatColor.GREEN + "Handel annehmen", ChatColor.GRAY + "Klick zum Akzeptieren"));
        }
        for (int slot = CANCEL_START; slot <= CANCEL_END; slot++) {
            view.setItem(slot, action(Material.RED_STAINED_GLASS_PANE, ChatColor.RED + "Handel ablehnen", ChatColor.GRAY + "Klick zum Abbrechen"));
        }

        for (int i = 0; i < 18; i++) {
            ItemStack theirs = otherTrade.getItem(i);
            view.setItem(OTHER_START + i, theirs == null ? placeholder(ChatColor.DARK_GRAY + "Gegenüber-Slot") : decorateOther(theirs, other.getName()));
        }

        player.openInventory(view);
    }

    public void refreshBothViews() {
        if (initiator != null && initiator.isOnline()) {
            openTradingUI(initiator);
        }
        if (target != null && target.isOnline()) {
            openTradingUI(target);
        }
    }

    public boolean addItemToOffer(Player player, int playerInventorySlot, boolean fullStack) {
        if (player == null) {
            return false;
        }
        ItemStack clicked = player.getInventory().getItem(playerInventorySlot);
        if (clicked == null || clicked.getType().isAir()) {
            return false;
        }

        int amount = fullStack ? clicked.getAmount() : 1;
        ItemStack moved = clicked.clone();
        moved.setAmount(amount);

        Map<Integer, ItemStack> overflow = getTradeInventory(player).addItem(moved);
        if (!overflow.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Dein Handelsinventar ist voll.");
            return false;
        }

        clicked.setAmount(clicked.getAmount() - amount);
        if (clicked.getAmount() <= 0) {
            player.getInventory().setItem(playerInventorySlot, null);
        } else {
            player.getInventory().setItem(playerInventorySlot, clicked);
        }

        resetAccepted();
        return true;
    }

    public boolean removeItemFromOfferBySlot(Player player, int uiSlot, boolean fullStack) {
        if (player == null || uiSlot < OWN_START || uiSlot > OWN_END) {
            return false;
        }
        int tradeSlot = uiSlot - OWN_START;
        Inventory tradeInv = getTradeInventory(player);
        ItemStack stack = tradeInv.getItem(tradeSlot);
        if (stack == null || stack.getType().isAir()) {
            return false;
        }

        int amount = fullStack ? stack.getAmount() : 1;
        ItemStack returned = stack.clone();
        returned.setAmount(amount);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(returned);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        stack.setAmount(stack.getAmount() - amount);
        if (stack.getAmount() <= 0) {
            tradeInv.setItem(tradeSlot, null);
        } else {
            tradeInv.setItem(tradeSlot, stack);
        }

        resetAccepted();
        return true;
    }

    private void resetAccepted() {
        initiatorAccepted = false;
        targetAccepted = false;
    }

    public boolean acceptTrade(Player player) {
        if (player == initiator) {
            initiatorAccepted = true;
        } else if (player == target) {
            targetAccepted = true;
        } else {
            return false;
        }
        if (initiatorAccepted && targetAccepted) {
            return executeTrade();
        }
        return false;
    }

    private boolean executeTrade() {
        try {
            if (!validateTrade()) {
                rollbackTrade();
                return false;
            }

            Inventory initTrade = getTradeInventory(initiator);
            Inventory targetTrade = getTradeInventory(target);

            List<ItemStack> initItems = collectItems(initTrade);
            List<ItemStack> targetItems = collectItems(targetTrade);

            for (ItemStack item : initItems) {
                Map<Integer, ItemStack> overflow = target.getInventory().addItem(item.clone());
                overflow.values().forEach(v -> target.getWorld().dropItemNaturally(target.getLocation(), v));
            }
            for (ItemStack item : targetItems) {
                Map<Integer, ItemStack> overflow = initiator.getInventory().addItem(item.clone());
                overflow.values().forEach(v -> initiator.getWorld().dropItemNaturally(initiator.getLocation(), v));
            }

            clearInventory(initTrade);
            clearInventory(targetTrade);

            double initCurrency = offeredCurrency.getOrDefault(initiator.getUniqueId(), 0.0);
            double targetCurrency = offeredCurrency.getOrDefault(target.getUniqueId(), 0.0);
            if (initCurrency > 0) {
                globalCurrencyManager.transfer(initiator.getUniqueId(), target.getUniqueId(), initCurrency);
            }
            if (targetCurrency > 0) {
                globalCurrencyManager.transfer(target.getUniqueId(), initiator.getUniqueId(), targetCurrency);
            }

            initiator.sendMessage(ChatColor.GREEN + "Trade erfolgreich abgeschlossen!");
            target.sendMessage(ChatColor.GREEN + "Trade erfolgreich abgeschlossen!");
            closeTradeInventories();
            logTrade(initiator.getName(), target.getName(), initItems, targetItems);
            return true;
        } catch (Exception e) {
            rollbackTrade();
            logger.severe("Trade execution failed: " + e.getMessage());
            return false;
        }
    }

    private boolean validateTrade() {
        if (initiator == null || target == null || !initiator.isOnline() || !target.isOnline()) {
            return false;
        }
        double initBal = globalCurrencyManager.getBalance(initiator.getUniqueId());
        double targetBal = globalCurrencyManager.getBalance(target.getUniqueId());
        return initBal >= offeredCurrency.getOrDefault(initiator.getUniqueId(), 0.0)
                && targetBal >= offeredCurrency.getOrDefault(target.getUniqueId(), 0.0);
    }

    private void rollbackTrade() {
        returnRemainingItems();
        if (initiator != null && initiator.isOnline()) {
            initiator.sendMessage(ChatColor.RED + "Trade konnte nicht durchgeführt werden.");
        }
        if (target != null && target.isOnline()) {
            target.sendMessage(ChatColor.RED + "Trade konnte nicht durchgeführt werden.");
        }
        closeTradeInventories();
    }

    public boolean cancelTrade(Player player) {
        returnRemainingItems();
        Player other = getOther(player);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.YELLOW + "Trade abgebrochen.");
        }
        if (other != null && other.isOnline()) {
            other.sendMessage(ChatColor.RED + "Die andere Seite hat den Trade abgebrochen.");
        }
        closeTradeInventories();
        return true;
    }

    private void closeTradeInventories() {
        if (initiator != null && initiator.isOnline()) {
            initiator.closeInventory();
        }
        if (target != null && target.isOnline()) {
            target.closeInventory();
        }
    }

    private void returnRemainingItems() {
        returnInventoryToOwner(initiator, getTradeInventory(initiator));
        returnInventoryToOwner(target, getTradeInventory(target));
        clearInventory(getTradeInventory(initiator));
        clearInventory(getTradeInventory(target));
    }

    private void returnInventoryToOwner(Player owner, Inventory tradeInv) {
        if (owner == null || tradeInv == null) {
            return;
        }
        for (ItemStack stack : tradeInv.getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> overflow = owner.getInventory().addItem(stack.clone());
            overflow.values().forEach(v -> owner.getWorld().dropItemNaturally(owner.getLocation(), v));
        }
    }

    private List<ItemStack> collectItems(Inventory inventory) {
        List<ItemStack> out = new ArrayList<>();
        if (inventory == null) {
            return out;
        }
        for (ItemStack s : inventory.getContents()) {
            if (s != null && !s.getType().isAir()) {
                out.add(s.clone());
            }
        }
        return out;
    }

    private void clearInventory(Inventory inventory) {
        if (inventory != null) {
            inventory.clear();
        }
    }

    private Inventory getTradeInventory(Player player) {
        if (player == null) {
            return Bukkit.createInventory(null, 18);
        }
        return tradeInventories.computeIfAbsent(player.getUniqueId(), k -> Bukkit.createInventory(null, 18));
    }

    public Player getOther(Player player) {
        if (player == null) {
            return null;
        }
        if (player.equals(initiator)) {
            return target;
        }
        if (player.equals(target)) {
            return initiator;
        }
        return null;
    }

    public void clearSession() {
        tradeInventories.clear();
        offeredCurrency.clear();
        initiatorAccepted = false;
        targetAccepted = false;
        initiator = null;
        target = null;
    }

    private ItemStack placeholder(String title) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack action(Material material, String title, String lore) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            meta.setLore(List.of(lore));
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack decorateOwn(ItemStack source) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ChatColor.GRAY + "Linksklick: -1");
            lore.add(ChatColor.GRAY + "Rechtsklick: ganzen Stack entfernen");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack decorateOther(ItemStack source, String otherName) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ChatColor.AQUA + "Angebot von " + otherName);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void logTrade(String a, String b, List<ItemStack> aItems, List<ItemStack> bItems) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String line = ts + " | " + a + " -> " + b + " : " + formatItems(aItems)
                + " || " + b + " -> " + a + " : " + formatItems(bItems)
                + System.lineSeparator();
        try {
            Files.writeString(tradeLogPath, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.warning("Could not write player trade log: " + e.getMessage());
        }
    }

    private String formatItems(List<ItemStack> items) {
        if (items.isEmpty()) {
            return "none";
        }
        List<String> out = new ArrayList<>();
        for (ItemStack i : items) {
            out.add(i.getType().name() + " x" + i.getAmount());
        }
        return String.join(", ", out);
    }

    @Override
    public TradeResult processTrade(Player buyer, TradeOffer offer, int quantity) {
        TradeResult result = new TradeResult();
        result.setSuccess(false);
        result.addError("Bitte nutze das P2P-Tradefenster");
        return result;
    }

    @Override
    public Optional<TradeOffer> getOfferForSlot(int slot) {
        return Optional.empty();
    }
}
