package com.centraldungeon.features.catalogs;

/**
 * What kind of thing a tag says about a table. A fixed set on purpose: the categories are how a
 * table's tags are grouped on screen, so a new one is a new block of the interface, not data an
 * admin types. The backend sends the code; each client translates it.
 *
 * <p>A tag has one category or none. It belongs to the tag's group, so only the canonical entry
 * stores it and its aliases read it from there.
 */
public enum TagCategory {

    /** What the story is about: horror, intrigue, exploration. */
    Theme,

    /** How the table plays: roleplay, combat, sandbox. */
    PlayStyle,

    /** Content a player may want to know about before joining. */
    ContentWarning,

    /** The safety tools the table uses: lines and veils, X-card. */
    SafetyTool,

    /** Who the table is for by experience: beginners, veterans. */
    Experience,

    /** The language the table is played in. */
    Language,

    /** The age range the table is meant for. */
    Age,

    /** The table's format and rough length: one-shot, homebrew, long campaign. */
    Format
}
