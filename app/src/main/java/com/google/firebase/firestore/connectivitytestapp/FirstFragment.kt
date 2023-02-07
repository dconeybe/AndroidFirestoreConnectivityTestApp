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
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import com.google.firebase.firestore.connectivitytestapp.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment(), FirstFragmentInterface {

  private val logger = Logger("FirstFragment");
  private lateinit var serviceConnection: ConnectivityTestServiceConnection
  private lateinit var workHandlerThread: HandlerThread
  private lateinit var workHandler: Handler

  private var _binding: FragmentFirstBinding? = null
  private val binding get() = _binding!!

  override fun onCreate(savedInstanceState: Bundle?) {
    logger.onCreate()
    super.onCreate(savedInstanceState)
    serviceConnection = ConnectivityTestServiceConnection()
    workHandlerThread = HandlerThread("FirstFragment-WorkHandler")
    workHandlerThread.start()
    workHandler = Handler(workHandlerThread.looper)
  }

  override fun onDestroy() {
    logger.onDestroy()
    workHandlerThread.quitSafely()
    super.onDestroy()
  }

  override fun onAttach(context: Context) {
    logger.onAttach(context)
    super.onAttach(context)
    (activity as MainActivityInterface).setFirstFragment(this)
  }

  override fun onDetach() {
    logger.onDetach()
    (activity as MainActivityInterface).clearFirstFragment(this)
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

  override fun onCreateView(
    inflater: LayoutInflater, container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    _binding = FragmentFirstBinding.inflate(inflater, container, false)
    return binding.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    binding.buttonFirst.setOnClickListener {
      findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    _binding = null
  }

  @MainThread
  override fun onFabClick() {
    logger.log("onFabClick()")
    workHandler.post {
      if (serviceConnection.isTestRunning()) {
        serviceConnection.cancelTest()
      } else {
        serviceConnection.startTest()
      }
    }
  }

}

private class ConnectivityTestServiceConnection : ServiceConnection {

  private val logger = Logger("ConnectivityTestServiceConnection")
  private var connection: IMainService? = null

  override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    logger.onServiceConnected(name, service)
    val connection = IMainService.Stub.asInterface(service)
    this.connection = connection
    Thread() { startTest() }.start()
  }

  override fun onServiceDisconnected(name: ComponentName?) {
    logger.onServiceDisconnected(name)
    this.connection = null
  }

  @WorkerThread
  fun startTest() = connection?.startConnectivityTest()

  @WorkerThread
  fun cancelTest() = connection?.cancelConnectivityTest()

  @WorkerThread
  fun isTestRunning() = connection?.isConnectivityTestRunning() ?: false

}

interface FirstFragmentInterface {

  @MainThread
  fun onFabClick()

}
