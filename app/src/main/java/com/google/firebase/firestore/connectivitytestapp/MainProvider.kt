// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.connectivitytestapp

import android.content.ContentProvider
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.provider.BaseColumns
import java.util.*
import java.util.stream.Collectors
import kotlin.collections.HashSet

class MainProvider : ContentProvider() {

  private val logger = Logger("MainProvider")
  private lateinit var db: DbOpenHelper

  override fun attachInfo(context: Context, info: ProviderInfo) {
    logger.attachInfo(context, info)
    super.attachInfo(context, info)
    db = DbOpenHelper(context)
  }

  override fun onCreate(): Boolean {
    logger.onCreate()
    return true
  }

  override fun query(
    uri: Uri,
    projection: Array<out String>?,
    selection: String?,
    selectionArgs: Array<out String>?,
    sortOrder: String?
  ): Cursor {
    throw IllegalStateException("not implemented")
  }

  override fun getType(uri: Uri) = when (MainProviderUriMatcher.match(uri)) {
    MainProviderUriMatcher.TESTS -> MainProviderContract.Tests.CONTENT_TYPE
    MainProviderUriMatcher.TESTS_ID -> MainProviderContract.Tests.CONTENT_TYPE_ITEM
    MainProviderUriMatcher.TEST_LOGS -> MainProviderContract.TestLogs.CONTENT_TYPE
    MainProviderUriMatcher.TEST_LOGS_ID -> MainProviderContract.TestLogs.CONTENT_TYPE_ITEM
    else -> null
  }

  override fun insert(uri: Uri, values: ContentValues?) = when(MainProviderUriMatcher.match(uri)) {
    MainProviderUriMatcher.TESTS -> insertTest(values)
    MainProviderUriMatcher.TEST_LOGS -> insertTestLog(values)
    else -> throw IllegalArgumentException("unsupported uri for insert: $uri")
  }

  private fun insertTest(values: ContentValues?): Uri {
    // Verify that the start time was specified in the values.
    val startTime = values?.getAsLong(MainProviderContract.Tests.START_TIME_MS_UTC)
    if (startTime === null) {
      throw IllegalArgumentException("start time must be specified when inserting a test")
    }

    // Verify that no other keys were given in the values.
    HashSet(values.keySet()).also {
      it.remove(MainProviderContract.Tests.START_TIME_MS_UTC)
      if (it.isNotEmpty()) {
        throw IllegalArgumentException("unsupported keys when inserting a test: "
          + it.stream().sorted().collect(Collectors.joining(", ")))
      }
    }

    // Insert the row into the database.
    val insertedRowId = ContentValues().let { values ->
      values.put(MainProviderContract.Tests.START_TIME_MS_UTC, startTime)
      db.writableDatabase.insertOrThrow(MainProviderContract.Tests.TABLE_NAME, null, values)
    }

    if (insertedRowId == -1L) {
      throw IllegalStateException("unable to insert row; inserted ID is -1")
    }

    val insertedRowUri = ContentUris.withAppendedId(MainProviderContract.Tests.CONTENT_URI, insertedRowId)
    context?.contentResolver?.notifyChange(insertedRowUri, null)
    return insertedRowUri
  }

  private fun insertTestLog(values: ContentValues?): Uri {
    // Verify that the required values were specified.
    val testId = values?.getAsLong(MainProviderContract.TestLogs.TEST_ID)
    val timeOffsetMs = values?.getAsLong(MainProviderContract.TestLogs.TIME_OFFSET_MS)
    val message = values?.getAsString(MainProviderContract.TestLogs.MESSAGE)
    if (testId === null) {
      throw IllegalArgumentException("test ID must be specified when inserting a test log")
    }

    // Verify that no other keys were given in the values.
    HashSet(values.keySet()).also {
      it.remove(MainProviderContract.TestLogs.TEST_ID)
      it.remove(MainProviderContract.TestLogs.TIME_OFFSET_MS)
      it.remove(MainProviderContract.TestLogs.MESSAGE)
      if (it.isNotEmpty()) {
        throw IllegalArgumentException("unsupported keys when inserting a test log: "
            + it.stream().sorted().collect(Collectors.joining(", ")))
      }
    }

    // Insert the row into the database.
    val insertedRowId = ContentValues().let {insertValues ->
      insertValues.put(MainProviderContract.TestLogs.TEST_ID, testId)
      timeOffsetMs?.also { insertValues.put(MainProviderContract.TestLogs.TIME_OFFSET_MS, it) }
      message?.also { insertValues.put(MainProviderContract.TestLogs.MESSAGE, it) }
      db.writableDatabase.insertOrThrow(MainProviderContract.TestLogs.TABLE_NAME, null, insertValues)
    }

    if (insertedRowId == -1L) {
      throw IllegalStateException("unable to insert row; inserted ID is -1")
    }

    val insertedRowUri = ContentUris.withAppendedId(MainProviderContract.TestLogs.CONTENT_URI, insertedRowId)
    context?.contentResolver?.notifyChange(insertedRowUri, null)
    return insertedRowUri
  }

