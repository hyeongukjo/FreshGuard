package com.example.freshguard

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class RecipeFragment : Fragment(R.layout.fragment_recipe) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fetchIngredientsForRecipe(view)
    }

    private fun fetchIngredientsForRecipe(view: View) {
        val chipGroup = view.findViewById<ChipGroup>(R.id.chip_group_ingredients)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = SupabaseManager.client.postgrest["ingredients"]
                    .select()
                    .decodeList<Ingredient>()

                // ★ 업그레이드: 이름별로 '가장 임박한 D-Day'를 계산해서 저장할 바구니 (Map)
                val ingredientStatus = mutableMapOf<String, Long>()
                val format = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
                val today = Date()

                for (item in data) {
                    try {
                        val expiration = format.parse(item.expiryDate) ?: continue
                        val diff = expiration.time - today.time
                        val dDay = TimeUnit.MILLISECONDS.toDays(diff)

                        // 처음 보는 재료거나, 기존에 저장된 D-Day보다 이번 재료가 더 급하면 업데이트!
                        if (!ingredientStatus.containsKey(item.name) || dDay < ingredientStatus[item.name]!!) {
                            ingredientStatus[item.name] = dDay
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }

                withContext(Dispatchers.Main) {
                    chipGroup.removeAllViews()

                    if (ingredientStatus.isEmpty()) {
                        Toast.makeText(requireContext(), "냉장고가 비어있어 레시피를 추천할 수 없어요!", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    // 맵(Map)에 저장된 [식재료 이름, D-Day]를 하나씩 꺼내서 칩 만들기
                    for ((foodName, dDay) in ingredientStatus) {
                        val chip = Chip(requireContext()).apply {
                            isClickable = true
                            isCheckable = false

                            // ★ 업그레이드: D-Day 표기 문자열 만들기
                            val dDayText = when {
                                dDay > 0 -> "D-$dDay"
                                dDay == 0L -> "D-Day"
                                else -> "D+${Math.abs(dDay)}" // 지났을 때
                            }

                            // ★ 업그레이드: 3일 이내면 붉은색으로 긴급 표시!
                            if (dDay <= 3) {
                                text = "🚨 $foodName 레시피 검색 ($dDayText)"
                                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFEBEE")) // 연한 빨강 배경
                                setTextColor(Color.parseColor("#D32F2F")) // 진한 빨강 글씨
                                chipStrokeWidth = 2f
                                chipStrokeColor = ColorStateList.valueOf(Color.parseColor("#D32F2F")) // 테두리 추가
                            } else {
                                text = "🍳 $foodName 요리하기"
                                chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E3F2FD")) // 연한 파랑 배경
                                setTextColor(Color.parseColor("#1976D2")) // 진한 파랑 글씨
                            }

                            setOnClickListener {
                                searchRecipeOnYouTube(foodName)
                            }
                        }
                        chipGroup.addView(chip)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "재료를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun searchRecipeOnYouTube(foodName: String) {
        val searchQuery = "${foodName} 레시피"
        val uri = Uri.parse("https://www.youtube.com/results?search_query=$searchQuery")
        val intent = Intent(Intent.ACTION_VIEW, uri)

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "유튜브를 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}