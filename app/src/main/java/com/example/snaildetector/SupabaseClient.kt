package com.example.snaildetector

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

val supabase = createSupabaseClient(
    supabaseUrl = "https://kkpryrqirierlovvafnl.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtrcHJ5cnFpcmllcmxvdnZhZm5sIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY2NzkyNTMsImV4cCI6MjA5MjI1NTI1M30.vxYbej1g4ctlnHeOvbMJXhfqnCeOuaYaCD7C5NjIslg"
) {
    install(Auth)
    install(Postgrest)
    install(Realtime)
    install(Storage)
}