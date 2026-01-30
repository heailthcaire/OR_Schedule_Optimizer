import { app, BrowserWindow, ipcMain, shell, dialog, protocol, net } from 'electron'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import fs from 'node:fs'
import { exec } from 'node:child_process'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const CONFIG_FILE = path.join(app.getPath('userData'), 'optimizer-config.json')

process.env.DIST = path.join(__dirname, '../dist')
process.env.VITE_PUBLIC = app.isPackaged ? process.env.DIST : path.join(process.env.DIST, '../public')

let win: BrowserWindow | null

// Helper to resolve config path
function getAppPropertiesPath(projectRoot: string) {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'engine', 'app.properties');
  }
  return path.resolve(projectRoot, 'src/main/resources/app.properties');
}

function parseProperties(filePath: string): Record<string, string> {
  try {
    if (!fs.existsSync(filePath)) {
      logToDiagnostic(`[Properties] File not found: ${filePath}`);
      return {};
    }
    const content = fs.readFileSync(filePath, 'utf-8');
    const lines = content.split(/\r?\n/);
    const props: Record<string, string> = {};
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) continue;
      const splitIndex = trimmed.indexOf('=');
      if (splitIndex === -1) continue;
      const key = trimmed.substring(0, splitIndex).trim();
      const value = trimmed.substring(splitIndex + 1).trim();
      props[key] = value;
    }
    return props;
  } catch (e) {
    logToDiagnostic(`[Properties] Error parsing ${filePath}: ${e}`);
    return {};
  }
}

function writeProperties(filePath: string, props: Record<string, string>) {
  let content = '';
  if (fs.existsSync(filePath)) {
    content = fs.readFileSync(filePath, 'utf-8');
  }
  const lines = content.split(/\r?\n/);
  const newLines: string[] = [];
  const handledKeys = new Set<string>();

  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('!')) {
      newLines.push(line);
      continue;
    }
    const splitIndex = trimmed.indexOf('=');
    if (splitIndex === -1) {
      newLines.push(line);
      continue;
    }
    const key = trimmed.substring(0, splitIndex).trim();
    if (props.hasOwnProperty(key)) {
      newLines.push(`${key} = ${props[key]}`);
      handledKeys.add(key);
    } else {
      newLines.push(line);
    }
  }

  // Add new keys
  for (const key in props) {
    if (!handledKeys.has(key)) {
      newLines.push(`${key} = ${props[key]}`);
    }
  }

  fs.writeFileSync(filePath, newLines.join('\n'));
}

function getProjectRoot() {
  let projectRoot = __dirname;
  while (projectRoot !== path.parse(projectRoot).root) {
    if (fs.existsSync(path.join(projectRoot, 'pom.xml'))) {
      return projectRoot;
    }
    projectRoot = path.dirname(projectRoot);
  }
  return path.resolve(__dirname, '../../');
}

// IPC Handlers for Configuration
ipcMain.handle('get-report-path', () => {
  const projectRoot = getProjectRoot();
  const configPath = getAppPropertiesPath(projectRoot);
  const props = parseProperties(configPath);
  let reportPath = props['report.path'];
  
  if (reportPath) {
    if (!path.isAbsolute(reportPath)) {
      reportPath = path.resolve(projectRoot, reportPath);
    }
  } else {
    // Default fallback
    reportPath = path.join(projectRoot, 'src/main/resources/report/report.html');
  }

  // Convert absolute path to a properly encoded safe-file:// URL
  // This bypasses Chromium's restriction on loading file:// inside non-file origins
  // We use safe-file:///path format.
  const url = pathToFileURL(reportPath).toString();
  return url.replace('file:', 'safe-file:');
});

ipcMain.handle('get-doc-path', (_, docName: string) => {
  const projectRoot = getProjectRoot();
  let docPath = '';
  
  if (app.isPackaged) {
    docPath = path.join(process.resourcesPath, 'engine', 'documentation', docName);
  } else {
    docPath = path.join(projectRoot, 'src/main/resources/documentation', docName);
  }

  if (!fs.existsSync(docPath)) {
    return null;
  }

  const url = pathToFileURL(docPath).toString();
  return url.replace('file:', 'safe-file:');
});

