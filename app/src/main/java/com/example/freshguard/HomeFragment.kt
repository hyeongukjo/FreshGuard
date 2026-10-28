package com.example.freshguard

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.tabs.TabLayout

class HomeFragment : Fragment(R.layout.fragment_home) {

    private lateinit var adapter: IngredientAdapter
    // 1. 서버에서 가져올 진짜 데이터를 담을 빈 바구니 준비
    private val ingredientList = mutableListOf<Ingredient>()
    private var allIngredients = listOf<Ingredient>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_ingredient_list)

        // 2. 어댑터에 바구니 연결 (처음엔 비어있음)
        adapter = IngredientAdapter(ingredientList) { clickedItem ->
            // 카드를 클릭하면 실행되는 곳!
            val bottomSheet = EditIngredientBottomSheet(clickedItem) {
                // 팝업창에서 '수정 완료'가 되면 서버에서 다시 최신 데이터를 싹 가져옵니다.
                fetchIngredientsFromSupabase()
            }
            bottomSheet.show(parentFragmentManager, "EditBottomSheet")
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false // 위아래 이동은 사용하지 않음
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 스와이프된 아이템의 위치 찾기
                val position = viewHolder.adapterPosition
                val targetItem = ingredientList[position]

                // 1. DB에서 삭제 시도
                deleteIngredientFromSupabase(targetItem, position)
            }
        }

        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)

        val tabLayout = view.findViewById<TabLayout>(R.id.tabs_location)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                filterListByTab(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    // ★ Supabase에서 데이터를 삭제하는 함수
    private fun deleteIngredientFromSupabase(item: Ingredient, position: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. DB에서 삭제
                SupabaseManager.client.postgrest["ingredients"].delete {
                    filter { eq("id", item.id ?: 0) }
                }

                withContext(Dispatchers.Main) {
                    // ★ 핵심 수정 구간 시작 ★

                    // 1. 보여주는 리스트에서 삭제
                    ingredientList.removeAt(position)

                    // 2. Master 바구니(allIngredients)에서도 삭제!
                    // List를 MutableList로 바꿔서 해당 아이템을 제거합니다.
                    val newList = allIngredients.toMutableList()
                    newList.removeAll { it.id == item.id }
                    allIngredients = newList

                    // 3. UI 갱신 (리스트 애니메이션)
                    adapter.notifyItemRemoved(position)

                    // 4. (중요) 필터링 함수를 다시 호출하여 현재 탭 상태를 유지하면서 갱신
                    val tabLayout = view?.findViewById<TabLayout>(R.id.tabs_location)
                    filterListByTab(tabLayout?.selectedTabPosition ?: 0)

                    // ★ 핵심 수정 구간 끝 ★

                    checkEmptyState()
                    updateDashboardStats()
                    Toast.makeText(requireContext(), "${item.name} 삭제 완료", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    adapter.notifyItemChanged(position)
                    Toast.makeText(requireContext(), "삭제 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 3. 화면이 사용자에게 보일 때마다(onResume) 서버에서 최신 데이터를 가져옵니다.
    override fun onResume() {
        super.onResume()
        fetchIngredientsFromSupabase()
    }

    // ★ Supabase에서 데이터를 가져오는 핵심 함수
    private fun fetchIngredientsFromSupabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val data = SupabaseManager.client.postgrest["ingredients"]
                    .select()
                    .decodeList<Ingredient>()

                withContext(Dispatchers.Main) {
                    // ★ 수정: 가져온 데이터를 일단 원본 바구니에 안전하게 저장!
                    allIngredients = data.sortedBy { it.expiryDate }

                    // 현재 선택된 탭 번호를 확인해서 리스트 필터링
                    val tabLayout = view?.findViewById<TabLayout>(R.id.tabs_location)
                    val currentTabPosition = tabLayout?.selectedTabPosition ?: 0
                    filterListByTab(currentTabPosition)
                    checkEmptyState()

                    updateDashboardStats()
                }
            } catch (e: Exception) {
                // ... 에러 처리
            }
        }
    }

    // ★ 새롭게 추가하는 핵심 필터링 함수
    private fun filterListByTab(tabPosition: Int) {
        ingredientList.clear()

        when (tabPosition) {
            0 -> { // '전체' 탭
                ingredientList.addAll(allIngredients)
            }
            1 -> { // '냉장실' 탭
                ingredientList.addAll(allIngredients.filter { it.location == "냉장실" })
            }
            2 -> { // '냉동실' 탭
                ingredientList.addAll(allIngredients.filter { it.location == "냉동실" })
            }
        }

        adapter.notifyDataSetChanged() // 화면 새로고침!
        checkEmptyState()
    }

    private fun checkEmptyState() {
        val recyclerView = view?.findViewById<RecyclerView>(R.id.rv_ingredient_list)
        val emptyState = view?.findViewById<View>(R.id.layout_empty_state)

        if (ingredientList.isEmpty()) {
            recyclerView?.visibility = View.GONE     // 리스트 숨기기
            emptyState?.visibility = View.VISIBLE    // 빈 화면 보여주기
        } else {
            recyclerView?.visibility = View.VISIBLE  // 리스트 보여주기
            emptyState?.visibility = View.GONE       // 빈 화면 숨기기
        }
    }

    // ★ 대시보드의 숫자를 계산해주는 새로운 함수 (클래스 맨 아래에 추가해주세요)
    private fun updateDashboardStats() {
        var imminentCount = 0
        var expiredCount = 0

        val format = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
        val today = Date()

        for (item in ingredientList) {
            try {
                val expiration = format.parse(item.expiryDate) ?: continue
                val diff = expiration.time - today.time
                val dDay = TimeUnit.MILLISECONDS.toDays(diff)

                if (dDay < 0) {
                    expiredCount++ // 지났으면 증가
                } else if (dDay <= 3) {
                    imminentCount++ // 3일 이하로 남았으면 증가
                }
            } catch (e: Exception) {
                continue
            }
        }

        // 계산된 숫자를 화면(TextView)에 꽂아주기
        view?.findViewById<TextView>(R.id.tv_total_count)?.text = "${ingredientList.size}개"
        view?.findViewById<TextView>(R.id.tv_imminent_count)?.text = "${imminentCount}개"
        view?.findViewById<TextView>(R.id.tv_expired_count)?.text = "${expiredCount}개"

    }
}