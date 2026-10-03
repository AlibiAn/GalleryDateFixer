package com.alibian.gallerydatefixer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MetadataDatesTest {

    private val zone = ZoneId.of("Europe/Berlin")

    @Test fun exifInLocalZone() {
        val expected = ZonedDateTime.of(2023, 5, 14, 18, 15, 30, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, MetadataDates.parseExif("2023:05:14 18:15:30", zone = zone))
    }

    @Test fun exifWithOffsetAndSubSec() {
        val expected = ZonedDateTime.parse("2023-05-14T18:15:30.250-05:00").toInstant().toEpochMilli()
        assertEquals(expected, MetadataDates.parseExif("2023:05:14 18:15:30", "-05:00", "25", zone))
    }

    @Test fun exifInvalid() {
        assertNull(MetadataDates.parseExif("0000:00:00 00:00:00", zone = zone))
        assertNull(MetadataDates.parseExif("    :  :     :  :  ", zone = zone))
        assertNull(MetadataDates.parseExif(null, zone = zone))
    }

    @Test fun videoUtc() {
        val expected = ZonedDateTime.parse("2023-05-14T16:15:30Z").toInstant().toEpochMilli()
        assertEquals(expected, MetadataDates.parseVideo("20230514T161530.000Z", zone))
    }

    @Test fun videoIsoFormat() {
        val expected = ZonedDateTime.parse("2023-05-14T16:15:30.123Z").toInstant().toEpochMilli()
        assertEquals(expected, MetadataDates.parseVideo("2023-05-14T16:15:30.123456Z", zone))
    }

    @Test fun videoUnsetDateIsRejected() {
        assertNull(MetadataDates.parseVideo("19040101T000000.000Z", zone))
    }

    @Test fun formatRoundTrip() {
        val millis = ZonedDateTime.of(2023, 5, 14, 18, 15, 30, 0, zone).toInstant().toEpochMilli()
        val (date, offset) = MetadataDates.formatExif(millis, zone)
        assertEquals("2023:05:14 18:15:30", date)
        assertEquals("+02:00", offset)
        assertEquals(millis, MetadataDates.parseExif(date, offset, zone = ZoneId.of("UTC")))
    }
}
