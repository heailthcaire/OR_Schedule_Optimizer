import React, { useEffect, useState } from 'react';
import { RefreshCw, HelpCircle } from 'lucide-react';

declare global {
  interface Window {
    ipcRenderer: any;
  }
}

export function CalculationMethodsView() {
  const [docPath, setDocPath] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadPath = async () => {
      try {
        if (window.ipcRenderer) {
          // Assuming we can get the path to resources/documentation/calculation_methodology_summary.html
          // We might need to add an IPC handler for this or use a relative path if supported
          const path = await window.ipcRenderer.invoke('get-doc-path', 'calculation_methodology_summary.html');
          if (path) {
            setDocPath(path);
          } else {
            setError('Could not determine documentation path.');
          }
        }
      } catch (err) {
        setError(`Error loading documentation path: ${err}`);
      }
    };
    loadPath();
  }, []);

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-[var(--text-main)] opacity-60 space-y-4">
        <div className="text-xl font-bold text-rose-500">Error</div>
        <p>{error}</p>
      </div>
    );
  }

  if (!docPath) {
    return (
      <div className="flex flex-col items-center justify-center h-full text-[var(--text-main)] opacity-60">
        <RefreshCw className="animate-spin mb-4" size={48} />
        <p>Loading Calculation Methods...</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full space-y-4 animate-in fade-in duration-500 text-[var(--text-main)]">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-3xl font-bold text-[var(--text-main)] flex items-center space-x-3">
            <HelpCircle size={32} className="text-blue-500" />
            <span>Calculation Methods</span>
          </h2>
          <p className="text-[var(--text-main)] opacity-60 mt-1 ml-11">Definitions and mathematical formulas for all system metrics</p>
        </div>
      </div>

      <div className="flex-1 bg-white rounded-2xl overflow-hidden border border-[var(--border-color)] shadow-2xl relative">
        <iframe 
          src={docPath} 
          className="w-full h-full border-none"
          title="Calculation Methods"
        />
      </div>
    </div>
  );
}