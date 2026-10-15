package nzvpn.nzbozorg.ir.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** پرچم بدون کادر، پس‌زمینه یا حاشیه؛ تصویر محلی Twemoji کنار نام می‌نشیند. */
@Composable
fun NzCountryFlag(code: String?, modifier: Modifier = Modifier) {
    val resourceId = code?.let(::flagDrawable) ?: return
    Image(
        painter = painterResource(resourceId),
        contentDescription = null, // عنوان سرور/پوشه کنار پرچم خوانده می‌شود.
        contentScale = ContentScale.Fit,
        modifier = modifier.then(Modifier.size(27.dp)),
    )
}
