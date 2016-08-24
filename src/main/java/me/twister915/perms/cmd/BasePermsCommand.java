package me.twister915.perms.cmd;

import me.twister915.perms.bukkit.TwistedPermsBukkit;
import me.twister915.perms.model.PermissionsManager;
import org.bukkit.command.CommandSender;
import tech.rayline.core.command.PermissionException;
import tech.rayline.core.command.RDCommand;

public abstract class BasePermsCommand extends RDCommand {
    protected final PermissionsManager manager = TwistedPermsBukkit.getInstance().getPermissionsManager();

    protected BasePermsCommand(String name) {
        super(name);
        registerSubCommand(generateHelpCommand());
    }

    protected BasePermsCommand(String name, RDCommand... subCommands) {
        super(name, subCommands);
        registerSubCommand(generateHelpCommand());
    }

    protected RDCommand generateHelpCommand() {
        return new PermsHelpCommand(this);
    }

    @Override
    protected boolean shouldGenerateHelpCommand() {
        return false;
    }

    protected String getPath() {
        RDCommand superCommand = getSuperCommand();
        if (superCommand != null && superCommand instanceof BasePermsCommand)
            return ((BasePermsCommand) superCommand).getPath() + "." + getName();

        return getName();
    }

    @Override
    protected void checkPermission(CommandSender sender) throws PermissionException {
        if (!sender.hasPermission("twperms." + getPath()))
            throw new PermissionException("You don't have permission.");
    }

    public boolean hasPermission(CommandSender sender) {
        try {
            checkPermission(sender);
        } catch (PermissionException e) {
            return false;
        }
        return true;
    }
}
