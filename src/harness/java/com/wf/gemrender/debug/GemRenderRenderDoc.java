package com.wf.gemrender.debug;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.SharedLibrary;

import com.wf.gemrender.GemRender;

public final class GemRenderRenderDoc {
	private static final int API_VERSION_1_6_0 = 10600;

	private static final int IDX_SET_CAPTURE_FILE_PATH_TEMPLATE = 11;
	private static final int IDX_GET_NUM_CAPTURES = 13;
	private static final int IDX_TRIGGER_CAPTURE = 15;
	private static final int IDX_IS_FRAME_CAPTURING = 20;
	private static final int IDX_TRIGGER_MULTI_FRAME_CAPTURE = 22;

	private static final String CAPTURE_PATH_ENV = "GEMRENDER_RENDERDOC_CAPTURE_PATH";

	private static boolean initialized;
	private static long apiTable;

	private GemRenderRenderDoc() {
	}

	public static synchronized boolean isAvailable() {
		if (!initialized) {
			initialized = true;
			apiTable = attach();
		}
		return apiTable != MemoryUtil.NULL;
	}

	public static boolean triggerCapture() {
		if (!isAvailable()) {
			return false;
		}
		JNI.invokeV(fn(IDX_TRIGGER_CAPTURE));
		GemRender.LOGGER.info("RenderDoc: capture of the next frame requested");
		return true;
	}

	public static boolean triggerMultiFrameCapture(int frames) {
		if (!isAvailable()) {
			return false;
		}
		if (frames < 1) {
			throw new IllegalArgumentException("frames must be >= 1, got " + frames);
		}
		JNI.invokeV(frames, fn(IDX_TRIGGER_MULTI_FRAME_CAPTURE));
		GemRender.LOGGER.info("RenderDoc: capture of the next {} frames requested", frames);
		return true;
	}

	public static int getNumCaptures() {
		return isAvailable() ? JNI.invokeI(fn(IDX_GET_NUM_CAPTURES)) : 0;
	}

	public static boolean isFrameCapturing() {
		return isAvailable() && JNI.invokeI(fn(IDX_IS_FRAME_CAPTURING)) != 0;
	}

	public static boolean setCaptureFilePathTemplate(String template) {
		if (!isAvailable()) {
			return false;
		}
		try (MemoryStack stack = MemoryStack.stackPush()) {
			JNI.invokePV(MemoryUtil.memAddress(stack.UTF8(template)), fn(IDX_SET_CAPTURE_FILE_PATH_TEMPLATE));
		}
		return true;
	}

	private static long fn(int index) {
		return MemoryUtil.memGetAddress(apiTable + (long) index * Long.BYTES);
	}

	private static long attach() {
		if (!isLibraryResident()) {
			GemRender.LOGGER.debug("RenderDoc: not loaded in this process, capture support disabled");
			return MemoryUtil.NULL;
		}

		try {
			SharedLibrary lib = APIUtil.apiCreateLibrary("librenderdoc.so");
			long getApi = APIUtil.apiGetFunctionAddressOptional(lib, "RENDERDOC_GetAPI");
			if (getApi == MemoryUtil.NULL) {
				GemRender.LOGGER.warn("RenderDoc: librenderdoc.so is loaded but exports no RENDERDOC_GetAPI");
				return MemoryUtil.NULL;
			}

			long table;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				PointerBuffer out = stack.mallocPointer(1);
				int ok = JNI.invokePI(API_VERSION_1_6_0, out.address(), getApi);
				if (ok != 1) {
					GemRender.LOGGER.warn("RenderDoc: RENDERDOC_GetAPI({}) refused, returned {}. "
							+ "The installed RenderDoc is probably older than 1.6.", API_VERSION_1_6_0, ok);
					return MemoryUtil.NULL;
				}
				table = out.get(0);
			}

			GemRender.LOGGER.info("RenderDoc: attached, in-application capture available");
			apiTable = table;
			applyCapturePathFromEnvironment();
			return table;
		} catch (Throwable t) {
			GemRender.LOGGER.warn("RenderDoc: attach failed, capture support disabled", t);
			return MemoryUtil.NULL;
		}
	}

	private static void applyCapturePathFromEnvironment() {
		String template = System.getenv(CAPTURE_PATH_ENV);
		if (template == null || template.isBlank()) {
			return;
		}
		try {
			Path dir = Paths.get(template).getParent();
			if (dir != null) {
				Files.createDirectories(dir);
			}
			setCaptureFilePathTemplate(template);
			GemRender.LOGGER.info("RenderDoc: captures will be written to {}*.rdc", template);
		} catch (IOException e) {
			GemRender.LOGGER.warn("RenderDoc: could not create the capture directory for {}", template, e);
		}
	}

	private static boolean isLibraryResident() {
		Path maps = Paths.get("/proc/self/maps");
		if (!Files.isReadable(maps)) {
			return false;
		}
		try (var lines = Files.lines(maps)) {
			return lines.anyMatch(line -> line.toLowerCase(Locale.ROOT).contains("librenderdoc"));
		} catch (IOException e) {
			return false;
		}
	}
}
