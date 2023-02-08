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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.connectivitytestapp.databinding.FragmentMainBinding
import java.lang.ref.WeakReference

private val KEY_CONNECTIVITY_TEST_AUTO_STARTED = "${MainFragment::class.qualifiedName} connectivityTestAutoStarted"

class MainFragment : Fragment() {

  private val logger = Logger("MainFragment")

  private lateinit var selfRef: WeakReference<MainFragment>
  private lateinit var serviceConnection: ConnectivityTestServiceConnection
  private lateinit var workHandlerThread: HandlerThread
  private lateinit var workHandler: Handler

  private var connectivityTestAutoStarted = false

  private var binding: FragmentMainBinding? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.onCreate()
    super.onCreate(savedInstanceState)

    savedInstanceState?.also { inState ->
      if (inState.containsKey(KEY_CONNECTIVITY_TEST_AUTO_STARTED)) {
        connectivityTestAutoStarted = inState.getBoolean(KEY_CONNECTIVITY_TEST_AUTO_STARTED)
      }
    }

    selfRef = WeakReference(this)
    val mainHandler = Handler(Looper.getMainLooper())
    serviceConnection = ConnectivityTestServiceConnection(mainHandler, selfRef)
    workHandlerThread = HandlerThread("MainFragment-WorkHandler")
    workHandlerThread.start()
    workHandler = Handler(workHandlerThread.looper)
  }

  override fun onDestroy() {
    logger.onDestroy()
    workHandlerThread.quitSafely()
    selfRef.clear()
    super.onDestroy()
  }

  override fun onAttach(context: Context) {
    logger.onAttach(context)
    super.onAttach(context)
  }

  override fun onDetach() {
    logger.onDetach()
    super.onDetach()
  }

  override fun onStart() {
    logger.onStart()
    super.onStart()

    val serviceIntent = Intent(requireContext(), MainService::class.java)
    val bindSuccess = requireContext().bindService(serviceIntent, serviceConnection,
      AppCompatActivity.BIND_AUTO_CREATE
    )
    if (! bindSuccess) {
      throw IllegalStateException("unable to bind to service $serviceIntent")
    }
  }

  override fun onStop() {
    logger.onStop()

    requireContext().unbindService(serviceConnection)

    super.onStop()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(KEY_CONNECTIVITY_TEST_AUTO_STARTED, connectivityTestAutoStarted)
  }

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    logger.onCreateView()
    binding = FragmentMainBinding.inflate(inflater, container, false)
    return binding!!.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    logger.onViewCreated()
    super.onViewCreated(view, savedInstanceState)

    syncUiWithConnectivityTest()

    binding!!.startTestButton.setOnClickListener { serviceConnection.startTest() }
    binding!!.stopTestButton.setOnClickListener { serviceConnection.cancelTest() }
  }

  override fun onDestroyView() {
    logger.onDestroyView()
    binding = null
    super.onDestroyView()
  }

  @MainThread
  internal fun onConnectivityTestServiceConnectionStateChange() {
    if (!connectivityTestAutoStarted && serviceConnection.isConnected()) {
      logger.log("Auto-starting the connectivity test")
      serviceConnection.startTest()
      connectivityTestAutoStarted = true
    }

    syncUiWithConnectivityTest()
  }

  @MainThread
  internal fun onConnectivityTestRunningStateChange() {
    syncUiWithConnectivityTest()
  }

  @MainThread
  private fun syncUiWithConnectivityTest() {
    val binding = this.binding ?: return
    if (! serviceConnection.isConnected()) {
      binding.startTestButton.isEnabled = false
      binding.stopTestButton.isEnabled = false
      binding.testIdText.visibility = View.INVISIBLE
    } else {
      serviceConnection.isTestRunning().also {isTestRunning ->
        binding.startTestButton.isEnabled = !isTestRunning
        binding.stopTestButton.isEnabled = isTestRunning
      }
      serviceConnection.getRunningTestId().also { runningTestId ->
        binding.testIdText.visibility = if (runningTestId == -1L)  View.INVISIBLE else View.VISIBLE
        binding.testIdText.text = "Connectivity test with ID $runningTestId is running"
      }
    }
  }

}

private class ConnectivityTestServiceConnection(private val mainHandler: Handler, private val fragmentRef: WeakReference<MainFragment>) : IMainServiceListener.Stub(), ServiceConnection {

  private val logger = Logger("ConnectivityTestServiceConnection")
  private var connection: IMainService? = null

  @MainThread
  override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    logger.onServiceConnected(name, service)
    val connection = IMainService.Stub.asInterface(service)
    connection.addListener(this)
    this.connection = connection
    notifyFragmentOfConnectionStateChange()
  }

  @MainThread
  override fun onServiceDisconnected(name: ComponentName?) {
    logger.onServiceDisconnected(name)
    this.connection = null
    notifyFragmentOfConnectionStateChange()
  }

  @MainThread
  fun isConnected(): Boolean {
    return this.connection !== null
  }

  @AnyThread
  fun startTest() = connection?.startConnectivityTest()

  @AnyThread
  fun cancelTest() = connection?.cancelConnectivityTest()

  @AnyThread
  fun isTestRunning() = connection?.isConnectivityTestRunning ?: false

  @AnyThread
  fun getRunningTestId() = connection?.runningConnectivityTestId ?: -1L

  @AnyThread
  private fun notifyFragmentOfConnectionStateChange() {
    fragmentRef.get()?.also {fragment ->
      mainHandler.post {
        fragment.onConnectivityTestServiceConnectionStateChange()
      }
    }
  }

  @AnyThread
  override fun onConnectivityTestRunningStateChange() {
    fragmentRef.get()?.also { fragment ->
      mainHandler.post {
        fragment.onConnectivityTestRunningStateChange()
      }
    }
  }

}
