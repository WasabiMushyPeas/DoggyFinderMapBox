package com.example.doggyfindermapbox

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.mapbox.bindgen.Value
import com.mapbox.common.NetworkRestriction
import com.mapbox.common.TileDataDomain
import com.mapbox.common.TileRegionLoadOptions
import com.mapbox.common.TileStore
import com.mapbox.common.TileStoreOptions
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.GlyphsRasterizationMode
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.OfflineManager
import com.mapbox.maps.Style
import com.mapbox.maps.StylePackLoadOptions
import com.mapbox.maps.TilesetDescriptorOptions
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.locationcomponent.location
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin


var mapView: MapView? = null


// Location text view, button, and Edit Text variables
private var locationTextViewUserLat: TextView? = null
private var locationTextViewUserLong: TextView? = null
private var locationTextViewDogLat: TextView? = null
private var locationTextViewDogLong: TextView? = null
private var locationTextViewDistance: TextView? = null
private var inputEditTextZoom: EditText? = null
private var inputEditTextLat: EditText? = null
private var inputEditTextLong: EditText? = null
private var locationButton: Button? = null
private var downloadButton: Button? = null
private var compassButton: Button? = null

// Location variables
private var fusedLocationProviderClient: FusedLocationProviderClient? = null
private var currentLocation: Location? = null
private var dogLocation: Location? = null
private var distance: Double? = 0.0


// Implement SensorEventListener

class MainActivity : AppCompatActivity(), SensorEventListener {
    // Compass variables
    private var image: ImageView? = null
    private var currentDegree = 0f
    private var mSensorManager: SensorManager? = null
    private var tvHeading: TextView? = null
    private var tvDistance: TextView? = null
    private var isCompassOpen = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Set dog location to random lat and long
        dogLocation = Location("")
        dogLocation?.latitude = 35.144687
        dogLocation?.longitude = -106.651482


        // Initialize location views and button variables


        findViewById<Button>(R.id.download_button)
            .setOnClickListener {
                onButtonShowPopupWindowClick(findViewById(R.id.download_button))
            }




        mapView = findViewById(R.id.mapView)
        mapView?.getMapboxMap()?.loadStyleUri(
            Style.SATELLITE_STREETS
        )
        // After the style is loaded, initialize the Location component.
        {
            mapView?.location?.updateSettings {
                enabled = true
                pulsingEnabled = true
            }
        }


        // initialize your android device sensor capabilities
        mSensorManager = getSystemService(SENSOR_SERVICE) as SensorManager?;


        var view = findViewById<View>(R.id.download_button)

