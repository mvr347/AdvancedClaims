package me.lovelace.advancedclaims.model;

public enum ClaimFlag {
    PVP(false), ENTRY_MESSAGE(true), EXPLOSIONS(false), FIRE_SPREAD(false), MOB_GRIEFING(false),
    HIDE_ANCHOR(false), DENY_ENTRY(false),

    SILENT_DENY(false),

    // ИЗМЕНЕНО: Убрана скорость, добавлен рост растений
    PERK_HASTE(false), PERK_REGEN(false), PERK_SATURATION(false), PERK_CROP_GROWTH(false),

    MSG_SCREEN(true), MSG_ACTIONBAR(true);

    private final boolean defaultState;

    ClaimFlag(boolean defaultState) { this.defaultState = defaultState; }
    public boolean getDefaultState() { return defaultState; }
}