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

import java.nio.ByteBuffer

internal class IcmpV6PacketSerializer : IcmpV6.Serializer() {

    override fun serializeRequest(request: IcmpV6.Message.Request): ByteBuffer =
        when (request) {
            is IcmpV6.Message.Request.Echo -> serializeEchoRequest(
                type = request.type.id.toByte(),
                identifier = request.identifier,
                sequenceNumber = request.sequenceNumber
            )
        }

    override fun deserializeResponseFromParts(header: MessageHeader, data: ByteBuffer): IcmpV6.Message.Response {
        return when (header.type) {
            IcmpV6.Message.Type.ECHO_REPLY.id -> deserializeEchoResponse(header)
            IcmpV6.Message.Type.DESTINATION_UNREACHABLE.id -> deserializeDestinationUnreachable(header)
            IcmpV6.Message.Type.PACKET_TOO_BIG.id -> deserializePacketTooBig(header)
            IcmpV6.Message.Type.TIME_EXCEEDED.id -> deserializeTimeExceeded(header)
            IcmpV6.Message.Type.PARAMETER_PROBLEM.id -> deserializeParamProblem(header)
            else -> throw InvalidMessageContent(message = "Unknown response type received: ${header.type}")
        }
    }

    /**
     * Echo or Echo Reply Message
     *
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |     Type      |     Code      |          Checksum             |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |           Identifier          |        Sequence Number        |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |     Data ...
     *    +-+-+-+-+-
     */
    private fun deserializeEchoResponse(header: MessageHeader): IcmpV6.Message.Response.Echo {
        //verify code
        if (header.code != CODE_DEFAULT) throw InvalidMessageContent(message = "Echo message must always have code $CODE_DEFAULT but was ${header.code}")

        header.typeSpecificHeaderPart.reset()
        val identifier = header.typeSpecificHeaderPart.getShort()
        val sequenceNumber = header.typeSpecificHeaderPart.getShort().toUShort()

        return IcmpV6.Message.Response.Echo(
            sequenceNumber = sequenceNumber,
            identifier = identifier
        )
    }

    /**
     * Destination Unreachable Message
     *
     *       0                   1                   2                   3
     *        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |     Type      |     Code      |          Checksum             |
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |                             Unused                            |
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |                    As much of invoking packet                 |
     *       +                as possible without the ICMPv6 packet          +
     *       |                exceeding the minimum IPv6 MTU [IPv6]          |
     */
    private val destinationUnreachableCode =
        IcmpV6.Message.Response.DestinationUnreachable.Reason.entries.associateBy { it.id }

    private fun deserializeDestinationUnreachable(
        header: MessageHeader
    ): IcmpV6.Message.Response.DestinationUnreachable =
        IcmpV6.Message.Response.DestinationUnreachable(
            reason = destinationUnreachableCode[header.code]
        )

    /**
     * Packet Too Big Message
     *
     *       0                   1                   2                   3
     *        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |     Type      |     Code      |          Checksum             |
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |                             MTU                               |
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |                    As much of invoking packet                 |
     *       +               as possible without the ICMPv6 packet           +
     *       |               exceeding the minimum IPv6 MTU [IPv6]           |
     */
    private val packetTooBigCode =
        IcmpV6.Message.Response.PacketTooBig.Reason.entries.associateBy { it.id }

    private fun deserializePacketTooBig(header: MessageHeader): IcmpV6.Message.Response.PacketTooBig =
        IcmpV6.Message.Response.PacketTooBig(
            reason = packetTooBigCode[header.code],
            mtu = header.typeSpecificHeaderPart.getInt().toUInt()
        )

    /**
     * Time Exceeded Message
     *
     *       0                   1                   2                   3
     *        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |     Type      |     Code      |          Checksum             |
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |                             Unused                            |
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |                    As much of invoking packet                 |
     *       +               as possible without the ICMPv6 packet           +
     *       |               exceeding the minimum IPv6 MTU [IPv6]           |
     */
    private val timeExceededCode =
        IcmpV6.Message.Response.TimeExceeded.Reason.entries.associateBy { it.id }

    private fun deserializeTimeExceeded(header: MessageHeader): IcmpV6.Message.Response.TimeExceeded =
        IcmpV6.Message.Response.TimeExceeded(
            reason = timeExceededCode[header.code]
        )

    /**
     * Parameter Problem Message
     *
     *       0                   1                   2                   3
     *        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |     Type      |     Code      |          Checksum             |
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |                            Pointer                            |
     *       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *       |                    As much of invoking packet                 |
     *       +               as possible without the ICMPv6 packet           +
     *       |               exceeding the minimum IPv6 MTU [IPv6]           |
     */
    private val parameterProblemCode =
        IcmpV6.Message.Response.ParameterProblem.Reason.entries.associateBy { it.id }

    private fun deserializeParamProblem(header: MessageHeader): IcmpV6.Message.Response.ParameterProblem =
        IcmpV6.Message.Response.ParameterProblem(
            reason = parameterProblemCode[header.code],
            pointer = header.typeSpecificHeaderPart.getInt().toUInt()
        )

}