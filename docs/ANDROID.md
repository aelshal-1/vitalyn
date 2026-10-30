# Vitalyn على Android

نفس تطبيق الآيفون بالظبط (Kotlin + Jetpack Compose)، في فولدر `android/`. مفيش حاجة محتاجة Mac ولا Android Studio — كل البناء على GitHub Actions.

## تنزيل التطبيق على الموبايل

1. افتح [Actions ← Android](https://github.com/aelshal-1/vitalyn/actions/workflows/android.yml) واختار آخر run عليه ✓ أخضر.
2. تحت **Artifacts** نزّل `Vitalyn-android-<رقم>` (ملف zip فيه `Vitalyn-<رقم>.apk`).
3. على الموبايل: فك الـ zip وافتح الـ APK ← لو طلب، اسمح بـ **Install unknown apps** للتطبيق اللي فتحت منه الملف (Files أو Chrome).
4. كل build جديد بيتسطب فوق القديم وبياناتك بتفضل زي ما هي.

> أقل نسخة: Android 9. الـ Health Connect جوه Android 14 وأحدث؛ على Android 13 وأقدم التطبيق هيفتحلك صفحته على Google Play تسطبه.

## الفرق عن الآيفون

| الآيفون | Android |
|---|---|
| Apple Health | **Health Connect** (الخطوات، السعرات النشطة، المسافة، النبض، النوم، الوزن، الدهون، التمارين — قراءة وكتابة) |
| Widgets (WidgetKit) | Widgets على الـ Home screen: سعرات وماكروز، التزام، خطوات، مية (بزرار + كوباية)، رحلتي |
| Keychain | مفتاح Claude متخزن مشفّر بالـ Android Keystore |
| الرد في الخلفية (Background URLSession) | WorkManager — الجدول الأسبوعي ورد المدرب بيكملوا لو قفلت التطبيق، ويوصلك إشعار |
| RealityKit / SceneKit | OpenGL ES — نفس الموديل والـ blend shapes |
| Vision (فحص الصور) | ML Kit (الوش والجسم والباركود) |
| Apple Watch | مش موجود على Android |

تسجيل الصوت بيستخدم التعرف على الكلام بتاع الموبايل (Google) بالمصري، والرد بيتقري بـ Text-to-Speech.

## التوقيع (اختياري)

من غير أي إعداد، الـ CI بيعمل مفتاح توقيع مرة واحدة ويحفظه في الـ Actions cache، فكل APK بيتسطب فوق اللي قبله. لو الـ cache اتمسح (بعد 7 أيام من غير أي build) هيتعمل مفتاح جديد ووقتها لازم تمسح التطبيق وتسطبه تاني.

عشان مفتاح ثابت للأبد، اعمل keystore مرة واحدة وحطه في GitHub Secrets:

```sh
keytool -genkeypair -keystore vitalyn.keystore -alias vitalyn -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 vitalyn.keystore   # على Mac: base64 -i vitalyn.keystore
```

Settings ← Secrets and variables ← Actions ← New repository secret:

- `ANDROID_KEYSTORE_BASE64` — ناتج الأمر اللي فوق
- `ANDROID_KEYSTORE_PASSWORD` — باسورد الـ keystore
- `ANDROID_KEY_ALIAS` — `vitalyn`
- `ANDROID_KEY_PASSWORD` — باسورد المفتاح (غالباً نفس الباسورد)

احتفظ بالـ keystore في مكان آمن؛ لو ضاع مش هتقدر تحدّث التطبيق من غير ما تمسحه.

## التركيبة

| المسار | المحتوى |
|---|---|
| `android/core` | نسخة Kotlin من `VitalynCore` (Claude client، الـ prompts، الحسابات، الجدول، الموديل 3D) + كل الـ tests — بتتبني وتتختبر على أي JVM |
| `android/app` | التطبيق: `data/` (التخزين)، `services/` (Claude، Health Connect، الإشعارات، الصوت، الكاميرا)، `ui/` (الألوان والمكونات)، `features/` (الشاشات)، `widget/` |
| `.github/workflows/android.yml` | tests للـ core ← APK موقّع ← Artifact |

لو معاك Android Studio: افتح فولدر `android/` وشغّل `app`.
