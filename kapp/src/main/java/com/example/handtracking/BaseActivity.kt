// Copyright 2019 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under
package com.example.handtracking

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.components.CameraHelper.CameraFacing
import com.google.mediapipe.components.CameraXPreviewHelper
import com.google.mediapipe.components.ExternalTextureConverter
import com.google.mediapipe.components.FrameProcessor
import com.google.mediapipe.components.PermissionHelper
import com.google.mediapipe.framework.AndroidAssetUtil
import com.google.mediapipe.glutil.EglManager

/** Main activity of MediaPipe basic app.  */
open class BaseActivity : AppCompatActivity() {
    // Sends camera-preview frames into a MediaPipe graph for processing, and displays the processed
    // frames onto a {@link Surface}.
    @JvmField
    protected var processor: FrameProcessor? = null

    // Handles camera access via the {@link CameraX} Jetpack support library.
    protected var cameraHelper: CameraXPreviewHelper? = null

    // {@link SurfaceTexture} where the camera-preview frames can be accessed.
    private var previewFrameTexture: SurfaceTexture? = null

    // {@link SurfaceView} that displays the camera-preview frames processed by a MediaPipe graph.
    private var previewDisplayView: SurfaceView? = null

    // Creates and manages an {@link EGLContext}.
    private var eglManager: EglManager? = null

    // Converts the GL_TEXTURE_EXTERNAL_OES texture from Android camera into a regular texture to be
    // consumed by {@link FrameProcessor} and the underlying MediaPipe graph.
    private var converter: ExternalTextureConverter? = null

    // ApplicationInfo for retrieving metadata defined in the manifest.
    private var applicationInfo: ApplicationInfo? = null
    private val cameraFacingFront = false
    private val binaryGraphName = "hand_tracking_mobile_gpu.binarypb"
    private val inputVideoStreamName = "input_video"
    private val outputVideoStreamName = "output_video"
    private val flipFramesVertically = true
    private val converterNumBuffers = 2
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(contentViewLayoutResId)
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Cannot find application info: $e")
        }
        previewDisplayView = SurfaceView(this)
        setupPreviewDisplayView()

        // Initialize asset manager so that MediaPipe native libraries can access the app assets, e.g.,
        // binary graphs.
        AndroidAssetUtil.initializeNativeAssetManager(this)
        eglManager = EglManager(null)
        processor = FrameProcessor(
                this,
                eglManager!!.nativeContext,
                binaryGraphName,
                inputVideoStreamName,
                outputVideoStreamName)
        processor!!
                .videoSurfaceOutput
                .setFlipY(flipFramesVertically)
        PermissionHelper.checkAndRequestCameraPermissions(this)
    }

    // Used to obtain the content view for this application. If you are extending this class, and
    // have a custom layout, override this method and return the custom layout.
    protected val contentViewLayoutResId: Int
        protected get() = R.layout.activity_main

    override fun onResume() {
        super.onResume()
        converter = ExternalTextureConverter(
                eglManager!!.context,
                NUM_BUFFERS)
        converter!!.setFlipY(FLIP_FRAMES_VERTICALLY)
        converter!!.setConsumer(processor)
        if (PermissionHelper.cameraPermissionsGranted(this)) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        converter!!.close()

        // Hide preview display until we re-open the camera again.
        previewDisplayView!!.visibility = View.GONE
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        PermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    protected fun onCameraStarted(surfaceTexture: SurfaceTexture?) {
        previewFrameTexture = surfaceTexture
        // Make the display view visible to start showing the preview. This triggers the
        // SurfaceHolder.Callback added to (the holder of) previewDisplayView.
        previewDisplayView!!.visibility = View.VISIBLE
    }

    protected fun cameraTargetResolution(): Size? {
        return null // No preference and let the camera (helper) decide.
    }

    fun startCamera() {
        cameraHelper = CameraXPreviewHelper()
        previewFrameTexture = converter!!.surfaceTexture
        cameraHelper!!.setOnCameraStartedListener { surfaceTexture: SurfaceTexture? -> onCameraStarted(surfaceTexture) }
        val cameraFacing = if (cameraFacingFront) CameraFacing.FRONT else CameraFacing.BACK
        cameraHelper!!.startCamera(
                this, cameraFacing, previewFrameTexture, cameraTargetResolution())
    }

    protected fun computeViewSize(width: Int, height: Int): Size {
        return Size(width, height)
    }

    protected fun onPreviewDisplaySurfaceChanged(
            holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        // (Re-)Compute the ideal size of the camera-preview display (the area that the
        // camera-preview frames get rendered onto, potentially with scaling and rotation)
        // based on the size of the SurfaceView that contains the display.
        val viewSize = computeViewSize(width, height)
        val displaySize = cameraHelper!!.computeDisplaySizeFromViewSize(viewSize)
        val isCameraRotated = cameraHelper!!.isCameraRotated

        // Configure the output width and height as the computed display size.
        converter!!.setDestinationSize(
                if (isCameraRotated) displaySize.height else displaySize.width,
                if (isCameraRotated) displaySize.width else displaySize.height)
    }

    private fun setupPreviewDisplayView() {
        previewDisplayView!!.visibility = View.GONE
        val viewGroup = findViewById<ViewGroup>(R.id.preview_display_layout)
        viewGroup.addView(previewDisplayView)
        previewDisplayView
                .getHolder()
                .addCallback(
                        object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                processor!!.videoSurfaceOutput.setSurface(holder.surface)
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                onPreviewDisplaySurfaceChanged(holder, format, width, height)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                processor!!.videoSurfaceOutput.setSurface(null)
                            }
                        })
    }

    companion object {
        private const val TAG = "MainActivity"

        // Flips the camera-preview frames vertically by default, before sending them into FrameProcessor
        // to be processed in a MediaPipe graph, and flips the processed frames back when they are
        // displayed. This maybe needed because OpenGL represents images assuming the image origin is at
        // the bottom-left corner, whereas MediaPipe in general assumes the image origin is at the
        // top-left corner.
        // NOTE: use "flipFramesVertically" in manifest metadata to override this behavior.
        private const val FLIP_FRAMES_VERTICALLY = true

        // Number of output frames allocated in ExternalTextureConverter.
        // NOTE: use "converterNumBuffers" in manifest metadata to override number of buffers. For
        // example, when there is a FlowLimiterCalculator in the graph, number of buffers should be at
        // least `max_in_flight + max_in_queue + 1` (where max_in_flight and max_in_queue are used in
        // FlowLimiterCalculator options). That's because we need buffers for all the frames that are in
        // flight/queue plus one for the next frame from the camera.
        private const val NUM_BUFFERS = 2

        init {
            // Load all native libraries needed by the app.
            System.loadLibrary("mediapipe_jni")
            try {
                System.loadLibrary("opencv_java3")
            } catch (e: UnsatisfiedLinkError) {
                // Some example apps (e.g. template matching) require OpenCV 4.
                System.loadLibrary("opencv_java4")
            }
        }
    }
}