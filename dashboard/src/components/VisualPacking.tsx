import type { SiteDayResult } from '../logic/siteDataProcessor'

interface Props {
  data: SiteDayResult;
}

export function VisualPacking({ data }: Props) {
  const hourHeight = 80; // pixels per hour
  
  // Calculate dynamic start and end hours based on case data
  let minHour = 7;
  let maxHour = 19;
  
  if (data.cases.length > 0) {
    const hours = data.cases.flatMap(c => {
      const [sh] = (c.record?.patientIn || "07:00").split(':').map(Number);
      const [eh] = (c.record?.patientOut || "19:00").split(':').map(Number);
      return [sh, eh + 1];
    }).filter(h => !isNaN(h));
    
    if (hours.length > 0) {
      minHour = Math.max(0, Math.min(...hours) - 1);
      maxHour = Math.min(24, Math.max(...hours) + 1);
    }
  }

  const startHour = minHour;
  const endHour = maxHour;
  
  return (
    <div className="space-y-6 animate-in fade-in duration-700 text-[var(--text-main)]">
      <header className="flex justify-between items-center">
        <div>
           <h3 className="text-xl font-bold text-[var(--text-main)] uppercase tracking-tight">Visual Comparison: {data.site}</h3>
           <p className="text-[var(--text-main)] text-sm font-black mt-1 uppercase tracking-wide opacity-70">Target Date: {data.date}</p>
        </div>
        <div className="flex space-x-6 text-[10px] font-black uppercase tracking-[0.2em] bg-[var(--card-bg)] px-6 py-2 rounded-full border border-[var(--border-color)] text-[var(--text-main)] shadow-xl">
          <div className="flex items-center space-x-2"><div className="h-2 w-2 bg-slate-400 rounded-full"></div><span>Historical Actual</span></div>
          <div className="flex items-center space-x-2"><div className="h-2 w-2 bg-blue-500 rounded-full"></div><span>Optimized Packing</span></div>
        </div>
      </header>

      <div className="grid grid-cols-2 gap-12">
        {/* Actual Packing */}
        <div className="space-y-4">
          <h4 className="text-[var(--text-main)] font-black uppercase tracking-[0.2em] text-[10px] text-center bg-[var(--card-bg)] py-2 rounded-xl border border-[var(--border-color)]">Historical Actual ({data.actualRoomsUsed} Rooms Used)</h4>
          <div className="relative bg-[var(--card-bg)]/50 rounded-3xl border border-[var(--border-color)] p-6 flex h-[600px] overflow-hidden">
             {/* Time labels */}
             <div className="w-14 pr-2 flex flex-col justify-between text-[11px] text-[var(--text-main)] font-mono py-1 border-r border-[var(--border-color)] font-black">
                {Array.from({ length: endHour - startHour + 1 }).map((_, i) => (
                  <div key={i}>{String(startHour + i).padStart(2, '0')}:00</div>
                ))}
             </div>
             {/* Rooms Container */}
             <div className="flex-1 flex space-x-3 px-4 overflow-x-auto custom-scrollbar relative">
                {Array.from(new Set(data.cases.map(c => c.record!.orName))).sort().map((roomName, roomIdx) => (
                  <div key={roomName} className="flex-1 min-w-[80px] relative">
                    <div className="absolute top-0 left-0 right-0 h-6 flex items-center justify-center text-[10px] font-black uppercase tracking-tight text-[var(--text-main)] border-b-2 border-[var(--border-color)] z-10 bg-[var(--card-bg)]">
                      Room {roomIdx + 1}
                    </div>
                    <div className="absolute inset-0 border-r-2 border-black/40 dark:border-white/50 pt-6"></div>
                    <div className="pt-6 relative h-full">
                      {data.cases.filter(c => c.record!.orName === roomName).map(c => {
                        const [sh, sm] = c.record!.patientIn.split(':').map(Number);
                        const top = ((sh - startHour) * hourHeight + (sm / 60) * hourHeight);
                        const height = (c.durationMinutes / 60) * hourHeight;
                        return (
                          <div 
                            key={c.record!.id}
                            className={`absolute w-full border rounded-lg p-2 text-[10px] overflow-hidden group hover:z-20 transition-all cursor-help border-l-4 shadow-xl ${
                              c.valid 
                                ? 'bg-slate-200 dark:bg-slate-700 border-slate-300 dark:border-slate-500 border-l-slate-500 dark:border-l-slate-200' 
                                : 'bg-red-100 dark:bg-red-900/30 border-red-200 dark:border-red-800 border-l-red-500 animate-pulse'
                            }`}
                            style={{ top: `${top}px`, height: `${height}px` }}
                            title={c.valid 
                              ? `${c.record!.surgeon}\n${c.record!.procedure}\n${c.record!.patientIn} - ${c.record!.patientOut} (${c.durationMinutes}m)`
                              : `INVALID: ${c.invalidReason}\n${c.record!.surgeon}\n${c.record!.procedure}\n${c.record!.patientIn} - ${c.record!.patientOut}`
                            }
                          >
                            <div className={`font-black truncate uppercase tracking-tighter ${c.valid ? 'text-slate-900 dark:text-white' : 'text-red-700 dark:text-red-300'}`}>
                              {c.valid ? '' : '⚠️ '}{c.record!.surgeon}
                            </div>
                            <div className={`truncate font-bold mt-1 ${c.valid ? 'text-slate-600 dark:text-slate-100' : 'text-red-600 dark:text-red-400'}`}>
                              {c.record!.procedure}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  </div>
                ))}
             </div>
          </div>
        </div>

        {/* Optimized Packing */}
        <div className="space-y-4">
          <div className="flex justify-between items-end">
            <h4 className="flex-1 text-[var(--text-main)] font-black uppercase tracking-[0.2em] text-[10px] text-center bg-blue-500/10 dark:bg-blue-500/20 py-2 rounded-xl border border-blue-500/20 dark:border-blue-400/30">Consolidated Optimized ({data.optimizedRoomsUsed} Rooms Needed)</h4>
          </div>

          {data.anesthesiaSavings && data.anesthesiaSavings.totalAnesthesiaSavings > 0 && (
            <div className="bg-blue-500/10 dark:bg-blue-500/20 p-4 rounded-3xl border border-blue-500/20 dark:border-blue-400/30 animate-in slide-in-from-top duration-500">
              <h5 className="text-blue-700 dark:text-blue-300 font-black text-[9px] uppercase tracking-[0.2em] mb-3 flex items-center">
                <div className="h-1.5 w-1.5 bg-blue-500 rounded-full mr-2"></div>
                Anesthesia Optimization
              </h5>
              <div className="grid grid-cols-4 gap-2">
                <div className="bg-[var(--card-bg)]/50 p-2 rounded-xl border border-[var(--border-color)]">
                  <span className="block text-lg font-black text-[var(--text-main)] leading-none">{data.anesthesiaSavings.method1Savings}</span>
                  <span className="text-[8px] uppercase font-bold opacity-50 tracking-tighter">OR Elim</span>
                </div>
                <div className="bg-[var(--card-bg)]/50 p-2 rounded-xl border border-[var(--border-color)]">
                  <span className="block text-lg font-black text-[var(--text-main)] leading-none">{data.anesthesiaSavings.method2Savings}</span>
                  <span className="text-[8px] uppercase font-bold opacity-50 tracking-tighter">Hrs Reduc</span>
                </div>
                <div className="bg-[var(--card-bg)]/50 p-2 rounded-xl border border-[var(--border-color)]">
                  <span className="block text-lg font-black text-[var(--text-main)] leading-none">{data.anesthesiaSavings.method3Savings}</span>
                  <span className="text-[8px] uppercase font-bold opacity-50 tracking-tighter">Gap Cov</span>
                </div>
                <div className="bg-blue-500 p-2 rounded-xl shadow-lg shadow-blue-500/20 flex flex-col justify-center">
                  <span className="block text-lg font-black text-white leading-none">{data.anesthesiaSavings.totalAnesthesiaSavings}</span>
                  <span className="text-[8px] uppercase font-bold text-blue-50 tracking-tighter">Total</span>
                </div>
              </div>
            </div>
          )}

          <div className="relative bg-[var(--card-bg)]/50 rounded-3xl border border-blue-500/20 p-6 flex h-[600px] overflow-hidden">
             <div className="w-14 pr-2 flex flex-col justify-between text-[11px] text-[var(--text-main)] font-mono py-1 border-r border-[var(--border-color)] font-black">
                {Array.from({ length: endHour - startHour + 1 }).map((_, i) => (
                  <div key={i}>{String(startHour + i).padStart(2, '0')}:00</div>
                ))}
             </div>
             <div className="flex-1 flex space-x-3 px-4 overflow-x-auto custom-scrollbar relative">
                {Array.from({ length: data.optimizedRoomsUsed }).map((_, idx) => {
                  const roomLetter = String.fromCharCode('A'.charCodeAt(0) + idx);
                  const roomName = `${data.site} - Consolidated Room ${roomLetter}`;
                  const roomCases = data.cases
                    .filter(c => c.assignedRoomName === roomName)
                    .sort((a, b) => {
                       // Sort by occupancy start time
                       if (a.occupancyStart && b.occupancyStart) {
                         return a.occupancyStart.localeCompare(b.occupancyStart);
                       }
                       return (a.rowNumber || 0) - (b.rowNumber || 0);
                    });
                  let currentTop = 0; // Sequential packing from the start hour

                  return (
                    <div key={roomName} className="flex-1 min-w-[80px] relative">
                      <div className="absolute top-0 left-0 right-0 h-6 flex items-center justify-center text-[10px] font-black uppercase tracking-tight text-[var(--text-main)] border-b-2 border-[var(--border-color)] z-10 bg-[var(--card-bg)]">
                        Room {idx + 1}
                      </div>
                      <div className="absolute inset-0 border-r-2 border-blue-500/60 pt-6"></div>
                      <div className="pt-6 relative h-full">
                        {roomCases.map((c) => {
                          const top = currentTop;
                          const height = (c.durationMinutes / 60) * hourHeight;
                          currentTop += height + 2; // small gap between cases
                          return (
                            <div 
                              key={c.record!.id}
                              className="absolute w-full bg-blue-100 dark:bg-blue-900/40 border border-blue-300 dark:border-blue-500 rounded-lg p-2 text-[10px] overflow-hidden group hover:z-20 transition-all cursor-help border-l-2 border-l-blue-600 dark:border-l-blue-400 shadow-xl"
                              style={{ top: `${top}px`, height: `${height}px` }}
                              title={`${c.record!.surgeon}\n${c.record!.procedure}\nDuration: ${c.durationMinutes}m`}
                            >
                              <div className="font-black text-blue-950 dark:text-white truncate uppercase tracking-tighter">{c.record!.surgeon}</div>
                              <div className="text-blue-800 dark:text-blue-100 truncate font-bold mt-1">{c.record!.procedure}</div>
                            </div>
                          )
                        })}
                      </div>
                    </div>
                  )
                })}
             </div>
          </div>
        </div>
      </div>
    </div>
  )
}