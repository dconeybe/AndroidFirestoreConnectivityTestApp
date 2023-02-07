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

import android.app.Service
import android.content.Intent
import android.os.*
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

val ACTION_START_TEST = "com.google.firebase.firestore.connectivitytestapp.MainService.ACTION_START_TEST"

private val TOKEN = Binder()
private val EXTRA_TOKEN = "com.google.firebase.firestore.connectivitytestapp.MainService.EXTRA_TOKEN"

class MainService : Service() {

  private val logger = Logger("MainService")

  private lateinit var selfRef: WeakReference<MainService>
  private lateinit var mainServiceImpl: IMainServiceImpl
  private lateinit var connectivityTest: ConnectivityTest
  private var lastStartId: Int? = null

  override fun onCreate() {
    logger.onCreate()
    super.onCreate()
    selfRef = WeakReference(this)
    mainServiceImpl = IMainServiceImpl(Handler(Looper.getMainLooper()), selfRef)
    connectivityTest = ConnectivityTest(applicationContext)
    connectivityTest.onCreate()
  }

  override fun onDestroy() {
    logger.onDestroy()
    connectivityTest.onDestroy()
    selfRef.clear()
    super.onDestroy()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    logger.onStartCommand(intent, flags, startId)
    lastStartId = startId
    return START_NOT_STICKY
  }

  override fun onBind(intent: Intent?): IBinder {
    logger.onBind(intent)
    return mainServiceImpl
  }

  override fun onUnbind(intent: Intent?): Boolean {
    logger.onUnbind(intent)
    return super.onUnbind(intent)
  }

  override fun onRebind(intent: Intent?) {
    logger.onRebind(intent)
    super.onRebind(intent)
  }

  @MainThread
  internal fun startConnectivityTest() {
    logger.log("startConnectivityTest()")
    if (connectivityTest.running) {
      return;
    }

    val intent = Intent(this, this::class.java);
    val componentName = startService(intent)
    if (componentName === null) {
      throw IllegalStateException("startService($intent) returned null")
    }

    Thread(connectivityTest).start()
  }

  @MainThread
  internal fun cancelConnectivityTest() {
    logger.log("cancelConnectivityTest()")
    connectivityTest.cancel()
    lastStartId?.also { stopSelf(it) }
  }

  @MainThread
  internal fun isConnectivityTestRunning(): Boolean {
    logger.log("isConnectivityTestRunning()")
    return connectivityTest.running
  }
}

private class IMainServiceImpl(val mainHandler: Handler, val serviceRef: WeakReference<MainService>) : IMainService.Stub() {

  private val logger = Logger("IMainServiceImpl");

  @AnyThread
  override fun startConnectivityTest() {
    logger.log("startConnectivityTest()")
    runOnMainThreadWithService { it.startConnectivityTest() }
  }

  @AnyThread
  override fun cancelConnectivityTest() {
    logger.log("cancelConnectivityTest()")
    runOnMainThreadWithService { it.cancelConnectivityTest() }
  }

  @AnyThread
  override fun isConnectivityTestRunning(): Boolean {
    logger.log("isConnectivityTestRunning()")
    val condition = ConditionVariable()
    val result = AtomicBoolean(false)
    runOnMainThreadWithService {
      result.set(it.isConnectivityTestRunning())
      condition.open()
    }
    condition.block()
    return result.get()
  }

  @AnyThread
  private fun runOnMainThreadWithService(operation: (MainService) -> Unit) {
    val exception = arrayOfNulls<RuntimeException>(1)
    val runnable: () -> Unit = {
      val service = serviceRef.get()
      if (service === null) {
        @Suppress("ThrowableNotThrown")
        exception[0] = IllegalStateException("service has terminated")
      } else {
        try {
          operation(service)
        } catch (e: RuntimeException) {
          exception[0] = e
        }
      }
    }

    val postCondition = ConditionVariable()
    val postSuccessful = mainHandler.post {
      try {
        runnable()
      } finally {
        postCondition.open()
      }
    }

    if (! postSuccessful) {
      throw IllegalStateException("unable to post message to handler")
    }

    if (! postCondition.block(2000L)) {
      throw IllegalStateException("timeout waiting for main thread to process the request")
    }
    
    exception[0]?.also { throw it }
  }

}
