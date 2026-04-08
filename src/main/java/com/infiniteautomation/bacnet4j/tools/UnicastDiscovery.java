/*
 * ============================================================================
 * GNU General Public License
 * ============================================================================
 *
 * Copyright (C) 2025 Radix IoT LLC. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * When signing a commercial license with Radix IoT LLC,
 * the following extension to GPL is made. A special exception to the GPL is
 * included to allow you to distribute a combined work that includes BAcnet4J
 * without being obliged to provide the source code for any proprietary components.
 *
 * See www.radixiot.com for commercial license options.
 */

package com.infiniteautomation.bacnet4j.tools;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.event.DefaultDeviceEventListener;
import com.serotonin.bacnet4j.exception.BACnetException;
import com.serotonin.bacnet4j.exception.BACnetTimeoutException;
import com.serotonin.bacnet4j.exception.ErrorAPDUException;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkUtils;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.constructed.Address;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;

import java.net.NoRouteToHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Sends a unicast WhoIs to one or more target IP addresses and prints the IAm responses received.
 *
 * <p>Usage:
 * <pre>
 *   java -jar BACnetUnicastDiscovery.jar \
 *     --localBindAddress 192.168.0.18 \
 *     --broadcastAddress 192.168.0.255/24 \
 *     --target 192.168.0.29 \
 *     --target 192.168.0.30 \
 *     [--whoIs true] \
 *     [--readProperty true] \
 *     [--port 47808] \
 *     [--deviceId 1]
 *     [--timeout 5] \
 *     [--retries 2] \
 * </pre>
 */
public class UnicastDiscovery {
    public static void main(String[] args) throws Exception {
        var ud = new UnicastDiscovery();

        ud.parseArguments(args);
        ud.parseTargets();
        try {
            ud.initialize();
            ud.discover();
        } finally {
            ud.terminate();
        }
    }

    String localBindAddress = null;
    String broadcastAddress = null;
    final List<String> targets = new ArrayList<>();
    boolean sendWhoIs = true;
    boolean sendReadProperty = true;
    int port = 47808;
    int timeout = 5;
    int retries = 2;
    int deviceId = 1;
    List<String> addresses;
    LocalDevice localDevice;
    Map<String, RemoteDevice> iAmResponses = new ConcurrentHashMap<>();