        view.setOnTouchListener(object : OnTouchListener {
            var handler = Handler()
            var numberOfTaps = 0
            var lastTapTimeMs: Long = 0
            var touchDownMs: Long = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> touchDownMs = System.currentTimeMillis()
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacksAndMessages(null)
                        if (System.currentTimeMillis() - touchDownMs > ViewConfiguration.getTapTimeout()) {
                            //it was not a tap
                            numberOfTaps = 0
                            lastTapTimeMs = 0
                        }
                        if (numberOfTaps > 0
                            && System.currentTimeMillis() - lastTapTimeMs < ViewConfiguration.getDoubleTapTimeout()
                        ) {
                            numberOfTaps += 1
                        } else {
                            numberOfTaps = 1
                        }
                        lastTapTimeMs = System.currentTimeMillis()
                        if (numberOfTaps == 3) {
                            onButtonCompassClick(findViewById(R.id.download_button))
                            //handle triple tap
                        } else if (numberOfTaps == 2) {
                            handler.postDelayed({ //handle double tap
                                onButtonShowPopupWindowClick(findViewById(R.id.download_button))
                            }, ViewConfiguration.getDoubleTapTimeout().toLong())
                        }
                    }
                }
                return true
            }
        })


    }

    private fun onButtonUpdateLocationClick(locationButton: Button) {
        // Check android version
        // Check if permission to access location is granted
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // If permission not granted, request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } else {
            // If permission granted, get location
            // Check if GPS is enabled
            val locationManager =
                getSystemService(LOCATION_SERVICE) as LocationManager
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // Set current location to location variable
                currentLocation =
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)

                // Update location text views
                locationTextViewUserLat?.text = currentLocation?.latitude.toString()
                locationTextViewUserLong?.text = currentLocation?.longitude.toString()
                locationTextViewDogLat?.text = dogLocation?.latitude.toString()
                locationTextViewDogLong?.text = dogLocation?.longitude.toString()

                var currentLocation =
                    Point.fromLngLat(currentLocation?.longitude!!, currentLocation?.latitude!!)

                // Set camera position to current location
                val cameraPosition = CameraOptions.Builder()
                    .center(currentLocation)
                    .zoom(15.0)
                    .build()
                mapView?.getMapboxMap()?.setCamera(cameraPosition)
                // move maker marker to dog location
                addAnnotationToMap(dogLocation?.latitude!!, dogLocation?.longitude!!)


                // Calculate distance between user and dog
                distance = distance(
                    currentLocation.latitude(),
                    currentLocation.longitude(),
                    dogLocation?.latitude!!,
                    dogLocation?.longitude!!
                )

                // Update distance text view rounded
                locationTextViewDistance?.text = distance!!.roundToInt().toString() + "m"
            } else {
                // Send toast to user to enable GPS
                Toast.makeText(this, "Please enable GPS", Toast.LENGTH_SHORT).show()
            }
        }

    }


    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }


    // on button click, download offline map
    private fun onButtonDownloadClick(locationButton: Button) {
        // Check to see if the edit text inputs are empty
        if (inputEditTextZoom?.text?.isEmpty() == true || inputEditTextLat?.text?.isEmpty() == true || inputEditTextLong?.text?.isEmpty() == true) {
            // Send toast to user to enter values
            Toast.makeText(this, "Please enter values", Toast.LENGTH_SHORT).show()
        } else {
            // Set zoom, lat and long to edit text values
            var zoom = inputEditTextZoom?.text.toString().toDouble()
            var lat = inputEditTextLat?.text.toString().toDouble()
            var long = inputEditTextLong?.text.toString().toDouble()


            var currentTime: Date = Calendar.getInstance().time
            var currentDate: String =
                SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(currentTime)
            // Define download location current location
            var downloadLocation: Point =
                Point.fromLngLat(currentLocation?.longitude!!, currentLocation?.latitude!!)

            var MetadataID =
                currentTime.toString() + "_" + currentDate.toString() + "_" + zoom.toString() + "_" + lat.toString() + "_" + long.toString()

            // log variables to console
            Log.d("Download", "Zoom: $zoom")
            Log.d("Download", "Lat: $lat")
            Log.d("Download", "Long: $long")
            Log.d("Download", "MetadataID: $MetadataID")
            Log.d("Download", "DownloadLocation: $downloadLocation")


            // Define Style Pack
            val stylePackLoadOptions = StylePackLoadOptions.Builder()
                .glyphsRasterizationMode(GlyphsRasterizationMode.IDEOGRAPHS_RASTERIZED_LOCALLY)
                .metadata(Value(MetadataID + "_StylePack"))
                .build()

            // Define tileset descriptor and tile region
            val offlineManager: OfflineManager =
                OfflineManager(MapInitOptions.getDefaultResourceOptions(this))

            val tilesetDescriptor = offlineManager.createTilesetDescriptor(
                TilesetDescriptorOptions.Builder()
                    .styleURI(Style.SATELLITE_STREETS)
                    .minZoom(0)
                    .maxZoom(16)
                    .build()
            )
            val tileRegionLoadOptions = TileRegionLoadOptions.Builder()
                .geometry(downloadLocation)
                .descriptors(listOf(tilesetDescriptor))
                .metadata(Value(MetadataID + "_TileRegion"))
                .acceptExpired(true)
                .networkRestriction(NetworkRestriction.NONE)
                .build()


            val stylePackCancelable = offlineManager.loadStylePack(
                Style.SATELLITE_STREETS,
                // Build Style pack load options
                stylePackLoadOptions,
                { progress ->
                    // Handle the download progress using toasts and log statements
                    Toast.makeText(
                        this,
                        "Style pack download progress: ${progress}%",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d("Download", "Style pack download progress: ${progress}%")

                },
                { expected ->
                    if (expected.isValue) {
                        expected.value?.let { stylePack ->
                            // Style pack download finished successfully using toasts
                            Toast.makeText(
                                this,
                                "Style pack downloaded successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.d("Download", "Style pack downloaded successfully")
                        }
                    }
                    expected.error?.let {
                        // Handle errors that occurred during the style pack download using toasts
                        Toast.makeText(
                            this,
                            "Style pack download error: ${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.d("Download", "Style pack download error: ${it.message}")
                    }
                }
            )

            Log.d("Download", "Style pack load options: $stylePackLoadOptions")
            val tileStore = TileStore.create().also {
                // Set default access token for the created tile store instance
                it.setOption(
                    TileStoreOptions.MAPBOX_ACCESS_TOKEN,
                    TileDataDomain.MAPS,
                    Value(getString(R.string.mapbox_access_token))
                )
            }
            val tileRegionCancelable = tileStore.loadTileRegion(
                (MetadataID + "_TileRegionID"),
                TileRegionLoadOptions.Builder()
                    .geometry(downloadLocation)
                    .descriptors(listOf(tilesetDescriptor))
                    .metadata(Value(MetadataID + "_TileRegion"))
                    .acceptExpired(true)
                    .networkRestriction(NetworkRestriction.NONE)
                    .build(),
                { progress ->
                    // Handle the download progress using toasts
                    Toast.makeText(
                        this,
                        "Tile region download progress: ${progress}%",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d("Download", "Tile region download progress: ${progress}%")
                }
            ) { expected ->
                if (expected.isValue) {
                    // Tile region download finishes successfully using toasts
                    Toast.makeText(
                        this,
                        "Tile region downloaded successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d("Download", "Tile region downloaded successfully")
                }
                expected.error?.let {
                    // Handle errors that occurred during the tile region download using toasts
                    Toast.makeText(
                        this,
                        "Tile region download error: ${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.d("Download", "Tile region download error: ${it.message}")
                }
            }

        }
        Log.d("Download", "Download finished")


    }


    fun onButtonShowPopupWindowClick(view: View?) {

        // inflate the layout of the popup window
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView: View = inflater.inflate(R.layout.popup_window, null)

        // create the popup window
        val width = LinearLayout.LayoutParams.WRAP_CONTENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val focusable = true // lets taps outside the popup also dismiss it
        val popupWindow = PopupWindow(popupView, width, height, focusable)

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)

        // Initialize location views and button variables
        locationTextViewUserLat = popupView.findViewById(R.id.location_text_view_user_lat)
        locationTextViewUserLong = popupView.findViewById(R.id.location_text_view_user_long)
        locationTextViewDogLat = popupView.findViewById(R.id.location_text_view_dog_lat)
        locationTextViewDogLong = popupView.findViewById(R.id.location_text_view_dog_long)
        locationTextViewDistance = popupView.findViewById(R.id.location_text_view_distance)
        inputEditTextZoom = popupView.findViewById(R.id.zoom_input)
        inputEditTextLat = popupView.findViewById(R.id.lat_input)
        inputEditTextLong = popupView.findViewById(R.id.long_input)
        downloadButton = popupView.findViewById(R.id.download_button)
        locationButton = popupView.findViewById(R.id.location_button)

        locationTextViewDistance?.text = distance?.roundToInt().toString() + "m"
        locationTextViewDogLat?.text = dogLocation?.latitude.toString()
        locationTextViewDogLong?.text = dogLocation?.longitude.toString()


        // Set location button on click listener
        locationButton?.setOnClickListener {
            onButtonUpdateLocationClick(locationButton!!)
        }

        // Set download button on click listener
        downloadButton?.setOnClickListener {
            onButtonDownloadClick(downloadButton!!)
        }


        // dismiss the popup window when touched
        popupView.setOnTouchListener { v, event ->
            popupWindow.dismiss()
            true
        }

    }

    private fun onButtonCompassClick(view: View?) {
        // inflate the layout of the popup window
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val popupView: View = inflater.inflate(R.layout.compas_window, null)

        // create the popup window
        val width = LinearLayout.LayoutParams.WRAP_CONTENT
        val height = LinearLayout.LayoutParams.WRAP_CONTENT
        val focusable = true // lets taps outside the popup also dismiss it
        val popupWindow = PopupWindow(popupView, width, height, focusable)

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0)





        image = popupView.findViewById(R.id.imageViewCompass)

        tvHeading = popupView.findViewById(R.id.tvHeading)
        tvDistance = popupView.findViewById(R.id.tvDistance)




        isCompassOpen = true


        // dismiss the popup window when touched
        popupView.setOnTouchListener { v, event ->
            isCompassOpen = false
            popupWindow.dismiss()
            true
        }

    }


    // Distance in meters between two lat/long points
    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val theta = lon1 - lon2
        var dist =
            sin(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) + cos(
                Math.toRadians(lat1)
            ) * cos(Math.toRadians(lat2)) * cos(Math.toRadians(theta))
        dist = acos(dist)
        dist = Math.toDegrees(dist)
        dist *= 60 * 1.1515
        dist *= 1609.344
        return dist
    }

    // Function called compute heading that takes in two lat/long doubles and compass heading and returns the heading
    private fun computeHeading(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        heading: Float
    ): Double? {
        val dLon = lon2 - lon1
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        var brng = Math.toDegrees(atan2(y, x))
        brng = 360 - (brng + 360) % 360
        return (brng - heading).toDouble()
    }


    private fun updateGPS() {
        // Check android version
        // Check if permission to access location is granted
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // If permission not granted, request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } else {
            // If permission granted, get location
            // Check if GPS is enabled
            val locationManager =
                getSystemService(LOCATION_SERVICE) as LocationManager
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                // Set current location to location variable
                currentLocation =
                    locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)


                var currentLocation =
                    Point.fromLngLat(currentLocation?.longitude!!, currentLocation?.latitude!!)

            } else {
                // Send toast to user to enable GPS
                Toast.makeText(this, "Please enable GPS", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun addAnnotationToMap(lat: Double, long: Double) {
// Create an instance of the Annotation API and get the PointAnnotationManager.
        bitmapFromDrawableRes(
            this@MainActivity,
            R.drawable.red_marker
        )?.let {
            val annotationApi = mapView?.annotations
            val pointAnnotationManager = annotationApi?.createPointAnnotationManager(mapView!!)
// Set options for the resulting symbol layer.
            val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
// Define a geographic coordinate.
                .withPoint(Point.fromLngLat(long, lat))
// Specify the bitmap you assigned to the point annotation
// The bitmap will be added to map style automatically.
                .withIconImage(it)
// Add the resulting pointAnnotation to the map.
            pointAnnotationManager?.create(pointAnnotationOptions)
        }
    }

    private fun bitmapFromDrawableRes(context: Context, @DrawableRes resourceId: Int) =
        convertDrawableToBitmap(AppCompatResources.getDrawable(context, resourceId))

    private fun convertDrawableToBitmap(sourceDrawable: Drawable?): Bitmap? {
        if (sourceDrawable == null) {
            return null
        }
        return if (sourceDrawable is BitmapDrawable) {
            sourceDrawable.bitmap
        } else {
// copying drawable object to not manipulate on the same reference
            val constantState = sourceDrawable.constantState ?: return null
            val drawable = constantState.newDrawable().mutate()
            val bitmap: Bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth, drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (isCompassOpen && tvHeading != null && image != null) {
            var heading: Double? = 0.0

            updateGPS()

            // calculate the heading in degrees from your current location to the dog's location
            if (currentLocation != null && dogLocation != null) {
                heading = computeHeading(
                    currentLocation!!.latitude,
                    currentLocation!!.longitude,
                    dogLocation!!.latitude,
                    dogLocation!!.longitude,
                    event!!.values[0]
                )
            }
            var dist = distance(
                currentLocation!!.latitude,
                currentLocation!!.longitude,
                dogLocation!!.latitude,
                dogLocation!!.longitude
            )

            // round the heading to the nearest degree tenth of a degree
            heading = heading?.let { round(it * 10) / 10 }
            // round distance to the nearest tenth of a meter
            dist = round(dist * 10) / 10


            // get the angle around the z-axis rotated


            tvHeading!!.text =
                "Heading: " + heading?.let { java.lang.Double.toString(it) } + " degrees"
            // update tvDistance with the distance between the current location and the dog's location
            tvDistance!!.text = "Distance: " + dist.toString() + " meters"


            // create a rotation animation to point to the dog's location
            val ra = RotateAnimation(
                currentDegree,
                heading!!.toFloat(),
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
//            val ra = RotateAnimation(
//                currentDegree,
//                (-heading!!).toFloat(),
//                Animation.RELATIVE_TO_SELF, 0.5f,
//                Animation.RELATIVE_TO_SELF,
//                0.5f
//            )

            // how long the animation will take place

            // how long the animation will take place
            ra.duration = 210

            // set the animation after the end of the reservation status

            // set the animation after the end of the reservation status
            ra.fillAfter = true

            // Start the animation

            // Start the animation
            image!!.startAnimation(ra)
            currentDegree = heading.toFloat()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // toast message
        Toast.makeText(this, "Compass accuracy changed", Toast.LENGTH_SHORT).show()

    }

    override fun onResume() {
        super.onResume()


        // for the system's orientation sensor registered listeners
        mSensorManager!!.registerListener(
            this, mSensorManager!!.getDefaultSensor(Sensor.TYPE_ORIENTATION),
            SensorManager.SENSOR_DELAY_GAME
        )

    }

    override fun onPause() {

        super.onPause()

        // to stop the listener and save battery
        mSensorManager!!.unregisterListener(this)

    }


}