package com.universalrp.cleansweep.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

data class WifiDetails(
    val connected: Boolean,
    val transport: String,
    val ssid: String?,
    val bssid: String?,
    val ip: String?,
    val prefixLength: Int?,
    val gateway: String?,
    val dns: List<String>,
    val linkSpeedMbps: Int?,
    val rssiDbm: Int?,
    val signalPercent: Int?,
    val frequencyMhz: Int?,
    val band: String?,
    val macAddress: String?,
    val needsPermission: Boolean,
) {
    val subnetLabel: String?
        get() {
            val address = ip ?: return null
            val prefix = prefixLength ?: return null
            return "$address/$prefix"
        }
}

data class LanDevice(
    val ip: String,
    val mac: String?,
    val vendor: String?,
    val hostname: String?,
    val isSelf: Boolean,
    val isGateway: Boolean,
    val openPorts: List<Int>,
) {
    /**
     * Device type guessed from three real signals, in order of strength:
     * open ports (a camera answers RTSP 554, a printer IPP 9100, an iPhone 62078,
     * Windows file sharing 445…), the hostname the device reports, and the MAC vendor.
     * The guess is labelled as a guess — "Looks like a phone" — never as a fact.
     */
    val identity: DeviceIdentity get() = identifyDevice(this)

    val kind: String get() = identity.type

    val evidence: String get() = identity.evidence
}

data class DeviceIdentity(val type: String, val confidence: String, val evidence: String)

private fun portHit(device: LanDevice, vararg ports: Int) =
    ports.any { device.openPorts.contains(it) }

fun identifyDevice(device: LanDevice): DeviceIdentity {
    val host = device.hostname?.lowercase().orEmpty()
    val vendor = device.vendor?.lowercase().orEmpty()

    // ---------------------------------------------------------------- ports first
    when {
        portHit(device, 554, 8554, 1935) && !portHit(device, 445, 3389) ->
            return DeviceIdentity(
                "IP camera / DVR",
                "Likely",
                "answers on ${if (device.openPorts.contains(554)) "554 (RTSP video)" else "a video-streaming port"}",
            )
        portHit(device, 9100, 631) ->
            return DeviceIdentity("Printer", "Likely", "answers on a print port")
        portHit(device, 62078) ->
            return DeviceIdentity("iPhone / iPad", "Likely", "answers on 62078 (Apple devices only)")
        portHit(device, 445, 3389, 139) ->
            return DeviceIdentity("Windows PC / laptop", "Likely", "answers on file-sharing or remote-desktop ports")
        portHit(device, 8009, 8008, 8060) ->
            return DeviceIdentity("TV / streaming stick", "Likely", "answers on a casting port")
        portHit(device, 5000, 5001, 6690, 2049) ->
            return DeviceIdentity("NAS / storage device", "Likely", "answers on a storage port")
        portHit(device, 22) && !portHit(device, 445) ->
            return DeviceIdentity("Laptop / computer (Linux)", "Possible", "answers on 22 (SSH)")
    }

    // ------------------------------------------------------------- hostname hints
    val hostnameHints = listOf(
        "iphone" to "iPhone", "ipad" to "iPad", "ipod" to "iPod",
        "macbook" to "MacBook", "imac" to "iMac", "mac-" to "Mac",
        "android" to "Android phone", "redmi" to "Redmi phone", "mi-" to "Xiaomi phone",
        "oneplus" to "OnePlus phone", "pixel" to "Pixel phone", "galaxy" to "Samsung phone",
        "samsung" to "Samsung phone", "oppo" to "Oppo phone", "vivo" to "Vivo phone",
        "realme" to "Realme phone", "moto" to "Motorola phone",
        "desktop" to "Windows PC", "laptop" to "Laptop", "pc-" to "Computer",
        "raspberry" to "Raspberry Pi", "esp32" to "Smart device (ESP32)",
        "chromecast" to "Chromecast", "appletv" to "Apple TV", "tv" to "Smart TV",
        "printer" to "Printer", "brother" to "Printer", "epson" to "Printer",
        "canon" to "Printer", "hp-" to "Printer or PC",
        "camera" to "IP camera", "ipcam" to "IP camera", "nvr" to "NVR / camera hub",
        "router" to "Router", "gateway" to "Router", "modem" to "Router / modem",
        "nas" to "NAS / storage", "synology" to "Synology NAS", "qnap" to "QNAP NAS",
        "echo" to "Amazon Echo", "alexa" to "Amazon Echo", "sonos" to "Sonos speaker",
        "homepod" to "HomePod", "nest" to "Google Nest", "tuya" to "Smart plug / bulb",
        "switch" to "Network switch", "accesspoint" to "Wi-Fi access point",
    )
    hostnameHints.firstOrNull { host.contains(it.first) }?.let { (needle, type) ->
        return DeviceIdentity(type, "Likely", "its name contains \"$needle\"")
    }

    // ---------------------------------------------------------------- MAC vendor
    when {
        vendor.contains("apple") ->
            return DeviceIdentity("Apple device (iPhone / iPad / Mac)", "Possible", "MAC vendor is Apple")
        vendor.contains("samsung") ->
            return DeviceIdentity("Samsung device", "Possible", "MAC vendor is Samsung")
        vendor.contains("xiaomi") || vendor.contains("oppo") || vendor.contains("vivo") ||
            vendor.contains("realme") || vendor.contains("oneplus") || vendor.contains("huawei") ||
            vendor.contains("motorola") || vendor.contains("nokia") || vendor.contains("tecno") ||
            vendor.contains("infinix") || vendor.contains("itel") || vendor.contains("google") ->
            return DeviceIdentity("Phone or tablet", "Possible", "MAC vendor is a phone maker")
        vendor.contains("espressif") || vendor.contains("tuya") || vendor.contains("sonos") ||
            vendor.contains("amazon") || vendor.contains("nest") ->
            return DeviceIdentity("Smart home device", "Possible", "MAC vendor makes smart devices")
        vendor.contains("tp-link") || vendor.contains("d-link") || vendor.contains("netgear") ||
            vendor.contains("tenda") || vendor.contains("ubiquiti") || vendor.contains("aruba") ||
            vendor.contains("cisco") || vendor.contains("ruckus") || vendor.contains("wistron") ->
            return DeviceIdentity("Network gear (router / AP)", "Possible", "MAC vendor is network equipment")
        vendor.contains("intel") || vendor.contains("dell") || vendor.contains("hp") ||
            vendor.contains("lenovo") || vendor.contains("micro-star") || vendor.contains("gigabyte") ->
            return DeviceIdentity("Computer / laptop", "Possible", "MAC vendor is a PC maker")
    }

    if (device.isSelf) return DeviceIdentity("This phone", "Certain", "this is the phone running the scan")
    if (device.isGateway) return DeviceIdentity("Router / gateway", "Certain", "it is your default gateway")
    return DeviceIdentity("Unknown device", "Unknown", "no ports, name or vendor gave it away")
}

