package com.fyr.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fyr.data.Category
import com.fyr.data.Dates
import com.fyr.data.Habit
import com.fyr.data.Suggestions
import com.fyr.ui.components.Emoji
import com.fyr.ui.components.Glyph
import com.fyr.ui.components.GlyphIcon
import com.fyr.ui.components.MiniCalendar
import com.fyr.ui.components.rememberDayStyle

/**
 * Adding a habit.
 *
 * The sheet opens on the shelf alone, because the decision that stalls people
 * is *what* to track, not *how*. **Add habit** is live from the first frame
 * and swaps the shelf out entirely for the write-your-own form — two paths,
 * one at a time, each owning the whole body. The shelf used to stay behind,
 * dimmed to 42% with the form appended underneath it, which left the form half
 * a screen tall and dropped its first field below the fold.
 *
 * The header follows: it reads "Write your own" with a chevron back to the
 * shelf, because a shelf you can no longer reach needs a door rather than a
 * fade.
 *
 * The sheet is three parts. The header stays put. The body scrolls. The footer
 * does *not* scroll: it holds the one control that commits what you typed,
 * which would otherwise live hundreds of dp below the fold. [imePadding] is on
 * the outer column so that footer rides above the keyboard instead of behind
 * it; where the window already resizes for the keyboard the inset reads zero
 * and the padding costs nothing.
 *
 * There is deliberately no drag handle. The sheet opens flush with the top of
 * the display, so the grab bar sat inside the status bar and behind the camera
 * cutout — a control the finger cannot usefully reach. The close cross in the
 * header does the same job from somewhere the hand already is.
 *
 * The weekday row defaults to all seven. Someone training six days a week can
 * untap Sunday in one tap; everyone else never has to look at it.
 *
 * **Started on** lets a streak be backdated — starting on Monday and reporting
 * it on Thursday would otherwise be impossible. It is shown as the date it
 * holds, in the accent, above a month-sized calendar: a value you cannot see
 * is a value nobody thinks to change. The calendar *is* the picker — there is
 * no separate "choose date" dialog over the top of it, because a second date
 * control two taps away from the first one is a second place for the answer to
 * be wrong. Days in between are still blank and still reset under the strict
 * rule; they get filled in from the calendar, exactly as any other missed day
 * would.
 *
 * The shelf opens with **your habits** as a row of pills: what you have
 * already written down, so you never have to type it twice. The cross on a
 * pill takes *the pill* off the shelf and nothing else. It used to reach
 * through to the habit itself and delete the row on Today — so the most
 * convenient control in the sheet, the one sitting under the thumb of someone
 * tidying up, was also the most dangerous one, and clearing a shelf was
 * quietly clearing the record with it. The habit stays where it is; only the
 * shortcut to it goes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddHabitSheet(
    onDismiss: () -> Unit,
    onCreate: (habit: Habit) -> Unit,
    newId: () -> String,
    today: Int,
    weekStart: Int,
    habits: List<Habit>,
    onHidePill: (habitId: String) -> Unit,
    initialWrite: Boolean = false,
    initialDatePicker: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme

    var customName by rememberSaveable { mutableStateOf("") }
    var customEmoji by rememberSaveable { mutableStateOf(Suggestions.palette.first()) }
    var weekdays by rememberSaveable { mutableStateOf("1,2,3,4,5,6,7") }
    // Shown when someone tries to clear the *last* lit day. That tap used to
    // go through, leaving the control showing no days at all while the habit
    // quietly meant every one of them (empty schedules fall back to daily on
    // save) — a silent contradiction between what the screen says and what
    // gets written. The last day now stays lit and says why.
    var weekdayHint by remember { mutableStateOf(false) }
    LaunchedEffect(weekdayHint) {
        if (weekdayHint) {
            kotlinx.coroutines.delay(3_000)
            weekdayHint = false
        }
    }
    // The old `date` launch hook used to open the Material date picker; the
    // inline month is the picker now, so it opens the form that contains it.
    var writing by rememberSaveable { mutableStateOf(initialWrite || initialDatePicker) }
    var startDay by rememberSaveable { mutableStateOf(today) }
    // Whether the *person* chose the start day, as opposed to it being seeded
    // from the day the sheet opened. Opened at 23:55 and submitted at 00:05,
    // and the habit would be born yesterday — due for a day it never existed
    // on, counted in rates it never took part in. Until a choice is made the
    // seed follows the day; [Store.setHabit] clamps at the write edge anyway,
    // so both guards have to fail before a future-born habit is possible.
    var startPicked by rememberSaveable { mutableStateOf(false) }
    // Which month the inline calendar is showing. Held apart from the chosen
    // day so that stepping back through months to find the right one does not
    // drag the selection along with it.
    var startMonthDay by rememberSaveable {
        mutableStateOf(Dates.epochDay(Dates.startOfMonth(today)))
    }
    LaunchedEffect(today) {
        if (!startPicked && startDay < today) {
            startDay = today
            startMonthDay = Dates.epochDay(Dates.startOfMonth(today))
        }
    }

    val selectedWeekdays = weekdays.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    val startMonth = remember(startMonthDay) { Dates.startOfMonth(startMonthDay) }
    val dayStyle = rememberDayStyle()

    val scroll = rememberScrollState()

    // Entering write mode starts the form at the top rather than wherever the
    // shelf had been scrolled to. The shelf is not merely dimmed any more — it
    // is gone — so its position is not a position anything still has.
    LaunchedEffect(writing) {
        if (writing) scroll.scrollTo(0)
    }

    fun goToStartMonth(day: Int) {
        startDay = day
        startPicked = true
        startMonthDay = Dates.epochDay(Dates.startOfMonth(day))
    }

    fun submitCustom() {
        val name = customName.trim()
        if (name.isEmpty()) return
        onCreate(
            Habit(
                id = newId(),
                name = name,
                emoji = customEmoji,
                category = Category.LIFESTYLE,
                createdAt = startDay,
                weekdays = if (selectedWeekdays.isEmpty()) Habit.DEFAULT_WEEK else selectedWeekdays,
            )
        )
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = scheme.surfaceContainerHigh,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Nothing at the top centre: that slot lands in the status bar.
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // Full height, not content height. A sheet that grew only as
                // tall as its shelf made the write form — and the footer
                // button that commits it — sit a finger away from the bottom
                // edge, with the rest of the screen showing through. Filling
                // the window is what keeps the body one continuous scroll and
                // the action pinned to the floor. The insets sit after the
                // fill so the content still starts below the cutout.
                .fillMaxHeight()
                .statusBarsPadding()
                .imePadding(),
        ) {
            // ── header ────────────────────────────────────────────────────
            //
            // The top padding replaces the grab bar it succeeds, so the title
            // keeps its distance from the status bar. In write mode the title
            // is also the way back out: the shelf is not a background element
            // any more, so it needs a door rather than a fade.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 20.dp, top = 14.dp, bottom = 6.dp),
            ) {
                if (writing) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { writing = false }
                            .semantics { contentDescription = "Back to your habits" },
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(Glyph.CHEVRON_LEFT, color = scheme.onSurfaceVariant, size = 20.dp)
                    }
                    Spacer(Modifier.width(6.dp))
                } else {
                    // Holds the title on the same left edge as before the back
                    // control existed, so nothing shifts when it appears.
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (writing) "Write your own" else "New habit",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceContainerHighest)
                        .clickable(onClick = onDismiss)
                        .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.CLOSE, color = scheme.onSurfaceVariant, size = 18.dp)
                }
            }

            // ── body ──────────────────────────────────────────────────────
            //
            // The body is what sets the sheet's height, so filling its share
            // pins the footer to the bottom of the screen and the header to
            // the top of it whatever is inside. It is still bounded by what
            // is left after header and footer, which is what lets it scroll
            // without pushing the action off the bottom.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scroll)
                    .padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(14.dp))

                // The two paths are no longer sharing one scroll. Writing used
                // to dim the shelf and append the form beneath it, which left
                // the form half a screen tall and its first field — "Started
                // on" — sitting below the fold where it was invisible and
                // therefore never set. Each path now owns the body outright.
                if (!writing) {
                    // ── what you already have ──────────────────────────
                    //
                    // First, because it is the only thing on this shelf that
                    // is not an offer. A habit you created yesterday and a
                    // habit Fyr suggests both end up in the same list, so the
                    // list is shown here before anything is added to it —
                    // otherwise the shelf reads as a catalogue you are
                    // browsing rather than as a drawer you are adding to.
                    if (habits.isNotEmpty()) {
                        Text(
                            text = "Your habits",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            habits.forEach { habit ->
                                HabitPill(
                                    habit = habit,
                                    onRemove = { onHidePill(habit.id) },
                                )
                            }
                        }
                    }

                    // ── prebuilt shelf ─────────────────────────────────
                    //
                    // No search field. The shelf is one screenful of pill-sized
                    // answers grouped by what they are for, and a search bar
                    // above it would only ever filter something the eye has
                    // already passed — while implying there is a catalogue too
                    // large to browse, which there is not. Whatever it does not
                    // offer, you write in the form.
                    Category.entries.forEach { category ->
                        val items = Suggestions.all.filter { it.category == category }
                        if (items.isEmpty()) return@forEach

                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 10.dp),
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items.forEach { s ->
                                SuggestionChip(
                                    emoji = s.emoji,
                                    label = s.name,
                                    onClick = {
                                        onCreate(
                                            Habit(
                                                id = newId(),
                                                name = s.name,
                                                emoji = s.emoji,
                                                category = s.category,
                                                createdAt = today,
                                                weekdays = Habit.DEFAULT_WEEK,
                                            )
                                        )
                                        onDismiss()
                                    },
                                )
                            }
                        }
                    }
                } else {
                    // ── your own ───────────────────────────────────────
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = customName,
                            onValueChange = { customName = it },
                            placeholder = { Text("e.g. No phone before bed") },
                            singleLine = true,
                            // The keyboard itself commits the habit, so a name
                            // typed never depends on finding a button below.
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submitCustom() }),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = scheme.primary,
                                unfocusedBorderColor = scheme.outlineVariant,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(Modifier.height(14.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Suggestions.palette.forEach { e ->
                                val selected = e == customEmoji
                                Box(
                                    modifier = Modifier
                                        // Smaller cells, which also means more
                                        // of them across: at 42dp the thirty
                                        // marks took five rows of a form whose
                                        // real estate belongs to the calendar.
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) scheme.primary.copy(alpha = 0.18f)
                                            else scheme.surfaceContainerHighest
                                        )
                                        .clickable { customEmoji = e },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Emoji(e, size = 20.dp)
                                }
                            }
                        }

                        // ── start date ──────────────────────────────────
                        //
                        // A calendar, not a one-line summary of one, and not
                        // a button beside it either. The chosen day is written
                        // out large because a value you cannot see is one
                        // nobody thinks to change — and the month underneath
                        // is already the control for changing it, so a
                        // "Choose date" button was a door onto a second
                        // calendar standing in front of the first.
                        Spacer(Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Started on",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            if (startDay != today) {
                                TextButton(onClick = { goToStartMonth(today) }) {
                                    Text(
                                        text = "Today",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = scheme.primary,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (startDay == today) "Today, ${Dates.dayTitle(startDay)}"
                            else Dates.dayTitle(startDay),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(Modifier.height(8.dp))

                        // A whole month, tap to choose. Inert after today:
                        // a streak cannot have started tomorrow.
                        MiniCalendar(
                            month = startMonth,
                            today = today,
                            selectedDay = startDay,
                            style = dayStyle,
                            weekStart = weekStart,
                            onPrev = {
                                startMonthDay = Dates.epochDay(startMonth.minusMonths(1))
                            },
                            onNext = {
                                startMonthDay = Dates.epochDay(startMonth.plusMonths(1))
                            },
                            prevEnabled = Dates.epochDay(startMonth) > 1,
                            nextEnabled = Dates.epochDay(startMonth) <
                                Dates.epochDay(Dates.startOfMonth(today)),
                            onDayClick = {
                                startDay = it
                                startPicked = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // ── schedule ────────────────────────────────────
                        Spacer(Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Days",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { weekdays = "1,2,3,4,5,6,7" }) {
                                Text(
                                    text = if (selectedWeekdays.size == 7) "All" else "Every day",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = scheme.primary,
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // The same order the calendars open on, so the
                            // row and the grid above it read as one week.
                            for (day in Dates.weekOrder(weekStart)) {
                                val active = selectedWeekdays.contains(day)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        // 34 rather than 44: the labels here
                                        // are one letter each, and a control
                                        // that only ever says "T" does not
                                        // need to be as tall as a button that
                                        // says something.
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (active) scheme.primary.copy(alpha = 0.18f)
                                            else scheme.surfaceContainerHighest
                                        )
                                        .clickable {
                                            if (active && selectedWeekdays.size == 1) {
                                                // The schedule must name at least
                                                // one day; say so rather than
                                                // silently meaning "all of them".
                                                weekdayHint = true
                                            } else {
                                                val next =
                                                    if (active) selectedWeekdays - day
                                                    else selectedWeekdays + day
                                                weekdays = next.sorted().joinToString(",")
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = Dates.weekdayName(day, narrow = true),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight =
                                            if (active) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (active) scheme.primary else scheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }

                        if (weekdayHint) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Keep at least one day on.",
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(20.dp))
                    }
                }
            }

            // ── footer ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Button(
                    onClick = {
                        if (writing) submitCustom() else writing = true
                    },
                    enabled = !writing || customName.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text(
                        text = if (writing) "Create habit" else "Add habit",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Unlimited habits — nothing is locked.",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    emoji: String,
    label: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    // A fixed height is what makes a wrapped row read as a row: every pill is
    // the same rectangle regardless of how long its label runs. 34dp rather
    // than the 40 a suggestion used to be — these are chips on a shelf, and at
    // forty they were reading as buttons while the section above them was
    // still offering things you had not asked for.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Emoji(emoji, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One habit you already keep, with the cross that lets you stop keeping it.
 *
 * Deliberately *not* a SuggestionChip with a mark on the end: a suggestion is
 * an offer and tapping it creates something, while this is an inventory and
 * tapping it must not. The cross is a shelf action, not a habit action — it
 * withdraws the shortcut and leaves the habit alone — and it is given a
 * 26×34 target of its own so a thumb aiming at it cannot land on the name
 * instead. Long names ellipsize rather than push the cross off the pill.
 *
 * Sized to the same 34dp as the suggestions below it, because they are the
 * same kind of object: a chip in a wrapped row. Two heights in one column of
 * chips looked like two different controls where there was only one.
 */
@Composable
private fun HabitPill(
    habit: Habit,
    onRemove: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(scheme.surfaceContainerHighest)
            .padding(start = 11.dp, end = 2.dp),
    ) {
        Emoji(habit.emoji, size = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = habit.name,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(2.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(34.dp)
                .clip(CircleShape)
                .clickable(onClick = onRemove)
                // The cross is the only thing on the pill that does anything,
                // and it is the size of a fingernail: without a name a screen
                // reader announces it as an unlabelled button sitting on top
                // of the habit's own name.
                .semantics { contentDescription = "Remove ${habit.name} from the shelf" },
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.CLOSE,
                color = scheme.onSurfaceVariant,
                size = 13.dp,
                strokeWidth = 2.2.dp,
            )
        }
    }
}
