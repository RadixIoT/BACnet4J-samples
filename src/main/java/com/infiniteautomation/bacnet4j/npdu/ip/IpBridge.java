/**
 * Copyright (C) 2018 Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.bacnet4j.npdu.ip;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ip.IpNetwork;

/**
 * Class to bridge 2 Local devices together so they can communicate on the same machine.
 * 
 * @see com.serotonin.bacnet4j.npdu.test.TestNetwork
 * 
 * @author Terry Packer
 *
 */
public class IpBridge extends Thread implements Closeable {

    final DatagramSocket local; // Port to receive forwarded messages

    final DatagramSocket receiveBroadcast;
    final DatagramSocket send;

    InetSocketAddress localAddress;
    InetSocketAddress forwardTo; // Socket Address to forward messages to
    InetSocketAddress recieveAddress;
    InetSocketAddress sendAddress;

    Thread thread;
    Thread broadcastReceiverThread;


    public IpBridge(final int listenOn, final LocalDevice device1, final LocalDevice device2) throws SocketException {
            super("Bridge from " + device1.getId() + " to " + device2.getId());
            
            DatagramSocket recieveSocket = ((IpNetwork)device1.getNetwork()).getSocket();
            DatagramSocket sendSocket = ((IpNetwork)device2.getNetwork()).getSocket();
            
            this.receiveBroadcast = new DatagramSocket(null);
            this.receiveBroadcast.setReuseAddress(true);
            this.receiveBroadcast.bind(new InetSocketAddress(recieveSocket.getLocalPort()));
            
            this.send = new DatagramSocket(null);
            this.send.setReuseAddress(true);
            this.send.bind(new InetSocketAddress(listenOn));

            this.localAddress = new InetSocketAddress(sendSocket.getLocalAddress(), listenOn);
            this.local = new DatagramSocket(null);
            this.local.setReuseAddress(true);
            this.local.bind(localAddress);
            
            this.recieveAddress = (InetSocketAddress)recieveSocket.getLocalSocketAddress();
            this.sendAddress = (InetSocketAddress)sendSocket.getLocalSocketAddress();
            
            broadcastReceiverThread = new Thread("Relay Broadcast to " + send.getLocalPort()) { 
                @Override
                public void run() {
                    final DatagramPacket p = new DatagramPacket(new byte[1024], 1024);
                    while (true) {
                        if(receiveBroadcast.isClosed())
                            break;
                        try {
                            receiveBroadcast.receive(p);

                            // Use that socket to send to individual addresses.
                            final DatagramPacket fwd = new DatagramPacket(p.getData(), p.getLength(), sendAddress);
                            synchronized(send) {
                                send.send(fwd);
                            }
                        } catch (final SocketException e) {
                            if ("Socket closed".equalsIgnoreCase(e.getMessage()))
                                break;

                            // ignore
                            e.printStackTrace();
                        } catch (final IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
        }

    @Override
    public void run() {
        thread = Thread.currentThread();

        // Startup a broadcast receiver thread
        broadcastReceiverThread.start();
        final DatagramPacket p = new DatagramPacket(new byte[1024], 1024);
        while (true) {
            if (local.isClosed())
                break;
            try {
                local.receive(p);
                DatagramPacket fwd;
                if (p.getPort() == sendAddress.getPort())
                    fwd = new DatagramPacket(p.getData(), p.getLength(), recieveAddress);
                else
                    fwd = new DatagramPacket(p.getData(), p.getLength(), sendAddress);

                synchronized (send) {
                    send.send(fwd);
                }
            } catch (final SocketException e) {
                if ("Socket closed".equalsIgnoreCase(e.getMessage()))
                    break;

                // ignore
                e.printStackTrace();
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }

    public DatagramSocket getLocal() {
        return local;
    }

    @Override
    public void close() {
        receiveBroadcast.close();
        local.close();
        send.close();

        try {
            if (thread != null)
                thread.join();
            if (broadcastReceiverThread != null)
                broadcastReceiverThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }
}
