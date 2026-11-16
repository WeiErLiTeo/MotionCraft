package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

object MediaPermissionHelper {

    fun getMediaPermissionsToRequest(): Array<String> {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_MEDIA_LOCATION
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    fun hasImageAccess(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES) ||
                    isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES)
            }
            else -> {
                isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    fun hasVideoAccess(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                isGranted(context, Manifest.permission.READ_MEDIA_VIDEO) ||
                    isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
            }
            else -> {
                isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    fun hasAnyMediaAccess(context: Context): Boolean {
        return hasImageAccess(context) || hasVideoAccess(context)
    }

    fun hasFullMediaAccess(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                isGranted(context, Manifest.permission.READ_MEDIA_IMAGES) &&
                    isGranted(context, Manifest.permission.READ_MEDIA_VIDEO)
            }
            else -> {
                isGranted(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    fun isPartialPhotoAccessOnly(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !hasFullMediaAccess(context) &&
            isGranted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    }

    fun hasMediaLocationAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isGranted(context, Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            true
        }
    }

    fun resolveOriginalUri(context: Context, uri: Uri): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            uri.scheme == "content" &&
            uri.authority == MediaStore.AUTHORITY &&
            !uri.toString().contains("/picker/") &&
            hasMediaLocationAccess(context)
        ) {
            return try {
                MediaStore.setRequireOriginal(uri)
            } catch (_: Exception) {
                uri
            }
        }
        return uri
    }

    fun openAppPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun isGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
