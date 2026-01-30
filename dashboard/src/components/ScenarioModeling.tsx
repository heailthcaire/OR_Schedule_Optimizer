import { Activity, Clock, ShieldCheck, Zap, DollarSign, Info, UserPlus, RefreshCw } from 'lucide-react'

interface Props {
  config: {
    validator: {
      startPadMinutes: number;
      endPadMinutes: number;
      binCapacity: number;
    };
    financials: {
      costPerOrDay: number;
      includeAnesthesiologist: boolean;
      includeCRNA1: boolean;
      anesthesiologistRate: number;
      crnaRate: number;
      anesBlocksSavedEnabled: boolean;
      anesFteEfficiencyEnabled: boolean;
      anesAbsorptionEnabled: boolean;
      anesPaddingMinutes: number;
    };
  };
  lastAnalyzedConfig: {
    binCapacity: number;
    startPadMinutes: number;
    endPadMinutes: number;
    anesPaddingMinutes: number;
  } | null;
  onUpdate: (partial: any) => void;
  metrics: {
    roomsSaved: number;
    totalActualRooms: number;
    utilization: number;
    currentUtilization: number;
    estimatedSavings: number;
    annualOpportunity: number;
    annualizationFactor: number;
    analyzedDays: number;
    startDate: string;
    endDate: string;
    bundledCostPerDay: number;
  };
  isAnalyzing: boolean;
  onRecompute: () => void;
}

