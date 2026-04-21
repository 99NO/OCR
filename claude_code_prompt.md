# Android OCR 방향 판정 데모 앱 — Claude Code 초기 프롬프트

## 0. 네 역할과 최우선 규칙

너는 Android Studio에서 OCR 데모 앱을 구현하는 시니어 개발자다. Kotlin + Jetpack Compose + Google ML Kit 스택에 익숙하고, 기하 계산과 영상 처리에 자신이 있다.

**가장 중요한 규칙: `ARCHITECTURE.md`를 프로젝트 루트에 만들고 항상 유지한다.**

- 세션을 시작할 때 가장 먼저 `ARCHITECTURE.md`를 읽어 현재 상태를 파악한다.
- 기능을 추가하거나 구조를 바꾸거나 알고리즘을 수정할 때마다 해당 섹션을 갱신한다.
- 갱신은 "뒤로 붙이기"가 아니라 "해당 섹션 다시 쓰기"다. 오래된 정보가 쌓이지 않게 한다.
- 변경 로그 섹션에만 날짜·한 줄 요약을 이어 붙인다.

이 문서의 구조는 다음과 같다.

```
# ARCHITECTURE.md
1. 프로젝트 개요
2. 현재 구현 상태 (체크리스트)
3. 디렉터리 구조
4. 핵심 컴포넌트
5. 알고리즘 상세
6. 의존성
7. 알려진 이슈 / TODO
8. 변경 로그
```

사용자는 별도의 대화 창(이 Claude Code가 아닌 다른 Claude)에서 구조와 알고리즘을 이어서 논의하고, 여기로 돌아와 지시를 내린다. 그 두 대화를 잇는 유일한 연결점이 이 파일이다. 따라서 **누군가 이 파일만 읽고도 프로젝트의 지금 상태와 왜 그렇게 만들어졌는지를 알 수 있어야 한다.**

---

## 1. 프로젝트 개요

**이름(가칭):** OcrOrientationDemo

**목적:** 사용자가 카메라 또는 갤러리로 한국어·영어 혼합 문서 이미지를 입력하면, Google ML Kit Text Recognition v2로 텍스트를 인식하고, 문서가 180° 뒤집혀 있으면 자동으로 감지해 보정한 뒤 결과를 보여주는 데모 앱.

**핵심 시연 포인트:**
1. ML Kit의 라인별 `cornerPoints` 위에 텍스트 박스 오버레이.
2. 각 라인에서 추출한 베이스라인 구두점(마침표 등)의 위치를 점으로 표시.
3. 이 점의 상/하 위치로 계산한 라인별 점수와 문서 전체 판정을 로그로 노출.
4. 뒤집힘으로 판정되면 이미지를 180° 회전한 뒤 OCR을 재실행해 최종 텍스트를 얻는 과정을 사용자가 볼 수 있게 한다.

---

## 2. 기술 스택과 의존성

- Kotlin 2.x, Jetpack Compose (Material 3)
- 최소 SDK 24, 타겟 SDK 최신 안정판
- `com.google.mlkit:text-recognition-korean:16.0.1` — **Korean 인식기 사용** (한/영 모두 처리됨, Latin 전용 모델은 쓰지 않는다)
- CameraX: `camera-core`, `camera-camera2`, `camera-lifecycle`, `camera-view`
- Activity Result API + Android 13+ `PhotoPicker` (갤러리 선택 시 저장소 권한 불필요)
- Kotlin Coroutines (ML Kit 호출 suspend 래핑)
- Coil 또는 Compose `AsyncImage` (이미지 표시)

**권한:** `android.permission.CAMERA`만. 저장소 권한은 추가하지 않는다.

---

## 3. 화면 구성

### 3.1 MainScreen (진입 화면)

- 상단에 앱 제목: "OCR 방향 판정 데모"
- 중앙에 두 개의 큰 버튼:
  - "카메라로 촬영" → CameraX 촬영 플로우
  - "갤러리에서 선택" → PhotoPicker
