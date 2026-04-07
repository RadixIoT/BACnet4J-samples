# BACnet4J-private
BACnet4J Sample Code

## NOTE this repo is duplicated code from BACnet4J and the majority has not been ported for the latest BACnet4J release

# Unicast device discovery

Build the jar file:

    mvn package -P runnable-jar -Dmaven.test.skip=true

Run the jar file. Remove the 'target/' prefix as necessary and alter other settings.

    java -jar target/BACnetUnicastDiscovery.jar \
        --localBindAddress 192.168.0.18 \
        --broadcastAddress 192.168.0.255/24 \
        --deviceId 1234 \
        --target 192.168.0.24/29 \
        --target 192.168.0.29

See full help:

    java -jar target/BACnetUnicastDiscovery.jar
