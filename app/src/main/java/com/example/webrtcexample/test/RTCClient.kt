package com.example.webrtcexample.test

import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.widget.Toast
import org.webrtc.*

class RTCClient(
    private var context: Application,
    observer: PeerConnection.Observer
) {

    companion object {
        private const val LOCAL_TRACK_ID = "local_track"
        private const val LOCAL_STREAM_ID = "local_stream"
        private const val SCREEN_TRACK_ID = "screen_track"

    }
    private val mediaProjectionManager: MediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    private var mediaProjection: MediaProjection? = null
    private val rootEglBase: EglBase = EglBase.create()

    init {
        initPeerConnectionFactory(context)
    }

    private val iceServer = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer()
    )

    private var screenCapturer: VideoCapturer? = null
    private var localStream: MediaStream? = null
    private var screenTrack: VideoTrack? = null
    private val screenVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnectionFactory by lazy { buildPeerConnectionFactory() }
    private val videoCapturer by lazy { getVideoCapturer(context) }
    private val localVideoSource by lazy { peerConnectionFactory.createVideoSource(false) }
    private val peerConnection by lazy { buildPeerConnection(observer) }
    private val constraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
    }

    private fun initPeerConnectionFactory(context: Application) {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
    }

    private fun buildPeerConnectionFactory(): PeerConnectionFactory {
        return PeerConnectionFactory
            .builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(rootEglBase.eglBaseContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(rootEglBase.eglBaseContext, true, true))
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = true
                disableNetworkMonitor = true
            })
            .createPeerConnectionFactory()
    }

    private fun buildPeerConnection(observer: PeerConnection.Observer) = peerConnectionFactory.createPeerConnection(
        iceServer,
        observer
    )

/*    private fun getVideoCapturer(context: Context) =
        Camera2Enumerator(context).run {
            deviceNames.find {
                isFrontFacing(it)
            }?.let {
                createCapturer(it, null)
            } ?: throw IllegalStateException()
        }*/
    private fun getVideoCapturer(context: Context): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        val deviceName = enumerator.deviceNames.find { enumerator.isFrontFacing(it) }

        return if (deviceName != null) {
            enumerator.createCapturer(deviceName, null)
        } else {
            showToast(context, "No front-facing camera found.")
            null
        }
    }

    private fun showToast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun initSurfaceView(view: SurfaceViewRenderer) = view.run {
        setMirror(true)
        setEnableHardwareScaler(true)
        init(rootEglBase.eglBaseContext, null)
    }

    fun startLocalVideoCapture(localVideoOutput: SurfaceViewRenderer) {
        try {
            val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
            (videoCapturer as VideoCapturer).initialize(surfaceTextureHelper, localVideoOutput.context, localVideoSource.capturerObserver)
            videoCapturer!!.startCapture(320, 240, 60)
            val localVideoTrack = peerConnectionFactory.createVideoTrack(LOCAL_TRACK_ID, localVideoSource)
            localVideoTrack.addSink(localVideoOutput)
            val localSt = peerConnectionFactory.createLocalMediaStream(LOCAL_STREAM_ID)
            localSt.addTrack(localVideoTrack)
            val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
            val audioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource).apply {
                setEnabled(true)
                setVolume(100.0)
            }
            localSt.addTrack(audioTrack)
            peerConnection?.addStream(localSt)
            localStream=localSt
        }catch (ex:Exception){
            showToast(context, "Exception startLocalVideoCapture $ex")
        }

    }

    private fun PeerConnection.call(sdpObserver: SdpObserver) {
        createOffer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {

                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                    }

                    override fun onSetSuccess() {
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, desc)
                sdpObserver.onCreateSuccess(desc)
            }
        }, constraints)
    }

    private fun PeerConnection.answer(sdpObserver: SdpObserver) {
        createAnswer(object : SdpObserver by sdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {
                setLocalDescription(object : SdpObserver {
                    override fun onSetFailure(p0: String?) {
                    }

                    override fun onSetSuccess() {
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {
                    }

                    override fun onCreateFailure(p0: String?) {
                    }
                }, p0)
                sdpObserver.onCreateSuccess(p0)
            }
        }, constraints)
    }

    fun call(sdpObserver: SdpObserver) = peerConnection?.call(sdpObserver)

    fun answer(sdpObserver: SdpObserver) = peerConnection?.answer(sdpObserver)

    fun onRemoteSessionReceived(sessionDescription: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetFailure(p0: String?) {
            }

            override fun onSetSuccess() {
            }

            override fun onCreateSuccess(p0: SessionDescription?) {
            }

            override fun onCreateFailure(p0: String?) {
            }
        }, sessionDescription)
    }

    fun addIceCandidate(iceCandidate: IceCandidate?) {
        peerConnection?.addIceCandidate(iceCandidate)
    }
    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun startScreenCapture(resultCode: Int, data: Intent) {
        Log.d("ScreenCapture", "Starting screen capture")
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d("ScreenCapture", "Screen capture stopped by user")
                stopScreenCapture()
            }
        })

        val surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().name, rootEglBase.eglBaseContext)
        screenCapturer?.initialize(surfaceTextureHelper, context, screenVideoSource.capturerObserver)
        screenCapturer?.startCapture(320, 240, 60)

        screenTrack = peerConnectionFactory.createVideoTrack(SCREEN_TRACK_ID, screenVideoSource)
        screenTrack?.setEnabled(true)

        Log.d("ScreenCapture", "Replacing local video track with screen capture track")
        localStream?.removeTrack(localStream?.videoTracks?.firstOrNull())

        if (localStream != null && screenTrack != null) {
            localStream!!.addTrack(screenTrack)
        } else {
            Log.e("ScreenCapture", "Failed to add screenTrack to localStream. localStream: $localStream, screenTrack: $screenTrack")
        }
    }
    private fun stopScreenCapture() {
        try {
            screenCapturer?.stopCapture()
            screenCapturer = null
            screenTrack?.setEnabled(false)
            screenTrack = null
            mediaProjection?.stop()
            mediaProjection = null
            Log.d("ScreenCapture", "Screen capture stopped successfully")
        } catch (e: Exception) {
            Log.e("ScreenCapture", "Exception in stopping screen capture", e)
        }
    }

}