package com.example.tile

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.AdGuardCredentialsStore
import com.example.data.AdGuardRepository
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class AdGuardTileService : TileService() {

    private val tag = "AdGuardTileService"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isRequestRunning = AtomicBoolean(false)
    private var refreshJob: Job? = null

    private val credentialsStore by lazy { AdGuardCredentialsStore(applicationContext) }
    private val repository by lazy { AdGuardRepository(credentialsStore) }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(tag, "onStartListening")
        refreshTileState()
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(tag, "onStopListening")
        refreshJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun refreshTileState() {
        if (!credentialsStore.hasCredentials()) {
            updateTileState(Tile.STATE_UNAVAILABLE, "AdGuard: Setup Required", "Configure App first")
            return
        }

        refreshJob?.cancel()
        refreshJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { repository.fetchStatus() }
                result.onSuccess { status ->
                    if (status.protection_enabled) {
                        updateTileState(Tile.STATE_ACTIVE, "AdGuard: ON", "Protection Active")
                    } else {
                        updateTileState(Tile.STATE_INACTIVE, "AdGuard: OFF", "Protection Inactive")
                    }
                }.onFailure { error ->
                    Log.e(tag, "Failed to refresh tile status from server", error)
                    updateTileState(Tile.STATE_UNAVAILABLE, "AdGuard: Offline", "Server unreachable")
                }
            } catch (e: CancellationException) {
                // Ignore cancellation
            } catch (e: Exception) {
                updateTileState(Tile.STATE_UNAVAILABLE, "AdGuard: Error", e.message ?: "Unknown error")
            }
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d(tag, "onClick")

        if (!credentialsStore.hasCredentials()) {
            updateTileState(Tile.STATE_UNAVAILABLE, "AdGuard: Setup Required", "Configure App first")
            return
        }

        if (isRequestRunning.getAndSet(true)) {
            Log.d(tag, "Request already running, ignoring click")
            return
        }

        val currentTile = qsTile ?: return
        val currentLabel = currentTile.label?.toString() ?: "AdGuard"
        val currentSublabel = currentTile.subtitle?.toString() ?: ""
        val originalState = currentTile.state
        
        scope.launch {
            // Show loading state if request takes more than 300ms
            val loadingJob = launch {
                delay(300)
                updateTileState(originalState, "Updating...", "Communicating with server")
            }

            try {
                // Fetch status to make sure we don't toggle blind (per requirements "Never rely on cached state")
                val statusResult = withContext(Dispatchers.IO) { repository.fetchStatus() }
                
                statusResult.onSuccess { currentStatus ->
                    val nextEnabledState = !currentStatus.protection_enabled
                    val toggleResult = withContext(Dispatchers.IO) { repository.setProtection(nextEnabledState) }
                    
                    loadingJob.cancel()

                    toggleResult.onSuccess {
                        if (nextEnabledState) {
                            updateTileState(Tile.STATE_ACTIVE, "AdGuard: ON", "Protection Active")
                            showOptionalNotification("AdGuard Protection Enabled", "AdGuard Home is now filtering traffic.")
                        } else {
                            updateTileState(Tile.STATE_INACTIVE, "AdGuard: OFF", "Protection Inactive")
                            showOptionalNotification("AdGuard Protection Disabled", "AdGuard Home protection has been paused.")
                        }
                    }.onFailure { error ->
                        updateTileState(Tile.STATE_UNAVAILABLE, "AdGuard: Error", error.message ?: "Toggle failed")
                    }
                }.onFailure { error ->
                    loadingJob.cancel()
                    updateTileState(Tile.STATE_UNAVAILABLE, "AdGuard: Offline", error.message ?: "Server offline")
                }
            } catch (e: Exception) {
                loadingJob.cancel()
                updateTileState(Tile.STATE_UNAVAILABLE, "AdGuard: Error", e.message ?: "Unknown error")
            } finally {
                isRequestRunning.set(false)
            }
        }
    }

    private fun updateTileState(state: Int, label: String, sublabel: String) {
        val tile = qsTile ?: return
        tile.state = state
        tile.label = label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = sublabel
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_shield_protect)
        tile.updateTile()
    }

    private fun showOptionalNotification(title: String, message: String) {
        if (!credentialsStore.isNotificationsEnabled()) {
            return
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "adguard_toggle_channel"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AdGuard Protection Toggle Status",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_shield_protect)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(101, notification)
    }
}
