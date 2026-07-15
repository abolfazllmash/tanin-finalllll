# Keep the Activity referenced from AndroidManifest
-keep class com.example.resonance.MainActivity { *; }

# Keep the custom View: it is inflated from XML by name via reflection
-keep class com.example.resonance.GameView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ViewBinding classes
-keep class com.example.resonance.databinding.** { *; }

# Kotlin metadata (safe default)
-keepattributes *Annotation*, InnerClasses, Signature
