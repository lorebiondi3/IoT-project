package collector.iot.unipi.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapHandler;
import org.eclipse.californium.core.CoapObserveRelation;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Application {
	
	
	public static void main(String[] args) {
		
		initialPrint();
		
		CoAPInitialization();
		
		MqttInitialization();
		
		while(true) {}

	}
	
	public static void lightControl(final long node_id,final long presence,final String date,final String time,final String room) {
		
		Thread t = new Thread() {
		    public void run() {
		    	String actuatorURI="coap://[fd00::20"+node_id+":"+node_id+":"+node_id+":"+node_id+"]/light";
				String postPayload="";
				//if a presence is detected,turn on the light bulb in the room
				if(presence==1) {
					System.out.println("["+date+" "+time+"] Presence detected in the room *"+room+"* : turning on the light bulb !");
					postPayload="on";
				}
				//otherwise turn off the light in the room
				else {
					System.out.println("["+date+" "+time+"] Presence not detected in the room *"+room+"* : turning off the light bulb !");
					postPayload="off";
				}
		    	CoapClient client = new CoapClient(actuatorURI);
		    	CoapResponse res = client.post("mode="+postPayload,MediaTypeRegistry.TEXT_PLAIN);
				String code = res.getCode().toString();
				if(!code.startsWith("2")) {
					System.out.println("---Error: "+code);
				}
		    }  
		};
		t.start();
	}
	
	public static void storeCoAPData(long node_id,long presence,String date,String time,String room) {
		
		  String connectionUrl = "jdbc:mysql://localhost:3306/sensors?serverTimezone=UTC";
		  String query = "INSERT INTO CoAPData (date,time,room,node_id,presence) VALUES ('"+date+"','"+time+"', '"+room+"',"+node_id+" ,"+presence+")";
		  
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
	
	//establish an observing relation with all the coap servers
	public static void CoAPInitialization() {
		String connectionURI = "";
		String endpoint="presence";
		
		for(int i=2;i<=6;i++) {
			connectionURI="coap://[fd00::20"+i+":"+i+":"+i+":"+i+"]/"+endpoint;
			System.out.println("Establishing observing relation with node "+i);
			CoapClient client = new CoapClient(connectionURI);
			CoapObserveRelation relation = client.observe(
					new CoapHandler() {
						public void onLoad(CoapResponse response) {
							System.out.println(response.getResponseText());
							
							//parsing
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
								
								//-----------------control logic----------------------------
								lightControl(node_id,presence,date,time,room);

								//--------------store coap sensors data in MySql db----------------------
								storeCoAPData(node_id,presence,date,time,room);
							}
				            
							
						}
						public void onError() {
							System.out.println("----Lost connection----");
						}
					}
			);
		} 
	}

	public static void MqttInitialization() {
		try {
			MyMqttClient mc = new MyMqttClient();
		} catch(MqttException me) {
			me.printStackTrace();
		}
	}
	
	public static void initialPrint() {
		System.out.println("######################################################");
		System.out.println("######     SMART HOME TELEMETRY APPLICATION     ######");
		System.out.println("######################################################");
	}
	
}