ipcMain.handle('get-config', () => {
  const projectRoot = getProjectRoot();
  const configPath = getAppPropertiesPath(projectRoot);
  const props = parseProperties(configPath);

  if (Object.keys(props).length === 0) {
    // Fallback to legacy JSON if properties are empty or missing
    if (fs.existsSync(CONFIG_FILE)) {
      try {
        return JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8'));
      } catch (e) {
        console.error('Failed to parse config file', e);
      }
    }
    return null;
  }

  // Map properties back to ProcessingConfig object
  const config: any = {
    mapping: {},
    validator: {},
    financials: {}
  };

  config.dateFormat = props['date.format'] || 'M/d/yyyy';
  config.timeFormat = props['time.format'] || 'hh:mm a';
  config.skipSingleOr = props['skip.single.or'] === 'true';
  config.mapping['Case_ID'] = props['id.column'] || 'Case_ID';
  config.mapping['Date'] = props['date.column'] || 'Date';
  config.mapping['Site'] = props['site.column'] || 'Site';
  config.mapping['OR_Room'] = props['or.column'] || 'OR_Room';
  config.mapping['Surgeon_Name'] = props['surgeon.column'] || 'Surgeon_Name';
  config.mapping['Procedure_Type'] = props['procedure.column'] || 'Procedure_Type';
  config.mapping['Anesthesia_Start_Time'] = props['anesthesia.start.time.column'] || 'Anesthesia_Start_Time';
  config.mapping['Anesthesia_End_Time'] = props['anesthesia.end.time.column'] || 'Anesthesia_End_Time';
  config.mapping['Patient_In_Room_Time'] = props['or.occupancy.start.time.column'] || 'Patient_In_Room_Time';
  config.mapping['Patient_Out_Room_Time'] = props['or.occupancy.end.time.column'] || 'Patient_Out_Room_Time';
  
  config.validator.binCapacity = parseInt(props['bin.capacity.minutes'] || '480');
  config.validator.startPadMinutes = parseInt(props['start.time.pad.minutes'] || '10');
  config.validator.endPadMinutes = parseInt(props['end.time.pad.minutes'] || '10');
  config.validator.maxProcedureDurationHours = parseInt(props['max.procedure.duration.hours'] || '12');
  config.validator.minProcedureDurationMinutes = parseInt(props['min.procedure.duration.minutes'] || '15');
  
  config.reportPackagePath = props['report.package.path'] || 'src/main/resources/report/CFO_Report_Package.zip';

  config.optimizationTimeout = parseInt(props['optimization.timeout.seconds'] || '2');
  config.reportDetailLevel = props['report.detail.level'] || 'high';
  config.showSummaryGraph = props['show.summary.graph'] !== 'false';
  config.showDetailGraph = props['show.detail.graph'] !== 'false';

  // Financials
  config.financials.costPerOrDay = parseFloat(props['cost.per.or.day'] || '1500');
  config.financials.includeAnesthesiologist = props['financials.include.anesthesiologist'] === 'true';
  config.financials.includeCRNA1 = props['financials.include.crna1'] === 'true';
  config.financials.anesthesiologistRate = parseFloat(props['financials.anesthesiologist.rate'] || '3500');
  config.financials.crnaRate = parseFloat(props['financials.crna.rate'] || '1800');
  config.financials.crnaProductivityFactor = parseFloat(props['crna.productivity.factor'] || '0.85');

  return config;
});

