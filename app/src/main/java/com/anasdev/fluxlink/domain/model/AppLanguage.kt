package com.anasdev.fluxlink.domain.model

import androidx.annotation.StringRes
import com.anasdev.fluxlink.R

enum class AppLanguage(val code: String, @param:StringRes val titleRes: Int) {
    SYSTEM("", R.string.language_system_default),
    ENGLISH("en", R.string.language_english),
    ARABIC("ar", R.string.language_arabic);
}
