package com.wf.gemrender.texture;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.libffi.FFICIF;
import org.lwjgl.system.libffi.FFIType;
import org.lwjgl.util.ktx.ktxBasisParams;
import org.lwjgl.util.ktx.ktxTexture2;
import org.lwjgl.util.ktx.ktxTextureCreateInfo;
import org.lwjgl.util.ktx.ktxTexture_vtbl;

import java.nio.ByteBuffer;

import static org.lwjgl.system.libffi.LibFFI.FFI_DEFAULT_ABI;
import static org.lwjgl.system.libffi.LibFFI.FFI_OK;
import static org.lwjgl.system.libffi.LibFFI.ffi_call;
import static org.lwjgl.system.libffi.LibFFI.ffi_prep_cif;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_pointer;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint32;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_uint32;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_uint8;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_void;

/**
 * libktx through libffi, not lwjgl-ktx's {@code KTX}: that class needs LWJGL >= 3.3.2 core
 * ({@code Configuration.KTX_LIBRARY_NAME}, {@code JNI.callPPI(JJIIZJ)I}, ...); 1.20.1 ships 3.3.1. lwjgl-ktx's
 * struct classes load on 3.3.1. {@code size_t} passed as pointer.
 */
final class Libktx {
    static final int SUCCESS = 0;
    static final int TEXTURE_CREATE_LOAD_IMAGE_DATA_BIT = 1;
    static final int TEXTURE_CREATE_ALLOC_STORAGE = 1;
    static final int PACK_UASTC_LEVEL_FASTEST = 0;
    static final int TTF_BC7_RGBA = 6;
    static final int TTF_RGBA32 = 13;

    private static final SharedLibrary LIBRARY = Library.loadNative(Libktx.class, "org.lwjgl.ktx",
            Platform.mapLibraryNameBundled("ktx"), true);

    private static final Function CREATE_FROM_MEMORY = new Function("ktxTexture2_CreateFromMemory", ffi_type_sint32,
            ffi_type_pointer, ffi_type_pointer, ffi_type_uint32, ffi_type_pointer);
    private static final Function ERROR_STRING = new Function("ktxErrorString", ffi_type_pointer, ffi_type_sint32);
    private static final Function TRANSCODE_BASIS = new Function("ktxTexture2_TranscodeBasis", ffi_type_sint32,
            ffi_type_pointer, ffi_type_sint32, ffi_type_uint32);
    private static final Function CREATE = new Function("ktxTexture2_Create", ffi_type_sint32,
            ffi_type_pointer, ffi_type_sint32, ffi_type_pointer);
    private static final Function COMPRESS_BASIS_EX = new Function("ktxTexture2_CompressBasisEx", ffi_type_sint32,
            ffi_type_pointer, ffi_type_pointer);

    // Virtuals: through ktxTexture.vtbl. Windows ktx.dll exports no ktxTexture2_SetImageFromMemory (3.3.3, 3.4.1),
    // no ktxTexture2_Destroy / ktxTexture2_WriteToMemory (3.3.3).
    private static final Signature DESTROY = new Signature(ffi_type_void, ffi_type_pointer);
    private static final Signature NEEDS_TRANSCODING = new Signature(ffi_type_uint8, ffi_type_pointer);
    private static final Signature GET_IMAGE_OFFSET = new Signature(ffi_type_sint32,
            ffi_type_pointer, ffi_type_uint32, ffi_type_uint32, ffi_type_uint32, ffi_type_pointer);
    private static final Signature SET_IMAGE_FROM_MEMORY = new Signature(ffi_type_sint32,
            ffi_type_pointer, ffi_type_uint32, ffi_type_uint32, ffi_type_uint32, ffi_type_pointer, ffi_type_pointer);

    private Libktx() {
    }

    /** @return error code; texture address in {@code handle[0]}. */
    static int createFromMemory(ByteBuffer bytes, int flags, PointerBuffer handle) {
        return (int) CREATE_FROM_MEMORY.call(MemoryUtil.memAddress(bytes), bytes.remaining(), flags,
                handle.address());
    }

