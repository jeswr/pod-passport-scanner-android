package org.jeswr.podpassport.service

import android.content.Context
import org.jeswr.podpassport.model.ChipBundle

/**
 * Loads the bundled synthetic eMRTD bundle from assets. This file is a
 * byte-for-byte copy of the issuer-side canonical test fixture
 * `apps/issuer/test/fixtures/emrtd-bundle.json` (synthetic "JANE DOE" eMRTD).
 */
object SampleBundle {
    const val ASSET_PATH = "sample_passport/emrtd-bundle.json"

    fun load(context: Context): ChipBundle {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        return ChipBundle.fromJsonString(json)
    }
}
