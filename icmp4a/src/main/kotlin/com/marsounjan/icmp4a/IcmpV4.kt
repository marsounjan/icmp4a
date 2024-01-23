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

                enum class Reason(val id: UByte) {
                    NETWORK_UNREACHABLE(id = 0u),
                    HOST_UNREACHABLE(id = 1u),
                    PROTOCOL_UNREACHABLE(id = 2u),
                    PORT_UNREACHABLE(id = 3u),
                    FRAGMENTATION_NEEDED(id = 4u),
                    SOURCE_ROUTE_FAILED(id = 5u)
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

    internal abstract class Serializer : IcmpMessageSerializer<Message.Request, Message.Response>()
    internal abstract class Session : IcmpSession<Message.Request, Message.Response>()


}