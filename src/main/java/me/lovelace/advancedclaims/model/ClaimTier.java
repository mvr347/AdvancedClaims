package me.lovelace.advancedclaims.model;

import org.bukkit.Material;

import java.util.List;

/**
 * Модель уровня якоря привата.
 * Определяет параметры для каждого тира якорей.
 * 
 * @param id              Уникальный идентификатор тира (например, "tier-1")
 * @param material        Материал блока якоря
 * @param name            Название предмета (MiniMessage формат)
 * @param lore            Описание предмета (список строк)
 * @param radiusX         Радиус привата по оси X
 * @param radiusY         Радиус привата по оси Y (высота)
 * @param radiusZ         Радиус привата по оси Z
 * @param maxRadius       Максимальный радиус для расширения
 * @param customModelData CustomModelData для кастомной текстуры
 * @param permission      Требуемая пермишн для использования
 * @param createCost      Стоимость создания привата
 * @param borderRed       Красный компонент цвета границы (0-255)
 * @param borderGreen     Зеленый компонент цвета границы (0-255)
 * @param borderBlue      Синий компонент цвета границы (0-255)
 * @param placeSound      Звук при установке якоря
 * @param breakSound      Звук при удалении якоря
 */
public record ClaimTier(
    String id,
    Material material,
    String name,
    List<String> lore,
    int radiusX,
    int radiusY,
    int radiusZ,
    int maxRadius,
    int customModelData,
    String permission,
    long createCost,
    int borderRed,
    int borderGreen,
    int borderBlue,
    String placeSound,
    String breakSound
) {
    /**
     * Конструктор с минимальными параметрами (обратная совместимость).
     */
    public ClaimTier {
        if (permission == null || permission.isEmpty()) permission = "none";
        if (createCost < 0) createCost = 0;
        if (borderRed < 0) borderRed = 0;
        if (borderRed > 255) borderRed = 255;
        if (borderGreen < 0) borderGreen = 0;
        if (borderGreen > 255) borderGreen = 255;
        if (borderBlue < 0) borderBlue = 0;
        if (borderBlue > 255) borderBlue = 255;
        if (placeSound == null || placeSound.isEmpty()) placeSound = "BLOCK_BEACON_ACTIVATE";
        if (breakSound == null || breakSound.isEmpty()) breakSound = "BLOCK_NOTE_BLOCK_BASS";
    }

    /**
     * Конструктор с минимальными параметрами.
     */
    public ClaimTier(String id, Material material, String name, List<String> lore,
                     int radiusX, int radiusY, int radiusZ, int maxRadius, int customModelData) {
        this(id, material, name, lore, radiusX, radiusY, radiusZ, maxRadius,
             customModelData, "none", 0, 0, 255, 0,
             "BLOCK_BEACON_ACTIVATE", "BLOCK_NOTE_BLOCK_BASS");
    }

    /**
     * Получить размер привата по оси X.
     * @return Полный размер (radiusX * 2 + 1)
     */
    public int getSizeX() {
        return radiusX * 2 + 1;
    }

    /**
     * Получить размер привата по оси Y.
     * @return Полный размер (radiusY * 2 + 1)
     */
    public int getSizeY() {
        return radiusY * 2 + 1;
    }

    /**
     * Получить размер привата по оси Z.
     * @return Полный размер (radiusZ * 2 + 1)
     */
    public int getSizeZ() {
        return radiusZ * 2 + 1;
    }

    /**
     * Получить максимальный размер привата.
     * @return Полный максимальный размер (maxRadius * 2 + 1)
     */
    public int getMaxSize() {
        return maxRadius * 2 + 1;
    }

    /**
     * Получить цвет границы в формате RGB.
     * @return int значение цвета
     */
    public int getBorderColor() {
        return (borderRed << 16) | (borderGreen << 8) | borderBlue;
    }

    /**
     * Получить строковое представление цвета границы.
     * @return Строка в формате "R,G,B"
     */
    public String getBorderColorString() {
        return borderRed + "," + borderGreen + "," + borderBlue;
    }

    /**
     * Проверить, доступна ли пермишн для этого тира.
     * @return true если пермишн "none"
     */
    public boolean isPermissionFree() {
        return permission == null || permission.equalsIgnoreCase("none");
    }

    /**
     * Получить Material из названия звука установки.
     * @return Material звука или null
     */
    public org.bukkit.Sound getPlaceSoundObject() {
        try {
            return org.bukkit.Sound.valueOf(placeSound);
        } catch (IllegalArgumentException e) {
            return org.bukkit.Sound.BLOCK_BEACON_ACTIVATE;
        }
    }

    /**
     * Получить Material из названия звука удаления.
     * @return Material звука или null
     */
    public org.bukkit.Sound getBreakSoundObject() {
        try {
            return org.bukkit.Sound.valueOf(breakSound);
        } catch (IllegalArgumentException e) {
            return org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS;
        }
    }
}
