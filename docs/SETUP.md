# إعداد Apple + GitHub عشان التطبيق يوصل موبايلك (من غير Mac)

الخطوات دي بتتعمل **مرة واحدة بس** من المتصفح. بعدها أي push على GitHub هيبني التطبيق ويرفعه على TestFlight لوحده.

> ⏱ الوقت المتوقع: حوالي 20 دقيقة.

---

## 1. الـ Team ID

1. افتح [developer.apple.com/account](https://developer.apple.com/account).
2. انزل لـ **Membership details**.
3. انسخ **Team ID** (10 حروف وأرقام، زي `AB12CD34EF`).

---

## 2. سجّل الـ Identifiers

افتح [Certificates, Identifiers & Profiles ← Identifiers](https://developer.apple.com/account/resources/identifiers/list) واعمل التلاتة دول بالترتيب من زرار **+**:

### أ) App Group
- اختار **App Groups** ← Continue.
- Description: `Vitalyn Group`
- Identifier: `group.com.elshal.vitalyn`

### ب) App ID للتطبيق
- اختار **App IDs** ← **App** ← Continue.
- Description: `Vitalyn`
- Bundle ID: **Explicit** ← `com.elshal.vitalyn`
- Capabilities: علّم على **HealthKit** و **App Groups**.
- بعد ما تحفظ، افتح الـ App ID تاني ← جنب App Groups دوس **Configure** ← اختار `group.com.elshal.vitalyn` ← Save.

### ج) App ID للـ Widgets
- نفس الخطوات: Description `Vitalyn Widgets`، Bundle ID `com.elshal.vitalyn.widgets`.
- Capabilities: **App Groups** بس، واربطه بنفس الـ group.

---

## 3. اعمل التطبيق في App Store Connect

1. افتح [App Store Connect ← Apps](https://appstoreconnect.apple.com/apps) ← **+** ← **New App**.
2. Platform: **iOS**.
3. Name: `Vitalyn` — لو الاسم مستخدم، جرّب `Vitalyn Health` أو `Vitalyn Coach`. (الاسم اللي تحت الأيقونة على موبايلك هيفضل Vitalyn.)
4. Primary Language: **Arabic**.
5. Bundle ID: اختار `com.elshal.vitalyn`.
6. SKU: `vitalyn`.
7. User Access: Full Access ← Create.

---

## 4. اعمل App Store Connect API Key

ده المفتاح اللي GitHub هيستخدمه عشان يوقّع التطبيق ويرفعه.

1. App Store Connect ← **Users and Access** ← **Integrations** ← **App Store Connect API** ← **Team Keys**.
2. لو أول مرة، دوس **Request Access** ووافق.
3. دوس **+** (Generate API Key):
   - Name: `GitHub Actions`
   - Access: **Admin** (لازم Admin عشان الـ signing الأوتوماتيك من غير Mac).
4. **نزّل ملف الـ `.p8`** — بيتنزل مرة واحدة بس، احتفظ بيه في مكان آمن.
5. انسخ **Key ID** (جنب المفتاح) و **Issuer ID** (فوق الجدول).

---

## 5. حط الأسرار في GitHub

1. افتح الـ repo على GitHub ← **Settings** ← **Secrets and variables** ← **Actions**.
2. دوس **New repository secret** واعمل الأربعة دول:

| الاسم | القيمة |
|---|---|
| `APPLE_TEAM_ID` | الـ Team ID من خطوة 1 |
| `ASC_KEY_ID` | الـ Key ID من خطوة 4 |
| `ASC_ISSUER_ID` | الـ Issuer ID من خطوة 4 |
| `ASC_KEY_P8` | افتح ملف `.p8` بأي Text editor وانسخ **كل** اللي فيه، بما فيهم سطر `-----BEGIN PRIVATE KEY-----` وسطر `-----END PRIVATE KEY-----` |

> 🔒 الـ secrets بتتخزن مشفرة، ومحدش يقدر يشوفها حتى لو الـ repo Public. الـ workflow بتاع TestFlight بيشتغل بس على push منك، مش على Pull Requests من برة.

---

## 6. TestFlight على أجهزتك

1. نزّل تطبيق **TestFlight** من الـ App Store على الآيفون والآيباد.
2. في App Store Connect ← التطبيق ← **TestFlight** ← **Internal Testing** ← **+** اعمل جروب واسمه مثلاً `Me` وضيف إيميلك.

---

## 7. شغّل أول build

1. GitHub ← **Actions** ← **TestFlight** ← **Run workflow**.
2. البناء بياخد حوالي 10–15 دقيقة، وبعدها Apple بتعالج الـ build من 5 لـ 30 دقيقة.
3. هيوصلك إيميل من TestFlight ← افتح التطبيق على الموبايل ← **Install**.

من بعدها، أي تحديث بيتعمل push هيوصلك build جديد لوحده.

---

## لو حاجة فشلت

- افتح الـ run في **Actions** وابعتلي اسم الخطوة اللي فشلت — أنا أقدر أقرا الـ logs وأصلّحها.
- أشهر الأسباب: نسخة `.p8` ناقصة سطر، أو الـ API Key مش Admin، أو الـ Bundle ID مكتوب غلط.
