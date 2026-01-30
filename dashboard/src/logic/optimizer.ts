import type { ParsedRow, OptimizationResult } from './types';
import { DateTimeParser } from './dateTimeParser';

export class BinPackingOptimizer {
  /**
   * Solve using First-Fit Decreasing (FFD) algorithm.
   * This implements volume-based packing with sequential time assignment
   * for visualization purposes.
   */
  static solve(cases: ParsedRow[], capacity: number, siteName: string, startTime: string = '08:00', skipSingle: boolean = true): OptimizationResult {
    if (cases.length === 0) {
      return { feasible: true, optimizedRoomsUsed: 0, assignments: {}, status: 'OK' };
    }

    const actualRooms = new Set(cases.map(r => r.record!.orName));
    const actualRoomsUsed = actualRooms.size;

    const skip = skipSingle && actualRoomsUsed <= 1;
    const offending = cases.filter(c => c.durationMinutes > capacity);

    if (offending.length > 0) {
      return { feasible: false, optimizedRoomsUsed: actualRoomsUsed, assignments: {}, status: 'INFEASIBLE' };
    }

    if (skip) {
      return { feasible: true, optimizedRoomsUsed: actualRoomsUsed, assignments: {}, status: 'SKIPPED' };
    }

    // Heuristics from Java OptimizationService
    const totalDuration = cases.reduce((sum, c) => sum + c.durationMinutes, 0);
    const minPossibleRooms = Math.ceil(totalDuration / capacity);

    if (minPossibleRooms >= actualRoomsUsed) {
      return { feasible: true, optimizedRoomsUsed: actualRoomsUsed, assignments: {}, status: 'OK_NO_SAVINGS_POSSIBLE' };
    }

    const currentUtilization = totalDuration / (actualRoomsUsed * capacity);
    if (
      (currentUtilization > 0.70 && cases.length > 5) ||
      (currentUtilization > 0.75 && cases.length > 8) ||
      (currentUtilization > 0.80 && cases.length > 10) ||
      (currentUtilization > 0.85 && cases.length > 15) ||
      (currentUtilization > 0.90)
    ) {
      return { feasible: true, optimizedRoomsUsed: actualRoomsUsed, assignments: {}, status: 'OK_HIGH_UTILIZATION' };
    }

    // Filter valid cases and sort by duration descending
    const validCases = [...cases]
      .filter(c => c.valid)
      .sort((a, b) => b.durationMinutes - a.durationMinutes);

    // rooms is an array of bins
    const rooms: { cases: ParsedRow[], totalDuration: number, lastEndTime: string }[] = [];

    for (const c of validCases) {
      let placed = false;
      
      for (const room of rooms) {
        if (room.totalDuration + c.durationMinutes <= capacity) {
          // Assign optimized start/end times sequentially for visualization
          const optStart = room.lastEndTime;
          const optEnd = DateTimeParser.addMinutes(optStart, c.durationMinutes);
          
          const roomLetter = String.fromCharCode('A'.charCodeAt(0) + rooms.indexOf(room));
          c.assignedRoomName = `${siteName} - Consolidated Room ${roomLetter}`;
          // Note: In a real scenario, we might want to store these in a separate 'optimized' record
          // but for the dashboard view, we can temporarily store them.
          
          room.cases.push(c);
          room.totalDuration += c.durationMinutes;
          room.lastEndTime = optEnd;
          placed = true;
          break;
        }
      }
      
      if (!placed) {
        const optStart = startTime;
        const optEnd = DateTimeParser.addMinutes(optStart, c.durationMinutes);
        
        const newRoom = { 
          cases: [c], 
          totalDuration: c.durationMinutes,
          lastEndTime: optEnd
        };
        rooms.push(newRoom);
        const roomLetter = String.fromCharCode('A'.charCodeAt(0) + rooms.length - 1);
        c.assignedRoomName = `${siteName} - Consolidated Room ${roomLetter}`;
      }
    }

    const assignments: Record<string, string[]> = {};
    rooms.forEach((room, idx) => {
      const roomLetter = String.fromCharCode('A'.charCodeAt(0) + idx);
      const roomName = `${siteName} - Consolidated Room ${roomLetter}`;
      assignments[roomName] = room.cases.map(c => c.record!.id);
    });

    return {
      feasible: true,
      optimizedRoomsUsed: rooms.length,
      assignments,
      status: 'OK'
    };
  }
}