    void parseArguments(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--localBindAddress" -> localBindAddress = args[++i];
                case "--broadcastAddress" -> broadcastAddress = args[++i];
                case "--target" -> targets.add(args[++i]);
                case "--whoIs" -> sendWhoIs = Boolean.parseBoolean(args[++i]);
                case "--readProperty" -> sendReadProperty = Boolean.parseBoolean(args[++i]);
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--timeout" -> timeout = Integer.parseInt(args[++i]);
                case "--retries" -> retries = Integer.parseInt(args[++i]);
                case "--deviceId" -> deviceId = Integer.parseInt(args[++i]);
                default -> {
                    System.err.println(paint(String.format("Unknown argument: %s%n", args[i]), AnsiColor.RED));
                    printUsageAndExit();
                }
            }
        }

        if (localBindAddress == null || broadcastAddress == null || targets.isEmpty()) {
            System.err.println(paint("--localBindAddress, --broadcastAddress, and at least one --target are required.",
                    AnsiColor.RED));
            printUsageAndExit();
        }

        if (!sendWhoIs && !sendReadProperty) {
            System.err.println(paint("At least one of whoIs or readProperty must be true.", AnsiColor.RED));
            printUsageAndExit();
        }
    }

    void printUsageAndExit() {
        System.err.println("""
                Usage: UnicastDiscovery
                  --localBindAddress <ip>        Local interface IP to bind to (required)
                  --broadcastAddress <ip/prefix> Broadcast address with prefix length in CIDR notation (required, e.g. 192.168.0.255/24)
                  --target <ip>                  Target IP to send WhoIs to (required, repeatable)
                  --whoIs <boolean>              Attempt discovery with unicast WhoIs (default: true)
                  --readProperty <boolean>       Attempt discovery with read property service (default: true)
                  --port <port>                  BACnet/IP port (default: 47808)
                  --deviceId <id>                Local device instance number (default: 1)
                  --timeout <seconds>            Seconds to wait for responses (default: 5)
                  --retries <count>              Number of ReadProperty retries per target (default: 2)
                
                At least one of whoIs or readProperty must be true.
                
                Target strings can be IP addresses (e.g. 192.168.0.123), CIDR defined ranges (e.g. 192.168.0.112/28),
                or dash-delimited ranges in octets (e.g. 192.168.1-4.50-79).
                """);
        System.exit(1);
    }

    void initialize() throws Exception {
        var slash = broadcastAddress.indexOf('/');
        if (slash == -1) {
            System.err.println(
                    paint("--broadcastAddress must be in CIDR notation, e.g. 192.168.0.255/24", AnsiColor.RED));
            printUsageAndExit();
        }
        var broadcastIp = broadcastAddress.substring(0, slash);
        var prefixLength = Integer.parseInt(broadcastAddress.substring(slash + 1));

        var network = new IpNetworkBuilder()
                .withLocalBindAddress(localBindAddress)
                .withBroadcast(broadcastIp, prefixLength)
                .withPort(port)
                .withReuseAddress(true)
                .build();
        var transport = new DefaultTransport(network);
        transport.setTimeout(timeout);
        transport.setRetries(retries);

        localDevice = new LocalDevice(deviceId, transport);
        localDevice.initialize();

        localDevice.getEventHandler().addListener(new DefaultDeviceEventListener() {
            @Override
            public void iAmReceived(RemoteDevice d) {
                var addr = IpNetworkUtils.toIpString(d.getAddress().getMacAddress());
                iAmResponses.put(addr, d);
            }
        });
    }

    void terminate() {
        if (localDevice != null) {
            localDevice.terminate();
        }
    }

    void parseTargets() {
        var parsed = new ArrayList<String>();
        for (String target : targets) {
            var hasCidr = target.contains("/");
            var hasDash = hasDashRange(target);
            if (hasCidr && hasDash)
                exitWithError("Target '%s' cannot combine dash ranges with CIDR notation", target);
            if (hasCidr) {
                expandCidr(target, parsed);
            } else if (hasDash) {
                expandDashRange(target, parsed);
            } else {
                parsePlainIp(target, parsed);
            }
        }

        var total = parsed.size();
        addresses = parsed.stream().distinct().toList();

        System.out.println(paint(String.format("Parsed %d distinct target address(es), %d duplicate(s).",
                addresses.size(), total - addresses.size()), AnsiColor.GREEN));
    }

    void discover() throws InterruptedException {
        var pool = Executors.newCachedThreadPool();

        for (String addr : addresses) {
            pool.submit(() -> discover(addr));
        }

        pool.shutdown();
        if (!pool.awaitTermination(2, TimeUnit.MINUTES)) {
            System.err.println(paint("Pool failed to complete", AnsiColor.RED));
        }
    }

    void discover(String addr) {
        Address address = new Address(IpNetworkUtils.toOctetString(addr, port));

        if (sendWhoIs) {
            tryWhoIs(addr, address);
        }
        if (sendReadProperty) {
            tryReadProperty(addr, address);
        }
    }

    void tryWhoIs(String addr, Address address) {
        localDevice.send(address, new WhoIsRequest());

        waitFor(timeout, () -> {
            RemoteDevice rd = iAmResponses.get(addr);
            if (rd != null) {
                System.out.println(paint(String.format("WhoIs found device ID %d at %s", rd.getInstanceNumber(), addr),
                        AnsiColor.BLUE));
                return true;
            }
            return false;
        });
    }

    void tryReadProperty(String addr, Address address) {
        try {
            ReadPropertyAck ack = localDevice.send(address, new ReadPropertyRequest(
                    new ObjectIdentifier(ObjectType.device, 0x3FFFFF),
                    PropertyIdentifier.objectIdentifier)
            ).get();
            ObjectIdentifier oid = ack.getValue();
            System.out.println(
                    paint(String.format("ReadProperty found device ID %d at %s", oid.getInstanceNumber(), addr),
                            AnsiColor.BLUE));
        } catch (ErrorAPDUException e) {
            // Weird, Something BACnet-like is here, but it didn't respond as expected.
            System.out.println(
                    paint(String.format("Unexpected response from %s: %s", addr, e.getMessage()), AnsiColor.MAGENTA));
        } catch (BACnetException e) {
            if (e.getCause() == null ||
                    e.getCause() instanceof NoRouteToHostException ||
                    e.getCause() instanceof BACnetTimeoutException) {
                // Nothing found here. Ignore.
            } else {
                System.out.printf("Error in to '%s': %s:%s%n", addr, e.getCause().getClass().getName(),
                        e.getCause().getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean waitFor(long timeout, BooleanSupplier task) {
        Instant deadline = Instant.now().plusSeconds(timeout);
        while (true) {
            boolean done = task.getAsBoolean();
            if (done || Instant.now().isAfter(deadline)) {
                return done;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    private boolean hasDashRange(String target) {
        for (String octet : target.split("\\.")) {
            if (octet.contains("-"))
                return true;
        }
        return false;
    }

    private void parsePlainIp(String target, List<String> out) {
        var parts = target.split("\\.", -1);
        if (parts.length != 4)
            exitWithError("Target '%s': expected 4 octets, got %d", target, parts.length);
        for (int i = 0; i < 4; i++)
            parseOctet(target, parts[i], i + 1);
        out.add(target);
    }

    private void expandCidr(String target, List<String> out) {
        var slash = target.indexOf('/');
        var ip = target.substring(0, slash);
        var prefixStr = target.substring(slash + 1);

        int prefixLength;
        try {
            prefixLength = Integer.parseInt(prefixStr);
        } catch (NumberFormatException e) {
            exitWithError("Target '%s': prefix length '%s' is not a valid integer", target, prefixStr);
            return;
        }
        if (prefixLength < 0 || prefixLength > 32)
            exitWithError("Target '%s': prefix length %d is out of range [0, 32]", target, prefixLength);

        var parts = ip.split("\\.", -1);
        if (parts.length != 4)
            exitWithError("Target '%s': expected 4 octets, got %d", target, parts.length);

        int ipInt = 0;
        for (int i = 0; i < 4; i++)
            ipInt = (ipInt << 8) | parseOctet(target, parts[i], i + 1);

        var mask = prefixLength == 0 ? 0 : (0xFFFFFFFF << (32 - prefixLength));
        var network = ipInt & mask;
        var broadcast = network | ~mask;

        // Enumerate host addresses, including the network and broadcast addresses
        for (int addr = network; addr <= broadcast; addr++)
            out.add(intToIp(addr));
    }

    private void expandDashRange(String target, List<String> out) {
        var octets = target.split("\\.", -1);
        if (octets.length != 4)
            exitWithError("Target '%s': expected 4 octets, got %d", target, octets.length);

        var ranges = new int[4][2];
        for (int i = 0; i < 4; i++) {
            var dash = octets[i].indexOf('-');
            if (dash == -1) {
                var v = parseOctet(target, octets[i], i + 1);
                ranges[i][0] = v;
                ranges[i][1] = v;
            } else {
                var start = parseOctet(target, octets[i].substring(0, dash), i + 1);
                var end = parseOctet(target, octets[i].substring(dash + 1), i + 1);
                if (start > end)
                    exitWithError("Target '%s': octet %d range start %d exceeds end %d", target, i + 1, start, end);
                ranges[i][0] = start;
                ranges[i][1] = end;
            }
        }

        for (int a = ranges[0][0]; a <= ranges[0][1]; a++)
            for (int b = ranges[1][0]; b <= ranges[1][1]; b++)
                for (int c = ranges[2][0]; c <= ranges[2][1]; c++)
                    for (int d = ranges[3][0]; d <= ranges[3][1]; d++)
                        out.add(String.format("%d.%d.%d.%d", a, b, c, d));
    }

    private int parseOctet(String target, String value, int octetNum) {
        int v;
        try {
            v = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            exitWithError("Target '%s': octet %d '%s' is not a valid integer", target, octetNum, value);
            return 0;
        }
        if (v < 0 || v > 255)
            exitWithError("Target '%s': octet %d value %d is out of range [0, 255]", target, octetNum, v);
        return v;
    }

    private void exitWithError(String message, Object... args) {
        String error = String.format(message, args);
        System.err.println(paint(error, AnsiColor.RED));
        System.exit(1);
    }

    private String intToIp(int addr) {
        return ((addr >> 24) & 0xFF) + "." + ((addr >> 16) & 0xFF) + "." + ((addr >> 8) & 0xFF) + "." + (addr & 0xFF);
    }

    enum AnsiColor {
        BLACK(30), RED(31), GREEN(32), YELLOW(33), BLUE(34), MAGENTA(35), CYAN(36), WHITE(37);

        final int code;

        AnsiColor(int code) {
            this.code = code;
        }
    }

    private static String paint(String text, AnsiColor color) {
        return "\u001B[" + color.code + "m" + text + "\u001B[0m";
    }
}
