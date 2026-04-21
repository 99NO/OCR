package com.example.newocr2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.newocr2.navigation.AppNavGraph
import com.example.newocr2.ocr.OcrProcessor
import com.example.newocr2.ui.theme.NewOCR2Theme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NewOCR2Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavGraph()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // isFinishing=true 일 때만 해제.
        // 카메라 앱 복귀·화면 회전 등 재생성 시에는 호출하지 않는다.
        if (isFinishing) OcrProcessor.close()
    }
}
