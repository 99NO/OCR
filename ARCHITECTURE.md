# ARCHITECTURE.md — OcrOrientationDemo

## 1. 프로젝트 개요

**목적:** 한국어·영어 혼합 문서 이미지를 카메라 또는 갤러리로 입력받아 Google ML Kit Text Recognition v2(Korean)로 텍스트를 인식하고, 문서가 180° 뒤집혀 있으면 자동 감지·보정 후 결과를 시각적으로 보여주는 데모 앱.

**패키지명:** `com.example.newocr2`  
**앱 이름(표시):** OCR 방향 판정 데모

**핵심 시연 포인트:**
1. ML Kit 라인별 `cornerPoints` 위에 텍스트 박스 오버레이 (판정 색상 적용)
2. 각 라인에서 추출한 베이스라인 구두점 위치를 점으로 표시
3. 라인별 점수·로컬 Y·판정 결과를 로그 리스트로 노출
4. 뒤집힘 판정 시 이미지 180° 회전 후 OCR 재실행 과정을 사용자가 볼 수 있음

---

## 2. 현재 구현 상태 (체크리스트)

- [x] ARCHITECTURE.md 초안 작성
- [x] Gradle 의존성 추가 (ML Kit Korean, CameraX, Navigation, Coroutines, Coil)
- [x] AndroidManifest.xml — CAMERA 권한 선언
- [x] `util/GeometryUtils.kt` — `computeLocalY` 구현
- [x] `ocr/Models.kt` — `Decision`, `LineScore`, `OrientationResult`
- [x] `ocr/OrientationAnalyzer.kt` — `analyze()` 구현
- [x] 유닛 테스트 — `GeometryUtilsTest` (정방형·기울어진 박스 케이스)
- [x] `ocr/OcrProcessor.kt` — ML Kit suspend 래퍼
- [x] `ui/main/MainScreen.kt` — 진입 화면 (두 버튼)
- [x] Compose Navigation 기본 틀 (`navigation/AppNavGraph.kt`, `BitmapHolder`)
- [x] `gallery/PhotoPicker.kt` — Uri 수신 + Bitmap 변환 (`uriToBitmap`, `BitmapUtils`)
- [x] `ui/result/ResultViewModel.kt` — OCR 파이프라인 상태 관리 (Loading/Success/Error)
- [x] `ui/result/ResultScreen.kt` — 이미지·판정 카드·라인 로그 플레이스홀더·최종 텍스트
- [x] `ui/result/OverlayCanvas.kt` — 라인 박스(판정 색상)·구두점 마커 Canvas 렌더링
- [x] `ui/result/LineLogList.kt` — `LineLogList` 컴포저블 (점수 색상, localY, 말줄임)
- [x] `camera/CameraCaptureScreen.kt` — CameraX Compose 통합, 권한 거부 화면
- [x] 엣지 케이스 처리 (텍스트 없음·UNCERTAIN 안내, 권한 거부, 인식 실패·재시도)
- [x] 성능 튜닝 (카메라 Bitmap 다운스케일, `remember(bitmap)` ImageBitmap 캐시, ViewModel 재실행 방지)

---

## 3. 디렉터리 구조

```
app/src/main/java/com/example/newocr2/
├── MainActivity.kt          ← AppNavGraph 호스팅, OcrProcessor.close()
├── navigation/
│   └── AppNavGraph.kt       ← Routes, NavHost, BitmapHolder
├── ui/
│   ├── main/MainScreen.kt   ← 진입 화면 (카메라·갤러리 버튼)
│   ├── result/
│   │   ├── ResultViewModel.kt ← ResultUiState, OCR 파이프라인
│   │   ├── ResultScreen.kt  ← 이미지·판정 카드·로그·최종 텍스트
│   │   ├── OverlayCanvas.kt ← 라인 박스·구두점 마커 Canvas 렌더링
│   │   └── LineLogList.kt   ← 라인별 평가 로그 리스트
│   └── theme/               (Color.kt, Theme.kt, Type.kt — 자동 생성)
├── ocr/
│   ├── Models.kt            ← Decision, LineScore, OrientationResult
│   ├── OrientationAnalyzer.kt  ← analyze()
│   └── OcrProcessor.kt     ← KoreanTextRecognizer lazy 싱글톤
├── camera/
│   └── CameraCaptureScreen.kt  ← Preview + ImageCapture, 권한 처리, 회전 보정
├── gallery/
│   └── PhotoPicker.kt      ← rememberPhotoPicker (Android 13+ PhotoPicker 래퍼)
└── util/
    ├── GeometryUtils.kt    ← computeLocalY
    └── BitmapUtils.kt      ← uriToBitmap, rotate(degrees), downscaleIfNeeded()

app/src/test/java/com/example/newocr2/
└── GeometryUtilsTest.kt    ← computeLocalY 유닛 테스트
```

