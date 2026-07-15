package com.example.village.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import com.example.village.model.Village;
import com.example.village.model.VillageMember;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.Set;
import java.util.UUID;

/**
 * Utility class for sending formatted messages to players.
 */
public final class MessageUtil {

    private static Function<String, String> textResolver = key -> null;

    private MessageUtil() {
    }

    public static void setTextResolver(Function<String, String> resolver) {
        textResolver = resolver != null ? resolver : key -> null;
    }

    public static String text(String key, String fallback) {
        if (key == null) {
            return fallback != null ? fallback : "";
        }
        String resolved = textResolver != null ? textResolver.apply(key) : null;
        if (resolved == null || resolved.equals(key) || resolved.isBlank()) {
            return fallback != null ? fallback : key;
        }
        return resolved;
    }

    public static List<String> texts(List<String> keys, List<String> fallback) {
        if (keys == null) {
            return fallback != null ? fallback : List.of();
        }
        return keys;
    }

    /**
     * Sends a formatted message to a player or console.
     *
     * @param player the command sender to send the message to
     * @param prefix the message prefix
     * @param message the message content
     */
    public static void send(CommandSender player, String prefix, String message) {
        if (player == null) return;
        String formattedPrefix = prefix != null ? translateColorCodes(prefix) : "";
        String formattedMessage = (!formattedPrefix.isEmpty() ? formattedPrefix + " " : "") + translateColorCodes(message);
        player.sendMessage(formattedMessage);
    }

    /**
     * Sends a message to a player or console (without prefix).
     *
     * @param player the command sender to send the message to
     * @param message the message content
     */
    public static void send(CommandSender player, String message) {
        if (player == null) return;
        player.sendMessage(translateColorCodes(message));
    }

    /**
     * Translates color codes from & format to § format and MiniMessage format.
     *
     * @param message the message to translate
     * @return the translated message
     */
    public static String translateColorCodes(String message) {
        if (message == null) return "";
        return message.replace("&", "§");
    }

    /**
     * Converts a string with & color codes to Adventure Component.
     *
     * @param message the message to convert
     * @return the Component
     */
    public static Component color(String message) {
        if (message == null) return Component.text("");
        String translated = translateColorCodes(message);
        return LegacyComponentSerializer.legacySection().deserialize(translated);
    }

