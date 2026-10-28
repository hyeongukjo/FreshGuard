package com.example.freshguard

// object 키워드를 사용하면 앱 전체에서 딱 하나만 존재하는 전역 저장소가 됩니다.
object IngredientManager {
    // 앱이 처음 켜질 때 기본으로 보여줄 데이터들
    val itemList = mutableListOf(
        Ingredient(name = "서울우유", expiryDate = "2026.05.10", location = "냉장실", quantity = "1개"),
        Ingredient(name = "풀무원 찌개용 두부", expiryDate = "2026.05.07", location = "냉장실", quantity = "1모")
    )

    // 새로운 식재료를 추가하는 함수
    fun addIngredient(item: Ingredient) {
        itemList.add(0, item) // 최신 데이터가 맨 위에 오도록 0번째 인덱스에 추가
    }
}