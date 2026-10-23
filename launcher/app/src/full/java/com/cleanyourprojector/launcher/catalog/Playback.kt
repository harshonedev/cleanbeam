package com.cleanyourprojector.launcher.catalog

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cleanyourprojector.launcher.apps.AppRepository

/** Builds the intent that gets a title playing (or as close as the app allows) and the Play Store intent. */
object Playback {

    /**
     * Best intent for [title] inside [pkg]: the title's own deep link when one is known
     * (an `intent:` URI or a web URL from [JustWatch]), then the service's search deep link,
     * then Android TV's in-app search action, then a plain launch. Each candidate is resolved
     * against the installed app so an unsupported link degrades to the next one instead of failing.
     */
    fun playIntent(context: Context, pkg: String, service: Service, title: String, deepLink: String? = null): Intent? {
        val candidates = ArrayList<Intent>()
        deepLink?.let { link -> linkIntent(link)?.let { candidates += it } }
        service.searchUrl(title)?.let { candidates += Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
        candidates += Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, title)
        for (i in candidates) {
            i.setPackage(pkg)
            val hit = context.packageManager.queryIntentActivities(i, 0).firstOrNull() ?: continue
            i.setClassName(pkg, hit.activityInfo.name)
            return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return AppRepository.launchIntentFor(context, pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * A plain VIEW of the URI in [link]. An `intent://…#Intent;…;end` string is parsed only for
     * its data URI: the link comes from a remote service, so its component, selector, extras,
     * categories and flags are dropped rather than trusted. The caller pins the package.
     */
    private fun linkIntent(link: String): Intent? = runCatching {
        val uri = if (link.startsWith("intent:")) Intent.parseUri(link, Intent.URI_INTENT_SCHEME).data
                  else Uri.parse(link)
        if (uri?.scheme.isNullOrEmpty()) null else Intent(Intent.ACTION_VIEW, uri)
    }.getOrNull()

    /** Play Store page for [pkg]; falls back to the web page when the store app is missing. */
    fun storeIntent(context: Context, pkg: String): Intent? {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
        for (i in listOf(market, web)) {
            if (context.packageManager.queryIntentActivities(i, 0).isNotEmpty()) return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return null
    }
}
