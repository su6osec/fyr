package com.fyr.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fyr.BuildConfig
import com.fyr.R
import com.fyr.data.Backup
import com.fyr.data.Dates
import com.fyr.data.FyrState
import com.fyr.data.STAGED_AVATAR
import com.fyr.data.nameAsTyped
import com.fyr.domain.Stats
import com.fyr.ui.components.Emoji
import com.fyr.ui.components.FlameBadge
import com.fyr.ui.components.Glyph
import com.fyr.ui.components.GlyphIcon
import com.fyr.ui.components.PhotoBadge
import com.fyr.ui.components.PortraitHalo
import com.fyr.ui.components.SegmentedOption
import com.fyr.ui.theme.Poppins
import com.fyr.ui.theme.Qurova

/**
 * Profile — short on purpose.
 *
 * Fyr follows the system's light or dark setting, needs no account, and has
 * no appearance section to show for it. There is no accent picker and no
 * cloud sync toggle, because neither changes whether the habit gets done.
 *
 * What there is, reads in one pass: the masthead says who and how today is
 * going, the row of counts says what the collection holds, and the cards
 * below carry the one preference and the two archives — no tabs to choose
 * between, nothing a person changes buried deeper than the card that owns
 * it. The one thing that *is* behind a tap is About: read once and
 * remembered, it sits behind the info mark at the title's shoulder rather
 * than charging every visit a scroll to reach copy that answers none of
 * the questions this page is opened for. Everything else on the page
 * either explains something or gets rid of what is in it.
 */
