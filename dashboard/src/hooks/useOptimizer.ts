import { useState, useMemo, useEffect } from 'react';
import type { ProcessingConfig } from '../logic/siteDataProcessor';

declare global {
  interface Window {
    ipcRenderer: import('electron').IpcRenderer;
  }
}

export function useOptimizer() {
  const [rawCsv, setRawCsv] = useState<string | null>(null);
  const [csvPath, setCsvPath] = useState<string | null>(null);
  const [results, setResults] = useState<any[]>([]);
  const [javaSummary, setJavaSummary] = useState<any | null>(null);
  const [lastAnalyzedConfig, setLastAnalyzedConfig] = useState<{
    binCapacity: number;
    startPadMinutes: number;
    endPadMinutes: number;
  } | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [rowCount, setRowCount] = useState<number>(0);
  const [crnaRowCount, setCrnaRowCount] = useState<number>(0);
  const [fileName, setFileName] = useState<string | null>(null);
  const [validationReport, setValidationReport] = useState<any | null>(null);
  const [crnaValidationReport, setCrnaValidationReport] = useState<any | null>(null);
  const [isValidated, setIsValidated] = useState(false);
  const [isCleaning, setIsCleaning] = useState(false);
  const [isCleaned, setIsCleaned] = useState(false);
  const [cleanedPath, setCleanedPath] = useState<string | null>(null);
  const [crnaCsvPath, setCrnaCsvPath] = useState<string | null>(null);
  const [crnaFileName, setCrnaFileName] = useState<string | null>(null);
  const [config, setConfig] = useState<ProcessingConfig>({
    mapping: {
      'Case_ID': 'Case_ID',
      'Date': 'Date',
      'Site': 'Site',
      'OR_Room': 'OR_Room',
      'Surgeon_Name': 'Surgeon_Name',
      'Procedure_Type': 'Procedure_Type',
      'Anesthesia_Start_Time': 'Anesthesia_Start_Time',
      'Anesthesia_End_Time': 'Anesthesia_End_Time',
      'Patient_In_Room_Time': 'Patient_In_Room_Time',
      'Patient_Out_Room_Time': 'Patient_Out_Room_Time',
    },
    validator: {
      startPadMinutes: 10,
      endPadMinutes: 10,
      binCapacity: 480,
      maxProcedureDurationHours: 12,
      minProcedureDurationMinutes: 15,
    },
    financials: {
      costPerOrDay: 1500,
      includeAnesthesiologist: true,
      includeCRNA1: true,
      anesthesiologistRate: 3500,
      crnaRate: 1800,
      crnaHourRate: 175,
      anesBlocksSavedEnabled: true,
      anesFteEfficiencyEnabled: true,
      anesAbsorptionEnabled: true,
      anesPaddingMinutes: 0,
      crnaProductivityFactor: 0.85,
    },
    startTime: '08:00',
    skipSingleOr: true,
    dateFormat: 'M/d/yyyy',
    timeFormat: 'hh:mm a',
    reportDetailLevel: 'high',
    showSummaryGraph: true,
    showDetailGraph: true,
    optimizationTimeout: 2,
    csvPathOverride: null,
    reportPackagePath: 'src/main/resources/report/CFO_Report_Package.zip',
    engineName1: 'HEalLTHCaiRE.ai Quark (v1.7)',
    engineName2: 'Google OR-Tools (v9.8)',
  });

  // Load persistent config on mount
  useEffect(() => {
    if (window.ipcRenderer) {
      window.ipcRenderer.invoke('get-doc-path', 'cfo_verification_guide.html').then((path: string) => {
        (window as any)._auditGuidePath = path;
      });
      window.ipcRenderer.invoke('get-config').then((savedConfig: any) => {
        if (savedConfig) {
          setConfig(prev => ({
            ...prev,
            ...savedConfig,
            // Ensure we don't overwrite current session state like rawCsv or results
            mapping: savedConfig.mapping || prev.mapping,
            validator: savedConfig.validator || prev.validator,
            financials: {
              ...prev.financials,
              ...(savedConfig.financials || {}),
            },
            csvPathOverride: savedConfig.csvPathOverride || prev.csvPathOverride,
            reportPackagePath: savedConfig.reportPackagePath || prev.reportPackagePath
          }));
        }
      });
    }
  }, []);

  const runJavaAnalysis = async (newConfig: ProcessingConfig, pathOverride?: string) => {
    if (!window.ipcRenderer) {
      console.warn('window.ipcRenderer is NOT available');
      return false;
    }
    const targetPath = pathOverride || cleanedPath || csvPath;
    if (!targetPath) {
      alert("Please select and validate a surgical cases file before running analysis.");
      return false;
    }
    setIsAnalyzing(true);
    try {
      console.log('Invoking analyze-with-java with params:', {
        csvPath: targetPath,
        capacity: newConfig.validator.binCapacity,
        startPad: newConfig.validator.startPadMinutes,
        endPad: newConfig.validator.endPadMinutes,
      });
      const javaResults: any = await window.ipcRenderer.invoke('analyze-with-java', {
        csvPath: targetPath,
        crnaCsvPath: crnaCsvPath,
        capacity: newConfig.validator.binCapacity,
        startPad: newConfig.validator.startPadMinutes,
        endPad: newConfig.validator.endPadMinutes,
        mode: 'FULL'
      });
      
      console.log('Received javaResults:', javaResults);
      if (javaResults && javaResults.error) {
        alert(`Analysis Error: ${javaResults.error}\n\nCheck optimizer-diagnostic.log for details.`);
        setIsAnalyzing(false);
        return false;
      }

      if (javaResults && typeof javaResults === 'object' && javaResults.summary && Array.isArray(javaResults.results)) {
        try {
          const { summary, results: javaRows } = javaResults;
          
          // Map Java output to our SiteDayResult format
          const mappedResults = javaRows.map((dr: any, drIdx: number) => {
            try {
              return {
                site: dr.groupKey?.site || 'Unknown Site',
                date: Array.isArray(dr.groupKey?.date) 
                  ? `${dr.groupKey.date[0]}-${String(dr.groupKey.date[1]).padStart(2, '0')}-${String(dr.groupKey.date[2]).padStart(2, '0')}` 
                  : (dr.groupKey?.date || 'Unknown Date'),
                cases: (dr.validCases || []).concat(dr.invalidCases || []).map((c: any, cIdx: number) => {
                   try {
                     return {
                       ...c,
                       valid: c.valid,
                       durationMinutes: c.durationMinutes,
                       anesthesiaStart: Array.isArray(c.anesthesiaStart) ? `${String(c.anesthesiaStart[0]).padStart(2, '0')}:${String(c.anesthesiaStart[1]).padStart(2, '0')}` : c.anesthesiaStart,
                       anesthesiaEnd: Array.isArray(c.anesthesiaEnd) ? `${String(c.anesthesiaEnd[0]).padStart(2, '0')}:${String(c.anesthesiaEnd[1]).padStart(2, '0')}` : c.anesthesiaEnd,
                       occupancyStart: Array.isArray(c.occupancyStart) ? `${String(c.occupancyStart[0]).padStart(2, '0')}:${String(c.occupancyStart[1]).padStart(2, '0')}` : c.occupancyStart,
                       occupancyEnd: Array.isArray(c.occupancyEnd) ? `${String(c.occupancyEnd[0]).padStart(2, '0')}:${String(c.occupancyEnd[1]).padStart(2, '0')}` : c.occupancyEnd,
                       record: c.record ? {
                         ...c.record,
                         date: Array.isArray(c.record.date) ? new Date(c.record.date[0], c.record.date[1] - 1, c.record.date[2]) : c.record.date,
                         csvStart: Array.isArray(c.record.csvStart) ? `${String(c.record.csvStart[0]).padStart(2, '0')}:${String(c.record.csvStart[1]).padStart(2, '0')}` : c.record.csvStart,
                         csvEnd: Array.isArray(c.record.csvEnd) ? `${String(c.record.csvEnd[0]).padStart(2, '0')}:${String(c.record.csvEnd[1]).padStart(2, '0')}` : c.record.csvEnd,
                         patientIn: Array.isArray(c.record.patientIn) ? `${String(c.record.patientIn[0]).padStart(2, '0')}:${String(c.record.patientIn[1]).padStart(2, '0')}` : c.record.patientIn,
                         patientOut: Array.isArray(c.record.patientOut) ? `${String(c.record.patientOut[0]).padStart(2, '0')}:${String(c.record.patientOut[1]).padStart(2, '0')}` : c.record.patientOut,
                       } : null
                    };
                   } catch (err) {
                     console.error(`Error mapping case ${cIdx} in result ${drIdx}:`, err);
                     return c;
                   }
                }),
                actualRoomsUsed: dr.actualRoomsUsed,
                optimizedRoomsUsed: dr.optimizedRoomsUsed ?? dr.actualRoomsUsed,
                roomsSaved: dr.roomsSavedDay,
                status: dr.status,
                anesthesiaSavings: dr.anesthesiaSavings || { method1Savings: 0, method2Savings: 0, method3Savings: 0, totalAnesthesiaSavings: 0 },
                extraCrnaHours: dr.extraCrnaHours || 0
              };
            } catch (err) {
              console.error(`Error mapping result ${drIdx}:`, err);
              return null;
            }
          }).filter(Boolean);

          console.log('--- JAVA ANALYSIS SUMMARY ---');
          console.log('Total Actual Rooms:', summary.totalActualRooms);
          console.log('Total Saved (Java):', summary.totalRoomsSaved);
          console.log('Total Saved (Mapped):', mappedResults.reduce((acc: number, curr: any) => acc + (curr.roomsSaved || 0), 0));
          console.log('Overall Util:', summary.overallUtilization);
          console.log('-----------------------------');
          
          setResults(mappedResults);
          setJavaSummary(summary);
          setConfig(prev => ({
            ...prev,
            engineName1: summary.engineName1 || prev.engineName1,
            engineName2: summary.engineName2 || prev.engineName2
          }));
          setLastAnalyzedConfig({
            binCapacity: newConfig.validator.binCapacity,
            startPadMinutes: newConfig.validator.startPadMinutes,
            endPadMinutes: newConfig.validator.endPadMinutes,
          });
          return true;
        } catch (err) {
          console.error('Error during results mapping:', err);
          return false;
        }
      } else {
        console.error('javaResults structure invalid:', javaResults);
        return false;
      }
    } catch (err) {
      console.error('Unexpected error during Java analysis:', err);
      return false;
    } finally {
      setIsAnalyzing(false);
    }
  };

  const savePersistentConfig = async (newConfig: ProcessingConfig) => {
    if (window.ipcRenderer) {
      await window.ipcRenderer.invoke('save-config', newConfig);
    }
    setConfig(newConfig);
    
    // Re-process if we have data
    if (csvPath) {
      await runJavaAnalysis(newConfig);
    }
  };

  const processData = async (csvText: string, path?: string) => {
    console.log('processData called with path:', path);
    if (!csvText || csvText.trim().length === 0) {
      console.error('processData: csvText is empty');
      return;
    }

    if (path) {
      setResults([]); // Clear old results
      setCsvPath(path);
      setRawCsv(csvText); // Set rawCsv to indicate we have a file selected
      setIsValidated(false);
      setValidationReport(null);
      // We no longer clear CRNA state here to allow both files to be selected
      setIsCleaned(false);
      setCleanedPath(null);
      
      // Extract file name from path
      try {
        const parts = path.split(/[\\/]/);
        setFileName(parts[parts.length - 1]);
      } catch (err) {
        console.error('Error extracting filename:', err);
        setFileName('Unknown File');
      }
      
      // Count rows
      try {
        const lines = csvText.split('\n');
        const count = lines.filter(line => line.trim().length > 0).length - 1;
        setRowCount(Math.max(0, count));
      } catch (err) {
        console.error('Error counting rows:', err);
        setRowCount(0);
      }
    } else {
      console.warn('processData: No path provided');
      alert(`Error: File path could not be determined. Please try selecting the file again.`);
    }
  };

  const validateFile = async () => {
    // Determine which file(s) we should actually validate based on what has been selected
    // and whether we have fresh data for them.
    if ((!csvPath && !crnaCsvPath) || !window.ipcRenderer) return;

    setIsAnalyzing(true);
    try {
      console.log('validateFile invoking with paths:', { csvPath, crnaCsvPath });
      const javaResults: any = await window.ipcRenderer.invoke('analyze-with-java', {
        csvPath: csvPath || "", // Java handles empty/missing path
        crnaCsvPath: crnaCsvPath || "",
        mode: 'VALIDATE_ONLY'
      });
      console.log('validateFile received:', javaResults);
      if (javaResults && (javaResults.report || javaResults.crnaReport)) {
        console.log('Setting validation reports:', { 
          report: javaResults.report, 
          crnaReport: javaResults.crnaReport 
        });
        
        // Always set the report objects if they exist, even if isHeaderRowPresent is false.
        // The component handles displaying the "Invalid Format" message based on isHeaderRowPresent.
        if (javaResults.report) {
          setValidationReport(javaResults.report);
        } else {
          setValidationReport(null);
        }

        if (javaResults.crnaReport) {
          setCrnaValidationReport(javaResults.crnaReport);
        } else {
          setCrnaValidationReport(null);
        }
        setIsValidated(true);
      } else if (javaResults && javaResults.error) {
        alert(`Validation Error: ${javaResults.error}`);
      }
    } catch (err) {
      console.error('Error during validation:', err);
    } finally {
      setIsAnalyzing(false);
    }
  };

  const cleanFile = async () => {
    if (!csvPath || !window.ipcRenderer) return;
    setIsCleaning(true);
    try {
      const javaResults: any = await window.ipcRenderer.invoke('analyze-with-java', {
        csvPath: csvPath,
        mode: 'NORMALIZE_ONLY'
      });
      if (javaResults && javaResults.cleanedPath) {
        setCleanedPath(javaResults.cleanedPath);
        setIsCleaned(true);
        // Refresh validation report if it includes new stats
        if (javaResults.report) setValidationReport(javaResults.report);
      } else if (javaResults && javaResults.error) {
        alert(`Cleaning Error: ${javaResults.error}`);
      }
    } catch (err) {
      console.error('Error during cleaning:', err);
    } finally {
      setIsCleaning(false);
    }
  };

  const updateConfig = (newPartial: any) => {
    setConfig(prev => {
      const newConfig = {
        ...prev,
        validator: { ...prev.validator },
        financials: { ...prev.financials },
        mapping: { ...prev.mapping }
      };
      
      if (newPartial.binCapacity !== undefined) newConfig.validator.binCapacity = newPartial.binCapacity;
      if (newPartial.startPadMinutes !== undefined) newConfig.validator.startPadMinutes = newPartial.startPadMinutes;
      if (newPartial.endPadMinutes !== undefined) newConfig.validator.endPadMinutes = newPartial.endPadMinutes;
      if (newPartial.validator?.maxProcedureDurationHours !== undefined) newConfig.validator.maxProcedureDurationHours = newPartial.validator.maxProcedureDurationHours;
      if (newPartial.validator?.minProcedureDurationMinutes !== undefined) newConfig.validator.minProcedureDurationMinutes = newPartial.validator.minProcedureDurationMinutes;
      
      if (newPartial.costPerOrDay !== undefined) newConfig.financials.costPerOrDay = newPartial.costPerOrDay;
      if (newPartial.includeAnesthesiologist !== undefined) newConfig.financials.includeAnesthesiologist = newPartial.includeAnesthesiologist;
      if (newPartial.includeCRNA1 !== undefined) newConfig.financials.includeCRNA1 = newPartial.includeCRNA1;
      if (newPartial.anesthesiologistRate !== undefined) newConfig.financials.anesthesiologistRate = newPartial.anesthesiologistRate;
      if (newPartial.crnaRate !== undefined) newConfig.financials.crnaRate = newPartial.crnaRate;
      if (newPartial.anesBlocksSavedEnabled !== undefined) newConfig.financials.anesBlocksSavedEnabled = newPartial.anesBlocksSavedEnabled;
      if (newPartial.anesFteEfficiencyEnabled !== undefined) newConfig.financials.anesFteEfficiencyEnabled = newPartial.anesFteEfficiencyEnabled;
      if (newPartial.anesAbsorptionEnabled !== undefined) newConfig.financials.anesAbsorptionEnabled = newPartial.anesAbsorptionEnabled;
      if (newPartial.anesPaddingMinutes !== undefined) newConfig.financials.anesPaddingMinutes = newPartial.anesPaddingMinutes;
      if (newPartial.crnaProductivityFactor !== undefined) newConfig.financials.crnaProductivityFactor = newPartial.crnaProductivityFactor;

      if (newPartial.dateFormat !== undefined) newConfig.dateFormat = newPartial.dateFormat;
      if (newPartial.timeFormat !== undefined) newConfig.timeFormat = newPartial.timeFormat;
      if (newPartial.reportDetailLevel !== undefined) newConfig.reportDetailLevel = newPartial.reportDetailLevel;
      if (newPartial.showSummaryGraph !== undefined) newConfig.showSummaryGraph = newPartial.showSummaryGraph;
      if (newPartial.showDetailGraph !== undefined) newConfig.showDetailGraph = newPartial.showDetailGraph;
      if (newPartial.optimizationTimeout !== undefined) newConfig.optimizationTimeout = newPartial.optimizationTimeout;
      if (newPartial.skipSingleOr !== undefined) newConfig.skipSingleOr = newPartial.skipSingleOr;
      if (newPartial.mapping !== undefined) newConfig.mapping = { ...newConfig.mapping, ...newPartial.mapping };

      if (newPartial.csvPathOverride !== undefined) newConfig.csvPathOverride = newPartial.csvPathOverride;

      return newConfig;
    });
  };

  const triggerRecompute = async () => {
    if (csvPath) {
      await runJavaAnalysis(config);
    } else {
      console.warn('triggerRecompute: No csvPath available');
    }
  };

  const globalMetrics = useMemo(() => {
    if (javaSummary) {
      const roomsSaved = javaSummary.totalRoomsSaved;
      
      const method1 = javaSummary.anesthesiaSavings?.method1Savings || 0;
      const method2 = javaSummary.anesthesiaSavings?.method2Savings || 0;
      const method3 = javaSummary.anesthesiaSavings?.method3Savings || 0;
      
      // Calculate total anesthesia savings based on ENABLED methods
      let anesthesiaSavingsCount = 0;
      if (config.financials.anesBlocksSavedEnabled) anesthesiaSavingsCount += method1;
      if (config.financials.anesFteEfficiencyEnabled) anesthesiaSavingsCount += method2;
      if (config.financials.anesAbsorptionEnabled) anesthesiaSavingsCount += method3;
      
      const costPerOrDay = config.financials.costPerOrDay || 0;
      const anesRate = config.financials.anesthesiologistRate || 3500;
      const crnaRate = config.financials.crnaRate || 1800;
      const crnaHourRate = config.financials.crnaHourRate || 175;

      let anesthesiaStaffingSavingsPerAnes = anesRate + crnaRate;

      const crnaCostSaved = javaSummary.crnaCostSaved || 0;

      // Dynamic Annualization Logic
      let annualizationFactor = 12.0;
      let analyzedDays = 30;
      const startStr = javaSummary.startDate;
      const endStr = javaSummary.endDate;

      if (startStr && endStr) {
        const start = new Date(startStr);
        const end = new Date(endStr);
        analyzedDays = Math.ceil((end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24)) + 1;
        if (analyzedDays > 0) {
          annualizationFactor = 365.25 / analyzedDays;
        }
      }

      // Hard Savings: Labor
      let hardSavingsLabor = 0;
      // Always include Anesthesiologist and CRNA savings
      hardSavingsLabor += (anesthesiaSavingsCount * anesRate * annualizationFactor);
      hardSavingsLabor += (javaSummary.extraCrnaHours * crnaHourRate * annualizationFactor);

      // Soft Savings: Assets
      const softSavingsAssets = (roomsSaved * costPerOrDay * annualizationFactor);

      // Re-sum using the same rounding logic as App.tsx to avoid visual discrepancies
      const annualOpportunity = (
        Math.round(hardSavingsLabor / 1000000) * 1000000 +
        Math.round(softSavingsAssets / 1000000) * 1000000
      );

      // Raw opportunity for non-rounded displays
      const rawAnnualOpportunity = hardSavingsLabor + softSavingsAssets;

      // Calculate current utilization using Java's total duration and rooms
      const currentUtilization = javaSummary.currentUtilization ?? (javaSummary.totalActualRooms > 0
        ? (javaSummary.totalDurationMinutes / (javaSummary.totalActualRooms * config.validator.binCapacity)) * 100
        : 0);

      const optimizedUtilization = javaSummary.overallUtilization ?? (javaSummary.totalOptimizedRooms > 0
        ? (javaSummary.totalDurationMinutes / (javaSummary.totalOptimizedRooms * config.validator.binCapacity)) * 100
        : 0);

      return {
        totalActualRooms: javaSummary.totalActualRooms,
        totalOptimizedRooms: javaSummary.totalOptimizedRooms,
        roomsSaved: javaSummary.totalRoomsSaved,
        anesthesiaSavings: anesthesiaSavingsCount,
        totalAnesthesiologists: javaSummary.totalAnesthesiologists || 0,
        utilization: optimizedUtilization,
        currentUtilization: currentUtilization,
        estimatedSavings: rawAnnualOpportunity / annualizationFactor,
        annualOpportunity: annualOpportunity,
        annualizationFactor: annualizationFactor,
        analyzedDays: analyzedDays,
        startDate: javaSummary.startDate,
        endDate: javaSummary.endDate,
        anesthesiaStaffingSavingsPerAnes: anesthesiaStaffingSavingsPerAnes,
        bundledCostPerDay: costPerOrDay,
        averageLaborYield: javaSummary.averageLaborYield,
        totalCostOfVariance: javaSummary.totalCostOfVariance,
        primeTimeUtilization: javaSummary.primeTimeUtilization,
        fcots: javaSummary.fcots,
        averageTot: javaSummary.averageTot,
        contributionMargin: javaSummary.contributionMargin,
        totalCrnaHours: javaSummary.totalCrnaHours || 0,
        scheduledCrnaHours: javaSummary.totalCrnaHours || 0, // Show raw supply
        requiredCrnaHours: javaSummary.requiredCrnaHours || 0,
        extraCrnaHours: javaSummary.extraCrnaHours || 0,
        crnaCurrentUtil: javaSummary.crnaCurrentUtil || 0,
        crnaTargetUtil: javaSummary.crnaTargetUtil || 0,
        crnaCostSaved: javaSummary.crnaCostSaved || 0,
        avgActiveRooms: javaSummary.avgActiveRooms || 0,
        maxActiveRooms: javaSummary.maxActiveRooms || 0,
        requiredAnesDays: javaSummary.requiredAnesDays || 0,
        scheduledAnesDays: javaSummary.scheduledAnesDays || 0,
        regionsCount: javaSummary.regionsCount || 0,
      };
    }

    // Fallback if no javaSummary yet
    return {
      totalActualRooms: 0,
      totalOptimizedRooms: 0,
      roomsSaved: 0,
      anesthesiaSavings: 0,
      totalAnesthesiologists: 0,
      utilization: 0,
      currentUtilization: 0,
      estimatedSavings: 0,
      annualOpportunity: 0,
      bundledCostPerDay: 0,
      averageLaborYield: 0,
      totalCostOfVariance: 0,
      primeTimeUtilization: 0,
      fcots: 0,
      averageTot: 0,
      contributionMargin: 0,
      totalCrnaHours: 0,
      scheduledCrnaHours: 0,
      extraCrnaHours: 0,
      crnaCurrentUtil: 0,
      crnaTargetUtil: 0,
      crnaCostSaved: 0,
      avgActiveRooms: 0,
      maxActiveRooms: 0,
    };
  }, [javaSummary, config.validator.binCapacity, config.financials]);

  const [isDiagnosticRunning, setIsDiagnosticRunning] = useState(false);
  const [diagnosticResult, setDiagnosticResult] = useState<{ success: boolean; output: string; error: string | null } | null>(null);

  const runDiagnostics = async () => {
    if (!window.ipcRenderer) return;
    setIsDiagnosticRunning(true);
    setDiagnosticResult(null);
    try {
      const result: any = await window.ipcRenderer.invoke('run-diagnostics');
      setDiagnosticResult(result);
    } catch (err) {
      console.error('Error running diagnostics:', err);
      setDiagnosticResult({ success: false, output: '', error: String(err) });
    } finally {
      setIsDiagnosticRunning(false);
    }
  };

  const getReportPath = async () => {
    if (!window.ipcRenderer) return null;
    return await window.ipcRenderer.invoke('get-report-path');
  };

  const selectCrnaFile = async () => {
    if (!window.ipcRenderer) return;
    try {
      const result = await window.ipcRenderer.invoke('select-file');
      if (result) {
        setCrnaCsvPath(result.path);
        setCrnaFileName(result.name);
        // Extract row count
        if (result.content) {
          const lines = result.content.split('\n');
          const count = lines.filter((line: string) => line.trim().length > 0).length - 1;
          setCrnaRowCount(Math.max(0, count));
        } else {
          setCrnaRowCount(0);
        }
        // Set global for display
        (window as any)._selectedCrnaFilePath = result.path;
        // Reset validation when a new file is selected
        setIsValidated(false);
        setCrnaValidationReport(null);
        // We no longer clear OR file state here to allow both files to be selected
      }
    } catch (err) {
      console.error('Error selecting CRNA file:', err);
    }
  };

  return {
    results,
    globalMetrics,
    config,
    lastAnalyzedConfig,
    hasData: !!rawCsv,
    isAnalyzing,
    rowCount,
    fileName,
    crnaCsvPath,
    crnaFileName,
    validationReport,
    crnaValidationReport,
    isValidated,
    isCleaning,
    isCleaned,
    cleanedPath,
    crnaRowCount,
    isDiagnosticRunning,
    diagnosticResult,
    getReportPath,
    processData,
    validateFile,
    cleanFile,
    runJavaAnalysis,
    runDiagnostics,
    selectCrnaFile,
    updateConfig,
    savePersistentConfig,
    triggerRecompute
  };
}