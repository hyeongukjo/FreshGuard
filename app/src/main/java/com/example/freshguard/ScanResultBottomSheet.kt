package com.example.freshguard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.content.DialogInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.jan.supabase.postgrest.postgrest

class ScanResultBottomSheet(private val scannedDate: String, private val onDismissed: () -> Unit) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 1단계에서 만든 디자인(xml)을 연결해 줍니다.
        return inflater.inflate(R.layout.bottom_sheet_scan_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 화면에 있는 버튼과 입력창들을 코드와 연결
        val tvScannedDate = view.findViewById<TextView>(R.id.tv_scanned_date)
        val etFoodName = view.findViewById<EditText>(R.id.et_food_name)
        val etQuantity = view.findViewById<EditText>(R.id.et_quantity)
        val rgLocation = view.findViewById<RadioGroup>(R.id.rg_location)
        val btnSave = view.findViewById<Button>(R.id.btn_save_ingredient)

        // 스캔된 날짜를 텍스트뷰에 보여주기
        tvScannedDate.text = "인식된 유통기한: $scannedDate"

        // 저장 버튼 클릭 이벤트
        btnSave.setOnClickListener {
            val foodName = etFoodName.text.toString()
            val quantity = etQuantity.text.toString()
            val location = if (rgLocation.checkedRadioButtonId == R.id.rb_fridge) "냉장실" else "냉동실"

            if (foodName.isEmpty()) {
                Toast.makeText(requireContext(), "식품명을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 전송할 새 식재료 바구니 만들기
            val newItem = Ingredient(
                name = foodName,
                expiryDate = scannedDate,
                location = location,
                quantity = quantity
            )

            // ★ 인터넷(DB) 통신은 백그라운드(Dispatchers.IO)에서 실행해야 앱이 안 멈춥니다!
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // 1. Supabase의 "ingredients" 테이블에 데이터 쏙 넣기
                    SupabaseManager.client.postgrest["ingredients"].insert(newItem)

                    // 2. 작업이 성공하면 화면 업데이트는 다시 메인 스레드에서 진행
                    withContext(Dispatchers.Main) {
                        IngredientManager.addIngredient(newItem) // 홈 화면 리스트에도 즉시 추가
                        Toast.makeText(
                            requireContext(),
                            "${foodName} 서버 저장 완료!",
                            Toast.LENGTH_SHORT
                        ).show()
                        dismiss() // 팝업 닫기
                    }
                } catch (e: Exception) {
                    // 인터넷이 끊겼거나 DB 오류가 발생했을 때
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "저장 실패: ${e.message}", Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }
    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissed() // 부모(ScanFragment)에서 전달해준 행동을 실행!
    }
}