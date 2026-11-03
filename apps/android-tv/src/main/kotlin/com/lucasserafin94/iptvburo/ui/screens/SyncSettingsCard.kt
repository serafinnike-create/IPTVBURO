package com.lucasserafin94.iptvburo.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lucasserafin94.iptvburo.R
import com.lucasserafin94.iptvburo.data.sync.AndroidFavoriteSync
import com.lucasserafin94.iptvburo.data.sync.FavoriteSyncWorker
import com.lucasserafin94.iptvburo.sync.SyncFailure
import com.lucasserafin94.iptvburo.sync.SyncResult
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButton
import com.lucasserafin94.iptvburo.ui.designsystem.BuroButtonStyle
import com.lucasserafin94.iptvburo.ui.theme.BuroAccent
import com.lucasserafin94.iptvburo.ui.theme.BuroTextPrimary
import com.lucasserafin94.iptvburo.ui.theme.BuroTextSecondary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/** How the card reaches the sync service without the screen's view model carrying it. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SyncEntryPoint {
    fun favoriteSync(): AndroidFavoriteSync
}

private fun favoriteSync(context: Context): AndroidFavoriteSync? =
    runCatching {
        EntryPointAccessors.fromApplication(context.applicationContext, SyncEntryPoint::class.java).favoriteSync()
    }.getOrNull()

/**
 * "Sincronizar aparelhos": the same favourites on the phone, the television, the computer.
 *
 * No account. One device shows an eight-letter code, another types it, and from then on the
 * favourites of profiles with the same name stay in step. The work is [AndroidFavoriteSync]'s;
 * this card only asks for it and says what happened.
 */
@Composable
internal fun SyncSettingsCard(compact: Boolean) {
    val context = LocalContext.current
    val sync = remember(context) { favoriteSync(context) } ?: return
    val scope = rememberCoroutineScope()
    var member by remember { mutableStateOf(sync.isMember) }
    var members by remember { mutableStateOf<Int?>(null) }
    var code by remember { mutableStateOf<String?>(null) }
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<SyncFailure?>(null) }
    var received by remember { mutableStateOf<Int?>(null) }

    fun <T> handle(result: SyncResult<T>, onSuccess: (T) -> Unit) {
        when (result) {
            is SyncResult.Success -> {
                problem = null
                onSuccess(result.value)
            }
            is SyncResult.Failure -> problem = result.reason
        }
        member = sync.isMember
    }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                block()
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(member) {
        if (member) handle(sync.status()) { status -> members = status.members.takeIf { status.inGroup } }
    }

    SettingsCard(compact = compact) {
        Text(stringResource(R.string.sync_title), color = BuroTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.sync_hint), color = BuroTextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        code?.let { shown ->
            Text(
                stringResource(R.string.sync_code, shown),
                color = BuroAccent,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
            Text(stringResource(R.string.sync_code_hint), color = BuroTextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
        }

        if (member) {
            members?.let { count ->
                Text(stringResource(R.string.sync_members, count), color = BuroTextPrimary, fontSize = 15.sp)
            }
            received?.let { count ->
                Text(stringResource(R.string.sync_received, count), color = BuroTextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BuroButton(onClick = { run { handle(sync.exchange()) { count -> received = count } } }, enabled = !busy) {
                    Text(stringResource(if (busy) R.string.sync_syncing else R.string.sync_now))
                }
                BuroButton(
                    onClick = { run { handle(sync.invite()) { invite -> code = invite.code } } },
                    enabled = !busy,
                    style = BuroButtonStyle.Secondary,
                ) { Text(stringResource(R.string.sync_show_code)) }
                BuroButton(
                    onClick = {
                        run {
                            handle(sync.leave()) {
                                FavoriteSyncWorker.cancelPeriodic(context)
                                code = null
                                members = null
                                received = null
                            }
                        }
                    },
                    enabled = !busy,
                    style = BuroButtonStyle.Secondary,
                ) { Text(stringResource(R.string.sync_leave)) }
            }
        } else {
            BuroButton(
                onClick = {
                    run {
                        handle(sync.invite()) { invite ->
                            code = invite.code
                            FavoriteSyncWorker.schedulePeriodic(context)
                        }
                    }
                },
                enabled = !busy,
            ) { Text(stringResource(R.string.sync_show_code)) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(MAX_CODE_INPUT) },
                    label = { Text(stringResource(R.string.sync_join_field)) },
                    singleLine = true,
                    modifier = Modifier.width(240.dp),
                )
                BuroButton(
                    onClick = {
                        run {
                            handle(sync.join(typed)) { count ->
                                members = count
                                typed = ""
                                FavoriteSyncWorker.schedulePeriodic(context)
                            }
                            if (sync.isMember) handle(sync.exchange()) { count -> received = count }
                        }
                    },
                    enabled = !busy && typed.count(Char::isLetterOrDigit) == CODE_LENGTH,
                    style = BuroButtonStyle.Secondary,
                ) { Text(stringResource(R.string.sync_join)) }
            }
        }

        problem?.let { failure ->
            Spacer(Modifier.height(8.dp))
            Text(stringResource(failure.messageResource()), color = BuroAccent, fontSize = 13.sp)
        }
    }
}

private fun SyncFailure.messageResource(): Int =
    when (this) {
        SyncFailure.LICENCE_REQUIRED -> R.string.sync_error_licence
        SyncFailure.NOT_IN_GROUP -> R.string.sync_error_not_in_group
        SyncFailure.UNKNOWN_CODE -> R.string.sync_error_code
        SyncFailure.GROUP_FULL -> R.string.sync_error_full
        SyncFailure.NETWORK -> R.string.sync_error_network
        SyncFailure.REJECTED -> R.string.sync_error_rejected
    }

/** Eight letters and digits, shown as ABCD-EFGH. */
private const val CODE_LENGTH = 8

/** Room for the dash and a stray space, nothing more. */
private const val MAX_CODE_INPUT = 12
