package com.example.freshguard

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

// object로 만들어서 앱 전체에서 하나의 연결만 사용하도록 합니다.
object SupabaseManager {

    // 접속 정보는 local.properties → BuildConfig 로 주입됩니다. (키를 코드에 직접 넣지 않음)
    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_KEY = BuildConfig.SUPABASE_KEY

    // Supabase 클라이언트 (이제 이 client를 통해 데이터를 넣고 뺄 수 있습니다)
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_KEY
    ) {
        install(Postgrest) // 데이터베이스(테이블) 기능을 사용하겠다는 뜻
    }
}