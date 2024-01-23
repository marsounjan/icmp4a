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
import kotlin.random.Random

internal abstract class IcmpSession<Request, Response> {

    protected var sequenceNumber: UShort = 0u
        private set

    protected val sessionIdentifier: Short = Random.nextInt().toShort()
    protected abstract val serializer: IcmpMessageSerializer<Request, Response>

    protected abstract fun getRequest(
        sequenceNumber: UShort,
        identifier: Short
    ): Request

    protected abstract fun isResponseToLatestRequest(response: Response): Boolean

    protected abstract fun packetResponseToIcmpResponse(response: Response, millis: Long): Icmp.PingResult

    fun nextRequest(): ByteBuffer {
        return serializer.serializeRequest(
            getRequest(
                sequenceNumber = ++sequenceNumber,
                identifier = sessionIdentifier
            )
        )
    }

    fun processResponse(buffer: ByteBuffer, millis: Long): Icmp.PingResult? =
        serializer.deserializeResponse(buffer)
            .takeIf { isResponseToLatestRequest(it) }
            ?.let { packetResponseToIcmpResponse(it, millis) }

}