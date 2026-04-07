package com.infiniteautomation.bacnet4j.ipv6;

import com.serotonin.bacnet4j.LocalDevice;
import com.serotonin.bacnet4j.npdu.ipv6.Ipv6Network;
import com.serotonin.bacnet4j.obj.BACnetObject;
import com.serotonin.bacnet4j.transport.DefaultTransport;
import com.serotonin.bacnet4j.type.constructed.ValueSource;
import com.serotonin.bacnet4j.type.enumerated.BinaryPV;
import com.serotonin.bacnet4j.type.enumerated.ObjectType;
import com.serotonin.bacnet4j.type.enumerated.PropertyIdentifier;
import com.serotonin.bacnet4j.type.primitive.ObjectIdentifier;
import com.serotonin.bacnet4j.type.primitive.Real;

public class Ipv6Slave {
    public static void main(String[] args) throws Exception {
        LocalDevice localDevice = new LocalDevice(9876, new DefaultTransport(new Ipv6Network("FF03::BAC0")));

        float av0Value = 0;
        BACnetObject av0 = new BACnetObject(localDevice, new ObjectIdentifier(ObjectType.analogValue, 0));
        av0.writeProperty(PropertyIdentifier.presentValue, new Real(av0Value));
        localDevice.addObject(av0);

        boolean bv0Value = false;
        BACnetObject bv0 = new BACnetObject(new ObjectIdentifier(ObjectType.binaryValue, 0));
        bv0.writeProperty(PropertyIdentifier.presentValue, bv0Value ? BinaryPV.active : BinaryPV.inactive);
        localDevice.addObject(bv0);

        localDevice.initialize();

        int iterations = 0;
        while (true) {
            Thread.sleep(1000);

            av0Value += 0.01F;
            av0.writeProperty(PropertyIdentifier.presentValue, new Real(av0Value));

            bv0Value = !bv0Value;
            bv0.writeProperty(PropertyIdentifier.presentValue, bv0Value ? BinaryPV.active : BinaryPV.inactive);

            iterations++;

            if (iterations % 30 == 0)
                System.out.println("Bytes in/out: " + localDevice.getBytesIn() + "/" + localDevice.getBytesOut());
        }
    }
}
