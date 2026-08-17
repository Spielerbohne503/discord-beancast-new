package uk.spielerbohne.petodo.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import uk.spielerbohne.petodo.data.alarm.AlarmScheduler

/**
 * Was gerade erlaubt ist — und wohin man tippen muss, um es zu ändern.
 *
 * Die drei Punkte entscheiden darüber, ob eine Erinnerung ankommt. Ohne sie ist die App
 * eine Liste, die manchmal piept.
 */
data class PermissionState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val batteryExemption: Boolean,
) {
    val allGranted: Boolean get() = notifications && exactAlarms && batteryExemption
}

object Permissions {

    fun current(context: Context): PermissionState = PermissionState(
        notifications = hasNotificationPermission(context),
        exactAlarms = AlarmScheduler(context).canScheduleExactAlarms(),
        batteryExemption = hasBatteryExemption(context),
    )

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun hasBatteryExemption(context: Context): Boolean =
        context.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Systemdialog für exakte Alarme (ab Android 12). */
    fun exactAlarmSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.fromParts("package", context.packageName, null))
        } else {
            null
        }

    /**
     * Abfrage der Akku-Ausnahme. Der Dialog ist die einzige Stelle, an der Android das
     * direkt erlaubt; scheitert er, führt [appSettingsIntent] zur Systemseite.
     */
    fun batteryExemptionIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", context.packageName, null))

    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))

    /**
     * Hersteller, bei denen korrekter Code allein nicht reicht (Projektplan,
     * Abschnitt 6.7).
     */
    fun needsManufacturerHint(): Boolean =
        Build.MANUFACTURER.lowercase() in MANUFACTURERS_WITH_OWN_KILLER

    private val MANUFACTURERS_WITH_OWN_KILLER = setOf(
        "samsung", "xiaomi", "redmi", "poco", "huawei", "honor", "oneplus", "oppo", "realme", "vivo",
    )
}
