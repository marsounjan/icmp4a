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

object IcmpV4 {

    sealed class Message {

        abstract val type: Type

        enum class Type(val id: UByte) {
            ECHO_REPLY(0u),
            DESTINATION_UNREACHABLE(3u),
            SOURCE_QUENCH(4u),
            REDIRECT(5u),
            ECHO(8u),
            TIME_EXCEEDED(11u),
            PARAMETER_PROBLEM(12u),
        }

        internal sealed class Request : Message() {

            data class Echo(
                val sequenceNumber: UShort,
                val identifier: Short
            ) : Request(), Icmp.Message.Informational {
                override val type = Type.ECHO
            }

        }

        sealed class Response : Message() {

            internal data class Echo(
                val sequenceNumber: UShort,
                val identifier: Short
            ) : Response(), Icmp.Message.Informational {
                override val type = Type.ECHO_REPLY
            }

            data class DestinationUnreachable(
                val reason: Reason?
            ) : Response(), Icmp.Message.Error {
                override val type = Type.DESTINATION_UNREACHABLE

                enum class Reason(val id: UByte, val message: String) {
                    NETWORK_UNREACHABLE(id = 0u, message = "Destination Net Unreachable"),
                    HOST_UNREACHABLE(id = 1u, message = "Destination Host Unreachable"),
                    PROTOCOL_UNREACHABLE(id = 2u, message = "Destination Protocol Unreachable"),
                    PORT_UNREACHABLE(id = 3u, message = "Destination Port Unreachable"),
                    FRAGMENTATION_NEEDED(id = 4u, message = "Frag needed and DF set"),
                    DEST_NET_UNKNOWN(id = 6u, message = "Destination network unknown"),
                    DEST_HOST_UNKNOWN(id = 7u, message = "Destination host unknown"),
                    SOURCE_HOST_ISOLATED(id = 8u, message = "Source host isolated"),
                    DEST_NETWORK_ADMIN_PROHIBITED(id = 9u, message = "Destination network is administratively prohibited"),
                    DEST_HOST_ADMIN_PROHIBITED(id = 10u, message = "Destination host is administratively prohibited"),
                    NETWORK_UNREACHABLE_FOR_TOS(id = 11u, message = "Network is unreachable for Type Of Service"),
                    HOST_UNREACHABLE_FOR_TOS(id = 12u, message = "Host is unreachable for Type Of Service"),
                    COMM_ADMIN_PROHIBITED(
                        id = 13u,
                        message = "Communication administratively prohibited (administrative filtering prevents packet from being forwarded)"
                    ),
                    HOST_PRECEDENCE_VIOLATION(
                        id = 14u,
                        message = "Host precedence violation (indicates the requested precedence is not permitted for the combination of host or network and port)"
                    ),
                    PRECEDENCE_CUTOFF(
                        id = 15u,
                        message = "Precedence cutoff in effect (precedence of datagram is below the level set by the network administrators)"
                    )
                }
            }

            data object SourceQuench : Response(), Icmp.Message.Error {
                override val type = Type.SOURCE_QUENCH
            }

            data class Redirect(
                val inetAddress: Inet4Address
            ) : Response(), Icmp.Message.Error {
                override val type = Type.REDIRECT
            }

            data object TimeExceeded : Response(), Icmp.Message.Error {
                override val type = Type.TIME_EXCEEDED
            }

            data class ParameterProblem(
                val pointer: Byte
            ) : Response(), Icmp.Message.Error {
                override val type = Type.PARAMETER_PROBLEM
            }

        }

    }

    internal abstract class MessageSerializer : IcmpMessageSerializer<Message.Request, Message.Response>()
    internal abstract class PingSession : IcmpPingSession<Message.Request, Message.Response>()


    /**
     * shouldn't exceed minimal IPv4 MTU which is 576
     */
    internal const val ERROR_DATAGRAM_LENGTH_MAX = 576
    internal const val DATAGRAM_LENGTH_MAX = Icmp.PACKET_SIZE_MAX_IPV4


}