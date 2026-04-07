package com.infiniteautomation.bacnet4j.ipv6;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.apdu.APDU;
import com.serotonin.bacnet4j.apdu.ComplexACK;
import com.serotonin.bacnet4j.apdu.UnconfirmedRequest;
import com.serotonin.bacnet4j.npdu.Network;
import com.serotonin.bacnet4j.npdu.ip.IpNetworkBuilder;
import com.serotonin.bacnet4j.service.unconfirmed.UnconfirmedRequestService;
import com.serotonin.bacnet4j.service.unconfirmed.WhoIsRequest;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.transport.Transport;
import com.serotonin.bacnet4j.type.constructed.ServicesSupported;
import com.serotonin.bacnet4j.type.primitive.UnsignedInteger;
import com.serotonin.bacnet4j.util.sero.ByteQueue;
import com.serotonin.bacnet4j.util.sero.Utils;

// For Sumeet Garg of ca.com
public class Garg2 {
    public static void main(String[] args) throws Exception {
        //        parse();
        whois();
    }

    static void whois() throws Exception {
        Network network = new IpNetworkBuilder().withBroadcast("10.255.255.255", 8).build();
        Transport transport = new DefaultTransport(network);
        LocalDevice localDevice = new LocalDevice(1234, transport);
        localDevice.initialize();

        localDevice.sendGlobalBroadcast(new WhoIsRequest(new UnsignedInteger(560002), new UnsignedInteger(560002)));

        Thread.sleep(1000);
        localDevice.terminate();
    }

    static void parse() throws Exception {
        // UnconfirmedPrivateTransferRequest
        //        String input = "10,4,9,18,19,3,2e,65,14,0,2,0,78,a,21,38,e8,2,8,8b,80,56,b,d7,4e,1,2c,15,e0,2f";

        // IAmRequest
        //        String input = "10,0,c4,2,8,8b,80,22,1,e0,91,3,21,18";

        // ReadPropertyAck
        String input = "30,0,c,c,2,8,8b,80,19,61,3e,85,6,0,df,cb,e8,3a,f9,3f";

        ByteQueue queue = new ByteQueue(Utils.commaSeparatedHex(input));
        ServicesSupported servicesSupported = new ServicesSupported();
        servicesSupported.setAll(true);

        APDU apdu = APDU.createAPDU(servicesSupported, queue);

        if (apdu instanceof UnconfirmedRequest) {
            UnconfirmedRequest req = (UnconfirmedRequest) apdu;
            req.parseServiceData();
            UnconfirmedRequestService service = req.getService();
            System.out.println(service);
        }
        else if (apdu instanceof ComplexACK) {
            ComplexACK ack = (ComplexACK) apdu;
            ack.parseServiceData();
            //            UnconfirmedRequestService service = ack.getService();
            System.out.println(ack);
        }
        else
            System.out.println(apdu);
    }
}
