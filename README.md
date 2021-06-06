# IoT-project
This project was developed for the AIDE MSc's IoT course at the University of Pisa

# Description
An IoT **telemetry and control system**, composed by two different networks of IoT devices:
- An **MQQT network**, deployed on physical sensors from the University of Pisa IoT Testbed
- A **CoAP network**, simulated on Cooja

A **Java application** that collects data from both these networks and apply the following operations:
- Outputs the data via a textual log
- Stores the data in a MySql database
- Applies a control logic based on the collected data

This is the overall system architecture:

![Screenshot (11)](https://user-images.githubusercontent.com/73020009/120617398-5a899980-c45a-11eb-9742-2198a8cebde5.png)

# Use-case
The use-case is a SmartHome application, with two functionalities:
- Detect presence in a room and turn on/off the light in that room
- Collect data about the humidity percentage in the house and regulate it

# How to use
The 3 code folders must be placed in **contiki-ng/examples/**
