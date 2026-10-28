package com.example.freshguard

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val switchNoti = view.findViewById<SwitchMaterial>(R.id.switch_notification)
        val btnReset = view.findViewById<Button>(R.id.btn_reset_all)

        // 1. 저장된 알림 설정 불러오기 (기본값은 true)
        val sharedPref = requireActivity().getSharedPreferences("freshguard_prefs", Context.MODE_PRIVATE)
        val isNotiEnabled = sharedPref.getBoolean("noti_enabled", true)
        switchNoti.isChecked = isNotiEnabled

        // 2. 알림 스위치 변경 시 저장하기
        switchNoti.setOnCheckedChangeListener { _, isChecked ->
            sharedPref.edit().putBoolean("noti_enabled", isChecked).apply()
            val msg = if (isChecked) "알림이 켜졌습니다." else "알림이 꺼졌습니다."
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        // 3. 전체 데이터 초기화 버튼 클릭 시
        btnReset.setOnClickListener {
            showResetConfirmDialog()
        }
    }

    private fun showResetConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("데이터 전체 초기화 ⚠️")
            .setMessage("냉장고에 있는 모든 식재료 데이터가 영구적으로 삭제됩니다. 정말 진행하시겠습니까?")
            .setPositiveButton("초기화") { _, _ ->
                resetAllData()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun resetAllData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // ★ 핵심 수정: 비어있던 delete 블록에 'id가 0보다 큰 데이터'라는 조건을 추가합니다.
                SupabaseManager.client.postgrest["ingredients"].delete {
                    filter {
                        gt("id", 0)
                    }
                }

                withContext(Dispatchers.Main) {
                    // 성공 토스트 메시지
                    Toast.makeText(requireContext(), "모든 식재료 데이터가 초기화되었습니다.", Toast.LENGTH_SHORT).show()

                    // (필요 시) 홈 화면에 반영하기 위해 로컬 마스터 리스트도 비워주거나 화면을 새로고침하는 코드를 여기에 추가하세요.
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "초기화 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}