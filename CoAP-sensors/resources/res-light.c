#include "contiki.h"
#include "coap-engine.h"
#include "os/dev/leds.h"
#include "sys/node-id.h"
#include <string.h>
#include <stdio.h>

static void res_post_put_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset);
static char* mapIdToRoom(int id);

RESOURCE(res_light,
         "title=\"Light: ?POST/PUT mode=on|off\";rt=\"light\"",
         NULL,
         res_post_put_handler,
         res_post_put_handler,
         NULL);
         
static char rooms[5][20] = {
	"kitchen",
	"dining room",
	"living room",
	"bathroom",
	"bedroom"
};


//light bulb on : green led , light bulb off : red led
static void res_post_put_handler(coap_message_t *request, coap_message_t *response, uint8_t *buffer, uint16_t preferred_size, int32_t *offset){
	
	size_t len = 0;
	int success=1;
	char room [20];
	strcpy(room,mapIdToRoom((int) node_id));
	const char *mode = NULL;
	
	if((len = coap_get_post_variable(request, "mode", &mode))){
		if(strncmp(mode, "on", len) == 0){ //turn on green led
			//check if the green led is already on
			if((leds_get() & (LEDS_NUM_TO_MASK(LEDS_GREEN)))>0){
				printf("[%s] The light bulb is already on\n",room);
			}
			else{
				printf("[%s] Turning on the light bulb\n",room);
				//turn on green and turn off red
				leds_set(LEDS_NUM_TO_MASK(LEDS_GREEN));
			}
		}
		else if(strncmp(mode, "off", len) == 0){ //turn on red led
			//check if the red led is already on
			if((leds_get() & (LEDS_NUM_TO_MASK(LEDS_RED)))>0){
				printf("[%s] The light bulb is already off\n",room);
			}
			else{
				printf("[%s] Turning off the light bulb\n",room);
				//turn on red and turn off green 
				leds_set(LEDS_NUM_TO_MASK(LEDS_RED));
			}
		}
		else{
			success=0;
		}
	}
	else{
		success=0;
	}
	
	
	if(success==1){
		coap_set_status_code(response, CHANGED_2_04);
	}
	else{
		coap_set_status_code(response, BAD_REQUEST_4_00);
	}
		
}

//------------------------utility------------------------------
static char* mapIdToRoom(int id){
	return rooms[id-2];
}
