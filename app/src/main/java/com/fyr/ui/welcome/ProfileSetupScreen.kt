package com.fyr.ui.welcome

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fyr.data.FyrState
import com.fyr.data.STAGED_AVATAR
import com.fyr.data.nameAsTyped
import com.fyr.ui.components.Glyph
import com.fyr.ui.components.PhotoBadge
import com.fyr.ui.components.PortraitHalo
import com.fyr.ui.theme.Poppins
import com.fyr.ui.theme.Qurova

/**
 * The form standing between the welcome and the app — the one screen a new
 * install *must* answer before it becomes a user.
 *
 * Three fields, in the order a person meets them: the portrait (with the
 * one action it needs riding its rim, because there is nothing to remove
 * yet), the first name the greeting will wear, the last name the profile
 * will carry beside it, and a save that opens the app. The gate does not
 * offer a skip: the greeting is the app's cheapest warmth and an unnamed
 * greeting is none, so the form's answer is required — but *optional
 * inputs* are not required, so the picture and the last name can both be
 * left to the profile editor, which asks the same three questions again
 * whenever they want to change them.
 *
 * The picture behaves exactly as it does in the editor: picking stages the
 * bytes beside the profile ([Store.stageAvatar]) and only the save adopts
 * them ([Store.adoptStagedAvatar]). Nothing can reach the profile from
 * here, because here the profile does not exist yet — the save *is* the
 * moment it comes into being, name and picture together.
 *
 * It is a gate rather than a route, like the welcome it follows: rendered
 * over the shell while the name is unanswered, absent from the back stack,
 * and remembered by the same flag that ends it. Back does nothing while it
 * is up — leaving is the phone's business (home, recents), not the form's
 * to offer as an answer.
 *
 * The portrait wears the same rim as the add control in the bar — the lit
 * ember gradient ring with the theme's own lift beneath it — because it is
 * the first thing on this screen that belongs to Fyr, and it should arrive
 * already wearing the app's one accent the way the app's one button does.
 */
@Composable
fun ProfileSetupScreen(
    onStagePhoto: (uri: Uri, onStaged: () -> Unit) -> Unit,
    // Whether the staged pick is still on disk. Saveable flags outlive the
    // process; staged files do not (the store drops them at startup), so a
    // restored "I picked a photo" with no file behind it is reconciled back
    // to the truth rather than previewing a picture that is gone.
    hasStagedPhoto: () -> Boolean,
    onSave: (first: String, last: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    // Empty until a pick lands: there is no saved profile yet, so the only
    // picture this circle can be showing is one that has not been adopted.
    // Saveable so a *rotation* does not silently drop a pick the file still
    // holds — the two were reconciled against each other only by accident.
    var pendingPhoto by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (pendingPhoto.isNotEmpty() && !hasStagedPhoto()) pendingPhoto = ""
    }

    // At screen scope, beside the state it feeds: a launcher registered in
    // a composable that leaves composition would never receive its result.
    // The result is *staged*, never saved — the save below is the only door.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onStagePhoto(uri) { pendingPhoto = STAGED_AVATAR }
    }

    val canSave = first.isNotBlank()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 28.dp)
            .padding(top = 48.dp, bottom = 40.dp),
    ) {
        // ── the portrait, wearing the one control it needs ────────────
        // The same frame the app gives every picture it shows — wavy
        // ember ring, hairline, beads — with the badge on the bottom
        // point, half in and half out. The knockout is the page itself:
        // on this ground there is no card behind the picture, only the
        // screen the frame sits on.
        PortraitHalo(
            photo = pendingPhoto,
            name = first.trim(),
            photoSize = 96.dp,
            badge = {
                PhotoBadge(
                    glyph = Glyph.UPLOAD,
                    accent = true,
                    description = "Choose a photo",
                    knockout = scheme.background,
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
            },
        )

        Spacer(Modifier.height(30.dp))

        Text(
            text = "Set up your profile",
            fontFamily = Qurova,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "A name and a picture — all on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        NameField(
            value = first,
            onValueChange = { first = nameAsTyped(it) },
            label = "First name",
            placeholder = "John",
        )

        Spacer(Modifier.height(14.dp))

        NameField(
            value = last,
            onValueChange = { last = nameAsTyped(it) },
            label = "Last name",
            placeholder = "Doe",
        )

        Spacer(Modifier.height(28.dp))

        // The same door the welcome holds, at full measure: a capsule in
        // the accent, with the disabled state stated as a *different
        // surface* rather than as the same ember washed out — a dimmed
        // ember still reads as fire that might light, while grey reads as
        // a rule that has not been met yet.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 9.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                )
                .clip(CircleShape)
                .background(if (canSave) scheme.primary else scheme.surfaceContainerHighest)
                .clickable(enabled = canSave, role = Role.Button) {
                    onSave(first.trim(), last.trim())
                }
                .padding(vertical = 17.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Save and continue",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (canSave) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One field of the two, sharing the editor's own voice: Poppins in the ink
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
        // The keyboard is asked to start every word capitalised, so the
        // suggestion strip offers names the way they will be read rather
        // than as the field happened to be typed — the field's own
        // [nameAsTyped] enforces the same rule either way.
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