---

## 4. 핵심 컴포넌트

### MainActivity
`AppNavGraph`를 호스팅하는 Compose Activity. `onDestroy()`에서 `OcrProcessor.close()`를 호출해 ML Kit 네이티브 리소스를 해제한다.

### AppNavGraph / Routes
- `Routes.MAIN` → `MainScreen`
- `Routes.RESULT` → `ResultScreen` + `ResultViewModel`
- Bitmap은 `BitmapHolder` object를 통해 전달. NavArgs로 Bitmap을 넣으면 Binder 한도(1 MB) 초과 위험이 있어 메모리 전달 방식을 선택.

### OcrProcessor
`suspend fun recognize(bitmap: Bitmap, rotation: Int = 0): Text`

- `object` 싱글톤. `recognizer`는 `lazy`로 초기화해 첫 호출 시에만 생성.
- ML Kit의 Task 콜백을 `suspendCoroutine`으로 래핑 — `addOnSuccessListener`/`addOnFailureListener`가 각각 `resume`/`resumeWithException` 호출.
- `close()`로 네이티브 리소스 해제 (Application 종료 시 호출 예정).
- 인스턴스를 Application-scoped로 단순 object 싱글톤으로 결정. ViewModel-scoped DI는 현 데모 규모에서 과도하다고 판단.

### OrientationAnalyzer
`analyze(text: Text): OrientationResult` — 블록·라인·엘리먼트를 순회해 베이스라인 구두점의 로컬 Y를 계산하고 라인별 점수를 집계.

### OverlayCanvas
Compose `Canvas`로 cornerPoints 박스와 구두점 마커를 이미지 위에 그린다.
- 스케일 팩터 = `canvasWidth / imageWidth` (x·y 동일 적용, 이미지가 `fillMaxWidth().aspectRatio(w/h)`로 표시되므로)
- 박스: cornerPoints 4점을 Path로 연결, 2dp stroke, 판정 색상(+1 녹색/-1 빨강/0 회색)
- 마커: markX/Y에 반지름 4.5dp 채움 원 (빨강)

---

## 5. 알고리즘 상세

### 5.1 데이터 모델

```kotlin
enum class Decision { NORMAL, FLIPPED, UNCERTAIN }

data class LineScore(
    val lineText: String,
    val baselineChar: Char,
    val localY: Float,      // 0 = 라인 상단 edge, 1 = 라인 하단 edge
    val score: Int,         // +1 / -1 / 0
    val lineCorners: List<android.graphics.Point>,
    val markX: Float,       // 구두점 마커 이미지 픽셀 좌표
    val markY: Float,
)

data class OrientationResult(
    val averageScore: Double,
    val decision: Decision,
    val contributingLines: Int,  // score != 0 인 라인 수
    val totalLines: Int,
    val perLine: List<LineScore>,
)
```

### 5.2 베이스라인 문자 집합

한/영 공통:
```kotlin
val BASELINE_CHARS = setOf('.', ',', '?', '!', ':', ';', '。')
```

### 5.3 computeLocalY

corners 순서: `[TL, TR, BR, BL]` (이미지 좌표계)

상단 edge 중심(topMid)과 하단 edge 중심(botMid) 사이의 축 벡터에 대해,
구두점 마커 위치 P를 투영해 정규화된 거리를 반환.

- `localY ≈ 0` → 라인 상단에 가까움 (문서가 뒤집힌 경우 구두점이 여기 위치)
- `localY ≈ 1` → 라인 하단에 가까움 (정상 방향에서 구두점 위치)

### 5.4 점수 판정 임계값

```
localY > 0.67  →  score = +1  (정상)
localY < 0.33  →  score = -1  (뒤집힘)
0.33 ≤ localY ≤ 0.67  →  score = 0  (노이즈, 버림)
```

