package com.starswipe.app.core.mediastore

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class ChinaGeoAnchor(
    val province: String,
    val city: String,
    val lat: Double,
    val lng: Double
)

class LocationGeocoder(private val context: Context) {

    private val cache = ConcurrentHashMap<String, String>()

    // Offline Anchor points for all Chinese provinces, municipalities and major cities
    private val chinaAnchors = listOf(
        // 直辖市 / 特别行政区
        ChinaGeoAnchor("北京市", "北京市", 39.904, 116.407),
        ChinaGeoAnchor("上海市", "上海市", 31.230, 121.473),
        ChinaGeoAnchor("天津市", "天津市", 39.125, 117.190),
        ChinaGeoAnchor("重庆市", "重庆市", 29.563, 106.551),
        ChinaGeoAnchor("香港", "香港", 22.319, 114.169),
        ChinaGeoAnchor("澳门", "澳门", 22.198, 113.543),
        ChinaGeoAnchor("台湾省", "台北市", 25.033, 121.565),

        // 广东省
        ChinaGeoAnchor("广东省", "深圳市", 22.543, 114.057),
        ChinaGeoAnchor("广东省", "广州市", 23.129, 113.264),
        ChinaGeoAnchor("广东省", "东莞市", 23.020, 113.751),
        ChinaGeoAnchor("广东省", "佛山市", 23.021, 113.121),
        ChinaGeoAnchor("广东省", "珠海市", 22.270, 113.576),
        ChinaGeoAnchor("广东省", "惠州市", 23.111, 114.416),
        ChinaGeoAnchor("广东省", "中山市", 22.517, 113.392),
        ChinaGeoAnchor("广东省", "汕头市", 23.354, 116.681),
        ChinaGeoAnchor("广东省", "湛江市", 21.270, 110.359),

        // 浙江省
        ChinaGeoAnchor("浙江省", "杭州市", 30.274, 120.155),
        ChinaGeoAnchor("浙江省", "宁波市", 29.868, 121.544),
        ChinaGeoAnchor("浙江省", "温州市", 27.994, 120.699),
        ChinaGeoAnchor("浙江省", "嘉兴市", 30.746, 120.755),
        ChinaGeoAnchor("浙江省", "绍兴市", 30.002, 120.582),

        // 江苏省
        ChinaGeoAnchor("江苏省", "南京市", 32.060, 118.796),
        ChinaGeoAnchor("江苏省", "苏州市", 31.298, 120.585),
        ChinaGeoAnchor("江苏省", "无锡市", 31.491, 120.311),
        ChinaGeoAnchor("江苏省", "常州市", 31.811, 119.974),
        ChinaGeoAnchor("江苏省", "南通市", 31.980, 120.894),

        // 四川省
        ChinaGeoAnchor("四川省", "成都市", 30.658, 104.066),
        ChinaGeoAnchor("四川省", "绵阳市", 31.467, 104.679),
        ChinaGeoAnchor("四川省", "乐山市", 29.552, 103.765),

        // 湖北省
        ChinaGeoAnchor("湖北省", "武汉市", 30.592, 114.305),
        ChinaGeoAnchor("湖北省", "宜昌市", 30.691, 111.286),

        // 湖南省
        ChinaGeoAnchor("湖南省", "长沙市", 28.228, 112.938),
        ChinaGeoAnchor("湖南省", "张家界市", 29.117, 110.479),

        // 陕西省
        ChinaGeoAnchor("陕西省", "西安市", 34.341, 108.939),

        // 福建省
        ChinaGeoAnchor("福建省", "福州市", 26.074, 119.296),
        ChinaGeoAnchor("福建省", "厦门市", 24.479, 118.089),
        ChinaGeoAnchor("福建省", "泉州市", 24.908, 118.589),

        // 山东省
        ChinaGeoAnchor("山东省", "济南市", 36.651, 117.120),
        ChinaGeoAnchor("山东省", "青岛市", 36.067, 120.382),
        ChinaGeoAnchor("山东省", "烟台市", 37.463, 121.447),

        // 河南省
        ChinaGeoAnchor("河南省", "郑州市", 34.746, 113.625),
        ChinaGeoAnchor("河南省", "洛阳市", 34.618, 112.453),

        // 河北省
        ChinaGeoAnchor("河北省", "石家庄市", 38.042, 114.514),

        // 安徽省
        ChinaGeoAnchor("安徽省", "合肥市", 31.820, 117.227),
        ChinaGeoAnchor("安徽省", "黄山市", 29.714, 118.337),

        // 江西省
        ChinaGeoAnchor("江西省", "南昌市", 28.682, 115.857),

        // 辽宁省
        ChinaGeoAnchor("辽宁省", "沈阳市", 41.805, 123.431),
        ChinaGeoAnchor("辽宁省", "大连市", 38.914, 121.614),

        // 吉林省
        ChinaGeoAnchor("吉林省", "长春市", 43.817, 125.323),

        // 黑龙江省
        ChinaGeoAnchor("黑龙江省", "哈尔滨市", 45.803, 126.535),

        // 云南省
        ChinaGeoAnchor("云南省", "昆明市", 25.045, 102.709),
        ChinaGeoAnchor("云南省", "大理", 25.606, 100.267),
        ChinaGeoAnchor("云南省", "丽江市", 26.855, 100.228),

        // 贵州省
        ChinaGeoAnchor("贵州省", "贵阳市", 26.647, 106.630),

        // 广西
        ChinaGeoAnchor("广西壮族自治区", "南宁市", 22.817, 108.366),
        ChinaGeoAnchor("广西壮族自治区", "桂林市", 25.273, 110.290),

        // 海南省
        ChinaGeoAnchor("海南省", "海口市", 20.044, 110.199),
        ChinaGeoAnchor("海南省", "三亚市", 18.252, 109.511),

        // 山西省
        ChinaGeoAnchor("山西省", "太原市", 37.870, 112.548),

        // 内蒙古
        ChinaGeoAnchor("内蒙古自治区", "呼和浩特市", 40.842, 111.749),

        // 新疆
        ChinaGeoAnchor("新疆维吾尔自治区", "乌鲁木齐市", 43.825, 87.616),

        // 西藏
        ChinaGeoAnchor("西藏自治区", "拉萨市", 29.652, 91.172),

        // 青海省
        ChinaGeoAnchor("青海省", "西宁市", 36.617, 101.778),

        // 宁夏
        ChinaGeoAnchor("宁夏回族自治区", "银川市", 38.487, 106.230),

        // 甘肃省
        ChinaGeoAnchor("甘肃省", "兰州市", 36.061, 103.834)
    )