data class NetworkReport(
    val wifi: WifiDetails,
    val devices: List<LanDevice>,
    val scannedAtMs: Long,
    val probedHosts: Int,
    val note: String,
)

/**
 * Wi-Fi details + "who else is on my network".
 *
 * The scan works the way every network scanner works on a normal (non-root) phone:
 * it asks each address on your subnet to answer — first with a TCP connection on
 * the ports devices normally have open, then with a ping. Devices that are asleep,
 * that block pings, or that your router keeps isolated simply will not appear, and
 * the screen says so instead of pretending the list is complete.
 */
object NetworkScanner {

    private val PROBE_PORTS = listOf(80, 443, 22, 53, 445, 554, 3389, 5000, 62078, 8080, 9100)

    /** A small MAC-prefix table: enough to tell phones, computers and routers apart. */
    private val OUI: Map<String, String> = mapOf(
        "000C29" to "VMware", "001A11" to "Google", "001B63" to "Apple", "001C42" to "Parallels",
        "001E52" to "Apple", "001F5B" to "Apple", "0021E9" to "Apple", "002312" to "Apple",
        "002332" to "Apple", "00236C" to "Apple", "002436" to "Apple", "0025BC" to "Apple",
        "002608" to "Apple", "00264A" to "Apple", "0026B0" to "Apple", "0026BB" to "Apple",
        "0050F2" to "Microsoft", "00E04C" to "Realtek", "04E536" to "Apple", "0C4DE9" to "Apple",
        "0C8268" to "TP-Link", "109ADD" to "Apple", "10DA43" to "Netgear", "145AFC" to "Apple",
        "18B430" to "Nest", "1C1AC0" to "Apple", "24A43C" to "Ubiquiti", "28CFE9" to "Apple",
        "2C3033" to "Netgear", "3C2EFF" to "Apple", "3C5A37" to "Samsung", "40D32D" to "Apple",
        "44D9E7" to "Ubiquiti", "44FB42" to "Apple", "48D705" to "Apple", "4C3275" to "Apple",
        "50C7BF" to "TP-Link", "525400" to "QEMU", "54AE27" to "Apple", "5C969D" to "Apple",
        "60A37D" to "Apple", "60FB42" to "Apple", "64B9E8" to "Apple", "68A86D" to "Apple",
        "6C709F" to "Apple", "6C8DC1" to "Apple", "70105C" to "Apple", "70DE99" to "Apple",
        "748114" to "Apple", "78CA39" to "Apple", "7C11BE" to "Apple", "7CD1C3" to "Apple",
        "8416F9" to "TP-Link", "84FCDF" to "Apple", "8863DF" to "Apple", "8C8590" to "Apple",
        "90B21F" to "Apple", "94E96A" to "Apple", "9801A7" to "Apple", "9C207B" to "Apple",
        "9C4FDA" to "Apple", "A45E60" to "Apple", "A4C361" to "Apple", "A4D18C" to "Apple",
        "A85C2C" to "Apple", "A8667F" to "Apple", "AC61EA" to "Apple", "ACBC32" to "Apple",
        "B065BD" to "Apple", "B0BE76" to "TP-Link", "B827EB" to "Raspberry Pi", "B8E856" to "Apple",
        "BC52B7" to "Apple", "BC926B" to "Apple", "C01ADA" to "Apple", "C42C03" to "Apple",
        "C82A14" to "Apple", "C8BCC8" to "Apple", "CC08E0" to "Apple", "CC2D1B" to "Sichuan AI-Link",
        "D02598" to "Apple", "D0E140" to "Apple", "D4619D" to "Apple", "D8004D" to "Apple",
        "D83062" to "Apple", "D89E3F" to "Apple", "DC2B2A" to "Apple", "DC3714" to "Apple",
        "DC446D" to "Apple", "DC86D8" to "Apple", "E0B9BA" to "Apple", "E425E7" to "Apple",
        "E4CE8F" to "Apple", "E8802E" to "Apple", "E8B2AC" to "Apple", "EC3586" to "Apple",
        "F0DBF8" to "Apple", "F4F15A" to "Apple", "F81EDF" to "Apple", "FCFBFB" to "Cisco",
        "001DD3" to "Samsung", "001DF6" to "Samsung", "0021D1" to "Samsung", "002454" to "Samsung",
        "002566" to "Samsung", "04FE31" to "Samsung", "08373D" to "Samsung", "0C715D" to "Samsung",
        "1077B1" to "Samsung", "14568E" to "Samsung", "1C5A3E" to "Samsung", "205531" to "Samsung",
        "24DBED" to "Samsung", "28987B" to "Samsung", "2C4401" to "Samsung", "3083AF" to "Samsung",
        "34AA8B" to "Samsung", "38AA3C" to "Samsung", "3C6200" to "Samsung", "40D3AE" to "Samsung",
        "44F459" to "Samsung", "4C3C16" to "Samsung", "5001BB" to "Samsung", "5492BE" to "Samsung",
        "5CE8EB" to "Samsung", "608F5C" to "Samsung", "64B853" to "Samsung", "68EBAE" to "Samsung",
        "6C2F2C" to "Samsung", "706F81" to "Samsung", "74EBDA" to "Samsung", "78521A" to "Samsung",
        "7C0BC6" to "Samsung", "8018A7" to "Samsung", "8425DB" to "Samsung", "88329B" to "Samsung",
        "8C1ABF" to "Samsung", "90189E" to "Samsung", "943BB0" to "Samsung", "98FC35" to "Samsung",
        "9C3AAF" to "Samsung", "A02195" to "Samsung", "A4EBE1" to "Samsung", "A80671" to "Samsung",
        "AC5F3E" to "Samsung", "B047BF" to "Samsung", "B4EF39" to "Samsung", "B8D9CE" to "Samsung",
        "BC1485" to "Samsung", "BC20A4" to "Samsung", "C01173" to "Samsung", "C46AB7" to "Samsung",
        "C8A823" to "Samsung", "CC07AB" to "Samsung", "D013FD" to "Samsung", "D487D8" to "Samsung",
        "D857EF" to "Samsung", "DC7144" to "Samsung", "E83935" to "Samsung", "EC1F72" to "Samsung",
        "F008F1" to "Samsung", "F40E22" to "Samsung", "F49F54" to "Samsung", "F8D0AC" to "Samsung",
        "FC1910" to "Samsung", "FC8F90" to "Samsung",
        "0C1DAF" to "Xiaomi", "146B9C" to "Xiaomi", "18B905" to "Xiaomi", "20829B" to "Xiaomi",
        "28E31F" to "Xiaomi", "34CE00" to "Xiaomi", "3C01EF" to "Xiaomi", "44411B" to "Xiaomi",
        "4C49E3" to "Xiaomi", "50647A" to "Xiaomi", "588E81" to "Xiaomi", "64CC2E" to "Xiaomi",
        "64E4D0" to "Xiaomi", "68DFDD" to "Xiaomi", "6C9A4F" to "Xiaomi", "742344" to "Xiaomi",
        "7811DC" to "Xiaomi", "7C1DD9" to "Xiaomi", "843B8B" to "Xiaomi", "8CBEBE" to "Xiaomi",
        "9088BF" to "Xiaomi", "9C99A0" to "Xiaomi", "A4DA22" to "Xiaomi", "AC01A3" to "Xiaomi",
        "B0E235" to "Xiaomi", "C40BCB" to "Xiaomi", "C8ADDE" to "Xiaomi", "CC5C4F" to "Xiaomi",
        "D4970B" to "Xiaomi", "E86EDC" to "Xiaomi", "F0B429" to "Xiaomi", "F8A45F" to "Xiaomi",
        "FC64BA" to "Xiaomi", "209BCA" to "Xiaomi", "0C8910" to "Xiaomi", "10A561" to "Xiaomi",
        "3C8370" to "Oppo", "508F4C" to "Oppo", "5C3A3D" to "Oppo", "6C5AB0" to "Oppo",
        "8C0F0F" to "Oppo", "A0C9A0" to "Oppo", "B0AA77" to "Oppo", "C0EEFB" to "Oppo",
        "D4A10F" to "Oppo", "E8BBA8" to "Oppo", "F4A0E9" to "Oppo", "34BF47" to "Vivo",
        "4C2AA0" to "Vivo", "5CF91F" to "Vivo", "7C3CBC" to "Vivo", "9C3F3A" to "Vivo",
        "A0A4C5" to "Vivo", "B4C62C" to "Vivo", "C8E7D8" to "Vivo", "E4C0E2" to "Vivo",
        "0CD6BD" to "Realme", "2CF432" to "Realme", "5CE5B8" to "Realme", "88DA1A" to "Realme",
        "A4A2A8" to "Realme", "D0B3C0" to "Realme", "F0A7F9" to "Realme",
        "086361" to "Huawei", "0C96BF" to "Huawei", "1C1D67" to "Huawei", "28A9DB" to "Huawei",
        "3083AF" to "Huawei", "3CDFBD" to "Huawei", "4C5499" to "Huawei", "5C7D5E" to "Huawei",
        "6CB749" to "Huawei", "781DBA" to "Huawei", "8846C6" to "Huawei", "9C28EF" to "Huawei",
        "A8688E" to "Huawei", "B8BC1B" to "Huawei", "C8D15E" to "Huawei", "D4B709" to "Huawei",
        "E0247F" to "Huawei", "F4C714" to "Huawei", "F8E811" to "Huawei", "FC48EF" to "Huawei",
        "0C8063" to "OnePlus", "2CF0EE" to "OnePlus", "34A3BF" to "OnePlus", "4C1FC2" to "OnePlus",
        "64A2F9" to "OnePlus", "94652D" to "OnePlus", "AC64DD" to "OnePlus", "C0EEFB" to "OnePlus",
        "C8E776" to "OnePlus", "D085C2" to "OnePlus", "F0A7FF" to "OnePlus",
        "1C5F2B" to "Motorola", "24DA9B" to "Motorola", "3C2C30" to "Motorola", "40F62D" to "Motorola",
        "5C5181" to "Motorola", "6C0B84" to "Motorola", "8CC8CD" to "Motorola", "A0E4CB" to "Motorola",
        "C0BDC1" to "Motorola", "D4C94B" to "Motorola", "E8B2AC" to "Motorola", "F4F5D8" to "Motorola",
        "0CC731" to "Nokia", "1C8E5C" to "Nokia", "2C5BB8" to "Nokia", "44D244" to "Nokia",
        "5CB395" to "Nokia", "6C2990" to "Nokia", "8C3AE3" to "Nokia", "A4E8A3" to "Nokia",
        "B0F1EC" to "Nokia", "D4A02B" to "Nokia", "E4E4AB" to "Nokia", "F8F0FC" to "Nokia",
        "00BE3E" to "Intel", "0C8BFD" to "Intel", "1C1B0D" to "Intel", "3C9C0F" to "Intel",
        "48F17F" to "Intel", "5C514F" to "Intel", "6C8814" to "Intel", "7C7A91" to "Intel",
        "8C1645" to "Intel", "94C691" to "Intel", "A0A8CD" to "Intel", "B49691" to "Intel",
        "D0577B" to "Intel", "E4A471" to "Intel", "F8633F" to "Intel",
        "18A905" to "HP", "2C4138" to "HP", "3C52A1" to "HP", "40A8F0" to "HP", "6C3BE5" to "HP",
        "9457A5" to "HP", "A0B3CC" to "HP", "B0A22A" to "HP", "D48564" to "HP", "F4CE46" to "HP",
        "0021CC" to "Dell", "14FEB5" to "Dell", "1866DA" to "Dell", "24B6FD" to "Dell",
        "3417EB" to "Dell", "5CF9DD" to "Dell", "74E6E2" to "Dell", "8CEBE9" to "Dell",
        "B88584" to "Dell", "BC305B" to "Dell", "D4BE4B" to "Dell", "F04DA2" to "Dell",
        "54EE75" to "Lenovo", "68F728" to "Lenovo", "7CF49B" to "Lenovo", "9C8E99" to "Lenovo",
        "B0E2E5" to "Lenovo", "D8D090" to "Lenovo", "E8611F" to "Lenovo", "F8B156" to "Lenovo",
        "04D4C4" to "Asus", "1C872C" to "Asus", "2C56DC" to "Asus", "3085A9" to "Asus",
        "382C4A" to "Asus", "40167E" to "Asus", "54A050" to "Asus", "704D7B" to "Asus",
        "9C5C8E" to "Asus", "AC220B" to "Asus", "B06EBF" to "Asus", "D850E6" to "Asus",
        "002454" to "Wistron", "00D0F8" to "Netgear", "10DA43" to "Netgear", "204E7F" to "Netgear",
        "28C68E" to "Netgear", "4494FC" to "Netgear", "6CB0CE" to "Netgear", "841B5E" to "Netgear",
        "9C3DCF" to "Netgear", "A040A0" to "Netgear", "B03956" to "Netgear", "C40415" to "Netgear",
        "E0469A" to "Netgear", "E091F5" to "Netgear", "00184D" to "Netgear", "20E52A" to "Netgear",
        "001839" to "Cisco", "000B5F" to "Cisco", "0021A0" to "Cisco", "002290" to "Cisco",
        "3C0E23" to "Cisco", "5017FF" to "Cisco", "6C2056" to "Cisco", "8C604F" to "Cisco",
        "A89D21" to "Cisco", "B000B4" to "Cisco", "C4143C" to "Cisco", "E05FB9" to "Cisco",
        "001333" to "D-Link", "1CBDB9" to "D-Link", "28107B" to "D-Link", "340804" to "D-Link",
        "5CD998" to "D-Link", "84C9B2" to "D-Link", "9C4FCF" to "D-Link", "B8A386" to "D-Link",
        "CCB255" to "D-Link", "F07D68" to "D-Link", "FC7516" to "D-Link",
        "18E829" to "Ubiquiti", "24A43C" to "Ubiquiti", "44D9E7" to "Ubiquiti", "687251" to "Ubiquiti",
        "7483C2" to "Ubiquiti", "802AA8" to "Ubiquiti", "94A07D" to "Ubiquiti", "DC9FDB" to "Ubiquiti",
        "E063DA" to "Ubiquiti", "F492BF" to "Ubiquiti", "FCECDA" to "Ubiquiti",
        "00380F" to "Sonos", "48A6B8" to "Sonos", "5CAAFD" to "Sonos", "78A3E4" to "Sonos",
        "94E36D" to "Sonos", "B8E937" to "Sonos", "D4A02B" to "Sonos",
        "0C47C9" to "Espressif", "240AC4" to "Espressif", "3C71BF" to "Espressif",
        "5CCF7F" to "Espressif", "68C63A" to "Espressif", "7C9EBD" to "Espressif",
        "84F3EB" to "Espressif", "A020A6" to "Espressif", "B4E62D" to "Espressif",
        "C44F33" to "Espressif", "CC50E3" to "Espressif", "D8BFC0" to "Espressif",
        "E8DB84" to "Espressif", "ECFA5C" to "Espressif", "F4CFA2" to "Espressif",
        "FCF5C4" to "Espressif", "10521C" to "Espressif", "BCDDC2" to "Espressif",
        "001788" to "Philips Hue", "ECB5FA" to "Philips Hue",
        "44650D" to "Amazon", "4CEFC0" to "Amazon", "6837E9" to "Amazon", "74C246" to "Amazon",
        "84D6D0" to "Amazon", "A002DC" to "Amazon", "B47C9C" to "Amazon", "F0272D" to "Amazon",
        "FCA183" to "Amazon", "0C47C9" to "Amazon", "38F73D" to "Amazon", "50F5DA" to "Amazon",
        "6C5697" to "Amazon", "8871E5" to "Amazon", "9C29B1" to "Amazon", "AC63BE" to "Amazon",
        "DA1191" to "Amazon", "F8E5CE" to "Amazon", "FCD2B6" to "Amazon",
        "1CDF0F" to "Tuya", "3C71BF" to "Tuya", "60A423" to "Tuya", "68572D" to "Tuya",
        "D8F15B" to "Tuya", "F0FE6B" to "Tuya", "5C0272" to "Tuya", "7CF666" to "Tuya",
        "84E342" to "Tuya", "A4C138" to "Tuya", "B4E842" to "Tuya", "C4DD57" to "Tuya",
        "D4AD71" to "Tuya", "E0E5CF" to "Tuya", "F4CFA2" to "Tuya", "FC67C8" to "Tuya",
        "0072EE" to "Xiaomi", "0C1DAF" to "Xiaomi", "1C9DC2" to "Xiaomi", "28167F" to "Xiaomi",
        "34CD6D" to "Xiaomi", "44AEAB" to "Xiaomi", "5C0214" to "Xiaomi", "64B473" to "Xiaomi",
        "74DA88" to "Xiaomi", "8CBEBE" to "Xiaomi", "A0C9A0" to "Xiaomi", "B0E235" to "Xiaomi",
        "C46AB7" to "Xiaomi", "D4970B" to "Xiaomi", "E4AA5D" to "Xiaomi", "F0B429" to "Xiaomi",
        "FC02E9" to "Xiaomi", "FCA13E" to "Xiaomi", "FC64BA" to "Xiaomi",
    )

