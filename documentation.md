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

This is the overall scenario, where the orange sensors are CoAP ones and the green one is the MQTT device. The blue device is the device acting as border router.

![deployment-iot](https://user-images.githubusercontent.com/73020009/120770620-9ede6d80-c51e-11eb-9680-687145eced2e.png)

Within the context of project, the presented scenario has been developed in this way: 
- The CoAP network has been simulated exploiting the Cooja simulator
- The MQTT network (including the MQTT Broker) has been deployed on real devices from the IoT testbed
- The Java Collector has been deployed on the same VM where Cooja runs
- The MySql Database runs locally on the VM 

As regards the MQTT network, it is composed by 2 real sensors (*/dev/ttyACM31* and */dev/ttyACM83*) of the testbed. The */dev/ttyACM83* acts as border router, while the */dev/ttyACM31* is the actual humidity sensor/actuator.

## MQTT Network
This network is deployed on real sensors from the testbed and consists of 2 devices: a humidity sensor/actuator and a border router. This last device allow the sensor to access the MQTT broker (deployed on the testbed as well) to publish or retrieve informations. More in depth, the */dev/ttyACM83* acts as border router, while the */dev/ttyACM31* is the actual humidity sensor/actuator.

![testbed](https://user-images.githubusercontent.com/73020009/120769865-d567b880-c51d-11eb-86ea-585a18f420eb.png)

The sensor reports periodically, **every 30 seconds**, a value that represents the relative humidity percentage measured in the house. This value must be in the range 40%-60%. If this is not the case, the percentage must be restored within this interval. This control operations will be performed by the collector that, if an out-of-range is detected, will notify the MQTT device through an actuator command.

The communication between the MQTT sensor and the Collector takes place through the MQTT Broker, implemented on the testbed with Mosquitto.

### Data Reporting
The MQTT device reports periodically the relative humidity percentage in the house by **publishing on the topic *humidity***. By **subscring** on the same topic, the Java Collector receives periodically all these measurements.

This is the related MQTT sensor code:
```
if(state == STATE_SUBSCRIBED){

	struct tm* tmp;
	char date [20];
	char time [20];
	etimer_set(&publish_timer, DEFAULT_PUBLISH_INTERVAL);
	PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&publish_timer));

	sprintf(pub_topic, "%s", "humidity");

	if(set == false){
		humidity_perc = (rand() % (80 - 20 + 1)) + 20;
	}
	set = false;

	current_time=time(NULL);
	tmp=gmtime(&current_time);
	sprintf(date,"%d-%d-%d",tmp->tm_mday,(tmp->tm_mon+1),(tmp->tm_year+1900));
	sprintf(time,"%d:%d:%d",tmp->tm_hour,tmp->tm_min,tmp->tm_sec);

	sprintf(app_buffer, "{\"node_id\":%d,\"date\":\"%s\",\"time\":\"%s\",\"humidity\":%d}",node_id,date,time,humidity_perc);
	mqtt_publish(&conn, NULL, pub_topic, (uint8_t *)app_buffer, strlen(app_buffer), MQTT_QOS_LEVEL_0, MQTT_RETAIN_OFF);
               
}
```
The humidity sensor behaviour is emulated generating every 30 seconds a random integer between 20 and 80. If the humidity percentage has been set before (*set=true*), this random generation is not performed and the humidity value previously set is used. This is done to make the situation a little bit more realistic. Once measured, this percentage is embedded in a json message with the following structure:
```
{
 "node_id" : 2
 "date" : "3-6-2021"
 "time" : "16:45:32"
 "humidity" : 55
}
```
Finally, the message is published on the topic "humidity" (*pub_topic*).

The Java Collector, subscribed to the broker on the topic *humidity*, executes the following code every time a measurement is published on that topic:

```
public void messageArrived(String topic, MqttMessage message) throws Exception {
		
	String json_message = new String(message.getPayload());
	System.out.println(json_message);
	JSONParser parser = new JSONParser();
	JSONObject jsonObject = null;
	try {
		jsonObject = (JSONObject) parser.parse(json_message);
	} catch (ParseException e) {
		e.printStackTrace();
	}
	if(jsonObject != null) {
		long node_id = (Long) jsonObject.get("node_id");
		String date = (String) jsonObject.get("date");
		String time = (String) jsonObject.get("time");
		long humidity = (Long) jsonObject.get("humidity");

		storeMqttData(node_id,humidity,date,time);
			
		if(humidity>60 || humidity<40) {
			humidityControl(node_id,date,time,humidity);
		}
	}
}
```
Once the json payload of the message is extracted and shown to the user, it is parsed using the *json simple* Java library. The data are stored in the database by the function *storeMqttData*. Then, we have the **control logic**: if the humidity value is out of the range 40%-60%, the function *humidityControl* is executed. The code of the function is the following:
```
public void humidityControl(long node_id,final String date,final String time,long humidity) {
		
	final int new_humidity = (int) ((Math.random() * (61 - 40)) + 40);
	System.out.println("["+date+" "+time+"] Humidity out of range : setting it to "+new_humidity+" %");

	try {
		JSONObject obj = new JSONObject();
		obj.put("humidity", new_humidity);
		MqttMessage message = new MqttMessage(obj.toJSONString().getBytes());
		mqttClient.publish(publisher_topic, message);
	}catch(MqttException me) {
		me.printStackTrace();
	}
  
}
```
This function simply choose randomly a new accettable (within 40% and 60%) humidity value to set in the house and publish it on the topic *actuator* (*publisher_topic*) as a json message. This message is a simple one, composed only by one field: *{"humidity":43}*.

### Actuator
The MQTT device receives remote commands by **subscribing on the topic *actuator***. By **publishing** on the same topic, the Java Collector can regulate the humidity levels in the house.

Each time a message is published on the topic *actuator*, the MQTT device simply parse the json message and set the humidity percentage to the specified value. This operation is emulated assigning this value to an internal variable. Moreover, the *set* boolean variable is set to true to ensure the next measurement to be consistent.

## CoAP Network
This network is simulated using Cooja and is composed by 5 CoAP sensors, one for each room. There is also an additional device that acts as border router to gain external access. Each device exposes 2 resources:
- **res_presence:** acts as a sensor for the detection of a presence in that room.
- **res_light:** acts as an actuator, to switch on/off the light in that room.

The aim of this network is to periodically detect the presence of a person in a room of the house. The data are sent to the Java Collector, that, exploiting some simple control logic, can give the order to turn the light on or off in a room. In order to be periodically updated, the Java Collector establishes an **observing relation** with the presence resource of each one of the 5 sensors.

### CoAP Servers
The main code of each sensor performs the following actions:
- **Initialize** a timer (*et*) with a value of **30 seconds**. This will be the periodicity through which the sensor will send updating about the resource
- **Activate** both *presence* and *light* resources 
- **Enter** this endless loop:
 ```
	while(1){
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
		res_presence.trigger();
		etimer_restart(&et);
	}
```
With the function **res_presence.trigger()**, executed every 30 seconds, the CoAP server triggers the event associated with the observable resource *presence* and notify the Java Collector (*observer*). 
In order to be observable, the resource *res_presence* is defined as follow:
```
EVENT_RESOURCE(res_presence,
         "title=\"Presence\";obs",
         res_get_handler,
         NULL,
         NULL,
         NULL,
         res_event_handler);
```
As only **get** operations are performed on this resource, the post-put-delete handlers are not defined. The *res_event_handler* is called every the *res_presence.trigger()* is executed by the main code of the CoAP server. This function simply notify all the observers (in our case only the Java Collector) of the *presence* resource. Before doing this, the **res_get_handler** is executed. Inside this function, the behaviour of the presence sensor is emulated using a **random function** that generates an integer value between 0 and 100. In particular a threshold (50) has been defined. If the random value is equal or above the threshold means that a presence is not detected in that room, otherwise someone is in the room. Then, a json message is created and notified to all the observers. The structure of the json message is the following:
```
{
	"node_id" : 2,
	"date" : "4-6-2021",
	"time" : "9:34:55",
	"room" : "kitchen",
	"presence" : 1
} 
```
The presence field is equal to 1 when a presence has been detected, 0 otherwise.

The *light* resource acts as an actuator, in charge of turning the light on or off in a specific room. The commands are sent by the Java Collector, that, after executing a control logic, issues a **post request** on the light resource of a specific sensor. The *res_light* is structured in this way:
```
RESOURCE(res_light,
         "title=\"Light: ?POST/PUT mode=on|off\";rt=\"light\"",
         NULL,
         res_post_put_handler,
         res_post_put_handler,
         NULL);
```
Unlike *res_presence*, this resource is not observable and can handle only post (or put) requests. The behaviour of the light bulb is emulated by the **led** interface. Considering a specific sensor, if only the **green led** is on, it means that **the light bulb in the relative room is on**. Otherwise, if the **red led** is on, **the light bulb is off**. The *res_post_put_handler*, after extracting the post variable (*on* or *off*), works with leds in order to implement this situation.

A simple example that summarises the interaction between a CoAP sensor and the Java Collector.

![coap-example](https://user-images.githubusercontent.com/73020009/120797150-ed4e3500-c53b-11eb-937a-45a3d417fe45.png)

### Java Collector
In order to be periodically updated, the Java Collector establishes an **observing relation** with all the 5 sensors. This is performed at the application boostrap. The following code shows an example for a generic sensor with a generic *connectionURI* URI. Note that the observing relation is established towards the **presence** resource of each sensor. 
```
String endpoint = "presence";
CoapClient client = new CoapClient(connectionURI + endpoint);
CoapObserveRelation relation = client.observe(
		new CoapHandler() {
			public void onLoad(CoapResponse response) {
				System.out.println(response.getResponseText());

				JSONParser parser = new JSONParser();
				JSONObject jsonObject = null;
				try {
					jsonObject = (JSONObject) parser.parse(response.getResponseText());
				} catch (ParseException e) {
					e.printStackTrace();
				}
				if(jsonObject != null) {
					long node_id = (Long) jsonObject.get("node_id");
					String date = (String) jsonObject.get("date");
					String time = (String) jsonObject.get("time");
					String room = (String) jsonObject.get("room");
					long presence = (Long) jsonObject.get("presence");

					lightControl(node_id,presence,date,time,room);

					storeCoAPData(node_id,presence,date,time,room);
				}


			}
		}
);
```
Every time an updating is received, the *onLoad* function is executed. Here, the json message is shown in output and then parsed. Then, a control logic is executed by the function *lightControl* and finally the data are stored in the MySql database. 
The *control logic* consists of checking wheter a presence is detected or not in a room and turning the respective light on or off consequently. To this aim, a **post request** is issued towards the relative sensor node, on the **light** resource. The following code shows an example for a generic sensor with a generic *actuatorURI* URI. The *postPayload* can be equal to *on* or *off*, based on the presence detection.
```
String endpoint = "light";
CoapClient client = new CoapClient(actuatorURI + endpoint);
CoapResponse res = client.post("mode="+postPayload,MediaTypeRegistry.TEXT_PLAIN);
```

### Simulation
A simulation of the presented CoAP network using Cooja.

The deployment is composed by 6 Cooja sensors. The sensor 1 is the border router. Then we have one sensors for each room: kitchen (2), dining room (3), living room (4), bathroom (5) and bedroom (6).

![Screenshot (17)](https://user-images.githubusercontent.com/73020009/120779837-c8e85d80-c527-11eb-9022-00721fe9035b.png)

At the beginning of the simulation, the network requires some time to build the RPL's DODAG. After this, the Java Collector starts its execution establishing observing relations with the 5 CoAP servers

![asas (2)](https://user-images.githubusercontent.com/73020009/120781688-950e3780-c529-11eb-9ab9-f7d0781617ae.png)

Then, the sensors start to notify the Java Collector with periodical presence updates, exploiting the observing mechanism. This is the first notification, with the consequent led status.

![final](https://user-images.githubusercontent.com/73020009/120782184-0e0d8f00-c52a-11eb-9db4-90e6f59b764d.png)

From the Java Collector's side, this is the situation: 

![asas (3)](https://user-images.githubusercontent.com/73020009/120783022-e834ba00-c52a-11eb-82d4-c783ec5cafed.png)

Second iteration:

![finale2](https://user-images.githubusercontent.com/73020009/120785027-04395b00-c52d-11eb-97ea-8cb683673b0b.png)

![Screenshot (32)](https://user-images.githubusercontent.com/73020009/120785475-727e1d80-c52d-11eb-9c04-39a987ecf081.png)

The presence detection returns the same results as before, thus nothing has changed. Note that the sensor, once received the actuation command, first check whether the led is already set in the desired mode. For instance, the sensor 2 (kitchen), receives from the Java Collector the command to turn off the light in the room. The sensor checks the status of the light bulb and finds out that it is already off (because it has been turned off at the previous iteration).

Three more iterations:

![Screenshot (27)](https://user-images.githubusercontent.com/73020009/120788091-5c259100-c530-11eb-9b56-41bb2803df93.png)

![Screenshot (28)](https://user-images.githubusercontent.com/73020009/120788098-5cbe2780-c530-11eb-908e-9524bd83e2e0.png)

![Screenshot (29)](https://user-images.githubusercontent.com/73020009/120788100-5d56be00-c530-11eb-8b48-94e01bad3cfd.png)




