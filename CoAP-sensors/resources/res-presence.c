#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <stdio.h>
#include <stdint.h>
#include "sys/node-id.h"
#include "coap-engine.h"


#define PRESENCE_TH 50

static void res_get_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset);
static void res_event_handler(void);
static char* mapIdToRoom(int id);
static void buildJsonResponse(char* json_response,int node_id,struct tm* tmp,char* room,int presence);

EVENT_RESOURCE(res_presence,
         "title=\"Presence\";obs",
         res_get_handler,
         NULL,
         NULL,
         NULL,
         res_event_handler);
         
//node "i" is rooms[i-2] --> (node 1:border router) node 2:kitchen ... node 6:bedroom
static char rooms[5][20] = {
	"kitchen",
	"dining room",
	"living room",
	"bathroom",
	"bedroom"
};        

static void
res_get_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset)
{
	time_t current_time;
	char* c_time_string;
	char message [200];
	int length;
	char room [20];
	strcpy(room,mapIdToRoom((int) node_id));
	int presence;
	char json_response [200];
	struct tm* tmp;
	
	current_time=time(NULL);
	tmp=gmtime(&current_time);
	
	c_time_string=ctime(&current_time);
	// remove newline
	if( c_time_string[strlen(c_time_string)-1] == '\n' )
    	c_time_string[strlen(c_time_string)-1] = ' ';

	//fake presence sensor
	int n = rand()%100;

	if(n<=PRESENCE_TH){
		sprintf(message,"Room *%s* [%s] Presence detected",room,c_time_string);
		presence=1;
	}
	else{
		sprintf(message,"Room *%s* [%s] Presence not detected",room,c_time_string);
		presence=0;
	}

	printf("%s\n",message);
	
	buildJsonResponse(json_response,node_id,tmp,room,presence);
	length=strlen(json_response);
	memcpy(buffer, json_response, length);

	coap_set_header_content_format(response, TEXT_PLAIN);
	coap_set_header_etag(response, (uint8_t *)&length, 1);
	coap_set_payload(response, buffer, length);
}

static void res_event_handler(void){
	//before sending the notification the get_handler is called
	coap_notify_observers(&res_presence);
}


//------------------------utility------------------------------
static char* mapIdToRoom(int id){
	return rooms[id-2];
}

static void buildJsonResponse(char* json_response,int node_id,struct tm* tmp,char* room,int presence){
	//{node_id,date,time,room,presence} 
	char date [20];
	char time [20];
	
	sprintf(date,"%d-%d-%d",tmp->tm_mday,(tmp->tm_mon+1),(tmp->tm_year+1900));
	sprintf(time,"%d:%d:%d",tmp->tm_hour,tmp->tm_min,tmp->tm_sec);
	
	sprintf(json_response,"{\"node_id\":%d,\"date\":\"%s\",\"time\":\"%s\",\"room\":\"%s\",\"presence\":%d}",node_id,date,time,room,presence);
	
	
}
