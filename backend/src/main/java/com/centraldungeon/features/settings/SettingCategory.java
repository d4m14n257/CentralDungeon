package com.centraldungeon.features.settings;

/**
 * How {@code /admin/settings} groups what it shows.
 *
 * <p>The category is a property of the setting and not of the screen: two admins looking for "the
 * file cap" and "how long a reservation lasts" are asking the same kind of question, and a screen
 * that listed thirty keys alphabetically would make them read all thirty.
 *
 * <p><b>The baseline migration's column comment names a third group, {@code Texts}, and it is
 * deliberately absent</b>.
 * That group existed so the community could edit the sentences the application writes;
 * the backend now writes no sentences at all - it sends a code and its parameters, and the
 * frontend builds the phrase in the reader's language from {@code src/locales/}. A {@code Texts}
 * setting would be a sentence stored in one language, rendered raw, and it would break the rule against hardcoded text
 * the moment somebody read the application in the other one. Declaring the constant with nothing
 * in it would be an orphan nothing can use, so it is not declared: the
 * column is a {@code VARCHAR} and only what is written to it matters.
 */
public enum SettingCategory {

    /**
     * Values that change how the platform behaves towards people - who can see whom, how long a
     * window lasts. Changing one of these is a decision about the community, not about a resource.
     */
    Business,

    /** Caps and quotas: how big, how many, how long. Changing one of these is an operational call. */
    Limits
}
