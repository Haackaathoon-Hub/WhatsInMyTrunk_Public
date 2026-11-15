package com.pleos.vehiclecontext.core.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import com.pleos.vehiclecontext.core.model.PoiCategory
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pleos NaviHelper(즐겨찾기·최근 목적지·현위치)와 Android GPS/Geocoder에서 수집한 장소 스냅샷
 */
data class PlaceSnapshot(
    val name: String,
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val poiCategory: PoiCategory = PoiCategory.UNKNOWN,
    val sourceLabel: String = "GPS"
)

/**
 * 출발지(내비 출발지 > 현재 GPS/지도) 및 도착지(내비 목적지 > 즐겨찾기/최근) 최적화 결과
 */
data class ResolvedRouteContext(
    val originName: String = "",
    val originAddress: String = "",
    val originLatitude: Double? = null,
    val originLongitude: Double? = null,
    val originPoi: PoiCategory = PoiCategory.UNKNOWN,
    val originSourceLabel: String = "GPS 위치 대기",
    val destinationName: String = "",
    val destinationAddress: String = "",
    val destinationLatitude: Double? = null,
    val destinationLongitude: Double? = null,
    val destinationSourceLabel: String = "내비 목적지",
    val originCandidates: List<PlaceSnapshot> = emptyList(),
    val destinationCandidates: List<PlaceSnapshot> = emptyList(),
    val suggestedPresetKey: String? = null
)

/**
 * GPS 좌표, Reverse Geocoding 주소, Pleos 지도 북마크/최근 목적지, 내비 경로 출발지를 통합하여
 * 트렁크 적재 팝업의 출발지/도착지 및 추천 프리셋을 자동으로 최적화하는 순수 + 플랫폼 헬퍼.
 */
object VehicleLocationResolver {

    const val PRESET_MART = "MART"
    const val PRESET_CAKE = "CAKE"
    const val PRESET_STROLLER = "STROLLER"
    const val PRESET_GOLFBAG = "GOLFBAG"

