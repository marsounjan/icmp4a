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

object IcmpV6 {

    sealed class Message {

        abstract val type: Type

        enum class Type(val id: UByte) {
            DESTINATION_UNREACHABLE(1u),
            PACKET_TOO_BIG(2u),
            TIME_EXCEEDED(3u),
            PARAMETER_PROBLEM(4u),

            ECHO(128u),
            ECHO_REPLY(129u),
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
                    NO_ROUTE_TO_DESTINATION(id = 0u),
                    COMMUNICATION_PROHIBITED(id = 1u),
                    BEYOND_ADDRESS_SCOPE(id = 2u),
                    ADDRESS_UNREACHABLE(id = 3u),
                    PORT_UNREACHABLE(id = 4u),
                    SOURCE_ADDRESS_FAILED_INGRESS_POLICY(id = 5u),
                    ROUTE_TO_DESTINATION_REJECTED(id = 6u),
                    SOURCE_ROUTING_HEADER_ERROR(id = 7u)
                }
            }

            data class PacketTooBig(
                val reason: Reason?,
                val mtu: UInt
            ) : Response(), Icmp.Message.Error {
                override val type = Type.TIME_EXCEEDED

                enum class Reason(val id: UByte) {
                    HOP_LIMIT_EXCEEDED_IN_TRANSIT(id = 0u),
                    FRAGMENT_REASSEMBLY_TIME_EXCEEDED(id = 1u)
                }
            }

            data class TimeExceeded(
                val reason: Reason?
            ) : Response(), Icmp.Message.Error {
                override val type = Type.TIME_EXCEEDED

                enum class Reason(val id: UByte) {
                    HOP_LIMIT_EXCEEDED_IN_TRANSIT(id = 0u),
                    FRAGMENT_REASSEMBLY_TIME_EXCEEDED(id = 1u)
                }
            }

            data class ParameterProblem(
                val reason: Reason?,
                val pointer: UInt,
            ) : Response(), Icmp.Message.Error {
                override val type = Type.PARAMETER_PROBLEM

                enum class Reason(val id: UByte) {
                    ERRONEOUS_HEADER_FIELD(id = 0u),
                    UNRECOGNIZED_NEXT_HEADER(id = 1u),
                    UNRECOGNIZED_IPV6_OPTION(id = 2u)
                }
            }

        }

    }

    internal abstract class Serializer : IcmpMessageSerializer<Message.Request, Message.Response>()
    internal abstract class Session : IcmpSession<Message.Request, Message.Response>()


}