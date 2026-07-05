package com.inktest

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.edit

enum class PredictionMode { OFF, ANDROID, LINEAR }
enum class LayerMode { NONE, HARDWARE, SOFTWARE }
enum class BitmapMode { ARGB_8888, RGB_565 }

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("ink_prefs", Context.MODE_PRIVATE)

    var strokeWidth: Float
        get() = sp.getFloat(KEY_STROKE_WIDTH, 4.0f)
        set(v) = sp.edit { putFloat(KEY_STROKE_WIDTH, v) }

    var smoothingFactor: Float
        get() = sp.getFloat(KEY_SMOOTHING, 0.5f)
        set(v) = sp.edit { putFloat(KEY_SMOOTHING, v) }

    var color: Int
        get() = sp.getInt(KEY_COLOR, Color.BLACK)
        set(v) = sp.edit { putInt(KEY_COLOR, v) }

    var smoothingAlgo: SmoothingAlgo
        get() = runCatching {
            SmoothingAlgo.valueOf(sp.getString(KEY_SMOOTHING_ALGO, SmoothingAlgo.BEZIER.name)!!)
        }.getOrDefault(SmoothingAlgo.BEZIER)
        set(v) = sp.edit { putString(KEY_SMOOTHING_ALGO, v.name) }

    var predictionMode: PredictionMode
        get() = runCatching {
            PredictionMode.valueOf(sp.getString(KEY_PREDICTION_MODE, PredictionMode.OFF.name)!!)
        }.getOrDefault(PredictionMode.OFF)
        set(v) = sp.edit { putString(KEY_PREDICTION_MODE, v.name) }

    var layerMode: LayerMode
        get() = runCatching {
            LayerMode.valueOf(sp.getString(KEY_LAYER_MODE, LayerMode.NONE.name)!!)
        }.getOrDefault(LayerMode.NONE)
        set(v) = sp.edit { putString(KEY_LAYER_MODE, v.name) }

    var bitmapMode: BitmapMode
        get() = runCatching {
            BitmapMode.valueOf(sp.getString(KEY_BITMAP_MODE, BitmapMode.ARGB_8888.name)!!)
        }.getOrDefault(BitmapMode.ARGB_8888)
        set(v) = sp.edit { putString(KEY_BITMAP_MODE, v.name) }

    var antialias: Boolean
        get() = sp.getBoolean(KEY_ANTIALIAS, true)
        set(v) = sp.edit { putBoolean(KEY_ANTIALIAS, v) }

    var canvasClipping: Boolean
        get() = sp.getBoolean(KEY_CLIPPING, false)
        set(v) = sp.edit { putBoolean(KEY_CLIPPING, v) }

    var damageRect: Boolean
        get() = sp.getBoolean(KEY_DAMAGE_RECT, false)
        set(v) = sp.edit { putBoolean(KEY_DAMAGE_RECT, v) }

    var unbufferedDispatch: Boolean
        get() = sp.getBoolean(KEY_UNBUFFERED, true)
        set(v) = sp.edit { putBoolean(KEY_UNBUFFERED, v) }

    var metricsVisible: Boolean
        get() = sp.getBoolean(KEY_METRICS_VIS, false)
        set(v) = sp.edit { putBoolean(KEY_METRICS_VIS, v) }

    var tool: Tool
        get() = runCatching {
            Tool.valueOf(sp.getString(KEY_TOOL, Tool.PEN.name)!!)
        }.getOrDefault(Tool.PEN)
        set(v) = sp.edit { putString(KEY_TOOL, v.name) }

    var linedSpacingMm: Float
        get() = sp.getFloat(KEY_LINED_MM, 9f)
        set(v) = sp.edit { putFloat(KEY_LINED_MM, v) }

    var gridSpacingMm: Float
        get() = sp.getFloat(KEY_GRID_MM, 5f)
        set(v) = sp.edit { putFloat(KEY_GRID_MM, v) }

    var dotsSpacingMm: Float
        get() = sp.getFloat(KEY_DOTS_MM, 5f)
        set(v) = sp.edit { putFloat(KEY_DOTS_MM, v) }

    var tuningVisible: Boolean
        get() = sp.getBoolean(KEY_TUNING_VIS, false)
        set(v) = sp.edit { putBoolean(KEY_TUNING_VIS, v) }

    var activeSectionId: String?
        get() = sp.getString(KEY_ACTIVE_SECTION, null)
        set(v) = sp.edit { if (v == null) remove(KEY_ACTIVE_SECTION) else putString(KEY_ACTIVE_SECTION, v) }

    var activePageId: String?
        get() = sp.getString(KEY_ACTIVE_PAGE, null)
        set(v) = sp.edit { if (v == null) remove(KEY_ACTIVE_PAGE) else putString(KEY_ACTIVE_PAGE, v) }

    // ---------------- WebDAV sync ----------------

    var webdavServer: String
        get() = sp.getString(KEY_WEBDAV_SERVER, "") ?: ""
        set(v) = sp.edit { putString(KEY_WEBDAV_SERVER, v) }

    var webdavUsername: String
        get() = sp.getString(KEY_WEBDAV_USER, "") ?: ""
        set(v) = sp.edit { putString(KEY_WEBDAV_USER, v) }

    var webdavPassword: String
        get() = sp.getString(KEY_WEBDAV_PW, "") ?: ""
        set(v) = sp.edit { putString(KEY_WEBDAV_PW, v) }

    var activeSchoolYear: String
        get() = sp.getString(KEY_SCHOOL_YEAR, "25-26") ?: "25-26"
        set(v) = sp.edit { putString(KEY_SCHOOL_YEAR, v) }

    var lastSyncTime: Long
        get() = sp.getLong(KEY_LAST_SYNC, 0L)
        set(v) = sp.edit { putLong(KEY_LAST_SYNC, v) }

    var toolbarFloating: Boolean
        get() = sp.getBoolean(KEY_TOOLBAR_FLOATING, false)
        set(v) = sp.edit { putBoolean(KEY_TOOLBAR_FLOATING, v) }

    var toolbarX: Float
        get() = sp.getFloat(KEY_TOOLBAR_X, 24f)
        set(v) = sp.edit { putFloat(KEY_TOOLBAR_X, v) }

    var toolbarY: Float
        get() = sp.getFloat(KEY_TOOLBAR_Y, 80f)
        set(v) = sp.edit { putFloat(KEY_TOOLBAR_Y, v) }

    fun resetAll() {
        sp.edit { clear() }
    }

    private companion object {
        const val KEY_STROKE_WIDTH = "strokeWidth"
        const val KEY_SMOOTHING = "smoothingFactor"
        const val KEY_COLOR = "color"
        const val KEY_SMOOTHING_ALGO = "smoothingAlgo"
        const val KEY_PREDICTION_MODE = "predictionMode"
        const val KEY_LAYER_MODE = "layerMode"
        const val KEY_BITMAP_MODE = "bitmapMode"
        const val KEY_ANTIALIAS = "antialias"
        const val KEY_CLIPPING = "canvasClipping"
        const val KEY_DAMAGE_RECT = "damageRect"
        const val KEY_UNBUFFERED = "unbufferedDispatch"
        const val KEY_METRICS_VIS = "metricsVisible"
        const val KEY_TUNING_VIS = "tuningVisible"
        const val KEY_ACTIVE_SECTION = "activeSectionId"
        const val KEY_ACTIVE_PAGE = "activePageId"
        const val KEY_TOOL = "tool"
        const val KEY_LINED_MM = "linedSpacingMm"
        const val KEY_GRID_MM = "gridSpacingMm"
        const val KEY_DOTS_MM = "dotsSpacingMm"
        const val KEY_WEBDAV_SERVER = "webdavServer"
        const val KEY_WEBDAV_USER = "webdavUsername"
        const val KEY_WEBDAV_PW = "webdavPassword"
        const val KEY_SCHOOL_YEAR = "activeSchoolYear"
        const val KEY_LAST_SYNC = "lastSyncTime"
        const val KEY_TOOLBAR_FLOATING = "toolbarFloating"
        const val KEY_TOOLBAR_X = "toolbarX"
        const val KEY_TOOLBAR_Y = "toolbarY"
    }
}
