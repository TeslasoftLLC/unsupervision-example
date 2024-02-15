package org.teslasoft.core.unsupervision.util

import android.content.Context
import android.content.SharedPreferences

class UpdateStore(private var preferences: SharedPreferences) {
    companion object {
        private var updateStore: UpdateStore? = null

        fun getUpdateStore(context: Context): UpdateStore {
            if (updateStore == null) {
                updateStore = UpdateStore(context.getSharedPreferences("update_store", Context.MODE_PRIVATE))
            }
            return updateStore as UpdateStore
        }
    }

    private fun getString(key: String, defaultValue: String?): String? {
        return preferences.getString(key, defaultValue)
    }

    private fun setString(key: String, value: String?) {
        val editor = preferences.edit()
        editor.putString(key, value)
        editor.apply()
    }

    private fun getDeviceID(): String? {
        return getString("device_id", "00000000-0000-0000-0000-000000000000")
    }

    private fun setDeviceID(deviceID: String?) {
        setString("device_id", deviceID)
    }

    private fun getUpdateID(): String? {
        return getString("update_id", null)
    }

    private fun setUpdateID(updateID: String?) {
        setString("update_id", updateID)
    }
}