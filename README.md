# Vitalyn

تطبيق صحة شخصي لـ iPhone و iPad و Apple Watch (مع Widgets)، بيستخدم Claude API عشان يحلل InBody والأكل والنشاط ويقترح وجبات ورياضة ويتابعك يوم بيوم.

- الخطة الكاملة: [docs/PLAN.md](docs/PLAN.md)
- إعداد Apple + GitHub + TestFlight: [docs/SETUP.md](docs/SETUP.md)

## تركيبة المشروع

| المسار | المحتوى |
|---|---|
| `project.yml` | وصف مشروع Xcode (XcodeGen) — الـ `.xcodeproj` بيتولّد في CI ومش متخزن |
| `Vitalyn/` | تطبيق iPhone/iPad (SwiftUI) |
| `VitalynWidgets/` | الـ Widgets (WidgetKit) |
| `Shared/` | الألوان والخطوط والمكونات المشتركة بين التطبيق والـ Widgets |
| `Packages/VitalynCore/` | المنطق (حسابات السعرات والماكروز، بيانات اليوم) + الـ unit tests |
| `fastlane/` | البناء والرفع لـ TestFlight |
| `.github/workflows/` | `ci.yml` (build + tests على كل push) و `testflight.yml` (رفع على TestFlight) |

## البناء

مفيش Mac — كل البناء على GitHub Actions (macOS):

- **CI**: كل push ← `xcodegen generate` ← tests لـ VitalynCore ← build للـ Simulator.
- **TestFlight**: كل push على الـ branch الأساسي ← build موقّع ← TestFlight. بيتخطى لوحده لحد ما الـ secrets تتحط (شوف `docs/SETUP.md`).

لو معاك Mac في أي وقت:

```sh
brew install xcodegen
xcodegen generate
open Vitalyn.xcodeproj
```

الخط: [Readex Pro](https://fonts.google.com/specimen/Readex+Pro) — SIL Open Font License (`Vitalyn/Resources/Fonts/ReadexPro-OFL.txt`).
