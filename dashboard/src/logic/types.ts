export interface CaseRecord {
  id: string;
  site: string;
  date: Date | null;
  orName: string;
  surgeon: string;
  procedure: string;
  csvStart: string;
  csvEnd: string;
  patientIn: string;
  patientOut: string;
  anesthesiologistName: string;
}

export interface ParsedRow {
  rowNumber: number;
  record: CaseRecord | null;
  anesthesiaStart: string | null;
  anesthesiaEnd: string | null;
  occupancyStart: string | null;
  occupancyEnd: string | null;
  durationMinutes: number;
  valid: boolean;
  invalidReason: string | null;
  outOfBounds: boolean;
  assignedRoomName?: string;
}

export interface OptimizationResult {
  feasible: boolean;
  optimizedRoomsUsed: number;
  assignments: Record<string, string[]>;
  status: string;
}