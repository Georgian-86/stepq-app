package com.stepq.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.stepq.app.ui.theme.StepqColors
import com.stepq.app.ui.theme.StepqType

/** Where StepQ's legal pages live, and the version of them a person agrees to at sign-up. */
object Legal {
    const val SITE_URL = "https://stepq.in"
    const val PRIVACY_URL = "https://stepq.in/privacy"
    const val HEALTH_DATA_URL = "https://stepq.in/privacy#health-and-step-data"
    const val TERMS_URL = "https://stepq.in/terms"
    const val GRIEVANCE_URL = "https://stepq.in/grievance"
    const val COMMUNITY_RULES_URL = "https://stepq.in/terms#community-rules"
    const val SUPPORT_EMAIL = "connect@stepq.in"
    // Until a dedicated grievance@ mailbox exists, grievances go to the main inbox.
    const val GRIEVANCE_EMAIL = "connect@stepq.in"

    /** Bump when the Terms or Privacy policy change in a way people must agree to again. */
    const val POLICY_VERSION = "2026-11-24"
}

private val linkStyle = TextLinkStyles(SpanStyle(color = StepqColors.Deep, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline))

/** "… our Terms of use and Privacy policy …" with both names as tappable links. */
fun legalText(before: String, after: String): AnnotatedString = buildAnnotatedString {
    append(before)
    withLink(LinkAnnotation.Url(Legal.TERMS_URL, linkStyle)) { append("Terms of use") }
    append(" and ")
    withLink(LinkAnnotation.Url(Legal.PRIVACY_URL, linkStyle)) { append("Privacy policy") }
    append(after)
}

/** An unticked-by-default checkbox with a sentence beside it. The text may hold links. */
@Composable
fun ConsentCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, text: AnnotatedString, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(role = Role.Checkbox, onClickLabel = if (checked) "Untick" else "Tick") { onChange(!checked) }
                .background(if (checked) StepqColors.Deep else StepqColors.White)
                .border(BorderStroke(1.5.dp, if (checked) StepqColors.Deep else StepqColors.Line), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Outlined.Check, contentDescription = null, tint = StepqColors.White, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = StepqType.Small, color = StepqColors.Ink, modifier = Modifier.weight(1f))
    }
}
