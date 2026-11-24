package com.tvelu77.crossfocus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.tvelu77.crossfocus.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val workerExecutor = Executors.newSingleThreadExecutor()
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var burstRunning = false
    private var lastLumaLog = 0L
    private var isFlashOn = false
    private var currentMode = MODE_SINGLE
    private var proEnabled = false
    private var proIso = 400
    private var proExposureNs = 10_000_000L
    private var proEv = 0
    private var proRebindRunnable: Runnable? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            toast(getString(R.string.camera_permission_denied))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.modeGroup.setOnCheckedChangeListener { _, checkedId ->
            currentMode = when (checkedId) {
                R.id.modeBurst -> MODE_BURST
                R.id.modeHdr -> MODE_HDR
                else -> MODE_SINGLE
            }
        }
        binding.shutterButton.setOnClickListener {
            when (currentMode) {
                MODE_BURST -> takeBurst(BURST_COUNT)
                MODE_HDR -> takeHdr()
                else -> takePhoto()
            }
        }
        binding.flashButton.setOnClickListener { toggleFlash() }
        binding.auditButton.setOnClickListener {
            startActivity(Intent(this, CameraAuditActivity::class.java))
        }
        binding.proButton.setOnClickListener { togglePro() }
        binding.previewView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                focusOnTap(event.x, event.y)
            }
            true
        }
        setupZoomSlider()
        setupProSliders()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshGallery()
    }

    override fun onDestroy() {
        super.onDestroy()
        workerExecutor.shutdown()
        analysisExecutor.shutdown()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindUseCases()
            if (camera?.cameraInfo?.hasFlashUnit() != true) {
                binding.flashButton.isEnabled = false
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(binding.previewView.surfaceProvider)
        }
        val captureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(90)
        if (proEnabled) {
            Camera2Interop.Extender(captureBuilder)
                .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, proIso)
                .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, proExposureNs)
        }
        imageCapture = captureBuilder.build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
        analysis.setAnalyzer(analysisExecutor) { image -> analyzeLuma(image) }
        provider.unbindAll()
        camera = provider.bindToLifecycle(
            this,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            imageCapture,
            analysis
        )
    }

    private fun toggleFlash() {
        val cam = camera ?: return
        if (cam.cameraInfo.hasFlashUnit()) {
            isFlashOn = !isFlashOn
            cam.cameraControl.enableTorch(isFlashOn)
            binding.flashButton.text = getString(if (isFlashOn) R.string.flash_on else R.string.flash_off)
        } else {
            toast(getString(R.string.flash_unavailable))
        }
    }

    private fun togglePro() {
        proEnabled = !proEnabled
        binding.proPanel.visibility = if (proEnabled) View.VISIBLE else View.GONE
        if (proEnabled) {
            applyEvComp()
        } else {
            camera?.cameraControl?.setExposureCompensationIndex(0)
        }
        bindUseCases()
    }

    private fun setupZoomSlider() {
        binding.zoomSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val zoom = 1.0f + progress / 100.0f
                binding.zoomValue.text = String.format(Locale.US, "%.1fx", zoom)
                camera?.cameraControl?.setZoomRatio(zoom)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun setupProSliders() {
        binding.isoSlider.progress = isoToProgress(proIso)
        binding.isoValue.text = getString(R.string.iso_label, proIso)
        binding.isoSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                proIso = 50 + (3200 - 50) * progress / 100
                binding.isoValue.text = getString(R.string.iso_label, proIso)
                scheduleProRebind()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        binding.shutterSlider.progress = shutterToProgress(proExposureNs)
        binding.shutterValue.text = shutterLabel(proExposureNs)
        binding.shutterSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                proExposureNs = shutterFromProgress(progress)
                binding.shutterValue.text = shutterLabel(proExposureNs)
                scheduleProRebind()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
        binding.evSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                proEv = progress - 12
                binding.evValue.text = getString(R.string.ev_label, proEv)
                applyEvComp()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun applyEvComp() {
        camera?.cameraControl?.setExposureCompensationIndex(proEv)
    }

    private fun scheduleProRebind() {
        proRebindRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { bindUseCases() }
        proRebindRunnable = runnable
        mainHandler.postDelayed(runnable, 300)
    }

    private fun isoToProgress(iso: Int): Int = ((iso - 50) * 100 / (3200 - 50)).coerceIn(0, 100)

    private fun shutterToProgress(ns: Long): Int {
        val ratio = ln(ns.toDouble() / MIN_EXPOSURE_NS) / ln(MAX_EXPOSURE_NS.toDouble() / MIN_EXPOSURE_NS)
        return (ratio * 100).roundToInt().coerceIn(0, 100)
    }

    private fun shutterFromProgress(progress: Int): Long {
        return (MIN_EXPOSURE_NS * (MAX_EXPOSURE_NS.toDouble() / MIN_EXPOSURE_NS).pow(progress / 100.0)).toLong()
    }

    private fun shutterLabel(ns: Long): String {
        val denominator = (1_000_000_000.0 / ns).roundToInt().coerceAtLeast(1)
        return getString(R.string.shutter_label, denominator)
    }

    private fun refreshGallery() {
        binding.galleryStrip.removeAllViews()
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return
        val files = dir.listFiles { f -> f.isFile && f.extension == "jpg" }
            ?.sortedByDescending { it.name }
            ?.take(20)
            ?: return
        val size = (72 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()
        for (file in files) {
            val imageView = ImageView(this)
            val params = LinearLayout.LayoutParams(size, size)
            params.setMargins(margin, 0, margin, 0)
            imageView.layoutParams = params
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setImageBitmap(decodeThumb(file))
            imageView.setOnClickListener {
                startActivity(
                    Intent(this, PhotoViewActivity::class.java)
                        .putExtra(PhotoViewActivity.EXTRA_PATH, file.absolutePath)
                )
            }
            binding.galleryStrip.addView(imageView)
        }
        binding.galleryScroll.post { binding.galleryScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun decodeThumb(file: File): android.graphics.Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
    }

    private fun focusOnTap(x: Float, y: Float) {
        val point = binding.previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point).build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    private fun analyzeLuma(image: ImageProxy) {
        try {
            val buffer = image.planes[0].buffer
            val size = buffer.remaining()
            var sum = 0L
            var count = 0
            var i = 0
            while (i < size) {
                sum += buffer.get(i).toInt() and 0xFF
                count++
                i += 64
            }
            val now = System.currentTimeMillis()
            if (now - lastLumaLog > 500) {
                lastLumaLog = now
                val mean = if (count > 0) sum / count else 0
                Log.d(TAG, "analysis ${image.width}x${image.height} meanLuma=$mean")
            }
        } finally {
            image.close()
        }
    }

    private fun takePhoto() {
        workerExecutor.execute {
            val file = newPhotoFile("single_")
            val ok = captureToFile(file)
            runOnUiThread {
                refreshGallery()
                toast(if (ok) getString(R.string.photo_saved, file.absolutePath) else getString(R.string.photo_error, "capture failed"))
            }
        }
    }

    private fun takeBurst(count: Int) {
        if (burstRunning) return
        burstRunning = true
        workerExecutor.execute {
            val t0 = System.nanoTime()
            var saved = 0
            for (i in 0 until count) {
                if (captureToFile(newPhotoFile("burst_${i}_"))) saved++
            }
            Log.d(TAG, "burstTotalMs=${(System.nanoTime() - t0) / 1_000_000} saved=$saved/$count")
            runOnUiThread {
                burstRunning = false
                refreshGallery()
                toast(getString(R.string.burst_done, saved, count))
            }
        }
    }

    private fun takeHdr() {
        val cam = camera ?: return
        workerExecutor.execute {
            val t0 = System.nanoTime()
            val baseIndex = cam.cameraInfo.exposureState.exposureCompensationIndex
            val frames = mutableListOf<Pair<File, Int>>()
            for (ev in HDR_EXPOSURES) {
                cam.cameraControl.setExposureCompensationIndex(ev)
                Thread.sleep(120)
                val f = newPhotoFile("hdr_ev${ev}_")
                if (captureToFile(f)) frames.add(f to ev)
            }
            cam.cameraControl.setExposureCompensationIndex(baseIndex)
            val merged = newPhotoFile("hdr_merged_")
            if (frames.size >= 2) {
                HdrMerger.fuseToFile(frames, merged)
            }
            Log.d(TAG, "hdrTotalMs=${(System.nanoTime() - t0) / 1_000_000} frames=${frames.size}")
            runOnUiThread {
                refreshGallery()
                toast(getString(R.string.hdr_done, merged.absolutePath))
            }
        }
    }

    private fun captureToFile(file: File): Boolean {
        val capture = imageCapture ?: return false
        val future = CompletableFuture<Boolean>()
        val start = System.nanoTime()
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    future.complete(true)
                    Log.d(TAG, "captureLatencyMs=${(System.nanoTime() - start) / 1_000_000} file=${file.name}")
                }

                override fun onError(exception: ImageCaptureException) {
                    future.complete(false)
                    Log.d(TAG, "captureError file=${file.name} err=${exception.message}")
                }
            }
        )
        return try {
            future.get(15, TimeUnit.SECONDS)
        } catch (e: Exception) {
            false
        }
    }

    private fun newPhotoFile(prefix: String): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        return File(dir, prefix + photoFileName())
    }

    private fun photoFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
        return "crossfocus_$timestamp.jpg"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "CrossFocus"
        private const val BURST_COUNT = 5
        private const val MODE_SINGLE = 0
        private const val MODE_BURST = 1
        private const val MODE_HDR = 2
        private const val MIN_EXPOSURE_NS = 96_600L
        private const val MAX_EXPOSURE_NS = 342_589_440L
        private val HDR_EXPOSURES = intArrayOf(-12, 0, 12)
    }
}