    public static void sendYesNoRunCommand(Player player,
                                          String prefix,
                                          String question,
                                          String yesCommand,
                                          String noCommand) {
        if (player == null) return;

        Component prefixComp = prefix != null ? color(prefix + " ") : Component.empty();
        Component questionComp = question != null ? color(question) : Component.empty();

        TextComponent yes = Component.text("[JA]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-yes", "Klicke: JA")).color(NamedTextColor.GREEN)))
                .clickEvent(ClickEvent.runCommand(yesCommand));

        TextComponent no = Component.text("[NEIN]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-no", "Klicke: NEIN")).color(NamedTextColor.RED)))
                .clickEvent(ClickEvent.runCommand(noCommand));

        Component msg = prefixComp.append(questionComp)
                .append(Component.space())
                .append(yes)
                .append(Component.space())
                .append(no);

        player.sendMessage(msg);
    }

    public static void sendClickableCommand(Player player,
                                            String prefix,
                                            String message,
                                            String command) {
        if (player == null) return;

        Component prefixComp = prefix != null ? color(prefix + " ") : Component.empty();
        Component messageComp = message != null ? color(message) : Component.empty();
        TextComponent action = Component.text("[ABBRECHEN]")
                .color(NamedTextColor.GRAY)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-cancel", "Klicke zum Abbrechen")).color(NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(command));

        player.sendMessage(prefixComp.append(messageComp).append(Component.space()).append(action));
    }

    public static void sendYesNoAbortRunCommand(Player player,
                                                String prefix,
                                                String question,
                                                String yesCommand,
                                                String noCommand,
                                                String abortCommand) {
        if (player == null) return;

        Component prefixComp = prefix != null ? color(prefix + " ") : Component.empty();
        Component questionComp = question != null ? color(question) : Component.empty();

        TextComponent yes = Component.text("[JA]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-yes", "Klicke: JA")).color(NamedTextColor.GREEN)))
                .clickEvent(ClickEvent.runCommand(yesCommand));

        TextComponent no = Component.text("[NEIN]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-no", "Klicke: NEIN")).color(NamedTextColor.RED)))
                .clickEvent(ClickEvent.runCommand(noCommand));

        TextComponent abort = Component.text("[ABBRECHEN]")
                .color(NamedTextColor.GRAY)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-abort", "Klicke: Abbrechen")).color(NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.runCommand(abortCommand));

        Component msg = prefixComp.append(questionComp)
                .append(Component.space())
                .append(yes)
                .append(Component.space())
                .append(no)
                .append(Component.space())
                .append(abort);

        player.sendMessage(msg);
    }

    public static void sendYesNoSuggest(Player player,
                                       String prefix,
                                       String question,
                                       String yesText,
                                       String noText) {
        if (player == null) return;

        Component prefixComp = prefix != null ? color(prefix + " ") : Component.empty();
        Component questionComp = question != null ? color(question) : Component.empty();

        TextComponent yes = Component.text("[JA]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-suggest-yes", "Klicke um 'ja' vorzuschlagen")).color(NamedTextColor.GREEN)))
                .clickEvent(ClickEvent.suggestCommand(yesText));

        TextComponent no = Component.text("[NEIN]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-suggest-no", "Klicke um 'nein' vorzuschlagen")).color(NamedTextColor.RED)))
                .clickEvent(ClickEvent.suggestCommand(noText));

        Component msg = prefixComp.append(questionComp)
                .append(Component.space())
                .append(yes)
                .append(Component.space())
                .append(no);

        player.sendMessage(msg);
    }

    /**
     * Notifies founders and elders (member management) about a join request with clickable accept/deny.
     */
    public static void notifyJoinRequestReviewers(String prefix, Village village, Player requester) {
        if (village == null || requester == null) return;
        UUID requesterId = requester.getUniqueId();
        String requesterName = requester.getName() != null ? requester.getName() : requesterId.toString();
        UUID villageId = village.getId();
        String villageName = village.getName();
        Set<UUID> seen = new HashSet<>();
        for (VillageMember m : village.getMembers().values()) {
            if (!m.canManageMembers()) continue;
            Player p = Bukkit.getPlayer(m.getPlayerId());
            if (p == null || !p.isOnline()) continue;
            if (!seen.add(p.getUniqueId())) continue;
            sendJoinRequestReviewMessage(p, prefix, requesterName, villageName, villageId, requesterId);
        }
    }

    private static void sendJoinRequestReviewMessage(Player reviewer, String prefix,
                                                     String requesterName, String villageName,
                                                     UUID villageId, UUID requesterId) {
        Component prefixComp = prefix != null ? color(prefix + " ") : Component.empty();
        Component line = color("&e" + requesterName + " &7möchte dem Dorf &f" + villageName + " &7beitreiten. ");

        String cmdBase = "/village joinrequest ";
        String acceptCmd = cmdBase + "accept " + villageId + " " + requesterId;
        String denyCmd = cmdBase + "deny " + villageId + " " + requesterId;

        TextComponent accept = Component.text("[ANNEHMEN]")
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-accept-request", "Anfrage annehmen")).color(NamedTextColor.GREEN)))
                .clickEvent(ClickEvent.runCommand(acceptCmd));

        TextComponent deny = Component.text("[ABLEHNEN]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-deny-request", "Anfrage ablehnen")).color(NamedTextColor.RED)))
                .clickEvent(ClickEvent.runCommand(denyCmd));

        reviewer.sendMessage(prefixComp.append(line).append(accept).append(Component.space()).append(deny));
    }

    public static void sendClickToCopy(Player player, String prefix, String label, String copyText) {
        if (player == null) return;
        Component prefixComp = prefix != null ? color(prefix + " ") : Component.empty();
        Component labelComp = label != null ? color(label + " ") : Component.empty();
        TextComponent clickable = Component.text(copyText)
                .color(NamedTextColor.YELLOW)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-copy", "Klicken zum Kopieren")).color(NamedTextColor.GOLD)))
                .clickEvent(ClickEvent.copyToClipboard(copyText));
        player.sendMessage(prefixComp.append(labelComp).append(clickable));
    }

    public static void sendRunCommand(Player player, String prefix, String question, String buttonLabel, String command) {
        if (player == null) return;
        Component prefixComp = prefix != null ? color(prefix + " ") : Component.empty();
        Component questionComp = question != null ? color(question + " ") : Component.empty();
        TextComponent button = Component.text(buttonLabel != null ? buttonLabel : "[KLICK]")
                .color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-execute", "Klicke ausfuehren")).color(NamedTextColor.RED)))
                .clickEvent(ClickEvent.runCommand(command));
        player.sendMessage(prefixComp.append(questionComp).append(button));
    }

    public static void sendTwoRunCommands(Player player, String prefix, String question,
                                          String label1, String cmd1,
                                          String label2, String cmd2) {
        if (player == null) return;
        Component prefixComp = prefix != null ? color(prefix + " ") : Component.empty();
        Component questionComp = question != null ? color(question + " ") : Component.empty();

        TextComponent b1 = Component.text(label1 != null ? label1 : "[1]")
                .color(NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-click", "Klicke")).color(NamedTextColor.YELLOW)))
                .clickEvent(ClickEvent.runCommand(cmd1));

        TextComponent b2 = Component.text(label2 != null ? label2 : "[2]")
                .color(NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-click", "Klicke")).color(NamedTextColor.AQUA)))
                .clickEvent(ClickEvent.runCommand(cmd2));

        Component msg = prefixComp.append(questionComp)
                .append(b1).append(Component.space()).append(b2);
        player.sendMessage(msg);
    }

    public static void sendSuggestCommand(Player player, String prefix, String label, String suggestedText) {
        if (player == null) return;
        Component prefixComp = prefix != null ? color(prefix + " ") : Component.empty();
        Component labelComp = label != null ? color(label + " ") : Component.empty();
        TextComponent clickable = Component.text(suggestedText)
                .color(NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text(text("messages.hover-suggest", "Klicken zum Einfuegen in Chat")).color(NamedTextColor.AQUA)))
                .clickEvent(ClickEvent.suggestCommand(suggestedText));
        player.sendMessage(prefixComp.append(labelComp).append(clickable));
    }
}