export function ScenarioModeling({ config, lastAnalyzedConfig, onUpdate, metrics, isAnalyzing, onRecompute }: Props) {
  const formatCurrency = (val: number) => {
    if (val >= 1000000) {
      return `$${Math.round(val / 1000000)} M`;
    }
    return `$${Math.round(val).toLocaleString()}`;
  };

  const hasChangesRequiringRecompute = !lastAnalyzedConfig || 
    config.validator.binCapacity !== lastAnalyzedConfig.binCapacity ||
    config.validator.startPadMinutes !== lastAnalyzedConfig.startPadMinutes ||
    config.validator.endPadMinutes !== lastAnalyzedConfig.endPadMinutes ||
    config.financials.anesPaddingMinutes !== lastAnalyzedConfig.anesPaddingMinutes;

  return (
    <div className="space-y-8 animate-in fade-in duration-500 pb-20 relative text-[var(--text-main)]">
      {isAnalyzing && (
        <div className="absolute inset-0 bg-[var(--bg-main)]/50 backdrop-blur-sm z-50 flex items-center justify-center rounded-3xl">
          <div className="bg-[var(--card-bg)] p-8 rounded-2xl border border-[var(--border-color)] shadow-2xl flex flex-col items-center space-y-4">
            <RefreshCw className="text-blue-500 animate-spin" size={48} />
            <div className="text-[var(--text-main)] font-black uppercase tracking-widest">Java Engine Recomputing...</div>
            <p className="text-[var(--text-main)] opacity-60 text-xs font-bold text-center max-w-xs">Optimizing schedules using high-precision OR-Tools solver</p>
          </div>
        </div>
      )}

      <header className="flex justify-between items-start">
        <div>
          <h2 className="text-2xl font-bold text-[var(--text-main)]">What-If Analysis</h2>
          <p className="text-[var(--text-main)] opacity-70 text-sm mt-1 font-medium">Adjust parameters for high-precision analysis</p>
        </div>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
        <div className="flex items-center">
          <button 
            onClick={onRecompute}
            disabled={isAnalyzing || !hasChangesRequiringRecompute}
            className="w-full h-full bg-emerald-600 hover:bg-emerald-500 text-white py-6 rounded-2xl font-black uppercase tracking-[0.2em] shadow-lg flex items-center justify-center space-x-2 transition-all disabled:opacity-50"
          >
            <Zap size={20} />
            <span>Update Statistics</span>
          </button>
        </div>
        <div className="bg-[var(--card-bg)] border border-[var(--border-color)] p-6 rounded-2xl flex flex-col justify-center items-center text-center group hover:border-emerald-500 transition-all">
          <div className="text-emerald-600 dark:text-emerald-400 text-5xl font-black group-hover:scale-110 transition-transform">{Math.round(metrics.roomsSaved || 0)}</div>
          <div className="text-[var(--text-main)] text-sm mt-2 uppercase tracking-[0.2em] font-black opacity-80">OR-Days Saved</div>
        </div>
        <div className="bg-[var(--card-bg)] border border-[var(--border-color)] p-6 rounded-2xl flex flex-col justify-center items-center text-center group hover:border-[var(--border-color)] transition-all">
          <div className="text-slate-600 dark:text-slate-400 text-5xl font-black group-hover:scale-110 transition-transform">{Math.round(metrics.totalActualRooms)}</div>
          <div className="text-[var(--text-main)] text-sm mt-2 uppercase tracking-[0.2em] font-black opacity-80">Total OR-Days</div>
        </div>
        <div className="bg-[var(--card-bg)] border border-[var(--border-color)] p-6 rounded-2xl flex flex-col justify-center items-center text-center group hover:border-blue-500 transition-all">
          <div className="text-blue-600 dark:text-blue-400 text-5xl font-black group-hover:scale-110 transition-transform">{formatCurrency(metrics.annualOpportunity)}</div>
          <div className="text-[var(--text-main)] text-sm mt-2 uppercase tracking-[0.2em] font-black opacity-80">Annual Savings</div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-8 items-start">
        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <label className="text-xs font-bold text-[var(--text-main)] flex items-center space-x-2 uppercase tracking-wide">
              <Clock size={14} className="text-blue-500 dark:text-blue-300" />
              <span>OR Day Capacity</span>
            </label>
            <span className="text-blue-500 dark:text-blue-300 font-mono text-xs font-black">{config.validator.binCapacity}m</span>
          </div>
          <input 
            type="range" min="240" max="720" step="30"
            value={config.validator.binCapacity}
            onChange={(e) => onUpdate({ binCapacity: Number(e.target.value) })}
            className="w-full h-2 bg-[var(--sidebar-bg)] rounded-lg appearance-none cursor-pointer accent-blue-500"
          />
          <div className="flex justify-between text-[10px] text-[var(--text-main)] opacity-60 font-black uppercase tracking-widest">
            <span>4h</span>
            <span>8h</span>
            <span>12h</span>
          </div>
        </div>

        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <label className="text-xs font-bold text-[var(--text-main)] flex items-center space-x-2 uppercase tracking-wide">
              <ShieldCheck size={14} className="text-emerald-500 dark:text-emerald-300" />
              <span>Start Padding</span>
            </label>
            <span className="text-emerald-500 dark:text-emerald-300 font-mono text-xs font-black">{config.validator.startPadMinutes}m</span>
          </div>
          <input 
            type="range" min="0" max="60" step="5"
            value={config.validator.startPadMinutes}
            onChange={(e) => onUpdate({ startPadMinutes: Number(e.target.value) })}
            className="w-full h-2 bg-[var(--sidebar-bg)] rounded-lg appearance-none cursor-pointer accent-emerald-500"
          />
        </div>

        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <label className="text-xs font-bold text-[var(--text-main)] flex items-center space-x-2 uppercase tracking-wide">
              <ShieldCheck size={14} className="text-rose-500 dark:text-rose-300" />
              <span>End Padding</span>
            </label>
            <span className="text-rose-500 dark:text-rose-300 font-mono text-xs font-black">{config.validator.endPadMinutes}m</span>
          </div>
          <input 
            type="range" min="0" max="60" step="5"
            value={config.validator.endPadMinutes}
            onChange={(e) => onUpdate({ endPadMinutes: Number(e.target.value) })}
            className="w-full h-2 bg-[var(--sidebar-bg)] rounded-lg appearance-none cursor-pointer accent-emerald-500"
          />
        </div>

        <div className="space-y-4">
          <div className="flex justify-between items-center">
            <div className="flex items-center space-x-2 group relative">
              <label className="text-xs font-bold text-[var(--text-main)] flex items-center space-x-2 uppercase tracking-wide">
                <DollarSign size={14} className="text-purple-500 dark:text-purple-300" />
                <span>Base OR-Day Cost</span>
              </label>
              <Info size={12} className="text-[var(--text-main)] opacity-60 cursor-help" />
              <div className="absolute bottom-full left-0 mb-2 w-64 p-3 bg-[var(--card-bg)] border border-[var(--border-color)] rounded-xl text-[10px] text-[var(--text-main)] shadow-2xl invisible group-hover:visible z-50 font-bold">
                This base rate represents fixed room overhead only (utilities, cleaning, basic equipment). It does not include professional clinical staffing or specialized labor.
              </div>
            </div>
            <span className="text-purple-500 dark:text-purple-300 font-mono text-xs font-black">${config.financials.costPerOrDay}</span>
          </div>
          <input 
            type="range" min="500" max="5000" step="100"
            value={config.financials.costPerOrDay}
            onChange={(e) => onUpdate({ costPerOrDay: Number(e.target.value) })}
            className="w-full h-2 bg-[var(--sidebar-bg)] rounded-lg appearance-none cursor-pointer accent-purple-500"
          />
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        <div className="p-4 bg-purple-600/10 border border-purple-600/20 rounded-xl">
          <div className="flex items-start space-x-3 text-purple-600 dark:text-purple-200">
            <DollarSign size={18} className="mt-1 flex-shrink-0" />
            <div>
              <div className="text-[10px] font-black uppercase tracking-[0.2em] opacity-90">Projection Factor (Annual)</div>
              <div className="text-xl font-black">{metrics.annualizationFactor.toFixed(2)}x</div>
              <p className="text-[9px] font-bold opacity-70 leading-tight mt-1">Based on {metrics.analyzedDays} analyzed days</p>
            </div>
          </div>
        </div>

        <div className="p-4 bg-blue-600/10 border border-blue-600/20 rounded-xl">
          <div className="flex items-start space-x-3">
            <Zap size={18} className="text-blue-600 dark:text-blue-300 mt-1" />
            <p className="text-xs text-blue-700 dark:text-blue-100 leading-relaxed font-bold">
              <strong>Tip:</strong> Reducing padding by just 5m across a high-volume site often saves 2-3 full OR-days per month.
            </p>
          </div>
        </div>
      </div>
    </div>
  )
}