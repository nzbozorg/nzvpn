package nzvpn.nzbozorg.ir.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import nzvpn.nzbozorg.ir.ui.theme.NzTheme

/**
 * فیلد ورودی NZBOZORG
 *  - حاشیهٔ ۱dp با شفافیت ۱۵٪؛ در حالت Focus حاشیهٔ مسی (بخش ۵ و ۶)
 *  - حداقل ارتفاع ۴۸dp (بخش ۱۱)
 *  - برچسب بالا + متن راهنما پایین (برای آدرس‌ها و توکن‌ها لازم است)
 *  - حالت خطا با رنگ قرمز و پیام متنی (نه فقط رنگ — بخش ۱۱)
 */
@Composable
fun NzTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    errorText: String? = null,
    placeholder: String? = null,
    leading: NzIconKind? = null,
    isSecret: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (minLines > 1) 6 else 1,
    enabled: Boolean = true,
    /** مقدار چپ‌به‌راست است (آدرس، توکن، میزبان) — جهت متن را LTR می‌کند */
    ltr: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = NzTheme.colors
    val hasError = !errorText.isNullOrBlank()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (hasError) colors.redSoft else colors.textMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NzTheme.dimens.minTouchTarget),
            enabled = enabled,
            isError = hasError,
            singleLine = minLines == 1,
            minLines = minLines,
            maxLines = maxLines,
            shape = RoundedCornerShape(8.dp),
            textStyle = (if (minLines > 1) {
                MaterialTheme.typography.bodyMedium
            } else {
                NzTheme.mono.value
            }).let { if (ltr) it.withTextDirection() else it },
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        style = if (minLines > 1) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            NzTheme.mono.value
                        },
                        color = colors.textDisabled,
                    )
                }
            },
            leadingIcon = leading?.let {
                {
                    NzIcon(kind = it, size = 18.dp, tint = colors.textMuted)
                }
            },
            visualTransformation = if (isSecret) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                disabledTextColor = colors.textDisabled,
                cursorColor = colors.copperBright,
                focusedBorderColor = colors.borderCopper,
                unfocusedBorderColor = colors.border,
                disabledBorderColor = colors.border,
                errorBorderColor = colors.red,
                focusedContainerColor = colors.surfaceDeep,
                unfocusedContainerColor = colors.surfaceDeep,
                disabledContainerColor = colors.surfaceDeep,
                errorContainerColor = colors.surfaceDeep,
            ),
        )
        if (supporting != null || hasError) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Top) {
                NzIcon(
                    kind = if (hasError) NzIconKind.WARNING else NzIconKind.INFO,
                    size = 13.dp,
                    tint = if (hasError) colors.redSoft else colors.textDisabled,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = errorText ?: supporting.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasError) colors.redSoft else colors.textDisabled,
                )
            }
        }
    }
}

private fun TextStyle.withTextDirection(): TextStyle = copy(textDirection = TextDirection.Ltr)
