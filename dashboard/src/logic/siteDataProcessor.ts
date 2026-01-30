import Papa from 'papaparse';
import type { CaseRecord, ParsedRow } from './types';
import { DateTimeParser } from './dateTimeParser';
import { CaseValidator } from './caseValidator';
import type { ValidatorConfig } from './caseValidator';
import { BinPackingOptimizer } from './optimizer';

export interface ProcessingConfig {
  mapping: Record<string, string>; // Expected Header -> CSV Header
  validator: ValidatorConfig;
  financials: {
    costPerOrDay: number;
    includeAnesthesiologist: boolean;
    includeCRNA1: boolean;
    anesthesiologistRate: number;
    crnaRate: number;
    crnaHourRate: number;
    anesBlocksSavedEnabled: boolean;
    anesFteEfficiencyEnabled: boolean;
    anesAbsorptionEnabled: boolean;
    crnaProductivityFactor: number;
  };
  startTime: string;
  skipSingleOr: boolean;
  dateFormat: string;
  timeFormat: string;
  reportDetailLevel: 'high' | 'low';
  showSummaryGraph: boolean;
  showDetailGraph: boolean;
  optimizationTimeout: number;
  csvPathOverride?: string | null;
  reportPackagePath?: string;
  engineName1?: string;
  engineName2?: string;
}

export interface SiteDayResult {
  site: string;
  date: string;
  cases: ParsedRow[];
  actualRoomsUsed: number;
  optimizedRoomsUsed: number;
  roomsSaved: number;
  status: string;
  anesthesiaSavings: {
    method1Savings: number;
    method2Savings: number;
    method3Savings: number;
    totalAnesthesiaSavings: number;
  };
  extraCrnaHours: number;
}

export class SiteDataProcessor {
  static processCsv(csvText: string, config: ProcessingConfig): SiteDayResult[] {
    const parsed = Papa.parse(csvText, { header: true, skipEmptyLines: true });
    const rows = parsed.data as any[];
    
    const validator = new CaseValidator(config.validator);
    const results: ParsedRow[] = [];

    rows.forEach((row, idx) => {
      const record = this.mapToRecord(row, config.mapping);
      results.push(validator.validate(idx + 1, record));
    });

    // Group by Site + Date
    const groups = new Map<string, ParsedRow[]>();
    results.forEach(r => {
      if (r.record) { // Include both valid and invalid records for visualization
        const key = `${r.record.site}|${r.record.date?.toISOString().split('T')[0]}`;
        if (!groups.has(key)) groups.set(key, []);
        groups.get(key)!.push(r);
      }
    });

    const dayResults: SiteDayResult[] = [];
    groups.forEach((groupCases, key) => {
      const [site, date] = key.split('|');
      const validGroupCases = groupCases.filter(c => c.valid);
      const actualRooms = new Set(groupCases.map(c => c.record!.orName)).size;
      const optimization = BinPackingOptimizer.solve(
        validGroupCases, 
        config.validator.binCapacity, 
        site,
        config.startTime,
        config.skipSingleOr
      );
      
      dayResults.push({
        site,
        date,
        cases: groupCases,
        actualRoomsUsed: actualRooms,
        optimizedRoomsUsed: optimization.optimizedRoomsUsed,
        roomsSaved: Math.max(0, actualRooms - optimization.optimizedRoomsUsed),
        status: optimization.status,
        anesthesiaSavings: { method1Savings: 0, method2Savings: 0, method3Savings: 0, totalAnesthesiaSavings: 0 },
        extraCrnaHours: 0
      });
    });

    return dayResults;
  }

  private static mapToRecord(row: any, mapping: Record<string, string>): CaseRecord | null {
    try {
      const id = row[mapping['Case_ID']];
      const site = row[mapping['Site']];
      const date = DateTimeParser.parseDate(row[mapping['Date']]);
      const orName = row[mapping['OR_Room']];
      const surgeon = row[mapping['Surgeon_Name']];
      const procedure = row[mapping['Procedure_Type']];
      const csvStart = DateTimeParser.parseTime(row[mapping['Anesthesia_Start_Time']]);
      const csvEnd = DateTimeParser.parseTime(row[mapping['Anesthesia_End_Time']]);
      const patientIn = DateTimeParser.parseTime(row[mapping['Patient_In_Room_Time']]);
      const patientOut = DateTimeParser.parseTime(row[mapping['Patient_Out_Room_Time']]);
      const anesthesiologistName = row['Provider_Name'] || '';

      if (!id || !site || !date || !orName || !csvStart || !csvEnd || !patientIn || !patientOut) return null;

      return { id, site, date, orName, surgeon, procedure, csvStart, csvEnd, patientIn, patientOut, anesthesiologistName };
    } catch (e) {
      return null;
    }
  }
}