0.33/0.67 임계값은 Latin 계열 descender(g, p, q 등)가 하단으로 내려가는 점을 고려해 중간 대역을 넓게 설정. 조정이 필요하면 이 섹션을 갱신.

### 5.5 최종 판정

```
contributing < 3           → UNCERTAIN
averageScore > +0.5        → NORMAL
averageScore < -0.5        → FLIPPED
그 외                       → UNCERTAIN
```

### 5.6 전체 파이프라인

```
이미지 입력 (Camera / Gallery)
  │
  ▼
Bitmap 준비 (긴 변 2048px 이하로 다운샘플 — BitmapUtils.downscaleIfNeeded)
  │
  ▼
OCR 1차: KoreanTextRecognizer.process(InputImage.fromBitmap(bitmap, 0))
  │
  ▼
analyze(text1) → result1
  │
  ├── result1.decision == FLIPPED
  │     bitmap2 = bitmap.rotate(180°)
  │     OCR 2차 → analyze(text2) → result2
  │     최종 표시: bitmap2, text2, result2
  │
  └── 그 외: bitmap, text1, result1 그대로 사용
```

회전 후 ML Kit 재실행 이유: ML Kit이 라인 그룹핑·블록 순서를 다시 계산해주므로 직접 재정렬 로직 불필요.

---

## 6. 의존성

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| `com.google.mlkit:text-recognition-korean` | 16.0.1 | OCR (한/영 통합) |
| `androidx.camera:camera-core/camera2/lifecycle/view` | 1.4.1 | CameraX |
| `androidx.navigation:navigation-compose` | 2.8.7 | 화면 전환 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.8.1 | suspend 래핑 |
| `io.coil-kt:coil-compose` | 2.7.0 | 이미지 로드 |
| Kotlin | 2.2.10 | — |
| Compose BOM | 2026.02.01 | — |
| minSdk 24, targetSdk 36 | — | — |

**권한:** `android.permission.CAMERA`만. 저장소 권한 없음 (Android 13+ PhotoPicker 사용).

---

## 7. 알려진 이슈 / TODO

- CameraX `ProcessCameraProvider` 바인딩 후 화면 재진입 시 `unbindAll()` 중복 호출 가능성 — 실사용 시 점검 필요
- `OcrProcessor.close()` 호출 시점 미연결 — Application.onTerminate() 또는 ProcessLifecycleOwner에서 연결 필요
- CameraX ImageCapture 해상도 설정 미결정
- 매우 적은 텍스트(라인 1~2개)에서 UNCERTAIN 판정이 빈번할 수 있음 — 임계값 재검토 필요

---

## 8. 변경 로그

| 날짜 | 내용 |
|---|---|
| 2026-04-20 | 단계 1~3: ARCHITECTURE.md 초안, Gradle 의존성, CAMERA 권한, GeometryUtils/Models/OrientationAnalyzer 구현, 유닛 테스트 추가 |
| 2026-04-20 | 단계 4: OcrProcessor — KoreanTextRecognizer lazy 싱글톤 + suspendCoroutine 래핑 |
| 2026-04-20 | 단계 5: MainScreen, AppNavGraph(Routes + BitmapHolder), MainActivity NavHost 교체 |
| 2026-04-20 | 단계 6: PhotoPicker, BitmapUtils(uriToBitmap·rotate), 갤러리→ResultScreen 플로우 연결 |
| 2026-04-20 | 단계 7: ResultViewModel(OCR 파이프라인·FLIPPED 재처리), ResultScreen(판정 카드·라인 로그·최종 텍스트) |
| 2026-04-20 | 단계 8: OverlayCanvas — 라인 박스(판정 색상)·구두점 마커 Canvas 렌더링, ResultScreen 이미지 영역에 연결 |
| 2026-04-20 | 단계 9: LineLogList 컴포저블 분리, ResultScreen 플레이스홀더 교체, 미사용 import 정리 |
| 2026-04-20 | 단계 10: CameraCaptureScreen (권한·Preview·ImageCapture·회전 보정), AppNavGraph CAMERA 라우트 연결 |
| 2026-04-20 | 단계 11: 엣지 케이스 — UNCERTAIN 세부 안내, 오류 재시도 버튼, 권한 거부 화면, 이미지 로드 실패 Toast |
| 2026-04-20 | 단계 12: downscaleIfNeeded(카메라 Bitmap), remember(bitmap) ImageBitmap 캐시, ViewModel started 플래그로 재실행 방지 |
