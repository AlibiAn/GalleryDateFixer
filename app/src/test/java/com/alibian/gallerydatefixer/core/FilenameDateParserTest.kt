package com.alibian.gallerydatefixer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FilenameDateParserTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0).atZone(zone).toInstant().toEpochMilli()
    private val parser = FilenameDateParser(zone) { now }

    private fun assertParsed(name: String, expected: LocalDateTime, dateOnly: Boolean = false) {
        val result = parser.parse(name)
        requireNotNull(result) { "no date parsed from $name" }
        assertEquals(name, expected, result.localDateTime)
        assertEquals(name, dateOnly, result.dateOnly)
        assertEquals(name, expected.atZone(zone).toInstant().toEpochMilli(), result.epochMillis)
    }

    @Test fun samsungAndAndroidCamera() {
        assertParsed("20230514_181530.jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
        assertParsed("IMG_20230514_181530.jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
        assertParsed("VID_20230514_181530.mp4", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
        assertParsed("20230514_181530(0).jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
        assertParsed("IMG20230514181530.jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
    }

    @Test fun pixelWithMillis() {
        assertParsed("PXL_20230514_181530123.jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30, 123_000_000))
        assertParsed("PXL_20230514_181530123.MP.jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30, 123_000_000))
    }

    @Test fun screenshots() {
        assertParsed("Screenshot_20230514-181530_Chrome.jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
        assertParsed("Screenshot_2023-05-14-18-15-30.png", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
        assertParsed("Screenshot 2023-05-14 at 18.15.30.png", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
    }

    @Test fun messengers() {
        assertParsed("PHOTO-2023-05-14-18-15-30.jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
        assertParsed("signal-2023-05-14-181530.jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
        assertParsed("signal-2023-05-14-181530_002.jpg", LocalDateTime.of(2023, 5, 14, 18, 15, 30))
    }

    @Test fun whatsappDateOnlyKeepsSequenceOrder() {
        assertParsed("IMG-20230514-WA0000.jpg", LocalDateTime.of(2023, 5, 14, 12, 0, 0), dateOnly = true)
        assertParsed("IMG-20230514-WA0007.jpg", LocalDateTime.of(2023, 5, 14, 12, 0, 7), dateOnly = true)
        assertParsed("VID-20230514-WA0012.mp4", LocalDateTime.of(2023, 5, 14, 12, 0, 12), dateOnly = true)
        assertParsed("2023-05-14.jpg", LocalDateTime.of(2023, 5, 14, 12, 0, 0), dateOnly = true)
    }

    @Test fun unixTimestamps() {
        val ms = parser.parse("FB_IMG_1684080930000.jpg")!!
        assertEquals(1684080930000L, ms.epochMillis)
        assertFalse(ms.dateOnly)
        assertEquals(1684080930000L, parser.parse("received_1684080930.jpeg")!!.epochMillis)
        assertEquals(1684080930000L, parser.parse("1684080930.jpg")!!.epochMillis)
    }

    @Test fun randomIdsAreNotTimestamps() {
        assertNull(parser.parse("Snapchat-1684080930.jpg"))
        assertNull(parser.parse("Snapchat-1684080930123.jpg"))
        assertNull(parser.parse("document_1684080930.jpg"))
        assertNull(parser.parse("1500x1684080930.jpg"))
    }

    @Test fun rejectsGarbage() {
        assertNull(parser.parse("holiday.jpg"))
        assertNull(parser.parse("IMG_1234.jpg"))
        assertNull(parser.parse("20231399_181530.jpg")) // invalid month/day
        assertNull(parser.parse("20300101_120000.jpg")) // in the future
        assertNull(parser.parse("photo_0000000001.jpg")) // timestamp in 1970
    }

    @Test fun invalidDateTimeFallsBackToDateOnly() {
        // 25 o'clock is not a time, but the day is still valid.
        val r = parser.parse("20230514_251530.jpg")
        assertTrue(r != null && r.dateOnly)
    }
}