    /**
     * True when Android will let us read the Wi-Fi name. Below Android 13 that needs
     * Location; from 13 on either Nearby-devices or Location is enough. The old build
     * only looked at Location, so a phone where the user allowed just "Nearby devices"
     * kept showing the permission card forever — that is the bug this fixes.
     */
    fun wifiPermissionGranted(context: Context): Boolean {
        fun granted(permission: String) =
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= 33) {
            granted(Manifest.permission.NEARBY_WIFI_DEVICES) ||
                granted(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * The Wi-Fi network itself. Important when a VPN app is running: the "active"
     * network is then the VPN, and asking only about it is why CleanSweep used to say
     * "Wi-Fi (name hidden)" on a phone whose Wi-Fi was perfectly readable.
     */
    private fun wifiNetwork(
        connectivity: ConnectivityManager?,
    ): Pair<Network?, NetworkCapabilities?> {
        if (connectivity == null) return null to null
        return try {
            val active = connectivity.activeNetwork
            val activeCaps = active?.let { connectivity.getNetworkCapabilities(it) }
            if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                return active to activeCaps
            }
            val wifi = connectivity.allNetworks.firstOrNull { candidate ->
                connectivity.getNetworkCapabilities(candidate)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            }
            if (wifi != null) {
                wifi to connectivity.getNetworkCapabilities(wifi)
            } else {
                active to activeCaps
            }
        } catch (e: Exception) {
            null to null
        }
    }

    fun details(context: Context): WifiDetails {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
            as? WifiManager
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager

        var transport = "Not connected"
        var ip: String? = null
        var prefix: Int? = null
        var gateway: String? = null
        var dns: List<String> = emptyList()
        var needsPermission = false
        var wifiLink = false
        var wifiCaps: NetworkCapabilities? = null

        try {
            val active = connectivity?.activeNetwork
            val activeCaps = active?.let { connectivity.getNetworkCapabilities(it) }
            val vpnActive = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            val (wifiNet, caps) = wifiNetwork(connectivity)
            wifiCaps = caps
            wifiLink = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            transport = when {
                wifiLink && vpnActive -> "Wi-Fi • through a VPN app"
                wifiLink -> "Wi-Fi"
                activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile data"
                activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                vpnActive -> "VPN"
                activeCaps != null -> "Other"
                else -> "Not connected"
            }
            // With a VPN running, your real LAN address lives on the Wi-Fi network, not on
            // the VPN one — so read the addresses from the Wi-Fi link whenever there is one.
            val link = (if (wifiLink) wifiNet else active)
                ?.let { connectivity?.getLinkProperties(it) }
            link?.linkAddresses?.forEach { address ->
                val host = address.address
                if (host is Inet4Address && ip == null) {
                    ip = host.hostAddress
                    prefix = address.prefixLength
                }
            }
            link?.routes?.forEach { route ->
                if (route.isDefaultRoute && gateway == null) {
                    gateway = route.gateway?.hostAddress
                }
            }
            dns = link?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        } catch (e: Exception) {
            // Keep whatever was read so far.
        }

        var ssid: String? = null
        var bssid: String? = null
        var linkSpeed: Int? = null
        var rssi: Int? = null
        var frequency: Int? = null
        var mac: String? = null

        // Android 10 and newer: this is the path that returns the Wi-Fi name once the
        // user has granted Location / Nearby-devices. WifiManager.connectionInfo still
        // answers "<unknown ssid>" on many phones, which is what made the permission
        // card reappear even after the user had allowed it.
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val transportInfo = wifiCaps?.transportInfo
                if (transportInfo is android.net.wifi.WifiInfo) {
                    val raw = transportInfo.ssid
                    if (raw != null && raw != "<unknown ssid>") {
                        ssid = raw.trim('"')
                    }
                    val rawBssid = transportInfo.bssid
                    if (rawBssid != null && rawBssid != "02:00:00:00:00:00" &&
                        rawBssid != "00:00:00:00:00:00"
                    ) {
                        bssid = rawBssid
                    }
                    if (transportInfo.linkSpeed > 0) linkSpeed = transportInfo.linkSpeed
                    val rawRssi = transportInfo.rssi
                    if (rawRssi != 0 && rawRssi < 0) rssi = rawRssi
                    if (transportInfo.frequency > 0) frequency = transportInfo.frequency
                }
            } catch (e: Exception) {
                // Fall through to WifiManager below.
            }
        }

