/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.marsounjan.icmp4a.demo

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marsounjan.icmp4a.Icmp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import java.util.concurrent.LinkedBlockingQueue

private const val LOG_TAG = "DEMO"
private const val INTERVAL_MS: Long = 1000
private const val RESULT_CACHE_CAPACITY = 50

class HomeScreenVM : ViewModel() {

    private val icmp = Icmp.new()

    private val _hostField = MutableStateFlow("8.8.8.8")

    @Immutable
    data class Stats(
        val host: String,
        val ip: String? = null,
        val pingMs: Long? = null,
        val error: String? = null,
        val packetsTransmitted: Int = 0,
        val packetsReceived: Int = 0,
        val packetLoss: Float = 0f,
        val results: List<ResultItem>
    )

    @Immutable
    data class ResultItem(
        val num: Int,
        val message: String,
        val ms: Long?
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val stats = _hostField
        .flatMapLatest { host ->
            icmp.pingInterval(
                host = host,
                intervalMillis = INTERVAL_MS
            )
                .cacheLatest(RESULT_CACHE_CAPACITY)
                .map { cachedStats ->
                    val latestStats = cachedStats.last()
                    Log.d(LOG_TAG, "Ping Result: ${latestStats.latest}")
                    Stats(
                        host = host,
                        ip = latestStats.ip.hostAddress,
                        packetsTransmitted = latestStats.packetsTransmitted,
                        packetsReceived = latestStats.packetsReceived,
                        packetLoss = latestStats.packetLoss,
                        pingMs = latestStats.latest.let { result ->
                            when (result) {
                                is Icmp.PingResult.Success -> result.millis
                                is Icmp.PingResult.Failed -> null
                            }
                        },
                        results = cachedStats
                            .asReversed()
                            .map { stats ->
                                stats.latest.let { result ->
                                    when (result) {
                                        is Icmp.PingResult.Success -> ResultItem(
                                            num = stats.packetsTransmitted,
                                            message = "",
                                            ms = result.millis
                                        )

                                        is Icmp.PingResult.Failed -> ResultItem(
                                            num = stats.packetsTransmitted,
                                            message = result.message,
                                            ms = null
                                        )
                                    }
                                }
                            }
                    )
                }
                .catch {
                    Log.w(LOG_TAG, "Ping Stream failed", it)
                    when (it) {
                        is Icmp.Error -> emit(
                            Stats(
                                host = host,
                                error = it.message,
                                results = listOf()
                            )
                        )

                        else -> throw it
                    }
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = Stats(
                host = "",
                results = listOf()
            )
        )

    private fun <T> Flow<T>.cacheLatest(capacity: Int): Flow<List<T>> {
        return scan<T, LinkedBlockingQueue<T>>(LinkedBlockingQueue(capacity)) { cache, item ->
            while (cache.remainingCapacity() < 1) {
                cache.poll()
            }
            cache.add(item)
            cache
        }
            .map { it.toList() }
            .drop(1)
    }

    val hostField: StateFlow<String>
        get() = _hostField

    fun onHostFieldChanged(value: String) {
        Log.d(LOG_TAG, "Host changed: $value")
        _hostField.value = value
    }

}