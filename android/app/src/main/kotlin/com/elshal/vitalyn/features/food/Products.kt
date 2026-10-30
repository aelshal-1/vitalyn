package com.elshal.vitalyn.features.food

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.elshal.vitalyn.LocalApp
import com.elshal.vitalyn.core.AITasks
import com.elshal.vitalyn.core.OpenFoodFacts
import com.elshal.vitalyn.core.ProductExtraction
import com.elshal.vitalyn.data.FoodProduct
import com.elshal.vitalyn.services.ImageTools
import com.elshal.vitalyn.services.userMessage
import com.elshal.vitalyn.ui.BiCaption
import com.elshal.vitalyn.ui.BiChip
import com.elshal.vitalyn.ui.BiLabel
import com.elshal.vitalyn.ui.BiText
import com.elshal.vitalyn.ui.Card
import com.elshal.vitalyn.ui.ErrorBanner
import com.elshal.vitalyn.ui.FullSheet
import com.elshal.vitalyn.ui.ImageSourceButtons
import com.elshal.vitalyn.ui.Ltr
import com.elshal.vitalyn.ui.NumberField
import com.elshal.vitalyn.ui.PrimaryButton
import com.elshal.vitalyn.ui.SectionTitle
import com.elshal.vitalyn.ui.SheetScaffold
import com.elshal.vitalyn.ui.VColor
import com.elshal.vitalyn.ui.VFont
import com.elshal.vitalyn.ui.VFormat
import com.elshal.vitalyn.ui.WorkingView
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class ProductCategory(val raw: String, val ar: String, val en: String) {
    ALL("all", "الكل", "All"),
    DAIRY("dairy", "ألبان", "Dairy"),
    GRAINS("grains", "حبوب وعيش", "Grains"),
    PROTEIN("protein", "بروتين", "Protein"),
    SNACKS("snacks", "سناكس", "Snacks"),
    DRINKS("drinks", "مشروبات", "Drinks"),
    OTHER("other", "تاني", "Other");

    companion object {
        fun fromRaw(raw: String?) = entries.firstOrNull { it.raw == raw }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProductsSection() {
    val app = LocalApp.current
    val state by app.store.state.collectAsState()
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ProductCategory.ALL) }
    var showLabelCapture by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FoodProduct?>(null) }
    var logging by remember { mutableStateOf<FoodProduct?>(null) }

    val filtered = state.sortedProducts.filter { product ->
        (category == ProductCategory.ALL || product.categoryRaw == category.raw) &&
            (search.isEmpty() || "${product.nameAr} ${product.nameEn} ${product.brand} ${product.barcode}".contains(search, ignoreCase = true))
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(onClick = { showScanner = true }, modifier = Modifier.weight(1f), color = VColor.night) {
                BiLabel("امسح باركود", "Scan barcode", icon = Icons.Rounded.QrCodeScanner, size = 14f)
            }
            PrimaryButton(onClick = { showLabelCapture = true }, modifier = Modifier.weight(1f)) {
                BiLabel("صوّر الليبل", "Snap label", icon = Icons.Rounded.PhotoCamera, size = 14f)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(VColor.surface, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = VColor.muted)
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                textStyle = VFont.style(14f, 400, VColor.ink),
                cursorBrush = SolidColor(VColor.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (search.isEmpty()) Text("ابحث باسم المنتج أو البراند · Search", style = VFont.style(14f, 400, VColor.muted))
                        inner()
                    }
                },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProductCategory.entries.forEach { item ->
                BiChip(item.ar, item.en, selected = category == item, dark = true) { category = item }
            }
        }

        if (filtered.isEmpty()) {
            BiText(
                "لسه مفيش منتجات. صوّر الليبل وجدول القيم الغذائية لأي منتج بتستخدمه.",
                "No products yet. Snap the label and nutrition table of anything you use.",
                modifier = Modifier.padding(vertical = 8.dp),
                size = 13f, weight = 400, color = VColor.inkSecondary,
            )
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(VColor.surface),
            ) {
                filtered.forEach { product ->
                    key(product.id) {
                        var menu by remember { mutableStateOf(false) }
                        Box {
                            ProductRow(
                                product = product,
                                modifier = Modifier.combinedClickable(
                                    onClick = { logging = product },
                                    onLongClick = { menu = true },
                                ),
                            )
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("سجّل أكلة · Log", style = VFont.style(14f, 500, VColor.ink)) },
                                    onClick = {
                                        menu = false
                                        logging = product
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("عدّل · Edit", style = VFont.style(14f, 500, VColor.ink)) },
                                    onClick = {
                                        menu = false
                                        editing = product
                                    },
                                )
                            }
                        }
                        HorizontalDivider(color = VColor.line)
                    }
                }
            }
        }
    }

    if (showLabelCapture) ProductCaptureSheet(barcode = null) { showLabelCapture = false }
    if (showScanner) BarcodeFlowSheet { showScanner = false }
    editing?.let { product -> ProductEditSheet(product) { editing = null } }
    logging?.let { product -> MealCaptureSheet(MealInput.Product(product)) { logging = null } }
}

