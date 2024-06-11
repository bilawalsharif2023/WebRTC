package com.example.webrtcexample.test

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import com.example.webrtcexample.R
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.SessionDescription
import org.webrtc.SurfaceViewRenderer


class MainActivity : AppCompatActivity() {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private val SCREEN_CAPTURE_REQUEST_CODE = 1000
        private val REQUEST_CODE_OVERLAY_PERMISSION = 5000
    }

    private lateinit var remote_view: SurfaceViewRenderer
    private lateinit var local_view: SurfaceViewRenderer
    private lateinit var call_button: ImageView
    private lateinit var switch_camera_button: ImageView
    private lateinit var mic_button: ImageView
    private lateinit var video_button: ImageView
    private lateinit var end_call_button: ImageView
    private lateinit var rtcClient: RTCClient
    private lateinit var signallingClient: SignallingClient
    private lateinit var remote_view_loading: ProgressBar
    private lateinit var mediaProjectionManager: MediaProjectionManager

    private lateinit var windowManager: WindowManager
    private lateinit var floatingWidget: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val sdpObserver = object : AppSdpObserver() {
        override fun onCreateSuccess(sdp: SessionDescription?) {
            super.onCreateSuccess(sdp)
            signallingClient.send(sdp)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        /*        remote_view = findViewById(R.id.remote_view)
                local_view = findViewById(R.id.local_view)
                call_button = findViewById(R.id.call_button)
                remote_view_loading = findViewById(R.id.remote_view_loading)*/
        initViews()
        checkCameraPermission()
    }

    private fun initViews() {
        if (Settings.canDrawOverlays(this)) {
            setupFloatingWidget()
        } else {
            requestOverlayPermission()
        }

        /*        switch_camera_button = findViewById(R.id.switch_camera_button)
                switch_camera_button.setOnClickListener {
                    mediaProjectionManager =
                        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
                    startActivityForResult(permissionIntent, SCREEN_CAPTURE_REQUEST_CODE)
                    startService(Intent(this, ScreenCaptureService::class.java))
                }*/
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
    }

    private fun setupFloatingWidget() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        floatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget, null)
        layoutParams = WindowManager.LayoutParams(
            convertDpToPixel(300F, this).toInt(),
            convertDpToPixel(400F, this).toInt(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        layoutParams.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        windowManager.addView(floatingWidget, layoutParams)

        local_view = floatingWidget.findViewById(R.id.local_view)
        remote_view = floatingWidget.findViewById(R.id.remote_view)
        call_button = floatingWidget.findViewById(R.id.call_button)
        end_call_button = floatingWidget.findViewById(R.id.end_call_button)
        remote_view_loading = floatingWidget.findViewById(R.id.remote_view_loading)
        switch_camera_button = floatingWidget.findViewById(R.id.switch_camera_button)
        switch_camera_button.setOnClickListener {
            mediaProjectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val permissionIntent = mediaProjectionManager.createScreenCaptureIntent()
            startActivityForResult(permissionIntent, SCREEN_CAPTURE_REQUEST_CODE)
            startService(Intent(this, ScreenCaptureService::class.java))
        }
        end_call_button.setOnClickListener {
            removeFloatingWidget()
        }

        floatingWidget.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingWidget, layoutParams)
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.let {
                rtcClient.startScreenCapture(resultCode, it)
            }
        }
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                CAMERA_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission()
        } else {
            onCameraPermissionGranted()
        }
    }

    private fun onCameraPermissionGranted() {
        rtcClient = RTCClient(
            application,
            object : PeerConnectionObserver() {
                override fun onIceCandidate(ice: IceCandidate?) {
                    super.onIceCandidate(ice)
                    signallingClient.send(ice)
                    rtcClient.addIceCandidate(ice)
                }

                override fun onAddStream(media: MediaStream?) {
                    super.onAddStream(media)
                    media?.videoTracks?.get(0)?.addSink(remote_view)
                    Log.d("onAddStream", "Remote stream added, video track sinks added")
                }
            }
        )
        rtcClient.initSurfaceView(local_view)
        rtcClient.initSurfaceView(remote_view)
        rtcClient.startLocalVideoCapture(local_view)
        signallingClient = SignallingClient(createSignallingClientListener())
        call_button.setOnClickListener {
            rtcClient.call(sdpObserver)
        }
    }

    private fun createSignallingClientListener() = object : SignallingClientListener {
        override fun onConnectionEstablished() {
            call_button.isClickable = true
        }

        override fun onOfferReceived(description: SessionDescription) {
            try {
                rtcClient.onRemoteSessionReceived(description)
                rtcClient.answer(sdpObserver)
                (getSystemService(Context.AUDIO_SERVICE) as AudioManager).apply {
                    stopBluetoothSco()
                    isBluetoothScoOn = false
                    isSpeakerphoneOn = true
                }
                remote_view_loading.isGone = true
            } catch (ex: Exception) {
                Log.d("onOfferReceived", "onOfferReceived: $ex")
            }

        }

        override fun onAnswerReceived(description: SessionDescription) {
            rtcClient.onRemoteSessionReceived(description)
            remote_view_loading.isGone = true
        }

        override fun onIceCandidateReceived(iceCandidate: IceCandidate) {
            rtcClient.addIceCandidate(iceCandidate)
        }
    }

    private fun requestCameraPermission(dialogShown: Boolean = false) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                CAMERA_PERMISSION
            ) && !dialogShown
        ) {
            showPermissionRationaleDialog()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(CAMERA_PERMISSION),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showPermissionRationaleDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera Permission Required")
            .setMessage("This app need the camera to function")
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                requestCameraPermission(true)
            }
            .setNegativeButton("Deny") { dialog, _ ->
                dialog.dismiss()
                onCameraPermissionDenied()
            }
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onCameraPermissionGranted()
        } else {
            onCameraPermissionDenied()
        }
    }

    private fun onCameraPermissionDenied() {
        Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        signallingClient.destroy()
        removeFloatingWidget()
        super.onDestroy()
    }

    private fun switchCamera() {
        rtcClient.switchCamera()
    }

    private fun removeFloatingWidget() {
        if (::floatingWidget.isInitialized) {
            windowManager.removeView(floatingWidget)
        }
        if (::local_view.isInitialized) {
            local_view.release()
        }
        if (::remote_view.isInitialized) {
            remote_view.release()
        }
    }

    private fun convertDpToPixel(dp: Float, context: Context): Float {
        return dp * (context.resources.displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT)
    }
}
