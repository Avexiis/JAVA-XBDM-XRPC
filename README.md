# JAVA-XBDM-XRPC
A proof of concept that interacts with modified Xbox 360s using Java Swing GUI

This is a proof of concept tool that uses Java 8 (because it is what I had installed) to connect to a modified Xbox 360 console over TCP socket & interacts with XBDM and JRPC2/XRPC.

It currently is capable of grabbing console info (cpu key, kernel version, board type, title ID, temps), sending XNotify messages with the full set of icons, reading and setting memory, and reloading the title or shutting down the console. I explored a file browser but it was not yet reliable and I do not currently desire to expand this into a full project. I am posting this for anyone else to continue if they so desire.

![Screenshot.](https://hellstxr.b-cdn.net/cSFgJ9tBBU.png)
