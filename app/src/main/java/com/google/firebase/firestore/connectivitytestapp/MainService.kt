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
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class MainService : Service() {

  private val logger = Logger("MainService")

  private lateinit var selfRef: WeakReference<MainService>
  private lateinit var mainServiceImpl: IMainServiceImpl
  private lateinit var connectivityTest: ConnectivityTest
  private var lastStartId: Int? = null
  private val listeners = mutableMapOf<Binder, IMainServiceListener>()

  override fun onCreate() {
    logger.onCreate()
    super.onCreate()
    selfRef = WeakReference(this)
    val mainHandler = Handler(Looper.getMainLooper())
    mainServiceImpl = IMainServiceImpl(mainHandler, selfRef)
    connectivityTest = ConnectivityTest(applicationContext)
    connectivityTest.onCreate(ConnectivityTestListenerImpl(mainHandler, selfRef))
  }

  override fun onDestroy() {
    logger.onDestroy()
    listeners.clear()
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
      return
    }

    val intent = Intent(this, this::class.java)
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

  @MainThread
  internal fun getRunningConnectivityTestId(): Long {
    logger.log("getRunningConnectivityTestId()")
    return connectivityTest.runningTestId ?: -1
  }

  @MainThread
  internal fun addListener(listener: IMainServiceListener) {
    listeners[Binder()] = listener
  }

  @MainThread
  internal fun removeListener(listener: IMainServiceListener?) {
    for (entry in listeners.entries) {
      if (entry.value === listener) {
        listeners.remove(entry.key)
        break
      }
    }
  }

  @MainThread
  private fun notifyListeners(op: (IMainServiceListener) -> Unit) {
    var deadListenerKeys: MutableList<Binder>? = null

    listeners.forEach { (key, listener) ->
      try {
        op(listener)
      } catch (e: RemoteException) {
        logger.warn("removing listener $listener because notifying it failed with $e")
        if (deadListenerKeys === null) {
          deadListenerKeys = mutableListOf()
        }
        deadListenerKeys!!.add(key)
      }
    }

    deadListenerKeys?.forEach { listeners.remove(it) }
  }

  @MainThread
  internal fun onConnectivityTestRunningStateChange() {
    notifyListeners { listener -> listener.onConnectivityTestRunningStateChange() }
  }
}

private class IMainServiceImpl(val mainHandler: Handler, val serviceRef: WeakReference<MainService>) : IMainService.Stub() {

  private val logger = Logger("IMainServiceImpl")

  @AnyThread
  override fun startConnectivityTest() {
    logger.log("startConnectivityTest()")
    runAsyncOnMainThreadWithService { it.startConnectivityTest() }
  }

  @AnyThread
  override fun cancelConnectivityTest() {
    logger.log("cancelConnectivityTest()")
    runAsyncOnMainThreadWithService { it.cancelConnectivityTest() }
  }

  @AnyThread
  override fun isConnectivityTestRunning(): Boolean {
    logger.log("isConnectivityTestRunning()")
    val condition = ConditionVariable()
    val result = AtomicBoolean(false)
    runAsyncOnMainThreadWithService {
      result.set(it.isConnectivityTestRunning())
      condition.open()
    }
    condition.block()
    return result.get()
  }

  override fun getRunningConnectivityTestId(): Long {
    logger.log("getRunningConnectivityTestId()")
    val condition = ConditionVariable()
    val result = AtomicLong(-1)
    runAsyncOnMainThreadWithService {
      result.set(it.getRunningConnectivityTestId())
      condition.open()
    }
    condition.block()
    return result.get()
  }

  override fun addListener(listener: IMainServiceListener?) {
    if (listener === null) {
      throw NullPointerException("listener==null")
    }
    runAsyncOnMainThreadWithService { it.addListener(listener) }
  }

  override fun removeListener(listener: IMainServiceListener?) {
    runAsyncOnMainThreadWithService { it.removeListener(listener) }
  }

  @AnyThread
  private fun runAsyncOnMainThreadWithService(operation: (MainService) -> Unit) {
    val postSucceeded = serviceRef.get().let { service ->
      if (service === null) false else mainHandler.post { operation(service) }
    }
    if (! postSucceeded) {
      logger.warn("posting to service failed")
    }
  }

}

private class ConnectivityTestListenerImpl(val mainHandler: Handler, val serviceRef: WeakReference<MainService>) : ConnectivityTestListener {

  override fun onStateChange(instance: ConnectivityTest) {
    serviceRef.get()?.also {service ->
      mainHandler.post {
        service.onConnectivityTestRunningStateChange()
      }
    }
  }

}
