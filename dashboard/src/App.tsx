import React, { useState, useEffect } from 'react'
import { LayoutDashboard, FileUp, Settings, Activity, Users, FileText, HelpCircle, Sun, Moon, ShieldCheck, Briefcase, TrendingUp } from 'lucide-react'
import { useOptimizer } from './hooks/useOptimizer'
import { DataIngestion } from './components/DataIngestion'
import { DashboardView } from './components/DashboardView'
import { ScenarioModeling } from './components/ScenarioModeling'
import { VisualPacking } from './components/VisualPacking'
import { FullReportView } from './components/FullAuditReportView'
import { StaffReductionView } from './components/StaffReductionView'
import { CalculationMethodsView } from './components/CalculationMethodsView'
import { ConfigurationView } from './components/ConfigurationView'

function App() {
  const [activeTab, setActiveTab] = useState('dashboard')
  const [selectedResultIndex, setSelectedResultIndex] = useState(0)
  const [isDarkMode, setIsDarkMode] = useState(true)

  useEffect(() => {
    if (isDarkMode) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }, [isDarkMode])
  const { 
    results, globalMetrics, config, lastAnalyzedConfig, hasData, isAnalyzing, 
    rowCount, fileName, crnaFileName, crnaRowCount, selectCrnaFile,
    validationReport, crnaValidationReport, isValidated, isCleaning, isCleaned, cleanedPath,
    isDiagnosticRunning, diagnosticResult,
    processData, validateFile, cleanFile, runJavaAnalysis, runDiagnostics,
    getReportPath, csvPath,
    updateConfig, savePersistentConfig, triggerRecompute 
  } = useOptimizer()

  const handleUpload = (text: string, path?: string) => {
    processData(text, path)
    setSelectedResultIndex(0)
  }

  const handleAnalyze = async () => {
    const success = await runJavaAnalysis(config)
    if (success) {
      setActiveTab('dashboard')
    }
  }

  const menuItems = [
    { id: 'ingestion', label: 'Add Data', icon: FileUp, disabled: isAnalyzing },
    { id: 'dashboard', label: 'OR Summary', icon: LayoutDashboard, disabled: results.length === 0 || isAnalyzing },
    { id: 'staff-reduction', label: 'Staff', icon: Users, disabled: results.length === 0 || isAnalyzing },
    { id: 'modeling', label: 'What-If Analysis', icon: Activity, disabled: results.length === 0 || isAnalyzing },
    { id: 'deep-dive', label: 'Site Deep-Dive', icon: Users, disabled: results.length === 0 || isAnalyzing },
    { id: 'reports', label: 'Brief Report', icon: FileText, disabled: results.length === 0 || isAnalyzing },
    { id: 'audit-report', label: 'Full Report', icon: FileText, disabled: results.length === 0 || isAnalyzing },
    { id: 'verification', label: 'Audit Guide', icon: ShieldCheck, disabled: isAnalyzing },
    { id: 'methodology', label: 'Calculations', icon: HelpCircle, disabled: isAnalyzing },
  ]

  const handleTabChange = (id: string) => {
    setActiveTab(id);
  }

  // Default to ingestion if no data
  React.useEffect(() => {
    if (!hasData && activeTab !== 'ingestion' && activeTab !== 'settings' && activeTab !== 'methodology') {
      setActiveTab('ingestion')
    }
  }, [hasData, activeTab])

  return (
    <div className="flex h-screen w-full bg-[var(--bg-main)] text-[var(--text-main)] overflow-hidden">
      {/* Sidebar */}
      <aside className="w-64 bg-[var(--sidebar-bg)] flex flex-col border-r border-[var(--border-color)] transition-colors duration-300">
        <div className="p-6 border-b border-[var(--border-color)]">
          <h1 className="text-xl font-bold bg-gradient-to-r from-blue-400 to-emerald-400 bg-clip-text text-transparent">
            smAIrtRx - OR
          </h1>
          <p className="text-xs text-[var(--sidebar-text)] mt-1 uppercase tracking-widest font-bold opacity-70">Enterprise v1.7</p>
        </div>
        
        <nav className="flex-1 p-4 space-y-2">
          {menuItems.map((item) => {
            const Icon = item.icon
            return (
              <button
                key={item.id}
                disabled={item.disabled}
                onClick={() => handleTabChange(item.id)}
                className={`w-full flex items-center space-x-3 px-4 py-3 rounded-lg transition-all ${
                  item.disabled ? 'opacity-30 cursor-not-allowed' :
                  activeTab === item.id 
                    ? 'bg-blue-600/20 text-blue-500 dark:text-blue-300 border border-blue-600/30 shadow-lg' 
                    : 'text-[var(--sidebar-text)] hover:bg-black/5 dark:hover:bg-white/5'
                }`}
              >
                <Icon size={20} className="flex-shrink-0" />
                <span className="font-bold whitespace-nowrap">{item.label}</span>
              </button>
            )
          })}
        </nav>

        <div className="p-4 border-t border-[var(--border-color)] space-y-2">
          <button 
            onClick={() => setIsDarkMode(!isDarkMode)}
            className="w-full flex items-center justify-between px-4 py-3 rounded-lg text-[var(--sidebar-text)] hover:bg-black/5 dark:hover:bg-white/5 transition-all"
          >
            <div className="flex items-center space-x-3">
              {isDarkMode ? <Sun size={20} /> : <Moon size={20} />}
              <span className="font-bold">{isDarkMode ? 'Light Mode' : 'Dark Mode'}</span>
            </div>
          </button>
          
          <button 
            onClick={() => setActiveTab('settings')}
            disabled={isAnalyzing}
            className={`w-full flex items-center space-x-3 px-4 py-3 rounded-lg transition-all ${
              isAnalyzing ? 'opacity-30 cursor-not-allowed' :
              activeTab === 'settings' 
                ? 'bg-blue-600/20 text-blue-500 dark:text-blue-300 border border-blue-600/30 shadow-lg' 
                : 'text-[var(--sidebar-text)] hover:bg-black/5 dark:hover:bg-white/5'
            }`}
          >
            <Settings size={20} />
            <span className="font-bold">Settings</span>
          </button>
        </div>
      </aside>

      {/* Main Content */}
      <main className="flex-1 flex flex-col overflow-hidden relative border-l border-[var(--border-color)]">
        {/* Custom Titlebar Placeholder (Electron Draggable Area) */}
        <div className="h-10 w-full bg-[var(--sidebar-bg)] opacity-80 backdrop-blur flex items-center justify-center pointer-events-none select-none border-b border-[var(--border-color)]" style={{ WebkitAppRegion: 'drag' } as any}>
          <span className="text-xs font-bold uppercase tracking-[0.2em]">smAIrtRx - OR - Standard View</span>
        </div>

        <div className="flex-1 overflow-y-auto p-8 custom-scrollbar relative">
          {isAnalyzing && (
            <div className="absolute inset-0 z-50 flex items-center justify-center bg-[var(--bg-main)]/60 backdrop-blur-sm">
              <div className="flex flex-col items-center space-y-4 p-8 bg-[var(--card-bg)] rounded-2xl border border-[var(--border-color)] shadow-2xl">
                <div className="w-12 h-12 border-4 border-blue-500 border-t-transparent rounded-full animate-spin"></div>
                <div className="text-xl font-bold text-white tracking-wide">
                  {isValidated ? 'Analyzing Data' : 'Validating File'}
                </div>
                <div className="text-slate-400 text-sm">
                  {isValidated ? 'Running OR Schedule Optimizer...' : 'Scanning for structural and logical errors...'}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'dashboard' && hasData && (
            <DashboardView results={results} config={config} metrics={globalMetrics} isAnalyzing={isAnalyzing} />
          )}

          {activeTab === 'staff-reduction' && hasData && (
            <StaffReductionView 
              metrics={globalMetrics} 
              config={config}
              isAnalyzing={isAnalyzing} 
              onUpdateConfig={updateConfig}
              onRecompute={triggerRecompute}
            />
          )}

          {activeTab === 'ingestion' && (
            <DataIngestion 
              onUpload={handleUpload}
              hasData={hasData}
              isAnalyzing={isAnalyzing}
              rowCount={rowCount}
              fileName={fileName}
              crnaFileName={crnaFileName}
              crnaRowCount={crnaRowCount}
              selectCrnaFile={selectCrnaFile}
              validationReport={validationReport}
              crnaValidationReport={crnaValidationReport}
              isValidated={isValidated}
              isCleaning={isCleaning}
              isCleaned={isCleaned}
              onValidate={validateFile}
              onClean={cleanFile}
              onAnalyze={handleAnalyze}
              resultsLoaded={results.length > 0}
            />
          )}

          {activeTab === 'modeling' && hasData && (
            <ScenarioModeling 
              config={config} 
              lastAnalyzedConfig={lastAnalyzedConfig}
              onUpdate={updateConfig} 
              metrics={globalMetrics} 
              isAnalyzing={isAnalyzing} 
              onRecompute={triggerRecompute} 
            />
          )}

          {activeTab === 'deep-dive' && hasData && results.length > 0 && (
            <div className="space-y-6">
              <div className="flex items-center space-x-4 bg-slate-800/50 p-4 rounded-2xl border border-slate-700/50">
                <label className="text-sm font-medium text-slate-400">Select Site/Date Analysis:</label>
                <select 
                  value={selectedResultIndex}
                  onChange={(e) => setSelectedResultIndex(Number(e.target.value))}
                  className="bg-slate-900 border border-slate-700 text-slate-200 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block p-2.5"
                >
                  {results.map((r, idx) => (
                    <option key={idx} value={idx}>
                      {r.roomsSaved > 0 ? '✨ ' : ''}
                      {r.site} - {r.date} ({r.cases.length} cases)
                      {r.roomsSaved > 0 ? ` [Saved ${r.roomsSaved} Room${r.roomsSaved > 1 ? 's' : ''}]` : ''}
                    </option>
                  ))}
                </select>
              </div>
              <VisualPacking data={results[selectedResultIndex]} />
            </div>
          )}

          {activeTab === 'reports' && hasData && (
            <div className="max-w-5xl mx-auto space-y-8 pb-20 animate-in fade-in duration-500">
              <div className="flex justify-between items-center">
                <h2 className="text-2xl font-bold text-[var(--text-main)] flex items-center space-x-2">
                  <FileText className="text-blue-500" />
                  <span>Brief Report</span>
                </h2>
                <button
                  onClick={async () => {
                    if (window.ipcRenderer) {
                      const btn = document.getElementById('cfo-report-btn-inline');
                      if (btn) btn.innerText = 'Exporting Package...';
                      try {
                        const res: any = await window.ipcRenderer.invoke('create-cfo-report', {
                          csvPath: csvPath || undefined,
                          capacity: config.validator.binCapacity,
                          startPad: config.validator.startPadMinutes,
                          endPad: config.validator.endPadMinutes
                        });
                        console.log('[DEBUG_UI] create-cfo-report response:', res);
                        if (res?.status === 'CFO_REPORT_CREATED') {
                          alert(`CFO Report Package exported successfully!\n\nLocation: ${config.reportPackagePath || 'src/main/resources/report/CFO_Report_Package.zip'}`);
                        } else {
                          alert(`Error: ${res?.error || res?.stderr || 'Unknown failure'}`);
                        }
                      } catch (e: any) {
                        console.error('[DEBUG_UI] create-cfo-report catch error:', e);
                        alert(`Failed to export report: ${e?.message || 'Unknown error'}`);
                      } finally {
                        if (btn) btn.innerText = 'Export CFO Report';
                      }
                    }
                  }}
                  id="cfo-report-btn-inline"
                  className="flex items-center space-x-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 active:translate-y-0.5 text-white rounded-lg font-bold text-sm transition-all shadow-lg"
                >
                  <Briefcase size={16} />
                  <span>Export CFO Report</span>
                </button>
              </div>

              <div className="bg-white dark:bg-slate-900 rounded-3xl shadow-2xl border border-slate-200 dark:border-slate-800 p-12 space-y-12 text-slate-900 dark:text-slate-100">
                <section>
                  <h3 className="text-xl font-bold flex items-center space-x-2 border-l-4 border-blue-600 pl-4 mb-6">
                    <TrendingUp size={20} className="text-blue-600" />
                    <span>Executive Summary: Recovery Opportunity</span>
                  </h3>
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                    <div className="p-6 bg-emerald-50 dark:bg-emerald-900/20 rounded-2xl border border-emerald-100 dark:border-emerald-800 text-center">
                      <div className="text-[10px] font-black uppercase text-emerald-600 dark:text-emerald-400 mb-1">Hard Savings: Labor</div>
                      <div className="text-3xl font-black text-emerald-500">${(
                        Math.round(((globalMetrics?.extraCrnaHours || 0) * (config.financials.crnaHourRate || 175) + (globalMetrics?.anesthesiaSavings || 0) * (config.financials.anesthesiologistRate || 3500)) * (globalMetrics?.annualizationFactor || 12) / 1000000)
                      ).toFixed(2)}M</div>
                    </div>
                    <div className="p-6 bg-blue-50 dark:bg-blue-900/20 rounded-2xl border border-blue-100 dark:border-blue-800 text-center">
                      <div className="text-[10px] font-black uppercase text-blue-600 dark:text-blue-400 mb-1">Soft Savings: Assets</div>
                      <div className="text-3xl font-black text-blue-500">${(
                        Math.round(((globalMetrics?.roomsSaved || 0) * (config.financials.costPerOrDay || 1500)) * (globalMetrics?.annualizationFactor || 12) / 1000000)
                      ).toFixed(2)}M</div>
                    </div>
                    <div className="p-6 bg-slate-50 dark:bg-slate-800/50 rounded-2xl border border-slate-100 dark:border-slate-700 text-center">
                      <div className="text-[10px] font-black uppercase text-slate-400 mb-1">OR-Days Saved</div>
                      <div className="text-3xl font-black text-slate-600 dark:text-slate-300">{Math.round(globalMetrics?.roomsSaved || 0)}</div>
                    </div>
                    <div className="p-6 bg-blue-600 rounded-2xl text-white text-center shadow-xl">
                      <div className="text-[10px] font-black uppercase text-blue-100 mb-1">Total Strategic ROI</div>
                      <div className="text-3xl font-black">${(
                        (Math.round(((globalMetrics?.extraCrnaHours || 0) * (config.financials.crnaHourRate || 175) + (globalMetrics?.anesthesiaSavings || 0) * (config.financials.anesthesiologistRate || 3500)) * (globalMetrics?.annualizationFactor || 12) / 1000000) +
                        Math.round(((globalMetrics?.roomsSaved || 0) * (config.financials.costPerOrDay || 1500)) * (globalMetrics?.annualizationFactor || 12) / 1000000))
                      ).toFixed(2)}M</div>
                    </div>
                  </div>
                </section>

                <section>
                  <h3 className="text-xl font-bold flex items-center space-x-2 border-l-4 border-slate-900 dark:border-white pl-4 mb-6 uppercase tracking-tight">
                    <span>Site Performance Matrix</span>
                  </h3>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse min-w-[500px]">
                      <thead>
                        <tr className="bg-slate-100 dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700">
                          <th className="p-4 font-bold uppercase text-[10px] tracking-widest text-slate-400">Site Name</th>
                          <th className="p-4 font-bold text-center uppercase text-[10px] tracking-widest text-slate-400">OR-Days Saved</th>
                          <th className="p-4 font-bold text-right uppercase text-[10px] tracking-widest text-slate-400">CRNA Hours Saved</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(() => {
                          const siteMap = new Map();
                          for (const r of results) {
                            const s = siteMap.get(r.site) || { name: r.site, saved: 0, crna: 0 };
                            s.saved += (r.roomsSaved || 0);
                            s.crna += (r.extraCrnaHours || 0);
                            siteMap.set(r.site, s);
                          }
                          const sorted = Array.from(siteMap.values()).sort((a, b) => b.saved - a.saved);
                          const maxCrna = Math.max(...sorted.map(s => s.crna), 0);
                          const chartMax = Math.max(500, Math.ceil((maxCrna + 200) / 500) * 500);
                          (window as any)._inlineChartMax = chartMax;
                          (window as any)._inlineSorted = sorted;

                          return sorted.map((site, i) => (
                            <tr key={i} className="border-b border-slate-50 dark:border-slate-800 last:border-0 hover:bg-black/5 dark:hover:bg-white/5 transition-colors">
                              <td className="p-4 font-bold text-slate-700 dark:text-slate-200">{site.name}</td>
                              <td className="p-4 text-center font-mono text-emerald-600 font-black">{site.saved}</td>
                              <td className="p-4 text-right font-mono text-blue-600 dark:text-blue-400 font-bold">{Math.round(site.crna).toLocaleString()}</td>
                            </tr>
                          ));
                        })()}
                      </tbody>
                    </table>
                  </div>
                </section>

                {((window as any)._inlineSorted || []).some((s: any) => s.crna > 0.5) && (
                  <section className="space-y-8 pt-8 border-t border-slate-100 dark:border-slate-800">
                    <h3 className="text-xl font-bold border-l-4 border-slate-900 dark:border-white pl-4 uppercase tracking-tight">CRNA Hours Saved Analysis</h3>
                    <p className="text-xs italic text-slate-500 pl-4">Sites with no CRNA hours saved are not graphed. Scale: {(window as any)._inlineChartMax} hrs max.</p>
                    <div className="space-y-6">
                      {((window as any)._inlineSorted || []).filter((s: any) => s.crna > 0.5).map((site: any, idx: number) => {
                        const width = Math.min(100, (site.crna / (window as any)._inlineChartMax) * 100);
                        return (
                          <div key={idx} className="space-y-2">
                            <div className="flex justify-between text-xs font-bold uppercase tracking-wide">
                              <span>{site.name}</span>
                              <span className="text-blue-600 font-mono">{Math.round(site.crna).toLocaleString()} hrs</span>
                            </div>
                            <div className="h-8 w-full bg-slate-100 dark:bg-slate-800 rounded-full overflow-hidden border border-slate-200 dark:border-slate-700">
                              <div 
                                className="h-full bg-gradient-to-r from-blue-500 to-blue-600 shadow-inner transition-all duration-1000"
                                style={{ width: `${width}%` }}
                              />
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </section>
                )}
              </div>
            </div>
          )}

          {activeTab === 'audit-report' && hasData && (
            <FullReportView 
              getReportPath={getReportPath} 
              isAnalyzing={isAnalyzing} 
              csvPath={csvPath}
              config={config}
            />
          )}

          {activeTab === 'methodology' && (
            <CalculationMethodsView />
          )}

          {activeTab === 'verification' && (
            <div className="flex flex-col h-full space-y-4 animate-in fade-in duration-500 text-[var(--text-main)]">
              <div className="flex justify-between items-center">
                <div>
                  <h2 className="text-3xl font-bold text-[var(--text-main)] flex items-center space-x-3">
                    <ShieldCheck size={32} className="text-emerald-500" />
                    <span>Audit & Verification Guide</span>
                  </h2>
                  <p className="text-[var(--text-main)] opacity-60 mt-1 ml-11">Step-by-step instructions for manual data reconciliation</p>
                </div>
              </div>
              <div className="flex-1 bg-white rounded-2xl overflow-hidden border border-[var(--border-color)] shadow-2xl relative">
                <iframe 
                  src={(window as any)._auditGuidePath || ""} 
                  className="w-full h-full border-none"
                  title="Audit Guide"
                />
              </div>
            </div>
          )}

          {activeTab === 'settings' && (
            <ConfigurationView 
              config={config} 
              onSave={savePersistentConfig} 
              onRunDiagnostics={runDiagnostics}
              isDiagnosticRunning={isDiagnosticRunning}
              diagnosticResult={diagnosticResult}
            />
          )}
        </div>
      </main>
    </div>
  )
}

export default App