- 촬영/선택이 끝나면 `Uri` 또는 `Bitmap`을 들고 ResultScreen으로 이동

### 3.2 ResultScreen (결과 화면)

레이아웃은 세로 스크롤, 섹션 순서는 다음과 같다.

**(A) 이미지 + 오버레이 영역** (화면 상단, 고정 비율)
- 원본 이미지(또는 보정된 이미지)를 표시
- 그 위에 Canvas로 다음을 그린다:
  - 각 라인의 `cornerPoints` 4점을 잇는 사각형 (2dp stroke)
    - 점수 +1 라인: 녹색
    - 점수 -1 라인: 빨강
    - 점수 0(판정 보류) 라인: 회색
  - 각 라인에서 검출한 베이스라인 구두점 위치에 작은 원 (반지름 4~5dp, 빨강 채움)
- 이미지 실제 픽셀 좌표를 Canvas 좌표로 스케일링해야 한다. 이미지가 `Modifier.fillMaxWidth().aspectRatio(w/h)` 식으로 표시되므로, 스케일 팩터 = `canvasWidth / imageWidth`로 계산

**(B) 판정 요약 카드**
- 집계 점수 (예: `+0.72`)
- 판정 라벨: "정상 방향" / "뒤집힘 — 자동 보정됨" / "판정 보류"
- 보조 정보: "기여 라인 수 X / 전체 라인 수 Y"
- 재실행이 일어났다면 "원본에서 뒤집힘 감지 → 180° 회전 후 재인식" 같은 안내

**(C) 라인별 평가 로그** (스크롤 가능한 리스트)

각 행에 다음을 한 줄로 보여준다:
```
[+1]  localY=0.81  '.'  "모든 과정은 TensorFlow Lite 런타임에서 실행되며,"
[-1]  localY=0.18  '.'  ".다니습있 어되화자양로8TNI 주로 은델모"
[ 0]  localY=0.47  ','  "애매한 라인 예시"
```
- 점수 색상: +1 녹색, -1 빨강, 0 회색
- `localY`는 소수 둘째 자리까지
- 라인 텍스트는 60자 이상이면 말줄임

**(D) 최종 인식 텍스트** (보정 후)
- 한 큰 `Text` 블록으로 `result.text` 전체 표시
- 길면 스크롤

---

## 4. 알고리즘 상세

### 4.1 데이터 모델

```kotlin
enum class Decision { NORMAL, FLIPPED, UNCERTAIN }

data class LineScore(
    val lineText: String,
    val baselineChar: Char,
    val localY: Float,
    val score: Int,
    val lineCorners: List<android.graphics.Point>,
    val markX: Float,
    val markY: Float,
)

data class OrientationResult(
    val averageScore: Double,
    val decision: Decision,
    val contributingLines: Int,
    val totalLines: Int,
    val perLine: List<LineScore>,
)
```

### 4.2 핵심 계산

베이스라인 문자 집합은 **한/영 공통**으로 다음을 쓴다:
```kotlin
val BASELINE_CHARS = setOf('.', ',', '?', '!', ':', ';', '。')
```

라인 로컬 y 좌표 (0 = 상단 edge, 1 = 하단 edge — 이미지 좌표계 기준, 기울어진 박스에서도 정확):

```kotlin
fun computeLocalY(x: Float, y: Float, corners: List<Point>): Float {
    // corners: [TL, TR, BR, BL] 이미지 좌표
    val topMidX = (corners[0].x + corners[1].x) / 2f
    val topMidY = (corners[0].y + corners[1].y) / 2f
    val botMidX = (corners[2].x + corners[3].x) / 2f
    val botMidY = (corners[2].y + corners[3].y) / 2f
    val ax = botMidX - topMidX
    val ay = botMidY - topMidY
    val px = x - topMidX
    val py = y - topMidY
    val axisSq = ax * ax + ay * ay
    if (axisSq == 0f) return 0.5f
    return ((px * ax + py * ay) / axisSq).coerceIn(0f, 1f)
}
```

방향 점수 집계:

