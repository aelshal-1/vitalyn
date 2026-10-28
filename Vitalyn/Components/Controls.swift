import SwiftUI
import PhotosUI
import UIKit

/// Two-line bilingual label used inside buttons.
struct BiLabel: View {
    let ar: String
    let en: String
    var systemImage: String?
    var size: CGFloat = 15

    var body: some View {
        HStack(spacing: 10) {
            if let systemImage {
                Image(systemName: systemImage).font(.system(size: size + 3))
            }
            VStack(spacing: 0) {
                Text(ar).font(VFont.readex(size, weight: 600))
                Text(en).font(VFont.readex(max(9, size * 0.68))).opacity(0.85)
            }
        }
    }
}

struct PrimaryButtonStyle: ButtonStyle {
    var color: Color = VColor.primary

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 56)
            .padding(.horizontal, 16)
            .background(color.opacity(configuration.isPressed ? 0.85 : 1), in: Capsule())
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(VColor.ink)
            .frame(minHeight: 48)
            .padding(.horizontal, 16)
            .background(VColor.surface.opacity(configuration.isPressed ? 0.7 : 1), in: Capsule())
            .overlay(Capsule().stroke(VColor.line))
    }
}

/// Selectable bilingual chip.
struct BiChip: View {
    let ar: String
    let en: String
    let selected: Bool
    var dark: Bool = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 0) {
                Text(ar).font(VFont.readex(13))
                Text(en).font(VFont.readex(10)).opacity(0.85)
            }
            .padding(.horizontal, 12)
            .frame(minHeight: 46)
            .foregroundStyle(selected ? Color.white : VColor.ink)
            .background(selected ? (dark ? VColor.ink : VColor.primary) : VColor.surface, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(selected ? Color.clear : VColor.line))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}

struct ErrorBanner: View {
    let message: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(VColor.energy)
            Text(message).font(VFont.readex(13)).foregroundStyle(VColor.ink)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(hex: 0xFDE4DC), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

struct WorkingView: View {
    let ar: String
    let en: String

    var body: some View {
        VStack(spacing: 14) {
            ProgressView().controlSize(.large).tint(VColor.primary)
            BiText(ar: ar, en: en, size: 15, weight: 500, alignment: .center)
        }
        .padding(28)
        .frame(maxWidth: .infinity)
        .background(VColor.surface, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

struct SectionTitle: View {
    let ar: String
    let en: String

    var body: some View {
        BiText(ar: ar, en: en, size: 16)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Labelled number field with a unit.
struct NumberField: View {
    let ar: String
    let en: String
    @Binding var value: Double
    var unit: String = ""
    var digits: Int = 1

    var body: some View {
        HStack {
            BiText(ar: ar, en: en, size: 14, weight: 500)
            Spacer()
            TextField("0", value: $value, format: .number.precision(.fractionLength(0...digits)).locale(Locale(identifier: "en_US")))
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .frame(width: 90)
                .font(VFont.readex(16, weight: 600))
            if !unit.isEmpty {
                Text(unit).font(VFont.readex(12)).foregroundStyle(VColor.muted)
            }
        }
    }
}

// MARK: - Camera and photo library

struct CameraPicker: UIViewControllerRepresentable {
    let onImage: (UIImage) -> Void
    @Environment(\.dismiss) private var dismiss

    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = Self.isAvailable ? .camera : .photoLibrary
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ controller: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ parent: CameraPicker) { self.parent = parent }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if let image = info[.originalImage] as? UIImage { parent.onImage(image) }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}

/// "Camera" and "Photos" buttons side by side; delivers a UIImage.
struct ImageSourceButtons: View {
    var cameraAr = "صوّر"
    var cameraEn = "Camera"
    let onImage: (UIImage) -> Void

    @State private var showCamera = false
    @State private var pickerItem: PhotosPickerItem?

    var body: some View {
        HStack(spacing: 10) {
            Button {
                showCamera = true
            } label: {
                BiLabel(ar: cameraAr, en: cameraEn, systemImage: "camera")
            }
            .buttonStyle(PrimaryButtonStyle())

            PhotosPicker(selection: $pickerItem, matching: .images) {
                BiLabel(ar: "من الصور", en: "Photos", systemImage: "photo.on.rectangle")
                    .foregroundStyle(VColor.ink)
                    .frame(maxWidth: .infinity, minHeight: 56)
                    .background(VColor.surface, in: Capsule())
                    .overlay(Capsule().stroke(VColor.line))
            }
        }
        .sheet(isPresented: $showCamera) {
            CameraPicker(onImage: onImage).ignoresSafeArea()
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let image = UIImage(data: data) {
                    onImage(image)
                }
                pickerItem = nil
            }
        }
    }
}
