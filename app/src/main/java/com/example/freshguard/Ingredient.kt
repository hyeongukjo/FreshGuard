package com.example.freshguard

import kotlinx.serialization.Serializable

// 이 어노테이션이 있어야 Supabase가 알아듣고 데이터를 DB에 넣을 수 있습니다.
@Serializable
data class Ingredient(
    val id: Int? = null,      // DB에서 자동으로 번호를 매겨주므로 Int? (비워둠)로 변경!
    val name: String,
    val expiryDate: String,
    val location: String,
    val quantity: String
)