```kotlin
fun analyze(text: com.google.mlkit.vision.text.Text): OrientationResult {
    val perLine = mutableListOf<LineScore>()
    var total = 0
    var contributing = 0
    var totalLines = 0

    for (block in text.textBlocks) {
        for (line in block.lines) {
            totalLines++
            val linePts = line.cornerPoints?.toList() ?: continue

            // Element 순회하며 베이스라인 문자로 끝나는 것 찾기
            for (el in line.elements) {
                val last = el.text.lastOrNull() ?: continue
                if (last !in BASELINE_CHARS) continue
                val ep = el.cornerPoints?.toList() ?: continue

                // Element의 "읽기 방향 끝" edge 중심 = (TR + BR)/2
                val markX = (ep[1].x + ep[2].x) / 2f
                val markY = (ep[1].y + ep[2].y) / 2f

                val ly = computeLocalY(markX, markY, linePts)

                // 0.33~0.67은 노이즈로 보고 버림 (Latin descender 고려)
                val score = when {
                    ly > 0.67f -> +1
                    ly < 0.33f -> -1
                    else -> 0
                }

                if (score != 0) { total += score; contributing++ }
                perLine.add(LineScore(line.text, last, ly, score, linePts, markX, markY))
            }
        }
    }

    val avg = if (contributing > 0) total.toDouble() / contributing else 0.0
    val decision = when {
        contributing < 3 -> Decision.UNCERTAIN
        avg > 0.5 -> Decision.NORMAL
        avg < -0.5 -> Decision.FLIPPED
        else -> Decision.UNCERTAIN
    }
    return OrientationResult(avg, decision, contributing, totalLines, perLine)
}
```

### 4.3 전체 파이프라인

```
사용자가 이미지 선택
  │
  ▼
원본 Bitmap 준비 (필요 시 긴 변 2048px로 다운샘플)
  │
  ▼
OCR 1차: KoreanTextRecognizer.process(InputImage.fromBitmap(bitmap, 0))
  │
  ▼
analyze(text1) → result1
  │
  ├── result1.decision == FLIPPED 이면:
  │     bitmap2 = bitmap.rotate(180f)
  │     OCR 2차: recognizer.process(InputImage.fromBitmap(bitmap2, 0))
  │     analyze(text2) → result2
  │     → 최종 표시에는 bitmap2, text2, result2 사용
  │
  └── 그 외: bitmap, text1, result1 그대로 사용
```

회전된 Bitmap에 대해 재호출하는 이유: 회전 후 ML Kit이 라인 그룹핑·블록 순서를 다시 계산해주므로 직접 재정렬 로직을 짜지 않아도 된다.

---

## 5. 디렉터리 구조 (초안)

```
app/src/main/java/<pkg>/
├── MainActivity.kt
├── ui/
│   ├── main/MainScreen.kt
│   ├── result/ResultScreen.kt
│   ├── result/OverlayCanvas.kt
│   ├── result/LineLogList.kt
│   └── theme/...
├── ocr/
│   ├── OcrProcessor.kt          // ML Kit 래퍼 (suspend)
│   ├── OrientationAnalyzer.kt   // analyze() 구현
│   └── Models.kt                // LineScore, OrientationResult, Decision
├── camera/
│   └── CameraCaptureScreen.kt   // CameraX Compose 통합
├── gallery/
│   └── PhotoPicker.kt           // Activity Result 래퍼
└── util/
    ├── BitmapUtils.kt           // rotate, downscale
    └── GeometryUtils.kt         // computeLocalY 등
```

---

## 6. 단계별 작업 순서

각 단계가 끝나면 반드시 `ARCHITECTURE.md`의 해당 섹션을 갱신하고, 변경 로그에 한 줄 추가한다.

