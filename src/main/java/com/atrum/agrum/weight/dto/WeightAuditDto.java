package com.atrum.agrum.weight.dto;

import java.util.List;

/**
 * View History: the weighing as recorded (original weight, entry type, supervisor, time)
 * and every change request on it, newest first.
 */
public record WeightAuditDto(
        WeightRecordDto weighing,
        List<WeightChangeDto> changes
) {}
