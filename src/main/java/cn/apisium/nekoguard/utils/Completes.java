package cn.apisium.nekoguard.utils;

import cn.apisium.nekocommander.completer.Completer;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class Completes {
    private Completes() {}
    @SuppressWarnings("deprecation")
    public static class MaterialCompleter implements Completer {
        private final static ArrayList<String> LIST = new ArrayList<>();
        static {
            for (final Material type : Material.values()) if (!type.isLegacy()) LIST.add(type.name().toLowerCase());
        }
        @Override
        @Nullable
        public List<String> complete(final @NotNull CommandSender sender, final @NotNull String[] args) {
            return LIST;
        }
    }
}