@Composable
fun ProductRow(product: FoodProduct, modifier: Modifier = Modifier) {
    val photo = rememberFoodBlobBitmap(product.photo, 120)
    Row(
        modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(VColor.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(photo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.Inventory2, contentDescription = null, tint = VColor.primary)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(product.nameAr, style = VFont.style(14f, 500, VColor.ink))
            Text(
                product.nameEn + if (product.brand.isEmpty()) "" else " · ${product.brand}",
                style = VFont.style(11f, 400, VColor.inkSecondary),
            )
            Ltr {
                Text(
                    "100 g: ${product.kcalPer100.toInt()} kcal · P ${VFormat.decimal(product.proteinPer100)} g",
                    style = VFont.style(11f, 400, VColor.muted),
                )
            }
        }
        Icon(
            if (product.barcode.isEmpty()) Icons.Rounded.Description else Icons.Rounded.QrCode,
            contentDescription = null,
            tint = VColor.muted,
        )
    }
}

/** Snap the front and the nutrition table; Claude reads them into a product. `prefilled` comes from Open Food Facts. */
@Composable
fun ProductCaptureSheet(barcode: String?, prefilled: ProductExtraction? = null, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf(listOf<Bitmap>()) }
    var extraction by remember { mutableStateOf(prefilled) }
    var working by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun analyze() {
        val images = photos
        working = true
        error = null
        scope.launch {
            try {
                val jpegs = withContext(Dispatchers.Default) { images.map { ImageTools.jpeg(it, maxDimension = 1800, quality = 85) } }
                var result = app.ai.run(ProductExtraction.serializer(), AITasks.productFromLabel(jpegs))
                if (!barcode.isNullOrEmpty()) result = result.copy(barcode = barcode)
                extraction = result
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                error = e.userMessage()
            }
            working = false
        }
    }

    fun save(result: ProductExtraction, category: ProductCategory) {
        if (saving) return
        saving = true
        val first = photos.firstOrNull()
        scope.launch {
            val photoName = first?.let { bitmap ->
                val bytes = withContext(Dispatchers.Default) { ImageTools.jpeg(bitmap, maxDimension = 600, quality = 70) }
                withContext(Dispatchers.IO) { app.store.saveBlob(bytes, "jpg") }
            }
            val product = FoodProduct().applying(result).copy(
                categoryRaw = category.raw,
                sourceRaw = if (prefilled != null) "openfoodfacts" else "label",
                photo = photoName,
            )
            app.store.update { it.upsertProduct(product) }
            onDismiss()
        }
    }

    FullSheet(onDismiss) {
        SheetScaffold("منتج جديد", "New product", onClose = onDismiss) {
            val current = extraction
            if (current != null) {
                ProductForm(current, photos.firstOrNull()) { result, category -> save(result, category) }
            } else {
                BiText("صوّر واجهة المنتج وجدول القيم الغذائية", "Snap the front and the nutrition table", size = 18f)
                BiText(
                    "صورة أو اتنين أو تلاتة — Claude هيقرا الاسم والقيم ويحفظهم.",
                    "One to three photos — Claude reads the name and values and saves them.",
                    size = 13f, weight = 400, color = VColor.inkSecondary,
                )
                if (photos.isNotEmpty()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        photos.forEach { bitmap ->
                            Image(
                                bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 110.dp, height = 140.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                        }
                    }
                }
                if (photos.size < 3) {
                    ImageSourceButtons(
                        cameraAr = if (photos.isEmpty()) "صوّر الواجهة" else "صوّر الجدول",
                        cameraEn = if (photos.isEmpty()) "Front" else "Table",
                        onImage = { image -> if (photos.size < 3) photos = photos + image },
                    )
                }
                if (working) {
                    WorkingView("بقرا الليبل…", "Reading the label…")
                } else if (photos.isNotEmpty()) {
                    PrimaryButton(onClick = { analyze() }, color = VColor.night) {
                        BiLabel("اقرا الليبل", "Read label", icon = Icons.Rounded.AutoAwesome)
                    }
                }
            }
            error?.let { ErrorBanner(it) }
        }
    }
}

