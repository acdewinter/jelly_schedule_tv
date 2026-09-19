package dev.jellyschedule.tv

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import dev.jellyschedule.tv.di.AppGraph

class JellyScheduleApp : Application(), SingletonImageLoader.Factory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = graph.okHttp))
                // The plugin's DevHost serves SVG placeholders; real servers serve JPEG/PNG/WebP.
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .build()
}

val Context.appGraph: AppGraph
    get() = (applicationContext as JellyScheduleApp).graph
