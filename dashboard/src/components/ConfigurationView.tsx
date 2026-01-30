import { useState } from 'react'
import { Save, RefreshCw, DollarSign, Clock, Info, CheckCircle2, ShieldCheck, AlertTriangle, FileText, Zap } from 'lucide-react'

interface Props {
  config: {
    validator: {
      startPadMinutes: number;
      endPadMinutes: number;
      binCapacity: number;
      maxProcedureDurationHours: number;
      minProcedureDurationMinutes: number;
    };
    skipSingleOr: boolean;
    dateFormat: string;
    timeFormat: string;
    reportDetailLevel: 'high' | 'low';
    showSummaryGraph: boolean;
    showDetailGraph: boolean;
    optimizationTimeout: number;
    reportPackagePath?: string;
    mapping: Record<string, string>;
    financials: {
      costPerOrDay: number;
      includeAnesthesiologist: boolean;
      includeCRNA1: boolean;
      anesthesiologistRate: number;
      crnaRate: number;
    };
  };
  onSave: (newConfig: any) => void;
  onRunDiagnostics?: () => void;
  isDiagnosticRunning?: boolean;
  diagnosticResult?: { success: boolean; output: string; error: string | null } | null;
}

export function ConfigurationView({ 
  config, onSave, onRunDiagnostics, isDiagnosticRunning, diagnosticResult 
}: Props) {
  const [localConfig, setLocalConfig] = useState(JSON.parse(JSON.stringify(config)));
  const [isSaving, setIsSaving] = useState(false);
  const [showSuccess, setShowSuccess] = useState(false);

  const handleSave = async () => {
    setIsSaving(true);
    setShowSuccess(false);
    await onSave(localConfig);
    setIsSaving(false);
    setShowSuccess(true);
    setTimeout(() => setShowSuccess(false), 3000);
  };

  const updateFinancials = (field: string, value: number) => {
    setLocalConfig((prev: any) => ({
      ...prev,
      financials: {
        ...prev.financials,
        [field]: value
      }
    }));
  };

  const updateValidator = (field: string, value: any) => {
    setLocalConfig((prev: any) => ({
      ...prev,
      validator: {
        ...prev.validator,
        [field]: value
      }
    }));
  };

  const updateMapping = (field: string, value: string) => {
    setLocalConfig((prev: any) => ({
      ...prev,
      mapping: {
        ...prev.mapping,
        [field]: value
      }
    }));
  };

  const updateRootField = (field: string, value: any) => {
    setLocalConfig((prev: any) => ({
      ...prev,
      [field]: value
    }));
  };

  return (
    <div className="max-w-4xl mx-auto space-y-8 animate-in fade-in slide-in-from-bottom-4 duration-500 pb-20 text-[var(--text-main)]">
      <header className="flex justify-between items-end border-b border-[var(--border-color)] pb-6">
        <div>
          <h2 className="text-3xl font-bold text-[var(--text-main)]">System Configuration</h2>
          <p className="text-[var(--text-main)] opacity-60 mt-1 font-bold">Manage global defaults and persistent modeling parameters</p>
        </div>
        <div className="flex items-center space-x-4">
          {showSuccess && (
            <div className="flex items-center space-x-2 text-emerald-500 animate-in fade-in slide-in-from-right-2 duration-300">
              <CheckCircle2 size={18} />
              <span className="text-sm font-black uppercase tracking-wide">Settings Saved</span>
            </div>
          )}
          <button 
            onClick={handleSave}
            disabled={isSaving}
            className={`flex items-center space-x-2 px-6 py-2.5 rounded-xl transition-all shadow-lg font-black uppercase tracking-widest text-sm ${
              showSuccess 
                ? 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-emerald-900/20' 
                : 'bg-blue-600 hover:bg-blue-500 text-white shadow-blue-900/20'
            } disabled:opacity-50`}
          >
            {isSaving ? <RefreshCw className="animate-spin" size={18} /> : (showSuccess ? <CheckCircle2 size={18} /> : <Save size={18} />)}
            <span>{isSaving ? 'Saving...' : (showSuccess ? 'Saved!' : 'Save Settings')}</span>
          </button>
        </div>
      </header>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* Financial Defaults */}
        <section className="bg-[var(--card-bg)] border border-[var(--border-color)] rounded-3xl p-8 space-y-6">
          <div className="flex items-center space-x-3 text-purple-500 dark:text-purple-300">
            <DollarSign size={24} />
            <h3 className="text-xl font-bold text-[var(--text-main)] uppercase tracking-wide">Financial Defaults</h3>
          </div>

          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Base OR-Day Cost ($)</label>
              <input 
                type="number" 
                value={localConfig.financials.costPerOrDay}
                onChange={(e) => updateFinancials('costPerOrDay', Number(e.target.value))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-purple-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Anesthesiologist Daily Rate ($)</label>
              <input 
                type="number" 
                value={localConfig.financials.anesthesiologistRate}
                onChange={(e) => updateFinancials('anesthesiologistRate', Number(e.target.value))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-purple-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">CRNA Daily Rate ($)</label>
              <input 
                type="number" 
                value={localConfig.financials.crnaRate}
                onChange={(e) => updateFinancials('crnaRate', Number(e.target.value))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-purple-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">CRNA Productivity Factor (0.5 - 1.0)</label>
              <input 
                type="number" 
                step="0.01"
                min="0.5"
                max="1.0"
                value={localConfig.financials.crnaProductivityFactor}
                onChange={(e) => updateFinancials('crnaProductivityFactor', Number(e.target.value))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-purple-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">CFO Report Package Path</label>
              <input 
                type="text" 
                value={localConfig.reportPackagePath}
                onChange={(e) => updateRootField('reportPackagePath', e.target.value)}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-purple-500 outline-none transition-all"
                placeholder="src/main/resources/report/CFO_Report_Package.zip"
              />
              <p className="text-[10px] text-slate-500 font-medium px-1">Relative to project root or absolute path.</p>
            </div>
          </div>
        </section>

        {/* Validation Defaults */}
        <section className="bg-slate-700/30 border border-slate-600/50 rounded-3xl p-8 space-y-6">
          <div className="flex items-center space-x-3 text-emerald-300">
            <Clock size={24} />
            <h3 className="text-xl font-bold text-white uppercase tracking-wide">Validation & Buffer Defaults</h3>
          </div>

          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Default OR Day Capacity (minutes)</label>
              <input 
                type="number" 
                value={localConfig.validator.binCapacity}
                onChange={(e) => updateValidator('binCapacity', Number(e.target.value))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Procedure Start Padding (minutes)</label>
              <input 
                type="number" 
                value={localConfig.validator.startPadMinutes}
                onChange={(e) => updateValidator('startPadMinutes', Number(e.target.value))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Procedure End Padding (minutes)</label>
              <input 
                type="number" 
                value={localConfig.validator.endPadMinutes}
                onChange={(e) => updateValidator('endPadMinutes', Number(e.target.value))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Max Procedure Duration (hours)</label>
              <input 
                type="number" 
                value={localConfig.validator.maxProcedureDurationHours}
                onChange={(e) => updateValidator('maxProcedureDurationHours', Number(e.target.value))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Min Procedure Duration (minutes)</label>
              <input 
                type="number" 
                value={localConfig.validator.minProcedureDurationMinutes}
                onChange={(e) => updateValidator('minProcedureDurationMinutes', Number(e.target.value))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Optimization Timeout (seconds)</label>
              <input 
                type="number" 
                value={localConfig.optimizationTimeout}
                onChange={(e) => setLocalConfig((prev: any) => ({ ...prev, optimizationTimeout: Number(e.target.value) }))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
              />
            </div>

            <div className="pt-4 flex items-center justify-between">
              <div className="space-y-0.5">
                <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Skip Single OR Days</label>
                <p className="text-xs text-slate-400 font-bold">Do not attempt to optimize days that only use 1 room.</p>
              </div>
              <button 
                onClick={() => setLocalConfig((prev: any) => ({ ...prev, skipSingleOr: !prev.skipSingleOr }))}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none ring-2 ring-slate-700 ring-offset-2 ring-offset-slate-900 ${
                  localConfig.skipSingleOr ? 'bg-emerald-600' : 'bg-slate-700'
                }`}
              >
                <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                  localConfig.skipSingleOr ? 'translate-x-6' : 'translate-x-1'
                }`} />
              </button>
            </div>
          </div>
        </section>

        {/* Format & Reporting */}
        <section className="bg-slate-700/30 border border-slate-600/50 rounded-3xl p-8 space-y-6">
          <div className="flex items-center space-x-3 text-blue-300">
            <Clock size={24} />
            <h3 className="text-xl font-bold text-white uppercase tracking-wide">Format & Reporting</h3>
          </div>

          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Date Format</label>
              <input 
                type="text" 
                value={localConfig.dateFormat}
                onChange={(e) => setLocalConfig((prev: any) => ({ ...prev, dateFormat: e.target.value }))}
                className="w-full bg-[var(--sidebar-bg)] border border-[var(--border-color)] rounded-xl px-4 py-3 text-[var(--text-main)] font-bold focus:ring-2 focus:ring-blue-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Time Format</label>
              <input 
                type="text" 
                value={localConfig.timeFormat}
                onChange={(e) => setLocalConfig((prev: any) => ({ ...prev, timeFormat: e.target.value }))}
                className="w-full bg-[var(--sidebar-bg)] border border-[var(--border-color)] rounded-xl px-4 py-3 text-[var(--text-main)] font-bold focus:ring-2 focus:ring-blue-500 outline-none transition-all"
              />
            </div>

            <div className="space-y-2">
              <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Report Detail Level</label>
              <select 
                value={localConfig.reportDetailLevel}
                onChange={(e) => setLocalConfig((prev: any) => ({ ...prev, reportDetailLevel: e.target.value }))}
                className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-3 text-white font-bold focus:ring-2 focus:ring-blue-500 outline-none transition-all"
              >
                <option value="high">High</option>
                <option value="low">Low</option>
              </select>
            </div>

            <div className="pt-4 flex items-center justify-between">
              <div className="space-y-0.5">
                <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Show Summary Graph</label>
              </div>
              <button 
                onClick={() => setLocalConfig((prev: any) => ({ ...prev, showSummaryGraph: !prev.showSummaryGraph }))}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none ring-2 ring-slate-700 ring-offset-2 ring-offset-slate-900 ${
                  localConfig.showSummaryGraph ? 'bg-blue-600' : 'bg-slate-700'
                }`}
              >
                <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                  localConfig.showSummaryGraph ? 'translate-x-6' : 'translate-x-1'
                }`} />
              </button>
            </div>

            <div className="pt-2 flex items-center justify-between">
              <div className="space-y-0.5">
                <label className="text-sm font-bold text-slate-400 uppercase tracking-wide">Show Detail Graph</label>
              </div>
              <button 
                onClick={() => setLocalConfig((prev: any) => ({ ...prev, showDetailGraph: !prev.showDetailGraph }))}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none ring-2 ring-slate-700 ring-offset-2 ring-offset-slate-900 ${
                  localConfig.showDetailGraph ? 'bg-blue-600' : 'bg-slate-700'
                }`}
              >
                <span className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
                  localConfig.showDetailGraph ? 'translate-x-6' : 'translate-x-1'
                }`} />
              </button>
            </div>
          </div>
        </section>

        {/* CSV Column Mapping */}
        <section className="bg-slate-700/30 border border-slate-600/50 rounded-3xl p-8 space-y-6 md:col-span-2">
          <div className="flex items-center space-x-3 text-amber-300">
            <FileText size={24} />
            <h3 className="text-xl font-bold text-white uppercase tracking-wide">CSV Column Mapping</h3>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {Object.keys(localConfig.mapping).map((key) => (
              <div key={key} className="space-y-2">
                <label className="text-[10px] font-bold text-slate-400 uppercase tracking-widest">{key.replace(/_/g, ' ')}</label>
                <input 
                  type="text" 
                  value={localConfig.mapping[key]}
                  onChange={(e) => updateMapping(key, e.target.value)}
                  className="w-full bg-slate-800 border border-slate-700 rounded-xl px-4 py-2 text-sm text-white font-bold focus:ring-2 focus:ring-amber-500 outline-none transition-all"
                />
              </div>
            ))}
          </div>
        </section>
      </div>

      {/* System Diagnostics */}
      <section className="bg-slate-700/30 border border-slate-600/50 rounded-3xl p-8 space-y-6">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-3 text-blue-300">
            <ShieldCheck size={24} />
            <h3 className="text-xl font-bold text-white uppercase tracking-wide">System Diagnostics</h3>
          </div>
          <button 
            onClick={onRunDiagnostics}
            disabled={isDiagnosticRunning}
            className="flex items-center space-x-2 px-6 py-2.5 rounded-xl bg-slate-700 hover:bg-slate-600 text-white transition-all shadow-lg font-black uppercase tracking-widest text-sm disabled:opacity-50"
          >
            {isDiagnosticRunning ? <RefreshCw className="animate-spin" size={18} /> : <Zap size={18} />}
            <span>{isDiagnosticRunning ? 'Running Tests...' : 'Run System Diagnostics'}</span>
          </button>
        </div>

        <div className="space-y-4">
          <p className="text-slate-400 font-bold">Verify the optimization engine's health and integrity by running a comprehensive suite of automated system checks.</p>
          
          {diagnosticResult && (
            <div className={`rounded-2xl border p-6 space-y-4 animate-in fade-in slide-in-from-top-4 duration-300 ${
              diagnosticResult.success ? 'bg-emerald-500/10 border-emerald-500/30' : 'bg-rose-500/10 border-rose-500/30'
            }`}>
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  {diagnosticResult.success ? (
                    <CheckCircle2 size={24} className="text-emerald-400" />
                  ) : (
                    <AlertTriangle size={24} className="text-rose-400" />
                  )}
                  <span className={`text-lg font-black uppercase tracking-tight ${
                    diagnosticResult.success ? 'text-emerald-400' : 'text-rose-400'
                  }`}>
                    {diagnosticResult.success ? 'All Systems Healthy' : 'Diagnostics Failed'}
                  </span>
                </div>
              </div>

              <div className="bg-black/40 rounded-xl p-4 font-mono text-xs overflow-auto max-h-64 custom-scrollbar whitespace-pre-wrap text-slate-300">
                {diagnosticResult.output || diagnosticResult.error}
              </div>
            </div>
          )}
        </div>
      </section>

      <div className="p-6 bg-blue-600/10 border border-blue-600/20 rounded-2xl flex items-start space-x-4">
        <Info className="text-blue-300 mt-1 flex-shrink-0" />
        <div className="text-sm text-blue-100 leading-relaxed font-bold">
          <strong>Persistent Settings:</strong> These values are saved to a local configuration file and will persist between application sessions. Changes made here will serve as the starting point for all new data ingestion and modeling sessions.
        </div>
      </div>
    </div>
  );
}