ipcMain.handle('save-config', (_, config) => {
  try {
    const projectRoot = getProjectRoot();
    const configPath = getAppPropertiesPath(projectRoot);
    
    const props: Record<string, string> = {
      'date.format': config.dateFormat,
      'time.format': config.timeFormat,
      'skip.single.or': String(config.skipSingleOr),
      'id.column': config.mapping['Case_ID'],
      'date.column': config.mapping['Date'],
      'site.column': config.mapping['Site'],
      'or.column': config.mapping['OR_Room'],
      'surgeon.column': config.mapping['Surgeon_Name'],
      'procedure.column': config.mapping['Procedure_Type'],
      'anesthesia.start.time.column': config.mapping['Anesthesia_Start_Time'],
      'anesthesia.end.time.column': config.mapping['Anesthesia_End_Time'],
      'or.occupancy.start.time.column': config.mapping['Patient_In_Room_Time'],
      'or.occupancy.end.time.column': config.mapping['Patient_Out_Room_Time'],
      'bin.capacity.minutes': String(config.validator.binCapacity),
      'start.time.pad.minutes': String(config.validator.startPadMinutes),
      'end.time.pad.minutes': String(config.validator.endPadMinutes),
      'max.procedure.duration.hours': String(config.validator.maxProcedureDurationHours),
      'min.procedure.duration.minutes': String(config.validator.minProcedureDurationMinutes),
      'report.package.path': config.reportPackagePath || 'src/main/resources/report/CFO_Report_Package.zip',
      'optimization.timeout.seconds': String(config.optimizationTimeout),
      'report.detail.level': config.reportDetailLevel,
      'show.summary.graph': String(config.showSummaryGraph),
      'show.detail.graph': String(config.showDetailGraph),
      // Financials
      'cost.per.or.day': String(config.financials.costPerOrDay),
      'financials.include.anesthesiologist': String(config.financials.includeAnesthesiologist),
      'financials.include.crna1': String(config.financials.includeCRNA1),
      'financials.anesthesiologist.rate': String(config.financials.anesthesiologistRate),
      'financials.crna.rate': String(config.financials.crnaRate),
      'crna.productivity.factor': String(config.financials.crnaProductivityFactor),
    };

    writeProperties(configPath, props);
    
    // Also save to JSON for backup/legacy compatibility
    fs.writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2));
    
    return true;
  } catch (e) {
    console.error('Failed to save config file', e);
    return false;
  }
});

ipcMain.handle('select-file', async () => {
  if (!win) return null
  logToDiagnostic('IPC select-file invoked');
  const result = await dialog.showOpenDialog(win, {
    properties: ['openFile'],
    filters: [
      { name: 'CSV Files', extensions: ['csv'] }
    ]
  })
  
  if (result.canceled || result.filePaths.length === 0) {
    logToDiagnostic('File selection canceled');
    return null
  }
  
  const filePath = result.filePaths[0]
  logToDiagnostic(`File selected: ${filePath}`);
  const stats = fs.statSync(filePath)
  const content = fs.readFileSync(filePath, 'utf-8')
  
  return {
    path: filePath,
    name: path.basename(filePath),
    size: stats.size,
    content: content
  }
})

ipcMain.on('run-java-optimization', (_, params: { csvPath?: string, capacity?: number, startPad?: number, endPad?: number }) => {
  runJavaProcess(false, params).then(reportPath => {
    if (reportPath && fs.existsSync(reportPath)) {
      shell.openPath(reportPath)
    }
  })
})

ipcMain.handle('analyze-with-java', async (_, params: { csvPath: string, capacity?: number, startPad?: number, endPad?: number, mode?: string }) => {
  logToDiagnostic(`IPC analyze-with-java invoked. Mode: ${params.mode}, CSV: ${params.csvPath}`);
  return await runJavaProcess(true, params)
})

ipcMain.handle('run-diagnostics', async () => {
  return await runJavaProcess(true, { mode: 'DIAGNOSTIC', csvPath: '' });
});

ipcMain.handle('create-cfo-report', async (_, params: { csvPath: string, capacity: number, startPad: number, endPad: number }) => {
  logToDiagnostic(`IPC create-cfo-report invoked. CSV: ${params.csvPath}`);
  console.log(`[DEBUG] IPC create-cfo-report invoked. CSV: ${params.csvPath}`);
  return await runJavaProcess(true, { ...params, mode: 'CREATE_CFO_REPORT' });
});

function logToDiagnostic(msg: string) {
  const projectRoot = getProjectRoot();
  const logFile = app.isPackaged 
    ? path.join(app.getPath('userData'), 'optimizer-diagnostic.log')
    : path.join(projectRoot, 'optimizer-diagnostic.log');
  const timestamp = new Date().toISOString();
  fs.appendFileSync(logFile, `[${timestamp}] ${msg}\n`);
  console.log(msg);
}

