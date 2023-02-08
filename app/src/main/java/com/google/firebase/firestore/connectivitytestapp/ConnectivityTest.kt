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

import android.content.Context
import android.os.SystemClock
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread

@WorkerThread
class ConnectivityTest(private val context: Context) : Runnable {

  private val logger = Logger("ConnectivityTest")
  private val persistence = MainProviderInterface(context)

  private val lock = Object()
  private var runThread: Thread? = null
  private var _runningTestId: Long? = null
  private var listener: ConnectivityTestListener? = null

  val running get() = synchronized(lock) { runThread !== null }
  val runningTestId get() = synchronized(lock) { _runningTestId }

  fun onCreate(listener: ConnectivityTestListener?) {
    logger.onCreate()
    this.listener = listener
    persistence.open()
  }

  fun onDestroy() {
    logger.onDestroy()
    this.listener = null
    persistence.close()
  }

  override fun run() {
    val currentThread = Thread.currentThread()
    synchronized(lock) {
      if (runThread !== null) {
        throw IllegalStateException("run() called while already running")
      }
      runThread = currentThread
      _runningTestId = null
    }

    try {
      // Notify the listener that the test has started.
      listener?.onStateChange(this)

      // Register the test and get a test ID.
      val testId = persistence.registerTest(System.currentTimeMillis())
      synchronized(lock) {
        _runningTestId = testId
      }

      // Notify the listener that the test ID has been acquired.
      listener?.onStateChange(this)

      val log = SystemClock.elapsedRealtime().let { startTime ->
        val logFunction: (String) -> Unit = { message ->
          logger.log("[Test ID $testId] $message")
          val timeOffsetMs = SystemClock.elapsedRealtime() - startTime
          persistence.insertTestLog(testId, message, timeOffsetMs)
        }
        logFunction
      }

      // Run the actual test.
      try {
        log("Test starting")
        runTest(log)
        log("Test completed successfully")
      } catch (e: InterruptedException) {
        log("Test cancelled")
      } catch (e: Exception) {
        log("Test failed: $e")
        logger.logException("Test with ID $testId FAILED", e)
      }
    } catch (e: InterruptedException) {
      logger.log("Test cancelled before acquiring a Test ID")
    } finally {
      synchronized(lock) {
        runThread = null
        _runningTestId = null
      }

      // Notify the listener that the test has completed.
      listener?.onStateChange(this)
    }
  }

  private fun runTest(log: (String) -> Unit) {
    val client = FirestoreClient.create(context, log)
    client.runAggregationQuery()

    client.newListenStream().use { listenStream ->
      listenStream.addTarget()
      Thread.sleep(5000L)
    }
  }

  @AnyThread
  fun cancel() {
    logger.log("cancel()")
    synchronized(lock) {
      runThread?.interrupt()
    }
  }

}

interface ConnectivityTestListener {

  fun onStateChange(instance: ConnectivityTest)

}
