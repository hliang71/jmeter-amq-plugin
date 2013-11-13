jmeter-amq-plugin
=================

jmeter amq rpc plugin to publish & consume messages from RabbitMQ or any Amq message bus.


JMeter Runtime Dependencies
---------------------------

Prior to building or installing this JMeter plugin, ensure that the RabbitMQ client library (amqp-client-3.x.x.jar) is installed in JMeter's lib/ directory.

Building
--------

The project is built using Maven. To execute the build script, just execute:
    mvn clean install 


Installing
----------

To install the plugin, build the project and copy the generated JMeterAMQP-1.0.0.jar file from target/dist to JMeter's lib/ext/ directory.