/** Editable product values. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductForm(
    initial: ProductExtraction,
    photo: Bitmap?,
    initialCategory: ProductCategory = ProductCategory.OTHER,
    onSave: (ProductExtraction, ProductCategory) -> Unit,
) {
    var extraction by remember { mutableStateOf(initial) }
    var category by remember { mutableStateOf(initialCategory) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        photo?.let { image ->
            Image(
                image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .heightIn(max = 180.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FormTextField("الاسم بالعربي", "Arabic name", extraction.nameAr) { extraction = extraction.copy(nameAr = it) }
                FormTextField("الاسم بالإنجليزي", "English name", extraction.nameEn) { extraction = extraction.copy(nameEn = it) }
                FormTextField("البراند", "Brand", extraction.brand ?: "") { extraction = extraction.copy(brand = it.ifEmpty { null }) }
                FormTextField("الباركود", "Barcode", extraction.barcode ?: "", number = true) {
                    extraction = extraction.copy(barcode = it.ifEmpty { null })
                }
                NumberField(
                    "حجم العبوة/الحصة", "Serving size",
                    value = extraction.servingSizeG ?: 0.0,
                    onValueChange = { extraction = extraction.copy(servingSizeG = if (it == 0.0) null else it) },
                    unit = "g", digits = 0,
                )
            }
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("القيم لكل 100 جم", "Per 100 g")
                val per100 = extraction.per100g
                NumberField(
                    "سعرات", "Calories", value = per100.kcal,
                    onValueChange = { extraction = extraction.copy(per100g = extraction.per100g.copy(kcal = it)) },
                    unit = "kcal", digits = 0,
                )
                NumberField(
                    "بروتين", "Protein", value = per100.proteinG,
                    onValueChange = { extraction = extraction.copy(per100g = extraction.per100g.copy(proteinG = it)) },
                    unit = "g",
                )
                NumberField(
                    "كارب", "Carbs", value = per100.carbsG,
                    onValueChange = { extraction = extraction.copy(per100g = extraction.per100g.copy(carbsG = it)) },
                    unit = "g",
                )
                NumberField(
                    "دهون", "Fat", value = per100.fatG,
                    onValueChange = { extraction = extraction.copy(per100g = extraction.per100g.copy(fatG = it)) },
                    unit = "g",
                )
                NumberField(
                    "صوديوم", "Sodium", value = per100.sodiumMg ?: 0.0,
                    onValueChange = { extraction = extraction.copy(per100g = extraction.per100g.copy(sodiumMg = if (it == 0.0) null else it)) },
                    unit = "mg", digits = 0,
                )
            }
        }
        Card {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BiCaption("التصنيف", "Category")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProductCategory.entries.filter { it != ProductCategory.ALL }.forEach { item ->
                        BiChip(item.ar, item.en, selected = category == item) { category = item }
                    }
                }
            }
        }
        PrimaryButton(onClick = { onSave(extraction, category) }) {
            BiLabel("احفظ في منتجاتي", "Save to my products", icon = Icons.Rounded.Check)
        }
    }
}

@Composable
private fun FormTextField(ar: String, en: String, value: String, number: Boolean = false, onValueChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BiCaption(ar, en)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = VFont.style(15f, 400, VColor.ink),
            cursorBrush = SolidColor(VColor.primary),
            keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .background(VColor.ground, RoundedCornerShape(10.dp))
                .padding(10.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text("$ar · $en", style = VFont.style(15f, 400, VColor.muted))
                    inner()
                }
            },
        )
    }
}

@Composable
fun ProductEditSheet(product: FoodProduct, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val photo = rememberFoodBlobBitmap(product.photo, 1200)
    FullSheet(onDismiss) {
        SheetScaffold(
            "عدّل المنتج", "Edit product",
            onClose = onDismiss,
            trailing = {
                Text(
                    "امسح · Delete",
                    style = VFont.style(14f, 500, VColor.energy),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            app.store.update { it.deleteProduct(product.id) }
                            app.store.deleteBlob(product.photo)
                            onDismiss()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            },
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            ProductForm(
                initial = ProductExtraction(
                    nameAr = product.nameAr,
                    nameEn = product.nameEn,
                    brand = product.brand,
                    barcode = product.barcode,
                    servingSizeG = product.servingSizeG,
                    servingDescAr = product.servingDescAr,
                    servingDescEn = product.servingDescEn,
                    per100g = product.per100g,
                ),
                photo = photo,
                initialCategory = ProductCategory.fromRaw(product.categoryRaw) ?: ProductCategory.OTHER,
            ) { result, category ->
                app.store.update { s ->
                    val stored = s.product(product.id) ?: return@update s
                    s.upsertProduct(stored.applying(result).copy(categoryRaw = category.raw))
                }
                onDismiss()
            }
        }
    }
}

// MARK: - Barcode

/** Scan → found in my products (log it) → found online (review & save) → not found (snap the label). */
@Composable
fun BarcodeFlowSheet(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf<String?>(null) }
    var looking by remember { mutableStateOf(false) }
    var online by remember { mutableStateOf<ProductExtraction?>(null) }
    var notFound by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf<FoodProduct?>(null) }

    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var cameraDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        cameraDenied = !granted
    }
    LaunchedEffect(Unit) {
        if (hasCamera && !cameraGranted) permission.launch(Manifest.permission.CAMERA)
    }

    fun found(value: String) {
        if (code != null) return
        code = value
        val match = app.store.value.products.firstOrNull { it.barcode == value }
        if (match != null) {
            local = match
            return
        }
        looking = true
        scope.launch {
            val result = try {
                OpenFoodFacts.lookup(value)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                null
            }
            looking = false
            if (result != null) online = result else notFound = true
        }
    }

    val localProduct = local
    val onlineProduct = online
    when {
        localProduct != null -> MealCaptureSheet(MealInput.Product(localProduct), onDismiss)
        onlineProduct != null -> ProductCaptureSheet(barcode = code, prefilled = onlineProduct, onDismiss = onDismiss)
        notFound -> ProductCaptureSheet(barcode = code, onDismiss = onDismiss)
        else -> FullSheet(onDismiss) {
            SheetScaffold(
                "امسح باركود", "Scan barcode",
                onClose = onDismiss,
                scroll = false,
                contentPadding = PaddingValues(0.dp),
            ) {
                when {
                    looking -> WorkingView("بدوّر على المنتج…", "Looking up the product…", Modifier.padding(20.dp))
                    hasCamera && !cameraDenied -> Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color.Black),
                    ) {
                        if (cameraGranted) BarcodeCameraScanner(onCode = { found(it) }, modifier = Modifier.fillMaxSize())
                        BiText(
                            "وجّه الكاميرا على الباركود", "Point the camera at the barcode",
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 40.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .padding(14.dp),
                            size = 15f, color = Color.White, enColor = Color.White,
                            alignment = Alignment.CenterHorizontally,
                        )
                    }
                    else -> ManualBarcodeEntry { found(it) }
                }
            }
        }
    }
}

