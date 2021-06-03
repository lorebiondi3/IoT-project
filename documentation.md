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

![deployment-iot](https://user-images.githubusercontent.com/73020009/120631128-cd4d4180-c467-11eb-865d-8fdc6b05e3f3.png)

Within the context of project, the presented scenario has been developed in this way: 
- The CoAP network has been simulated exploiting the Cooja simulator
- The MQTT network (including the MQTT Broker) has been deployed on real devices from the IoT testbed
- The Java Collector has been deployed on the same VM where Cooja runs
- The MySql Database runs locally on the VM 

As regards the MQTT network, it is composed by 2 real sensors (*/dev/ttyACM31* and */dev/ttyACM83*) of the testbed. The */dev/ttyACM83* acts as border router, while the */dev/ttyACM31* is the actual humidity sensor/actuator.

## MQTT Network
This network is deployed on real sensors from the testbed and consists of 2 devices: a humidity sensor/actuator and a border router. This last device allow the sensor to access the MQTT broker (deployed on the testbed as well) to publish or retrieve informations.

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
The aim of this network is to periodically detect the presence of a person in a room of the house. The data are sent to the Java Collector, that, exploiting some simple control logic, can give the order to turn the light on or off in a room.

### sensors scrivi qui la parte sui sensori

In order to be periodically updated, the Java Collector establishes an **observing relation** with all the 5 sensors. This is performed at the application boostrap. The following code show an example for a generic sensor with a generic *connectionURI* URI. Note that the observe relation is established towards the **presence** resource of each sensor. 
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
The *control logic* consists of checking wheter a presence is detected or not in a room and turn the light on or off consequently.
```
CoapClient client = new CoapClient(actuatorURI);
CoapResponse res = client.post("mode="+postPayload,MediaTypeRegistry.TEXT_PLAIN);
```
