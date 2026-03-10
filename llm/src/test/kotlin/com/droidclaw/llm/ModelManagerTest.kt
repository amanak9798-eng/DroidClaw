package com.droidclaw.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var modelManager: ModelManager

    @Before
    fun setUp() {
        modelManager = ModelManager(tempFolder.root)
    }

    @Test
    fun isModelCached_returnsFalse_whenNotDownloaded() {
        assertFalse(modelManager.isModelCached("test-model"))
    }

    @Test
    fun isModelCached_returnsTrue_whenFileExists() {
        File(tempFolder.root, "test-model.gguf").writeText("fake model data")
        assertTrue(modelManager.isModelCached("test-model"))
    }

    @Test
    fun getModelPath_returnsNull_whenNotDownloaded() {
        assertNull(modelManager.getModelPath("nonexistent"))
    }

    @Test
    fun getModelPath_returnsFile_whenExists() {
        val file = File(tempFolder.root, "my-model.gguf")
        file.writeText("model content")
        val result = modelManager.getModelPath("my-model")
        assertNotNull(result)
        assertEquals(file.absolutePath, result!!.absolutePath)
    }

    @Test
    fun getDownloadedModels_returnsEmpty_whenNoModels() {
        val models = modelManager.getDownloadedModels()
        assertTrue(models.isEmpty())
    }

    @Test
    fun getDownloadedModels_listsGgufFiles() {
        File(tempFolder.root, "model-a.gguf").writeText("aaa")
        File(tempFolder.root, "model-b.gguf").writeText("bbbbb")
        File(tempFolder.root, "not-a-model.txt").writeText("skip me")

        val models = modelManager.getDownloadedModels()
        assertEquals(2, models.size)

        val names = models.map { it.modelId }.toSet()
        assertTrue(names.contains("model-a"))
        assertTrue(names.contains("model-b"))
    }

    @Test
    fun deleteModel_removesFile() {
        val file = File(tempFolder.root, "delete-me.gguf")
        file.writeText("temporary")
        assertTrue(file.exists())

        val result = modelManager.deleteModel("delete-me")
        assertTrue(result)
        assertFalse(file.exists())
    }

    @Test
    fun deleteModel_returnsFalse_whenFileDoesNotExist() {
        val result = modelManager.deleteModel("nonexistent")
        assertFalse(result)
    }

    @Test
    fun modelId_withSlashes_isSanitized() {
        val file = File(tempFolder.root, "provider_model-name.gguf")
        file.writeText("data")
        assertTrue(modelManager.isModelCached("provider/model-name"))
    }

    @Test
    fun getAvailableStorageBytes_returnsPositive() {
        assertTrue(modelManager.getAvailableStorageBytes() > 0)
    }

    @Test
    fun verifyChecksum_returnsTrue_forCorrectHash() {
        val content = "hello world"
        File(tempFolder.root, "checksum-test.gguf").writeText(content)
        // SHA-256 of "hello world"
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        assertTrue(modelManager.verifyChecksum("checksum-test", expected))
    }

    @Test
    fun verifyChecksum_returnsFalse_forWrongHash() {
        File(tempFolder.root, "checksum-test2.gguf").writeText("some data")
        assertFalse(modelManager.verifyChecksum("checksum-test2", "0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun initialDownloadState_isIdle() {
        assertEquals(DownloadState.Idle, modelManager.downloadState.value)
    }
}
