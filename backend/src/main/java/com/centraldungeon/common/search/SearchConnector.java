package com.centraldungeon.common.search;

/** How a term joins the ones before it. Evaluated left to right, without precedence (decisiones.md #164). */
public enum SearchConnector {
    AND,
    OR
}
