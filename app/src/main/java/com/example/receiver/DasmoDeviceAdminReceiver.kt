package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * DeviceAdminReceiver for DASMO App Lock.
 * When activated by the user, this prevents unauthorized users or intruders
 * from uninstalling the application from the Android settings or home screen launcher.
 */
class DasmoDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "DASMO Uninstall Protection is now ACTIVE", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "Warning: Disabling Uninstall Protection will allow anyone to uninstall DASMO App Lock without Master PIN!"
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "DASMO Uninstall Protection has been DEACTIVATED", Toast.LENGTH_SHORT).show()
    }
}
