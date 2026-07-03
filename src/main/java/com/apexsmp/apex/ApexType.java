package com.apexsmp.apex;

import java.util.List;
import java.util.Random;

public enum ApexType {
    LION("Lion", "<gold>", "Perm Strength I and +10% speed.", "Blood Frenzy: +20% damage dealt for 10s."),
    WOLF("Wolf", "<gray>", "Perm Speed I.", "Pack Hunt: summon 5 wolves and reveal all enemies within 30 blocks for 20s."),
    RHINO("Rhino", "<dark_gray>", "+4 hearts of max health.", "Charge: rush 7 blocks forward; hit players take 4.5 hearts and heavy knockback."),
    TREX("T-Rex", "<dark_green>", "Perm Fire Resistance.", "Rend: slash for 4 hearts and bleed for half a heart per second for 10s."),
    POLAR_BEAR("Polar Bear", "<aqua>", "+2 hearts, Speed II on snow and ice.", "Deep Freeze: trap your target in an ice cube for 3s."),
    SNAKE("Snake", "<green>", "+20% crouch speed; every 20 hits poisons the target (Poison I, 10s).", "Venomous Bite: inflict Poison II for 15s."),
    PANTHER("Panther", "<dark_purple>", "Perm Speed I.", "Shadow Dance: 3 teleport slashes of 2.5 hearts each; target is stunned until it ends."),
    HIPPO("Hippo", "<blue>", "Perm Resistance I and Water Breathing.", "Riverquake: leap up and slam down for 4 hearts and massive knockback in a 5 block radius."),
    DRAGON("Dragon", "<light_purple>", "Perm Strength I, Speed I and Fire Resistance.", "Skyfall: fly for 5s, then slam into the ground for 4.5 hearts.");

    private static final Random RANDOM = new Random();

    private final String displayName;
    private final String colorTag;
    private final String passiveDescription;
    private final String abilityDescription;

    ApexType(String displayName, String colorTag, String passiveDescription, String abilityDescription) {
        this.displayName = displayName;
        this.colorTag = colorTag;
        this.passiveDescription = passiveDescription;
        this.abilityDescription = abilityDescription;
    }

    public String displayName() {
        return displayName;
    }

    public String colorTag() {
        return colorTag;
    }

    public String coloredName() {
        return colorTag + "<bold>" + displayName + "</bold>";
    }

    public String passiveDescription() {
        return passiveDescription;
    }

    public String abilityDescription() {
        return abilityDescription;
    }

    /** Dragon is never rolled - it is traded in to an admin with a dragon egg. */
    public static List<ApexType> rollable() {
        return List.of(LION, WOLF, RHINO, TREX, POLAR_BEAR, SNAKE, PANTHER, HIPPO);
    }

    public static ApexType randomRollable() {
        List<ApexType> pool = rollable();
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    /** A random rollable apex that is guaranteed to differ from the current one. */
    public static ApexType randomRollableExcept(ApexType current) {
        ApexType next = randomRollable();
        while (next == current) {
            next = randomRollable();
        }
        return next;
    }

    public static ApexType fromString(String raw) {
        for (ApexType type : values()) {
            if (type.name().equalsIgnoreCase(raw) || type.displayName.equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }
}
