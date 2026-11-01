package com.centurywar.meta

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Wąski provider eksportujący tylko pliki .cwr z katalogu replays.
 * Nie ma żadnej ścieżki zapisu i nie udostępnia plików profilu.
 */
class ReplayShareProvider : ContentProvider() {
    companion object {
        private val idPattern = Regex("[a-z0-9_-]{1,64}")
        const val MIME = "application/octet-stream"

        fun uriFor(context: Context, id: String): Uri {
            require(idPattern.matches(id)) { "Niepoprawny identyfikator powtórki" }
            return Uri.Builder().scheme("content")
                .authority(context.packageName + ".replays")
                .appendPath(id + ".cwr").build()
        }

        /** Także dowiązania symboliczne prowadzące poza katalog są odrzucane. */
        internal fun fileFor(root: File, path: String?): File? {
            val fileName = path?.removePrefix("/") ?: return null
            if (!fileName.endsWith(".cwr")) return null
            val id = fileName.removeSuffix(".cwr")
            if (!idPattern.matches(id)) return null
            val directory = File(root, "replays").canonicalFile
            val candidate = File(directory, fileName).canonicalFile
            return candidate.takeIf {
                it.parentFile == directory && it.isFile &&
                    it.length() in 24L..ReplayStore.MAX_SHARE_BYTES.toLong()
            }
        }
    }

    override fun onCreate(): Boolean = true

    private fun resolve(uri: Uri): File? {
        val app = context ?: return null
        if (uri.scheme != "content" || uri.authority != app.packageName + ".replays" ||
            uri.pathSegments.size != 1 || uri.query != null || uri.fragment != null) return null
        return fileFor(app.filesDir, uri.path)
    }

    override fun getType(uri: Uri): String? = if (resolve(uri) != null) MIME else null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Tylko odczyt")
        val file = resolve(uri) ?: throw FileNotFoundException("Nieznana powtórka")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? {
        val file = resolve(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = cols.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> file.name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }.toTypedArray()
        return MatrixCursor(cols).apply { addRow(row) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?,
    ): Int = 0
}