        if (wifiManager != null) {
            try {
                @Suppress("DEPRECATION")
                val info = wifiManager.connectionInfo
                if (info != null) {
                    val rawSsid = info.ssid
                    val legacySsid = when {
                        rawSsid == null -> null
                        rawSsid == "<unknown ssid>" -> null
                        rawSsid.startsWith("\"") && rawSsid.endsWith("\"") -> rawSsid.trim('"')
                        else -> rawSsid
                    }
                    if (ssid == null) ssid = legacySsid
                    val rawBssid = info.bssid
                    if (bssid == null &&
                        rawBssid != null &&
                        rawBssid != "02:00:00:00:00:00" &&
                        rawBssid != "00:00:00:00:00:00"
                    ) {
                        bssid = rawBssid
                    }
                    if (linkSpeed == null && info.linkSpeed > 0) linkSpeed = info.linkSpeed
                    if (rssi == null && info.rssi != 0 && info.rssi < 0) rssi = info.rssi
                    if (frequency == null && info.frequency > 0) frequency = info.frequency
                    @Suppress("DEPRECATION")
                    val rawMac = info.macAddress
                    if (!rawMac.isNullOrBlank() && rawMac != "02:00:00:00:00:00") mac = rawMac
                }
            } catch (e: Exception) {
                // Ignored: shown as "not reported".
            }
        }

