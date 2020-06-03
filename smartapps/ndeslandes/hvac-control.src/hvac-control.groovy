/**
 *  HVAC control
 *
 *  Copyright 2019 Nicolas Deslandes
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
 */

import java.math.BigDecimal
import physicalgraph.app.DeviceWrapperList

definition(
        name: "HVAC control",
        namespace: "ndeslandes",
        author: "Nicolas Deslandes",
        description: "HVAC control using temperature measurement device",
        category: "Green Living",
        iconUrl: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving.png",
        iconX2Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@2x.png",
        iconX3Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@3x.png"
)

preferences {
    section("Heating based on the following devices") {
        input "acHot", "capability.switch", title: "HVAC Heater", required: true, multiple: false
        input "acCool", "capability.switch", title: "HVAC Cooler", required: true, multiple: false
        input "sameFloorHeaters", "capability.thermostatHeatingSetpoint", title: "Heaters that are on the same floor where the HVAC is", required: true, multiple: true
        input "bedroomHeaters", "capability.thermostatHeatingSetpoint", title: "Bedroom heaters", required: false, multiple: true
        input "basementHeaters", "capability.thermostatHeatingSetpoint", title: "Basement heaters", required: true, multiple: true
        input "insideSensors", "capability.temperatureMeasurement", title: "Sensors  that are on the same floor where the HVAC is", required: true, multiple: true
        input "outsideSensor", "capability.temperatureMeasurement", title: "Outside temperature sensor", required: true, multiple: false
        input "doors", "capability.contactSensor", title: "Door and window contact sensors", required: true, multiple: true
    }
    section("Preferences") {
        input "wakeTime", "time", title: "Wake Time", required: true, defaultValue: "2015-01-09T07:00:00.000-0500"
        input "sleepTime", "time", title: "Sleep Time", required: true, defaultValue: "2015-01-09T22:00:00.000-0500"
        input "homeTemp", "decimal", title: "Home temperature", required: true, defaultValue: 22.0
        input "idleTemp", "decimal", title: "Idle temperature", required: true, defaultValue: 15.0
        input "nightTemp", "decimal", title: "Night temperature", required: true, defaultValue: 18.0
        input "preheatingTime", "number", title: "Preheating time (in minutes)", required: true, defaultValue: 30
        input "minHVACOutsideTemp", "decimal", title: "Minimal outside temperature", required: true, defaultValue: -10.0
        input "deadZone", "decimal", title: "Dead Zone", required: true, defaultValue: 1.0
        input "lockDuration", "number", title: "Lock duratiom time in minutes", required: true, defaultValue: 60
    }
}

def installed() {
    log.debug "Installed with settings: ${settings}"

    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"

    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(insideSensors, "temperature", temperatureHandler)
    subscribe(doors, "contact", contactHandler)
    subscribe(sameFloorHeaters, "heatingSetpoint", setpointHandler)
    subscribe(basementHeaters, "heatingSetpoint", setpointHandler)
    subscribe(bedroomHeaters, "heatingSetpoint", setpointHandler)
    
    bedroomHeaters.each {
       state."lock${it}" = false
    }
	basementHeaters.each {
       state."lock${it}" = false
    }
	sameFloorHeaters.each {
       state."lock${it}" = false
    }
    runEvery1Minute(controlHeatpumpAndHeaters)
}

def temperatureHandler(evt) {
    log.info "Received temperature sensor event from ${evt.displayName} with a temperature at ${evt.doubleValue}"

    controlHeatpumpAndHeaters()
}

def contactHandler(evt) {
    log.info "Received door sensor event from ${evt.displayName} with a value of ${evt.value}"

    controlHeatpumpAndHeaters()
}

def setpointHandler(evt) {
    log.info "${evt.device} was manually updated, it will be lock for ${lockDuration} minutes"
    state."lock${evt.device}" = true
    runIn(lockDuration * 60, "unlock", [device: evt.device])
}

def unlock(device) {
    log.info "Unlocking ${device}"
    state."lock${device}" = false
}

def controlHeatpumpAndHeaters() {
    Long timeNow = now()
    Long heatingTimeStart = timeToday(wakeTime, location.timeZone).time - preheatingTime * 60 * 1000
    Long heatingTimeStop = timeToday(sleepTime, location.timeZone).time
        
    BigDecimal currentTemp = (insideSensors.currentTemperature).sum()/(insideSensors.currentTemperature).count { it }
    log.info "Average temperature is ${currentTemp}"
    
    BigDecimal heatTurningPoint = homeTemp
    BigDecimal coolTurningPoint = homeTemp + deadZone
    
    log.info("HVAC cool will start at ${coolTurningPoint}")
    log.info("HVAC heat will start at ${heatTurningPoint}")
    
    if (timeNow >= heatingTimeStart && timeNow <= heatingTimeStop) {
        log.info "Inside heating hours"
        setAllHeatingSetpoint(basementHeaters, homeTemp)
        setAllHeatingSetpoint(bedroomHeaters, nightTemp) 
        
        if (doors.every { it.currentContact == "closed"}) {
            log.info "All doors are closed"
            if (outsideSensor.currentTemperature >= minHVACOutsideTemp) {
                setAllHeatingSetpoint(sameFloorHeaters, idleTemp)

                if (currentTemp < heatTurningPoint) {
                    log.info "Current temp ${currentTemp} is bellow ${heatTurningPoint}, starting AC heat"
                    startACHeat()
                } else {
                    log.info "Current temp ${currentTemp} is over ${heatTurningPoint}, stoping AC heat"
                    stopACHeat()
                }
            } else {
                log.info "Outside temperature is too cold"
                stopACHeat()
                setAllHeatingSetpoint(sameFloorHeaters, homeTemp)
            }

            if (currentTemp > coolTurningPoint) {
                log.info "Current temp ${currentTemp} is over ${coolTurningPoint}, starting AC cool"
                startACCool()
            } else {
                log.info "Current temp ${currentTemp} is below ${coolTurningPoint}, stoping AC cool"
                stopACCool()
            }
        } else {
            log.info "One door is open"
            stopACHeat()
        	stopACCool()
        }
    } else {
        log.info "Outside heating/cooling hours"
        stopACHeat()
        stopACCool()
        setAllHeatingSetpoint(sameFloorHeaters, nightTemp)
        setAllHeatingSetpoint(basementHeaters, nightTemp)
        setAllHeatingSetpoint(bedroomHeaters, homeTemp)
    }
}

def setAllHeatingSetpoint(DeviceWrapperList heaters, BigDecimal temperature) {
    heaters.each {
        if (state."lock${it}" == false) {
            if (it.heatingSetpoint != temperature)
                it.setHeatingSetpoint(temperature)
        } else {
            log.info "${it} is locked"
        }
    }
}

def stopACHeat() {
    log.info "Stoping AC heat mode"
    if (acHot.currentSwitch == "on")
        acHot.off()
}

def stopACCool() {
    log.info "Stoping AC cool mode"
    if (acCool.currentSwitch == "on")
        acCool.off()
}

def startACHeat() {
    log.info "Starting AC heat mode"
	if (acCool.currentSwitch == "on")
        acCool.off()
    if (acHot.currentSwitch == "off")
        acHot.on()
}

def startACCool() {
    log.info "Starting AC cool mode"
    if (acHot.currentSwitch == "on")
        acHot.off()
    if (acCool.currentSwitch == "off")
        acCool.on()
}