@Composable
fun ManualBarcodeEntry(onCode: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BiText(
            "الكاميرا مش متاحة — اكتب الباركود", "Camera unavailable — type the barcode",
            size = 16f, alignment = Alignment.CenterHorizontally,
        )
        Ltr {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = VColor.ink, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(VColor.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(VColor.surface, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (text.isEmpty()) {
                            Text(
                                "6221234567890",
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = VColor.muted, textAlign = TextAlign.Center),
                            )
                        }
                        inner()
                    }
                },
            )
        }
        PrimaryButton(onClick = { onCode(text) }, enabled = text.length >= 6) {
            BiLabel("دوّر", "Look up")
        }
    }
}

/**
 * Live camera preview that reads product barcodes with ML Kit and reports the first one, once.
 * The caller must already hold the CAMERA permission.
 */
@Composable
fun BarcodeCameraScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_QR_CODE,
                )
                .build(),
        )
        val delivered = AtomicBoolean(false)
        val preview = Preview.Builder().build()
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        var provider: ProcessCameraProvider? = null
        var disposed = false

        analysis.setAnalyzer(
            executor,
            BarcodeAnalyzer(scanner, delivered) { value ->
                // Called on the main thread: stop the camera, then hand the code over.
                provider?.unbind(preview, analysis)
                currentOnCode(value)
            },
        )

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (disposed) return@addListener
            val cameraProvider = try {
                providerFuture.get()
            } catch (e: Exception) {
                Log.w("BarcodeScanner", "Camera unavailable", e)
                return@addListener
            }
            provider = cameraProvider
            preview.setSurfaceProvider(previewView.surfaceProvider)
            try {
                cameraProvider.unbind(preview, analysis)
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Log.w("BarcodeScanner", "Could not start the camera", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Feeds camera frames to ML Kit; reports the first non-empty barcode once (on the main thread). */
private class BarcodeAnalyzer(
    private val scanner: BarcodeScanner,
    private val delivered: AtomicBoolean,
    private val onCode: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null || delivered.get()) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstNotNullOfOrNull { barcode -> barcode.rawValue?.takeIf { it.isNotEmpty() } }
                if (value != null && delivered.compareAndSet(false, true)) onCode(value)
            }
            .addOnCompleteListener { image.close() }
    }
}
