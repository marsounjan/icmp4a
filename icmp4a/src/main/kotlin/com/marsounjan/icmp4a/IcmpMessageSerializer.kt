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

internal abstract class IcmpMessageSerializer<Request, Response> {

    data class MessageHeader(
        val type: UByte,
        val code: UByte,
        val checksum: Short,
        val typeSpecificHeaderPart: ByteBuffer
    )

    /**
     * Message Header
     *
     * 0                   1                   2                   3
     *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |     Type      |     Code      |          Checksum             |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     *    |                        type specific                          |
     *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     */
    private fun extractHeader(byteBuffer: ByteBuffer): MessageHeader {
        //reset buffer position
        byteBuffer.rewind()
        //check if buffer is long enough to contain the header
        if (byteBuffer.remaining() < HEADER_SIZE) throw InvalidMessageContent(message = "Incoming message doesn't match minimal length requirements. Length: ${byteBuffer.remaining()}}")
        //extract header
        return MessageHeader(
            type = byteBuffer.get().toUByte(),
            code = byteBuffer.get().toUByte(),
            checksum = byteBuffer.getShort(),
            typeSpecificHeaderPart = ByteBuffer.wrap(byteBuffer.array(), 4, 4)
                .apply { mark() }
        )
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
    protected fun serializeEchoRequest(
        type: Byte,
        identifier: Short,
        sequenceNumber: UShort
    ): ByteBuffer {
        val buffer = ByteBuffer.allocate(8)
        buffer.put(type)
        buffer.put(CODE_DEFAULT.toByte())
        //don't bother with checksum calculation since it's recalculated by kernel anyway
        buffer.putShort(0)
        buffer.putShort(identifier)
        buffer.putShort(sequenceNumber.toShort())
        buffer.flip()
        return buffer
    }

    abstract fun serializeRequest(request: Request): ByteBuffer

    /**
     * @throws InvalidMessageContent
     */
    abstract fun deserializeResponseFromParts(header: MessageHeader, data: ByteBuffer): Response

    /**
     * Don't bother with checksum verification since it's already recalculated by kernel at this point
     *
     * @throws InvalidMessageContent
     */
    fun deserializeResponse(byteBuffer: ByteBuffer): Response {
        //verify message size and extract header
        val header = extractHeader(byteBuffer)
        //get the data buffer
        val msgDataBuffer = ByteBuffer.wrap(byteBuffer.array(), HEADER_SIZE, byteBuffer.limit() - HEADER_SIZE)
            .apply {
                //mark the position
                mark()
            }
        return deserializeResponseFromParts(
            header = header,
            data = msgDataBuffer
        )
    }

    data class InvalidMessageContent(override val message: String) : Exception()

    companion object {
        const val HEADER_SIZE = 8
        const val CODE_DEFAULT: UByte = 0u
    }

}