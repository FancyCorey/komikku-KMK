package exh.recs.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// KMK -->

class RecommendationBundleValidatorTest {

    private fun validJson(
        schema: String = RecommendationBundle.SCHEMA_ID,
        version: Int = RecommendationBundle.SCHEMA_VERSION,
        itemCount: Int = 1,
    ): String {
        val items = (1..itemCount).joinToString(",") { i ->
            """{"title":"Manga $i","url":"/manga/$i","sourceId":1}"""
        }
        return """
            {
                "schema":"$schema",
                "schemaVersion":$version,
                "kmkRecsVersion":"v0.7.5",
                "createdAt":1000000,
                "title":"Test Bundle",
                "bundleType":"TOP_PICKS",
                "items":[$items]
            }
        """.trimIndent()
    }

    @Test
    fun `valid bundle passes validation`() {
        val result = RecommendationBundleValidator.validate(validJson())
        assertInstanceOf(RecommendationBundleValidator.ValidationResult.Valid::class.java, result)
    }

    @Test
    fun `wrong schema id is rejected`() {
        val result = RecommendationBundleValidator.validate(validJson(schema = "some.other.schema"))
        assertInstanceOf(RecommendationBundleValidator.ValidationResult.WrongSchema::class.java, result)
        assertEquals("some.other.schema", (result as RecommendationBundleValidator.ValidationResult.WrongSchema).found)
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val result = RecommendationBundleValidator.validate(validJson(version = 99))
        assertInstanceOf(RecommendationBundleValidator.ValidationResult.UnsupportedVersion::class.java, result)
        assertEquals(99, (result as RecommendationBundleValidator.ValidationResult.UnsupportedVersion).found)
    }

    @Test
    fun `too many items is rejected`() {
        val result = RecommendationBundleValidator.validate(validJson(itemCount = RecommendationBundleValidator.MAX_ITEMS + 1))
        assertEquals(RecommendationBundleValidator.ValidationResult.TooManyItems, result)
    }

    @Test
    fun `exactly max items is accepted`() {
        val result = RecommendationBundleValidator.validate(validJson(itemCount = RecommendationBundleValidator.MAX_ITEMS))
        assertInstanceOf(RecommendationBundleValidator.ValidationResult.Valid::class.java, result)
    }

    @Test
    fun `file too large is rejected`() {
        val result = RecommendationBundleValidator.validate(
            validJson(),
            fileSizeBytes = RecommendationBundleValidator.MAX_FILE_SIZE_BYTES + 1,
        )
        assertEquals(RecommendationBundleValidator.ValidationResult.FileTooLarge, result)
    }

    @Test
    fun `malformed json is rejected`() {
        val result = RecommendationBundleValidator.validate("{ not valid json !!!}")
        assertInstanceOf(RecommendationBundleValidator.ValidationResult.MalformedJson::class.java, result)
    }

    @Test
    fun `empty string is rejected as malformed json`() {
        val result = RecommendationBundleValidator.validate("")
        assertInstanceOf(RecommendationBundleValidator.ValidationResult.MalformedJson::class.java, result)
    }

    @Test
    fun `valid bundle carries through parsed bundle`() {
        val result = RecommendationBundleValidator.validate(validJson(itemCount = 3))
        val valid = result as RecommendationBundleValidator.ValidationResult.Valid
        assertEquals(3, valid.bundle.items.size)
        assertEquals("Test Bundle", valid.bundle.title)
    }

    @Test
    fun `too many sources is rejected`() {
        val sources = (1..RecommendationBundleValidator.MAX_SOURCES + 1).joinToString(",") { i ->
            """{"sourceId":$i,"sourceName":"Source $i"}"""
        }
        val json = """
            {
                "schema":"${RecommendationBundle.SCHEMA_ID}",
                "schemaVersion":${RecommendationBundle.SCHEMA_VERSION},
                "kmkRecsVersion":"v0.7.5",
                "createdAt":1000000,
                "title":"Test",
                "bundleType":"TOP_PICKS",
                "requiredSources":[$sources],
                "items":[]
            }
        """.trimIndent()
        val result = RecommendationBundleValidator.validate(json)
        assertEquals(RecommendationBundleValidator.ValidationResult.TooManySources, result)
    }

    @Test
    fun `unknown keys in valid json are tolerated`() {
        val json = """
            {
                "schema":"${RecommendationBundle.SCHEMA_ID}",
                "schemaVersion":${RecommendationBundle.SCHEMA_VERSION},
                "kmkRecsVersion":"v0.7.5",
                "createdAt":1000000,
                "title":"Test",
                "bundleType":"TOP_PICKS",
                "items":[],
                "futureKey":"future_value"
            }
        """.trimIndent()
        val result = RecommendationBundleValidator.validate(json)
        assertTrue(result is RecommendationBundleValidator.ValidationResult.Valid)
    }
}

// KMK <--
