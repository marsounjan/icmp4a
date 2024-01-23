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

import android.net.Network
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FileDescriptor
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketException
import java.net.UnknownHostException
import java.nio.ByteBuffer

internal class IcmpImpl : Icmp {

    /**
     *
     *
     * @return file descriptor of the new socket
     */
    private fun createSocketForDestination(destination: InetAddress): FileDescriptor {
        try {
            val fd = when (destination) {
                is Inet6Address -> Os.socket(OsConstants.AF_INET6, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMPV6)
                is Inet4Address -> Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
                else -> throw IllegalStateException("Unsupported destination address type ${destination.javaClass.canonicalName}")
            }

            if (!fd.valid()) throw Icmp.Error.Socket.Creation(message = "Created file descriptor is invalid")

            return fd
        } catch (e: ErrnoException) {
            throw Icmp.Error.Socket.Creation(message = "Socket creation failed", cause = e)
        }
    }

    /**
     * todo
     *
     *
     */
    private fun setSocketOptions(
        fd: FileDescriptor
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Os.setsockoptInt(fd, OsConstants.IPPROTO_IP, OsConstants.IP_TOS, IPTOS_LOWDELAY)
            } catch (e: ErrnoException) {
                throw Icmp.Error.Socket.OptionsSet(message = "Failed to set IP_TOS to low delay")
            }
        }
    }

    private fun bindToNetwork(
        fd: FileDescriptor,
        network: Network
    ) {
        try {
            network.bindSocket(fd)
        } catch (e: IOException) {
            throw Icmp.Error.Socket.NetworkBind(message = "Failed to bind socket to specified network")
        }
    }

    private fun newPacketSessionForDestination(destination: InetAddress): IcmpSession<*, *> =
        when (destination) {
            is Inet4Address -> IcmpV4Session()
            is Inet6Address -> IcmpV6Session()
            else -> throw IllegalArgumentException("Unsupported destination address type ${destination.javaClass.canonicalName}")
        }

    companion object {
        const val RESPONSE_BUFFER_SIZE = 64
        private const val IPTOS_LOWDELAY = 0x10
        private const val ICMP_SERVICE_PORT = 7

        //POLLIN isn't populated correctly in test stubs
        protected val POLLIN = (if (OsConstants.POLLIN == 0) 1 else OsConstants.POLLIN).toShort()
        private const val MSG_DONTWAIT = 0x40
    }

    internal sealed class HostnameResolutionResult {

        data class Success(
            val inetAddress: InetAddress
        ) : HostnameResolutionResult()

        sealed class Failed() : HostnameResolutionResult() {

            abstract val cause: Exception

            class UnknownHost(override val cause: UnknownHostException) : Failed()
            class SecurityError(override val cause: SecurityException) : Failed()
            class Timeout(override val cause: TimeoutCancellationException) : Failed()

        }

    }

    private suspend fun resolveIpForHost(hostname: String, timeoutMillis: Long): HostnameResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(timeoutMillis) {
                    HostnameResolutionResult.Success(
                        inetAddress = InetAddress.getByName(hostname)
                    )
                }
            } catch (e: UnknownHostException) {
                HostnameResolutionResult.Failed.UnknownHost(cause = e)
            } catch (e: SecurityException) {
                HostnameResolutionResult.Failed.SecurityError(cause = e)
            } catch (e: TimeoutCancellationException) {
                HostnameResolutionResult.Failed.Timeout(cause = e)
            }
        }

    private suspend fun ProducerScope<Icmp.PingStats>.updateAndEmitStats(
        previous: Icmp.PingStats?,
        ip: InetAddress,
        result: Icmp.PingResult
    ): Icmp.PingStats {
        val newStats = previous?.copy(
            packetsTransmitted = previous.packetsTransmitted + 1,
            packetsReceived = if (result !is Icmp.PingResult.Failed) previous.packetsReceived + 1 else previous.packetsReceived,
            latest = result
        )
            ?: Icmp.PingStats(
                ip = ip,
                packetsTransmitted = 1,
                packetsReceived = if (result !is Icmp.PingResult.Failed) 1 else 0,
                latest = result
            )

        send(newStats)

        return newStats
    }


    override suspend fun ping(
        destination: Icmp.Destination,
        timeoutMillis: Long,
        packetSize: Int,
        network: Network?
    ): Icmp.PingStats =
        pingInterval(
            destination = destination,
            count = 1,
            timeoutMillis = timeoutMillis,
            network = network
        )
            .first()

    override fun pingInterval(
        destination: Icmp.Destination,
        count: Int?,
        timeoutMillis: Long,
        packetSize: Int,
        intervalMillis: Long,
        network: Network?
    ): Flow<Icmp.PingStats> =
        callbackFlow<Icmp.PingStats> {
            val ip = when (destination) {
                is Icmp.Destination.IP -> destination.ip
                is Icmp.Destination.Hostname ->
                    when (val result = resolveIpForHost(
                        hostname = destination.host,
                        timeoutMillis = timeoutMillis
                    )) {
                        is HostnameResolutionResult.Success -> result.inetAddress
                        is HostnameResolutionResult.Failed.UnknownHost -> {
                            throw Icmp.Error.UnknownHost(message = "Unknown host '${destination.host}'")
                        }

                        is HostnameResolutionResult.Failed.Timeout -> {
                            throw Icmp.Error.UnknownHost(message = "Failed to resolve host ${destination.host} in $timeoutMillis ms")
                        }

                        is HostnameResolutionResult.Failed.SecurityError -> {
                            throw Icmp.Error.SystemIO(
                                message = "Security manager exists doesn't allow hostname resolution",
                                cause = result.cause
                            )
                        }
                    }
            }
            val packetSession = newPacketSessionForDestination(destination = ip)
            val fd = createSocketForDestination(destination = ip)
            try {
                setSocketOptions(fd)
                if (network != null) bindToNetwork(fd, network)

                val pollFd = StructPollfd()
                pollFd.fd = fd
                val pollFds = arrayOf(pollFd)

                var timestamp: Long
                var millis: Long
                val buffer = ByteArray(RESPONSE_BUFFER_SIZE)
                var rc: Int
                var result: Icmp.PingResult?
                var sentCount = 0
                var stats: Icmp.PingStats? = null
                while (count == null || sentCount++ < count) {
                    /**
                     * ping
                     */
                    try {
                        timestamp = System.currentTimeMillis()
                        Log.println(Log.DEBUG, "Honza", "session ${packetSession.hashCode()} send")
                        rc = Os.sendto(fd, packetSession.nextRequest(), 0, ip, ICMP_SERVICE_PORT)
                        Log.println(Log.DEBUG, "Honza", "session ${packetSession.hashCode()} sent")
                        if (rc < 0) {
                            Log.println(Log.DEBUG, "Honza", "session ${packetSession.hashCode()} send failed")
                            stats = updateAndEmitStats(
                                previous = stats,
                                ip = ip,
                                Icmp.PingResult.Failed.IO("Failed to send ICMP Echo: Os.sendto() failed with $rc")
                            )
                            continue
                        }
                        while (true) {
                            pollFd.events = POLLIN
                            rc = Os.poll(pollFds, timeoutMillis.toInt())
                            millis = System.currentTimeMillis() - timestamp
                            if (rc < 0) {
                                Log.println(Log.DEBUG, "Honza", "session ${packetSession.hashCode()} poll error")
                                throw Icmp.Error.SystemIO(message = "Listening for ICMP Echo response failed: Os.poll() failed with $rc")
                            }
                            if (pollFd.revents != POLLIN) {
                                stats = updateAndEmitStats(
                                    previous = stats,
                                    ip = ip,
                                    Icmp.PingResult.Failed.RequestTimeout(
                                        message = "Request timeout for ${ip.hostAddress} seq ${sentCount}",
                                        millis = timeoutMillis
                                    )
                                )
                                break
                            }

                            rc = recvfrom(fd, buffer)
                            if (rc < 0) {
                                Log.println(Log.DEBUG, "Honza", "session ${packetSession.hashCode()} read error")
                                throw Icmp.Error.SystemIO(message = "Reading ICMP Echo failed: Os.recvfrom() failed with $rc")
                            }

                            result = packetSession.processResponse(
                                buffer = ByteBuffer.wrap(buffer, 0, rc),
                                millis = millis
                            )

                            if (result == null) {
                                Log.println(Log.DEBUG, "Honza", "session ${packetSession.hashCode()} result null")
                            }

                            if (result != null) {
                                stats = updateAndEmitStats(
                                    previous = stats,
                                    ip = ip,
                                    result
                                )
                                break
                            }

                        }
                    } catch (e: ErrnoException) {
                        throw Icmp.Error.SystemIO(message = "Icmp session failed", e)
                    } catch (e: SocketException) {
                        throw Icmp.Error.SystemIO(message = "Icmp session failed", e)
                    }

                    delay(intervalMillis)
                }
            } finally {
                try {
                    Os.close(fd)
                } catch (_: ErrnoException) {
                }
            }
        }
            .flowOn(Dispatchers.IO)


    private fun recvfrom(fd: FileDescriptor, buffer: ByteArray): Int {
        val rc = Os.recvfrom(fd, buffer, 0, buffer.size, MSG_DONTWAIT, null)
        return if (rc == OsConstants.EMSGSIZE) {
            //incoming message longer then buffer
            buffer.size
        } else {
            rc
        }
    }
}