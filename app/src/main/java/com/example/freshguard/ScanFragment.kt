package com.example.freshguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.util.concurrent.Executors
import android.util.Log

class ScanFragment : Fragment(R.layout.fragment_scan) {

    private lateinit var viewFinder: PreviewView
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
    private var isPopupShowing = false
    private var camera: androidx.camera.core.Camera? = null // 카메라 제어용 변수

    // 1. 카메라 권한 요청을 위한 도구
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera() // 권한 허용 시 카메라 시작
        } else {
            Toast.makeText(context, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewFinder = view.findViewById(R.id.viewFinder)

        // 2. 권한 확인 후 카메라 실행
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        /*viewFinder.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_UP) {
                val factory = viewFinder.meteringPointFactory
                // 터치한 좌표를 카메라용 좌표로 변환
                val point = factory.createPoint(event.x, event.y)
                val action = androidx.camera.core.FocusMeteringAction.Builder(point).build()

                // 카메라에게 초점 잡기 명령 하달!
                camera?.cameraControl?.startFocusAndMetering(action)

                // 터치 이벤트를 소비함
                return@setOnTouchListener true
            }
            false
        }*/
    }

    // 3. 실제 카메라를 켜는 핵심 로직
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // 미리보기 설정
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // [추가] 이미지 분석 설정 (실시간 스캔 핵심)
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, DateAnalyzer())
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                // 분석(imageAnalysis)을 추가해서 바인딩합니다.
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    // 4. 실시간 이미지 분석기 클래스
    // 4. 실시간 이미지 분석기 클래스
    private inner class DateAnalyzer : ImageAnalysis.Analyzer {
        @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {

            // ★ 팝업이 떠있으면 이미지 분석을 건너뛰고 프레임만 닫아줍니다.
            if (isPopupShowing) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                recognizer.process(image)
                    .addOnSuccessListener { visionText ->

                        // ----------------------------------------------------
                        // ★ 수정된 핵심 부분: 카메라가 읽은 전체 글자를 '만능 번역기'에 넣습니다!
                        val formattedDate = extractStandardDate(visionText.text)

                        // 번역 결과가 빈 칸이 아니고(날짜를 찾았고), 팝업이 안 떠있을 때만 실행
                        if (formattedDate.isNotEmpty() && !isPopupShowing) {
                            isPopupShowing = true // 팝업이 뜬다고 상태 변경

                            activity?.runOnUiThread {
                                // ★ 수정됨: 큰 바텀시트를 바로 띄우지 않고, 중간 확인 팝업을 먼저 띄웁니다!
                                showVerifyDialog(formattedDate)
                            }
                        }
                        // ----------------------------------------------------

                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    private fun showVerifyDialog(scannedDate: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_verify_date, null)
        val etVerifyDate = dialogView.findViewById<android.widget.EditText>(R.id.et_verify_date)
        val btnRescan = dialogView.findViewById<android.widget.Button>(R.id.btn_rescan)
        val btnApply = dialogView.findViewById<android.widget.Button>(R.id.btn_apply_date)

        // 인식된 날짜를 입력창에 미리 적어두기
        etVerifyDate.setText(scannedDate)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false) // 사용자가 바깥을 터치해서 꺼버리는 것 방지
            .create()

        // 팝업 배경을 투명하게 해서 모서리 둥근 디자인(rounded_bottom_sheet)이 보이게 함
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // [다시 인식] 버튼: 팝업 닫고 카메라 분석 재개
        btnRescan.setOnClickListener {
            dialog.dismiss()
            isPopupShowing = false
        }

        // [적용하기] 버튼: 사용자가 수정한 날짜를 들고 원래 띄우려던 큰 바텀시트로 이동!
        btnApply.setOnClickListener {
            val finalDate = etVerifyDate.text.toString()
            dialog.dismiss()

            // 바텀시트를 띄우고, 바텀시트가 닫힐 때 카메라를 다시 켜도록 설정
            val bottomSheet = ScanResultBottomSheet(finalDate) {
                isPopupShowing = false
            }
            bottomSheet.show(parentFragmentManager, bottomSheet.tag)
        }

        dialog.show()
    }
    private fun extractStandardDate(rawText: String): String {
       /* // 줄바꿈이나 불필요한 공백을 하나로 합침
        val text = rawText.replace("\n", " ")

        // 날짜를 잡아낼 정규표현식(패턴) 리스트
        val regexList = listOf(
            // 1. 한국어 표기 (예: 2026년 12월 31일, 26년 5월 1일)
            """(\d{2,4})\s*년\s*(\d{1,2})\s*월\s*(\d{1,2})\s*일?""".toRegex(),
            // 2. 일/월/년 외국식 (예: 31/12/26, 31-12-2026) -> 첫 번째 덩어리가 13~31 사이인 경우
            """([1-3][0-9])[\.\-\/]\s*(0?[1-9]|1[0-2])[\.\-\/]\s*(\d{2,4})""".toRegex(),
            // 3. 보편적 기호 표기 (예: 2026.12.31, 26-12-31, 2026/12/31)
            """(\d{2,4})[\.\-\/]\s*(\d{1,2})[\.\-\/]\s*(\d{1,2})""".toRegex()
        )

        for (regex in regexList) {
            val match = regex.find(text)
            if (match != null) {
                val part1 = match.groupValues[1]
                var month = match.groupValues[2]
                val part3 = match.groupValues[3]

                var year: String
                var day: String

                // 외국식(일/월/년) 판별: 첫 번째 덩어리가 13 이상이면 '일'로 간주하여 순서 뒤집기
                val val1 = part1.toIntOrNull() ?: 0
                val val3 = part3.toIntOrNull() ?: 0

                // 1. 첫 숫자가 13~31 사이면 확실한 '일(Day)' (예: 25/12/26 -> 2026.12.25)
                if (val1 in 13..31) {
                    year = part3
                    day = part1
                }
                // 2. 첫 숫자가 12 이하(월/일)인데, 끝 숫자가 24 이상(최근 연도)이면 끝이 '연도' (예: 12/12/27 -> 2027.12.12)
                else if (val1 <= 12 && val3 >= 24) {
                    year = part3
                    day = part1
                }
                // 3. 그 외에는 한국 기본 표기(연/월/일)로 간주 (예: 26/05/11 -> 2026.05.11)
                else {
                    year = part1
                    day = part3
                }

                // 연도가 2자리(예: 26)면 앞에 20을 붙여서 4자리(2026)로 통일
                if (year.length == 2) {
                    year = "20$year"
                }

                // 월, 일이 1자리(예: 5)면 앞에 0을 붙여서 2자리(05)로 통일
                month = month.padStart(2, '0')
                day = day.padStart(2, '0')

                // 앱의 공식 규격인 yyyy.MM.dd 로 조립해서 반환!
                return "$year.$month.$day"
            }
        }

        // 어떤 정규식에도 안 걸렸다면(날짜가 없으면) 빈 칸 반환
        return ""*/
        val text = rawText.replace("\n", " ")

        // 1순위: YYYY.MM.DD
        val yyyyRegex =
            """(20\d{2})[\.\-\/]\s*(\d{1,2})[\.\-\/]\s*(\d{1,2})"""
                .toRegex()

        yyyyRegex.find(text)?.let {

            val year = it.groupValues[1]
            val month = it.groupValues[2].padStart(2, '0')
            val day = it.groupValues[3].padStart(2, '0')

            return "$year.$month.$day"
        }

        // 2순위: YY.MM.DD
        val yyRegex =
            """(\d{2})[\.\-\/]\s*(\d{1,2})[\.\-\/]\s*(\d{1,2})"""
                .toRegex()

        yyRegex.find(text)?.let {

            val year = "20${it.groupValues[1]}"
            val month = it.groupValues[2].padStart(2, '0')
            val day = it.groupValues[3].padStart(2, '0')

            return "$year.$month.$day"
        }

        return ""
    }
}



