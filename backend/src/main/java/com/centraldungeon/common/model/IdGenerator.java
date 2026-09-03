package com.centraldungeon.common.model;

import java.security.SecureRandom;
import java.util.UUID;

/** UUID v7 (RFC 9562): 48-bit millisecond timestamp + random bits, sortable by creation order (modelo-datos.md #9). */
public final class IdGenerator {

    /** Source of the 74 random bits. Cryptographic, so ids cannot be guessed from one another. */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Utility class: it only generates ids and holds no state. */
    private IdGenerator() {
    }

    /**
     * Generates one id.
     *
     * <p>Ids being unpredictable is <b>defence in depth, never authorization</b> (arquitectura.md
     * 2.6): knowing an id must never be the only thing standing between someone and a resource.
     *
     * @return a UUID v7 in its canonical 36-character form, sortable by creation time
     */
    public static String newId() {
        long unixTsMs = System.currentTimeMillis() & 0xFFFFFFFFFFFFL;

        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);

        long randA = ((rand[0] & 0xFFL) << 4) | ((rand[1] & 0xF0L) >>> 4);
        long mostSigBits = (unixTsMs << 16) | (0x7L << 12) | (randA & 0xFFFL);

        long randB = 0;
        for (int i = 2; i < 10; i++) {
            randB = (randB << 8) | (rand[i] & 0xFFL);
        }
        randB &= 0x3FFFFFFFFFFFFFFFL;
        long leastSigBits = (0b10L << 62) | randB;

        return new UUID(mostSigBits, leastSigBits).toString();
    }
}