1. **초기 문서화**: `ARCHITECTURE.md`를 위 구조대로 생성. 이 프롬프트의 개요·알고리즘·디렉터리 섹션을 옮겨 적는다. 체크리스트(섹션 2)는 전부 `[ ]` 상태로 둔다.
2. **프로젝트 뼈대**: Empty Compose Activity로 새 Android 프로젝트 생성. Gradle 의존성 추가. 매니페스트에 `CAMERA` 권한 선언.
3. **순수 Kotlin 코어 구현**: `util/GeometryUtils.kt`의 `computeLocalY`, `ocr/Models.kt`, `ocr/OrientationAnalyzer.kt` 구현. 유닛 테스트 하나만이라도 추가(기울어진 박스 케이스 포함). ← 이 단계에서 Android UI 빌드 실패해도 상관없음.
4. **OCR 래퍼**: `OcrProcessor`에 `suspend fun recognize(bitmap: Bitmap): Text` 구현. Korean recognizer 인스턴스를 싱글톤으로 관리.
5. **Main UI + Navigation**: `MainScreen`과 Compose Navigation 기본 틀. 두 버튼은 아직 placeholder로 Toast만 띄움.
6. **갤러리 플로우 연결**: `PhotoPicker`로 Uri 받고, Uri → Bitmap 변환, ResultScreen으로 전달. 이때까지는 ResultScreen이 Bitmap만 표시.
7. **ResultScreen 파이프라인**: OCR 호출 → `analyze()` → 뒤집힘이면 재호출. 판정 카드와 최종 텍스트 표시까지.
8. **OverlayCanvas**: 라인 박스와 마침표 점 렌더링. 스케일 계산 주의. 판정 색상 적용.
9. **라인별 로그 리스트**: `LineLogList` 컴포저블.
10. **CameraX 플로우**: `CameraCaptureScreen` 구현하고 MainScreen의 카메라 버튼에 연결.
11. **엣지 케이스 처리**: 텍스트 없음, 인식 실패, 권한 거부, 극저조도 이미지 등. 에러 상태를 ResultScreen이 자연스럽게 보여주게.
12. **성능 튜닝**: 큰 이미지 다운샘플, 재호출 시 중복 연산 제거, Compose recomposition 최적화.

단계 1~3을 먼저 끝내고 사용자에게 보고한다. 그 뒤로는 사용자가 "다음 단계" 혹은 구체적 지시를 줄 때마다 진행한다.

---

## 7. 커뮤니케이션 규칙

- 코드 클래스/함수/변수명은 영어 컨벤션. 주석은 한국어 허용.
- UI 문자열은 모두 한국어. `strings.xml`로 분리.
- 사용자가 별도의 대화에서 알고리즘을 수정해달라고 하면, **그 변경 이유를 반드시 `ARCHITECTURE.md` 알고리즘 섹션에 근거와 함께 기록**한 뒤 구현한다. 구현만 하고 문서를 건너뛰지 않는다.
- 코드에 불확실한 결정(예: 임계값 `0.67`)이 있으면 해당 함수 위 주석에 "이 값은 ARCHITECTURE.md §5에서 논의" 식으로 참조를 남긴다.
- 중요한 아키텍처 선택(예: Compose vs XML, MVVM 도입 여부, DI 라이브러리 도입) 전에는 먼저 한 줄 제안을 하고 확인을 받는다.

---

## 8. 지금 할 일

1. 위 내용을 전부 파악한다.
2. 프로젝트 루트(아직 프로젝트가 없으면 지금 만들 위치)에 `ARCHITECTURE.md`를 생성한다. 내용은 이 프롬프트의 §1·§4·§5·§2(의존성)·§6(체크리스트, 전부 `[ ]`)·빈 변경 로그를 재구성해 옮겨 적는다. 단순 복붙이 아니라 요약·정돈된 형태로 쓴다.
3. 작업 순서 1~3번(ARCHITECTURE.md 초안 → 프로젝트 뼈대 → 순수 Kotlin 코어)을 진행하고 결과를 보고한다.
4. 보고할 때는 (a) 생성/수정한 파일 목록, (b) `ARCHITECTURE.md`에 기록한 주요 결정, (c) 다음 단계 제안을 포함한다.

시작해라.
