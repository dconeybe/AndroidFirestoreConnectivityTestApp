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

import io.grpc.android.AndroidChannelBuilder
import android.content.Context
import android.os.ConditionVariable
import android.os.SystemClock
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import com.google.common.collect.ImmutableList
import com.google.common.collect.Streams
import com.google.firestore.v1.FirestoreGrpc
import com.google.firestore.v1.FirestoreGrpc.FirestoreBlockingStub
import com.google.firestore.v1.FirestoreGrpc.FirestoreStub
import com.google.firestore.v1.ListenRequest
import com.google.firestore.v1.ListenResponse
import com.google.firestore.v1.RunAggregationQueryRequest
import com.google.firestore.v1.RunQueryRequest
import com.google.firestore.v1.StructuredAggregationQuery
import com.google.firestore.v1.StructuredQuery
import io.grpc.*

@WorkerThread
class ConnectivityTest(private val context: Context) : Runnable {

  private val logger = Logger("ConnectivityTest")
  private val persistence = MainProviderInterface(context)

  private val lock = Object()
  private var runThread: Thread? = null
  private var runningTestCancelled = false
  private var runningTestId: Long? = null

  val running get() = synchronized(lock) { runThread !== null }

  fun onCreate() {
    logger.onCreate()
    persistence.open()
  }

  fun onDestroy() {
    logger.onDestroy()
    persistence.close()
  }

  override fun run() {
    val currentThread = Thread.currentThread()
    synchronized(lock) {
      if (runThread !== null) {
        throw IllegalStateException("run() called while already running")
      }
      runThread = currentThread
      runningTestCancelled = false
    }

    try {
      runAfterRegisteringRunThread()
    } catch (e: TestCancelledException) {
      logger.log("Test with ID $runningTestId was cancelled")
    } catch (e: InterruptedException) {
      synchronized(lock) {
        if (runningTestCancelled) {
          logger.log("Test with ID $runningTestId was cancelled")
        } else {
          throw e
        }
      }
    } finally {
      synchronized(lock) {
        if (runThread !== currentThread) {
          throw IllegalStateException("internal error: runThread changed during run()")
        }
        runThread = null
        runningTestId = null
      }
    }
  }

  private fun runAfterRegisteringRunThread() {
    val testId = persistence.registerTest(System.currentTimeMillis())
    synchronized(lock) {
      runningTestId = testId
    }

    val startTime = SystemClock.elapsedRealtime()
    val logFunction: (String) -> Unit = {message ->
      logger.log(message)
      val timeOffsetMs = SystemClock.elapsedRealtime() - startTime
      persistence.insertTestLog(testId, message, timeOffsetMs)
    }
    try {
      logFunction("Starting test with ID $testId")
      runAfterRegisteringTest(logFunction)
      logFunction("Test with ID $testId completed successfully")
    } catch (e: Exception) {
      logger.logException("Test with ID $testId FAILED", e)
      val timeOffsetMs = SystemClock.elapsedRealtime() - startTime
      persistence.insertTestLog(testId, "Test with ID $testId FAILED: $e", timeOffsetMs)
    }
  }

  private fun runAfterRegisteringTest(log: (String) -> Unit) {
    val client = FirestoreClient.create(context, log)
    client.runAggregationQuery()

    client.newListenStream().use { listenStream ->
      listenStream.addTarget()
      Thread.sleep(5000L)
    }
  }

  private fun throwIfCancelled() {
    synchronized(lock) {
      if (runningTestCancelled) {
        Thread.interrupted() // clear the "interrupted" flag
        throw TestCancelledException()
      }
    }
  }

  @AnyThread
  fun cancel() {
    logger.log("cancel()")
    synchronized(lock) {
      runningTestCancelled = true
      runThread?.interrupt()
    }
  }

}

private class TestCancelledException : Exception()
