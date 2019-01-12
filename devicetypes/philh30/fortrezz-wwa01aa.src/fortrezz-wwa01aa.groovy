/**
 *  Device Handler for Fortrezz WWA-01AA Temperature and Leak Sensor
 *
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *  Based on the Smartsense Moisture device handler.
 *
 *  TEMPERATURE
 *	The WWA-01AA provides a temperature report only if it is requested during its wakeup. Configure the wakeup interval
 *  based on the frequency that you want a temperature update (shorter intervals may shorten battery life).
 *
 *  BATTERY
 *  The WWA-01AA reports battery only at either 99% or 0%, and will therefore battery will appear to die suddenly. 
 *
 *  ALARMS
 *	The water alarm sends a BasicSet.
 *  The freeze alarm is triggered at 39F, sending a SensorBinaryReport. This set point cannot be configured in the device.
 *  The overheating and freezing thresholds that are in the DH configuration do not get passed to the device, and therefore
 *  will not trigger an instant report from the device when the temperature breaks the threshold. Those thresholds will only
 *  trigger an event when the temperature is reported during the wake up.
 */
metadata {
	definition (name: "FortrezZ WWA01AA", namespace: "philh30", author: "philh30", runLocally: false, minHubCoreVersion: '000.017.0012', executeCommandsLocally: true, mnmn: "SmartThings", vid: "generic-leak") {
		capability "Water Sensor"
		capability "Sensor"
		capability "Battery"
		capability "Temperature Measurement"
		capability "Health Check"
		
		fingerprint mfr:"0084", prod:"0053", model:"0216", deviceJoinName: "FortrezZ Temperature and Leak Sensor"
	}
	
    preferences {
        input "wakeUpIntervalMinutes", "number", title: "Wake up interval in minutes (1-480). The WWA-01AA only reports temperature and battery status if they are requested during wakeup.", range: "1..480", required: true
        input "requestTemp", "enum", title: "Request temperature when device wakes up?", required: true,
        	options:[[0:"On"],
            		[1:"Off"]]
		input "logTemp", "enum", title: "Temperature reporting", required: true,
        	options:[[0:"Log changes only"],
            		[1:"Log every wake up"]]
        input "highTempThreshold", "number", title: "Overheating Threshold", range: "0..150", required: true
        input "lowTempThreshold", "number", title: "Freezing Threshold", range: "0..150", required: true
        input "requestBattery", "enum", title: "Request battery status when device wakes up?", required: true,
			options:[[0:"Every wake up"],
					[1:"Daily"],
					[2:"Weekly"],
					[3:"Never"]]
		input "logBattery", "enum", title: "Battery reporting", required: true,
			options:[[0:"Log changes only"],
					[1:"Log every wake up"]]
    }
    
	simulator {
		status "dry": "command: 2001, payload: 00"
		status "wet": "command: 2001, payload: FF"
		status "freezing": "command: 3003, payload: FF"
		status "normal": "command: 3003, payload: 00"
		for (int i = 0; i <= 100; i += 20) {
			status "battery ${i}%": new physicalgraph.zwave.Zwave().batteryV1.batteryReport(batteryLevel: i).incomingMessage()
		}
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"water", type: "generic", width: 6, height: 4){
			tileAttribute ("device.water", key: "PRIMARY_CONTROL") {
				attributeState "dry", label: "Dry", icon:"st.alarm.water.dry", backgroundColor:"#ffffff"
				attributeState "wet", label: "Wet", icon:"st.alarm.water.wet", backgroundColor:"#00A0DC"
			}
		}
		standardTile("temperatureState", "device.temperatureState", width: 2, height: 2) {
			state "normal", icon:"st.alarm.temperature.normal", backgroundColor:"#ffffff"
            state "freezing", icon:"st.alarm.temperature.freeze", backgroundColor:"#00A0DC"
			state "overheated", icon:"st.alarm.temperature.overheat", backgroundColor:"#e86d13"
		}
		valueTile("temperature", "device.temperature", width: 2, height: 2) {
			state("temperature", label:'${currentValue}°',
				backgroundColors:[
					[value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]
				]
			)
		}
		valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}
		main (["water"])//, "temperatureState"])
		details(["water", "temperatureState", "temperature", "battery"])
	}
}

