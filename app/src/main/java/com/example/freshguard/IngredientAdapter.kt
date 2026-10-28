package com.example.freshguard

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit // ★ 이 줄이 MILLISECONDS 에러를 잡아줍니다!

class IngredientAdapter(private val ingredientList: List<Ingredient>, private val onItemClick: (Ingredient) -> Unit) :
    RecyclerView.Adapter<IngredientAdapter.IngredientViewHolder>() {

    // 1. ViewHolder: XML의 모든 이름표(ID)를 연결합니다.
    class IngredientViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_item_name)
        val tvLocation: TextView = itemView.findViewById(R.id.tv_item_location)
        val tvExpiry: TextView = itemView.findViewById(R.id.tv_item_expiry)
        val tvQuantity: TextView = itemView.findViewById(R.id.tv_item_quantity)
        val tvDday: TextView = itemView.findViewById(R.id.tv_item_dday)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingredient, parent, false)
        return IngredientViewHolder(view)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        val item = ingredientList[position]

        holder.tvName.text = item.name
        holder.tvLocation.text = item.location
        holder.tvExpiry.text = item.expiryDate
        holder.tvQuantity.text = "수량: ${item.quantity}"

        // ★ D-Day 계산 로직
        val dDay = calculateDday(item.expiryDate)

        when {
            dDay < 0 -> { // 기한 지남
                holder.tvDday.text = "D+${Math.abs(dDay)}"
                holder.tvDday.setTextColor(Color.parseColor("#B00020")) // 진한 빨강
            }
            dDay == 0L -> { // 당일
                holder.tvDday.text = "D-Day"
                holder.tvDday.setTextColor(Color.parseColor("#D32F2F"))
            }
            dDay <= 3 -> { // 3일 이내 임박
                holder.tvDday.text = "D-$dDay"
                holder.tvDday.setTextColor(Color.parseColor("#D32F2F"))
            }
            else -> { // 여유로움
                holder.tvDday.text = "D-$dDay"
                holder.tvDday.setTextColor(Color.parseColor("#888888")) // 회색
            }
        }

        // 아이템 터치 시 수정 팝업
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = ingredientList.size

    // 날짜 차이를 계산하는 함수
    private fun calculateDday(expiryDateStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy.MM.dd", Locale.KOREA)
            val expiryDate = format.parse(expiryDateStr) ?: return -999

            // 오늘 날짜 (시간 정보 제거)
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)

            }.time

            val diff = expiryDate.time - today.time
            TimeUnit.MILLISECONDS.toDays(diff) // ★ 여기서 에러가 났던 부분 해결!
        } catch (e: Exception) {
            -999
        }
    }
}