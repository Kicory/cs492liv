package kr.ac.kaist.nclab.liv

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.ac.kaist.nclab.liv.analyzer.createTempPictureUri
import kr.ac.kaist.nclab.liv.ui.theme.LivTheme
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc


class MainActivity : ComponentActivity() {

    @Composable
    fun normalButton(pv: PaddingValues, txt: String, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .padding(pv)
                .height(55.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(size = 20.dp),
        ) {
            Text(
                text = txt,
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight(300),
                    textAlign = TextAlign.Center
                )
            )
        }
    }
    @Composable
    fun BitmapImage(bitmap: Bitmap) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "some useful description",
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }

        setContent {
            LivTheme {
                // A surface container using the 'background' color from the theme
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Greeting("Android")
                }
            }

            val context = LocalContext.current
            // The most recently successfully taken photo
            var recentPhotoUri by remember { mutableStateOf(value = Uri.EMPTY) }
            // Temporary URL holder for ActivityResultContract
            var tempPhotoUri by remember { mutableStateOf(value = Uri.EMPTY) }
            var thh by remember { mutableDoubleStateOf(value = 70.0) }
            var photoResult by remember {mutableStateOf(value = "")}
            var coreColorHSV by remember { mutableStateOf(value = DoubleArray(3)) }
            var coreColorAmp by remember { mutableIntStateOf(value = 0) }
            var coreColorOri by remember { mutableStateOf(value = DoubleArray(3))}

            val cameraLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture(), onResult = { success ->
                Log.d("TTT", "success?: $success, uri: $tempPhotoUri")
                if (success) {
                    recentPhotoUri = tempPhotoUri
                }
                val bitmapImg: Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, recentPhotoUri)) { decoder, _, _ -> decoder.isMutableRequired = true }
                val mat = Mat()
                Utils.bitmapToMat(bitmapImg, mat)
                Log.d("dd", "1")


                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
                // 이진화
                Log.d("dd", "2")
                Imgproc.GaussianBlur(mat, mat, Size(5.0, 5.0), 0.0)
                Imgproc.threshold(mat, mat, 70.0, 255.0, Imgproc.THRESH_BINARY_INV)
                //Imgproc.adaptiveThreshold(mat, mat, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 12.0)
                //Imgproc.Canny(mat, mat, lowerT, highT)

//                // 이진화
//                Imgproc.threshold(mat, mat, 240.0, 255.0, Imgproc.THRESH_BINARY_INV)
//                Log.d("dd", "3")

                // 윤곽선 검출
                val contours: List<MatOfPoint> = ArrayList()
                val hierarchy = Mat()
                Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
                Log.d("dd", "4")

                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_GRAY2RGB)

                val oriMat = Mat()
                Utils.bitmapToMat(bitmapImg, oriMat)

                val goodContours: MutableList<MatOfPoint> = ArrayList()
                for (i in contours.indices) {
                    val contour = contours[i]
                    val size = Imgproc.contourArea(contours[i])
                    if (size > 3000) {
                        Log.d("cnt", "$size")
                        Imgproc.drawContours(mat, contours, i, Scalar(0.0, 255.0, 0.0), -1)
                        goodContours.add(contour)

                        val m = Imgproc.moments(contour)
                        val cx = (m._m10 / m._m00)
                        val cy = (m._m01 / m._m00)
                        Imgproc.drawMarker(oriMat, Point(cx, cy), Scalar(255.0, 0.0, 0.0), Imgproc.MARKER_SQUARE, 5)

                    }
                    else {
                        //Imgproc.drawContours(mat, contours, i, Scalar(255.0, 0.0, 0.0), -1)
                    }
                }

                if (goodContours.size != 4) {
                    photoResult = "Failed!! Please re-take the photo."
                    return@rememberLauncherForActivityResult
                }
                else {
                    var cx = 0
                    var cy = 0
                    for (i in goodContours.indices) {
                        val contour = goodContours[i]
                        val m = Imgproc.moments(contour)
                        cx += ((m._m10 / m._m00) / goodContours.size).toInt()
                        cy += ((m._m01 / m._m00) / goodContours.size).toInt()
                    }
                    val HSVmat = Mat()
                    Imgproc.cvtColor(oriMat, HSVmat, Imgproc.COLOR_RGB2HSV)
                    coreColorHSV = HSVmat[cy, cx]
                    photoResult = "OK! HUE: ${coreColorHSV[0].toInt() * 2}"
                    coreColorAmp = android.graphics.Color.HSVToColor(floatArrayOf(coreColorHSV[0].toFloat() * 2, coreColorHSV[1].toFloat(), coreColorHSV[2].toFloat()))
                    coreColorOri = oriMat[cy, cx]

                    Imgproc.drawMarker(oriMat, Point(cx.toDouble(), cy.toDouble()), Scalar(0.0, 0.0, 255.0), Imgproc.MARKER_STAR, 10)
                }
                val bitmapRawResult: Bitmap = Bitmap.createBitmap(bitmapImg)
                val bitmapResult: Bitmap = Bitmap.createBitmap(bitmapImg)
                Utils.matToBitmap(oriMat, bitmapResult)
                Utils.matToBitmap(mat, bitmapRawResult)
                Log.d("fdsa", "END")
            })
            Column {
                normalButton(pv = PaddingValues(bottom = 16.dp), txt = "Yes") {
                    tempPhotoUri = context.createTempPictureUri()
                    cameraLauncher.launch(tempPhotoUri)
                }

                Slider(value = thh.toFloat(), onValueChange = {
                    thh = it.toDouble()
                }, valueRange = 0f..255f, steps = 0)
                Text(text = photoResult)
                Box(modifier = Modifier
                    .background(color = Color(coreColorAmp))
                    .height(160.dp)
                    .fillMaxWidth())
                Box(modifier = Modifier
                    .background(color = Color(coreColorOri[0].toInt(), coreColorOri[1].toInt(), coreColorOri[2].toInt()))
                    .height(160.dp)
                    .fillMaxWidth())
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LivTheme {
        Greeting("Android")
    }
}