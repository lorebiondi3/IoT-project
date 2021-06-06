#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include "contiki.h"
#include "coap-engine.h"

/* Log configuration */
#include "sys/log.h"
#define LOG_MODULE "App"
#define LOG_LEVEL LOG_LEVEL_APP
#define PRESENCE_INTERVAL CLOCK_SECOND*30


PROCESS(coap_sensor, "CoAP Sensor");
AUTOSTART_PROCESSES(&coap_sensor);

extern coap_resource_t res_presence;
extern coap_resource_t res_light;

PROCESS_THREAD(coap_sensor, ev, data){

	static struct etimer et;

	PROCESS_BEGIN();
	
	etimer_set(&et,PRESENCE_INTERVAL);
	srand(time(NULL));
	setenv("TZ", "Europe/Rome", 1);
	
	LOG_INFO("Starting CoAP server\n");
	printf("Activating resource \"presence\"...\n");
	coap_activate_resource(&res_presence,"presence");
	printf("Activating resource \"light\"...\n");
	coap_activate_resource(&res_light,"light");
	while(1){
		PROCESS_WAIT_EVENT_UNTIL(etimer_expired(&et));
		res_presence.trigger();
		etimer_restart(&et);
	}
	
	PROCESS_END();
}
