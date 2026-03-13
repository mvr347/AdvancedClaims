package me.lovelace.advancedclaims.model;

import java.util.List;

/**
 * Модель квеста с полной поддержкой всех типов и функций.
 */
public record Quest(
    String id,
    String name,
    String description,
    QuestType type,
    String targetName,
    int targetAmount,
    int rewardSlots,
    int rewardBlocks,
    int rewardExpansionBlocks,
    int rewardMembers,
    String rewardBuff,
    String tier,
    boolean repeatable,
    boolean daily,
    Difficulty difficulty,
    String category,
    boolean eventOnly,
    String eventDate,
    int minPlayers,
    List<String> commands
) {
    /**
     * Типы квестов.
     */
    public enum QuestType {
        MINE_BLOCKS,          // Добыча блоков
        KILL_MOBS,            // Убийство мобов
        PLACE_BLOCKS,         // Установка блоков
        CRAFT_ITEMS,          // Крафт предметов
        FISH,                 // Рыбалка
        BREAK_CROPS,          // Сбор урожая
        BREED_ANIMALS,        // Разведение животных
        TRADE_VILLAGERS,      // Торговля с жителями
        ENCHANT_ITEMS,        // Зачарование предметов
        BREW_POTIONS,         // Варка зелий
        COMPLETE_RAIDS,       // Завершение рейдов
        EXPLORE_STRUCTURES,   // Исследование структур
        COLLECT_ITEMS,        // Сбор предметов
        USE_ITEMS,            // Использование предметов
        COMPLETE_ADVANCEMENTS,// Выполнение достижений
        CUSTOM                // Кастомный (через команды)
    }

    /**
     * Сложность квеста.
     */
    public enum Difficulty {
        EASY,       // Легкий
        MEDIUM,     // Средний
        HARD,       // Сложный
        LEGENDARY,  // Легендарный
        BOSS,       // Босс
        EVENT,      // Событие
        TEAM        // Командный
    }

    /**
     * Конструктор с минимальными параметрами.
     */
    public Quest {
        if (rewardSlots < 0) rewardSlots = 0;
        if (rewardBlocks < 0) rewardBlocks = 0;
        if (rewardExpansionBlocks < 0) rewardExpansionBlocks = 0;
        if (rewardMembers < 0) rewardMembers = 0;
        if (minPlayers < 1) minPlayers = 1;
        if (eventOnly && eventDate == null) eventDate = "";
        if (commands == null) commands = List.of();
    }
}
