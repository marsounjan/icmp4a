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

import java.net.Inet4Address
import java.nio.ByteBuffer

internal class IcmpV4MessageSerializer : IcmpV4.MessageSerializer() {

    override fun serializeRequest(request: IcmpV4.Message.Request): ByteBuffer =
        when (request) {
            is IcmpV4.Message.Request.Echo -> serializeEchoRequest(
                type = request.type.id.toByte(),
                identifier = request.identifier,
                sequenceNumber = request.sequenceNumber
            )
        }

    override fun deserializeResponseFromParts(header: MessageHeader, data: ByteBuffer): IcmpV4.Message.Response {
        return when (header.type) {
            IcmpV4.Message.Type.ECHO_REPLY.id -> deserializeEchoResponse(header)
            IcmpV4.Message.Type.DESTINATION_UNREACHABLE.id -> deserializeDestinationUnreachable(header)
            IcmpV4.Message.Type.SOURCE_QUENCH.id -> deserializeSourceQuench(header)
            IcmpV4.Message.Type.REDIRECT.id -> deserializeRedirect(header)
            IcmpV4.Message.Type.TIME_EXCEEDED.id -> deserializeTimeExceeded(header)
            IcmpV4.Message.Type.PARAMETER_PROBLEM.id -> deserializeParamProblem(header)
            else -> throw InvalidMessageContentException(message = "Unknown response type received: ${header.type}")
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
    private fun deserializeEchoResponse(header: MessageHeader): IcmpV4.Message.Response.Echo {
        //verify code
        if (header.code != CODE_DEFAULT) throw InvalidMessageContentException(message = "Echo message must always have code $CODE_DEFAULT but was ${header.code}")

        header.typeSpecificHeaderPart.reset()
        val identifier = header.typeSpecificHeaderPart.getShort()
        val sequenceNumber = header.typeSpecificHeaderPart.getShort().toUShort()

        return IcmpV4.Message.Response.Echo(
            sequenceNumber = sequenceNumber,
            identifier = identifier
        )
    }

    /**
     * Destination Unreachable Message
     *
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |     Type      |     Code      |          Checksum             |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |                             unused                            |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |      Internet Header + 64 bits of Original Data Datagram      |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    private val destinationUnreachableCode =
        IcmpV4.Message.Response.DestinationUnreachable.Reason.entries.associateBy { it.id }

    private fun deserializeDestinationUnreachable(
        header: MessageHeader
    ): IcmpV4.Message.Response.DestinationUnreachable =
        IcmpV4.Message.Response.DestinationUnreachable(
            reason = destinationUnreachableCode[header.code]
        )

    /**
     * Source Quench Message
     *
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |     Type      |     Code      |          Checksum             |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |                             unused                            |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |      Internet Header + 64 bits of Original Data Datagram      |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    private fun deserializeSourceQuench(header: MessageHeader): IcmpV4.Message.Response.SourceQuench =
        IcmpV4.Message.Response.SourceQuench

    /**
     * Redirect Message
     *
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |     Type      |     Code      |          Checksum             |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |                 Gateway Internet Address                      |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |      Internet Header + 64 bits of Original Data Datagram      |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    private fun deserializeRedirect(header: MessageHeader): IcmpV4.Message.Response.Redirect {
        val ipBytes = ByteArray(4)
        header.typeSpecificHeaderPart.get(ipBytes)
        return IcmpV4.Message.Response.Redirect(
            inetAddress = Inet4Address.getByAddress(ipBytes) as Inet4Address
        )
    }

    /**
     * Time Exceeded Message
     *
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |     Type      |     Code      |          Checksum             |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |                             unused                            |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |      Internet Header + 64 bits of Original Data Datagram      |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    private fun deserializeTimeExceeded(header: MessageHeader): IcmpV4.Message.Response.TimeExceeded =
        IcmpV4.Message.Response.TimeExceeded

    /**
     * Parameter Problem Message
     *
     *     0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |     Type      |     Code      |          Checksum             |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |    Pointer    |                   unused                      |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |      Internet Header + 64 bits of Original Data Datagram      |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    private fun deserializeParamProblem(header: MessageHeader): IcmpV4.Message.Response.ParameterProblem =
        IcmpV4.Message.Response.ParameterProblem(
            pointer = header.typeSpecificHeaderPart.get()
        )

}