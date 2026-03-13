package me.lovelace.advancedclaims.gui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractGUI implements InventoryHolder {
    protected Inventory inventory;

    // Константы со всеми текстурами
    public static final String HEAD_BACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmUyYjkzNmNhODAyNmJkMjg2NTFkN2M5ZjI4MTlkMmU5MjM2OTc3MzRkMThkZmRiMTM1NTBmOGZkYWQ1ZiJ9fX0=";
    public static final String HEAD_BARRIER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkMWFiYTczZjYzOWY0YmM0MmJkNDgxOTZjNzE1MTk3YmUyNzEyYzNiOTYyYzk3ZWJmOWU5ZWQ4ZWZhMDI1In19fQ==";
    public static final String HEAD_SETTINGS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGViMmQ3NTRhYmJjNWQwMDRiODBmZTA4YjUzMTg2ZjY5ZGM4ZTllZjZmOGY3ZmQwNzIxNGE4Yzg0OTZlYjA4NSJ9fX0=";
    public static final String HEAD_MEMBERS = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjFhZTgwMzg0YTAwYjZmOWY2NGRkODMwN2E5MDY3NjU0NGM5N2E3OTI5NzE2NWVhNzEzMjYyYzdkODgzMzg0NyJ9fX0=";
    public static final String HEAD_INFO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjgwZDMyOTVkM2Q5YWJkNjI3NzZhYmNiOGRhNzU2ZjI5OGE1NDVmZWU5NDk4YzRmNjlhMWMyYzc4NTI0YzgyNCJ9fX0=";
    public static final String HEAD_DESC = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmRkMmViMGM2ZjhhOTU0M2VmNWZkNzI1MjVjYzJmYWIzNTY2M2NkNzA5MTM1ZTQzYjhlMjU3ZGMwYjc1ODk0OCJ9fX0=";
    public static final String HEAD_EXPAND = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzA0MGZlODM2YTZjMmZiZDJjN2E5YzhlYzZiZTUxNzRmZGRmMWFjMjBmNTVlMzY2MTU2ZmE1ZjcxMmUxMCJ9fX0=";
    public static final String HEAD_MSG = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDc1N2U5NzJjNzYzMTQ5NDc2YmM1NTZjZmJhZDM2MTRjMWQ0OTk1NGYxM2Y4ODkzZWI5ZDEwNTg0NTc1M2YyZSJ9fX0=";
    public static final String HEAD_SATURATION = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdhMzk4N2M1Y2RiMzVhYmE5YWU2ZjJlMjM0ODlhYTk2ZTA4MGU5M2ZhYzQzNWRjNjQwZjczN2I1Y2E0MDFkMyJ9fX0=";
    public static final String HEAD_DENY_ENTRY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWFlMGU0ODZkYjRlYzQ5ZmYxYjUyY2ZlY2VkYTRjM2YzNmZkZTIzYzgzNWVhM2NjZmNhYWM5MzVlNDliNWYxMCJ9fX0=";
    public static final String HEAD_REGEN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWVmNTM5YjE2NTEyNWNmYTQ2YjA2ZmZiOTY1OWU3Y2Y4OTA4NGJiZDNlZGUxYjMxNGVkYzhmNDQzMzQzZDYxYyJ9fX0=";
    public static final String HEAD_HASTE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTRlNDM1OGQ3MzRhNmUwNjhlYjA3Y2I4ZmM1ZmZkZThiOTQ4MDBlYjM5Njc3NzQyOGE0ZjU1OTMxNWExZmY0ZCJ9fX0=";
    public static final String HEAD_QUEST = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTZkN2I3MjA0YmVhMWZmMGRmNzkzMGJkNDU1OWEzMGI1ZWE1NTllNTYyNDI5NDY5NGU4NjdiZjdiNWFlMDM2MSJ9fX0=";
    public static final String HEAD_ADD_MEMBER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvM2VkZDIwYmU5MzUyMDk0OWU2Y2U3ODlkYzRmNDNlZmFlYjI4YzcxN2VlNmJmY2JiZTAyNzgwMTQyZjcxNiJ9fX0=";
    public static final String HEAD_DELETE_YES = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTMwZjQ1MzdkMjE0ZDM4NjY2ZTYzMDRlOWM4NTFjZDZmN2U0MWEwZWI3YzI1MDQ5YzlkMjJjOGM1ZjY1NDVkZiJ9fX0=";
    public static final String HEAD_DELETE_NO = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE2Nzg3YmEzMjU2NGU3YzJmM2EwY2U2NDQ5OGVjYmIyM2I4OTg0NWU1YTY2YjVjZWM3NzM2ZjcyOWVkMzcifX19";
    public static final String HEAD_HIDE_ANCHOR = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODI1MzBmMzAxZDFhNDA2NDcyNjEzY2UzNTgwNzgwYTUzYmFmZGI2MWM4NzE0ZGQzNWRmYTU0NDQwYmFhMjE2YiJ9fX0=";
    public static final String HEAD_DEMOTE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzQzNzM0NmQ4YmRhNzhkNTI1ZDE5ZjU0MGE5NWU0ZTc5ZGFlZGE3OTVjYmM1YTEzMjU2MjM2MzEyY2YifX19";
    public static final String HEAD_CROP_GROWTH = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTNjYjhmNjFlN2Y2YmY5NTdhMjEzNGU5NmZhZWIwZmM5MjQxMzdkNGJmZjg4ZDk1MThiMmJmNjYyNTg2YzkyZSJ9fX0=";
    public static final String HEAD_PVP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNmE1MzYxYjUyZGFmNGYxYzVjNTQ4MGEzOWZhYWExMDg5NzU5NWZhNTc2M2Y3NTdiZGRhMzk1NjU4OGZlYzY3OCJ9fX0=";

    public AbstractGUI(int size, Component title) {
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public AbstractGUI(org.bukkit.event.inventory.InventoryType type, Component title) {
        this.inventory = Bukkit.createInventory(this, type, title);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    public abstract void handleClick(InventoryClickEvent event);
    protected abstract void setMenuItems();

    // Генератор кастомных голов через Paper API
    protected ItemStack createHead(String base64, Component name, java.util.List<Component> lore) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
        if (meta != null) {
            com.destroystokyo.paper.profile.PlayerProfile profile = org.bukkit.Bukkit.createProfile(java.util.UUID.randomUUID());
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
            if (name != null) meta.displayName(name);
            if (lore != null && !lore.isEmpty()) meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    protected void fillEmptySlots() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            glass.setItemMeta(meta);
        }
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType() == Material.AIR) {
                inventory.setItem(i, glass);
            }
        }
    }
}