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
 * RFC: https://datatracker.ietf.org/doc/html/rfc792
 */
interface Icmp {

    data class PingStats(
        val ip: InetAddress,
        val packetsTransmitted: Int,
        val packetsReceived: Int,
        val latest: PingResult
    ) {
        val packetLoss: Float = if (packetsTransmitted != 0) 1f - packetsReceived / packetsTransmitted.toFloat() else 1f
    }

    sealed class PingResult {

        data class Success(
            val sequenceNumber: Int,
            val millis: Long
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
     * @throws Error
     */
    suspend fun ping(
        host: String,
        timeoutMillis: Long = 1000,
        packetSize: Int = DEFAULT_PACKET_SIZE,
        network: Network? = null
    ): PingStats

    /**
     * @throws Error
     */
    suspend fun ping(
        ip: InetAddress,
        timeoutMillis: Long = 1000,
        packetSize: Int = DEFAULT_PACKET_SIZE,
        network: Network? = null
    ): PingStats

    /**
     * @throws Error
     */
    fun pingInterval(
        host: String,
        count: Int? = null,
        timeoutMillis: Long = 1000,
        packetSize: Int = DEFAULT_PACKET_SIZE,
        intervalMillis: Long = 1000,
        network: Network? = null,
    ): Flow<PingStats>

    /**
     * @throws Error
     */
    fun pingInterval(
        ip: InetAddress,
        count: Int? = null,
        timeoutMillis: Long = 1000,
        packetSize: Int = DEFAULT_PACKET_SIZE,
        intervalMillis: Long = 1000,
        network: Network? = null,
    ): Flow<PingStats>

    sealed class Error : Exception() {

        class UnknownHost(override val message: String) : Error()

        class SocketException(
            override val message: String,
            override val cause: Throwable? = null
        ) : Error()

        class ProtocolException(
            override val message: String,
            override val cause: Throwable
        ) : Error()

    }

    companion object {

        const val PORT = 7
        const val DEFAULT_PACKET_SIZE = 56

        fun new(): Icmp = IcmpImpl()
    }

}