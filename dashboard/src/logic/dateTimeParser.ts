import { parse, isValid, format } from 'date-fns';

const DATE_FORMATS = [
  'yyyy-MM-dd',
  'M/d/yyyy',
  'MM/dd/yyyy',
  'MM-dd-yyyy',
  'M-d-yyyy',
  'yyyyMMdd',
];

const TIME_FORMATS = [
  'HH:mm',
  'H:mm',
  'hh:mm a',
  'h:mm a',
  'HH:mm:ss',
];

export class DateTimeParser {
  static parseDate(dateStr: string, primaryFormat?: string): Date | null {
    if (!dateStr || !dateStr.trim()) return null;

    const cleaned = dateStr.trim();
    const formatsToTry = primaryFormat ? [primaryFormat, ...DATE_FORMATS] : DATE_FORMATS;

    for (const fmt of formatsToTry) {
      try {
        const parsed = parse(cleaned, fmt, new Date());
        if (isValid(parsed)) return parsed;
      } catch (e) {
        // continue
      }
    }
    return null;
  }

  static parseTime(timeStr: string, primaryFormat?: string): string | null {
    if (!timeStr || !timeStr.trim()) return null;

    const cleaned = timeStr.trim();
    const formatsToTry = primaryFormat ? [primaryFormat, ...TIME_FORMATS] : TIME_FORMATS;

    for (const fmt of formatsToTry) {
      try {
        const parsed = parse(cleaned, fmt, new Date());
        if (isValid(parsed)) return format(parsed, 'HH:mm');
      } catch (e) {
        // continue
      }
    }
    return null;
  }

  static minutesBetween(start: string, end: string): number {
    const [h1, m1] = start.split(':').map(Number);
    const [h2, m2] = end.split(':').map(Number);
    return (h2 * 60 + m2) - (h1 * 60 + m1);
  }

  static addMinutes(time: string, minutes: number): string {
    const [h, m] = time.split(':').map(Number);
    const totalMinutes = (h * 60 + m + minutes + 1440) % 1440;
    const hh = Math.floor(totalMinutes / 60).toString().padStart(2, '0');
    const mm = (totalMinutes % 60).toString().padStart(2, '0');
    return `${hh}:${mm}`;
  }
}