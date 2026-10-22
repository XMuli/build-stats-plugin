package io.jenkins.plugins.buildvalue.logic;

import com.sun.management.OperatingSystemMXBean;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** One-second snapshot of this computer, in the same spirit as Task Manager. */
public final class MachineMetrics {
    private static final AtomicReference<String> JSON = new AtomicReference<>("{\"ok\":false}");
    private static volatile boolean started;
    private static volatile int paceMs = 1000;
    private static volatile Double cpu;
    private static volatile String cpuName = "";
    private static volatile int cpuCores = -1;
    private static volatile int cpuLogical = -1;
    private static volatile int cpuMhz = -1;
    private static volatile String coreVals = "";
    private static volatile String gpusRaw = "";
    private static volatile long memoryUsed = -1;
    private static volatile long memoryTotal = -1;
    private static volatile Double diskActive;
    private static volatile Double netDown;
    private static volatile Double netUp;
    private static volatile Double diskRead;
    private static volatile Double diskWrite;
    private static volatile Double gpu;
    private static volatile String gpuName = "";
    private static volatile long gpuMemoryUsed = -1;
    private static volatile long gpuMemoryTotal = -1;
    private static volatile String disksJson = "[]";

    private MachineMetrics() {}

    public static void start() {
        synchronized (MachineMetrics.class) {
            if (started) {
                return;
            }
            started = true;
        }
        Thread fast = new Thread(MachineMetrics::fastLoop, "build-value-machine");
        fast.setDaemon(true);
        fast.start();
        Thread slow = new Thread(MachineMetrics::slowLoop, "build-value-machine-gpu");
        slow.setDaemon(true);
        slow.start();
    }

    public static void pace(String raw) {
        if ("1".equals(raw)) {
            paceMs = 1000;
        } else if ("5".equals(raw)) {
            paceMs = 5000;
        }
    }

    public static String json() {
        start();
        return JSON.get();
    }

