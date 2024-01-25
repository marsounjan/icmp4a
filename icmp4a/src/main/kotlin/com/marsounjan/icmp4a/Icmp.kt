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

package com.marsounjan.icmp4a

import android.net.Network
import kotlinx.coroutines.flow.Flow
import java.net.InetAddress

/**
 *  INTERNET CONTROL MESSAGE PROTOCOL (ICMP)
 *
 * IPv4: https://datatracker.ietf.org/doc/html/rfc792
 * IPv6: https://datatracker.ietf.org/doc/html/rfc4443
 */
interface Icmp {

    data class PingStatus(
        val ip: InetAddress,
        val packetsTransmitted: Int,
        val packetsReceived: Int,
        val packetLoss: Float,
        val stats: LatencyStats?,
        val latestResult: PingResult
    )

    data class LatencyStats(
        val minMs: Long,
        val avgMs: Long,
        val maxMs: Long
    )

    sealed class PingResult {

        data class Success(
            val sequenceNumber: Int,
            val packetSize: Int,
            val ms: Long
        ) : PingResult()

        sealed class Failed : PingResult() {

            abstract val message: String

            data class Error(
                override val message: String,
                val error: Message.Error
            ) : Failed()

            data class RequestTimeout(
                override val message: String,
                val millis: Long
            ) : Failed()

            data class IO(override val message: String) : Failed()

        }
    }

    sealed interface Message {

        sealed interface Informational
        sealed interface Error

    }

    /**
     * @throws IllegalArgumentException
     * @throws Error
     */
    suspend fun ping(
        host: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        packetSize: Int = DEFAULT_PACKET_SIZE,
        network: Network? = null
    ): PingStatus

    /**
     * @throws IllegalArgumentException
     * @throws Error
     */
    suspend fun ping(
        ip: InetAddress,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        packetSize: Int = DEFAULT_PACKET_SIZE,
        network: Network? = null
    ): PingStatus

    /**
     * @throws IllegalArgumentException
     * @throws Error
     */
    fun pingInterval(
        host: String,
        count: Int? = null,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        packetSize: Int = DEFAULT_PACKET_SIZE,
        intervalMillis: Long = DEFAULT_INTERVAL_MS,
        network: Network? = null,
    ): Flow<PingStatus>

    /**
     * @throws IllegalArgumentException
     * @throws Error
     */
    fun pingInterval(
        ip: InetAddress,
        count: Int? = null,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        packetSize: Int = DEFAULT_PACKET_SIZE,
        intervalMillis: Long = DEFAULT_INTERVAL_MS,
        network: Network? = null,
    ): Flow<PingStatus>

    sealed class Error : Exception() {

        class UnknownHost(override val message: String) : Error()

        class SocketException(
            override val message: String,
            override val cause: Throwable? = null
        ) : Error()

        class ProtocolException(
            override val message: String,
            override val cause: Throwable? = null
        ) : Error()

    }

    companion object {
        const val PORT = 7
        const val DEFAULT_PACKET_SIZE = 56
        const val PACKET_SIZE_MAX_IPV4 = 65507
        const val PACKET_SIZE_MAX_IPV6 = 131024
        private const val DEFAULT_INTERVAL_MS: Long = 1000
        private const val DEFAULT_TIMEOUT_MS: Long = 1000
    }
}