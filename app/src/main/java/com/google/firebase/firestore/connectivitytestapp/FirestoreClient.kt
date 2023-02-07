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
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableList.toImmutableList
import com.google.common.collect.Streams
import com.google.firestore.v1.FirestoreGrpc
import com.google.firestore.v1.ListenRequest
import com.google.firestore.v1.ListenResponse
import com.google.firestore.v1.RunAggregationQueryRequest
import com.google.firestore.v1.StructuredAggregationQuery
import com.google.firestore.v1.StructuredQuery
import com.google.firestore.v1.Target
import io.grpc.ClientCall
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.android.AndroidChannelBuilder
import java.io.Closeable
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

private const val HOST = "firestore.googleapis.com"

class FirestoreClient(private val channel: ManagedChannel, private val stub: FirestoreGrpc.FirestoreBlockingStub, private val log: (String) -> Unit) {

  fun runAggregationQuery() {
    log("Sending runAggregationQuery RPC")
    val request = createRunAggregationQueryRequest()
    val responses = stub.runAggregationQuery(request)

    val counts = Streams.stream(responses).map { response ->
      response.result.aggregateFieldsMap.get(
        "count"
      )!!.integerValue
    }.collect(
      toImmutableList()
    )

    if (counts.size != 1) {
      throw FirestoreClientException(
        "runAggregationQuery RPC returned ${counts.size} responses, " +
            "but expected exactly 1: $counts")
    }

    log("runAggregationQuery returned a count of ${counts[0]}")
  }

  fun newListenStream(): ListenStream {
    val call = channel.newCall(FirestoreGrpc.getListenMethod(), stub.callOptions)
    return ListenStream(call) { message -> log("ListenStream $message")}
  }

  companion object {

    fun create(context: Context, log: (String) -> Unit): FirestoreClient {
      log("Creating GRPC channel to $HOST")
      val channel = AndroidChannelBuilder.forTarget(HOST).context(context).build()
      log("Creating FirestoreGrpc stub")
      val stub = FirestoreGrpc.newBlockingStub(channel)
      val id = "FirestoreClient@${generateUniqueFirestoreClientId()}"
      log("Created $id")
      return FirestoreClient(channel, stub) {message -> log("$id $message") }
    }

  }

}

class ListenStreamClosedException : RuntimeException()

class ListenStream(private val call: ClientCall<ListenRequest, ListenResponse>, private val log: (String) -> Unit) : Closeable {

  private val listener = ListenStreamListener(call) { message -> log("ListenStreamListener $message")}
  private var closed = false

  init {
    val headers = Metadata()
    headers.put(RESOURCE_PREFIX_HEADER, "projects/dconeybe-testing/databases/(default)")
    headers.put(X_GOOG_REQUEST_PARAMS_HEADER, "projects/dconeybe-testing/databases/(default)")
    headers.put(X_GOOG_API_CLIENT_HEADER, "gl-java/ fire/24.4.2 grpc/")
    log("ClientCall.start() with headers=$headers")
    call.start(listener, headers)
  }

  fun addTarget() = nextTargetId.incrementAndGet().also { targetId ->
    if (closed) {
      throw ListenStreamClosedException()
    }
    val listenRequest = createListenRequest(targetId)
    log("addTarget() targetId=$targetId $listenRequest")
    call.sendMessage(listenRequest)
    call.request(1)
  }

  override fun close() {
    log("close()")
    closed = true
    call.cancel("done with listen stream", null)
  }

}

private class ListenStreamListener(private val call: ClientCall<ListenRequest, ListenResponse>, private val log: (String) -> Unit) : ClientCall.Listener<ListenResponse>() {

  override fun onHeaders(headers: Metadata?) {
    log("onHeaders() headers=$headers")
  }

  override fun onMessage(message: ListenResponse?) {
    log("onMessage() message=$message")
    call.request(1)
  }

  override fun onClose(status: Status?, trailers: Metadata?) {
    log("onClose() status=$status trailers=$trailers")
  }

  override fun onReady() {
    log("onReady()")
  }

}

class FirestoreClientException(message: String) : Exception(message)

private fun generateUniqueFirestoreClientId() = MessageDigest.getInstance("SHA-1").run {
    update(SystemClock.elapsedRealtimeNanos().toString().toByteArray())
    digest()
  }.joinToString(separator = "", transform = { "%02x".format(it) }).substring(0, 8)

private fun createRunAggregationQueryRequest() =
  RunAggregationQueryRequest.newBuilder().run {
    parent = "projects/dconeybe-testing/databases/(default)/documents"

    setStructuredAggregationQuery(StructuredAggregationQuery.newBuilder().apply {
      structuredQuery = createStructuredQuery()
      addAggregations(StructuredAggregationQuery.Aggregation.newBuilder().apply {
        alias = "count"
        count = StructuredAggregationQuery.Aggregation.Count.newBuilder().build()
      })
    })

    build()
  }

private fun createStructuredQuery() =
      StructuredQuery.newBuilder().run {
        addFrom(StructuredQuery.CollectionSelector.newBuilder().apply {
          collectionId = "v9web-demo-loEjzZ1SColQ1u2Hi4wq"
        })
        addOrderBy(StructuredQuery.Order.newBuilder().apply {
          setField(StructuredQuery.FieldReference.newBuilder().apply {
            fieldPath = "__name__"
          })
          setDirection(StructuredQuery.Direction.ASCENDING)
        })
        build()
      }

private fun createListenRequest(targetId: Int) = ListenRequest.newBuilder().run {
  setAddTarget(Target.newBuilder().apply {
    database = "projects/dconeybe-testing/databases/(default)"
    setQuery(Target.QueryTarget.newBuilder().apply {
      setTargetId(targetId)
      structuredQuery = createStructuredQuery()
    })
  })
  build()
}

private val nextTargetId = AtomicInteger(0)

private val RESOURCE_PREFIX_HEADER = Metadata.Key.of("google-cloud-resource-prefix", Metadata.ASCII_STRING_MARSHALLER);
private val X_GOOG_REQUEST_PARAMS_HEADER = Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER);
private val X_GOOG_API_CLIENT_HEADER = Metadata.Key.of("x-goog-api-client", Metadata.ASCII_STRING_MARSHALLER);
