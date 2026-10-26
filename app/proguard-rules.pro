# Room and Retrofit ship their own consumer rules. Do not keep the entire app.
-keepattributes Signature,InnerClasses,EnclosingMethod
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
