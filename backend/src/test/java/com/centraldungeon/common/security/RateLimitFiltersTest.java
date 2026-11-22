package com.centraldungeon.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** How a client address becomes the key its requests are counted under. */
class RateLimitFiltersTest {

    @Test
    void anIpv4AddressIsCountedWhole() {
        assertThat(RateLimitFilters.ipKey("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(RateLimitFilters.ipKey("203.0.113.8")).isNotEqualTo(RateLimitFilters.ipKey("203.0.113.7"));
    }

    @Test
    void twoIpv6AddressesOfTheSame64ShareACounter() {
        assertThat(RateLimitFilters.ipKey("2001:db8:1:2:aaaa:bbbb:cccc:dddd"))
                .isEqualTo(RateLimitFilters.ipKey("2001:db8:1:2::1"))
                .isEqualTo("2001:0db8:0001:0002::/64");
    }

    @Test
    void ipv6AddressesOfDifferent64sDoNotShareACounter() {
        assertThat(RateLimitFilters.ipKey("2001:db8:1:2::1")).isNotEqualTo(RateLimitFilters.ipKey("2001:db8:1:3::1"));
    }

    @Test
    void anIpv4MappedAddressIsCountedAsTheIpv4ItCarries() {
        assertThat(RateLimitFilters.ipKey("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }

    @Test
    void aZoneIdOfALinkLocalAddressDoesNotChangeItsPrefix() {
        assertThat(RateLimitFilters.ipKey("fe80::1%en0")).isEqualTo(RateLimitFilters.ipKey("fe80::2"));
    }

    @Test
    void whatIsNotAnAddressIsKeptAsItCame() {
        assertThat(RateLimitFilters.ipKey("not:an:address")).isEqualTo("not:an:address");
    }
}
