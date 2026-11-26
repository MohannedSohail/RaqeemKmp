package org.mohtech.app.feature.receipts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Phase3ReceiptStorageTest {

    private lateinit var tempSandbox: File

    @BeforeTest
    fun setUp() {
        tempSandbox = Files.createTempDirectory("raqeem_receipt_test_").toFile()
        PlatformReceiptStorage.testSandboxDirOverride = tempSandbox
    }

    @AfterTest
    fun tearDown() {
        PlatformReceiptStorage.testSandboxDirOverride = null
        tempSandbox.deleteRecursively()
    }

    private fun createSyntheticPngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            for (y in 0 until height step 20) {
                g.color = Color((y * 13) % 255, (y * 37) % 255, (y * 71) % 255)
                g.fillRect(0, y, width, 20)
            }
            for (x in 0 until width step 40) {
                g.color = Color((x * 19) % 255, 40, 90)
                g.drawLine(x, 0, width - x, height)
            }
        } finally {
            g.dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    @Test
    fun compressAndStoreReceiptBytesDownscalesOversizedImageAndReducesSize() = runTest {
        val rawPngBytes = createSyntheticPngBytes(width = 2400, height = 1800)

        val storedUri = PlatformReceiptStorage.compressAndStoreReceiptBytes("receipt_oversized", rawPngBytes)
        assertNotNull(storedUri, "Expected stored file path for valid oversized PNG")

        val storedFile = File(storedUri)
        assertTrue(storedFile.exists(), "Compressed receipt file must exist")
        assertEquals("receipt_oversized.jpg", storedFile.name)
        assertTrue(
            storedFile.length() < rawPngBytes.size,
            "Compressed JPEG (${storedFile.length()} B) must be smaller than input PNG (${rawPngBytes.size} B)"
        )

        val decoded = ImageIO.read(storedFile)
        assertNotNull(decoded, "Stored JPEG must decode cleanly")
        assertEquals(1280, decoded.width, "Longest edge must be downscaled to 1280px")
        assertEquals(960, decoded.height, "Aspect ratio 4:3 must preserve height 960px")
        assertTrue(decoded.width <= 1280 && decoded.height <= 1280)
    }

    @Test
    fun compressAndStoreReceiptBytesOverwritesDeterministicallyWithoutTmpArtifacts() = runTest {
        val firstBytes = createSyntheticPngBytes(width = 1600, height = 1200)
        val secondBytes = createSyntheticPngBytes(width = 1000, height = 800)

        val firstPath = PlatformReceiptStorage.compressAndStoreReceiptBytes("bill_42", firstBytes)
        val secondPath = PlatformReceiptStorage.compressAndStoreReceiptBytes("bill_42", secondBytes)

        assertNotNull(firstPath)
        assertNotNull(secondPath)
        assertEquals(firstPath, secondPath, "Repeated save for same key must overwrite single file URI")

        val filesInSandbox = tempSandbox.listFiles().orEmpty()
        assertEquals(1, filesInSandbox.size, "Sandbox must contain exactly 1 file (no duplicates or .tmp files)")
        assertEquals("bill_42.jpg", filesInSandbox.single().name)
        assertFalse(filesInSandbox.any { it.name.endsWith(".tmp") }, "No leftover .tmp files allowed")

        val decoded = ImageIO.read(filesInSandbox.single())
        assertNotNull(decoded)
        assertEquals(1000, decoded.width)
        assertEquals(800, decoded.height)
    }

    @Test
    fun loadReceiptThumbnailDecodesBoundedImageBitmap() = runTest {
        val rawPngBytes = createSyntheticPngBytes(width = 2000, height = 1000)
        val storedUri = PlatformReceiptStorage.compressAndStoreReceiptBytes("thumb_case", rawPngBytes)
        assertNotNull(storedUri)

        val thumbnail = PlatformReceiptStorage.loadReceiptThumbnail(storedUri, maxDimensionPx = 512)
        assertNotNull(thumbnail, "Expected non-null ImageBitmap thumbnail")
        assertTrue(maxOf(thumbnail.width, thumbnail.height) <= 512, "Thumbnail max dimension must be <= 512px")
        assertEquals(512, thumbnail.width)
        assertEquals(256, thumbnail.height)
    }

    @Test
    fun receiptExistsAndDeleteReceiptManageLifecycleSafely() = runTest {
        val rawPngBytes = createSyntheticPngBytes(width = 800, height = 600)
        val storedUri = PlatformReceiptStorage.compressAndStoreReceiptBytes("delete_case", rawPngBytes)
        assertNotNull(storedUri)

        assertTrue(PlatformReceiptStorage.receiptExists(storedUri))
        assertTrue(PlatformReceiptStorage.deleteReceipt(storedUri))
        assertFalse(PlatformReceiptStorage.receiptExists(storedUri))
        assertFalse(PlatformReceiptStorage.deleteReceipt(storedUri))
        assertFalse(PlatformReceiptStorage.receiptExists(""))
        assertFalse(PlatformReceiptStorage.deleteReceipt(""))
    }

    @Test
    fun sanitizeReceiptKeyPreventsPathTraversalAndKeepsFileInsideSandbox() = runTest {
        val rawPngBytes = createSyntheticPngBytes(width = 640, height = 480)

        val traversal1 = PlatformReceiptStorage.compressAndStoreReceiptBytes("../../evil/secret", rawPngBytes)
        val traversal2 = PlatformReceiptStorage.compressAndStoreReceiptBytes("..\\foo/bar", rawPngBytes)
        val blankFallback = PlatformReceiptStorage.compressAndStoreReceiptBytes("../../../", rawPngBytes)

        assertNotNull(traversal1)
        assertNotNull(traversal2)
        assertNotNull(blankFallback)

        val sandboxCanonical = tempSandbox.canonicalFile
        listOf(traversal1, traversal2, blankFallback).forEach { path ->
            val file = File(path).canonicalFile
            assertEquals(sandboxCanonical, file.parentFile, "File $file must stay strictly inside sandbox $sandboxCanonical")
        }
        assertEquals("evil_secret.jpg", File(traversal1).name)
        assertEquals("foo_bar.jpg", File(traversal2).name)
        assertEquals("receipt.jpg", File(blankFallback).name)
    }

    @Test
    fun corruptOrEmptyBytesReturnNullWithoutWritingFiles() = runTest {
        val emptyResult = PlatformReceiptStorage.compressAndStoreReceiptBytes("empty_key", byteArrayOf())
        val corruptResult = PlatformReceiptStorage.compressAndStoreReceiptBytes(
            "corrupt_key",
            "not a valid image payload".encodeToByteArray()
        )
        val missingThumb = PlatformReceiptStorage.loadReceiptThumbnail(File(tempSandbox, "missing.jpg").absolutePath)

        assertNull(emptyResult)
        assertNull(corruptResult)
        assertNull(missingThumb)
        assertEquals(0, tempSandbox.listFiles().orEmpty().size, "No files may be created on empty/corrupt inputs")
    }

    private fun createSyntheticJpegBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        try {
            g.color = Color(24, 32, 50)
            g.fillRect(0, 0, width, height)
            g.color = Color(0, 196, 140)
            g.fillRect(width / 4, height / 4, (width / 2).coerceAtLeast(1), (height / 2).coerceAtLeast(1))
        } finally {
            g.dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    private fun createTransparentPngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.composite = AlphaComposite.Clear
            g.fillRect(0, 0, width, height)
            g.composite = AlphaComposite.SrcOver
            g.color = Color(0, 196, 140, 255)
            g.fillRect(width / 4, height / 4, width / 2, height / 2)
        } finally {
            g.dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    @Test
    fun extremeAspectRatiosAndSmallImagesRespectBoundsWithoutUpscaling() = runTest {
        val cases = listOf(
            Triple("wide_4000x200", 4000 to 200, 1280 to 64),
            Triple("tall_200x4000", 200 to 4000, 64 to 1280),
            Triple("panoramic_4000x1", 4000 to 1, 1280 to 1),
            Triple("small_320x240", 320 to 240, 320 to 240)
        )

        for ((key, inputDim, expectedDim) in cases) {
            val pngBytes = createSyntheticPngBytes(width = inputDim.first, height = inputDim.second)
            val storedPath = PlatformReceiptStorage.compressAndStoreReceiptBytes(key, pngBytes)
            assertNotNull(storedPath, "Expected stored file for $key")

            val decoded = ImageIO.read(File(storedPath))
            assertNotNull(decoded, "Stored JPEG for $key must decode cleanly")
            assertEquals(expectedDim.first, decoded.width, "Unexpected width for $key")
            assertEquals(expectedDim.second, decoded.height, "Unexpected height for $key")
        }
    }

    @Test
    fun transparentRgbaPngCompositesOntoWhiteBackground() = runTest {
        val rgbaPng = createTransparentPngBytes(width = 400, height = 400)
        val storedPath = PlatformReceiptStorage.compressAndStoreReceiptBytes("rgba_transparent", rgbaPng)
        assertNotNull(storedPath)

        val decoded = ImageIO.read(File(storedPath))
        assertNotNull(decoded)
        assertFalse(decoded.colorModel.hasAlpha(), "Stored JPEG must not have alpha channel")

        val corners = listOf(0 to 0, 399 to 0, 0 to 399, 399 to 399)
        for ((cx, cy) in corners) {
            val rgb = decoded.getRGB(cx, cy)
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            assertTrue(
                r >= 250 && g >= 250 && b >= 250,
                "Transparent corner ($cx,$cy) must composite onto white, got RGB($r,$g,$b)"
            )
        }

        val centerRgb = decoded.getRGB(200, 200)
        val centerR = (centerRgb shr 16) and 0xFF
        val centerG = (centerRgb shr 8) and 0xFF
        assertTrue(centerG > centerR + 50, "Center region should preserve colored foreground")
    }

    @Test
    fun corruptTruncatedAndZeroBytePayloadsReturnNullWithoutLeftovers() = runTest {
        val validPng = createSyntheticPngBytes(width = 300, height = 200)
        val validJpg = createSyntheticJpegBytes(width = 300, height = 200)
        val randomBytes = ByteArray(64) { idx -> ((idx * 73 + 19) and 0xFF).toByte() }
        val truncatedPng = validPng.copyOfRange(0, validPng.size / 2)
        val truncatedJpg = validJpg.copyOfRange(0, minOf(64, validJpg.size / 4))

        assertNull(PlatformReceiptStorage.compressAndStoreReceiptBytes("zero_bytes", byteArrayOf()))
        assertNull(PlatformReceiptStorage.compressAndStoreReceiptBytes("random_bytes", randomBytes))
        assertNull(PlatformReceiptStorage.compressAndStoreReceiptBytes("truncated_png", truncatedPng))
        assertNull(PlatformReceiptStorage.compressAndStoreReceiptBytes("truncated_jpg", truncatedJpg))

        val sandboxFiles = tempSandbox.listFiles().orEmpty()
        assertEquals(0, sandboxFiles.size, "No files or .tmp leftovers may exist after corrupt inputs")
    }

    @Test
    fun maliciousArabicAndWindowsReservedKeysStayUniqueDeterministicAndContained() = runTest {
        val rawPng = createSyntheticPngBytes(width = 200, height = 150)
        val sandboxCanonical = tempSandbox.canonicalFile

        val evil1 = PlatformReceiptStorage.compressAndStoreReceiptBytes("../../evil/secret", rawPng)
        val evil2 = PlatformReceiptStorage.compressAndStoreReceiptBytes("..\\foo/bar", rawPng)
        val dots = PlatformReceiptStorage.compressAndStoreReceiptBytes("../../../", rawPng)
        val arabic1 = PlatformReceiptStorage.compressAndStoreReceiptBytes("إيصال_١", rawPng)
        val arabic1Repeat = PlatformReceiptStorage.compressAndStoreReceiptBytes("إيصال_١", rawPng)
        val arabic2 = PlatformReceiptStorage.compressAndStoreReceiptBytes("إيصال_٢", rawPng)
        val longKey = PlatformReceiptStorage.compressAndStoreReceiptBytes("a".repeat(250), rawPng)
        val nullByteKey = PlatformReceiptStorage.compressAndStoreReceiptBytes("bad\u0000key", rawPng)
        val reservedCon = PlatformReceiptStorage.compressAndStoreReceiptBytes("CON", rawPng)
        val allDashes = PlatformReceiptStorage.compressAndStoreReceiptBytes("---", rawPng)

        val allPaths = listOfNotNull(
            evil1, evil2, dots, arabic1, arabic1Repeat, arabic2, longKey, nullByteKey, reservedCon, allDashes
        )
        assertEquals(10, allPaths.size)
        allPaths.forEach { path ->
            val file = File(path).canonicalFile
            assertEquals(sandboxCanonical, file.parentFile, "File $file escaped sandbox $sandboxCanonical")
            assertTrue(file.nameWithoutExtension.length <= 80, "Stem length exceeded 80: ${file.name}")
            assertTrue(file.nameWithoutExtension.matches(Regex("^[A-Za-z0-9_-]+$")), "Non-ASCII/unsafe stem: ${file.name}")
        }

        assertEquals("evil_secret.jpg", File(evil1!!).name)
        assertEquals("foo_bar.jpg", File(evil2!!).name)
        assertEquals("receipt.jpg", File(dots!!).name)
        assertEquals("bad_key.jpg", File(nullByteKey!!).name)
        assertEquals("r_CON.jpg", File(reservedCon!!).name)
        assertEquals("receipt.jpg", File(allDashes!!).name)

        assertEquals(arabic1, arabic1Repeat, "Arabic receipt key sanitization must be deterministic")
        assertNotEquals(arabic1, arabic2, "Distinct Arabic receipt keys must not collide")
        assertNotEquals("receipt.jpg", File(arabic1!!).name)
        assertNotEquals("receipt.jpg", File(arabic2!!).name)

        assertEquals("r_prn", sanitizeReceiptKey("prn"))
        assertEquals("r_LPT9", sanitizeReceiptKey("LPT9"))
        assertTrue(sanitizeReceiptKey("a".repeat(250) + "_إيصال").length <= 80)
    }

    @Test
    fun concurrentSameKeyOverwritesProduceSingleValidJpegWithoutTmpLeftovers() = runTest {
        val payloads = (0 until 16).map { index ->
            createSyntheticPngBytes(width = 400 + index * 10, height = 300 + index * 10)
        }

        val results = payloads.map { bytes ->
            async(Dispatchers.IO) {
                PlatformReceiptStorage.compressAndStoreReceiptBytes("concurrent_key", bytes)
            }
        }.awaitAll()

        assertTrue(results.all { it != null }, "All concurrent overwrites should succeed cleanly")
        val expectedFile = File(tempSandbox, "concurrent_key.jpg").canonicalFile
        assertTrue(results.all { File(it!!).canonicalFile == expectedFile })

        val filesInSandbox = tempSandbox.listFiles().orEmpty()
        assertEquals(1, filesInSandbox.size, "Exactly one file must exist after concurrent overwrites")
        assertEquals("concurrent_key.jpg", filesInSandbox.single().name)
        assertFalse(filesInSandbox.any { it.name.endsWith(".tmp") })

        val decoded = ImageIO.read(filesInSandbox.single())
        assertNotNull(decoded, "Final JPEG after concurrent writes must decode cleanly")
        assertTrue(decoded.width >= 400 && decoded.height >= 300)
    }

    @Test
    fun thumbnailAndLifecycleEdgeCasesHandleMissingInvalidDirectoriesAndBounds() = runTest {
        val missingFile = File(tempSandbox, "missing_receipt.jpg")
        val emptyFile = File(tempSandbox, "zero_byte.jpg").apply { writeBytes(byteArrayOf()) }
        val textFile = File(tempSandbox, "fake.txt").apply { writeText("hello world") }

        assertNull(PlatformReceiptStorage.loadReceiptThumbnail(missingFile.absolutePath))
        assertNull(PlatformReceiptStorage.loadReceiptThumbnail(emptyFile.absolutePath))
        assertNull(PlatformReceiptStorage.loadReceiptThumbnail(textFile.absolutePath))

        assertFalse(PlatformReceiptStorage.receiptExists(emptyFile.absolutePath))
        assertFalse(PlatformReceiptStorage.receiptExists(tempSandbox.absolutePath))
        assertFalse(PlatformReceiptStorage.deleteReceipt(tempSandbox.absolutePath))
        assertTrue(tempSandbox.exists() && tempSandbox.isDirectory, "Directory must never be deleted")

        val storedPath = PlatformReceiptStorage.compressAndStoreReceiptBytes(
            "thumb_bounds",
            createSyntheticPngBytes(width = 1000, height = 500)
        )
        assertNotNull(storedPath)

        val smallThumb = PlatformReceiptStorage.loadReceiptThumbnail(storedPath, maxDimensionPx = 128)
        assertNotNull(smallThumb)
        assertEquals(128, smallThumb.width)
        assertEquals(64, smallThumb.height)

        val unscaledThumb = PlatformReceiptStorage.loadReceiptThumbnail(storedPath, maxDimensionPx = 2048)
        assertNotNull(unscaledThumb)
        assertEquals(1000, unscaledThumb.width)
        assertEquals(500, unscaledThumb.height)

        assertTrue(PlatformReceiptStorage.receiptExists(storedPath))
        assertTrue(PlatformReceiptStorage.deleteReceipt(storedPath))
        assertFalse(PlatformReceiptStorage.receiptExists(storedPath))
        assertFalse(PlatformReceiptStorage.deleteReceipt(storedPath))
    }
}
