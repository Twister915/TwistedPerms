package me.twister915.perms.cmd;

import org.bukkit.command.CommandSender;
import tech.rayline.core.command.CommandException;
import tech.rayline.core.command.CommandMeta;
import tech.rayline.core.command.RDCommand;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.bukkit.ChatColor.translateAlternateColorCodes;

public final class PermsHelpCommand extends RDCommand {
    private final BasePermsCommand parent;

    public PermsHelpCommand(BasePermsCommand parent) {
        super("help");
        this.parent = parent;
    }

    protected Stream<BasePermsCommand> getApplicableSubCommands(CommandSender sender, BasePermsCommand command) {
        return command.getSubCommands().stream()
                .filter(c -> !(c instanceof PermsHelpCommand) && (c instanceof BasePermsCommand))
                .map(c -> (BasePermsCommand)c)
                .filter(c -> c.hasPermission(sender));
    }

    @Override protected void handleCommandUnspecific(CommandSender sender, String[] args) throws CommandException {
        List<BasePermsCommand> subCommands = getApplicableSubCommands(sender, parent).collect(Collectors.toList());
        sender.sendMessage(translateAlternateColorCodes('&', "&7|"));
        sender.sendMessage(getLine(parent, subCommands.size(), 0));
        for (BasePermsCommand subCommand : subCommands)
            sender.sendMessage(getLine(subCommand, (int) getApplicableSubCommands(sender, subCommand).count(), 1));
    }

    private String getLine(RDCommand command, int subs, int indent) {
        CommandMeta meta = command.getMeta();
        return getLine(command.getName(), meta.description(), meta.usage(), subs, indent);
    }

    private static String[] CHAT_COLORS_INDENT = new String[]{"a", "3"};

    private String getLine(String command, String description, String usage, int subCommands, int indent) {
        String indentSpaces;
        {
            char[] spaces = new char[indent * 2];
            Arrays.fill(spaces, ' ');
            indentSpaces = new String(spaces);
        }

        //this is static because it's complicated

        StringBuilder message = new StringBuilder();

        //command name
        message.append("&7| ").append(indentSpaces).append("&").append(CHAT_COLORS_INDENT[indent]).append("/").append(command);

        if (usage != null && (usage = usage.trim()).length() > 0)
            message.append(" &7").append(usage);

        if (description != null && (description = description.trim()).length() > 0)
            message.append(" &a- &f").append(description);

        if (subCommands > 0)
            message.append(" &7(&d").append(subCommands).append("&f sub commands&7)");

        return translateAlternateColorCodes('&', message.toString());
    }
}
