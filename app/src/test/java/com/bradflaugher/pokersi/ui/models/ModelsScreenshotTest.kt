package com.bradflaugher.pokersi.ui.models

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bradflaugher.pokersi.llm.DownloadProgress
import com.bradflaugher.pokersi.llm.EngineState
import com.bradflaugher.pokersi.llm.LocalModel
import com.bradflaugher.pokersi.llm.ModelCatalog
import com.bradflaugher.pokersi.llm.ModelLocation
import com.bradflaugher.pokersi.ui.pixel.PixelTheme
import com.bradflaugher.pokersi.ui.screenshot.DeviceSpec
import com.bradflaugher.pokersi.ui.screenshot.renderToPng
import com.bradflaugher.pokersi.ui.screenshot.screenshotDir
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The model bay with a loaded model, a download in flight and a custom file. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModelsScreenshotTest {
    @Test
    fun modelBay() = renderToPng(File(screenshotDir, "screens/models.png"), DeviceSpec.PHONE) {
        PixelTheme { ModelsContent(scene(), NoActions) }
    }

    companion object {
        fun scene(): ModelsUiState {
            val catalog = ModelCatalog.models.map { c ->
                val location = if (c.recommended) ModelLocation.FilePath(File("/models/${c.fileName}")) else null
                val download = if (c.id == "qwen3-1.7b") DownloadProgress(DownloadProgress.State.RUNNING, 512_000_000, c.sizeBytes, 14_000_000) else null
                LocalModel(c.id, c.name, c.fileName, c.sizeBytes, location, c, download)
            }
            val loaded = catalog.first { it.catalog!!.recommended }
            return ModelsUiState(
                engine = EngineState.Ready(loaded.id, loaded.name, "lfm2 1.2B Q4_K_M"),
                models = catalog,
                folders = listOf(Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AModels")),
            )
        }
    }
}

private object NoActions : ModelsActions {
    override fun download(model: LocalModel) = Unit
    override fun cancel(model: LocalModel) = Unit
    override fun load(model: LocalModel) = Unit
    override fun unload() = Unit
    override fun delete(model: LocalModel) = Unit
    override fun addFolder() = Unit
    override fun addFile() = Unit
    override fun removeFolder(uri: Uri) = Unit
    override fun back() = Unit
}
