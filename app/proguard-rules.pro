-repackageclasses ""
-allowaccessmodification

-keep class pokego.pip.Main

-assumenosideeffects public class android.util.Log {
    public static *** d(...);
    public static *** i(...);
}