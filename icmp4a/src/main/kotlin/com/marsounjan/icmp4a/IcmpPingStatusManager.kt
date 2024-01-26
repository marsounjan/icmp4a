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

import java.net.InetAddress
import kotlin.math.roundToLong

internal class IcmpPingStatusManager(
    private val ip: InetAddress
) {

    private var latest: Icmp.PingStatus? = null

    private var latencyTotal: Long = 0
    private var latencyMin: Long = Long.MAX_VALUE
    private var latencyMax: Long = Long.MIN_VALUE

    private var packetsTransmitted: Int = 0
    private var packetsReceived: Int = 0

    private fun updateLatencyStats(result: Icmp.PingResult) {
        when (result) {
            is Icmp.PingResult.Success -> {
                latencyTotal += result.ms
                if (latencyMin > result.ms) latencyMin = result.ms
                if (latencyMax < result.ms) latencyMax = result.ms

            }

            is Icmp.PingResult.Failed -> {
                //there is nothing to do here since stats are counted only by successful results
            }
        }
    }

    private fun updatePacketStats(result: Icmp.PingResult) {
        packetsTransmitted += 1
        when (result) {
            is Icmp.PingResult.Success -> packetsReceived += 1
            is Icmp.PingResult.Failed -> {}
        }
    }

    private fun getStatus(latest: Icmp.PingResult): Icmp.PingStatus =
        Icmp.PingStatus(
            ip = ip,
            packetsTransmitted = packetsTransmitted,
            packetsReceived = packetsReceived,
            packetLoss = if (packetsTransmitted != 0) 1f - packetsReceived / packetsTransmitted.toFloat() else 1f,
            result = latest,
            stats = if (packetsReceived > 0) {
                Icmp.LatencyStats(
                    minMs = latencyMin,
                    maxMs = latencyMax,
                    avgMs = (latencyTotal / packetsReceived.toFloat()).roundToLong()
                )
            } else {
                null
            }
        )

    fun update(
        result: Icmp.PingResult
    ): Icmp.PingStatus {
        updateLatencyStats(result)
        updatePacketStats(result)

        val newStatus = getStatus(result)
        latest = newStatus
        return newStatus
    }

}