    suspend fun getAddress(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        val cacheKey = String.format(Locale.US, "%.2f,%.2f", lat, lng)
        cache[cacheKey]?.let { return@withContext it }

        var result: String? = null

        // 1. Try system Geocoder if present, guarded by 400ms timeout
        if (Geocoder.isPresent()) {
            try {
                result = withTimeoutOrNull(400L) {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        suspendCancellableCoroutine<String?> { continuation ->
                            geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (continuation.isActive) {
                                        continuation.resume(formatAddress(addresses.firstOrNull()))
                                    }
                                }

                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) {
                                        continuation.resume(null)
                                    }
                                }
                            })
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(lat, lng, 1)
                        formatAddress(addresses?.firstOrNull())
                    }
                }
            } catch (ignored: Exception) {}
        }

        // 2. Offline Fallback for China Coordinates (lat 18-54, lng 73-135)
        if (result.isNullOrEmpty() && lat in 15.0..55.0 && lng in 70.0..136.0) {
            result = matchNearestChinaRegion(lat, lng)
        }

        // 3. Fallback to coordinate representation
        if (result.isNullOrEmpty()) {
            val latDir = if (lat >= 0) "N" else "S"
            val lngDir = if (lng >= 0) "E" else "W"
            result = String.format(Locale.US, "%.2f°%s, %.2f°%s", kotlin.math.abs(lat), latDir, kotlin.math.abs(lng), lngDir)
        }

        cache[cacheKey] = result
        result
    }

    private fun matchNearestChinaRegion(lat: Double, lng: Double): String {
        var minDistance = Double.MAX_VALUE
        var nearestAnchor: ChinaGeoAnchor? = null

        for (anchor in chinaAnchors) {
            val d = distanceKm(lat, lng, anchor.lat, anchor.lng)
            if (d < minDistance) {
                minDistance = d
                nearestAnchor = anchor
            }
        }

        return if (nearestAnchor != null) {
            if (nearestAnchor.province == nearestAnchor.city) {
                nearestAnchor.province
            } else {
                "${nearestAnchor.province} · ${nearestAnchor.city}"
            }
        } else {
            "中国"
        }
    }

    private fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun formatAddress(address: Address?): String? {
        if (address == null) return null
        val admin = address.adminArea
        val locality = address.locality
        val subLocality = address.subLocality

        val city = locality ?: subLocality
        return when {
            !admin.isNullOrEmpty() && !city.isNullOrEmpty() -> {
                if (admin == city) city else "$admin · $city"
            }
            !admin.isNullOrEmpty() -> admin
            !city.isNullOrEmpty() -> city
            else -> address.countryName
        }
    }
}
