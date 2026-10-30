package com.atrum.agrum.face;

/** Checks for the face pictures phones send: a JPEG of a cropped face, a few KB. */
public final class FacePhotos {

    // The phone sends about 15 KB; anything far bigger isn't the face crop
    static final int MAX_BYTES = 200 * 1024;

    private FacePhotos() {
    }

    /** True for a JPEG (starts with FF D8) of a sensible size. */
    public static boolean isValid(byte[] photo) {
        return photo != null
                && photo.length > 2
                && photo.length <= MAX_BYTES
                && (photo[0] & 0xFF) == 0xFF
                && (photo[1] & 0xFF) == 0xD8;
    }
}
