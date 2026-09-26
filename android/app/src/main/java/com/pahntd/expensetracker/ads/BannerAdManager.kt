package com.pahntd.expensetracker.ads

import android.util.Log
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads one standard (not "large", so it stays compact) anchored adaptive banner into a
 * screen's ad container, scoped to that screen's view lifecycle. The container starts `GONE`
 * and is only shown once an ad has actually loaded, so a missing SDK init, missing consent or a
 * failed load simply leaves the screen as it was.
 *
 * One request per view lifecycle: the [AdView] is created, loaded and destroyed with the
 * fragment's view, so it never outlives (or leaks) the Activity it was inflated against.
 */
class BannerAdManager @Inject constructor(
    private val adMobInitializer: AdMobInitializer
) {

    /** Call from `onViewCreated` with `viewLifecycleOwner`. */
    fun bind(container: ViewGroup, viewLifecycleOwner: LifecycleOwner) {
        var adView: AdView? = null

        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                adView?.destroy()
                adView = null
                container.removeAllViews()
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            // Suspends until consent allowed the SDK to initialize; cancelled with the view.
            adMobInitializer.isInitialized.first { it }

            val context = container.context
            val adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                context,
                context.resources.configuration.screenWidthDp
            )
            val request = BannerAdRequest.Builder(AdsConfig.BANNER_AD_UNIT_ID, adSize).build()

            val view = AdView(context).also { adView = it }
            container.addView(view)
            view.loadAd(request, object : AdLoadCallback<BannerAd> {
                // SDK callbacks may arrive off the main thread; hop back through the view's
                // scope so nothing runs once the view has been destroyed.
                override fun onAdLoaded(ad: BannerAd) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                        container.isVisible = true
                    }
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.w(TAG, "Banner failed to load: ${adError.code} ${adError.message}")
                }
            })
        }
    }

    private companion object {
        const val TAG = "BannerAdManager"
    }
}
