package com.anasdev.fluxlink.domain.model

import androidx.annotation.StringRes
import com.anasdev.fluxlink.R

enum class AppTheme(@StringRes val titleRes: Int) {
    SYSTEM(R.string.theme_system_default),
    LIGHT(R.string.theme_light),
    DARK(R.string.theme_dark);
}
