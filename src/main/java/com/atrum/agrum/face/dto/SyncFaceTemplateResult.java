package com.atrum.agrum.face.dto;

import com.atrum.agrum.attendance.dto.SyncStatus;

public record SyncFaceTemplateResult(
        String employeeCode,
        SyncStatus status,
        // Shown on the phone next to a rejected face
        String reason
) {
    public static SyncFaceTemplateResult sent(String employeeCode) {
        return new SyncFaceTemplateResult(employeeCode, SyncStatus.SENT, null);
    }

    public static SyncFaceTemplateResult rejected(String employeeCode, String reason) {
        return new SyncFaceTemplateResult(employeeCode, SyncStatus.REJECTED, reason);
    }
}
