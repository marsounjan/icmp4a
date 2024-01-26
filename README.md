icmp4a
======
modern ICMP Echo (Ping) library for Android written in pure Kotlin

Features
------
1. **ICMPv4 Echo/Ping** [RFC][rfcIcmpV4]
2. **ICMPv6 Echo/Ping** [RFC][rfcIcmpV6]
3. bind ICMP sockets on speficied (not default) [android.net.Network][androidNetwork]

How Does It Work?
------
1. No `SOCK_RAW` (Raw sockets) - banned on Android
2. No executing `ping` command via [Runtime.exec()](https://developer.android.com/reference/java/lang/Runtime#exec(java.lang.String[])) in separate process


`icmp4a` is based on using `IPPROTO_ICMP` socket kind and datagram `SOCK_DGRAM` sockets.\
[As described here][icmpProtoSocketKind], it makes it possible to send `ICMP_ECHO` messages and receive the corresponding `ICMP_ECHOREPLY` messages without any special privileges.\


Single ICMP Ping
------

```kotlin
val icmp = Icmp4a()
val host = "google.com"
try {
  val status = icmp.ping( host = "8.8.8.8")
  val result = status.result
  when (result) {
    is Icmp.PingResult.Success -> Log.d("ICMP", "$host(${status.ip.hostAddress}) ${result.packetSize} bytes - ${result.ms} ms")
    is Icmp.PingResult.Failed -> Log.d("ICMP", "$host(${status.ip.hostAddress}) Failed: ${result.message}")
  }
} catch (error: Icmp.Error.UnknownHost) {
  Log.d("ICMP", "Unknown host $host")
}
```
**Logcat output**
```logcat
2024-01-26 16:22:48.515 ICMP D  google.com(8.8.8.8) 64 bytes - 12 ms
```

Multiple ICMP Pings with specified interval
------
```kotlin
val icmp = Icmp4a()
val host = "google.com"
try {
  icmp.pingInterval(
      "8.8.8.8",
      count = 5,
      intervalMillis = 1000
  )
    .onEach { status ->
        val result = status.result
        when (result) {
          is Icmp.PingResult.Success -> Log.d("ICMP", "$host(${status.ip.hostAddress}) ${result.packetSize} bytes - ${result.ms} ms")
          is Icmp.PingResult.Failed -> Log.d("ICMP", "$host(${status.ip.hostAddress}) Failed: ${result.message}")
        }
    }
      .launchIn(viewModelScope)
} catch (error: Icmp.Error.UnknownHost) {
  Log.d("ICMP", "Unknown host $host")
}
```
**Logcat output**
```
2024-01-26 16:29:16.633 ICMP D  google.com(8.8.8.8) 64 bytes - 15 ms
2024-01-26 16:29:17.577 ICMP D  google.com(8.8.8.8) 64 bytes - 17 ms
2024-01-26 16:29:18.598 ICMP D  google.com(8.8.8.8) 64 bytes - 17 ms
2024-01-26 16:29:19.614 ICMP D  google.com(8.8.8.8) 64 bytes - 15 ms
2024-01-26 16:29:20.630 ICMP D  google.com(8.8.8.8) 64 bytes - 13 ms
```

Requirements
------------

Works on **Android 6.0+** (**API level 23+**) and **Java 8+**.\
*In theory it should work even on API level 21, but I had device to test it.*

Releases
--------

The library is available on [Maven Central][mavenCentralIcmp4a].

```groovy
implementation "com.marsounjan:icmp4a:1.0.0"
```
Check [Releases][releases] on this repository to get latest version and release history with changelog.

```groovy
//don't forget to check you have mavenCentral repo listed in your project build.gradle
buildscript {
...
    repositories {
        mavenCentral()
    }
...
}
```

Demo app
------
Check out the [demo app](/demo) to get more idea about on how the library works.

License
-------

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

 [releases]: https://github.com/marsounjan/icmp4a/releases
 [mavenCentralIcmp4a]: https://search.maven.org/artifact/com.marsounjan/icmp4a/1.0.0/aar
 [icmpProtoSocketKind]: https://lwn.net/Articles/443051
 [rfcIcmpV4]: https://datatracker.ietf.org/doc/html/rfc792
 [rfcIcmpV6]: https://datatracker.ietf.org/doc/html/rfc4443
 [androidNetwork]: https://developer.android.com/reference/android/net/Network
