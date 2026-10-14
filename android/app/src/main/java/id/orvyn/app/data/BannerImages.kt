package id.orvyn.app.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import id.orvyn.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Banner photos from the ORVYN server, kept in memory and on disk so banners still show offline. Nothing else is fetched. */
object BannerImages {
    private val memory = ConcurrentHashMap<String, ImageBitmap>()
    private val safeName = Regex("^[a-z]+\\.webp$")

    suspend fun load(context: Context, name: String): ImageBitmap? {
        if (!safeName.matches(name)) return null
        memory[name]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File(context.cacheDir, "banners/$name")
                if (!file.exists()) {
                    file.parentFile!!.mkdirs()
                    val part = File(file.path + ".part")
                    val conn = URL(BuildConfig.API_URL.removeSuffix("/api") + "/banners/$name").openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000; conn.readTimeout = 15_000
                    conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                    part.renameTo(file)
                }
                BitmapFactory.decodeFile(file.path)?.asImageBitmap()
            }.getOrNull()?.also { memory[name] = it }
        }
    }
}
