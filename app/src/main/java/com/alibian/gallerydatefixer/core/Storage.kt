package com.alibian.gallerydatefixer.core

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

data class StorageRoot(val label: String, val dir: File)

object Storage {

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    /** Internal storage plus any SD card / USB drive that is mounted. */
    fun roots(context: Context): List<StorageRoot> {
        val sm = context.getSystemService(StorageManager::class.java)
        val volumes = sm?.storageVolumes.orEmpty().mapNotNull { v ->
            val dir = v.directory ?: return@mapNotNull null
            StorageRoot(v.getDescription(context) ?: dir.name, dir)
        }
        return volumes.ifEmpty {
            listOf(StorageRoot("Internal storage", Environment.getExternalStorageDirectory()))
        }
    }

    /** Frequently used photo folders that exist on this phone. */
    fun shortcuts(): List<StorageRoot> {
        val base = Environment.getExternalStorageDirectory()
        return listOf(
            "Camera" to "DCIM/Camera",
            "Screenshots" to "DCIM/Screenshots",
            "Pictures" to "Pictures",
            "Download" to "Download",
            "WhatsApp Images" to "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images",
            "WhatsApp Video" to "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video",
            "Telegram" to "Pictures/Telegram",
        ).map { (label, path) -> StorageRoot(label, File(base, path)) }
            .filter { it.dir.isDirectory }
    }

    /** Sub-folders of [dir] for the folder browser, sorted by name, hidden ones excluded. */
    fun subfolders(dir: File): List<File> =
        dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
}