    private static void fastLoop() {
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                double load = os.getCpuLoad();
                if (load >= 0) {
                    cpu = load * 100.0;
                }
                long total = os.getTotalMemorySize();
                long free = os.getFreeMemorySize();
                if (total > 0) {
                    memoryTotal = total;
                    memoryUsed = Math.max(0, total - free);
                }
                disksJson = readDisks();
                JSON.set(build());
            } catch (Throwable ex) {
                JSON.set("{\"ok\":false,\"error\":" + quote(ex.toString()) + "}");
            }
            sleep(paceMs);
        }
    }

    private static void slowLoop() {
        String script = """
                $ErrorActionPreference='SilentlyContinue'
                $smiLines = @(& nvidia-smi --query-gpu=name,utilization.gpu,memory.used,memory.total --format=csv,noheader,nounits 2>$null)
                $smi = $smiLines | Select-Object -First 1
                $idleSample = (Get-Counter '\\PhysicalDisk(_Total)\\% Idle Time').CounterSamples | Select-Object -First 1
                $active = ''
                if ($idleSample) {
                  $active = [math]::Round([math]::Min(100,[math]::Max(0,100-$idleSample.CookedValue)),1)
                }
                $gpu = ''
                $gname = ''
                $gused = -1
                $gtotal = -1
                if ($smi) {
                  $p = $smi -split ','
                  if ($p.Length -ge 4) {
                    $gname = $p[0].Trim()
                    $gpu = [math]::Round([double]$p[1].Trim(),1)
                    $gused = [int64]$p[2].Trim() * 1MB
                    $gtotal = [int64]$p[3].Trim() * 1MB
                  }
                } else {
                  $samples = (Get-Counter '\\GPU Engine(*)\\Utilization Percentage').CounterSamples
                  $max = 0.0
                  $any = $false
                  foreach ($s in $samples) { $any = $true; if ($s.CookedValue -gt $max) { $max = $s.CookedValue } }
                  if ($any) { $gpu = [math]::Round([math]::Min(100,$max),1) }
                  $ctrl = Get-CimInstance Win32_VideoController | Select-Object -First 1
                  if ($ctrl) { $gname = [string]$ctrl.Name }
                  $ms = (Get-Counter '\\GPU Adapter Memory(*)\\Dedicated Usage').CounterSamples
                  $sum = 0.0
                  foreach ($s in $ms) { $sum += $s.CookedValue }
                  if ($sum -gt 0) { $gused = [int64]$sum }
                }
                $proc = Get-CimInstance Win32_Processor | Select-Object -First 1
                if ($proc) {
                  'cpuName=' + [string]$proc.Name
                  'cpuCores=' + $proc.NumberOfCores
                  'cpuLogical=' + $proc.NumberOfLogicalProcessors
                  'cpuMhz=' + $proc.MaxClockSpeed
                }
                $coreList = @()
                $coreCounter = Get-Counter '\\Processor(*)\\% Processor Time' -ErrorAction SilentlyContinue
                if ($coreCounter) {
                  foreach ($s in $coreCounter.CounterSamples) {
                    if ($s.InstanceName -ne '_Total') { $coreList += [math]::Round($s.CookedValue,1) }
                  }
                }
                'coreVals=' + ($coreList -join ',')
                'active=' + $active
                'gpu=' + $gpu
                'name=' + $gname
                'used=' + $gused
                'total=' + $gtotal
                $down = 0.0
                $up = 0.0
                $recv = Get-Counter '\\Network Interface(*)\\Bytes Received/sec' -ErrorAction SilentlyContinue
                if ($recv) {
                  foreach ($s in $recv.CounterSamples) {
                    if ($s.InstanceName -notmatch 'Loopback|isatap|Teredo') { $down += $s.CookedValue }
                  }
                }
                $sent = Get-Counter '\\Network Interface(*)\\Bytes Sent/sec' -ErrorAction SilentlyContinue
                if ($sent) {
                  foreach ($s in $sent.CounterSamples) {
                    if ($s.InstanceName -notmatch 'Loopback|isatap|Teredo') { $up += $s.CookedValue }
                  }
                }
                $readSample = Get-Counter '\\PhysicalDisk(_Total)\\Disk Read Bytes/sec' -ErrorAction SilentlyContinue
                $writeSample = Get-Counter '\\PhysicalDisk(_Total)\\Disk Write Bytes/sec' -ErrorAction SilentlyContinue
                'down=' + [int64]$down
                'up=' + [int64]$up
                'read=' + $(if ($readSample) { [int64]$readSample.CounterSamples[0].CookedValue } else { '' })
                'write=' + $(if ($writeSample) { [int64]$writeSample.CounterSamples[0].CookedValue } else { '' })
                $gpuParts = @()
                foreach ($line in $smiLines) {
                  if (-not $line) { continue }
                  $gp = $line -split ','
                  if ($gp.Length -ge 4) {
                    $gpuParts += ($gp[0].Trim() + '|' + [math]::Round([double]$gp[1].Trim(),1) + '|' + ([int64]$gp[2].Trim() * 1MB) + '|' + ([int64]$gp[3].Trim() * 1MB))
                  }
                }
                if ($gpuParts.Count -gt 0) {
                  $seen = ($gpuParts -join ';;')
                  foreach ($adapter in @(Get-CimInstance Win32_VideoController)) {
                    $an = [string]$adapter.Name
                    if (-not $an -or $an -match 'Microsoft|Remote|Basic Display|NVIDIA') { continue }
                    if ($seen -notlike ('*' + $an + '*')) { $gpuParts += ($an + '|0|-1|-1') }
                  }
                } else {
                  $i = 0
                  $byPhys = @{}
                  $eng = Get-Counter '\\GPU Engine(*)\\Utilization Percentage' -ErrorAction SilentlyContinue
                  if ($eng) {
                    foreach ($s in $eng.CounterSamples) {
                      if ($s.InstanceName -match 'phys_(\\d+)') {
                        $phys = $Matches[1]
                        if (-not $byPhys.ContainsKey($phys) -or $s.CookedValue -gt $byPhys[$phys]) { $byPhys[$phys] = $s.CookedValue }
                      }
                    }
                  }
                  foreach ($adapter in @(Get-CimInstance Win32_VideoController)) {
                    $an = [string]$adapter.Name
                    if (-not $an -or $an -match 'Microsoft|Remote|Basic Display') { continue }
                    $util = 0
                    if ($byPhys.ContainsKey("$i")) { $util = [math]::Round([double]$byPhys["$i"],1) }
                    $gpuParts += ($an + '|' + $util + '|-1|-1')
                    $i++
                  }
                }
                'gpus=' + ($gpuParts -join ';;')
                """;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                readCounters(script);
            } catch (Exception ignored) {
                // keep the last good sample
            }
            sleep(paceMs);
        }
    }

    private static void readCounters(String script) throws Exception {
        Process process = new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true)
                .start();
        String active = "";
        String gpuText = "";
        String name = "";
        String used = "";
        String total = "";
        String down = "";
        String up = "";
        String read = "";
        String write = "";
        String cpuNameLine = "";
        String cpuCoresLine = "";
        String cpuLogicalLine = "";
        String cpuMhzLine = "";
        String coreLine = "";
        String gpusLine = "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int cut = line.indexOf('=');
                if (cut < 0) {
                    continue;
                }
                String key = line.substring(0, cut).trim();
                String value = line.substring(cut + 1).trim();
                switch (key) {
                    case "active" -> active = value;
                    case "gpu" -> gpuText = value;
                    case "name" -> name = value;
                    case "used" -> used = value;
                    case "total" -> total = value;
                    case "down" -> down = value;
                    case "up" -> up = value;
                    case "read" -> read = value;
                    case "write" -> write = value;
                    case "cpuName" -> cpuNameLine = value;
                    case "cpuCores" -> cpuCoresLine = value;
                    case "cpuLogical" -> cpuLogicalLine = value;
                    case "cpuMhz" -> cpuMhzLine = value;
                    case "coreVals" -> coreLine = value;
                    case "gpus" -> gpusLine = value;
                    default -> {
                    }
                }
            }
        }
        if (!process.waitFor(12, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return;
        }
        diskActive = parseDouble(active);
        gpu = parseDouble(gpuText);
        if (!name.isEmpty()) {
            gpuName = name;
        }
        Long usedBytes = parseLong(used);
        Long totalBytes = parseLong(total);
        gpuMemoryUsed = usedBytes == null ? -1 : usedBytes;
        gpuMemoryTotal = totalBytes == null ? -1 : totalBytes;
        netDown = parseDouble(down);
        netUp = parseDouble(up);
        diskRead = parseDouble(read);
        diskWrite = parseDouble(write);
        if (!cpuNameLine.isEmpty()) {
            cpuName = cpuNameLine;
        }
        Integer cores = parseInt(cpuCoresLine);
        Integer logical = parseInt(cpuLogicalLine);
        Integer mhz = parseInt(cpuMhzLine);
        if (cores != null) {
            cpuCores = cores;
        }
        if (logical != null) {
            cpuLogical = logical;
        }
        if (mhz != null) {
            cpuMhz = mhz;
        }
        if (!coreLine.isEmpty()) {
            coreVals = coreLine;
        }
        if (!gpusLine.isEmpty()) {
            gpusRaw = gpusLine;
        }
        JSON.set(build());
    }

    private static String build() {
        Double memPct = memoryTotal > 0 && memoryUsed >= 0 ? memoryUsed * 100.0 / memoryTotal : null;
        return "{\"ok\":true"
                + ",\"cpu\":" + num(cpu)
                + ",\"cpuName\":" + quote(cpuName)
                + ",\"cpuCores\":" + (cpuCores < 0 ? "null" : Integer.toString(cpuCores))
                + ",\"cpuLogical\":" + (cpuLogical < 0 ? "null" : Integer.toString(cpuLogical))
                + ",\"cpuMhz\":" + (cpuMhz < 0 ? "null" : Integer.toString(cpuMhz))
                + ",\"cores\":" + coreJson()
                + ",\"memoryUsed\":" + (memoryUsed < 0 ? "null" : Long.toString(memoryUsed))
                + ",\"memoryTotal\":" + (memoryTotal < 0 ? "null" : Long.toString(memoryTotal))
                + ",\"memoryPercent\":" + num(memPct)
                + ",\"diskActive\":" + num(diskActive)
                + ",\"netDown\":" + num(netDown)
                + ",\"netUp\":" + num(netUp)
                + ",\"diskRead\":" + num(diskRead)
                + ",\"diskWrite\":" + num(diskWrite)
                + ",\"gpu\":" + num(gpu)
                + ",\"gpuName\":" + quote(gpuName)
                + ",\"gpuMemoryText\":" + quote(gpuMemoryText())
                + ",\"gpus\":" + gpusJson()
                + ",\"disks\":" + disksJson
                + "}";
    }

    private static String gpusJson() {
        if (gpusRaw == null || gpusRaw.isBlank()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String row : gpusRaw.split(";;")) {
            String[] part = row.split("\\|", -1);
            if (part.length < 2) {
                continue;
            }
            Double util = parseDouble(part[1]);
            long used = part.length > 2 ? longOrNeg(part[2]) : -1;
            long total = part.length > 3 ? longOrNeg(part[3]) : -1;
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"name\":").append(quote(part[0].trim()))
                    .append(",\"util\":").append(num(util))
                    .append(",\"memoryText\":").append(quote(memoryPair(used, total))).append('}');
        }
        json.append(']');
        return json.toString();
    }

    private static long longOrNeg(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String memoryPair(long used, long total) {
        if (used < 0) {
            return "";
        }
        if (total > 0) {
            return gb(used) + " / " + gb(total);
        }
        return "已用 " + gb(used);
    }

    private static String gpuMemoryText() {
        if (gpuMemoryUsed < 0) {
            return "";
        }
        if (gpuMemoryTotal > 0) {
            return gb(gpuMemoryUsed) + " / " + gb(gpuMemoryTotal);
        }
        return "已用 " + gb(gpuMemoryUsed);
    }

    private static String readDisks() {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        int count = 0;
        for (FileStore store : FileSystems.getDefault().getFileStores()) {
            try {
                long total = store.getTotalSpace();
                if (total <= 0) {
                    continue;
                }
                long used = Math.max(0, total - store.getUsableSpace());
                if (!first) {
                    json.append(',');
                }
                first = false;
                json.append("{\"name\":").append(quote(store.toString()))
                        .append(",\"percent\":").append(String.format(Locale.US, "%.1f", used * 100.0 / total))
                        .append(",\"usedText\":").append(quote(gb(used)))
                        .append(",\"totalText\":").append(quote(gb(total)))
                        .append('}');
                if (++count >= 8) {
                    break;
                }
            } catch (Exception ignored) {
                // unreadable volume
            }
        }
        json.append(']');
        return json.toString();
    }

    private static String gb(long bytes) {
        double value = bytes / 1073741824d;
        return String.format(Locale.US, value >= 10 ? "%.1f GB" : "%.2f GB", value);
    }

    private static String num(Double value) {
        if (value == null || value.isNaN()) {
            return "null";
        }
        return String.format(Locale.US, "%.1f", value);
    }

    private static String coreJson() {
        if (coreVals == null || coreVals.isBlank()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (String part : coreVals.split(",")) {
            Double value = parseDouble(part.trim());
            if (value == null) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append(num(value));
        }
        json.append(']');
        return json.toString();
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Double parseDouble(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String quote(String raw) {
        if (raw == null) {
            return "\"\"";
        }
        StringBuilder out = new StringBuilder(raw.length() + 2);
        out.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\', '"' -> out.append('\\').append(c);
                case '\n', '\r' -> out.append(' ');
                default -> out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