    fun isValidCoordinate(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) return false
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        return latitude != 0.0 || longitude != 0.0
    }

    /**
     * 두 WGS84 좌표 간의 거리(미터)를 Haversine 공식으로 계산합니다.
     */
    fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * 현재 GPS 좌표/주소, 내비 경로 시작 시점 출발지, 즐겨찾기, 최근 목적지를 조합하여
     * 가장 정확한 출발지/도착지 컨텍스트와 추천 프리셋 키를 도출합니다.
     */
    fun resolveRouteContext(
        naviOrigin: PlaceSnapshot?,
        currentGpsPlace: PlaceSnapshot?,
        activeDestination: PlaceSnapshot?,
        bookmarks: List<PlaceSnapshot> = emptyList(),
        recentDestinations: List<PlaceSnapshot> = emptyList(),
        fallbackDestinationName: String = "",
        fallbackOriginPoi: PoiCategory = PoiCategory.UNKNOWN
    ): ResolvedRouteContext {
        val knownPlaces = (bookmarks + recentDestinations).distinctBy { "${it.name}|${it.address}" }

        // 1. 현재 GPS 좌표 반경 250m 내에 북마크/최근 장소 POI가 있으면 상세 장소명으로 보강
        val enrichedCurrent = enrichWithNearbyPlace(currentGpsPlace, knownPlaces)

        // 2. 출발지 결정: (1) 활성 내비 경로의 출발지 -> (2) 현재 GPS/내비 현위치 -> (3) 적재 추정 POI명
        val selectedOrigin = when {
            naviOrigin != null && naviOrigin.name.isNotBlank() ->
                enrichWithNearbyPlace(naviOrigin, knownPlaces) ?: naviOrigin
            enrichedCurrent != null && (enrichedCurrent.name.isNotBlank() || enrichedCurrent.address.isNotBlank()) ->
                enrichedCurrent
            else -> PlaceSnapshot(
                name = fallbackOriginPoi.takeIf { it != PoiCategory.UNKNOWN }?.displayName ?: "",
                poiCategory = fallbackOriginPoi,
                sourceLabel = if (fallbackOriginPoi != PoiCategory.UNKNOWN) "차량 맥락 추론" else "GPS 위치 확인 중"
            )
        }

        val resolvedOriginName = selectedOrigin.name.ifBlank {
            formatShortAddress(selectedOrigin.address)
        }
        val resolvedOriginPoi = selectedOrigin.poiCategory.takeIf { it != PoiCategory.UNKNOWN }
            ?: inferPoiFromPlaceText("${resolvedOriginName} ${selectedOrigin.address}")
            .takeIf { it != PoiCategory.UNKNOWN }
            ?: fallbackOriginPoi

        // 3. 도착지 결정: (1) 내비게이션 활성 목적지 -> (2) 기존 세션 목적지
        val resolvedDestName = activeDestination?.name?.trim()?.takeIf { it.isNotBlank() }
            ?: fallbackDestinationName.trim()
        val resolvedDestAddress = activeDestination?.address?.trim().orEmpty()
        val resolvedDestSource = when {
            activeDestination != null && activeDestination.name.isNotBlank() -> activeDestination.sourceLabel
            resolvedDestName.isNotBlank() -> "내비 지정 목적지"
            else -> "목적지 미지정"
        }

        val originCandidates = buildList {
            if (naviOrigin != null && naviOrigin.name.isNotBlank()) add(naviOrigin)
            if (enrichedCurrent != null && (enrichedCurrent.name.isNotBlank() || enrichedCurrent.address.isNotBlank())) {
                add(enrichedCurrent.copy(name = enrichedCurrent.name.ifBlank { formatShortAddress(enrichedCurrent.address) }))
            }
            addAll(bookmarks)
            addAll(recentDestinations.take(3))
        }.filter { it.name.isNotBlank() || it.address.isNotBlank() }
            .distinctBy { it.name.trim() }
            .take(5)

        val destCandidates = buildList {
            if (activeDestination != null && activeDestination.name.isNotBlank()) add(activeDestination)
            else if (fallbackDestinationName.isNotBlank()) {
                add(PlaceSnapshot(name = fallbackDestinationName, sourceLabel = "현재 내비"))
            }
            addAll(bookmarks)
            addAll(recentDestinations)
        }.filter { it.name.isNotBlank() }
            .distinctBy { it.name.trim() }
            .take(6)

        val presetKey = suggestPresetKey(
            originName = resolvedOriginName,
            originAddress = selectedOrigin.address,
            originPoi = resolvedOriginPoi,
            destinationName = resolvedDestName
        )

        return ResolvedRouteContext(
            originName = resolvedOriginName,
            originAddress = selectedOrigin.address,
            originLatitude = selectedOrigin.latitude,
            originLongitude = selectedOrigin.longitude,
            originPoi = resolvedOriginPoi,
            originSourceLabel = selectedOrigin.sourceLabel,
            destinationName = resolvedDestName,
            destinationAddress = resolvedDestAddress,
            destinationLatitude = activeDestination?.latitude,
            destinationLongitude = activeDestination?.longitude,
            destinationSourceLabel = resolvedDestSource,
            originCandidates = originCandidates,
            destinationCandidates = destCandidates,
            suggestedPresetKey = presetKey
        )
    }

    private fun enrichWithNearbyPlace(
        base: PlaceSnapshot?,
        knownPlaces: List<PlaceSnapshot>
    ): PlaceSnapshot? {
        if (base == null) return null
        if (!isValidCoordinate(base.latitude, base.longitude)) return base
        val lat = base.latitude!!
        val lon = base.longitude!!
        val nearest = knownPlaces
            .filter { isValidCoordinate(it.latitude, it.longitude) }
            .map { candidate ->
                candidate to distanceMeters(lat, lon, candidate.latitude!!, candidate.longitude!!)
            }
            .filter { (_, dist) -> dist <= 250.0 }
            .minByOrNull { (_, dist) -> dist }
            ?.first

        return if (nearest != null && nearest.name.isNotBlank()) {
            base.copy(
                name = nearest.name,
                address = base.address.ifBlank { nearest.address },
                poiCategory = nearest.poiCategory.takeIf { it != PoiCategory.UNKNOWN } ?: base.poiCategory,
                sourceLabel = "${base.sourceLabel} · 지도 장소 매칭"
            )
        } else {
            val formattedName = base.name.ifBlank { formatShortAddress(base.address) }
            base.copy(
                name = formattedName,
                poiCategory = base.poiCategory.takeIf { it != PoiCategory.UNKNOWN }
                    ?: inferPoiFromPlaceText("$formattedName ${base.address}")
            )
        }
    }

    fun formatShortAddress(rawAddress: String): String {
        val trimmed = rawAddress.trim()
        if (trimmed.isBlank()) return ""
        val tokens = trimmed.split(Regex("\\s+")).filter {
            it.isNotBlank() && it != "대한민국" && it != "South" && it != "Korea"
        }
        return when {
            tokens.size >= 3 -> tokens.drop(1).take(3).joinToString(" ")
            tokens.isNotEmpty() -> tokens.joinToString(" ")
            else -> trimmed
        }
    }

    fun inferPoiFromPlaceText(text: String): PoiCategory {
        val normalized = text.lowercase()
        return when {
            normalized.containsAny(
                "이마트", "emart", "트레이더스", "코스트코", "costco", "롯데마트", "홈플러스",
                "하나로마트", "농협", "슈퍼", "백화점", "스타필드", "마트", "아울렛", "마켓"
            ) -> PoiCategory.MART_SHOPPING
            normalized.containsAny(
                "골프", "cc", "컨트리클럽", "캠핑", "글램핑", "수목원", "공원", "스키", "리조트"
            ) -> PoiCategory.LEISURE_CAMPING
            normalized.containsAny("회사", "오피스", "연구소", "사옥", "지점", "스튜디오") -> PoiCategory.OFFICE
            normalized.containsAny("공항", "ktx", "기차역", "터미널") -> PoiCategory.AIRPORT_STATION
            normalized.containsAny("집", "자택", "아파트", "래미안", "힐스테이트", "푸르지오", "자이") -> PoiCategory.HOME
            else -> PoiCategory.UNKNOWN
        }
    }

    fun suggestPresetKey(
        originName: String,
        originAddress: String,
        originPoi: PoiCategory,
        destinationName: String
    ): String? {
        val originText = "$originName $originAddress".lowercase()
        val destText = destinationName.lowercase()
        return when {
            originText.containsAny("베이커리", "빵집", "파리바게", "뚜레쥬르", "케이크", "와인", "꽃집", "화원", "공방") ->
                PRESET_CAKE
            originText.containsAny("골프", "cc", "컨트리클럽", "연습장") || destText.containsAny("골프", "cc", "컨트리클럽") ->
                PRESET_GOLFBAG
            originText.containsAny("어린이집", "키즈", "유아", "소아과", "유치원") || destText.containsAny("어린이집", "키즈", "유아", "공원") ->
                PRESET_STROLLER
            originPoi == PoiCategory.MART_SHOPPING ||
                originText.containsAny("이마트", "코스트코", "트레이더스", "롯데마트", "홈플러스", "하나로", "마트", "백화점", "스타필드") ->
                PRESET_MART
            else -> null
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { contains(it) }

    /**
     * Android [LocationManager]에서 마지막 유효 좌표를 읽고 [Geocoder]로 주소를 변환합니다.
     * IO 스레드에서 호출해야 하며, 권한 미부여/오프라인 환경에서도 예외 없이 안전하게 동작합니다.
     */
    @SuppressLint("MissingPermission")
    fun readPlatformLocationSnapshot(
        context: Context,
        latitudeOverride: Double? = null,
        longitudeOverride: Double? = null,
        sourceLabel: String = "GPS"
    ): PlaceSnapshot? {
        var lat = latitudeOverride
        var lon = longitudeOverride
        var activeSource = sourceLabel

        if (!isValidCoordinate(lat, lon)) {
            val locationManager = runCatching {
                context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            }.getOrNull()

            val bestLocation = locationManager?.let { lm ->
                val providers = listOfNotNull(
                    LocationManager.GPS_PROVIDER,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                ).distinct()
                providers.mapNotNull { provider ->
                    runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
                }.filter { loc ->
                    isValidCoordinate(loc.latitude, loc.longitude)
                }.maxByOrNull { it.time }
            }
            if (bestLocation != null) {
                lat = bestLocation.latitude
                lon = bestLocation.longitude
                activeSource = "GPS(${bestLocation.provider ?: "system"})"
            }
        }

        if (!isValidCoordinate(lat, lon)) return null
        val addressText = reverseGeocodeAddress(context, lat!!, lon!!)
        val shortName = formatShortAddress(addressText).ifBlank {
            String.format(Locale.US, "%.4f, %.4f", lat, lon)
        }
        return PlaceSnapshot(
            name = shortName,
            address = addressText,
            latitude = lat,
            longitude = lon,
            poiCategory = inferPoiFromPlaceText("$shortName $addressText"),
            sourceLabel = activeSource
        )
    }

    @SuppressLint("MissingPermission")
    fun registerPassiveGpsListener(
        context: Context,
        onLocation: (Location) -> Unit
    ): (() -> Unit)? {
        val locationManager = runCatching {
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        }.getOrNull() ?: return null

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (isValidCoordinate(location.latitude, location.longitude)) {
                    onLocation(location)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val registered = runCatching {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            var count = 0
            providers.forEach { provider ->
                if (runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)) {
                    runCatching {
                        locationManager.requestLocationUpdates(
                            provider,
                            10_000L,
                            25f,
                            listener,
                            Looper.getMainLooper()
                        )
                        count++
                    }
                }
            }
            count > 0
        }.getOrDefault(false)

        return if (registered) {
            { runCatching { locationManager.removeUpdates(listener) } }
        } else {
            null
        }
    }

    @Suppress("DEPRECATION")
    fun reverseGeocodeAddress(context: Context, latitude: Double, longitude: Double): String {
        if (!Geocoder.isPresent()) return ""
        return runCatching {
            val geocoder = Geocoder(context, Locale.KOREAN)
            val addresses: List<Address> = geocoder.getFromLocation(latitude, longitude, 1).orEmpty()
            val addr = addresses.firstOrNull() ?: return@runCatching ""
            val line = addr.getAddressLine(0)?.trim().orEmpty()
            if (line.isNotBlank()) return@runCatching line
            listOfNotNull(
                addr.adminArea,
                addr.locality ?: addr.subAdminArea,
                addr.subLocality ?: addr.thoroughfare,
                addr.featureName
            ).distinct().joinToString(" ").trim()
        }.getOrDefault("")
    }
}
