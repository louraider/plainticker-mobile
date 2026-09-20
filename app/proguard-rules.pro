# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# ---- Debuggable release stack traces --------------------------------------------------------
# This file shipped with every rule below commented out until "the read" section on Detail went
# blank on a release build with a 200 already logged server-side (fix/read-section). The R8 audit
# that followed cleared kotlinx.serialization (see below) but found this: a release crash or a
# logged exception carries no file name and no line number, obfuscated names only. That is a real
# cost on its own, made worse because this app ships no crash reporter, so `adb logcat` against a
# real device is the only window into a release failure at all. Keep the two attributes that make
# a retraced stack trace (via mapping.txt and R8's retrace tool) readable.
-keepattributes SourceFile,LineNumberTable
# Left un-renamed on purpose: the real file name in a retraced trace is more useful than a
# generic "SourceFile" placeholder, and mapping.txt never ships inside the APK.

# ---- kotlinx.serialization ---------------------------------------------------------------------
# The dependency's own bundled consumer rules (kotlinx-serialization-common.pro and
# kotlinx-serialization-r8.pro, visible in app/build/outputs/mapping/release/configuration.txt)
# already keep every @Serializable class's Companion, its `serializer()`, its $$serializer's
# INSTANCE and descriptor, so this project's wire models (ReadModels, PlainTickerModels,
# XStocksModels, JupiterModels, NextUpModels, VoteModels, PassModels, EntitlementModels,
# RpcModels, and the on-disk snapshot/receipt models) were never actually unprotected. These
# rules are kept here anyway, scoped to this app's own package, so that contract is this
# project's to own rather than something that only holds as long as a transitive dependency's
# bundled rules keep matching whatever R8 version AGP ships next.
-keepattributes InnerClasses
-keep,includedescriptorclasses class com.plainticker.mobile.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.plainticker.mobile.** {
    static ** Companion;
}
-keepclasseswithmembers @kotlinx.serialization.Serializable class com.plainticker.mobile.** {
    kotlinx.serialization.KSerializer serializer(...);
}
