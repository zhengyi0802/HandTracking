package com.example.handtracking

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import com.google.mediapipe.formats.proto.LandmarkProto.NormalizedLandmarkList
import com.google.mediapipe.framework.Packet
import com.google.mediapipe.framework.PacketGetter

/** Main activity of MediaPipe hand tracking app.  */
class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val applicationInfo: ApplicationInfo
        applicationInfo = try {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        } catch (e: PackageManager.NameNotFoundException) {
            throw AssertionError(e)
        }
        val packetCreator = processor!!.packetCreator
        val inputSidePackets: MutableMap<String, Packet> = HashMap()
        inputSidePackets[INPUT_NUM_HANDS_SIDE_PACKET_NAME] = packetCreator.createInt32(NUM_HANDS)
        if (applicationInfo.metaData.containsKey("modelComplexity")) {
            inputSidePackets[INPUT_MODEL_COMPLEXITY] = packetCreator.createInt32(applicationInfo.metaData.getInt("modelComplexity"))
        }
        processor!!.setInputSidePackets(inputSidePackets)

        // To show verbose logging, run:
        // adb shell setprop log.tag.MainActivity VERBOSE
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            processor!!.addPacketCallback(
                    OUTPUT_LANDMARKS_STREAM_NAME
            ) { packet: Packet ->
                Log.v(TAG, "Received multi-hand landmarks packet.")
                val multiHandLandmarks = PacketGetter.getProtoVector(packet, NormalizedLandmarkList.parser())
                Log.v(
                        TAG,
                        "[TS:"
                                + packet.timestamp
                                + "] "
                                + getMultiHandLandmarksDebugString(multiHandLandmarks))
            }
        }
    }

    private fun getMultiHandLandmarksDebugString(multiHandLandmarks: List<NormalizedLandmarkList>): String {
        if (multiHandLandmarks.isEmpty()) {
            return "No hand landmarks"
        }
        var multiHandLandmarksStr = """
            Number of hands detected: ${multiHandLandmarks.size}
            
            """.trimIndent()
        var handIndex = 0
        for (landmarks in multiHandLandmarks) {
            multiHandLandmarksStr += """	#Hand landmarks for hand[$handIndex]: ${landmarks.landmarkCount}
"""
            var landmarkIndex = 0
            for (landmark in landmarks.landmarkList) {
                multiHandLandmarksStr += """		Landmark [$landmarkIndex]: (${landmark.x}, ${landmark.y}, ${landmark.z})
"""
                ++landmarkIndex
            }
            ++handIndex
        }
        return multiHandLandmarksStr
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val INPUT_NUM_HANDS_SIDE_PACKET_NAME = "num_hands"
        private const val INPUT_MODEL_COMPLEXITY = "model_complexity"
        private const val OUTPUT_LANDMARKS_STREAM_NAME = "hand_landmarks"

        // Max number of hands to detect/process.
        private const val NUM_HANDS = 2
    }
}