async function runJavaProcess(jsonMode = false, params?: { csvPath?: string, capacity?: number, startPad?: number, endPad?: number, mode?: string }) {
  const isPackaged = app.isPackaged;
  const projectRoot = getProjectRoot();
  const configPath = getAppPropertiesPath(projectRoot);
  let jarPath = '';
  let command = '';

  if (isPackaged) {
    // In packaged app, resources are in Contents/Resources (macOS) or resources/ (Win/Linux)
    const resourcesPath = process.resourcesPath;
    jarPath = path.join(resourcesPath, 'engine', 'optimizer-engine.jar');
    command = `java -jar "${jarPath}"`;
  } else {
    command = `mvn -B compile exec:java -Dexec.mainClass="org.heailth.Main"`;
  }

  logToDiagnostic(`--- Starting Java Process (jsonMode=${jsonMode}, packaged=${isPackaged}) ---`);
  logToDiagnostic(`CSV Path: ${params?.csvPath}`);
  logToDiagnostic(`Processing Mode: ${params?.mode}`);
  logToDiagnostic(`Capacity: ${params?.capacity}`);
  
  const env = { ...process.env };
  if (process.platform === 'darwin') {
    env.PATH = `${env.PATH}:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin`;
  }

  if (params?.csvPath !== undefined) {
    let absolutePath = params.csvPath;
    if (absolutePath && !path.isAbsolute(absolutePath)) {
      absolutePath = path.resolve(isPackaged ? app.getPath('desktop') : projectRoot, absolutePath);
    }
    // Ensure backslashes are handled if on Windows, though we are on Mac
    env.OPTIMIZER_CSV_PATH = absolutePath;
    logToDiagnostic(`Resolved OPTIMIZER_CSV_PATH: ${env.OPTIMIZER_CSV_PATH}`);
  }
  if ((params as any)?.crnaCsvPath !== undefined) {
    let crnaAbsolutePath = (params as any).crnaCsvPath;
    if (crnaAbsolutePath && !path.isAbsolute(crnaAbsolutePath)) {
      crnaAbsolutePath = path.resolve(isPackaged ? app.getPath('desktop') : projectRoot, crnaAbsolutePath);
    }
    env.OPTIMIZER_CRNA_CSV_PATH = crnaAbsolutePath || "";
    logToDiagnostic(`CRNA CSV Path: ${env.OPTIMIZER_CRNA_CSV_PATH}`);
  }
  if (params?.capacity !== undefined) env.OPTIMIZER_CAPACITY = String(params.capacity);
  if (params?.startPad !== undefined) env.OPTIMIZER_START_PAD = String(params.startPad);
  if (params?.endPad !== undefined) env.OPTIMIZER_END_PAD = String(params.endPad);
  if (params?.mode) env.OPTIMIZER_PROCESSING_MODE = params.mode;
  if (jsonMode) env.OPTIMIZER_JSON_OUTPUT = 'true';

  // Mapping and other settings from the persistent config if available
  const savedConfig = fs.existsSync(CONFIG_FILE) ? JSON.parse(fs.readFileSync(CONFIG_FILE, 'utf-8')) : null;
  if (savedConfig) {
    if (savedConfig.dateFormat) env.OPTIMIZER_DATE_FORMAT = savedConfig.dateFormat;
    if (savedConfig.timeFormat) env.OPTIMIZER_TIME_FORMAT = savedConfig.timeFormat;
    if (savedConfig.reportDetailLevel) env.OPTIMIZER_REPORT_DETAIL = savedConfig.reportDetailLevel;
    if (savedConfig.showSummaryGraph !== undefined) env.OPTIMIZER_SHOW_SUMMARY_GRAPH = String(savedConfig.showSummaryGraph);
    if (savedConfig.showDetailGraph !== undefined) env.OPTIMIZER_SHOW_DETAIL_GRAPH = String(savedConfig.showDetailGraph);
    if (savedConfig.optimizationTimeout !== undefined) env.OPTIMIZER_TIMEOUT = String(savedConfig.optimizationTimeout);
    if (savedConfig.reportPackagePath) env.OPTIMIZER_REPORT_PACKAGE_PATH = savedConfig.reportPackagePath;
    if (savedConfig.validator?.maxProcedureDurationHours !== undefined) env.OPTIMIZER_MAX_DURATION = String(savedConfig.validator.maxProcedureDurationHours);
    if (savedConfig.skipSingleOr !== undefined) env.OPTIMIZER_SKIP_SINGLE = String(savedConfig.skipSingleOr);

    if (savedConfig.engineName1) env.OPTIMIZER_ENGINE_NAME1 = savedConfig.engineName1;
    if (savedConfig.engineName2) env.OPTIMIZER_ENGINE_NAME2 = savedConfig.engineName2;

    // Financials
    if (savedConfig.financials) {
      if (savedConfig.financials.includeAnesthesiologist !== undefined) env.OPTIMIZER_INC_ANES = String(savedConfig.financials.includeAnesthesiologist);
      if (savedConfig.financials.includeCRNA1 !== undefined) env.OPTIMIZER_INC_CRNA1 = String(savedConfig.financials.includeCRNA1);
      if (savedConfig.financials.anesthesiologistRate !== undefined) env.OPTIMIZER_ANES_RATE = String(savedConfig.financials.anesthesiologistRate);
      if (savedConfig.financials.crnaRate !== undefined) env.OPTIMIZER_CRNA_RATE = String(savedConfig.financials.crnaRate);
      if (savedConfig.financials.crnaHourRate !== undefined) env.OPTIMIZER_CRNA_HOUR_RATE = String(savedConfig.financials.crnaHourRate);
      if (savedConfig.financials.anesBlocksSavedEnabled !== undefined) env.OPTIMIZER_ANES_BLOCKS_SAVED = String(savedConfig.financials.anesBlocksSavedEnabled);
      if (savedConfig.financials.anesFteEfficiencyEnabled !== undefined) env.OPTIMIZER_ANES_FTE_EFFICIENCY = String(savedConfig.financials.anesFteEfficiencyEnabled);
      if (savedConfig.financials.anesAbsorptionEnabled !== undefined) env.OPTIMIZER_ANES_ABSORPTION = String(savedConfig.financials.anesAbsorptionEnabled);
      if (savedConfig.financials.anesPaddingMinutes !== undefined) env.OPTIMIZER_ANES_PADDING = String(savedConfig.financials.anesPaddingMinutes);
      if (savedConfig.financials.crnaProductivityFactor !== undefined) env.OPTIMIZER_CRNA_PRODUCTIVITY = String(savedConfig.financials.crnaProductivityFactor);
    }

    // Mappings
    if (savedConfig.mapping) {
      if (savedConfig.mapping['Case_ID']) env.OPTIMIZER_COL_ID = savedConfig.mapping['Case_ID'];
      if (savedConfig.mapping['Date']) env.OPTIMIZER_COL_DATE = savedConfig.mapping['Date'];
      if (savedConfig.mapping['Site']) env.OPTIMIZER_COL_SITE = savedConfig.mapping['Site'];
      if (savedConfig.mapping['OR_Room']) env.OPTIMIZER_COL_OR = savedConfig.mapping['OR_Room'];
      if (savedConfig.mapping['Surgeon_Name']) env.OPTIMIZER_COL_SURGEON = savedConfig.mapping['Surgeon_Name'];
      if (savedConfig.mapping['Procedure_Type']) env.OPTIMIZER_COL_PROCEDURE = savedConfig.mapping['Procedure_Type'];
      if (savedConfig.mapping['Anesthesia_Start_Time']) env.OPTIMIZER_COL_START = savedConfig.mapping['Anesthesia_Start_Time'];
      if (savedConfig.mapping['Anesthesia_End_Time']) env.OPTIMIZER_COL_END = savedConfig.mapping['Anesthesia_End_Time'];
      if (savedConfig.mapping['Patient_In_Room_Time']) env.OPTIMIZER_COL_PATIENT_IN = savedConfig.mapping['Patient_In_Room_Time'];
      if (savedConfig.mapping['Patient_Out_Room_Time']) env.OPTIMIZER_COL_PATIENT_OUT = savedConfig.mapping['Patient_Out_Room_Time'];
    }
  }
  
  env.OPTIMIZER_CONFIG_PATH = configPath;

  // Diagnostic
  if (!isPackaged) {
    exec('mvn -version && java -version', { env }, (err, stdout, stderr) => {
      logToDiagnostic(`Environment check:\n${stdout}\n${stderr}`);
    });
  } else {
    exec('java -version', { env }, (err, stdout, stderr) => {
      logToDiagnostic(`JRE Check:\n${stdout}\n${stderr}`);
    });
  }

  logToDiagnostic(`Executing command: ${command}`);
  console.log(`[DEBUG] Executing command: ${command}`);
  logToDiagnostic(`Project/Resource Root: ${projectRoot}`);
  console.log(`[DEBUG] Project/Resource Root: ${projectRoot}`);
  logToDiagnostic(`Config Path: ${configPath}`);
  console.log(`[DEBUG] Config Path: ${configPath}`);
  const envDebug = {
    OPTIMIZER_CSV_PATH: env.OPTIMIZER_CSV_PATH,
    OPTIMIZER_CRNA_CSV_PATH: env.OPTIMIZER_CRNA_CSV_PATH,
    OPTIMIZER_PROCESSING_MODE: env.OPTIMIZER_PROCESSING_MODE,
    OPTIMIZER_CONFIG_PATH: env.OPTIMIZER_CONFIG_PATH
  };
  logToDiagnostic(`Full Env for Java: ${JSON.stringify(envDebug)}`);
  console.log(`[DEBUG] Full Env for Java: ${JSON.stringify(envDebug)}`);
  
  if (isPackaged && !fs.existsSync(jarPath)) {
    logToDiagnostic(`ERROR: JAR not found at ${jarPath}`);
  }

  return new Promise((resolve) => {
    exec(command, { cwd: isPackaged ? app.getPath('userData') : projectRoot, maxBuffer: 1024 * 1024 * 50, env }, (error, stdout, stderr) => {
      logToDiagnostic(`Java process finished. Stdout length: ${stdout.length}, Stderr length: ${stderr.length}`);
      console.log(`[DEBUG] Java process finished. Stdout length: ${stdout.length}, Stderr length: ${stderr.length}`);
      if (stderr.length > 0) {
        logToDiagnostic(`Java STDERR: ${stderr.substring(0, 2000)}${stderr.length > 2000 ? '...' : ''}`);
        console.log(`[DEBUG] Java STDERR snippet: ${stderr.substring(0, 500)}`);
      }
      if (error) {
        logToDiagnostic(`Error executing Java: ${error}`);
        logToDiagnostic(`Exit code: ${error.code}`);
        logToDiagnostic(`Signal: ${error.signal}`);
        logToDiagnostic(`STDOUT: ${stdout}`);
        logToDiagnostic(`Stderr: ${stderr}`);
        resolve({ error: error.message, stderr, stdout });
        return;
      }

      if (jsonMode) {
        try {
          // Find the start and end markers in stdout
          const startMarker = "JSON_START";
          const endMarker = "JSON_END";
          const jsonStart = stdout.indexOf(startMarker);
          const jsonEnd = stdout.indexOf(endMarker);
          
          if (jsonStart === -1 || jsonEnd === -1) {
            logToDiagnostic('Could not find JSON markers in Java output');
            logToDiagnostic(`STDOUT (first 500 chars): ${stdout.substring(0, 500)}`);
            logToDiagnostic(`STDERR: ${stderr}`);
            resolve({ error: 'JSON markers not found', stderr });
            return;
          }
          
          const finalJsonStr = stdout.substring(jsonStart + startMarker.length, jsonEnd).trim();
          logToDiagnostic(`JSON extracted, length: ${finalJsonStr.length}`);
          logToDiagnostic(`Raw JSON content: ${finalJsonStr.substring(0, 1000)}${finalJsonStr.length > 1000 ? '...' : ''}`);
          
          const parsed = JSON.parse(finalJsonStr);
          logToDiagnostic(`JSON parsed, type: ${Array.isArray(parsed) ? 'array' : typeof parsed}`);
          resolve(parsed);
        } catch (e) {
          logToDiagnostic(`Failed to parse Java JSON output: ${e}`);
          resolve({ error: `Parse error: ${e}`, stderr });
        }
      } else {
        const reportPath = path.join(projectRoot, 'src/main/resources/report/report.html')
        logToDiagnostic(`Optimization ran, report: ${reportPath}`);
        resolve(reportPath)
      }
    })
  })
}

