import React from 'react'
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, Cell } from 'recharts'
import { RefreshCw } from 'lucide-react'

interface Props {
  results: any[];
  config: {
    financials: {
      includeAnesthesiologist: boolean;
      includeCRNA1: boolean;
    };
    engineName1?: string;
    engineName2?: string;
  };
  metrics: {
    totalActualRooms: number;
    totalOptimizedRooms: number;
    roomsSaved: number;
    totalRoomsSaved?: number;
    anesthesiaSavings: number;
    totalAnesthesiologists: number;
    utilization: number;
    currentUtilization: number;
    estimatedSavings: number;
    annualOpportunity: number;
    annualizationFactor: number;
    analyzedDays: number;
    startDate: string;
    endDate: string;
    anesthesiaStaffingSavingsPerAnes: number;
    bundledCostPerDay: number;
    averageLaborYield: number;
    primeTimeUtilization: number;
    fcots: number;
    averageTot: number;
    contributionMargin: number;
    totalCostOfVariance: number;
  };
  isAnalyzing: boolean;
}

export function DashboardView({ results, config, metrics, isAnalyzing }: Props) {
  const formatCurrency = (val: number) => {
    if (val >= 1000000) {
      return `$${Math.round(val / 1000000)} M`;
    }
    return `$${Math.round(val).toLocaleString()}`;
  };

  const hasAnesthesiaStaffing = true;

  const siteSummary = React.useMemo(() => {
    const siteMap = new Map<string, { name: string, actual: number, optimized: number, saved: number }>();
    results.forEach(r => {
      if (!siteMap.has(r.site)) {
        siteMap.set(r.site, { name: r.site, actual: 0, optimized: 0, saved: 0 });
      }
      const s = siteMap.get(r.site)!;
      s.actual += r.actualRoomsUsed;
      s.optimized += r.optimizedRoomsUsed;
      s.saved += (r.roomsSaved || 0);
    });
    return Array.from(siteMap.values())
      .filter(s => s.actual > 0)
      .sort((a, b) => b.saved - a.saved)
      .slice(0, 10); // Show top 10 sites for better visibility
  }, [results]);

  const globalChartData = [
    { name: 'Actual OR-Days', value: metrics.totalActualRooms, color: '#64748b' },
    { name: 'Optimized', value: metrics.totalOptimizedRooms, color: '#3b82f6' },
    { name: 'OR-Days Saved', value: metrics.roomsSaved || 0, color: '#10b981' },
  ];

  return (
    <div className="space-y-8 animate-in fade-in duration-500 relative text-[var(--text-main)]">
      
      {isAnalyzing && (
        <div className="absolute inset-0 bg-slate-900/50 backdrop-blur-sm z-50 flex items-center justify-center rounded-3xl">
          <div className="bg-[var(--card-bg)] p-8 rounded-2xl border border-[var(--border-color)] shadow-2xl flex flex-col items-center space-y-4">
            <RefreshCw className="text-blue-500 animate-spin" size={48} />
            <div className="text-[var(--text-main)] font-black uppercase tracking-widest">Java Engine Processing...</div>
          </div>
        </div>
      )}

      <header className="flex justify-between items-end">
        <div>
          <h2 className="text-3xl font-bold text-[var(--text-main)] dark:text-white">OR Summary</h2>
          <p className="text-[var(--text-main)] dark:text-slate-200 opacity-70 dark:opacity-90 mt-1 font-medium">Operational performance and efficiency trends across all sites</p>
        </div>
        <div className="text-right">
          <span className="text-xs text-[var(--text-main)] dark:text-slate-300 uppercase font-black tracking-widest opacity-60 dark:opacity-80">Analysis Engine</span>
          <div className="text-emerald-500 dark:text-emerald-400 font-mono text-sm flex items-center justify-end space-x-2">
            <span className="h-2 w-2 bg-emerald-400 rounded-full animate-pulse"></span>
            <span>{config.engineName1 || 'HEalLTHCaiRE.ai Quark (v1.7)'} + {config.engineName2 || 'Google OR-Tools (v9.8)'}</span>
          </div>
        </div>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {[
          { label: 'OR-Days Saved', value: (metrics.roomsSaved || 0).toLocaleString(), color: 'text-emerald-500 dark:text-emerald-400' },
          { label: 'Total OR-Days', value: metrics.totalActualRooms.toLocaleString(), color: 'text-slate-500 dark:text-slate-400' },
          { label: 'Strategic ROI Potential', value: formatCurrency(metrics.annualOpportunity), color: 'text-blue-600 dark:text-blue-400', sub: `${metrics.analyzedDays} days analyzed (${metrics.startDate} to ${metrics.endDate})`, tooltip: `Annual Savings = (Identified Savings) × (365.25 / ${metrics.analyzedDays} days)` },
        ].map((stat) => (
          <div key={stat.label} title={stat.tooltip} className="bg-[var(--card-bg)] border border-[var(--border-color)] p-6 rounded-2xl hover:shadow-lg transition-all min-w-fit shadow-sm">
            <div className="text-[var(--text-main)] dark:text-white opacity-80 dark:opacity-100 text-sm font-black uppercase tracking-wide whitespace-nowrap">{stat.label}</div>
            <div className={`text-3xl font-black mt-2 ${stat.color} whitespace-nowrap`}>{stat.value}</div>
            {stat.sub && <div className="text-[10px] opacity-60 dark:opacity-90 font-bold mt-1 uppercase tracking-tighter whitespace-nowrap text-[var(--text-main)] dark:text-slate-200">{stat.sub}</div>}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-4">
        {[
          { label: 'Current OR Util', value: `${Math.round(metrics.currentUtilization)}%`, color: 'text-blue-600 dark:text-blue-400' },
          { label: 'Prime Time Util', value: `${Math.round(metrics.primeTimeUtilization)}%`, color: 'text-blue-600 dark:text-blue-400', tooltip: "Efficiency during core hours (07:00 AM - 03:00 PM)" },
          { label: 'Labor Yield', value: `${Math.round(metrics.averageLaborYield)}%`, color: 'text-blue-600 dark:text-blue-400', tooltip: "Anesthesia Minutes / Total Block Time" },
          { label: 'FCOTS %', value: `${Math.round(metrics.fcots)}%`, color: 'text-amber-600 dark:text-amber-400', tooltip: "% of first cases starting on-time (07:30 AM)" },
          { label: 'Avg TOT', value: `${Math.round(metrics.averageTot)}m`, color: 'text-rose-600 dark:text-rose-400', tooltip: "Average non-productive time between patients" },
          { label: 'Contr. Margin', value: `$${Math.round(metrics.contributionMargin).toLocaleString()}/hr`, color: 'text-emerald-600 dark:text-emerald-400', visible: hasAnesthesiaStaffing, tooltip: "Revenue per hour minus variable costs" },
        ].filter(s => s.visible !== false).map((stat) => (
          <div key={stat.label} title={stat.tooltip} className="bg-[var(--card-bg)] opacity-90 border border-[var(--border-color)] p-5 rounded-2xl hover:border-blue-500/30 transition-all shadow-sm">
            <div className="text-[var(--text-main)] dark:text-white text-[10px] font-black uppercase tracking-widest whitespace-nowrap">{stat.label}</div>
            <div className={`text-2xl font-bold mt-1 ${stat.color} whitespace-nowrap`}>{stat.value}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Global Overview Chart */}
        <div className="lg:col-span-1 bg-[var(--card-bg)] border border-[var(--border-color)] rounded-2xl p-6">
          <h3 className="text-lg font-semibold mb-6 text-[var(--text-main)]">Total Resource Opportunity</h3>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={globalChartData}>
                <XAxis dataKey="name" stroke="currentColor" opacity={0.5} fontSize={12} tickLine={false} axisLine={false} />
                <Tooltip 
                  contentStyle={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px', color: 'var(--text-main)' }}
                  cursor={{ fill: 'currentColor', opacity: 0.1 }}
                />
                <Bar dataKey="value" radius={[6, 6, 0, 0]}>
                  {globalChartData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Site Comparison Chart */}
        <div className="lg:col-span-2 bg-[var(--card-bg)] border border-[var(--border-color)] rounded-2xl p-6">
          <h3 className="text-lg font-semibold mb-6 text-[var(--text-main)]">Efficiency Savings by Site (OR-Days)</h3>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={siteSummary} layout="vertical" margin={{ left: 40 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="currentColor" opacity={0.1} horizontal={true} vertical={false} />
                <XAxis type="number" stroke="currentColor" opacity={0.5} fontSize={12} />
                <YAxis dataKey="name" type="category" stroke="currentColor" opacity={0.5} fontSize={12} width={100} />
                <Tooltip 
                   contentStyle={{ backgroundColor: 'var(--card-bg)', border: '1px solid var(--border-color)', borderRadius: '8px', color: 'var(--text-main)' }}
                />
                <Legend />
                <Bar dataKey="actual" name="Actual OR-Days" fill="#64748b" radius={[0, 4, 4, 0]} barSize={12} />
                <Bar dataKey="optimized" name="Optimized OR-Days" fill="#3b82f6" radius={[0, 4, 4, 0]} barSize={12} />
                <Bar dataKey="saved" name="OR-Days Saved" fill="#10b981" radius={[0, 4, 4, 0]} barSize={12} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>
    </div>
  )
}