        val signalPercent = rssi?.let { signalPercentOf(it) }
        val band = frequency?.let {
            when {
                it < 2500 -> "2.4 GHz"
                it < 5900 -> "5 GHz"
                else -> "6 GHz"
            }
        }

        val macFromInterface = mac ?: try {
            NetworkInterface.getNetworkInterfaces().toList()
                .firstOrNull { it.name.startsWith("wlan") }
                ?.hardwareAddress
                ?.joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        } catch (e: Exception) {
            null
        }

        // Only complain about permission when the name is genuinely unreadable *and* the
        // permission is really missing.
        needsPermission = ssid == null && !wifiPermissionGranted(context)

        return WifiDetails(
            connected = wifiLink || transport == "Ethernet",
            transport = transport,
            ssid = ssid,
            bssid = bssid,
            ip = ip,
            prefixLength = prefix,
            gateway = gateway,
            dns = dns,
            linkSpeedMbps = linkSpeed,
            rssiDbm = rssi,
            signalPercent = signalPercent,
            frequencyMhz = frequency,
            band = band,
            macAddress = macFromInterface,
            needsPermission = needsPermission,
        )
    }

    /** Rough signal strength from RSSI, the same curve Android uses for 5 bars. */
    private fun signalPercentOf(rssi: Int): Int? {
        if (rssi >= 0) return null
        val percent = when {
            rssi <= -100 -> 0
            rssi >= -50 -> 100
            else -> 2 * (rssi + 100)
        }
        return percent.coerceIn(0, 100)
    }

    suspend fun sweep(
        context: Context,
        onProgress: (done: Int, total: Int) -> Unit,
    ): NetworkReport = withContext(Dispatchers.IO) {
        val wifi = details(context)
        val selfIp = wifi.ip
        if (selfIp == null || !selfIp.startsWith("10.") && !selfIp.startsWith("192.168.") &&
            !selfIp.startsWith("172.") && !selfIp.startsWith("169.254")
        ) {
            return@withContext NetworkReport(
                wifi = wifi,
                devices = emptyList(),
                scannedAtMs = System.currentTimeMillis(),
                probedHosts = 0,
                note = "CleanSweep could only see a mobile-data or public address, so there is no " +
                    "home network to walk. Connect to your Wi-Fi and scan again.",
            )
        }

        val prefixLength = wifi.prefixLength ?: 24
        val hosts = hostsFor(selfIp, prefixLength)
        val arp = arpTable()
        var done = 0
        val devices = mutableListOf<LanDevice>()

        coroutineScope {
            val chunkSize = 40
            hosts.chunked(chunkSize).forEach { chunk ->
                val results = chunk.map { host ->
                    async {
                        probeHost(host, selfIp, wifi.gateway, arp)
                    }
                }.awaitAll()
                devices.addAll(results.filterNotNull())
                done += chunk.size
                onProgress(done.coerceAtMost(hosts.size), hosts.size)
            }
        }

        val sorted = devices.sortedWith(
            compareByDescending<LanDevice> { it.isSelf }
                .thenByDescending { it.isGateway }
                .thenBy { ipToLong(it.ip) }
        )

        NetworkReport(
            wifi = wifi,
            devices = sorted,
            scannedAtMs = System.currentTimeMillis(),
            probedHosts = hosts.size,
            note = "Devices that are asleep, that block pings, or that your router keeps isolated " +
                "will not appear. Phones in deep sleep are the usual reason a device you know about " +
                "is missing. Types are guessed from open ports, the device name and the MAC vendor — " +
                "they are good guesses, not certainties. Tap \"Identify with AI\" for a second opinion.",
        )
    }

    private suspend fun probeHost(
        ip: String,
        selfIp: String,
        gateway: String?,
        arp: Map<String, String>,
    ): LanDevice? {
        val address = try {
            InetAddress.getByName(ip)
        } catch (e: Exception) {
            return null
        }
        if (address.hostAddress != ip) return null

        var reachable = false
        val openPorts = mutableListOf<Int>()
        for (port in PROBE_PORTS) {
            val opened = try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), 220)
                    true
                }
            } catch (e: Exception) {
                false
            }
            if (opened) {
                reachable = true
                openPorts.add(port)
                if (openPorts.size >= 3) break
            }
        }
        if (!reachable) {
            reachable = try {
                address.isReachable(300)
            } catch (e: Exception) {
                false
            }
        }
        if (!reachable && ip != selfIp && ip != gateway) return null

        val hostname = withTimeoutOrNull(400) {
            try {
                val name = address.canonicalHostName
                if (name != null && name != ip) name else null
            } catch (e: Exception) {
                null
            }
        }
        val mac = arp[ip]
        return LanDevice(
            ip = ip,
            mac = mac,
            vendor = mac?.let { vendorOf(it) },
            hostname = hostname,
            isSelf = ip == selfIp,
            isGateway = ip == gateway,
            openPorts = openPorts,
        )
    }

    private fun hostsFor(selfIp: String, prefixLength: Int): List<String> {
        // Never walk more than 254 addresses, and never treat a huge subnet as
        // "the home network" — a phone on a /16 would otherwise try 65k hosts.
        val effective = prefixLength.coerceIn(24, 30)
        val mask = (0xFFFFFFFFL shl (32 - effective)) and 0xFFFFFFFFL
        val self = ipToLong(selfIp)
        val network = self and mask
        val broadcast = network or (mask.inv() and 0xFFFFFFFFL)

        val list = mutableListOf<String>()
        var host = network + 1
        while (host < broadcast && list.size < 254) {
            list.add(longToIp(host))
            host++
        }
        if (list.isEmpty()) list.add(selfIp)
        // Scan around our own address first: the router and the phones are usually close by.
        return list.sortedBy { kotlin.math.abs(ipToLong(it) - self) }
    }

    private fun vendorOf(mac: String): String? {
        val cleaned = mac.uppercase().replace("-", ":").split(":")
        if (cleaned.size < 3) return null
        val prefix = cleaned.take(3).joinToString("")
        return OUI[prefix]
    }

    private fun arpTable(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val file = File("/proc/net/arp")
            if (!file.exists() || !file.canRead()) return result
            file.readLines().drop(1).forEach { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4) {
                    val ip = parts[0]
                    val mac = parts[3]
                    if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                        result[ip] = mac.uppercase()
                    }
                }
            }
        } catch (e: Exception) {
            // Not readable on every phone; the scan still works without MACs.
        }
        return result
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.split(".")
        if (parts.size != 4) return 0L
        var value = 0L
        parts.forEach { part ->
            value = (value shl 8) or (part.toLongOrNull() ?: 0L)
        }
        return value and 0xFFFFFFFFL
    }

    private fun longToIp(value: Long): String {
        return listOf(
            (value shr 24) and 0xFF,
            (value shr 16) and 0xFF,
            (value shr 8) and 0xFF,
            value and 0xFF,
        ).joinToString(".")
    }
}
