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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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

    private sealed class Destination {
        data class IP(val ip: InetAddress) : Destination()
        data class Hostname(val host: String) : Destination()
    }

    /**
     *
     *
     * @return file descriptor of the new socket
     */
    private fun createSocketForDestination(destination: InetAddress): FileDescriptor {
        try {
            val fd = when (destination) {
                is Inet4Address -> Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
                is Inet6Address -> Os.socket(OsConstants.AF_INET6, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMPV6)
                else -> throw IllegalStateException("Unsupported destination address type ${destination.javaClass.canonicalName}")
            }

            if (!fd.valid()) throw Icmp.Error.SocketException(message = "Created file descriptor is invalid")

            return fd
        } catch (e: ErrnoException) {
            throw Icmp.Error.SocketException(message = "Socket creation failed", cause = e)
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
                throw Icmp.Error.SocketException(message = "Failed to set IP_TOS to low delay")
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
            throw Icmp.Error.SocketException(message = "Failed to bind socket to specified network")
        }
    }

    private fun newPacketSessionForDestination(destination: InetAddress): IcmpSession<*, *> =
        when (destination) {
            is Inet4Address -> IcmpV4Session()
            is Inet6Address -> IcmpV6Session()
            else -> throw IllegalArgumentException("Unsupported destination address type ${destination.javaClass.canonicalName}")
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
                if (hostname.isNotBlank()) {
                    withTimeout(timeoutMillis) {
                        HostnameResolutionResult.Success(
                            inetAddress = InetAddress.getByName(hostname)
                        )
                    }
                } else {
                    HostnameResolutionResult.Failed.UnknownHost(cause = UnknownHostException("Cannot resolve empty host"))
                }
            } catch (e: UnknownHostException) {
                HostnameResolutionResult.Failed.UnknownHost(cause = e)
            } catch (e: SecurityException) {
                HostnameResolutionResult.Failed.SecurityError(cause = e)
            } catch (e: TimeoutCancellationException) {
                HostnameResolutionResult.Failed.Timeout(cause = e)
            }
        }

    private suspend fun ping(
        destination: Destination,
        timeoutMillis: Long,
        packetSize: Int,
        network: Network?
    ): Icmp.PingStatus =
        pingInterval(
            destination = destination,
            count = 1,
            timeoutMillis = timeoutMillis,
            packetSize = packetSize,
            intervalMillis = Long.MAX_VALUE,
            network = network
        )
            .first()

    private fun pingInterval(
        destination: Destination,
        count: Int?,
        timeoutMillis: Long,
        packetSize: Int,
        intervalMillis: Long,
        network: Network?
    ): Flow<Icmp.PingStatus> =
        callbackFlow<Icmp.PingStatus> {
            val ip = when (destination) {
                is Destination.IP -> destination.ip
                is Destination.Hostname ->
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
                            throw Icmp.Error.UnknownHost(
                                message = "Security manager exists doesn't allow hostname resolution"
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
                val statusManager = IcmpPingStatusManager(ip = ip)
                while (count == null || sentCount++ < count) {
                    try {
                        timestamp = System.currentTimeMillis()
                        rc = Os.sendto(fd, packetSession.nextRequest(), 0, ip, Icmp.PORT)
                        if (rc < 0) {
                            send(statusManager.update(Icmp.PingResult.Failed.IO("sendto failed with $rc")))
                            continue
                        }
                        while (true) {
                            pollFd.events = POLLIN
                            rc = Os.poll(pollFds, timeoutMillis.toInt())
                            millis = System.currentTimeMillis() - timestamp
                            if (rc < 0) {
                                send(statusManager.update(Icmp.PingResult.Failed.IO(message = "poll failed with $rc")))
                                break
                            }
                            if (pollFd.revents != POLLIN) {
                                send(
                                    statusManager.update(
                                        Icmp.PingResult.Failed.RequestTimeout(
                                            message = "Request timeout for ${ip.hostAddress} seq $sentCount",
                                            millis = timeoutMillis
                                        )
                                    )
                                )
                                break
                            }

                            rc = recvfrom(fd, buffer)
                            if (rc < 0) {
                                send(statusManager.update(Icmp.PingResult.Failed.IO(message = "recvfrom failed with $rc")))
                                break
                            }

                            result = try {
                                packetSession.processResponse(
                                    buffer = ByteBuffer.wrap(buffer, 0, rc),
                                    millis = millis
                                )
                            } catch (e: IcmpMessageSerializer.InvalidMessageContentException) {
                                throw Icmp.Error.ProtocolException(
                                    message = "Failed to deserialize incoming packet",
                                    cause = e
                                )
                            }

                            if (result != null) {
                                send(statusManager.update(result))
                                break
                            }

                        }
                    } catch (e: ErrnoException) {
                        val errIdStr = "Err: ${e.errno}"
                        send(statusManager.update(Icmp.PingResult.Failed.IO(message = e.message?.let { "${it} ($errIdStr)" } ?: errIdStr)))
                    } catch (e: SocketException) {
                        send(statusManager.update(Icmp.PingResult.Failed.IO(message = e.message ?: "Socket Exception")))
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

    override suspend fun ping(host: String, timeoutMillis: Long, packetSize: Int, network: Network?): Icmp.PingStatus =
        ping(
            destination = Destination.Hostname(host),
            timeoutMillis = timeoutMillis,
            packetSize = packetSize,
            network = network
        )

    override suspend fun ping(ip: InetAddress, timeoutMillis: Long, packetSize: Int, network: Network?): Icmp.PingStatus =
        ping(
            destination = Destination.IP(ip),
            timeoutMillis = timeoutMillis,
            packetSize = packetSize,
            network = network
        )

    override fun pingInterval(
        host: String,
        count: Int?,
        timeoutMillis: Long,
        packetSize: Int,
        intervalMillis: Long,
        network: Network?
    ): Flow<Icmp.PingStatus> =
        pingInterval(
            destination = Destination.Hostname(host),
            count = count,
            timeoutMillis = timeoutMillis,
            packetSize = packetSize,
            intervalMillis = intervalMillis,
            network = network
        )

    override fun pingInterval(
        ip: InetAddress,
        count: Int?,
        timeoutMillis: Long,
        packetSize: Int,
        intervalMillis: Long,
        network: Network?
    ): Flow<Icmp.PingStatus> =
        pingInterval(
            destination = Destination.IP(ip),
            count = count,
            timeoutMillis = timeoutMillis,
            packetSize = packetSize,
            intervalMillis = intervalMillis,
            network = network
        )

    companion object {
        private const val RESPONSE_BUFFER_SIZE = 64

        private const val IPTOS_LOWDELAY = 0x10

        protected val POLLIN = (if (OsConstants.POLLIN == 0) 1 else OsConstants.POLLIN).toShort()
        private const val MSG_DONTWAIT = 0x40
    }

}