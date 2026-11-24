package tw.edu.pu.csim.yywen.digitalink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tw.edu.pu.csim.yywen.digitalink.ui.theme.DigitalInkTheme

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DigitalInkTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Doodle(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}


@Composable
fun Doodle( modifier: Modifier = Modifier) {
    var recognizedText by remember {
        mutableStateOf("請在畫布區手寫，再按「開始辨識」按鈕")
    }

    // 建立一個路徑物件
    var path by remember {
        /*
        val p = Path()
        p.moveTo(100f, 100f)  // 移動到起始點 (50f, 100f)
        p.lineTo(100f, 500f) // 畫一條線到 (200f, 500f)
        p.lineTo(400f, 500f) // 畫一條線到 (450f, 400f)
        p.close()                    // 閉合路徑
        mutableStateOf(p)    // 最後一行回傳包有 p 的 MutableState
        */
        mutableStateOf(
            Path().apply{
            moveTo(100f, 100f)  // 移動到起始點 (50f, 100f)
            lineTo(100f, 500f) // 畫一條線到 (200f, 500f)
            lineTo(400f, 500f)
            lineTo(400f, 600f)
            close()
          }
        )
    }

    // ML Kit 墨水與筆劃收集器 ----------
    // inkBuilder 負責收集「整頁的全部筆劃」（把多個單一筆劃組合在一起）。
    // strokeBuilder 負責記錄「單一筆劃」（手指從按下到抬起，抬起後就歸零）。

    var inkBuilder by remember {
        mutableStateOf(Ink.builder())
    }

    var strokeBuilder by remember {
        mutableStateOf<Ink.Stroke.Builder?>(null)
    }


// 記憶 ML Kit 模型與 Recognizer 實體 (繁體中文 zh-Hant) ----------

    // 取得語言辨識碼
    val modelIdentifier = remember {
        try {
            DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hant")
        } catch (e: Exception) {
            null
        }
    }

    // 建立語言模型物件
    val model = remember(modelIdentifier) {
        modelIdentifier?.let { DigitalInkRecognitionModel.builder(it).build() }
    }

    // 建立手寫辨識引擎
    val recognizer = remember(model) {
        model?.let { DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(it).build()) }
    }



    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier.size(300.dp)
                .border(2.dp, Color.Gray)
                .pointerInput(Unit) {
                    // 監聽拖曳手勢
                    detectDragGestures(
                        onDragStart = { offset -> /* 手指按下 */
                            //recognizedText="手指按下"
                            // 按下：記錄 Path 起點
                            val x = offset.x
                            val y = offset.y
                            val t = System.currentTimeMillis()  // 時間戳記

                             path.moveTo(x, y)

                            //  ML Kit Ink.Stroke.Builder 紀錄起點
                            strokeBuilder = Ink.Stroke.builder()
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))


                        },
                        onDrag = { change, dragAmount -> /* 手指移動 */
                            //recognizedText="手指拖曳"
                            // 消費拖曳事件（避免影響父層元件手勢）
                            // 這個移動手勢已經被 Canvas 處理，請不要再傳給外層的其他元件
                            change.consume()

                            // 移動：拉線並加入點座標
                            val x = change.position.x
                            val y = change.position.y
                            val t = System.currentTimeMillis()
                            path.lineTo(x, y)

                            //複製出一個內容一模一樣、但記憶體位址全新的 Path 物件
                            // 並指派回 path，藉此強制 Compose 重繪 Canvas 畫面
                            val tempPath = Path()
                            tempPath.addPath(path)
                            path = tempPath

                            // ML Kit 紀錄移動點座標
                            strokeBuilder?.addPoint(Ink.Point.create(x, y, t))


                        },
                        onDragEnd = { /* 手指離開 */
                            //recognizedText="手指彈開"
                            // 手指離開：將打包好的 Stroke 加入 inkBuilder
                            strokeBuilder?.build()?.let { stroke ->
                                inkBuilder.addStroke(stroke)
                            }

                            // 重置當前 Stroke Builder 準備下一筆劃
                            strokeBuilder = null

                        }
                    )
                }



        ) {
            // 繪製黑色路徑，透明度1 (不透明)，線條粗細10
            drawPath(path, Color.Black, 1f, Stroke(10f))

        }

        Spacer(modifier = Modifier.height(16.dp))

        Row {
            // 開始辨識
            Button(onClick = {
                val ink = inkBuilder.build()   // 包含全部手寫軌跡的「欲辨識內容」

                if (ink.strokes.isEmpty()) {
                    recognizedText = "請先書寫內容"
                    return@Button
                }

                recognizedText = "正在處理中..."

                if (model != null) {
                    RemoteModelManager.getInstance()
                        .download(model, DownloadConditions.Builder().build())
                        .addOnSuccessListener {

                            recognizer?.recognize(ink)
                                ?.addOnSuccessListener { result ->
                                    if (result.candidates.isNotEmpty()) {
                                        val topCandidate = result.candidates[0].text
                                        recognizedText = "辨識結果: $topCandidate"
                                    } else {
                                        recognizedText = "辨識結果: 無法辨識"
                                    }
                                }
                                ?.addOnFailureListener { recognizedText = "辨識失敗: ${it.message}" }


                        }
                        .addOnFailureListener { recognizedText = "模型下載失敗" }
                } else {
                    recognizedText = "模型建立失敗"
                }


            }) {
                Text("開始辨識")
            }
            Spacer(modifier = Modifier.width(16.dp))

            // 清除畫布
            Button(onClick = {
                path = Path()

                // 清除 ML Kit 累積的筆畫資料
                inkBuilder = Ink.builder()
                strokeBuilder = null
                recognizedText = "請在畫布區手寫，再按「開始辨識」按鈕"

            }){
                Text("清除")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(recognizedText)
    }
}
