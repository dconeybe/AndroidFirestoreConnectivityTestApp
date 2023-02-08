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
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.os.IBinder
import android.util.Log
import android.view.MenuItem

private const val TAG = "FstConnTestApp";

class Logger(val tag: String) {

  fun onCreate() {
    log("onCreate()")
  }

  fun onDestroy() {
    log("onDestroy()")
  }

  fun onStart() {
    log("onStart()")
  }

  fun onStop() {
    log("onStop()")
  }

  fun onResume() {
    log("onResume()")
  }

  fun onPause() {
    log("onPause()")
  }

  fun onAttach(context: Context) {
    log("onAttach() context=${context::class.qualifiedName}")
  }

  fun onDetach() {
    log("onDetach()")
  }

  fun onCreateView() {
    log("onCreateView()")
  }

  fun onViewCreated() {
    log("onViewCreated()")
  }

  fun onDestroyView() {
    log("onDestroyView()")
  }

  fun onOptionsItemSelected(item: MenuItem) {
    log("onOptionsItemSelected() item=$item")
  }

  fun onSupportNavigateUp() {
    log("onSupportNavigateUp()")
  }

  fun onStartCommand(intent: Intent?, flags: Int, startId: Int) {
    log("onStartCommand() intent=$intent "
        + "flags=${friendlyNameFromServiceStartFlags(flags)} startId=$startId")
  }

  fun onBind(intent: Intent?) {
    log("onBind() intent=$intent")
  }

  fun onUnbind(intent: Intent?) {
    log("onUnbind() intent=$intent")
  }

  fun onRebind(intent: Intent?) {
    log("onRebind() intent=$intent")
  }

  fun onServiceConnected(name: ComponentName?, service: IBinder?) {
    log("onServiceConnected() name=$name service=$service")
  }

  fun onServiceDisconnected(name: ComponentName?) {
    log("onServiceDisconnected() name=$name")
  }

  fun attachInfo(context: Context, info: ProviderInfo) {
    log("attachInfo() context=${context::class.qualifiedName} info=$info")
  }

  fun log(message: String) {
    Log.i(TAG, "$tag $message")
  }

  fun warn(message: String) {
    Log.w(TAG, "$tag $message")
  }

  fun logException(message: String, exception: Exception) {
    Log.e(TAG, message, exception)
  }

}


private fun friendlyNameFromServiceStartFlags(flags: Int): String {
  val sb = StringBuilder()
  sb.append(flags)

  var flagsFound = 0
  val flagsFoundNames = mutableListOf<String>()

  if ((flags and Service.START_FLAG_REDELIVERY) == Service.START_FLAG_REDELIVERY) {
    flagsFound = flagsFound or Service.START_FLAG_REDELIVERY
    flagsFoundNames.add("START_FLAG_REDELIVERY")
  }

  if ((flags and Service.START_FLAG_RETRY) == Service.START_FLAG_RETRY) {
    flagsFound = flagsFound or Service.START_FLAG_RETRY
    flagsFoundNames.add("START_FLAG_RETRY")
  }

  val unnamedFlags = flags and flagsFound.inv()
  if (unnamedFlags != 0) {
    flagsFoundNames.add(unnamedFlags.toString())
  }

  if (flagsFoundNames.isEmpty()) {
    return "0"
  }

  return flagsFoundNames.joinToString("|")
}
