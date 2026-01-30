import React from 'react'
import { RefreshCw } from 'lucide-react'

interface Props {
  metrics: {
    anesthesiaSavings: number;
    totalAnesthesiologists: number;
    anesthesiaStaffingSavingsPerAnes: number;
    totalCrnaHours: number;
    scheduledCrnaHours: number;
    requiredCrnaHours: number;
    extraCrnaHours: number;
    crnaCostSaved: number;
    avgActiveRooms: number;
    maxActiveRooms: number;
    requiredAnesDays: number;
    scheduledAnesDays: number;
  };
  config: {
    financials: {
      includeAnesthesiologist: boolean;
      includeCRNA1: boolean;
      anesthesiologistRate: number;
      crnaRate: number;
      crnaHourRate: number;
      anesBlocksSavedEnabled: boolean;
      anesFteEfficiencyEnabled: boolean;
      anesAbsorptionEnabled: boolean;
      anesPaddingMinutes: number;
      crnaProductivityFactor: number;
    };
  };
  isAnalyzing: boolean;
  onUpdateConfig: (val: any) => void;
  onRecompute: () => void;
}

export function StaffReductionView({ 
  metrics, 
  config,
  isAnalyzing, 
  onUpdateConfig,
  onRecompute
}: Props) {
  const formatCurrency = (val: number) => {
    return `$${Math.round(val).toLocaleString()}`;
  };

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
          <h2 className="text-3xl font-bold text-[var(--text-main)] dark:text-white">Staff</h2>
          <p className="text-[var(--text-main)] dark:text-slate-200 opacity-70 dark:opacity-90 mt-1 font-medium">Anesthesia staffing efficiency and optimization</p>
        </div>
        <button 
          onClick={onRecompute}
          disabled={isAnalyzing}
          className="bg-emerald-600 hover:bg-emerald-500 text-white px-6 py-2 rounded-xl font-black uppercase tracking-widest shadow-lg flex items-center space-x-2 transition-all disabled:opacity-50"
        >
          <RefreshCw size={16} className={isAnalyzing ? 'animate-spin' : ''} />
          <span>Update Statistics</span>
        </button>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {[
          { label: 'CRNA Hours Saved', value: Math.round(metrics.extraCrnaHours).toLocaleString(), color: 'text-emerald-500 dark:text-emerald-400' },
          { label: 'Required CRNA (Demand)', value: Math.round(metrics.requiredCrnaHours).toLocaleString(), color: 'text-amber-500 dark:text-amber-400' },
          { label: 'Scheduled CRNA (Supply)', value: Math.round(metrics.scheduledCrnaHours).toLocaleString(), color: 'text-slate-500 dark:text-slate-400' },
          { label: 'Anes-Days Saved', value: Math.round(metrics.anesthesiaSavings).toLocaleString(), color: 'text-emerald-500 dark:text-emerald-400' },
          { label: 'Required Anes-Days (Demand)', value: Math.round(metrics.requiredAnesDays).toLocaleString(), color: 'text-amber-500 dark:text-amber-400' },
          { label: 'Scheduled Anes-Days (Supply)', value: Math.round(metrics.scheduledAnesDays).toLocaleString(), color: 'text-slate-500 dark:text-slate-400' },
        ].map((stat) => (
          <div key={stat.label} className="bg-[var(--card-bg)] border border-[var(--border-color)] p-6 rounded-2xl hover:shadow-lg transition-all min-w-fit shadow-sm">
            <div className="text-[var(--text-main)] dark:text-white opacity-80 dark:opacity-100 text-sm font-black uppercase tracking-wide whitespace-nowrap">{stat.label}</div>
            <div className={`text-3xl font-black mt-2 ${stat.color} whitespace-nowrap`}>{stat.value}</div>
          </div>
        ))}
      </div>

      <div className="bg-[var(--card-bg)] border border-[var(--border-color)] p-8 rounded-2xl shadow-sm">
        <h3 className="text-xl font-bold mb-4 text-[var(--text-main)] dark:text-white">Staffing Impact Analysis</h3>
        <p className="text-[var(--text-main)] opacity-70 mb-6">
          Optimization of anesthesia resources through regional labor pools. While CRNAs can float between sites within a region, procedures and doctors remain site-bound due to specialized equipment and logistics.
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
            <div className="space-y-4">
                <div className="text-sm font-black uppercase tracking-widest text-slate-500">Hard Savings: Efficiency Metrics</div>
                <div className="flex justify-between items-center p-4 bg-black/5 dark:bg-white/5 rounded-xl">
                    <span className="font-medium">Potential FTE Reduction (Anes-Days)</span>
                    <span className="text-xl font-bold text-emerald-500">{Math.round(metrics.anesthesiaSavings)}</span>
                </div>
                <div className="flex justify-between items-center p-4 bg-black/5 dark:bg-white/5 rounded-xl">
                    <span className="font-medium">Annual Labor Recovery</span>
                    <span className="text-xl font-bold text-blue-500">${(
                      Math.round(((metrics?.extraCrnaHours || 0) * (config.financials.crnaHourRate || 175) + (metrics?.anesthesiaSavings || 0) * (config.financials.anesthesiologistRate || 3500)) * (metrics?.annualizationFactor || 12) / 1000000)
                    ).toFixed(2)}M</span>
                </div>
                <div className="flex justify-between items-center p-4 bg-black/5 dark:bg-white/5 rounded-xl">
                    <span className="font-medium">Avg Daily Active Rooms</span>
                    <span className="text-xl font-bold text-blue-500">{Math.round(metrics.avgActiveRooms)}</span>
                </div>
                <div className="flex justify-between items-center p-4 bg-black/5 dark:bg-white/5 rounded-xl">
                    <span className="font-medium">Max Daily Active Rooms</span>
                    <span className="text-xl font-bold text-blue-500">{metrics.maxActiveRooms}</span>
                </div>
            </div>

            <div className="space-y-4">
                <div className="text-sm font-black uppercase tracking-widest text-slate-500">Regional CRNA Optimization</div>
                <div className="flex justify-between items-center p-4 bg-black/5 dark:bg-white/5 rounded-xl">
                    <span className="font-medium">Extra CRNA Hours</span>
                    <span className="text-xl font-bold text-emerald-500">{Math.round(metrics.extraCrnaHours).toLocaleString()}</span>
                </div>
                <div className="flex justify-between items-center p-4 bg-black/5 dark:bg-white/5 rounded-xl">
                    <span className="font-medium">Scheduled CRNA Cost</span>
                    <span className="text-xl font-bold text-blue-500">${metrics.crnaCostSaved.toLocaleString()}</span>
                </div>

                <div className="flex justify-between items-center p-4 bg-black/5 dark:bg-white/5 rounded-xl">
                    <span className="font-medium">CRNA Hours Saved ($)</span>
                    <span className="text-xl font-bold text-blue-500">${Math.round(metrics.extraCrnaHours * config.financials.crnaHourRate).toLocaleString()}</span>
                </div>

                <div className="p-4 bg-black/5 dark:bg-white/5 rounded-xl space-y-3">
                    <div className="flex justify-between items-center">
                        <span className="font-medium text-blue-600 dark:text-blue-400">CRNA Hour Rate</span>
                        <span className="text-xl font-bold text-blue-500">${config.financials.crnaHourRate}</span>
                    </div>
                    <input 
                        type="range" 
                        min="50" 
                        max="300" 
                        step="5" 
                        value={config.financials.crnaHourRate} 
                        onChange={(e) => onUpdateConfig({ crnaHourRate: parseInt(e.target.value) })}
                        className="w-full h-2 bg-blue-200 rounded-lg appearance-none cursor-pointer accent-blue-500"
                    />
                    <div className="flex justify-between text-[10px] font-black uppercase tracking-widest opacity-50">
                        <span>$50</span>
                        <span>$300</span>
                    </div>
                </div>
                
                <div className="p-4 bg-black/5 dark:bg-white/5 rounded-xl space-y-3">
                    <div className="flex justify-between items-center">
                        <span className="font-medium text-blue-600 dark:text-blue-400">CRNA Productivity Factor</span>
                        <span className="text-xl font-bold text-blue-500">{Math.round(config.financials.crnaProductivityFactor * 100)}%</span>
                    </div>
                    <input 
                        type="range" 
                        min="0.5" 
                        max="1.0" 
                        step="0.01" 
                        value={config.financials.crnaProductivityFactor} 
                        onChange={(e) => onUpdateConfig({ crnaProductivityFactor: parseFloat(e.target.value) })}
                        className="w-full h-2 bg-blue-200 rounded-lg appearance-none cursor-pointer accent-blue-500"
                    />
                    <div className="flex justify-between text-[10px] font-black uppercase tracking-widest opacity-50">
                        <span>50% (High Buffer)</span>
                        <span>100% (Raw Demand)</span>
                    </div>
                </div>

                <div className="p-4 bg-black/5 dark:bg-white/5 rounded-xl space-y-4">
                    <div className="text-sm font-black uppercase tracking-widest text-emerald-600 pt-4 border-t border-emerald-500/10">Anesthesia Efficiency Logic</div>
                    
                    <label className="flex items-center space-x-3 cursor-pointer group">
                        <input 
                            type="checkbox" 
                            checked={config.financials.anesBlocksSavedEnabled}
                            onChange={(e) => onUpdateConfig({ anesBlocksSavedEnabled: e.target.checked })}
                            className="w-5 h-5 rounded border-emerald-500 text-emerald-600 focus:ring-emerald-500"
                        />
                        <div className="flex-1">
                            <div className="text-sm font-bold group-hover:text-emerald-500 transition-colors">Anes-blocks saved</div>
                            <div className="text-[10px] opacity-60 font-bold">OR Elimination Savings</div>
                        </div>
                    </label>

                    <label className="flex items-center space-x-3 cursor-pointer group">
                        <input 
                            type="checkbox" 
                            checked={config.financials.anesFteEfficiencyEnabled}
                            onChange={(e) => onUpdateConfig({ anesFteEfficiencyEnabled: e.target.checked })}
                            className="w-5 h-5 rounded border-emerald-500 text-emerald-600 focus:ring-emerald-500"
                        />
                        <div className="flex-1">
                            <div className="text-sm font-bold group-hover:text-emerald-500 transition-colors">FTE Efficiency</div>
                            <div className="text-[10px] opacity-60 font-bold">Coverage Hours Reduction</div>
                        </div>
                    </label>

                    <label className="flex items-center space-x-3 cursor-pointer group">
                        <input 
                            type="checkbox" 
                            checked={config.financials.anesAbsorptionEnabled}
                            onChange={(e) => onUpdateConfig({ anesAbsorptionEnabled: e.target.checked })}
                            className="w-5 h-5 rounded border-emerald-500 text-emerald-600 focus:ring-emerald-500"
                        />
                        <div className="flex-1">
                            <div className="text-sm font-bold group-hover:text-emerald-500 transition-colors">Anes Absorption</div>
                            <div className="text-[10px] opacity-60 font-bold">Idle Gap Absorption</div>
                        </div>
                    </label>

                    <div className="pt-2 border-t border-emerald-500/10 space-y-2">
                        <div className="flex justify-between items-center">
                            <span className="text-xs font-bold text-emerald-600 uppercase tracking-widest">Anes Padding</span>
                            <span className="text-sm font-black text-emerald-500">{config.financials.anesPaddingMinutes}m</span>
                        </div>
                        <input 
                            type="range" min="0" max="180" step="30"
                            value={config.financials.anesPaddingMinutes}
                            onChange={(e) => onUpdateConfig({ anesPaddingMinutes: Number(e.target.value) })}
                            className="w-full h-2 bg-emerald-200 rounded-lg appearance-none cursor-pointer accent-emerald-500"
                        />
                        <div className="flex justify-between text-[8px] font-black uppercase tracking-tighter opacity-50">
                            <span>0m</span>
                            <span>90m</span>
                            <span>180m</span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
      </div>
    </div>
  )
}
