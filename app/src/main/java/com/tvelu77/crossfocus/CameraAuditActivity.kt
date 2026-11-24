package com.tvelu77.crossfocus

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.tvelu77.crossfocus.databinding.ActivityAuditBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class CameraAuditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuditBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val report = buildReport()
        binding.auditText.text = report
        binding.exportButton.setOnClickListener {
            val file = File(getExternalFilesDir(null), reportFileName())
            file.writeText(report)
            Toast.makeText(this, getString(R.string.audit_exported, file.absolutePath), Toast.LENGTH_LONG).show()
        }
    }

    private fun buildReport(): String {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        val sb = StringBuilder()
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(System.currentTimeMillis())
        sb.appendLine("CrossFocus - Camera2 audit")
        sb.appendLine("generated: $timestamp")
        sb.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
        sb.appendLine()
        for (cameraId in cameraManager.cameraIdList) {
            val c = cameraManager.getCameraCharacteristics(cameraId)
            sb.appendLine("=== camera $cameraId ===")
            sb.appendLine("facing: ${facingName(c.get(CameraCharacteristics.LENS_FACING))}")
            sb.appendLine("hardware level: ${hardwareLevelName(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))}")
            val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: IntArray(0)
            sb.appendLine("capabilities: ${caps.joinToString { capabilityName(it) }}")
            sb.appendLine("max digital zoom: ${c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)}")
            sb.appendLine("active array: ${c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)}")
            sb.appendLine("pixel array: ${c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)}")
            sb.appendLine("physical size: ${c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)}")
            sb.appendLine("focal lengths (mm): ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString()}")
            sb.appendLine("min focus distance (diopters): ${c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)}")
            sb.appendLine("apertures: ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.joinToString()}")
            sb.appendLine("ISO range: ${c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)}")
            sb.appendLine("max analog ISO: ${c.get(CameraCharacteristics.SENSOR_MAX_ANALOG_SENSITIVITY)}")
            sb.appendLine("exposure time range (ns): ${c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)}")
            sb.appendLine("AE compensation range: ${c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)}")
            sb.appendLine("target FPS ranges: ${c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.joinToString { it.toString() }}")
            sb.appendLine("max regions AE/AF/AWB: ${c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE)}/${c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF)}/${c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AWB)}")
            sb.appendLine("flash available: ${c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)}")
            sb.appendLine("max faces: ${c.get(CameraCharacteristics.STATISTICS_INFO_MAX_FACE_COUNT)}")
            val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map != null) {
                val jpegSizes = map.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
                sb.appendLine("JPEG sizes (${jpegSizes.size}): ${jpegSizes.sortedWith(compareByDescending<Size> { it.width.toLong() * it.height }).take(8).joinToString { "${it.width}x${it.height}" }}")
                val yuvSizes = map.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray()
                sb.appendLine("YUV_420_888 sizes (${yuvSizes.size}): max=${yuvSizes.maxByOrNull { it.width.toLong() * it.height }}")
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun reportFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        return "crossfocus-camera-audit_$timestamp.txt"
    }

    private fun facingName(facing: Int?): String = when (facing) {
        CameraCharacteristics.LENS_FACING_FRONT -> "front"
        CameraCharacteristics.LENS_FACING_BACK -> "back"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "external"
        else -> "unknown($facing)"
    }

    private fun hardwareLevelName(level: Int?): String = when (level) {
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        else -> "unknown($level)"
    }

    private fun capabilityName(cap: Int): String = when (cap) {
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BACKWARD_COMPATIBLE"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST_PROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING -> "PRIVATE_REPROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR_SETTINGS"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST_CAPTURE"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING -> "YUV_REPROCESSING"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT -> "DEPTH_OUTPUT"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO -> "CONSTRAINED_HIGH_SPEED_VIDEO"
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BLACK_LEVEL_LOCK -> "BLACK_LEVEL_LOCK"
        else -> "cap_$cap"
    }
}
