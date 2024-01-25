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

internal class IcmpV6PingSession(
    override val packetSize: Int
) : IcmpV6.PingSession() {

    init {
        if (packetSize > IcmpV6.DATAGRAM_LENGTH_MAX) throw Icmp.Error.ProtocolException("Packet size '$packetSize' exceeded maximal IPv6 packet size ${IcmpV6.DATAGRAM_LENGTH_MAX}")
    }

    override val packetBuffer = ByteArray(IcmpMessageSerializer.HEADER_SIZE + max(IcmpV4.ERROR_DATAGRAM_LENGTH_MAX, packetSize))
    override val serializer = IcmpV6MessageSerializer()

    override fun getRequest(sequenceNumber: UShort, identifier: Short): IcmpV6.Message.Request =
        IcmpV6.Message.Request.Echo(
            sequenceNumber = sequenceNumber,
            identifier = identifier
        )

    override fun isResponseToLatestRequest(response: IcmpV6.Message.Response): Boolean =
        when (response) {
            //todo honza response identifier
            //todo kernel is messing up with identifier
            // https://lwn.net/Articles/443051/
            // https://stackoverflow.com/a/37456455
            is IcmpV6.Message.Response.Echo -> /*response.identifier == sessionIdentifier && */response.sequenceNumber == sequenceNumber
            is IcmpV6.Message.Response.DestinationUnreachable,
            is IcmpV6.Message.Response.TimeExceeded,
            is IcmpV6.Message.Response.ParameterProblem,
            is IcmpV6.Message.Response.PacketTooBig -> true
        }

    override fun packetResponseToIcmpResponse(
        response: IcmpV6.Message.Response,
        packetSize: Int,
        millis: Long
    ): Icmp.PingResult =
        when (response) {
            is IcmpV6.Message.Response.Echo -> Icmp.PingResult.Success(
                sequenceNumber = response.sequenceNumber.toInt(),
                packetSize = packetSize,
                ms = millis
            )

            is IcmpV6.Message.Response.DestinationUnreachable -> Icmp.PingResult.Failed.Error(
                message = response.reason?.message ?: "Destination Unreachable",
                error = response,
            )

            is IcmpV6.Message.Response.PacketTooBig -> Icmp.PingResult.Failed.Error(
                message = "Packet too big. MTU: ${response.mtu}",
                error = response,
            )

            is IcmpV6.Message.Response.TimeExceeded -> Icmp.PingResult.Failed.Error(
                message = response.reason?.message ?: "Time exceeded",
                error = response,
            )

            is IcmpV6.Message.Response.ParameterProblem -> Icmp.PingResult.Failed.Error(
                message = (response.reason?.message ?: "Parameter Problem") + " at ${response.pointer}",
                error = response,
            )
        }

}