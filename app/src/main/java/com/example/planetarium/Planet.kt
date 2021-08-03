package com.example.planetarium

import android.graphics.*
import kotlin.math.*
import android.graphics.drawable.VectorDrawable
import android.provider.ContactsContract
import java.util.*
import java.util.function.DoubleUnaryOperator
import kotlin.collections.ArrayList

class Planet {
    var rotation: Float
    var rotLen: Double
    var revLen: Float
    var distance: Float
    var activeBitmap: Bitmap
    var planetBitmap: Bitmap
    var amPlanetBitmap: Bitmap

    var revAngle: Float

    //position
    var point: Point// = Point()
    var center: Point = Point(195,195)

    //planet watch face
    var wfBitmap: Bitmap
    var wfBg: ArrayList<Bitmap> = ArrayList()
    var sf:Double
    var wfMercMap: Bitmap


    constructor(rotation: Float, rotation_length: Double, revolution_length: Float, distance: Float, planetBitmap: Bitmap) {
        //properties
        this.rotation = rotation
        this.rotLen = rotation_length*60*60
        this.revLen = revolution_length
        this.revAngle = 360f / revLen
        this.distance = distance
        this.sf = this.rotLen/84600 // earth / planet rotation

        this.planetBitmap = planetBitmap

        this.amPlanetBitmap = Bitmap.createBitmap(planetBitmap.width, planetBitmap.height, Bitmap.Config.ARGB_8888)

        //create gray scale image from planetBitmap
        val canvas = Canvas(amPlanetBitmap)
        val grayPaint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = ColorMatrixColorFilter(colorMatrix)
        grayPaint.colorFilter = filter
        canvas.drawBitmap(planetBitmap, 0f, 0f, grayPaint)

        this.activeBitmap = amPlanetBitmap

        this.wfBitmap = Bitmap.createBitmap(390,390,Bitmap.Config.ARGB_8888)
        this.point = Point(0,0)
        getGlobalCoords()

        //mercator map (empty for now, will become part of the constructor once working)
        this.wfMercMap = Bitmap.createBitmap(390,780,Bitmap.Config.ARGB_8888)
    }

    //changes active colors
    fun setActiveBitmap(ambient: Boolean) {
        activeBitmap = when (ambient) {
            true -> amPlanetBitmap
            false -> planetBitmap
        }
    }

    //work out position of planet in orrery (x,y)
    fun getGlobalCoords() {
        var radians: Double = this.rotation * (PI / 180)
        var pX: Int = center.x //- (this.activeBitmap.width / 2)
        var pY: Int = (center.y - (center.y * this.distance)).toInt() //- (this.activeBitmap.height / 2)).toInt()
        this.point.x = ((cos(radians) * (pX-center.x)) - (sin(radians) * (pY-center.y))).roundToInt() + center.x
        this.point.y = ((sin(radians) * (pX-center.x)) + (cos(radians) * (pY-center.y))).roundToInt() + center.y
    }

    fun getRelativeTime(currentHour:Int, currentMinute:Int, currentSecond:Int):String {
        val totalSeconds:Double = (currentHour*60*60 + currentMinute*60 + currentSecond)*this.sf//(currentTime.timeInMillis % 86400000) / this.sf
        //val totalSeconds:Double = timeInMillis / 1000
        //val totalSeconds:Double = currentTime.get
        val seconds:Int = (totalSeconds % 60).toInt()-1
        val totalMinutes:Double = totalSeconds / 60
        val minutes:Int = (totalMinutes % 60).toInt()-1
        val totalHours:Double = totalMinutes / 60
        val hours:Int = (totalHours % 60).toInt()
        var timeout:String = String.format("%02d:%02d:%02d",hours,minutes,seconds)
        return timeout
    }
}