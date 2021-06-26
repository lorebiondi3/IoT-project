package collector.iot.unipi.it;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class MyMqttClient implements MqttCallback{
	
	String subscriber_topic = "humidity";
	String publisher_topic = "actuator";
	String broker = "tcp://127.0.0.1:1883";
	String clientId = "JavaCollector";
	
	MqttClient mqttClient;
	
	public MyMqttClient() throws MqttException{
		
		mqttClient = new MqttClient(broker,clientId);
		mqttClient.setCallback(this);
		mqttClient.connect();
		mqttClient.subscribe(subscriber_topic);
	}

	public void connectionLost(Throwable cause) {
		System.out.println(cause.getMessage());
		
	}

	public void messageArrived(String topic, MqttMessage message) throws Exception {
		
		String json_message = new String(message.getPayload());

		//parsing
		JSONParser parser = new JSONParser();
		JSONObject jsonObject = null;
		try {
			jsonObject = (JSONObject) parser.parse(json_message);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		if(jsonObject != null) {
			
			long humidity = (Long) jsonObject.get("humidity");
			
			// get current date
			SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
			Date d = new Date();
			String[] tokens = dateFormat.format(d).split(" ");
			String date = tokens[0];
			String time = tokens[1];
			
			System.out.println("{\"date\":"+date+",\"time\":"+time+",\"humidity\":"+humidity+"}");
			
			//---------- control logic ----------------
			//if the humidity is out of the range [40,60]% --> set to a random value within this range
			if(humidity>60 || humidity<40) {
				humidityControl(date,time,humidity);
			}
			
			//--------------store mqtt sensors data in MySql db----------------------
			storeMqttData(humidity,date,time);
			
		}
		
	}

	public void deliveryComplete(IMqttDeliveryToken token) {
		// TODO Auto-generated method stub
		
	}
	
	public void humidityControl(String date,String time,long humidity) {
		
		final int new_humidity = (int) ((Math.random() * (61 - 40)) + 40);
		System.out.println("["+date+" "+time+"] Humidity out of range : setting it to "+new_humidity+" %");
		
		//publish on topic 'actuator'
		try {
			//json message --> {"humidity":50}
			JSONObject obj = new JSONObject();
	        obj.put("humidity", new_humidity);
			MqttMessage message = new MqttMessage(obj.toJSONString().getBytes());
			mqttClient.publish(publisher_topic, message);
		}catch(MqttException me) {
			me.printStackTrace();
		}
	}
	
	public static void storeMqttData(long humidity,String date,String time) {
		
		  String connectionUrl = "jdbc:mysql://localhost:3306/sensors?serverTimezone=UTC";
		  String query = "INSERT INTO MQTTData (date,time,humidity) VALUES ('"+date+"','"+time+"',"+humidity+")";
		  
		  try {
			  Connection conn = DriverManager.getConnection(connectionUrl,"root","root");
			  PreparedStatement ps = conn.prepareStatement(query);
			  ps.executeUpdate();
			  conn.close();
		  }
		  catch(SQLException e){
			  e.printStackTrace();
		  }
	
	}
	
}
