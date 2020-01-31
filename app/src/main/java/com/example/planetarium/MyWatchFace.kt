package com.example.planetarium

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Message
import androidx.palette.graphics.Palette
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.view.SurfaceHolder
import android.widget.Toast

import java.lang.ref.WeakReference
import java.time.LocalDate

import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import kotlin.collections.ArrayList

/**
 * Updates rate in milliseconds for interactive mode. We update once a second to advance the
 * second hand.
 */
private const val INTERACTIVE_UPDATE_RATE_MS = 1000

/**
 * Handler message id for updating the time periodically in interactive mode.
 */
private const val MSG_UPDATE_TIME = 0

private const val HOUR_STROKE_WIDTH = 5f
private const val MINUTE_STROKE_WIDTH = 3f
private const val SECOND_TICK_STROKE_WIDTH = 2f

private const val CENTER_GAP_AND_CIRCLE_RADIUS = 4f

private const val SHADOW_RADIUS = 6f

private const val START_DATE = "2019-09-26"

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 * Important Note: Because watch face apps do not have a default Activity in
 * their project, you will need to set your Configurations to
 * "Do not launch Activity" for both the Wear and/or Application modules. If you
 * are unsure how to do this, please review the "Run Starter project" section
 * in the Google Watch Face Code Lab:
 * https://codelabs.developers.google.com/codelabs/watchface/index.html#0
 */
