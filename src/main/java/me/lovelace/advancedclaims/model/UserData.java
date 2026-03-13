package me.lovelace.advancedclaims.model;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class UserData {
    private final UUID uuid;
    private final AtomicInteger expansionBlocks = new AtomicInteger(0);
    private final AtomicInteger bonusMemberLimit = new AtomicInteger(0);
    private final AtomicInteger bonusClaimSlots = new AtomicInteger(0);
    private final AtomicInteger bonusBlocks = new AtomicInteger(0);

    private final Map<String, Integer> questProgress = new ConcurrentHashMap<>();
    private final Map<String, Boolean> questCompleted = new ConcurrentHashMap<>();
    private final Set<String> unlockedBuffs = ConcurrentHashMap.newKeySet();

    public UserData(UUID uuid) { this.uuid = uuid; }

    public UUID getUuid() { return uuid; }

    public int getExpansionBlocks() { return expansionBlocks.get(); }
    public void setExpansionBlocks(int val) { this.expansionBlocks.set(val); }
    public void addExpansionBlocks(int amount) { this.expansionBlocks.addAndGet(amount); }
    public void removeExpansionBlocks(int amount) {
        this.expansionBlocks.updateAndGet(v -> Math.max(0, v - amount));
    }

    public int getBonusMemberLimit() { return bonusMemberLimit.get(); }
    public void setBonusMemberLimit(int val) { this.bonusMemberLimit.set(val); }
    public void addBonusMemberLimit(int amount) { this.bonusMemberLimit.addAndGet(amount); }
    public void addBonusMembers(int amount) { this.bonusMemberLimit.addAndGet(amount); }

    public int getBonusClaimSlots() { return bonusClaimSlots.get(); }
    public void setBonusClaimSlots(int val) { this.bonusClaimSlots.set(val); }
    public void addBonusClaimSlots(int amount) { this.bonusClaimSlots.addAndGet(amount); }

    public int getBonusBlocks() { return bonusBlocks.get(); }
    public void setBonusBlocks(int val) { this.bonusBlocks.set(val); }
    public void addBonusBlocks(int amount) { this.bonusBlocks.addAndGet(amount); }

    public int getQuestProgress(String questId) { return questProgress.getOrDefault(questId, 0); }
    public void setQuestProgress(String questId, int progress) { questProgress.put(questId, progress); }
    public Map<String, Integer> getProgressMap() { return questProgress; }

    public boolean isQuestCompleted(String questId) { return questCompleted.getOrDefault(questId, false); }
    public void setQuestCompleted(String questId, boolean completed) { questCompleted.put(questId, completed); }
    public Map<String, Boolean> getCompletedMap() { return questCompleted; }

    public boolean hasBuffUnlocked(String buff) { return unlockedBuffs.contains(buff); }
    public void unlockBuff(String buff) { unlockedBuffs.add(buff); }
    public Set<String> getUnlockedBuffs() { return unlockedBuffs; }

    public void loadFrom(UserData other) {
        this.expansionBlocks.set(other.getExpansionBlocks());
        this.bonusMemberLimit.set(other.getBonusMemberLimit());
        this.bonusClaimSlots.set(other.getBonusClaimSlots());
        this.bonusBlocks.set(other.getBonusBlocks());

        this.questProgress.clear();
        this.questProgress.putAll(other.getProgressMap());

        this.questCompleted.clear();
        this.questCompleted.putAll(other.getCompletedMap());

        this.unlockedBuffs.clear();
        this.unlockedBuffs.addAll(other.getUnlockedBuffs());
    }
}