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

import kotlin.math.max

internal class IcmpV4PingSession(
    override val packetSize: Int,
) : IcmpV4.PingSession() {

    init {
        if (packetSize > IcmpV4.DATAGRAM_LENGTH_MAX) throw Icmp.Error.ProtocolException("Packet size '$packetSize' exceeded maximal IPv4 packet size ${IcmpV4.DATAGRAM_LENGTH_MAX}")
    }

    override val packetBuffer = ByteArray(IcmpMessageSerializer.HEADER_SIZE + max(IcmpV4.ERROR_DATAGRAM_LENGTH_MAX, packetSize))
    override val serializer = IcmpV4MessageSerializer()

    override fun getRequest(sequenceNumber: UShort, identifier: Short): IcmpV4.Message.Request =
        IcmpV4.Message.Request.Echo(
            sequenceNumber = sequenceNumber,
            identifier = identifier
        )

    override fun isResponseToLatestRequest(response: IcmpV4.Message.Response): Boolean =
        when (response) {
            /**
             * as described here https://lwn.net/Articles/443051/ kernel is touching the packet and using it's own session identifier (port)
             * and calculated new checksum. It it's already taken care about session identifier and there is no reason to check it here
             * (also it wouldn't match with previously our set identifier)
             */
            is IcmpV4.Message.Response.Echo -> /*response.identifier == sessionIdentifier && */response.sequenceNumber == sequenceNumber
            is IcmpV4.Message.Response.DestinationUnreachable,
            is IcmpV4.Message.Response.SourceQuench,
            is IcmpV4.Message.Response.TimeExceeded,
            is IcmpV4.Message.Response.ParameterProblem -> true

            is IcmpV4.Message.Response.Redirect -> false
        }

    override fun packetResponseToIcmpResponse(
        response: IcmpV4.Message.Response,
        packetSize: Int,
        millis: Long
    ): Icmp.PingResult =
        when (response) {
            is IcmpV4.Message.Response.Echo -> Icmp.PingResult.Success(
                sequenceNumber = response.sequenceNumber.toInt(),
                packetSize = packetSize,
                ms = millis
            )

            is IcmpV4.Message.Response.DestinationUnreachable -> Icmp.PingResult.Failed.Error(
                message = response.reason?.message ?: "Destination Unreachable",
                error = response
            )

            is IcmpV4.Message.Response.SourceQuench -> Icmp.PingResult.Failed.Error(
                message = "Source Quench",
                error = response
            )

            is IcmpV4.Message.Response.TimeExceeded -> Icmp.PingResult.Failed.Error(
                message = "Transport Time Exceeded",
                error = response
            )

            is IcmpV4.Message.Response.ParameterProblem -> Icmp.PingResult.Failed.Error(
                message = "Parameter Problem at index ${response.pointer}",
                error = response
            )

            is IcmpV4.Message.Response.Redirect ->
                Icmp.PingResult.Failed.Error(
                    message = "Redirected to ${response.inetAddress.hostAddress}",
                    error = response
                )
        }

}