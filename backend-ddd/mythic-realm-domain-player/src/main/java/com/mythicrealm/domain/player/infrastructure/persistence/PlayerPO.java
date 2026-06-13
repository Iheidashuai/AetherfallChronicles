package com.mythicrealm.domain.player.infrastructure.persistence;

/**
 * 玩家持久化对象
 */
public class PlayerPO {
    private Long id;
    private Long accountId;
    private String name;
    private String profession;
    private int level;
    private int experience;
    private int gold;
    private int strength;
    private int agility;
    private int constitution;
    private int intelligence;
    private int spirit;
    private int freePoints;

    // Constructors
    public PlayerPO() {
    }

    public PlayerPO(Long id, Long accountId, String name, String profession,
                   int level, int experience, int gold,
                   int strength, int agility, int constitution,
                   int intelligence, int spirit, int freePoints) {
        this.id = id;
        this.accountId = accountId;
        this.name = name;
        this.profession = profession;
        this.level = level;
        this.experience = experience;
        this.gold = gold;
        this.strength = strength;
        this.agility = agility;
        this.constitution = constitution;
        this.intelligence = intelligence;
        this.spirit = spirit;
        this.freePoints = freePoints;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public int getStrength() {
        return strength;
    }

    public void setStrength(int strength) {
        this.strength = strength;
    }

    public int getAgility() {
        return agility;
    }

    public void setAgility(int agility) {
        this.agility = agility;
    }

    public int getConstitution() {
        return constitution;
    }

    public void setConstitution(int constitution) {
        this.constitution = constitution;
    }

    public int getIntelligence() {
        return intelligence;
    }

    public void setIntelligence(int intelligence) {
        this.intelligence = intelligence;
    }

    public int getSpirit() {
        return spirit;
    }

    public void setSpirit(int spirit) {
        this.spirit = spirit;
    }

    public int getFreePoints() {
        return freePoints;
    }

    public void setFreePoints(int freePoints) {
        this.freePoints = freePoints;
    }
}