  override fun delete(p0: Uri, p1: String?, p2: Array<out String>?): Int {
    throw IllegalStateException("not implemented")
  }

  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = when(MainProviderUriMatcher.match(uri)) {
    MainProviderUriMatcher.TESTS_ID -> updateTest(uri, values, selection, selectionArgs)
    else -> throw IllegalArgumentException("unsupported uri for update: $uri")
  }

  private fun updateTest(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
    if (selection !== null) {
      throw IllegalArgumentException("selection is not supported when updating a test")
    }
    if (selectionArgs !== null) {
      throw IllegalArgumentException("selection arguments is not supported when updating a test")
    }

    // Verify that the end time was specified in the values.
    val endTime = values?.getAsLong(MainProviderContract.Tests.END_TIME_MS_UTC)
    if (endTime === null) {
      throw IllegalArgumentException("end time must be specified when updating a test")
    }

    // Verify that no other keys were given in the values.
    HashSet(values.keySet()).also {
      it.remove(MainProviderContract.Tests.END_TIME_MS_UTC)
      if (it.isNotEmpty()) {
        throw IllegalArgumentException("unsupported keys when updating a test: "
            + it.stream().sorted().collect(Collectors.joining(", ")))
      }
    }

    // Update the row in the database.
    val id = ContentUris.parseId(uri)
    val numRowsAffected = ContentValues().let { values ->
      values.put(MainProviderContract.Tests.END_TIME_MS_UTC, endTime)
      @Suppress("NAME_SHADOWING") val selection = "${MainProviderContract.Tests._ID} = $id"
      db.writableDatabase.update(MainProviderContract.Tests.TABLE_NAME, values, selection, null)
    }

    if (numRowsAffected > 0) {
      context?.contentResolver?.notifyChange(uri, null)
    }

    return numRowsAffected
  }

}

class MainProviderInterface(private val context: Context) {

  private var client: ContentProviderClient? = null

  fun open() {
    if (client !== null) {
      throw IllegalStateException("open() cannot be called when already opened")
    }
    val name = MainProvider::class.qualifiedName!!
    client = context.contentResolver.acquireContentProviderClient(name)
    if (client === null) {
      throw RuntimeException("acquireContentProviderClient($name) returned null")
    }
  }

  fun close() {
    val client = this.client
    this.client = null
    if (client !== null) {
      client.close()
    }
  }

  fun registerTest(startTimeMillis: Long): Long {
    val values = ContentValues()
    values.put(MainProviderContract.Tests.START_TIME_MS_UTC, startTimeMillis)
    val insertedItemUri = withClient { client ->
      client.insert(MainProviderContract.Tests.CONTENT_URI, values)
    }
    if (insertedItemUri === null) {
      throw RuntimeException("insert() returned null")
    }
    val id = ContentUris.parseId(insertedItemUri)
    if (id == -1L) {
      throw RuntimeException("insert() returned a Uri with an invalid ID: $insertedItemUri")
    }
    return id
  }

  fun updateTestEndTime(testId: Long, endTimeMillis: Long) {
    val uri = ContentUris.withAppendedId(MainProviderContract.Tests.CONTENT_URI, testId)
    val values = ContentValues()
    values.put(MainProviderContract.Tests.END_TIME_MS_UTC, endTimeMillis)
    withClient { client ->
      client.update(uri, values, null, null)
    }
  }

  fun insertTestLog(testId: Long, message: String, timeoffsetMs: Long) {
    val values = ContentValues()
    values.put(MainProviderContract.TestLogs.TEST_ID, testId)
    values.put(MainProviderContract.TestLogs.TIME_OFFSET_MS, timeoffsetMs)
    values.put(MainProviderContract.TestLogs.MESSAGE, message)
    val insertedItemUri = withClient { client ->
      client.insert(MainProviderContract.TestLogs.CONTENT_URI, values)
    }
    if (insertedItemUri === null) {
      throw RuntimeException("insert() returned null")
    }
  }

