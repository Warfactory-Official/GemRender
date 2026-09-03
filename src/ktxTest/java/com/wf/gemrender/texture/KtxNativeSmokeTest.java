package com.wf.gemrender.texture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.ktx.KTX;

class KtxNativeSmokeTest {
	@Test
	@DisplayName("libktx links, and its transcode format table is the one the docs describe")
	void theNativeLoads() {
		assertThat(KTX.ktxTranscodeFormatString(KTX.KTX_TTF_RGBA32)).isNotBlank();

		assertThat(KTX.KTX_TTF_RGBA32).isEqualTo(13);
		assertThat(KTX.KTX_TTF_BC7_RGBA).isEqualTo(6);
	}
}
