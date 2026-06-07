-keepattributes Signature,InnerClasses,EnclosingMethod,Exceptions

-keep class com.programaorcamentos.data.** { *; }

-keepclassmembers class com.programaorcamentos.data.** {
    <fields>;
}

-dontwarn javax.annotation.**
