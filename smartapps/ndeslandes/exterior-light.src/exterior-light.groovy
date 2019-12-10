/**
 *  Exterior light
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
        name: "Exterior light",
        namespace: "ndeslandes",
        author: "Nicolas Deslandes",
        description: "Exterior light",
        category: "Green Living",
        iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
        iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
        iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
    section("Title") {
        input "lights", "capability.switch", title: "Exterior lights", required: true, multiple: true
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
    Long sunrise = getSunriseAndSunset().sunrise.time
    Long sunset = getSunriseAndSunset().sunset.time
    Long timeNow = now()
    if (timeNow > sunset) {
        turnOn()
    } else if (timeNow > sunrise) {
        turnOff()
    }
    subscribe(location, "sunset", sunsetHandler)
    subscribe(location, "sunrise", sunriseHandler)
}

def sunsetHandler(evt) {
    turnOn()
}

def sunriseHandler(evt) {
    turnOff()
}

def turnOff() {
    lights.each {
        if (it.currentSwitch == "on")
            it.off()
    }
}

def turnOn() {
    lights.each {
        if (it.currentSwitch == "off")
            it.on()
    }
}