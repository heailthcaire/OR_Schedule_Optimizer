import type { CaseRecord, ParsedRow } from './types';
import { DateTimeParser } from './dateTimeParser';

export interface ValidatorConfig {
  startPadMinutes: number;
  endPadMinutes: number;
  binCapacity: number;
  maxProcedureDurationHours?: number;
  minProcedureDurationMinutes?: number;
}

export class CaseValidator {
  private processedIds = new Set<string>();
  private config: ValidatorConfig;

  constructor(config: ValidatorConfig) {
    this.config = config;
  }

  validate(rowNumber: number, record: CaseRecord | null, unparseableField?: string): ParsedRow {
    if (unparseableField) {
      return this.createInvalid(rowNumber, record, `Unparseable field: ${unparseableField}`);
    }

    if (!record) {
      return this.createInvalid(rowNumber, null, "Missing data record");
    }

    // Duplicate Check
    if (this.processedIds.has(record.id)) {
      return this.createInvalid(rowNumber, record, "Duplicate Case_ID");
    }
    this.processedIds.add(record.id);

    const anesthStart = DateTimeParser.addMinutes(record.csvStart, -this.config.startPadMinutes);
    const anesthEnd = DateTimeParser.addMinutes(record.csvEnd, this.config.endPadMinutes);

    // Occupancy-based logic for packing/duration
    const occStart = DateTimeParser.addMinutes(record.patientIn, -this.config.startPadMinutes);
    const occEnd = DateTimeParser.addMinutes(record.patientOut, this.config.endPadMinutes);
    const duration = DateTimeParser.minutesBetween(occStart, occEnd);

    // Cross-midnight Check
    if (duration < 0) {
      return this.createInvalid(rowNumber, record, `Cross-midnight occupancy (end before start). In: ${record.patientIn}, Out: ${record.patientOut}`);
    }

    // Min Duration Check
    const minMin = this.config.minProcedureDurationMinutes || 0;
    if (duration < minMin) {
      return this.createInvalid(rowNumber, record, `Occupancy under min duration (<${minMin}m)`);
    }

    // Capacity Check
    const outOfBounds = duration > this.config.binCapacity;
    if (outOfBounds) {
      return {
        rowNumber,
        record,
        anesthesiaStart: anesthStart,
        anesthesiaEnd: anesthEnd,
        occupancyStart: occStart,
        occupancyEnd: occEnd,
        durationMinutes: duration,
        valid: false,
        invalidReason: `Occupancy over max duration (>${this.config.binCapacity}m)`,
        outOfBounds: true
      };
    }

    return {
      rowNumber,
      record,
      anesthesiaStart: anesthStart,
      anesthesiaEnd: anesthEnd,
      occupancyStart: occStart,
      occupancyEnd: occEnd,
      durationMinutes: duration,
      valid: true,
      invalidReason: null,
      outOfBounds: false
    };
  }

  private createInvalid(rowNumber: number, record: CaseRecord | null, reason: string): ParsedRow {
    return {
      rowNumber,
      record,
      anesthesiaStart: null,
      anesthesiaEnd: null,
      occupancyStart: null,
      occupancyEnd: null,
      durationMinutes: 0,
      valid: false,
      invalidReason: reason,
      outOfBounds: false
    };
  }

  reset() {
    this.processedIds.clear();
  }
}