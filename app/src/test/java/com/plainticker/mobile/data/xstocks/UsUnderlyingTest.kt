package com.plainticker.mobile.data.xstocks

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [XStockAsset.isUsUnderlying], the ballot's filter (audit 2026-09-26, item 3). Every case below
 * is a record shape the live catalog actually carried that day, not an invented one.
 */
class UsUnderlyingTest {

    private fun asset(country: String? = null, isin: String? = null, legacyIsin: String? = null, mic: String? = null) = XStockAsset(
        name = "Test xStock",
        symbol = "TESTx",
        isin = "CH1588664644",
        underlyingIsin = legacyIsin,
        underlying = if (country == null && isin == null) null else Underlying(symbol = "TEST", isin = isin, listingCountry = country),
        trading = mic?.let { Trading(exchange = Exchange(mic = it)) },
    )

    @Test
    fun `a US listing is US, whatever the incorporation`() {
        assertTrue(asset(country = "US", isin = "US98421M1062").isUsUnderlying) // Xerox
        // Medtronic: Irish ISIN, NYSE listing. The ISIN prefix alone would drop it wrongly.
        assertTrue(asset(country = "US", isin = "IE00BTN1Y115").isUsUnderlying)
        assertTrue(asset(country = " us ").isUsUnderlying)
    }

    @Test
    fun `London, Hong Kong, Madrid and Frankfurt listings are not`() {
        assertFalse(asset(country = "GB", isin = "GB00BKDRYJ47").isUsUnderlying) // Airtel Africa
        assertFalse(asset(country = "GB", isin = "GB0006731235").isUsUnderlying) // Associated British Foods
        assertFalse(asset(country = "HK", isin = "KYG875721634").isUsUnderlying)
        assertFalse(asset(country = "ES", isin = "ES0148396007").isUsUnderlying)
        assertFalse(asset(country = "DE", isin = "DE0007164600").isUsUnderlying)
    }

    @Test
    fun `the country wins over a contradicting ISIN`() {
        assertFalse("a London listing of a US-incorporated name is still London", asset(country = "GB", isin = "US0000000001").isUsUnderlying)
    }

    @Test
    fun `without a country, the underlying ISIN decides, then the venue`() {
        assertTrue(asset(isin = "US0378331005").isUsUnderlying)
        assertFalse(asset(isin = "GB0006731235").isUsUnderlying)
        assertTrue(asset(legacyIsin = "US0378331005").isUsUnderlying)
        assertFalse(asset(legacyIsin = "HK0000069689").isUsUnderlying)
        assertTrue(asset(mic = "XNYS").isUsUnderlying)
        assertTrue(asset(mic = "ARCX").isUsUnderlying)
        assertFalse(asset(mic = "XLON").isUsUnderlying)
        assertFalse(asset(mic = "XHKG").isUsUnderlying)
    }

    @Test
    fun `a blank legacy ISIN is not a signal`() {
        // Upstream sends an empty string for an unrecorded underlyingIsin.
        assertFalse(asset(legacyIsin = "", mic = "XLON").isUsUnderlying)
    }

    @Test
    fun `a record with no signal at all stays in, because the server still guards the vote`() {
        assertTrue(asset().isUsUnderlying)
    }
}
