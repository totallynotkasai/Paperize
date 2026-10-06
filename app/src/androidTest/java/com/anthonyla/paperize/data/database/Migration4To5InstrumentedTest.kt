package com.anthonyla.paperize.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.anthonyla.paperize.core.FavoritesMode
import com.anthonyla.paperize.core.ScreenType
import com.anthonyla.paperize.data.mapper.toDomainModel
import com.anthonyla.paperize.domain.model.WallpaperEffects
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Opening a version 4 library with the version 5 schema; Room fails to open it if the schemas differ. */
class Migration4To5InstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun version4LibraryGainsTheNewColumnsWithDefaultsAndKeepsEverything() = runBlocking {
        val name = "migration-test-4-5"
        context.deleteDatabase(name)
        try {
            createVersion4Database(name)
            val db = Room.databaseBuilder(context, PaperizeDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS).build()
            try {
                val album = db.albumDao().getAlbumWithDetails("album").first()!!.toDomainModel()
                assertEquals("My album", album.name)
                assertNull(album.effects)
                assertEquals(FavoritesMode.MARKER_ONLY, album.favoritesMode)
                val images = album.wallpapers + album.folders.flatMap { it.wallpapers }
                assertEquals(setOf("direct", "folder-image"), images.map { it.id }.toSet())
                images.forEach {
                    assertFalse(it.excluded)
                    assertFalse(it.favorite)
                    assertFalse(it.accessLost)
                }
                assertEquals(listOf("direct"), db.wallpaperQueueDao().getQueueItems("album", ScreenType.HOME).map { it.wallpaperId })
                assertEquals("direct", db.wallpaperCurrentDao().getCurrentWallpaper("album", ScreenType.HOME)?.id)
                // Direct images rotate first, then folders, whatever their stored order says.
                assertEquals(listOf("direct", "folder-image"), db.wallpaperDao().getOrderedWallpaperIdsByAlbum("album"))
                db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }

                // The new columns hold values the v5 entities read back. (An album REPLACE would
                // cascade-delete its images, so this writes the row in place.)
                db.openHelper.writableDatabase.execSQL(
                    """UPDATE albums SET effects = '{"enableBlur":true,"blurPercentage":30}', favoritesMode = 'FAVORITES_ONLY' WHERE id = 'album'"""
                )
                assertEquals(1, db.wallpaperDao().setAccessLost(listOf("folder-image"), true))
                val reloaded = db.albumDao().getAlbumWithDetails("album").first()!!.toDomainModel()
                assertEquals(WallpaperEffects(enableBlur = true, blurPercentage = 30), reloaded.effects)
                assertEquals(FavoritesMode.FAVORITES_ONLY, reloaded.favoritesMode)
                assertEquals(listOf("direct"), db.wallpaperDao().getOrderedWallpaperIdsByAlbum("album"))
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun upgradedLibraryKeepsOlderRowsAndItsCleanUpRules() = runBlocking {
        val name = "migration-test-4-5-rules"
        context.deleteDatabase(name)
        try {
            createVersion4Database(name) { db ->
                db.execSQL("INSERT INTO albums VALUES ('other', 'Other', NULL, 11, 12)")
                db.execSQL("INSERT INTO wallpapers (id, albumId, folderId, uri, fileName, dateModified, displayOrder, sourceType, addedAt, mediaType) VALUES ('other-image', 'other', NULL, 'content://other', 'c.jpg', 13, 0, 'DIRECT', 14, 'IMAGE')")
                db.execSQL("INSERT INTO wallpaper_queue VALUES (2, 'album', 'folder-image', 'LIVE', 0, 15)")
                db.execSQL("INSERT INTO wallpaper_current VALUES ('other', 'LIVE', 'other-image', 16)")
                // 4.2.0 could record one image for both screens; Phase 3 stopped reading these.
                db.execSQL("INSERT INTO wallpaper_current VALUES ('album', 'BOTH', 'folder-image', 17)")
            }
            val db = Room.databaseBuilder(context, PaperizeDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS).build()
            try {
                assertEquals(listOf("folder-image"), db.wallpaperQueueDao().getQueueItems("album", ScreenType.LIVE).map { it.wallpaperId })
                assertEquals("other-image", db.wallpaperCurrentDao().getCurrentWallpaper("other", ScreenType.LIVE)?.id)
                // The old shared record is kept and still reads; it only goes with its album.
                assertEquals("folder-image", db.wallpaperCurrentDao().getCurrentWallpaper("album", ScreenType.BOTH)?.id)

                val sql = db.openHelper.writableDatabase
                // A row written without the new columns gets their defaults.
                sql.execSQL("INSERT INTO wallpapers (id, albumId, folderId, uri, fileName, dateModified, displayOrder, sourceType, addedAt, mediaType) VALUES ('late', 'other', NULL, 'content://late', 'd.jpg', 18, 1, 'DIRECT', 19, 'IMAGE')")
                sql.query("SELECT excluded, favorite, accessLost FROM wallpapers WHERE id = 'late'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(listOf(0, 0, 0), (0..2).map(it::getInt))
                }
                sql.execSQL("INSERT INTO albums (id, name, coverUri, createdAt, modifiedAt) VALUES ('new', 'New', NULL, 20, 21)")
                sql.query("SELECT effects, favoritesMode FROM albums WHERE id = 'new'").use {
                    assertTrue(it.moveToFirst())
                    assertTrue(it.isNull(0))
                    assertEquals(FavoritesMode.MARKER_ONLY.name, it.getString(1))
                }
                sql.query("PRAGMA integrity_check").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("ok", it.getString(0))
                }

                // Deleting an album still removes everything it owns, and nothing else.
                db.albumDao().deleteAlbumById("album")
                listOf("folders", "wallpapers", "wallpaper_queue", "wallpaper_current").forEach { table ->
                    sql.query("SELECT COUNT(*) FROM $table WHERE albumId = 'album'").use {
                        assertTrue(it.moveToFirst())
                        assertEquals("$table rows left behind", 0, it.getInt(0))
                    }
                }
                assertEquals(listOf("late", "other-image"), db.wallpaperDao().getOrderedWallpaperIdsByAlbum("other").sorted())
                assertEquals("other-image", db.wallpaperCurrentDao().getCurrentWallpaper("other", ScreenType.LIVE)?.id)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

    /** A version 4 database built from the exported 4.json, with one album; [extra] adds rows. */
    private fun createVersion4Database(name: String, extra: (SQLiteDatabase) -> Unit = {}) {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.anthonyla.paperize.data.database.PaperizeDatabase/4.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database").getJSONArray("entities") }
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            for (index in 0 until schema.length()) {
                val entity = schema.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (i in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            db.execSQL("INSERT INTO albums VALUES ('album', 'My album', 'content://direct', 1, 2)")
            db.execSQL("INSERT INTO folders (id, albumId, name, uri, coverUri, dateModified, displayOrder, addedAt) VALUES ('folder', 'album', 'Photos', 'content://tree', 'content://folder-image', 3, 0, 4)")
            // The folder image has the lower order, as upstream's global numbering could leave it.
            db.execSQL("INSERT INTO wallpapers (id, albumId, folderId, uri, fileName, dateModified, displayOrder, sourceType, addedAt, mediaType) VALUES ('folder-image', 'album', 'folder', 'content://folder-image', 'a.jpg', 5, 0, 'FOLDER', 6, 'IMAGE')")
            db.execSQL("INSERT INTO wallpapers (id, albumId, folderId, uri, fileName, dateModified, displayOrder, sourceType, addedAt, mediaType) VALUES ('direct', 'album', NULL, 'content://direct', 'b.jpg', 7, 1, 'DIRECT', 8, 'IMAGE')")
            db.execSQL("INSERT INTO wallpaper_queue VALUES (1, 'album', 'direct', 'HOME', 0, 9)")
            db.execSQL("INSERT INTO wallpaper_current VALUES ('album', 'HOME', 'direct', 10)")
            extra(db)
            db.version = 4
        }
    }
}