function createWindow() {
  win = new BrowserWindow({
    width: 1320,
    height: 960,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      webSecurity: false,
    },
    titleBarStyle: 'hidden',
    titleBarOverlay: {
      color: '#0f172a',
      symbolColor: '#f8fafc',
    },
  })

  if (process.env.VITE_DEV_SERVER_URL) {
    console.log('Loading URL:', process.env.VITE_DEV_SERVER_URL)
    win.loadURL(process.env.VITE_DEV_SERVER_URL)
    // win.webContents.openDevTools()
    
    // Refresh if the first load fails or is empty (sometimes Vite takes a second)
    win.webContents.on('did-finish-load', () => {
      const currentUrl = win?.webContents.getURL()
      logToDiagnostic(`[BrowserWindow] did-finish-load: ${currentUrl}`)
      if (currentUrl === 'about:blank') {
        logToDiagnostic('[BrowserWindow] about:blank detected, retrying loadURL...')
        win?.loadURL(process.env.VITE_DEV_SERVER_URL!)
      }
    })
  } else {
    console.log('Loading File:', path.join(process.env.DIST, 'index.html'))
    win.loadFile(path.join(process.env.DIST, 'index.html'))
  }
  
  win.webContents.on('did-fail-load', (event, errorCode, errorDescription, validatedURL) => {
    console.error('Failed to load:', errorCode, errorDescription, validatedURL)
    logToDiagnostic(`[BrowserWindow] Failed to load ${validatedURL}: ${errorCode} (${errorDescription})`)
  })
}