  private fun <T> withClient(callback: java.util.function.Function<ContentProviderClient, T>): T {
    client.also {
      if (it === null) {
        throw IllegalStateException("client has not been opened")
      }
      return callback.apply(it)
    }
  }

}

private object MainProviderContract {
  const val AUTHORITY = "com.google.firebase.firestore.connectivitytestapp.MainProvider"
  val CONTENT_URI: Uri = Uri.parse("content://${AUTHORITY}")

  object Tests {
    const val TABLE_NAME = "tests"
    val CONTENT_URI: Uri = MainProviderContract.CONTENT_URI.buildUpon().appendPath(TABLE_NAME).build()
    const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$TABLE_NAME"
    const val CONTENT_TYPE_ITEM = "vnd.android.cursor.item/vnd.$AUTHORITY.$TABLE_NAME"

    const val _ID = BaseColumns._ID
    const val START_TIME_MS_UTC = "start_time_ms_utc"
    const val END_TIME_MS_UTC = "end_time_ms_utc"
  }

  object TestLogs {
    const val TABLE_NAME = "test_logs"
    val CONTENT_URI: Uri = MainProviderContract.CONTENT_URI.buildUpon().appendPath(TABLE_NAME).build()
    const val CONTENT_TYPE = "vnd.android.cursor.dir/vnd.$AUTHORITY.$TABLE_NAME"
    const val CONTENT_TYPE_ITEM = "vnd.android.cursor.item/vnd.$AUTHORITY.$TABLE_NAME"

    const val _ID = BaseColumns._ID
    const val TEST_ID = "test_id"
    const val TIME_OFFSET_MS = "time_offset_ms"
    const val MESSAGE = "message"
  }
}

private val MainProviderUriMatcher = object {

  val TESTS = 1
  val TESTS_ID = 2
  val TEST_LOGS = 3
  val TEST_LOGS_ID = 4

  private val matcher: UriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
    addURI(MainProviderContract.AUTHORITY, MainProviderContract.Tests.TABLE_NAME, TESTS)
    addURI(MainProviderContract.AUTHORITY, MainProviderContract.Tests.TABLE_NAME + "/#", TESTS_ID)
    addURI(MainProviderContract.AUTHORITY, MainProviderContract.TestLogs.TABLE_NAME, TEST_LOGS)
    addURI(MainProviderContract.AUTHORITY, MainProviderContract.TestLogs.TABLE_NAME + "/#", TEST_LOGS_ID)
  }

  fun match(uri: Uri): Int? = matcher.match(uri).let {
    if (it != UriMatcher.NO_MATCH) it else null
  }

}

private class DbOpenHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

  private val logger = Logger("DbOpenHelper")

  init {
    setWriteAheadLoggingEnabled(true)
  }

  override fun onConfigure(db: SQLiteDatabase) {
    logger.log("onConfigure")
    super.onConfigure(db)
    db.setForeignKeyConstraintsEnabled(true)
    db.setLocale(Locale.ENGLISH)
  }

  override fun onCreate(db: SQLiteDatabase) {
    logger.onCreate()
    db.execSQL("""
      CREATE TABLE ${MainProviderContract.Tests.TABLE_NAME} (
        ${MainProviderContract.Tests._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
        ${MainProviderContract.Tests.START_TIME_MS_UTC} INTEGER,
        ${MainProviderContract.Tests.END_TIME_MS_UTC} INTEGER
      )      
    """)
    db.execSQL("""
      CREATE TABLE ${MainProviderContract.TestLogs.TABLE_NAME} (
        ${MainProviderContract.TestLogs._ID} INTEGER PRIMARY KEY AUTOINCREMENT,
        ${MainProviderContract.TestLogs.TEST_ID} INTEGER,
        ${MainProviderContract.TestLogs.TIME_OFFSET_MS} INTEGER,
        ${MainProviderContract.TestLogs.MESSAGE} TEXT,
        FOREIGN KEY (${MainProviderContract.TestLogs.TEST_ID})
          REFERENCES ${MainProviderContract.Tests.TABLE_NAME}(
            ${MainProviderContract.Tests._ID}
          )
      )      
    """)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    logger.log("onUpgrade() oldVersion=$oldVersion newVersion=$newVersion")
    db.execSQL("DROP TABLE IF EXISTS ${MainProviderContract.Tests.TABLE_NAME}")
    db.execSQL("DROP TABLE IF EXISTS ${MainProviderContract.TestLogs.TABLE_NAME}")
    onCreate(db)
  }

  companion object {
    const val DATABASE_NAME = "MainProvider"
    const val DATABASE_VERSION = 2
  }

}
