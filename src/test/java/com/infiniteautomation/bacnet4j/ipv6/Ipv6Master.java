package com.infiniteautomation.bacnet4j.ipv6;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableInt;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.RemoteDevice;
import com.serotonin.bacnet4j.ResponseConsumerAdapter;
import com.serotonin.bacnet4j.event.DeviceEventAdapter;
import com.serotonin.bacnet4j.npdu.ipv6.Ipv6Network;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyAck;
import com.serotonin.bacnet4j.service.acknowledgement.ReadPropertyMultipleAck;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyMultipleRequest;
import com.serotonin.bacnet4j.service.confirmed.ReadPropertyRequest;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.constructed.ReadAccessSpecification;
import com.serotonin.bacnet4j.type.constructed.SequenceOf;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.util.sero.ThreadUtils;

public class Ipv6Master {
    static LocalDevice localDevice;
    static RemoteDevice d;

    public static void main(String[] args) throws Exception {
        //        localDevice = new LocalDevice(6789, new DefaultTransport(new IpNetwork("192.168.0.255")));
        localDevice = new LocalDevice(6789, new DefaultTransport(new Ipv6Network("FF03::BAC0")));
        localDevice.getEventHandler().addListener(new DeviceEventAdapter() {
            @Override
            public void iAmReceived(RemoteDevice d) {
                System.out.println("Received IAm from " + d);
            }
        });

        try {
            localDevice.initialize();
            localDevice.sendGlobalBroadcast(new WhoIsRequest());

            ThreadUtils.sleep(3000);

            d = localDevice.getRemoteDevice(1969).get();

            ReadPropertyRequest req = new ReadPropertyRequest(d.getObjectIdentifier(), PropertyIdentifier.objectList,
                    null);
            ReadPropertyAck ack = localDevice.send(d, req).get();
            System.out.println(ack);

            SequenceOf<ObjectIdentifier> oids = ack.getValue();

            List<ReadAccessSpecification> specs = new ArrayList<ReadAccessSpecification>();
            for (ObjectIdentifier oid : oids)
                specs.add(new ReadAccessSpecification(oid, PropertyIdentifier.presentValue));
            ReadPropertyMultipleRequest mreq = new ReadPropertyMultipleRequest(new SequenceOf<ReadAccessSpecification>(
                    specs));

            deluge(mreq);

            System.out.println("Sleeping for 1 minute +1.");
            Thread.sleep(1000 * 60 + 1000);
            deluge(mreq);

            System.out.println("Sleeping for 5 minutes +1.");
            Thread.sleep(1000 * 60 * 5 + 1000);
            deluge(mreq);

            System.out.println("Sleeping for 7 minutes +1.");
            Thread.sleep(1000 * 60 * 7 + 1000);
            deluge(mreq);
        }
        finally {
            localDevice.terminate();
        }
    }

    static void deluge(ReadPropertyMultipleRequest mreq) throws Exception {
        int requests = 0;
        final MutableInt responses = new MutableInt();

        for (int i = 0; i < 100; i++) {
            requests++;
            localDevice.send(d, mreq, new ResponseConsumerAdapter<ReadPropertyMultipleAck>() {
                @Override
                public void ack(ReadPropertyMultipleAck ack) {
                    responses.increment();
                    //                    for (ReadAccessResult readResult : ack.getListOfReadAccessResults()) {
                    //                        System.out.println(readResult.getObjectIdentifier());
                    //                        for (Result result : readResult.getListOfResults())
                    //                            System.out.println("   " + result.getPropertyIdentifier() + ": "
                    //                                    + result.getReadResult().getDatum());
                    //                    }
                }
            });
            Thread.sleep(100);
        }
        System.out.println("Send " + requests + " requests, received " + responses + " responses");
    }
}
