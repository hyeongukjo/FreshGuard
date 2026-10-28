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
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 기존 식재료 정보(ingredient)와, 수정이 끝났을 때 실행할 행동(onUpdateComplete)을 전달받습니다.
class EditIngredientBottomSheet(
    private val ingredient: Ingredient,
    private val onUpdateComplete: () -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // ★ 마법의 코드: 스캔할 때 만들었던 예쁜 팝업 화면(XML)을 그대로 가져다 씁니다!
        return inflater.inflate(R.layout.bottom_sheet_scan_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvTitle = view.findViewById<TextView>(R.id.tv_scan_result_title)
        val etFoodName = view.findViewById<EditText>(R.id.et_food_name)
        val etQuantity = view.findViewById<EditText>(R.id.et_quantity)
        val rgLocation = view.findViewById<RadioGroup>(R.id.rg_location)
        val btnSave = view.findViewById<Button>(R.id.btn_save_ingredient)

        // 1. 팝업이 뜨자마자 기존 데이터를 칸에 미리 채워넣기
        tvTitle.text = "식재료 정보 수정 ✏️"
        etFoodName.setText(ingredient.name)
        etQuantity.setText(ingredient.quantity)
        if (ingredient.location == "냉장실") rgLocation.check(R.id.rb_fridge) else rgLocation.check(R.id.rb_freezer)

        // 2. [저장] 버튼을 눌렀을 때
        btnSave.setOnClickListener {
            val updatedName = etFoodName.text.toString()
            val updatedQuantity = etQuantity.text.toString()
            val updatedLocation = if (rgLocation.checkedRadioButtonId == R.id.rb_fridge) "냉장실" else "냉동실"

            if (updatedName.isEmpty()) return@setOnClickListener

            // 기존 바구니에서 이름, 수량, 위치만 새로 바꾼(copy) 바구니를 만듭니다.
            val updatedItem = ingredient.copy(
                name = updatedName,
                quantity = updatedQuantity,
                location = updatedLocation
            )

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // ★ DB에 업데이트(수정) 요청! (id가 똑같은 녀석을 찾아서 덮어씌웁니다)
                    SupabaseManager.client.postgrest["ingredients"].update(updatedItem) {
                        filter { eq("id", ingredient.id ?: 0) }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "수정 완료!", Toast.LENGTH_SHORT).show()
                        onUpdateComplete() // 부모 화면(Home)에게 "나 수정 끝났어! 새로고침 해!" 라고 알림
                        dismiss() // 팝업 닫기
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "수정 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}