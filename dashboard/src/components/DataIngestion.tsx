import { useCallback, useState } from 'react'
import { FileUp, CheckCircle, AlertCircle, FileText, Search, ShieldCheck, Zap } from 'lucide-react'

interface Props {
  onUpload: (text: string, path?: string) => void;
  hasData: boolean;
  isAnalyzing: boolean;
  rowCount: number;
  crnaRowCount: number;
  fileName: string | null;
  crnaFileName: string | null;
  selectCrnaFile: () => void;
  validationReport?: any;
  crnaValidationReport?: any;
  isValidated: boolean;
  isCleaning: boolean;
  isCleaned: boolean;
  onValidate: () => void;
  onClean: () => void;
  onAnalyze: () => void;
  resultsLoaded: boolean;
}

export function DataIngestion({ 
  onUpload, hasData, isAnalyzing, rowCount, crnaRowCount, fileName,
  crnaFileName, selectCrnaFile,
  validationReport, crnaValidationReport, isValidated, isCleaning, isCleaned,
  onValidate, onClean, onAnalyze, resultsLoaded
}: Props) {
  const [isReading, setIsReading] = useState(false);

  const onSelectFile = useCallback(async () => {
    console.log('onSelectFile triggered');
    if (isAnalyzing || isReading) {
      console.warn('Already reading or analyzing, ignoring');
      return;
    }

    if (!window.ipcRenderer) {
      console.error('ipcRenderer not available');
      return;
    }

    setIsReading(true);
    try {
      const result = await window.ipcRenderer.invoke('select-file');
      console.log('File selection result:', result ? { name: result.name, size: result.size, path: result.path } : 'null');
      
      if (result) {
        // Store path globally for display purposes since we no longer use input element
        (window as any)._selectedFilePath = result.path;
        // Hook's processData will clear CRNA state and _selectedCrnaFilePath
        onUpload(result.content, result.path);
      }
    } catch (err) {
      console.error('Error during file selection:', err);
      alert('Failed to select file. Please try again.');
    } finally {
      setIsReading(false);
    }
  }, [onUpload, isAnalyzing, isReading]);

  const onSelectCrnaFile = useCallback(async () => {
    console.log('onSelectCrnaFile triggered');
    if (isAnalyzing || isReading) {
      console.warn('Already reading or analyzing, ignoring');
      return;
    }

    if (!window.ipcRenderer) {
      console.error('ipcRenderer not available');
      return;
    }

    setIsReading(true);
    try {
      // Hook's selectCrnaFile will clear OR state and _selectedFilePath
      await selectCrnaFile();
    } catch (err) {
      console.error('Error during CRNA file selection:', err);
    } finally {
      setIsReading(false);
    }
  }, [selectCrnaFile, isAnalyzing, isReading]);

  const showSpin = isAnalyzing || isReading;

  return (
    <div className="max-w-4xl mx-auto py-12 space-y-8 animate-in slide-in-from-bottom-4 duration-500 text-[var(--text-main)]">
      <div className="text-center">
        <h2 className="text-3xl font-bold text-[var(--text-main)] uppercase tracking-tight">Add Data</h2>
        <p className="text-[var(--text-main)] opacity-70 mt-2 font-bold">Upload your EHR surgical cases (CSV) to begin optimization</p>
      </div>
      
      <div className="relative group">
        <div className="absolute -inset-1 bg-gradient-to-r from-blue-600 to-emerald-600 rounded-[2rem] blur opacity-25 group-hover:opacity-50 transition duration-1000 group-hover:duration-200"></div>
        <div 
          className={`relative block border-2 border-dashed rounded-3xl p-16 flex flex-col items-center justify-center space-y-4 transition-all ${
          showSpin ? 'border-blue-500 bg-[var(--card-bg)]/80 cursor-wait' :
          (hasData || crnaFileName) ? 'border-emerald-500/50 bg-emerald-500/5' : 'border-[var(--border-color)] bg-[var(--card-bg)]/50 hover:border-blue-500/50 hover:bg-blue-500/5 cursor-pointer'
        }`}
        onClick={(!hasData && !crnaFileName && !showSpin) ? onSelectFile : undefined}
        >
          <div className={`p-6 rounded-2xl transition-colors ${
            showSpin ? 'bg-blue-600/20' :
            (hasData || crnaFileName) ? 'bg-emerald-600/10' : 'bg-[var(--sidebar-bg)] group-hover:bg-blue-600/10'
          }`}>
            {showSpin ? (
              <div className="w-12 h-12 border-4 border-blue-400 border-t-transparent rounded-full animate-spin"></div>
            ) : (hasData || crnaFileName) ? (
              <CheckCircle size={48} className="text-emerald-500 dark:text-emerald-300" />
            ) : (
              <FileUp size={48} className="text-[var(--text-main)] group-hover:text-blue-500 dark:group-hover:text-blue-300 transition-colors" />
            )}
          </div>
          
          <div className="text-center">
            <p className="text-xl font-black text-[var(--text-main)] uppercase tracking-wide">
              {isReading ? 'Reading File...' : 
               isAnalyzing ? (isValidated ? 'Analyzing Data...' : 'Validating File...') : 
               isCleaning ? 'Cleaning File...' :
               (hasData && crnaFileName) ? (isCleaned ? 'Data Cleaned & Loaded' : 'Both Files Selected') :
               (hasData || crnaFileName) ? (isCleaned ? 'Data Cleaned & Loaded' : 'File Selected') : 'Click to select a file'}
            </p>
            <div className="text-sm text-slate-100 mt-2 font-bold flex flex-col items-center space-y-1">
              {isReading ? <span>Accessing local filesystem...</span> :
               isAnalyzing ? (isValidated ? <span>Preparing Data for Optimization</span> : <span>Scanning for structural and logical errors</span>) : 
               isCleaning ? <span>Correcting formats and removing outliers</span> :
               (hasData || crnaFileName) ? (
                 <>
                   {isCleaned ? <span>Cleaned version is ready for analysis</span> : <span>File is loaded and ready for validation</span>}
                   {fileName && (
                     <div className="mt-2 flex flex-col items-center space-y-2">
                       <div className="flex items-center space-x-2 bg-emerald-500/20 px-4 py-1 rounded-full border border-emerald-500/30">
                         <FileText size={14} />
                         <span>{fileName}: <b>{rowCount.toLocaleString()} rows (CASE)</b></span>
                       </div>
                     </div>
                   )}
                   {crnaFileName && (
                     <div className="mt-2 flex flex-col items-center space-y-2">
                       <div className="flex items-center space-x-2 bg-blue-500/20 px-4 py-1 rounded-full border border-blue-500/30">
                         <FileText size={14} />
                         <span>{crnaFileName}: <b>{crnaRowCount.toLocaleString()} rows (CRNA)</b></span>
                       </div>
                     </div>
                   )}
                   {(fileName || crnaFileName) && (
                      <div className="text-[10px] text-slate-400 font-mono break-all w-full max-w-2xl px-4 opacity-60 mt-4 flex flex-col space-y-2 items-center">
                        {(window as any)._selectedFilePath && (
                          <div className="flex items-center space-x-2 bg-black/20 py-1 px-3 rounded-md w-full">
                            <span className="opacity-50 uppercase tracking-tighter font-black flex-shrink-0">OR Path:</span>
                            <span className="truncate">{ (window as any)._selectedFilePath }</span>
                          </div>
                        )}
                        {(window as any)._selectedCrnaFilePath && (
                          <div className="flex items-center space-x-2 bg-black/20 py-1 px-3 rounded-md w-full">
                            <span className="opacity-50 uppercase tracking-tighter font-black flex-shrink-0">CRNA Path:</span>
                            <span className="truncate">{ (window as any)._selectedCrnaFilePath }</span>
                          </div>
                        )}
                      </div>
                   )}
                 </>
               ) : <span>Surgical_Case_Data.csv (Max 50MB)</span>}
            </div>
          </div>

      {!showSpin && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div 
            onClick={onSelectFile}
            className="px-8 py-3 bg-blue-600 hover:bg-blue-500 rounded-full text-white font-black uppercase tracking-widest transition-all transform hover:scale-105 shadow-xl shadow-blue-900/40 cursor-pointer text-center truncate">
            {fileName ? `CASE: ${fileName}` : 'Select Case File'}
          </div>
          <div 
            onClick={onSelectCrnaFile}
            className={`px-8 py-3 rounded-full font-black uppercase tracking-widest transition-all transform hover:scale-105 shadow-xl cursor-pointer text-center truncate ${
              crnaFileName 
                ? 'bg-emerald-600 hover:bg-emerald-500 text-white shadow-emerald-900/40' 
                : 'bg-slate-700 hover:bg-slate-600 text-slate-200 shadow-slate-900/40'
            }`}>
            {crnaFileName ? `CRNA: ${crnaFileName}` : 'Select CRNA File (Optional)'}
          </div>
        </div>
      )}

          {(hasData || crnaFileName) && !showSpin && !isCleaning && (
             <div className="flex flex-col items-center space-y-6 w-full max-w-2xl mt-4">
                {/* Stages / Buttons */}
                <div className="flex flex-col gap-4 w-full">
                   <div className="grid grid-cols-2 gap-4 w-full">
                      <button 
                        onClick={onValidate}
                        disabled={isValidated || isAnalyzing || (!hasData && !crnaFileName)}
                        className={`flex flex-col items-center justify-center p-4 rounded-2xl border-2 transition-all space-y-2 ${
                          isValidated 
                           ? 'bg-emerald-500/10 border-emerald-500/50 text-emerald-600 dark:text-emerald-300' 
                           : (!hasData && !crnaFileName)
                             ? 'opacity-30 cursor-not-allowed bg-[var(--card-bg)] border-[var(--border-color)] text-[var(--text-main)]'
                             : 'bg-[var(--card-bg)] border-[var(--border-color)] text-[var(--text-main)] hover:border-blue-500 hover:bg-blue-500/10'
                        }`}
                      >
                        <Search size={24} />
                        <span className="text-xs font-black uppercase tracking-widest">1. Validate</span>
                      </button>

                      <button 
                        onClick={onAnalyze}
                        disabled={!isValidated || !hasData || (validationReport && validationReport.missingHeaders && validationReport.missingHeaders.length > 0) || resultsLoaded || isAnalyzing}
                        title={!hasData ? "OR Schedule data is required for optimization analysis" : ""}
                        className={`flex flex-col items-center justify-center p-4 rounded-2xl border-2 transition-all space-y-2 ${
                          resultsLoaded
                           ? 'bg-emerald-500/10 border-emerald-500/50 text-emerald-600 dark:text-emerald-300'
                           : !isValidated || !hasData || (validationReport && validationReport.missingHeaders && validationReport.missingHeaders.length > 0)
                             ? 'opacity-30 cursor-not-allowed bg-[var(--sidebar-bg)] border-[var(--border-color)] text-[var(--text-main)]'
                             : 'bg-blue-600 border-blue-500 text-white hover:bg-blue-500 shadow-lg'
                        }`}
                      >
                        <Zap size={24} />
                        <span className="text-xs font-black uppercase tracking-widest">2. Analyze</span>
                      </button>
                   </div>

                   <button 
                     onClick={onClean}
                     disabled={!isValidated || !hasData || isCleaned || (validationReport && validationReport.totalErrorRows === 0)}
                     className={`flex flex-row items-center justify-center p-4 rounded-2xl border-2 transition-all space-x-4 ${
                       isCleaned 
                        ? 'bg-emerald-500/10 border-emerald-500/50 text-emerald-600 dark:text-emerald-300' 
                        : !isValidated || !hasData || (validationReport && validationReport.totalErrorRows === 0)
                          ? 'opacity-30 cursor-not-allowed bg-[var(--card-bg)] border-[var(--border-color)] text-[var(--text-main)]'
                          : 'bg-[var(--card-bg)] border-[var(--border-color)] text-[var(--text-main)] hover:border-blue-500 hover:bg-blue-500/10'
                     }`}
                   >
                     <ShieldCheck size={24} />
                     <span className="text-xs font-black uppercase tracking-widest">Create Backup File</span>
                   </button>
                </div>

                {/* Validation Report Display */}
                {isValidated && (
                  <div className={`grid gap-6 animate-in zoom-in-95 duration-500 w-full ${(validationReport || crnaValidationReport) ? 'grid-cols-1 xl:grid-cols-2' : 'grid-cols-1'}`}>
                    {(!validationReport && !crnaValidationReport) ? (
                      <div className="col-span-full p-4 bg-slate-900 border border-slate-800 rounded-2xl text-center">
                         <span className="text-sm text-slate-400 font-bold uppercase">No validation data available. Ensure files were selected and scanned.</span>
                         <div className="text-[10px] text-slate-600 mt-2 font-mono">
                           Debug: OR={fileName ? 'selected' : 'none'} CRNA={crnaFileName ? 'selected' : 'none'}
                         </div>
                      </div>
                    ) : null}
                    {validationReport && (
                     <div className="bg-slate-950 rounded-2xl border border-slate-800 overflow-hidden">
                       <div className="bg-slate-900 px-6 py-3 border-b border-slate-800 flex justify-between items-center">
                         <span className="text-xs font-black text-slate-400 uppercase tracking-[0.2em]">OR Validation Report</span>
                         {!validationReport.isHeaderRowPresent || (validationReport.missingHeaders && validationReport.missingHeaders.length > 0) ? (
                           <span className="text-[10px] px-2 py-0.5 bg-rose-500/20 text-rose-400 rounded-full font-bold border border-rose-500/30">FAILED</span>
                         ) : (
                           <span className="text-[10px] px-2 py-0.5 bg-emerald-500/20 text-emerald-400 rounded-full font-bold border border-emerald-500/30">SUCCESS</span>
                         )}
                       </div>

                       {!validationReport.isHeaderRowPresent ? (
                          <div className="p-8 flex flex-col items-center justify-center text-center">
                            <AlertCircle className="text-rose-500 mb-4" size={48} />
                            <span className="text-sm font-black text-rose-400 uppercase tracking-widest block mb-2">OR Schedule: Invalid Format</span>
                            <span className="text-xs text-slate-500 font-bold uppercase max-w-xs">The selected file could not be parsed. Ensure it has the required headers and correct CSV structure.</span>
                            {/* Path debug if available */}
                            {(window as any)._selectedFilePath && (
                              <div className="mt-4 text-[9px] text-slate-600 font-mono break-all opacity-50">
                                Path: {(window as any)._selectedFilePath}
                              </div>
                            )}
                          </div>
                       ) : (
                        <>
                          <div className="p-6 grid grid-cols-2 gap-8">
                            <div className="space-y-4">
                                <div className="flex justify-between items-end">
                                  <span className="text-xs text-slate-500 font-bold uppercase">Total Rows</span>
                                  <span className="text-xl font-black text-white">{(validationReport.totalRowsRead || 0).toLocaleString()}</span>
                                </div>
                                <div className="flex justify-between items-end">
                                  <span className="text-xs text-slate-500 font-bold uppercase">Valid Rows</span>
                                  <span className="text-xl font-black text-emerald-400">{(validationReport.totalValidRows || 0).toLocaleString()}</span>
                                </div>
                                <div className="flex justify-between items-end">
                                  <span className="text-xs text-slate-500 font-bold uppercase">Error Rows</span>
                                  <span className={`text-xl font-black ${(validationReport.totalErrorRows || 0) > 0 ? 'text-rose-400' : 'text-slate-400'}`}>
                                    {(validationReport.totalErrorRows || 0).toLocaleString()}
                                  </span>
                                </div>
                            </div>
                            <div className="space-y-3">
                                <div className="flex items-center space-x-3">
                                  <div className={`w-2 h-2 rounded-full ${validationReport.missingHeaders && validationReport.missingHeaders.length === 0 ? 'bg-emerald-400' : 'bg-rose-400'}`}></div>
                                  <span className="text-xs text-slate-300 font-bold">
                                      {validationReport.missingHeaders && validationReport.missingHeaders.length === 0 ? 'All required columns found' : 'Missing required columns (Structure)'}
                                  </span>
                                </div>
                                <div className="flex items-center space-x-3">
                                  <div className="w-2 h-2 rounded-full bg-emerald-400"></div>
                                  <span className="text-xs text-slate-300 font-bold">All Dates are valid</span>
                                </div>
                                <div className="flex items-center space-x-3">
                                  <div className="w-2 h-2 rounded-full bg-emerald-400"></div>
                                  <span className="text-xs text-slate-300 font-bold">All Case IDs present</span>
                                </div>
                            </div>
                          </div>
                        
                          {validationReport.missingHeaders && validationReport.missingHeaders.length > 0 && (
                            <div className="px-6 pb-6 pt-2 border-t border-slate-900/50">
                              <span className="text-[10px] font-black text-rose-400 uppercase tracking-widest block mb-3">Missing Required Columns (Entire file):</span>
                              <div className="flex flex-wrap gap-2">
                                {validationReport.missingHeaders.map((header: string) => (
                                  <div key={header} className="bg-rose-500/10 px-3 py-1 rounded-full border border-rose-500/30">
                                      <span className="text-xs text-rose-300 font-bold">{header}</span>
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}
                        
                          {validationReport.skippedReasons && Object.keys(validationReport.skippedReasons).length > 0 && (
                            <div className="px-6 pb-6 pt-2 border-t border-slate-900/50">
                              <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest block mb-3">Error Breakdown:</span>
                              <div className="space-y-2">
                                {Object.entries(validationReport.skippedReasons).map(([reason, count]) => (
                                  <div key={reason} className="flex justify-between items-center bg-slate-900/50 px-3 py-2 rounded-lg border border-slate-800/50">
                                      <span className="text-xs text-slate-400 font-medium">{reason}</span>
                                      <span className="text-xs font-bold text-rose-300">{count as number}</span>
                                  </div>
                                ))}
                              </div>
                            </div>
                          )}
                        </>
                       )}
                    </div>
                    )}

                    {crnaValidationReport && (
                      <div className="bg-slate-950 rounded-2xl border border-slate-800 overflow-hidden">
                        <div className="bg-slate-900 px-6 py-3 border-b border-slate-800 flex justify-between items-center">
                          <span className="text-xs font-black text-slate-400 uppercase tracking-[0.2em]">CRNA Validation Report</span>
                          {!crnaValidationReport.isHeaderRowPresent || (crnaValidationReport.missingHeaders && crnaValidationReport.missingHeaders.length > 0) ? (
                            <span className="text-[10px] px-2 py-0.5 bg-rose-500/20 text-rose-400 rounded-full font-bold border border-rose-500/30">FAILED</span>
                          ) : (
                            <span className="text-[10px] px-2 py-0.5 bg-emerald-500/20 text-emerald-400 rounded-full font-bold border border-emerald-500/30">SUCCESS</span>
                          )}
                        </div>

                        {!crnaValidationReport.isHeaderRowPresent ? (
                          <div className="p-8 flex flex-col items-center justify-center text-center">
                            <AlertCircle className="text-rose-500 mb-4" size={48} />
                            <span className="text-sm font-black text-rose-400 uppercase tracking-widest block mb-2">CRNA Schedule: Invalid Format</span>
                            <span className="text-xs text-slate-500 font-bold uppercase max-w-xs">The selected file could not be parsed. Ensure it has the required headers and correct CSV structure.</span>
                            {/* Path debug if available */}
                            {(window as any)._selectedCrnaFilePath && (
                              <div className="mt-4 text-[9px] text-slate-600 font-mono break-all opacity-50">
                                Path: {(window as any)._selectedCrnaFilePath}
                              </div>
                            )}
                          </div>
                        ) : (
                          <>
                            <div className="p-6 grid grid-cols-2 gap-8">
                              <div className="space-y-4">
                                  <div className="flex justify-between items-end">
                                    <span className="text-xs text-slate-500 font-bold uppercase">Total Rows</span>
                                    <span className="text-xl font-black text-white">{(crnaValidationReport.totalRowsRead || 0).toLocaleString()}</span>
                                  </div>
                                  <div className="flex justify-between items-end">
                                    <span className="text-xs text-slate-500 font-bold uppercase">Valid Rows</span>
                                    <span className="text-xl font-black text-emerald-400">{(crnaValidationReport.totalValidRows || 0).toLocaleString()}</span>
                                  </div>
                                  <div className="flex justify-between items-end">
                                    <span className="text-xs text-slate-500 font-bold uppercase">Error Rows</span>
                                    <span className={`text-xl font-black ${(crnaValidationReport.totalErrorRows || 0) > 0 ? 'text-rose-400' : 'text-slate-400'}`}>
                                      {(crnaValidationReport.totalErrorRows || 0).toLocaleString()}
                                    </span>
                                  </div>
                              </div>
                              <div className="space-y-3">
                                  <div className="flex items-center space-x-3">
                                    <div className={`w-2 h-2 rounded-full ${crnaValidationReport.missingHeaders && crnaValidationReport.missingHeaders.length === 0 ? 'bg-emerald-400' : 'bg-rose-400'}`}></div>
                                    <span className="text-xs text-slate-300 font-bold">
                                      {crnaValidationReport.missingHeaders && crnaValidationReport.missingHeaders.length === 0 ? 'All required columns found' : 'Missing required columns (Structure)'}
                                    </span>
                                  </div>
                                  <div className="flex items-center space-x-3">
                                    <div className="w-2 h-2 rounded-full bg-emerald-400"></div>
                                    <span className="text-xs text-slate-300 font-bold">All Dates are valid</span>
                                  </div>
                                  <div className="flex items-center space-x-3">
                                    <div className="w-2 h-2 rounded-full bg-emerald-400"></div>
                                    <span className="text-xs text-slate-300 font-bold">Durations are valid</span>
                                  </div>
                              </div>
                            </div>
                            
                            {crnaValidationReport.missingHeaders && crnaValidationReport.missingHeaders.length > 0 && (
                              <div className="px-6 pb-6 pt-2 border-t border-slate-900/50">
                                <span className="text-[10px] font-black text-rose-400 uppercase tracking-widest block mb-3">Missing Required Columns:</span>
                                <div className="flex flex-wrap gap-2">
                                  {crnaValidationReport.missingHeaders.map((header: string) => (
                                    <div key={header} className="bg-rose-500/10 px-3 py-1 rounded-full border border-rose-500/30">
                                        <span className="text-xs text-rose-300 font-bold">{header}</span>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}

                            {crnaValidationReport.skippedReasons && Object.keys(crnaValidationReport.skippedReasons).length > 0 && (
                              <div className="px-6 pb-6 pt-2 border-t border-slate-900/50">
                                <span className="text-[10px] font-black text-slate-500 uppercase tracking-widest block mb-3">Error Breakdown:</span>
                                <div className="space-y-2">
                                  {Object.entries(crnaValidationReport.skippedReasons).map(([reason, count]) => (
                                    <div key={reason} className="flex justify-between items-center bg-slate-900/50 px-3 py-2 rounded-lg border border-slate-800/50">
                                        <span className="text-xs text-slate-400 font-medium">{reason}</span>
                                        <span className="text-xs font-bold text-rose-300">{count as number}</span>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}
                          </>
                        )}
                      </div>
                    )}
                  </div>
                )}
             </div>
          )}
        </div>
        
        {hasData && (
          <div className="mt-6 p-4 bg-slate-800/50 border border-slate-700 rounded-xl flex items-center space-x-3">
            <AlertCircle size={20} className="text-blue-300" />
            <p className="text-xs text-white font-bold uppercase tracking-wide">
              Note: Data is processed entirely locally on your machine. No sensitive patient information is transmitted externally.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}