    static int create(ktxTextureCreateInfo info, int storage, PointerBuffer handle) {
        return (int) CREATE.call(info.address(), storage, handle.address());
    }

    static String errorString(int error) {
        return MemoryUtil.memASCIISafe(ERROR_STRING.call(error));
    }

    static void destroy(long texture) {
        DESTROY.call(ktxTexture_vtbl.nDestroy(vtbl(texture)), texture);
    }

    static boolean needsTranscoding(ktxTexture2 texture) {
        long address = texture.address();
        return (NEEDS_TRANSCODING.call(ktxTexture_vtbl.nNeedsTranscoding(vtbl(address)), address) & 0xFF) != 0;
    }

    static int transcodeBasis(ktxTexture2 texture, int format, int flags) {
        return (int) TRANSCODE_BASIS.call(texture.address(), format, flags);
    }

    static int imageOffset(long texture, PointerBuffer offset) {
        return (int) GET_IMAGE_OFFSET.call(ktxTexture_vtbl.nGetImageOffset(vtbl(texture)), texture, 0, 0, 0,
                offset.address());
    }

    static int setImageFromMemory(long texture, ByteBuffer pixels) {
        return (int) SET_IMAGE_FROM_MEMORY.call(ktxTexture_vtbl.nSetImageFromMemory(vtbl(texture)), texture, 0, 0, 0,
                MemoryUtil.memAddress(pixels), pixels.remaining());
    }

    static int compressBasisEx(ktxTexture2 texture, ktxBasisParams params) {
        return (int) COMPRESS_BASIS_EX.call(texture.address(), params.address());
    }

    /** Image data, {@code dataSize} bytes; null when there is none. */
    static ByteBuffer data(ktxTexture2 texture) {
        return MemoryUtil.memByteBufferSafe(MemoryUtil.memGetAddress(texture.address() + ktxTexture2.PDATA),
                (int) texture.dataSize());
    }

    static long symbol(String name) {
        return LIBRARY.getFunctionAddress(name);
    }

    /** {@code ktxTexture_vtbl*} of a {@code ktxTexture*}. */
    static long vtbl(long texture) {
        return MemoryUtil.memGetAddress(texture + ktxTexture2.VTBL);
    }

    static final class Function {
        private final long address;
        private final Signature signature;

        Function(String name, FFIType result, FFIType... arguments) {
            address = LIBRARY.getFunctionAddress(name);
            if (address == MemoryUtil.NULL) {
                throw new UnsatisfiedLinkError("libktx has no " + name);
            }
            signature = new Signature(result, arguments);
        }

        long call(long... arguments) {
            return signature.call(address, arguments);
        }
    }

    static final class Signature {
        private final FFICIF cif = FFICIF.malloc();
        private final int arity;

        Signature(FFIType result, FFIType... arguments) {
            arity = arguments.length;
            PointerBuffer types = MemoryUtil.memAllocPointer(arity);
            for (FFIType type : arguments) {
                types.put(type);
            }
            types.flip();
            if (ffi_prep_cif(cif, FFI_DEFAULT_ABI, result, types) != FFI_OK) {
                throw new UnsatisfiedLinkError("libffi rejected a " + arity + "-argument signature");
            }
        }

        /** Each argument in an 8-byte slot (narrower types read its low bytes: little-endian only). */
        long call(long function, long... arguments) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer slots = stack.malloc(8, 8 * Math.max(1, arity));
                PointerBuffer values = stack.mallocPointer(arity);
                for (int i = 0; i < arity; i++) {
                    slots.putLong(8 * i, arguments[i]);
                    values.put(i, MemoryUtil.memAddress(slots) + 8L * i);
                }
                ByteBuffer result = stack.malloc(8, 8);
                result.putLong(0, 0);
                ffi_call(cif, function, result, values);
                return result.getLong(0);
            }
        }
    }
}