@Composable
fun SettingsScreen(
    state: FyrState,
    // The day the masthead's coin counts against: read from the root's own
    // clock so this screen and the Today header never disagree about what
    // "today" is when the composition outlives midnight.
    today: Int,
    // Required rather than defaulted: a defaulted parameter here would sit
    // ahead of `modifier`, which the compose lint wants to be the first one.
    initialEditing: Boolean,
    onName: (String) -> Unit,
    // The picture, in two steps. The picker's result is *staged* — copied
    // to a file beside the profile rather than into it — and only Save
    // [adoptPhoto]s it into place; dismissal [discardPhoto]s it. The old
    // single hop wrote the profile the moment the picker returned, which
    // meant a picture chosen and then cancelled had already happened.
    stagePhoto: (uri: Uri, onStaged: () -> Unit) -> Unit,
    adoptPhoto: () -> Unit,
    discardPhoto: () -> Unit,
    // Whether a staged pick is actually on disk. Process death preserves the
    // editor's *flags* but not the file — the store clears staging the moment
    // it starts — and an editor that comes back claiming a pending photo it
    // can no longer adopt would Save nothing while showing a ghost.
    hasStagedPhoto: () -> Boolean,
    onAvatarClear: () -> Unit,
    onWeekStart: (day: Int) -> Unit,
    onOpenHabit: (habitId: String) -> Unit,
    onRestoreTrash: (habitId: String) -> Unit,
    onPurgeTrash: (habitId: String) -> Unit,
    onEmptyTrash: () -> Unit,
    // The backup, in three steps that never run without the person choosing
    // a file first. [exportBackup] writes the document out; [readBackup]
    // only *parses* — it touches no data at all, so the dialog after it can
    // show exactly what the file holds before anything is decided; and only
    // a deliberate [applyBackup] with the mode they picked changes anything.
    exportBackup: (uri: Uri, onDone: (Boolean) -> Unit) -> Unit,
    readBackup: (uri: Uri, onReady: (Backup.Archive?) -> Unit) -> Unit,
    applyBackup: (archive: Backup.Archive, mode: Backup.Mode, onDone: (Boolean) -> Unit) -> Unit,
    // The box over this screen is a window of its own floating above it, so
    // the frost that goes behind it has to be painted by the root — this
    // screen only reports when a box (the editor or the About card) opens
    // and closes, and the root blurs what it owns (the bar included) for as
    // long as it does.
    onOverlayChange: (Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // One editor for one thing. Name and picture are two halves of the same
    // line at the top of this card, and two buttons opening two boxes was the
    // app asking, twice, a question it had already been given the answer to.
    var editingProfile by rememberSaveable { mutableStateOf(initialEditing) }

    // Open, closed, and gone: the About card, one tap from the masthead's
    // info mark. It moved out of the page and into a box because it is read
    // once and remembered — the one content on this screen nobody comes
    // back to — and it sat far enough down that reaching it meant scrolling
    // past every preference to get at copy that answers none of them.
    var showingAbout by rememberSaveable { mutableStateOf(false) }

    // One frost, either box. The editor and the About card dim the page
    // behind them identically — same blur, same glass — so a single signal
    // to the root covers both, and the root stops frosting the moment this
    // screen leaves composition too, so a dialog dismissed by a back press
    // from anywhere else never leaves the app permanently out of focus.
    val overlayOpen = editingProfile || showingAbout
    LaunchedEffect(overlayOpen) { onOverlayChange(overlayOpen) }
    DisposableEffect(Unit) { onDispose { onOverlayChange(false) } }

    // What the editor shows as *its* picture: the saved one until the
    // dialog itself changes it. Held at screen scope beside the picker
    // because that is where the pick's result lands — and because the
    // profile underneath reads none of it until Save. `photoPending` is
    // what makes Save the only writer: adoption or clearing, never both,
    // and never before the press.
    var dialogPhoto by rememberSaveable { mutableStateOf(state.avatarPhoto) }
    var photoPending by rememberSaveable { mutableStateOf(false) }

    // Held at screen scope rather than inside the dialog: a launcher
    // registered in a composable that has already left composition would
    // never receive its result. The dialog itself stays up behind the
    // picker — what it is editing does not change when the picker opens.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            stagePhoto(uri) {
                dialogPhoto = STAGED_AVATAR
                photoPending = true
            }
        }
    }

    // Reconcile the flags with the disk after a process death: a pending
    // photo whose staged file is gone was never going to be adopted, and the
    // only honest picture is the saved one. (The store drops staging at
    // startup, so this is not a race — the file is already known missing.)
    LaunchedEffect(Unit) {
        if (photoPending && !hasStagedPhoto()) {
            photoPending = false
            dialogPhoto = state.avatarPhoto
        }
    }

    // ── backup ────────────────────────────────────────────────────────
    // The two launchers live here, not in any button, for the same reason as
    // the photo picker: a result arrives at whoever registered the contract,
    // and that has to outlive the composable that started it.
    var pendingImport by remember { mutableStateOf<Backup.Archive?>(null) }
    // A one-line verdict under the buttons — written, refused, imported —
    // rather than a toast machinery or a snackbar whose lifetime belongs to
    // another screen. It says its piece and clears itself.
    var backupNote by remember { mutableStateOf("") }
    LaunchedEffect(backupNote) {
        if (backupNote.isNotEmpty()) {
            kotlinx.coroutines.delay(5_000)
            backupNote = ""
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            exportBackup(uri) { ok ->
                backupNote = if (ok) {
                    "Backup saved."
                } else {
                    "Couldn't write that file."
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            readBackup(uri) { archive ->
                backupNote = if (archive == null) {
                    "Not a Fyr backup, or it's damaged."
                } else {
                    ""
                }
                pendingImport = archive
            }
        }
    }

    val totalTicks = remember(state) {
        state.live.sumOf { Stats.total(it, state.completionsFor(it.id)) }
    }
    val retired = remember(state) { state.habits.filter { it.archived } }
    // The list of retired habits lives behind its own count rather than
    // beside it as a whole card: a card that is empty four days out of five
    // is a card teaching people to scroll past it. The trash is kept in the
    // same shape, for the same reason and the same bar.
    var showingRetired by rememberSaveable { mutableStateOf(false) }
    var showingTrash by rememberSaveable { mutableStateOf(false) }
    var confirmingEmpty by remember { mutableStateOf(false) }
    // The picture's delete badge used to fire the moment it was pressed —
    // one tap, no words, a photograph gone. Destructive actions get one
    // question first; and even the answer only *stages* the loss — the
    // picture leaves on Save, with everything else this dialog changes.
    var confirmingPhotoClear by remember { mutableStateOf(false) }

    // One page, one scroll. Nothing here swaps content out from under the
    // reader any more, so the state lives where it did before the tabs came
    // and went: inside the modifier that uses it.
    val scroll = rememberScrollState()

    // The masthead pill's figure: today taken across every habit due it — an
    // aggregate with its denominator printed on the face, which is the one
    // thing a single streak number could never be on a screen where several
    // streaks of different lengths live at once.
    val todayProgress = remember(state, today) { Stats.today(state, today) }

    // The rate the data row leads with: all habits, trailing thirty days,
    // answered as one number — the same completion figure the insights
    // screen shows, stated here where the counts are.
    val rate30 = remember(state, today) { Stats.overallRate(state, today) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .padding(bottom = contentPadding.calculateBottomPadding())
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── the masthead ─────────────────────────────────────────────────
        // The page opens on its own ground now — no band — with the three
        // things a profile exists for held in one line of sight: the title,
        // the count of what is still burning, the door to the editor. The
        // portrait below is the subject rather than an ornament on a
        // banner, and the name under it is set in its own face and the
        // page's own colour: the gold belonged to the band it used to sit
        // in, and the name says itself without it.
        ProfileMasthead(
            state = state,
            progress = todayProgress,
            ticks = totalTicks,
            onEdit = {
                // The dialog opens on the *saved* picture every time: what
                // a previous session staged and never saved died with that
                // session (see Store's init), so there is nothing to
                // reconcile — only something to start fresh from.
                dialogPhoto = state.avatarPhoto
                photoPending = false
                editingProfile = true
            },
            onAbout = { showingAbout = true },
        )

        // ── week starts on ─────────────────────────────────────────────
        // One preference with a blast radius: it re-axes every month grid,
        // every week strip, every heatmap and every weekday rail in the app,
        // because they all ask this same number where a week opens.
        Section("Week starts on") {
            // No track behind these. Two greys stacked in one control — a
            // container fill with a bordered pill sitting on top of it — read
            // as two unrelated objects, and the fill was carrying no
            // information the border was not already carrying. One pill, one
            // ring, on the card's own surface.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SegmentedOption(
                    label = "Sunday",
                    selected = state.weekStartDay == FyrState.DEFAULT_WEEK_START,
                    onClick = { onWeekStart(FyrState.DEFAULT_WEEK_START) },
                    modifier = Modifier.weight(1f),
                )
                SegmentedOption(
                    label = "Monday",
                    selected = state.weekStartDay == 1,
                    onClick = { onWeekStart(1) },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Used by every calendar and graph.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }

        // ── appearance ──────────────────────────────────────────────────
        // Deliberately absent. Fyr reads the system's dark flag, so the phone
        // already is the appearance setting; a second one in here would only
        // be somewhere for the two to disagree.

        // ── data ────────────────────────────────────────────────────────
        // The counts, out on the page instead of nested in a card inside a
        // tab. Three tiles where there were four: habits and ticks are not
        // repeated here — the line under the name already carries them —
        // so this row earns its place with what that line cannot say. How
        // reliably the days are kept leads, because it is the only figure
        // in the row with a denominator printed on its face; the two
        // archives follow, and each stays its own handle into the list
        // behind it.
        Text(
            text = "Your data",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            StatTile(
                label = "On time",
                value = if (rate30.due == 0) "—"
                else "${Math.round(rate30.hit * 100f / rate30.due)}%",
                hint = "last 30 days",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Retired",
                value = "${retired.size}",
                // The count is also the handle, and the line under it says
                // so out loud. A number that opens a list and a number that
                // does not must not look the same, which is how a person
                // ends up never finding the list at all.
                hint = listHint(retired.size, showingRetired),
                hintAccent = retired.isNotEmpty(),
                highlighted = showingRetired,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = retired.isNotEmpty()) {
                        showingRetired = !showingRetired
                        showingTrash = false
                    },
            )
            StatTile(
                label = "Trash",
                value = "${state.trash.size}",
                hint = listHint(state.trash.size, showingTrash),
                hintAccent = state.trash.isNotEmpty(),
                highlighted = showingTrash,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = state.trash.isNotEmpty()) {
                        showingTrash = !showingTrash
                        showingRetired = false
                    },
            )
        }

        if (showingRetired && retired.isNotEmpty()) {
            Section("Retired") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    retired.forEach { habit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(scheme.surfaceContainerHighest)
                                .clickable {
                                    showingRetired = false
                                    onOpenHabit(habit.id)
                                }
                                .padding(horizontal = 13.dp, vertical = 10.dp),
                        ) {
                            Emoji(habit.emoji, size = 26.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = habit.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Since ${Dates.shortDate(habit.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = scheme.primary,
                            )
                        }
                    }
                }
            }
        }

        // ── the trash ───────────────────────────────────────────────────
        // Deleting now *moves* rather than destroys, so the five-second
        // Undo bar is no longer the only way back and no longer has to be
        // raced. What the record was — schedule, ticks, the day it was
        // made — is carried intact, because a restore that brings back a
        // habit with a blank history would be a worse surprise than the
        // delete it replaced.
        if (showingTrash && state.trash.isNotEmpty()) {
            Section("Trash") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.trash.forEach { entry ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(scheme.surfaceContainerHighest)
                                .padding(
                                    start = 13.dp,
                                    end = 4.dp,
                                    top = 7.dp,
                                    bottom = 7.dp,
                                ),
                        ) {
                            Emoji(entry.habit.emoji, size = 26.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.habit.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "Deleted ${Dates.shortDate(entry.deletedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { onRestoreTrash(entry.habit.id) }) {
                                Text(
                                    text = "Put back",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.primary,
                                )
                            }
                            TextButton(onClick = { onPurgeTrash(entry.habit.id) }) {
                                Text(
                                    text = "Erase",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = scheme.error,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(scheme.primary.copy(alpha = 0.12f))
                        .clickable { confirmingEmpty = true }
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Empty trash",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = scheme.primary,
                    )
                }
            }
        }

        // ── backup ───────────────────────────────────────────────────────
        // The one thing a local-only app owes its user is a way out. No
        // cloud, no account and no automatic copy exist here *by manifest* —
        // so the exported file is the backup, and saying that plainly on the
        // page matters more than hiding it in a menu: a history that cannot
        // leave the phone is a history waiting to lose the phone. Import is
        // equally plain about its two options, because a restore that
        // silently replaced what was here would be the app losing data on
        // the user's behalf.
        Section("Backup") {
            Text(
                text = "No cloud, no account — export a file and keep it safe.",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                BackupAction(
                    label = "Export",
                    accent = true,
                    onClick = { exportLauncher.launch(Backup.suggestedName(today)) },
                    modifier = Modifier.weight(1f),
                )
                BackupAction(
                    label = "Import",
                    accent = false,
                    // json, plain text and octet-stream: file managers are
                    // wildly inconsistent about what they call a .json, and
                    // refusing the file on a mime technicality would read as
                    // the feature being broken. The parse decides, not the
                    // label.
                    onClick = {
                        importLauncher.launch(
                            arrayOf(
                                "application/json",
                                "text/plain",
                                "application/octet-stream",
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (backupNote.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = backupNote,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        // ── reset ───────────────────────────────────────────────────────
        // Deliberately absent. It was a red button that erased every tick on
        // the phone, sitting one scroll from the daily numbers and reachable
        // by the same thumb that records them. Nothing else in this screen is
        // destructive, so the one thing that was could only ever be pressed by
        // accident — and the app already has a per-habit delete with an undo,
        // which is the granularity at which people actually mean it.
        //
        // Deleting has since narrowed further: a deleted habit now waits in
        // the trash above instead of vanishing, so permanent erasure happens
        // one habit at a time from inside its own list, or through the single
        // Empty trash button behind a confirmation. That button is the only
        // destructive control left on this screen, and it says what it is
        // about to lose before it does.


        Spacer(Modifier.height(28.dp))
    }

    // About, behind the info mark. The whole card that used to sit at the
    // foot of this page, in a box that opens where the reader is standing
    // rather than where the page ends — the copy is read once, so it costs
    // the scroll it used to charge everyone for.
    if (showingAbout) {
        AboutDialog(onDismiss = { showingAbout = false })
    }

    // What the file holds, said out loud before either option is taken.
    // The dialog exists because both modes are destructive *in different
    // directions*: replace clears what is here, merge changes it — and the
    // person choosing has to see the counts (and the day it was exported)
    // at the moment they decide, not discover them afterwards.
    pendingImport?.let { archive ->
        val importedHabits = archive.state.habits.size
        // The same definition of "tick" every other counter in the app
        // uses — days that were genuinely due (Stats.total) — rather than
        // the raw number of marks the file holds. The raw marks all travel;
        // only the *number* is computed the way the masthead computes it, so
        // the dialog can't say 51 while the page under it says 50 over the
        // very same history (a mark on an excused day is data, but it is
        // not a tick anywhere else in Fyr either).
        val importedTicks = archive.state.live.sumOf { h ->
            Stats.total(h, archive.state.completionsFor(h.id))
        }
        val choose: (Backup.Mode) -> Unit = { mode ->
            val accepted = pendingImport
            pendingImport = null
            if (accepted != null) {
                applyBackup(accepted, mode) { ok ->
                    backupNote = if (ok) "Backup imported." else "Couldn't import that file."
                }
            }
        }
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            containerColor = scheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Import backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = buildString {
                            append(importedHabits)
                            append(if (importedHabits == 1) " habit · " else " habits · ")
                            append(importedTicks)
                            append(if (importedTicks == 1) " tick" else " ticks")
                            if (archive.exportedAt > 0) {
                                append(" · ${Dates.dayTitle(archive.exportedAt)}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = "Merge — keeps what's here, adds the rest.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Replace — clears this phone, then copies the file.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.error.copy(alpha = 0.9f),
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { choose(Backup.Mode.REPLACE) }) {
                        Text("Replace", color = scheme.error)
                    }
                    TextButton(onClick = { choose(Backup.Mode.MERGE) }) {
                        Text("Merge")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (confirmingEmpty) {
        AlertDialog(
            onDismissRequest = { confirmingEmpty = false },
            containerColor = scheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Empty trash?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
            },
            text = {
                Text(
                    text = buildString {
                        append(state.trash.size)
                        append(if (state.trash.size == 1) " habit" else " habits")
                        append(
                            " and their ticks — gone for good. No way back.",
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingEmpty = false
                        onEmptyTrash()
                    },
                ) {
                    Text(
                        text = "Empty",
                        fontWeight = FontWeight.Medium,
                        color = scheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingEmpty = false }) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.Medium,
                        color = scheme.primary,
                    )
                }
            },
        )
    }

    if (editingProfile) {
        ProfileDialog(
            initialName = state.userName,
            photo = dialogPhoto,
            // The dialog stays up behind the picker: what it is editing does
            // not change when the picker opens, and coming back to a box that
            // has already closed is the phone deciding you are finished.
            onChoosePhoto = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemovePhoto = { confirmingPhotoClear = true },
            onDismiss = {
                editingProfile = false
                // Whatever was staged stays unstaged: the saved picture is
                // the one the profile keeps, which is the whole promise
                // this dialog makes.
                discardPhoto()
            },
            onSave = { name ->
                editingProfile = false
                onName(name)
                // The picture's half of the edit, on the same press: adopt
                // what was staged, or carry out the staged removal. Neither
                // has touched the profile a moment before this line — and
                // a dialog that never went near the picture does neither.
                when {
                    !photoPending -> Unit
                    dialogPhoto == STAGED_AVATAR -> adoptPhoto()
                    else -> onAvatarClear()
                }
            },
        )
    }

    // The one question the picture's delete badge asks. Composed after the
    // editor so it lands on top of it, and it clears nothing even when
    // "Remove" is pressed — the badge alone only ever opens this, and this
    // only ever *stages* the loss. Save is what lets go.
    if (confirmingPhotoClear) {
        AlertDialog(
            onDismissRequest = { confirmingPhotoClear = false },
            containerColor = scheme.surfaceContainerHigh,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(scheme.error.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        glyph = Glyph.DELETE,
                        color = scheme.error,
                        size = 24.dp,
                        strokeWidth = 2.2.dp,
                    )
                }
            },
            title = {
                Text(
                    text = "Remove your photo?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
            },
            text = {
                Text(
                    // Staged, and the dialog says so: the picture leaves on
                    // Save — Cancel here or in the editor puts it back —
                    // and nothing else about the profile is part of this
                    // question.
                    text = "Leaves on Save. Everything else stays as it is.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingPhotoClear = false
                        dialogPhoto = ""
                        photoPending = true
                    },
                ) {
                    Text(
                        text = "Remove",
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingPhotoClear = false }) {
                    Text(
                        text = "Keep it",
                        fontWeight = FontWeight.Medium,
                        color = scheme.primary,
                    )
                }
            },
        )
    }
}


/**
 * The About card, in a box of its own.
 *
 * Everything the foot of the profile page used to carry — the mark, the
 * name with its version, how Fyr keeps score, and the credit with its
 * two doors — held in one dialog an inch from the info mark that asked for
 * it. It opens on identity rather than a heading: the mark breathes on a
 * radial of its own ember, the name signs it, and two stamps carry the
 * facts that used to be a clause under the name. Below that the card is
 * structure — the scoring rule on a well with an ember rule down its side,
 * the credit as two doors of one identical design, and a Close as wide as
 * the card itself, so the way out is the easiest target on it.
 *
 * The page behind it takes the same frost the profile editor asks for:
 * one blur, either box, so both read as glass over a room that has
 * stepped back rather than a card pasted on a screen still demanding taps.
 *
 * Dismissed by Close, the scrim, or back — it is information, so there is
 * no confirm button asking the reader to agree with anything.
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The mark on a breath of its own ember — a radial wash
                // drawn behind it rather than the ruled cell the old row
                // filed it into: identity first, like a cover, before any
                // line of copy asks to be read.
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(
                                        scheme.primary.copy(alpha = 0.18f),
                                        scheme.primary.copy(alpha = 0.05f),
                                        scheme.primary.copy(alpha = 0f),
                                    ),
                                    center = this.center,
                                    radius = size.minDimension / 2f,
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    FlameBadge(size = 58.dp)
                }

                Text(
                    text = "Fyr",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )

                Spacer(Modifier.height(8.dp))

                // Two stamps, one fact each — the version and the promise —
                // outlined rather than filled so they read as printing on
                // the cover instead of buttons waiting to be pressed.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetaChip(text = "v${BuildConfig.VERSION_NAME}", accent = true)
                    MetaChip(text = "no account  ·  no tracking", accent = false)
                }

                Spacer(Modifier.height(16.dp))

                // The scoring rule, on a well with an ember rule down its
                // side. The bar is what makes the eye file this as a note
                // before it reads a word: the fine print keeps its size
                // and gains a container to be small *in*.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(12.dp))
                        .background(scheme.surfaceContainerHighest),
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(scheme.primary.copy(alpha = 0.55f)),
                    )
                    Text(
                        // Small on purpose: this is the fine print of how
                        // Fyr keeps score, read once and remembered.
                        text = "One missed day ends the streak.",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 13.dp,
                            top = 11.dp,
                            bottom = 11.dp,
                            end = 14.dp,
                        ),
                    )
                }

                Spacer(Modifier.height(18.dp))

                // The credit, as two doors side by side: equal halves of
                // one line, same size, same weight, same hit — the pair
                // reads as a set rather than as a stack to scroll past.
                // The handle is gone from the face of each: the address
                // lives inside the link itself, and printing it under the
                // platform's name was one label too many for a button.
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Text(
                        text = "DEVELOPER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        DeveloperLink(
                            url = "https://github.com/$DEVELOPER_HANDLE",
                            icon = R.drawable.ic_github,
                            platform = "GitHub",
                            modifier = Modifier.weight(1f),
                        )
                        DeveloperLink(
                            url = "https://www.linkedin.com/in/$DEVELOPER_HANDLE",
                            icon = R.drawable.ic_linkedin,
                            platform = "LinkedIn",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {
            // Full width: the card's one action is leaving it, so the way
            // out is as wide as the card — a target you cannot miss at the
            // bottom edge, in the same filled ember Save wears.
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Close",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}


/**
 * One stamp under the name: version and promise, printed not pressable.
 *
 * Outlined rather than filled because these two say what the card *is* —
 * a version and a vow — where a filled chip in this app is asked for a
 * decision. The version keeps the ember (it is Fyr's own number); the
 * promise sits in the quiet ink, because it is a fact, not a feature.
 */
@Composable
private fun MetaChip(
    text: String,
    accent: Boolean,
) {
    val scheme = MaterialTheme.colorScheme

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (accent) scheme.primary else scheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = if (accent) {
                    scheme.primary.copy(alpha = 0.55f)
                } else {
                    scheme.outlineVariant
                },
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}


/**
 * The one editor for the line at the top of the profile: name and picture
 * together, because they are one identity wearing two shapes and asking
 * about them twice was the app being unnecessarily thorough at the user's
 * expense.
 *
 * The name is asked in its two parts, because that is how it is stored and
 * shown: first for the greeting, last for the profile, and the save joins
 * them. Each field stops at [FyrState.NAME_LIMIT] on the keystroke rather
 * than refusing the word afterwards — a field that accepts anything and
 * then rejects it feels broken; a field that simply stops is understood
 * immediately.
 *
 * The portrait wears the shared [PortraitHalo] — the wavy ember ring on
 * the picture's own edge, the hairline beyond it, the beads outside that
 * — because this picture is the identity the whole card is about and it
 * should wear the app's one frame exactly the way the masthead and the
 * first-launch form wear it. On its bottom rim, *one* control instead of
 * the pair it used to carry: the bin while there
 * is a picture to lose, the tray the moment there is not. Both icons at
 * once asked a person to read "…or take it away" on a picture they had
 * just chosen to add; a control that only exists when it can act is the
 * rarer, plainer sentence.
 *
 * The picture half is the platform's own photo picker: no storage
 * permission, no crop step. The copy into the app's own directory happens
 * the moment the URI arrives (see [com.fyr.data.Store.stageAvatar]),
 * because the grant behind it does not outlive the handover — but the
 * copy lands *beside* the profile, and only Save
 * [com.fyr.data.Store.adoptStagedAvatar] turns it into the picture the rest
 * of the app reads. There is no gallery
 * of built-in marks either — six silhouettes was a decision Fyr was making
 * on the user's behalf, and the plain mark underneath is the default
 * rather than the first option.
 *
 * The dialog stays open while the picker is up, so coming back shows
 * the new picture where the old one was.
 */
@Composable
private fun ProfileDialog(
    initialName: String,
    photo: String,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    // Saveable: a rotation mid-edit used to reset the field to whatever the
    // dialog opened with, which for a profile being renamed meant losing the
    // name that had just been typed.
    var first by rememberSaveable {
        mutableStateOf(initialName.substringBefore(' ').trim())
    }
    var last by rememberSaveable {
        mutableStateOf(initialName.substringAfter(' ', "").trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        title = {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── the portrait, wearing its control on the rim ──────
                // The full frame, same as the masthead and the first
                // launch: breath under, the wavy ember ring hugging the
                // picture's edge, a thin ring beyond it and both rings of
                // beads outside that. One action rides the bottom point —
                // half in, half out: a bin while there is a picture to
                // lose, the tray the moment there is not, because a
                // removed or brand-new portrait needs the way *in* and
                // offering both at once buried it under the way out.
                PortraitHalo(
                    photo = photo,
                    name = first.trim(),
                    photoSize = 96.dp,
                    badge = {
                        if (photo.isEmpty()) {
                            PhotoBadge(
                                glyph = Glyph.UPLOAD,
                                accent = true,
                                description = "Choose a new photo",
                                knockout = scheme.surfaceContainerHigh,
                                onClick = onChoosePhoto,
                            )
                        } else {
                            PhotoBadge(
                                glyph = Glyph.DELETE,
                                description = "Remove the photo",
                                knockout = scheme.surfaceContainerHigh,
                                onClick = onRemovePhoto,
                            )
                        }
                    },
                )

                Spacer(Modifier.height(18.dp))

                // ── the name, in its own face ─────────────────────────
                // The fields wear what they are holding: Poppins Medium,
                // the same face the profile states the name in, in the
                // dialog's own text colour. The gold belonged to the band
                // the name used to sit under; here on the card there is no
                // band, so the name is typed in the ink it will be read in
                // outside. The counters stay grey and small — rules, not
                // signatures.
                NameField(
                    value = first,
                    onValueChange = { first = nameAsTyped(it) },
                    label = "First name",
                    placeholder = "For the greeting",
                )
                Spacer(Modifier.height(12.dp))
                NameField(
                    value = last,
                    onValueChange = { last = nameAsTyped(it) },
                    label = "Last name",
                    placeholder = "Shown on your profile",
                )
            }
        },
        confirmButton = {
            Button(
                enabled = first.isNotBlank(),
                onClick = {
                    onSave(
                        listOf(first.trim(), last.trim())
                            .filter(String::isNotBlank)
                            .joinToString(" "),
                    )
                },
            ) {
                Text(
                    text = "Save",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = scheme.onSurfaceVariant)
            }
        },
    )
}

/**
 * One half of the name pair, in the editor's own voice: Poppins in the ink
 * the name will be read in outside, the accent rule under the line being
 * typed, and the character count kept small and grey — a rule, not a
 * signature.
 */
@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        // Every word offered capitalised — the field enforces the same
        // rule on whatever arrives, suggestion or keystroke alike.
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        label = { Text(label, color = scheme.onSurfaceVariant) },
        placeholder = { Text(placeholder, color = scheme.onSurfaceVariant.copy(alpha = 0.5f)) },
        shape = RoundedCornerShape(16.dp),
        textStyle = TextStyle(
            fontFamily = Poppins,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurface,
        ),
        supportingText = {
            Text(
                text = "${value.length}/${FyrState.NAME_LIMIT}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outlineVariant,
            cursorColor = scheme.primary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The masthead: the title with its two doors, then the portrait, the name
 * and today's count under it, centred on the page's own ground.
 *
 * The band is gone. It was carrying the portrait and title the way a banner
 * carries things, which is to say as decoration around them; on the plain
 * ground the portrait becomes the subject and the row above becomes what it
 * always was — a heading with two controls at its shoulder.
 *
 * The coin no longer wears a streak, and no longer sits at the title's
 * shoulder either. A streak belongs to one habit, and on a screen where
 * several habits live at once, a bare `46` at the top is a riddle: whose
 * 46? The coin counts *today* across every habit due it — done over due,
 * with the ring saying the same figure in shape — and it lives under the
 * name now, where a profile answers *how is today going* rather than
 * where a toolbar keeps its buttons. The streaks themselves live on
 * Insights, next to the names they belong to.
 *
 * Two roads at the title's shoulder: the pencil to the editor, the info
 * mark to About. The portrait and the name are content, not doors — a
 * name that opens a card when you meant to read it is a trap, and the
 * two symbols say what they do in the one mark that means it.
 */
@Composable
private fun ProfileMasthead(
    state: FyrState,
    progress: Stats.TodayProgress,
    ticks: Int,
    onEdit: () -> Unit,
    onAbout: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val portrait = 104.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        // Centred as a column, not line by line: the portrait, the name and
        // the count below it all wrap to their own content, so without one
        // shared axis each would find its own edge — the name centred by its
        // own full-width box while the portrait beside it sat flush left.
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onBackground,
            )
            Spacer(Modifier.weight(1f))

            // Two doors at the title's shoulder, in the order a reader
            // meets them: the pencil that changes the profile, then the
            // info mark that explains the app around it — edit first
            // because it is the one a returning hand reaches for, about
            // second because it is the one nobody needs twice.
            //
            // The editor, behind the one symbol that names editing. A gear
            // would promise preferences; the cards below already carry
            // every preference this screen has.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = scheme.outlineVariant.copy(alpha = 0.7f),
                        shape = CircleShape,
                    )
                    .clickable(role = Role.Button, onClick = onEdit)
                    // A pencil with no words is nothing to TalkBack — the
                    // row below already says "your name", so this speaks only
                    // what the icon itself means.
                    .semantics { contentDescription = "Edit profile" },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.EDIT,
                    color = scheme.onSurfaceVariant,
                    size = 18.dp,
                    strokeWidth = 2.dp,
                )
            }

            Spacer(Modifier.width(10.dp))

            // About this app — one tap from the masthead, no section to
            // scroll for. The info mark is the universal sign for "read
            // more", and putting it beside the pencil keeps the two doors
            // at the title's shoulder: one changes the profile, one
            // explains the app that holds it.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceContainerHighest)
                    .border(
                        width = 1.dp,
                        color = scheme.outlineVariant.copy(alpha = 0.7f),
                        shape = CircleShape,
                    )
                    .clickable(role = Role.Button, onClick = onAbout)
                    .semantics { contentDescription = "About this app" },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.INFO,
                    color = scheme.onSurfaceVariant,
                    size = 18.dp,
                    strokeWidth = 2.dp,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // The portrait, whole and centred — wearing the one frame every
        // identity in Fyr wears: the wavy ember ring riding the picture's
        // own edge, the hairline beyond it, two rings of beads outside
        // that, and the theme's lift beneath — black shadow on paper, an
        // ember glow in the dark — so the identity at the top of this page
        // is dressed exactly like the control that adds things and the
        // form that first asked for it. At this size it is the subject,
        // so it still gets no banner behind it; the frame holds it the way
        // the hump frames the button rather than the way a card would
        // frame a picture.
        //
        // Not a button. The pencil above is the editor; a picture that
        // opens a card on contact is one more way to land somewhere you
        // did not point at.
        PortraitHalo(
            photo = state.avatarPhoto,
            name = state.userName,
            photoSize = portrait,
        )

        Spacer(Modifier.height(16.dp))

        // The name in its own voice and the page's own colour: Poppins,
        // Medium, no gradient — the gold was the old band's accent borrowed
        // by the text, and a name is a fact about a person rather than a
        // piece of the banner it used to sit in. One face for the profile
        // (this and the field that edits it), the system face for
        // everything else.
        Text(
            text = state.userName.ifEmpty { "No name set" },
            fontFamily = Poppins,
            fontWeight = FontWeight.Medium,
            fontSize = 27.sp,
            color = scheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(10.dp))

        // Today, on a coin under the name it belongs to: the ring is the
        // fraction, the text is its arithmetic, and neither can be
        // mistaken for a version number or someone else's streak. It
        // moved down from the title's shoulder because up there it was
        // one more button among the doors; here it answers the first
        // question a profile asks — how is today going — in the slot
        // where a social page would put its headline stat. The text goes
        // accent when the day is cleared — the same moment the ring
        // closes — because that is the one state worth saying out loud.
        val allClear = progress.total > 0 && progress.done == progress.total
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(scheme.surfaceContainerHighest)
                .border(
                    width = 1.dp,
                    color = scheme.outlineVariant.copy(alpha = 0.7f),
                    shape = CircleShape,
                )
                .padding(horizontal = 13.dp),
        ) {
            Canvas(Modifier.size(15.dp)) {
                val stroke = 2.6.dp.toPx()
                val r = (size.minDimension - stroke) / 2f
                drawCircle(
                    color = scheme.outlineVariant,
                    radius = r,
                    style = Stroke(width = stroke),
                )
                if (progress.total > 0 && progress.done > 0) {
                    drawArc(
                        color = scheme.primary,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.fraction,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                    )
                }
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (progress.total == 0) "Nothing due"
                else "${progress.done}/${progress.total} today",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (allClear) scheme.primary else scheme.onSurface,
            )
        }

        Spacer(Modifier.height(10.dp))

        // The line where a social screen would put its followers: habits and
        // ticks, two facts and a dot — the whole screen's arithmetic said
        // once, asking for nothing. Coloured as the two facts they are:
        // each phrase takes the accent, the dot between them stays in the
        // grey it is — punctuation rather than content — so the eye reads
        // two answers, not one grey sentence to decode.
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.Medium)) {
                    append(
                        "${state.live.size} ${if (state.live.size == 1) "habit" else "habits"}",
                    )
                }
                withStyle(SpanStyle(color = scheme.onSurfaceVariant)) {
                    append("  \u00b7  ")
                }
                withStyle(SpanStyle(color = scheme.primary, fontWeight = FontWeight.Medium)) {
                    append("$ticks ${if (ticks == 1) "tick" else "ticks"}")
                }
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * One of the two backup buttons — equal weight, equal height, split only by
 * emphasis: export is the accent (the thing worth doing), import sits back on
 * the card's own surface (the thing you do when you need it). Same 13dp
 * corner and vertical rhythm as the trash bar below, so the row reads as part
 * of the same furniture rather than as a different kind of control.
 */
@Composable
private fun BackupAction(
    label: String,
    accent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(
                if (accent) scheme.primary.copy(alpha = 0.12f)
                else scheme.surfaceContainerHighest,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (accent) scheme.primary else scheme.onSurface,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // The page gutter rides on the card rather than on the column
            // above it: this inset is what lines every card up under the
            // masthead's title and the tile row, whichever is showing.
            .padding(horizontal = 20.dp)
            // Then shape, paint, inset — in that order and no other.
            // Padding *this card* first would have inset the paint instead
            // of the content: the card would draw 16dp inside its own
            // bounds while the text sat flush against the rounded corners
            // and got clipped by them. Clip before paint so the fill is cut
            // to the card, and pad after so the content lives inside it.
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/**
 * One fact standing on the page: a figure, the name of what it counts, and
 * the line saying what it is worth.
 *
 * Three across instead of four-fifths-of-a-row inside a card inside a
 * section: at this width the figure can be read at a glance and the hint
 * under it fits whole. Left-aligned like every other panel of numbers the
 * app draws — a centred number floats, a left-aligned one starts where the
 * eye starts.
 *
 * Tinted whole when the list behind it is open: the count is the handle,
 * and lighting it is how an open drawer reads from the row that pulled it.
 */
@Composable
private fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    hint: String = "",
    hintAccent: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                // The tiles that can be pressed say so in the accent rather
                // than in an arrow: they are numbers, and a number that
                // lights up reads as the number you just opened.
                if (highlighted) scheme.primary.copy(alpha = 0.14f)
                else scheme.surfaceContainerHighest,
            )
            .padding(horizontal = 13.dp, vertical = 14.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (highlighted) scheme.primary
            else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The third line is what the number *is for*. Two of these counts
        // open a list and one does not, and without this line the three of
        // them look identical — which is how a list of everything you have
        // deleted ends up being a thing nobody ever finds.
        if (hint.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (highlighted || hintAccent) scheme.primary
                    else scheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * What the line under a count that opens a list says.
 *
 * Worded as a fact rather than an instruction when there is nothing in it,
 * because "tap to open" over a zero is an invitation to an empty room.
 *
 * Every one of these is short on purpose. The tile is a quarter of a
 * four-across row — about sixty usable dp — and the hint is set in the
 * smallest type Fyr uses, so "nothing here" arrived one word past the edge
 * and Compose politely ate it and hung an ellipsis off the end. A hint that
 * reads "nothing…" tells the reader that something failed rather than that
 * something is empty, which is the opposite of what it is there for.
 */
private fun listHint(count: Int, open: Boolean): String = when {
    count == 0 -> "none yet"
    open -> "tap to close"
    else -> "tap to open"
}

/** One identity, two homes — the address behind both links, never printed on them. */
private const val DEVELOPER_HANDLE = "su6osec"

/**
 * A link to where Fyr came from, shaped to sit half a line's width.
 *
 * A filled chip rather than a bordered card: no outline, no handle, no
 * arrow-corner — icon, name and the one small "↗" the card owns, all in
 * the accent, centred as a single mark. The handle used to print here and
 * made every button two labels wide for an address the link already
 * carries; the old outline made them look like settings rows rather than
 * places to go. Equal weight with its sibling ([Modifier.weight] from the
 * row) keeps GitHub and LinkedIn two halves of one pair.
 *
 * The browser is opened with a plain view intent, so an unconfigured device
 * just no-ops instead of crashing.
 */
@Composable
private fun DeveloperLink(
    url: String,
    @DrawableRes icon: Int,
    platform: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHighest)
            .clickable {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            .padding(vertical = 13.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = platform,
            tint = scheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = platform,
            fontFamily = Qurova,
            fontWeight = FontWeight.Medium,
            color = scheme.primary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            // The way out of the app, in the accent beside its own name:
            // the only arrow on the card, so its direction needs no legend.
            text = "↗",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.primary,
        )
    }
}
