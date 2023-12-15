package kr.ac.kaist.nclab.liv

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29394C), contentColor = Color(0xFFFFFFFF))
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
    val commonTextStyle = TextStyle(
        fontSize = 24.sp,
        lineHeight = 31.09.sp,
        fontFamily = FontFamily(Font(R.font.roboto)),
        fontWeight = FontWeight(300),
        color = Color.White,
        textAlign = TextAlign.Center
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initDebug()) {
            // Handle initialization error
        }

        setContent {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF16283C))
            )

            val navCont = rememberNavController()

            val context = LocalContext.current
            // The most recently successfully taken photo
            var recentPhotoUri by remember { mutableStateOf(value = Uri.EMPTY) }
            // Temporary URL holder for ActivityResultContract
            var tempPhotoUri by remember { mutableStateOf(value = Uri.EMPTY) }
            var thh by remember { mutableDoubleStateOf(value = 70.0) }
            var photoResult by remember { mutableStateOf(value = "") }
            var resultH by remember { mutableIntStateOf(value = 0) }

            val cameraLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.TakePicture(), onResult = { success ->
                Log.d("TTT", "success?: $success, uri: $tempPhotoUri")
                if (success) {
                    recentPhotoUri = tempPhotoUri
                }
                val bitmapImg: Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, recentPhotoUri)) { decoder, _, _ -> decoder.isMutableRequired = true }
                val mat = Mat()
                Utils.bitmapToMat(bitmapImg, mat)


                // 이진화
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_RGB2GRAY)
                Imgproc.GaussianBlur(mat, mat, Size(5.0, 5.0), 0.0)
                Imgproc.threshold(mat, mat, 70.0, 255.0, Imgproc.THRESH_BINARY_INV)

                // 윤곽선 검출
                val contours: List<MatOfPoint> = ArrayList()
                val hierarchy = Mat()
                Imgproc.findContours(mat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

                //마킹
                Imgproc.cvtColor(mat, mat, Imgproc.COLOR_GRAY2RGB)

                val oriMat = Mat()
                Utils.bitmapToMat(bitmapImg, oriMat)

                val goodContours: MutableList<MatOfPoint> = ArrayList()
                for (i in contours.indices) {
                    val contour = contours[i]
                    val size = Imgproc.contourArea(contour)
                    if (size > 3000) {
                        goodContours.add(contour)
                    }
                }

                if (goodContours.size != 4) {
                    photoResult = ""
                    navCont.navigate("fail") {
                        popUpTo(navCont.graph.findStartDestination().id) {
                            saveState = true
                        }
                        this.launchSingleTop = true
                    }
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
                    val matHSV = Mat()
                    Imgproc.cvtColor(oriMat, matHSV, Imgproc.COLOR_RGB2HSV)
                    resultH = (matHSV[cy, cx][0] * 2).toInt()
                    photoResult = "OK! HUE: $resultH"
                    val toGo:String
                    if (resultH > 20) {
                        toGo = "doneGood"
                    }
                    else {
                        toGo = "doneBad"
                    }
                    navCont.navigate(toGo) {
                        popUpTo(navCont.graph.findStartDestination().id) {
                            saveState = true
                        }
                        this.launchSingleTop = true
                    }
                }
            })

            navCont.addOnDestinationChangedListener { _, dest, _ ->
                if (dest.route == "fail") {
                    Handler(Looper.getMainLooper()).postDelayed({
                        tempPhotoUri = context.createTempPictureUri()
                        cameraLauncher.launch(tempPhotoUri)
                    }, 1000)
                }
            }
            NavHost(navController = navCont, startDestination = "hi") {
                composable("hi") {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 28.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.weight(1.0f))
                        Text(
                            text = "Do you want to register a band test result?",
                            style = TextStyle(
                                fontSize = 26.sp,
                                lineHeight = 31.09.sp,
                                fontFamily = FontFamily(Font(R.font.roboto)),
                                fontWeight = FontWeight(300),
                                color = Color.White,
                                textAlign = TextAlign.Center
                            ),
                            modifier = Modifier.wrapContentHeight()
                        )
                        Spacer(modifier = Modifier.weight(1.0f))
                        normalButton(pv = PaddingValues(bottom = 16.dp), txt = "Yes") {
                            tempPhotoUri = context.createTempPictureUri()
                            cameraLauncher.launch(tempPhotoUri)
                        }
                        Slider(value = thh.toFloat(), onValueChange = {
                            thh = it.toDouble()
                        }, valueRange = 0f..255f, steps = 0)
                    }
                }
                composable("fail") {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 28.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.weight(1.0f))
                        Text(
                            text = "Please retry",
                            style = commonTextStyle,
                            modifier = Modifier.wrapContentHeight()
                        )
                        Spacer(modifier = Modifier.weight(1.0f))
                        Text(
                            text = photoResult,
                            style = commonTextStyle,
                            modifier = Modifier.wrapContentHeight()
                        )
                        Spacer(modifier = Modifier.weight(0.1f))
                    }
                }
                composable("doneGood") {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            modifier = Modifier
                                .fillMaxWidth(),
                            painter = painterResource(id = R.drawable.normal),
                            contentDescription = null,
                        )
                        Button(
                            onClick = {
                                navCont.navigate("hi") {
                                    popUpTo(navCont.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    this.launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 28.dp)
                                .height(55.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(size = 20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29394C), contentColor = Color(0xFFFFFFFF))
                        ) {
                            Text(
                                text = "Close",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight(300),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
                composable("doneBad") {
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            modifier = Modifier
                                .fillMaxWidth(),
                            painter = painterResource(id = R.drawable.abnormal),
                            contentDescription = null,
                        )
                        Button(
                            onClick = {
                                navCont.navigate("hi") {
                                    popUpTo(navCont.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    this.launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = 28.dp)
                                .height(55.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(size = 20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF29394C), contentColor = Color(0xFFFFFFFF))
                        ) {
                            Text(
                                text = "Close",
                                style = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight(300),
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}