class MyWatchFace : CanvasWatchFaceService() {

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: MyWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<MyWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get()
            if (engine != null) {
                when (msg.what) {
                    MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
                }
            }
        }
    }

    inner class Engine : CanvasWatchFaceService.Engine() {
        private lateinit var mPlanets: Map<String, Planet>
        private lateinit var mCalendar: Calendar

        private var mRegisteredTimeZoneReceiver = false
        private var mMuteMode: Boolean = false
        private var mCenterX: Float = 0F
        private var mCenterY: Float = 0F

        private var mSecondHandLength: Float = 0F
        private var mThirdHandLength: Float = 0F
        private var sMinuteHandLength: Float = 0F
        private var sHourHandLength: Float = 0F

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private var mWatchHandColor: Int = 0
        private var mWatchHandHighlightColor: Int = 0
        private var mWatchHandShadowColor: Int = 0

        //current day to drive the movement of the planets
        private var mCurrentDay: Int = 0

        private lateinit var mHourPaint: Paint
        private lateinit var mMinutePaint: Paint
        private lateinit var mSecondPaint: Paint
        private lateinit var mThirdPaint: Paint
        private lateinit var mTickAndCirclePaint: Paint
        private lateinit var mTextPaint: Paint

        private lateinit var mBackgroundPaint: Paint
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mGrayBackgroundBitmap: Bitmap

        private var mAmbient: Boolean = false
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false

        private lateinit var activePlanetWatchFace: String

        /* Handler to update the time once a second in interactive mode. */
        private val mUpdateTimeHandler = EngineHandler(this)

        private val mTimeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@MyWatchFace)
                    .setAcceptsTapEvents(true)
                    .build())

            mCalendar = Calendar.getInstance()
            mCurrentDay = mCalendar.get(Calendar.DATE)
            activePlanetWatchFace = ""

            initializeBackground()
            initializePlanets()
            initializeWatchFace()
        }

        private fun initializeBackground() {
            mBackgroundPaint = Paint().apply {
                color = Color.BLACK
            }
            mBackgroundBitmap = BitmapFactory.decodeResource(resources, R.drawable.watchface_test)

            /* Extracts colors from background image to improve watchface style. */
            Palette.from(mBackgroundBitmap).generate {
                it?.let {
                    mWatchHandHighlightColor = it.getVibrantColor(Color.CYAN)
                    mWatchHandColor = it.getLightVibrantColor(Color.RED)
                    mWatchHandShadowColor = it.getDarkMutedColor(Color.BLACK)
                    mMinutePaint.color = Color.RED
                    updateWatchHandStyle()
                }
            }
        }

        private fun getOffset():Long {
            val start:Calendar = Calendar.getInstance()
            start.set(2019, 7, 26) // months are indexed from 0...WHY?! anyway 7 = August
            return (mCalendar.timeInMillis - start.timeInMillis) / 86400000
        }

        //private val NAMES = ArrayList<String>()
        private fun initializePlanets() {

            var mercuryBitmap = BitmapFactory.decodeResource(resources, R.drawable.mercury)
            var venusBitmap = BitmapFactory.decodeResource(resources, R.drawable.venus)
            var earthBitmap = BitmapFactory.decodeResource(resources, R.drawable.earth)
            var marsBitmap = BitmapFactory.decodeResource(resources, R.drawable.mars)
            var jupiterBitmap = BitmapFactory.decodeResource(resources, R.drawable.jupiter)
            var saturnBitmap = BitmapFactory.decodeResource(resources, R.drawable.saturn)
            var uranusBitmap = BitmapFactory.decodeResource(resources, R.drawable.uranus)
            var neptuneBitmap = BitmapFactory.decodeResource(resources, R.drawable.neptune)


            mPlanets = mapOf(
                "mercury" to Planet(99.7f, 1407.6, 88f, 0.12f, mercuryBitmap),
                "venus" to Planet(45.4f, 5832.5, 224.7f, 0.2f, venusBitmap),
                "earth" to Planet(233.1f, 24.0, 365.2f, 0.28f, earthBitmap),
                "mars" to Planet(48.2f, 24.6, 687f, 0.36f, marsBitmap),
                "jupiter" to Planet(298.8f, 9.9, 4331f, 0.45f, jupiterBitmap),
                "saturn" to Planet(275.8f, 10.7, 10747f, 0.56f, saturnBitmap),
                "uranus" to Planet(170.1f, 17.2, 30589f, 0.66f, uranusBitmap),
                "neptune" to Planet(217.1f, 16.1, 59800f, 0.76f, neptuneBitmap)
            )

            //for (p in mPlanets) {
                var i:Int = 0
                val Frames = ArrayList<Bitmap>()
                for (i in 0..59) {
                    var name: String = String.format("earth_%05d", i)//String.format("%s_%05d", p.key,i)
                    //println(name)
                    val resID: Int = resources.getIdentifier(name, "drawable", packageName)
                    val frame: Bitmap = BitmapFactory.decodeResource(resources, resID)
                    Frames.add(frame)
                }
                //p.value.wfBg = Frames
                mPlanets.getValue("earth").wfBg = Frames
            //}

            mPlanets.getValue("mercury").wfBitmap = BitmapFactory.decodeResource(resources, R.drawable.mercury_wf)
            mPlanets.getValue("venus").wfBitmap = BitmapFactory.decodeResource(resources, R.drawable.venus_wf)
            mPlanets.getValue("earth").wfBitmap = BitmapFactory.decodeResource(resources, R.drawable.earth_wf)
            mPlanets.getValue("mars").wfBitmap = BitmapFactory.decodeResource(resources, R.drawable.mars_wf)
            mPlanets.getValue("jupiter").wfBitmap = BitmapFactory.decodeResource(resources, R.drawable.jupiter_wf)
            mPlanets.getValue("saturn").wfBitmap = BitmapFactory.decodeResource(resources, R.drawable.saturn_wf)
            mPlanets.getValue("uranus").wfBitmap = BitmapFactory.decodeResource(resources, R.drawable.uranus_wf)
            mPlanets.getValue("neptune").wfBitmap = BitmapFactory.decodeResource(resources, R.drawable.neptune_wf)

            val offset = getOffset()
            for (p in mPlanets) {
                p.value.rotation += offset * p.value.revAngle
            }
        }

        private fun initializeWatchFace() {
            /* Set defaults for colors */
            mWatchHandColor = Color.MAGENTA
            mWatchHandHighlightColor = Color.CYAN
            mWatchHandShadowColor = Color.BLACK

            mHourPaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = HOUR_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                        SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
            }

            mMinutePaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = MINUTE_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                        SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
            }

            mSecondPaint = Paint().apply {
                color = mWatchHandHighlightColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                        SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
            }

            mThirdPaint = Paint().apply {
                color = mWatchHandHighlightColor
                strokeWidth = MINUTE_STROKE_WIDTH
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
            }

            mTickAndCirclePaint = Paint().apply {
                color = mWatchHandColor
                strokeWidth = SECOND_TICK_STROKE_WIDTH
                isAntiAlias = true
                style = Paint.Style.STROKE
                setShadowLayer(
                        SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
            }

            mTextPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(SHADOW_RADIUS, 0f, 0f, Color.BLACK)
                textAlign = Paint.Align.CENTER
            }
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(
                    WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(
                    WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            updateWatchHandStyle()

            // Check and trigger whether or not timer should be running (only
            // in active mode).
            updateTimer()
        }

        private fun updateWatchHandStyle() {
            for (p in mPlanets) {
                p.value.setActiveBitmap(mAmbient)
            }

            if (mAmbient) {
                //clear active watch face if one is active
                if (activePlanetWatchFace != "") {
                    activePlanetWatchFace = ""
                }
                mHourPaint.color = Color.WHITE
                mMinutePaint.color = Color.WHITE
                mSecondPaint.color = Color.WHITE
                mThirdPaint.color = Color. WHITE
                mTickAndCirclePaint.color = Color.WHITE

                mHourPaint.isAntiAlias = false
                mMinutePaint.isAntiAlias = false
                mSecondPaint.isAntiAlias = false
                mThirdPaint.isAntiAlias  = false
                mTickAndCirclePaint.isAntiAlias = false

                mHourPaint.clearShadowLayer()
                mMinutePaint.clearShadowLayer()
                mSecondPaint.clearShadowLayer()
                mThirdPaint.clearShadowLayer()
                mTickAndCirclePaint.clearShadowLayer()

            } else {
                /*mHourPaint.color = Color.BLUE
                mMinutePaint.color = Color.BLUE
                mSecondPaint.color = Color.CYAN
                mThirdPaint.color = Color.YELLOW
                mTickAndCirclePaint.color = Color.BLUE

                mHourPaint.isAntiAlias = true
                mMinutePaint.isAntiAlias = true
                mSecondPaint.isAntiAlias = true
                mThirdPaint.isAntiAlias = true
                mTickAndCirclePaint.isAntiAlias = true

                mHourPaint.setShadowLayer(
                        SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
                mMinutePaint.setShadowLayer(
                        SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
                mSecondPaint.setShadowLayer(
                        SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
                mThirdPaint.setShadowLayer(
                    SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)
                mTickAndCirclePaint.setShadowLayer(
                        SHADOW_RADIUS, 0f, 0f, mWatchHandShadowColor)*/

                //mTextPaint.color = Color.WHITE
            }
        }

        override fun onInterruptionFilterChanged(interruptionFilter: Int) {
            super.onInterruptionFilterChanged(interruptionFilter)
            val inMuteMode = interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode
                mHourPaint.alpha = if (inMuteMode) 100 else 255
                mMinutePaint.alpha = if (inMuteMode) 100 else 255
                mThirdPaint.alpha = if (inMuteMode) 100 else 255
                mSecondPaint.alpha = if (inMuteMode) 80 else 255
                invalidate()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f
            mCenterY = height / 2f

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (mCenterX * 0.8).toFloat()
            mThirdHandLength = (mCenterX * 0.65).toFloat()
            sMinuteHandLength = (mCenterX * 0.75).toFloat()
            sHourHandLength = (mCenterX * 0.5).toFloat()


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            val scale = width.toFloat() / mBackgroundBitmap.width.toFloat()

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (mBackgroundBitmap.width * scale).toInt(),
                    (mBackgroundBitmap.height * scale).toInt(), true)

            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap()
            }
        }

        private fun initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.width,
                    mBackgroundBitmap.height,
                    Bitmap.Config.ARGB_8888)
            val canvas = Canvas(mGrayBackgroundBitmap)
            val grayPaint = Paint()
            val colorMatrix = ColorMatrix()
            colorMatrix.setSaturation(0f)
            val filter = ColorMatrixColorFilter(colorMatrix)
            grayPaint.colorFilter = filter
            canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, grayPaint)
        }

        /**
         * Captures tap event (and tap type). The [WatchFaceService.TAP_TYPE_TAP] case can be
         * used for implementing specific logic to handle the gesture.
         */
        var p:Point = Point(0,0)
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            p = Point(x, y)
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                    // The user has started touching the screen.
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                    // The user has started a different gesture or otherwise cancelled the tap.
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    //Toast.makeText(applicationContext, R.string.message, Toast.LENGTH_SHORT).show()
                    //if no active watchface, check if planet is requested
                    //activePlanetWatchFace = ""
                    if(activePlanetWatchFace == "") {
                        for (p in mPlanets) {
                            p.value.getGlobalCoords()
                            if (x >= p.value.point.x - (p.value.planetBitmap.width*2) && x <= p.value.point.x + p.value.planetBitmap.width*2) {
                                if (y >= p.value.point.y - p.value.planetBitmap.height*2 && y <= p.value.point.y + p.value.planetBitmap.height*2) {
                                    activePlanetWatchFace = p.key
                                    break
                                }
                            }
                        }

                    } else { // dismiss active planet watchface
                        activePlanetWatchFace = ""
                    }
                    //Toast.makeText(applicationContext, activePlanetWatchFace, Toast.LENGTH_SHORT).show()
                }
            }
            invalidate()
        }


        override fun onDraw(canvas: Canvas, bounds: Rect) {
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now

            drawBackground(canvas)
            drawWatchFace(canvas)
        }

        private fun drawBackground(canvas: Canvas) {

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                activePlanetWatchFace = "" // resets active watch face
                canvas.drawColor(Color.BLACK)
            } else if (mAmbient) {
                activePlanetWatchFace = "" // resets active watch face
                canvas.drawBitmap(mGrayBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            } else {
                canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            }
        }



        private fun drawWatchFace(canvas: Canvas) {
            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            /*val innerTickRadius = mCenterX - 10
            val outerTickRadius = mCenterX
            for (tickIndex in 0..11) {
                val tickRot = (tickIndex.toDouble() * Math.PI * 2.0 / 12).toFloat()
                val innerX = Math.sin(tickRot.toDouble()).toFloat() * innerTickRadius
                val innerY = (-Math.cos(tickRot.toDouble())).toFloat() * innerTickRadius
                val outerX = Math.sin(tickRot.toDouble()).toFloat() * outerTickRadius
                val outerY = (-Math.cos(tickRot.toDouble())).toFloat() * outerTickRadius
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint)
            }*/

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            val seconds =
                    mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f
            val secondsRotation = seconds * 6f

            val minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f

            val hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f
            val hoursRotation = mCalendar.get(Calendar.HOUR) * 30 + hourHandOffset

            //update planet rotations if day has changed
            if(mCurrentDay != mCalendar.get(Calendar.DATE)) {
                for (p in mPlanets) {
                    p.value.rotation += p.value.revAngle
                    p.value.getGlobalCoords()
                }
                mCurrentDay = mCalendar.get(Calendar.DATE)
            }

            /*
             * Save the canvas state before we can begin to rotate it.
            */

            /*var digitalString:String = String.format("%d:%d", mCalendar.get(Calendar.HOUR), mCalendar.get(Calendar.MINUTE))
            if (activePlanetWatchFace != "") {
                canvas.drawBitmap(mPlanets.getValue(activePlanetWatchFace).wfBitmap, 0f,0f,mBackgroundPaint)
                canvas.drawText(digitalString, 195f, 195f, mTextPaint)
            } else {*/

                var prevRot: Float = 0f
                canvas.save()
                for (p in mPlanets) {
                    canvas.rotate(p.value.rotation - prevRot, mCenterX, mCenterY)
                    //canvas.drawLine(mCenterX,mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS, mCenterX, mCenterY - (mCenterX * p.value.distance), p.value.activeColor)
                    canvas.drawBitmap(
                        p.value.activeBitmap,
                        195f - (p.value.activeBitmap.width / 2),
                        195f - (mCenterY * p.value.distance) - (p.value.activeBitmap.height / 2),
                        mBackgroundPaint
                    )
                    prevRot = p.value.rotation
                }
                canvas.restore()

                //canvas.drawText("hello", 100f, 300f, mTextPaint)
            //}


            /*canvas.drawLine(
                mCenterX,
                mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                mCenterX,
                mCenterY - mThirdHandLength,
                mThirdPaint)

            canvas.rotate(hoursRotation, mCenterX, mCenterY)
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourPaint)

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY)
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sMinuteHandLength,
                    mMinutePaint)
*/
            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                //draw planet watchface
                if (activePlanetWatchFace != "") {

                    //System.out.println("PRINTING WATCH FACE")
                    var secondString:String = String.format("%02d", mCalendar.get(Calendar.SECOND))
                    var minuteString:String = String.format("%02d", mCalendar.get(Calendar.MINUTE))
                    var hourString:String = String.format("%02d", mCalendar.get(Calendar.HOUR_OF_DAY))

                    var currentTimeString:String = String.format("%s:%s:%s", hourString, minuteString, secondString)
                    var digitalString:String = mPlanets.getValue(activePlanetWatchFace).getRelativeTime(mCalendar.get(Calendar.HOUR_OF_DAY), mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND))
                    if(activePlanetWatchFace == "earth") {
                        canvas.drawBitmap(mPlanets.getValue(activePlanetWatchFace).wfBg[mCalendar.get(Calendar.SECOND)], 0f, 0f, mBackgroundPaint)
                    }else {
                        canvas.drawBitmap(mPlanets.getValue(activePlanetWatchFace).wfBitmap, 0f,0f,mBackgroundPaint)

                    }
                    canvas.drawText(activePlanetWatchFace.capitalize(), canvas.width/2.toFloat(), 60f, mTextPaint)
                    canvas.drawText(digitalString, 195f, 195-10f, mTextPaint)
                    canvas.drawText(currentTimeString, 195f, 195+40f, mTextPaint)
                    //canvas.drawLine(0f, 195f, 390f, 195f, Paint(Color.RED))

                }
                //if(mCurrentDay != mCalendar.get(Calendar.DATE)) {
                    /*for (p in mPlanets) {
                        p.value.rotation += p.value.revAngle
                    }*/
                    //mCurrentDay = mCalendar.get(Calendar.DATE)
                //}
                /*canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY)
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint)*/

            }
            /*canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_GAP_AND_CIRCLE_RADIUS,
                    mTickAndCirclePaint)*/

            /* Restore the canvas' original orientation. */
            //canvas.restore()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@MyWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@MyWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts/stops the [.mUpdateTimeHandler] timer based on the state of the watch face.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer
         * should only run in active mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !mAmbient
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}


