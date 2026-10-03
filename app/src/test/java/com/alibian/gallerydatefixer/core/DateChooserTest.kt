package com.alibian.gallerydatefixer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DateChooserTest {

    private val zone = ZoneId.of("Asia/Almaty")
    private val chooser = DateChooser(zone)
    private val parser = FilenameDateParser(zone)
    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0) =
        LocalDateTime.of(y, mo, d, h, mi, s).atZone(zone).toInstant().toEpochMilli()

    private val copiedToday = millis(2025, 9, 1, 10, 0)

    @Test fun exifWinsForPhotos() {
        val exif = millis(2023, 5, 14, 18, 15, 31)
        val c = chooser.choose(false, copiedToday, exif, parser.parse("20230514_181530.jpg"), preferFilename = false)
        assertEquals(DateSource.EXIF, c.source)
    }

    @Test fun preferFilenameOverridesExif() {
        val exif = millis(2020, 1, 1, 0, 0)
        val c = chooser.choose(false, copiedToday, exif, parser.parse("20230514_181530.jpg"), preferFilename = true)
        assertEquals(DateSource.FILENAME, c.source)
        assertEquals(millis(2023, 5, 14, 18, 15, 30), c.nameDate)
    }

    @Test fun videoWithTimeZoneShiftedMetadataUsesFileName() {
        // Local time written as if it were UTC: 5 hours off in Almaty.
        val shifted = millis(2023, 5, 14, 23, 15, 30)
        val c = chooser.choose(true, copiedToday, shifted, parser.parse("VID_20230514_181530.mp4"), preferFilename = false)
        assertEquals(DateSource.FILENAME, c.source)
    }

    @Test fun videoMetadataUsedWhenNameIsUnrelated() {
        val meta = millis(2023, 5, 14, 18, 15, 30)
        val c = chooser.choose(true, copiedToday, meta, parser.parse("VID_20190101_120000.mp4"), preferFilename = false)
        assertEquals(DateSource.VIDEO_METADATA, c.source)
    }

    @Test fun whatsappKeepsExactTimeWhenSameDay() {
        val received = millis(2023, 5, 14, 21, 3, 7)
        val c = chooser.choose(false, received, null, parser.parse("IMG-20230514-WA0007.jpg"), preferFilename = false)
        assertEquals(DateSource.FILENAME, c.source)
        assertEquals(received, c.nameDate)
    }

    @Test fun whatsappCopiedLaterGetsEstimatedTimeInOrder() {
        val a = chooser.choose(false, copiedToday, null, parser.parse("IMG-20230514-WA0007.jpg"), false).nameDate!!
        val b = chooser.choose(false, copiedToday, null, parser.parse("IMG-20230514-WA0008.jpg"), false).nameDate!!
        assertEquals(millis(2023, 5, 14, 12, 0, 7), a)
        assertTrue(b > a)
    }

    @Test fun nothingFound() {
        assertEquals(DateSource.NONE, chooser.choose(false, copiedToday, null, null, false).source)
    }

    @Test fun mediaItemStatusAndSourceSwitch() {
        val exif = millis(2023, 5, 14, 18, 15, 30)
        val name = millis(2023, 5, 20, 9, 0, 0)
        val item = MediaItem(
            path = "/x/a.jpg", name = "a.jpg", relativeFolder = "", isVideo = false,
            currentModified = exif, embeddedDate = exif, nameDate = name, nameDateIsDayOnly = false,
            canWriteExif = true, writeExifEnabled = true, source = DateSource.EXIF,
        )
        assertEquals(ItemStatus.OK, item.status)
        assertTrue(item.datesDisagree)
        val switched = item.copy(source = DateSource.FILENAME)
        assertEquals(ItemStatus.NEEDS_FIX, switched.status)
        assertTrue(switched.needsTimestamp)
        assertTrue(switched.needsExif)
        assertFalse(item.copy(writeExifEnabled = false, currentModified = name, source = DateSource.FILENAME).needsExif)
    }
}
