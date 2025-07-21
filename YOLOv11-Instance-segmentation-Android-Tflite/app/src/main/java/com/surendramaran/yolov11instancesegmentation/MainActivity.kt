<<<<<<< HEAD
=======

>>>>>>> d9f151705c538cc92b5fbb13527618299000db84
package com.surendramaran.yolov11instancesegmentation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.widget.Toast
<<<<<<< HEAD
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
=======
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
>>>>>>> d9f151705c538cc92b5fbb13527618299000db84
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
<<<<<<< HEAD
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.surendramaran.yolov11instancesegmentation.ui.theme.YOLOv11InstanceSegmentationTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), InstanceSegmentation.InstanceSegmentationListener {
    private lateinit var instanceSegmentation: InstanceSegmentation
    private lateinit var drawImages: DrawImages

    // Compose state variables
    private var preprocessTime by mutableStateOf("0")
    private var inferenceTime by mutableStateOf("0")
    private var postprocessTime by mutableStateOf("0")
    private var overlayBitmap by mutableStateOf<Bitmap?>(null)
    private var permissionGranted by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            permissionGranted = true
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
=======
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.surendramaran.yolov11instancesegmentation.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), InstanceSegmentation.InstanceSegmentationListener {
    private lateinit var binding: ActivityMainBinding

    private lateinit var instanceSegmentation: InstanceSegmentation

    private lateinit var drawImages: DrawImages


    private lateinit var previewView: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        previewView = binding.previewView

        checkPermission()
>>>>>>> d9f151705c538cc92b5fbb13527618299000db84

        drawImages = DrawImages(applicationContext)

        instanceSegmentation = InstanceSegmentation(
            context = applicationContext,
            modelPath = "yolo11n-seg_float16.tflite",
            labelPath = null,
            instanceSegmentationListener = this,
            message = {
                Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
            },
        )
<<<<<<< HEAD

        checkPermission()

        setContent {
            YOLOv11InstanceSegmentationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (permissionGranted) {
                        CameraScreen()
                    } else {
                        PermissionScreen()
                    }
                }
            }
        }
    }

    @Composable
    private fun CameraScreen() {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        Box(modifier = Modifier.fillMaxSize()) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                 PreviewView(ctx).apply {
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val aspectRatio = AspectRatio.RATIO_4_3

                            val preview = Preview.Builder()
                                .setTargetAspectRatio(aspectRatio)
                                .build()
                                .also {
                                    it.surfaceProvider = this.surfaceProvider
                                }

                            val imageAnalyzer = ImageAnalysis.Builder()
                                .setTargetAspectRatio(aspectRatio)
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                .build()
                                .also {
                                    it.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalyzer())
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner, cameraSelector, preview, imageAnalyzer
                                )
                            } catch (exc: Exception) {
                                Log.e("CameraX", "Use case binding failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(3f / 4f)
            )

            // Overlay Image
            overlayBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Segmentation Overlay",
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(3f / 4f)
                )
            }

            // Performance Stats
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Speed (in milliseconds)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PerformanceItem("Preprocess:", preprocessTime)
                        PerformanceItem("Inference:", inferenceTime)
                        PerformanceItem("Postprocess:", postprocessTime)
                    }
                }
            }
        }
    }

    @Composable
    private fun PerformanceItem(label: String, value: String) {
        Column {
            Text(
                text = label,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color.White
            )
            Text(
                text = value,
                fontSize = 12.sp,
                color = Color.Green
            )
        }
    }

    @Composable
    private fun PermissionScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "This app needs camera access to perform instance segmentation",
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { checkPermission() }
            ) {
                Text("Grant Permission")
            }
        }
    }

    inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
=======
    }


    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Set aspect ratio to 3:4 (4:3 in CameraX terms)
            val aspectRatio = AspectRatio.RATIO_4_3

            // Preview Use Case
            val preview = Preview.Builder()
                .setTargetAspectRatio(aspectRatio)
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

            // Image Analysis Use Case
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(aspectRatio) // Set aspect ratio
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor(), ImageAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }


    inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        override fun analyze(imageProxy: ImageProxy) {

            val bitmapBuffer =
                Bitmap.createBitmap(
                    imageProxy.width,
                    imageProxy.height,
                    Bitmap.Config.ARGB_8888
                )
>>>>>>> d9f151705c538cc92b5fbb13527618299000db84
            imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
            imageProxy.close()

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )
            instanceSegmentation.invoke(rotatedBitmap)
        }
    }

<<<<<<< HEAD
    private fun checkPermission() {
        val isGranted = REQUIRED_PERMISSIONS.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (isGranted) {
            permissionGranted = true
=======

    private fun checkPermission() = lifecycleScope.launch(Dispatchers.IO) {
        val isGranted = REQUIRED_PERMISSIONS.all {
            ActivityCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
        if (isGranted) {
            startCamera()
>>>>>>> d9f151705c538cc92b5fbb13527618299000db84
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

<<<<<<< HEAD
    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
            overlayBitmap = null
=======

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { map ->
            if(map.all { it.value }) {
                startCamera()
            } else {
                Toast.makeText(baseContext, "Permission required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, error, Toast.LENGTH_SHORT).show()
            binding.ivTop.setImageResource(0)
>>>>>>> d9f151705c538cc92b5fbb13527618299000db84
        }
    }

    override fun onDetect(
        interfaceTime: Long,
        results: List<SegmentationResult>,
        preProcessTime: Long,
        postProcessTime: Long
    ) {
        val image = drawImages.invoke(results)
        runOnUiThread {
<<<<<<< HEAD
            preprocessTime = preProcessTime.toString()
            inferenceTime = interfaceTime.toString()
            this.postprocessTime = postProcessTime.toString()
            overlayBitmap = image
=======
            binding.tvPreprocess.text = preProcessTime.toString()
            binding.tvInference.text = interfaceTime.toString()
            binding.tvPostprocess.text = postProcessTime.toString()
            binding.ivTop.setImageBitmap(image)
>>>>>>> d9f151705c538cc92b5fbb13527618299000db84
        }
    }

    override fun onEmpty() {
        runOnUiThread {
<<<<<<< HEAD
            overlayBitmap = null
=======
            binding.ivTop.setImageResource(0)
>>>>>>> d9f151705c538cc92b5fbb13527618299000db84
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instanceSegmentation.close()
    }

    companion object {
<<<<<<< HEAD
        val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
=======
        val REQUIRED_PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA
        ).toTypedArray()
    }
}
>>>>>>> d9f151705c538cc92b5fbb13527618299000db84
