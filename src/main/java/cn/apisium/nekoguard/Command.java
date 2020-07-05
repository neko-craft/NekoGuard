package cn.apisium.nekoguard;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class Command implements CommandExecutor {
    private final API api;
    private final Main main;
    Command(final Main main) {
        this.api = main.getApi();
        this.main = main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull org.bukkit.command.Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return true;
        }
        switch (args[0]) {
            case "i":
            case "inspect":
                if (!(sender instanceof Player)) return false;
                if (main.inspecting.contains(sender)) {
                    main.inspecting.remove(sender);
                } else main.inspecting.add((Player) sender);
                break;
        }
        return false;
    }
}
