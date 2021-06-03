# Documentation

## Introduction
This is a **SmartHome application**, composed by two different networks of IoT devices.
The application in mainly based on the idea of switching on the light in a room whenever a person enter in the room, and switching off
 whenever no one is in the room. In addition to this, the humidity of the house is periodically monitored and, if needed, adjusted.

The devices are deployed in a house consisting of 5 rooms: **Kitchen**, **Dining Room**, **Living Room**, **Bathroom**, **Bedroom**.
As regard the **CoAP network**, there is a device for each room. Each device exposes two resources: a presence sensor and a light actuator.
For the **MQTT network**, there is a unique device deployed in the center of the house. This device measures the humidity percentage in the house and ,if needed, regulates it.
In addition to these 6 sensors, there is an additional device in charge of acting as a border router. This way, every sensor can comunicate with the external world, and in particular with the **Java Collector**, in order to periodically report data and receive actuation commands. The collector is teoretically deployed on the cloud, but in this project it runs on the same virtual machine where Cooja runs. This module is aimed to collect data reported by the sensors, output them to the user via a textual log and store them in a MySql database. In addition to this, it implements a control logic to regulate the behaviour of the sensors. 
To enable the communication between the MQTT device and the Java collector, an MQTT broker is needed. For this project is used Mosquitto, an open source implementation of an MQTT broker.

This is the overall scenario, where the orange sensors are CoAP ones and the green is the MQTT device. The blue device is the device acting as border router.

![deployment-iot](https://user-images.githubusercontent.com/73020009/120631128-cd4d4180-c467-11eb-865d-8fdc6b05e3f3.png)

Within the context of project, the presented scenario has been developed in this way: 
- The CoAP network has been simulated exploiting the Cooja simulator
- The MQTT network has been deployed on real devices from the IoT testbed
- The Java Collector runs on the same VM where Cooja runs

