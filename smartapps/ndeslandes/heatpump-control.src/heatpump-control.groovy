/**
 *  Heatpump control
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
definition(
        name: "Heatpump control",
        namespace: "ndeslandes",
        author: "Nicolas Deslandes",
        description: "Heat pump control using temperature measurement device",
        category: "Green Living",
        iconUrl: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving.png",
        iconX2Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@2x.png",
        iconX3Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@3x.png"
)

preferences {
    section("Heating based on the following devices") {
        input "heatPump", "capability.switch", title: "Heat Pump", required: true, multiple: false
        input "heaters", "capability.thermostatHeatingSetpoint", title: "Heaters that are on the same floor than the heat pump", required: true, multiple: true
        input "insideSensors", "capability.temperatureMeasurement", title: "Sensors that are on the same floor than the heat pump", required: true, multiple: true
        input "outsideSensor", "capability.temperatureMeasurement", title: "Outside temperature sensor", required: true, multiple: false
    }
    section("Preferences") {
        input "wakeTime", "time", title: "Wake Time", required: true, defaultValue: "2015-01-09T07:00:00.000-0500"
        input "sleepTime", "time", title: "Sleep Time", required: true, defaultValue: "2015-01-09T22:00:00.000-0500"
        input "homeTemp", "decimal", title: "Home temperature", required: true, defaultValue: 21.0
        input "idleTemp", "decimal", title: "Idle temperature", required: true, defaultValue: 18.0
        input "preheatingTime", "number", title: "Preheating time (in minutes)", required: true, defaultValue: 15
        input "minHeatpumpOutsideTemp", "decimal", title: "Minimal outside temperature", required: true, defaultValue: -12.0
        input "tempSwing", "decimal", title: "Temperature swing (+)", required: true, defaultValue: 0.5
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
    controlHeatpumpAndHeaters()
    subscribe(insideSensors, "temperature", temperatureHandler)
}

def temperatureHandler(evt) {
    log.info "Received temperature sensor event from ${evt.displayName} with a temperature at ${evt.doubleValue}"

    controlHeatpumpAndHeaters()
}

def controlHeatpumpAndHeaters() {
    Long timeNow = now()
    Long heatingTimeStart = timeToday(wakeTime, location.timeZone).time - preheatingTime * 60 * 1000
    Long heatingTimeStop = timeToday(sleepTime, location.timeZone).time

    if (timeNow >= heatingTimeStart && timeNow <= heatingTimeStop) {
        log.info "Inside heating hours"
        if (outsideSensor.currentTemperature > minHeatpumpOutsideTemp) {
            log.info "Outside temperature is fine"
            setAllHeatingSetpoint(idleTemp)
            if (insideSensors.every { it.currentTemperature >= homeTemp + tempSwing})
                stopHeatPump()
            else if (insideSensors.any { it.currentTemperature < homeTemp})
                startHeatPump()
        } else {
            log.info "Outside temperature is too cold"
            stopHeatPump()
            setAllHeatingSetpoint(homeTemp)
        }
    } else {
        log.info "Outside heating hours"
        stopHeatPump()
        setAllHeatingSetpoint(idleTemp)
    }
}

def setAllHeatingSetpoint(java.math.BigDecimal temperature) {
    heaters.each {
        if (it.heatingSetpoint != temperature)
            it.setHeatingSetpoint(temperature)
    }
}

def stopHeatPump() {
    if (heatPump.currentSwitch == "on")
        heatPump.off()
}

def startHeatPump() {
    if (heatPump.currentSwitch == "off")
        heatPump.on()
}