def parse(String description) {
	def result = []
	def parsedZwEvent = zwave.parse(description, [0x30: 1, 0x31: 1, 0x71: 1, 0x84: 1])
    
    log.debug "New event to parse: ${description}"
    
	if(parsedZwEvent) {
		if(parsedZwEvent.CMD == "8407") {
			//log.debug "Requesting battery report"
			if(!state.lastBatteryCheck) {
                result << new physicalgraph.device.HubAction(zwave.batteryV1.batteryGet().format())
            }
            def ageInMinutes = state.lastBatteryCheck ? (new Date().time - state.lastBatteryCheck)/60000 : 600
            log.debug "Last Battery check was ${ageInMinutes/60/24} days ago"
            switch(requestBattery) {
            	case "0":
                	//Request battery status every time... Half minute lag to prevent repeat requests if multiple wake up notifications are received
                    if(ageInMinutes >= 0.5) {
                    	result << new physicalgraph.device.HubAction(zwave.batteryV1.batteryGet().format())
                    }
                    break
                case "1":
                	//Request battery status daily
                	if(ageInMinutes >= 60*24) {
                    	result << new physicalgraph.device.HubAction(zwave.batteryV1.batteryGet().format())
                    }
                    break
                case "2":
                	//Request battery status weekly
                    if(ageInMinutes >= 60*24*7) {
                    	result << new physicalgraph.device.HubAction(zwave.batteryV1.batteryGet().format())
                    }
                	break
               	case "3":
                	//Request battery status never
                	break
            } 
            //log.debug "Requesting temperature report" 
            if (requestTemp == "0") {
				def ageInSeconds = state.lastTemperature ? (new Date().time - state.lastTemperature)/1000 : 600
                state.lastTemperature = new Date().time
                if (ageInSeconds > 30) {
                	result << new physicalgraph.device.HubAction(zwave.sensorMultilevelV1.sensorMultilevelGet().format())
                }
            }
            //log.debug "Checking for config changes - update wakeup interval"
            if (state.updateConfigNextWakeup == 1) {
            	result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpIntervalSet(seconds:60*wakeUpIntervalMinutes, nodeid:zwaveHubNodeId).format())
            	state.updateConfigNextWakeup = 0
            }
            
            //log.debug "Requesting wakeup interval report"
            //result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpIntervalGet().format())
            
            result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format())
		}
        
        result << createEvent( zwaveEvent(parsedZwEvent) )
	}
    
	if(!result) result = [ descriptionText: parsedZwEvent, displayed: false ]
	log.debug "Parse returned ${result}"
	
    
    
    return result
}

def installed() {
    state.updateConfigNextWakeup = 1
}

def updated() {
    state.updateConfigNextWakeup = 1
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	[descriptionText: "${device.displayName} woke up", isStateChange: false]
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
    //This is the freeze event
    def map = [:]
	switch (cmd.CMD) {
    	case "2001":
    		map.name = "water"
			if(cmd.sensorValue == 0) { 
            	map.value = "dry"
                }
    			else { map.value = "wet" }
			map.descriptionText = "${device.displayName} is ${map.value}"
			break
        case "3003":
    		map.name = "temperatureState"
			if(cmd.sensorValue == 0) { map.value = "normal" }
    			else { map.value = "freezing" }
			map.descriptionText = "${device.displayName} is ${map.value}"
			break
        default:
        	log.debug "Unrecognized alarm"
    }
    
    map
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
    if(cmd.batteryLevel == 0xFF) {
		map.name = "battery"
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.displayed = true
	} else {
		map.name = "battery"
		map.value = 1 + cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
		map.unit = "%"
	}
    if (logBattery == "1") {
        map.isStateChange = true
    }
    state.lastBatteryCheck = new Date().time
	map
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv1.SensorMultilevelReport cmd)
{
    def map = [:]
	if(cmd.sensorType == 1) {
		map.name = "temperature"
		if(cmd.scale == 0) {
			map.value = getTemperature(cmd.scaledSensorValue)
		} else {
			map.value = cmd.scaledSensorValue
		}
		map.unit = location.temperatureScale
        if (logTemp == "1") {
        	map.isStateChange = true
        }
	}
    if(map.value >= highTempThreshold) {
    	sendEvent(name: "temperatureState", value:"overheated", descriptionText: "${device.displayName} is overheated")
    } else if(map.value <= lowTempThreshold) {
    	sendEvent(name: "temperatureState", value:"freezing", descriptionText: "${device.displayName} is freezing")
    } else {
    	sendEvent(name: "temperatureState", value:"normal", descriptionText: "${device.displayName} is normal")
    }
	map
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpIntervalReport cmd)
{
	def map = [:]
    map.name = "wakeup"
    map.value = cmd.seconds / 60
    map.descriptionText = "${device.displayName} wake up interval is ${cmd.seconds / 60} minutes."
    map
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
    //This is the water event
    def map = [:]
    switch (cmd.CMD) {
    	case "2001":
    		map.name = "water"
			if(cmd.value == 0) { 
            	map.value = "dry"
                }
    			else { map.value = "wet" }
			map.descriptionText = "${device.displayName} is ${map.value}"
			break
        case "3003":
    		map.name = "temperatureState"
			if(cmd.value == 0) { map.value = "normal" }
    			else { map.value = "freezing" }
			map.descriptionText = "${device.displayName} is ${map.value}"
			break
        default:
        	log.debug "Unrecognized alarm"
    }
    
	map
}

def getTemperature(value) {
	if(location.temperatureScale == "C"){
		return value
	} else {
		return Math.round(celsiusToFahrenheit(value))
	}
}

def zwaveEvent(physicalgraph.zwave.Command cmd)
{
	log.debug "COMMAND CLASS: $cmd"
}
