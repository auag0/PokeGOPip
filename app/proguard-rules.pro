-repackageclasses ""
-allowaccessmodification

-keep class io.github.auag0.pokegopip.Main

-assumenosideeffects public class android.util.Log {
    public static *** d(...);
    public static *** i(...);
}