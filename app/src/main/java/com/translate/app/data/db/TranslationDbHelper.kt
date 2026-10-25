package com.translate.app.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TranslationDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "simple_translate.db"
        const val DATABASE_VERSION = 1

        const val TABLE_NAME = "translations"
        const val COLUMN_ID = "id"
        const val COLUMN_SOURCE_TEXT = "source_text"
        const val COLUMN_TRANSLATED_TEXT = "translated_text"
        const val COLUMN_SOURCE_LANG = "source_lang"
        const val COLUMN_TARGET_LANG = "target_lang"
        const val COLUMN_ENGINE_TYPE = "engine_type"
        const val COLUMN_IS_FAVORITE = "is_favorite"
        const val COLUMN_TIMESTAMP = "timestamp"

        @Volatile
        private var instance: TranslationDbHelper? = null

        fun getInstance(context: Context): TranslationDbHelper {
            return instance ?: synchronized(this) {
                instance ?: TranslationDbHelper(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createSql = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SOURCE_TEXT TEXT NOT NULL,
                $COLUMN_TRANSLATED_TEXT TEXT NOT NULL,
                $COLUMN_SOURCE_LANG TEXT NOT NULL,
                $COLUMN_TARGET_LANG TEXT NOT NULL,
                $COLUMN_ENGINE_TYPE TEXT NOT NULL,
                $COLUMN_IS_FAVORITE INTEGER NOT NULL DEFAULT 0,
                $COLUMN_TIMESTAMP INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createSql)
        db.execSQL("CREATE INDEX idx_trans_time ON $TABLE_NAME ($COLUMN_TIMESTAMP DESC)")
        db.execSQL("CREATE INDEX idx_trans_fav ON $TABLE_NAME ($COLUMN_IS_FAVORITE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun insertOrUpdate(record: TranslationRecord): Long {
        val db = writableDatabase
        // 检查是否存在完全相同的源文本与语种对，存在则更新时间戳和翻译结果，避免刷屏
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_ID, COLUMN_IS_FAVORITE),
            "$COLUMN_SOURCE_TEXT = ? AND $COLUMN_SOURCE_LANG = ? AND $COLUMN_TARGET_LANG = ?",
            arrayOf(record.sourceText.trim(), record.sourceLang, record.targetLang),
            null, null, null
        )
        val existingId = if (cursor.moveToFirst()) cursor.getLong(0) else -1L
        val existingFav = if (existingId != -1L) cursor.getInt(1) == 1 else record.isFavorite
        cursor.close()

        val values = ContentValues().apply {
            put(COLUMN_SOURCE_TEXT, record.sourceText.trim())
            put(COLUMN_TRANSLATED_TEXT, record.translatedText.trim())
            put(COLUMN_SOURCE_LANG, record.sourceLang)
            put(COLUMN_TARGET_LANG, record.targetLang)
            put(COLUMN_ENGINE_TYPE, record.engineType)
            put(COLUMN_IS_FAVORITE, if (existingFav) 1 else 0)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        }

        return if (existingId != -1L) {
            db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(existingId.toString()))
            existingId
        } else {
            db.insert(TABLE_NAME, null, values)
        }
    }

    fun toggleFavorite(id: Long, isFavorite: Boolean): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IS_FAVORITE, if (isFavorite) 1 else 0)
        }
        return db.update(TABLE_NAME, values, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun deleteRecord(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, "$COLUMN_ID = ?", arrayOf(id.toString()))
    }

    fun clearAll(keepFavorites: Boolean = true): Int {
        val db = writableDatabase
        return if (keepFavorites) {
            db.delete(TABLE_NAME, "$COLUMN_IS_FAVORITE = 0", null)
        } else {
            db.delete(TABLE_NAME, null, null)
        }
    }

    fun clearFavorites(): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_IS_FAVORITE, 0)
        }
        return db.update(TABLE_NAME, values, "$COLUMN_IS_FAVORITE = 1", null)
    }

    fun queryRecords(onlyFavorites: Boolean = false, keyword: String = ""): List<TranslationRecord> {
        val db = readableDatabase
        val selection = StringBuilder()
        val args = mutableListOf<String>()

        if (onlyFavorites) {
            selection.append("$COLUMN_IS_FAVORITE = 1")
        }

        if (keyword.isNotBlank()) {
            if (selection.isNotEmpty()) selection.append(" AND ")
            selection.append("($COLUMN_SOURCE_TEXT LIKE ? OR $COLUMN_TRANSLATED_TEXT LIKE ?)")
            val pattern = "%$keyword%"
            args.add(pattern)
            args.add(pattern)
        }

        val cursor = db.query(
            TABLE_NAME,
            null,
            if (selection.isEmpty()) null else selection.toString(),
            if (args.isEmpty()) null else args.toTypedArray(),
            null,
            null,
            "$COLUMN_TIMESTAMP DESC",
            "100"
        )

        val list = mutableListOf<TranslationRecord>()
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID))
            val src = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_TEXT))
            val trans = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRANSLATED_TEXT))
            val sLang = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_LANG))
            val tLang = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TARGET_LANG))
            val eng = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ENGINE_TYPE))
            val fav = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_IS_FAVORITE)) == 1
            val time = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
            list.add(
                TranslationRecord(
                    id = id,
                    sourceText = src,
                    translatedText = trans,
                    sourceLang = sLang,
                    targetLang = tLang,
                    engineType = eng,
                    isFavorite = fav,
                    timestamp = time
                )
            )
        }
        cursor.close()
        return list
    }
}
