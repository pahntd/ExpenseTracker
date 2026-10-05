# App-specific R8 rules for the release build.
#
# Most libraries here ship their own consumer rules, which R8 applies automatically:
# Gson (META-INF/proguard/gson.pro), Retrofit, Room, WorkManager, hilt-work, Tink, OkHttp.
# Only add a rule below when the app needs something those rules do not cover.

# --- Gson DTOs (Retrofit GsonConverterFactory) ---
# Gson's bundled rules keep @SerializedName fields of referenced classes. The response DTOs are
# Kotlin data classes with no no-args constructor, so Gson creates them via Unsafe and app code never
# calls their constructors. Under R8 full mode such classes can be treated as never instantiated
# (made abstract / field reads folded), so keep their constructors and annotated fields. Names may
# still be obfuscated: every field carries an explicit @SerializedName.
-keep,allowobfuscation class com.pahntd.expensetracker.data.remote.dto.** {
    <init>(...);
    @com.google.gson.annotations.SerializedName <fields>;
}

# --- Proto DataStore (protobuf-javalite) ---
# Lite runtime resolves message fields reflectively by their Java names (e.g. "accessToken_"), and
# protobuf-javalite 3.25.x ships no consumer rules. Scoped to the generated session message package.
-keepclassmembers class com.pahntd.expensetracker.data.auth.session.** extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
