package com.apexsmp.apex;

public class PlayerApexData {

    /** Everyone begins with 1 absorbed kill token; death resets this to 0. */
    public static final int STARTING_TOKENS = 1;

    private ApexType apex;
    private int tokensConsumed = STARTING_TOKENS;
    private boolean abilityUnlocked;
    private int snakeHitCounter;
    private String lastKnownName = "";

    public ApexType getApex() {
        return apex;
    }

    public void setApex(ApexType apex) {
        this.apex = apex;
    }

    public int getTokensConsumed() {
        return tokensConsumed;
    }

    public void setTokensConsumed(int tokensConsumed) {
        this.tokensConsumed = Math.max(0, tokensConsumed);
    }

    public boolean isAbilityUnlocked() {
        return abilityUnlocked;
    }

    public void setAbilityUnlocked(boolean abilityUnlocked) {
        this.abilityUnlocked = abilityUnlocked;
    }

    public int getSnakeHitCounter() {
        return snakeHitCounter;
    }

    public void setSnakeHitCounter(int snakeHitCounter) {
        this.snakeHitCounter = snakeHitCounter;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }
}
