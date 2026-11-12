package com.stepq.app.ui.qrun

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.stepq.app.data.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Where the phone is (roughly) and place search, using only what's built into Android. */
interface PlaceSearch {
    suspend fun search(query: String, near: LatLng?): List<Place>
}

object NoPlaceSearch : PlaceSearch {
    override suspend fun search(query: String, near: LatLng?) = emptyList<Place>()
}

class AndroidPlaces(private val context: Context) : PlaceSearch {

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** A rough fix (city-block level) from the network provider, or the last one known. */
    @SuppressLint("MissingPermission")
    suspend fun here(): LatLng? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val fresh = providers.firstOrNull()?.let { p ->
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    val signal = androidx.core.os.CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(lm, p, signal, ContextCompat.getMainExecutor(context)) {
                            if (cont.isActive) cont.resume(it)
                        }
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
        }
        val loc = fresh ?: providers.firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        return loc?.let { LatLng(it.latitude, it.longitude) }
    }

    /** "Bandra West, Mumbai" for the A18 header. */
    suspend fun areaName(at: LatLng): String? = geocode { g -> g.getFromLocation(at.lat, at.lng, 1) }
        ?.firstOrNull()?.let { a -> listOfNotNull(a.subLocality, a.locality ?: a.subAdminArea).distinct().joinToString(", ").ifBlank { null } }

    override suspend fun search(query: String, near: LatLng?): List<Place> {
        if (query.trim().length < 3) return emptyList()
        val found = geocode { g ->
            if (near != null) {
                // Prefer places within roughly 50 km.
                g.getFromLocationName(query, 5, near.lat - 0.45, near.lng - 0.45, near.lat + 0.45, near.lng + 0.45)
                    ?.takeIf { it.isNotEmpty() } ?: g.getFromLocationName(query, 5)
            } else {
                g.getFromLocationName(query, 5)
            }
        }.orEmpty()
        return found.map { Place(placeName(it), it.latitude, it.longitude) }.distinctBy { it.name }
    }

    @Suppress("DEPRECATION") // The listener versions need API 33; these run off the main thread.
    private suspend fun geocode(block: (Geocoder) -> List<Address>?): List<Address>? {
        if (!Geocoder.isPresent()) return null
        return withContext(Dispatchers.IO) { runCatching { block(Geocoder(context, Locale.getDefault())) }.getOrNull() }
    }

    private fun placeName(a: Address): String {
        val first = a.featureName?.takeIf { it.isNotBlank() && it.any(Char::isLetter) && it != a.subLocality && it != a.locality }
        val street = a.thoroughfare?.takeIf { it != first }
        return listOfNotNull(first, street, a.subLocality, a.locality).distinct().take(3).joinToString(", ")
            .ifBlank { a.getAddressLine(0).orEmpty() }
    }
}