// Register custom protocol to handle local files
// Must be called before app.whenReady()
protocol.registerSchemesAsPrivileged([
  { 
    scheme: 'safe-file', 
    privileges: { 
      standard: true, 
      secure: true, 
      supportFetchAPI: true, 
      corsEnabled: true,
      allowServiceWorkers: true,
      bypassCSP: true 
    } 
  }
])

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit()
    win = null
  }
})

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    createWindow()
  }
})

app.whenReady().then(() => {
  // Register custom protocol to handle local files
  // Usage: safe-file:///absolute/path/to/file
  protocol.handle('safe-file', async (request) => {
    const urlStr = request.url;
    try {
      // urlStr will be something like safe-file:///Users/...
      // On macOS (darwin), fileURLToPath requires an empty or "localhost" host for file:// URLs.
      // If the URL is safe-file:///Users/..., replacing 'safe-file:' with 'file:' results in file:///Users/...
      // which has an empty host.
      
      const fileUrlStr = urlStr.replace('safe-file:', 'file:');
      let decodedPath: string;
      
      try {
        const url = new URL(fileUrlStr);
        // On macOS, fileURLToPath often fails if there are two slashes (host is not empty)
        // or other host-related issues. Standard local paths should be file:///path (3 slashes)
        // If we get file://users/..., 'users' becomes the host.
        
        // Remove any query parameters (like cache busters) before path resolution
        const pathname = url.pathname;
        
        if (process.platform === 'darwin' && url.host && url.host !== 'localhost') {
          // Reconstruct path from host + pathname
          decodedPath = decodeURIComponent('/' + url.host + pathname);
          logToDiagnostic(`[safe-file] Darwin host-path recovery: ${decodedPath}`);
        } else {
          // Use only the origin and pathname to avoid issues with query strings
          decodedPath = fileURLToPath(new URL(url.origin + pathname).toString());
        }
      } catch (pathErr) {
        logToDiagnostic(`[safe-file] URL parsing failed for ${fileUrlStr}: ${pathErr}`);
        // Last resort manual decode
        const pathPart = urlStr.split('://')[1] || '';
        decodedPath = decodeURIComponent(pathPart.startsWith('/') ? pathPart : '/' + pathPart);
        logToDiagnostic(`[safe-file] Last resort manual decode: ${decodedPath}`);
      }

      logToDiagnostic(`[safe-file] Request URL: ${urlStr}`);
      logToDiagnostic(`[safe-file] Resolved Path: ${decodedPath}`);
      
      if (!fs.existsSync(decodedPath)) {
        logToDiagnostic(`[safe-file] File NOT FOUND at: ${decodedPath}`);
        return new Response(`File Not Found: ${decodedPath}`, { status: 404 });
      }

      const stats = fs.statSync(decodedPath);
      if (stats.isDirectory()) {
        logToDiagnostic(`[safe-file] Path is a directory: ${decodedPath}`);
        return new Response('Directory Access Not Allowed', { status: 403 });
      }

      const content = fs.readFileSync(decodedPath);
      const extension = path.extname(decodedPath).toLowerCase();
      const contentType = extension === '.html' ? 'text/html' : 
                        extension === '.css' ? 'text/css' :
                        extension === '.js' ? 'text/javascript' :
                        'application/octet-stream';
      
      logToDiagnostic(`[safe-file] Serving via fs.readFileSync: ${decodedPath} (${contentType}, ${content.length} bytes)`);

      return new Response(content, {
        headers: { 
          'Content-Type': contentType,
          'Access-Control-Allow-Origin': '*',
          'Cache-Control': 'no-store, no-cache, must-revalidate, proxy-revalidate',
          'Pragma': 'no-cache',
          'Expires': '0',
          'Content-Security-Policy': "default-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; img-src 'self' data:; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com;"
        }
      });
    } catch (e) {
      logToDiagnostic(`[safe-file] Handler Error: ${e}`);
      return new Response(`Internal Error: ${e}`, { status: 500 });
    }
  });

  createWindow();
})