package com.example.planetarium

import java.util.*

class PlanetCalendar {
    private var start: Calendar = Calendar.getInstance()
    private var daysInYear: Float
    private var hoursInDay: Float

    private var secondInMillis: Long = 1000
    private var minuteInMillis: Long = secondInMillis*60
    private var hourInMillis: Long = minuteInMillis*60


    constructor(rotationPeriod:Float, orbitPeriod:Float) {
        this.start.set(2019, 7, 26)// months are indexed from 0...WHY?! anyway 7 = August
        this.hoursInDay = rotationPeriod
        this.daysInYear = orbitPeriod
    }


}