import React, { useEffect, useState } from 'react';
import { RefreshCw, ExternalLink, Briefcase } from 'lucide-react';

interface Props {
  getReportPath: () => Promise<string | null>;
  isAnalyzing: boolean;
  csvPath: string | null;
  config: any;
}

export function FullReportView({ getReportPath, isAnalyzing, csvPath, config }: Props) {
  const [reportPath, setReportPath] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [key, setKey] = useState(0); // Used to force iframe refresh

  useEffect(() => {
    const loadPath = async () => {
      try {
        const path = await getReportPath();
        if (path) {
          // The path is already a properly encoded file:// URL from the main process
          // Append a timestamp to bypass caching
          const cacheBuster = `?t=${new Date().getTime()}`;
          setReportPath(path + cacheBuster);
        } else {
          setError('Could not determine report path.');
        }
      } catch (err) {
        setError(`Error loading report path: ${err}`);
      }
    };
    loadPath();
  }, [getReportPath, key]); // Re-run when key changes to get a fresh timestamp

  const handleRefresh = () => {
    setKey(prev => prev + 1);
  };

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-[var(--text-main)] opacity-60 space-y-4">
        <div className="text-xl font-bold text-rose-500">Error</div>
        <p>{error}</p>
      </div>
    );
  }

  if (!reportPath) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-[var(--text-main)] opacity-60">
        <RefreshCw className="animate-spin mb-4" size={48} />
        <p>Locating Full Report...</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full space-y-4 animate-in fade-in duration-500 text-[var(--text-main)]">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold text-[var(--text-main)]">Full Report</h2>
          <p className="text-[var(--text-main)] opacity-60 mt-1">Deep-dive mathematical proof and operational details (Java Engine Source of Truth)</p>
        </div>
        <div className="flex space-x-3">
          <button
            onClick={async () => {
              if (window.ipcRenderer) {
                const btn = document.getElementById('cfo-report-btn-full');
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
            id="cfo-report-btn-full"
            className="flex items-center space-x-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 active:translate-y-0.5 text-white rounded-lg font-bold text-sm transition-all shadow-lg"
          >
            <Briefcase size={16} />
            <span>Export CFO Report</span>
          </button>
          <button 
            onClick={handleRefresh}
            disabled={isAnalyzing}
            className="flex items-center space-x-2 px-4 py-2 bg-[var(--card-bg)] hover:bg-black/5 dark:hover:bg-white/5 text-[var(--text-main)] rounded-lg border border-[var(--border-color)] transition-all"
          >
            <RefreshCw size={18} className={isAnalyzing ? 'animate-spin' : ''} />
            <span>Refresh Report</span>
          </button>
        </div>
      </div>

      <div className="flex-1 bg-white rounded-2xl overflow-hidden border border-[var(--border-color)] shadow-2xl relative">
        {isAnalyzing && (
          <div className="absolute inset-0 bg-black/20 backdrop-blur-[2px] z-10 flex items-center justify-center">
            <div className="bg-[var(--card-bg)] p-6 rounded-xl border border-[var(--border-color)] shadow-xl flex items-center space-x-4">
              <RefreshCw className="text-blue-500 animate-spin" size={24} />
              <span className="text-[var(--text-main)] font-bold">Java Engine Updating Report...</span>
            </div>
          </div>
        )}
        <iframe 
          key={key}
          src={reportPath} 
          className="w-full h-full border-none"
          title="Java Full Report"
        />
      </div>
      
      <div className="text-xs text-[var(--text-main)] opacity-40 flex items-center justify-center space-x-2 pb-2">
        <ExternalLink size={12} />
        <span>Source: {reportPath}</span>
      </div>
    </div>
  );
}
