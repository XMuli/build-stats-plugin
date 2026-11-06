/*
 * seed-jobs.groovy - paste into Manage Jenkins > Script Console of a local dev Jenkins.
 *
 * Seeds ~127 jobs with ~900 runs so the Build Stats job sidebar can be stress-tested for
 * search, pagination (25 per page => 6 pages), deep folder paths, name escaping and a
 * bounded DOM. Verified against jenkins-core 2.555.3.
 *
 * HOW BUILD HISTORY IS MADE - READ THIS BEFORE TRUSTING THE NUMBERS
 * The runs are SYNTHESIZED, not executed. Nothing is queued, no executor runs, no workspace
 * is checked out and no console log exists. For each run the script creates one real
 * hudson.model.FreeStyleBuild object, overwrites its timestamp/startTime/duration/result
 * fields, and calls Run.save(), which serializes build.xml through Jenkins' own XStream
 * instance (hudson.model.Run.XSTREAM). The XML is therefore written by Jenkins itself and
 * cannot drift from the core version in use. The runs are then made visible by calling
 * purgeCache() on the job's RunMap, which re-scans builds/ for numeric directory names and
 * lazy-loads each one through the real public FreeStyleBuild(FreeStyleProject, File)
 * constructor - the exact path a restart would take. No Jenkins restart is needed.
 *
 * WHAT THIS MEANS IN PRACTICE
 *   - run.getResult(), getTimeInMillis(), getStartTimeInMillis(), getDuration() and
 *     getCauses() all return real values, so trend/heatmap/trigger charts have data.
 *   - run.getLog() is empty and there is no changelog, no artifacts, no build.log file.
 *   - Durations are invented; they do not reflect any real work.
 *   - Stage timings are absent. Build Stats reads those from Pipeline: Stage View via
 *     StageReader, which no-ops for FreeStyleBuild, so stage charts stay empty by design.
 *
 * SAFETY
 * Every folder and job this script creates gets MARKER in its description. The script never
 * writes to, reloads or deletes an item that lacks MARKER, so pre-existing jobs are left
 * alone. Re-running is idempotent: existing items are reused and jobs that already have
 * builds are skipped. The commented-out CLEANUP section at the bottom deletes only
 * MARKER-tagged items under the known roots.
 */

import hudson.model.FreeStyleBuild
import hudson.model.FreeStyleProject
import hudson.model.Result
import hudson.model.Cause
import hudson.model.CauseAction
import hudson.triggers.SCMTrigger
import hudson.triggers.TimerTrigger
import jenkins.model.Jenkins

// ============================== CONFIG ==============================

/** Tag written into every created item's description. Cleanup and re-runs match on it. */
final String MARKER = 'seeded-by=preview/seed-jobs.groovy'

/** Top-level names this script is allowed to create or touch. Nothing else is ever written. */
final List<String> ROOTS = ['acme-engineering', 'infrastructure', 'observability', 'services']

/** true = print the plan and touch nothing. Flip to false to actually seed. */
final boolean DRY_RUN = false

/** How many jobs get a long history (30-60 runs). The rest get 1-3. */
final int HEAVY_JOBS = 15

/**
 * Leave one job with a build in BUILDING state. This is a real in-memory Run that the UI
 * shows as running; it does NOT occupy an executor and has no log. It reverts to ABORTED
 * when Jenkins restarts (Run.reload() pre-sets result = ABORTED before unmarshalling a
 * build.xml that has no <result>), which is the same thing Jenkins does to a build killed
 * by a crash. Set false if a permanently-running build would confuse you.
 */
final boolean SEED_RUNNING_BUILD = true

/**
 * Windows MAX_PATH guard. Jenkins nests folder jobs as jobs/<a>/jobs/<b>/..., so a deep path
 * plus the per-build files can pass 260 chars and fail mid-write. Any job whose projected
 * path exceeds this budget is skipped with a warning instead of being half-created.
 * Raise it only if you know your filesystem supports long paths.
 */
final int MAX_PATH_BUDGET = 250

/**
 * Longest path Jenkins appends under a job root for a build. build.xml is what this script
 * writes, but a real build later adds changelog.xml / workspace paths, so the guard reserves
 * for the longest of these rather than just build.xml.
 */
final String LONGEST_BUILD_SUFFIX = '\\builds\\99999\\changelog.xml'

final long WALL_START = System.currentTimeMillis()
final Jenkins JENKINS = Jenkins.get()
final Random RNG = new Random(20261106L)   // fixed seed: same data every run

// ============================== PREFLIGHT ==============================

/*
 * The folders plugin (cloudbees-folder) is optional. Detect it by class lookup through the
 * plugin uberClassLoader and verify Jenkins actually has a descriptor for it; a class on the
 * classpath with no descriptor cannot be instantiated as an item.
 */
def folderDescriptor = null
try {
    Class<?> folderClass = JENKINS.pluginManager.uberClassLoader.loadClass('com.cloudbees.hudson.plugins.folder.Folder')
    folderDescriptor = JENKINS.getDescriptor(folderClass)
} catch (ClassNotFoundException | LinkageError ignored) {
    folderDescriptor = null
}
final boolean NESTED = folderDescriptor != null

println '=' * 78
if (NESTED) {
    println 'MODE: NESTED  - cloudbees-folder found, creating real nested folders.'
} else {
    println 'MODE: FLAT    - WARNING: cloudbees-folder is NOT installed.'
    println '              Folder paths are flattened into single job names joined by " -- ",'
    println '              e.g. "acme-engineering -- platform-delivery -- release -- 2026.11".'
    println '              Names still contain dashes and are long, so sidebar search and'
    println '              pagination are exercised, but true folder nesting and the'
    println '              /job/a/job/b URL shape are NOT. Install cloudbees-folder and'
    println '              re-run for full coverage.'
}
println '=' * 78

final TopLevelItemDescriptor_FREESTYLE = JENKINS.getDescriptorByType(FreeStyleProject.DescriptorImpl)

// ============================== JOB CATALOGUE ==============================

/*
 * Each entry is a list of path segments. In NESTED mode the leading segments become folders
 * and the last becomes the FreeStyleProject. In FLAT mode they are joined into one name.
 */
List<List<String>> paths = []

// The five exact shapes the sidebar must handle.
paths << ['acme-engineering', 'platform-delivery', 'release', '2026.11']
paths << ['acme-engineering', 'platform-delivery', 'PR-1842']
paths << ['acme-engineering', 'agent-images', 'main']
paths << ['infrastructure', 'windows-agents', 'nightly']
paths << ['observability', 'log-pipeline', 'PR-801']

// Bulk set: services/team-01..99/integration/main - the volume that forces pagination.
(1..99).each { n -> paths << ['services', String.format('team-%02d', n), 'integration', 'main'] }

/*
 * Escaping probe. Jenkins' own Jenkins.checkGoodName() rejects the characters
 * ?*\/\%!@#$^&|<>[]:; in an item NAME, verified in the 2.555.3 bytecode, so a job literally
 * named "release & hotfix 2026" cannot be created through any supported API. The test is
 * therefore split across the two fields that do reach a page:
 *   - the NAME carries spaces. Verified live: Build Stats renders the job link as
 *     ?job=acme-engineering%2Fplatform-delivery%2Frelease+and+hotfix+2026, so the space and
 *     the path separator are genuinely exercised in both a URL path and a query value.
 *   - the DISPLAY NAME carries the ampersand. setDisplayName() is not subject to
 *     checkGoodName. Verified live: Jenkins' own job page escapes it to
 *     "release &amp; hotfix 2026" with no raw ampersand.
 * KNOWN GAP: Build Stats reads getFullName() and never getDisplayName(), so the ampersand
 * does NOT reach the plugin's own markup. To cover the plugin's HTML escaping, have it read
 * getDisplayName(), or add a job whose legal name needs escaping.
 */
paths << ['acme-engineering', 'platform-delivery', 'release and hotfix 2026']
final List<String> ESCAPE_PROBE = paths[-1]
final String ESCAPE_DISPLAY_NAME = 'release & hotfix 2026'

// Pathologically long path: 130+ chars of logical path across several realistic segments.
paths << ['acme-engineering', 'platform-delivery-extended-matrix',
          'release-candidate-validation-suite', '2026.11-hotfix-rollup-candidate-07-eu-west']

// Remaining realistic shapes, to clear 120 jobs and give the sidebar varied prefixes.
paths << ['acme-engineering', 'platform-delivery', 'PR-1843']
paths << ['acme-engineering', 'platform-delivery', 'PR-1844']
paths << ['acme-engineering', 'platform-delivery', 'main']
paths << ['acme-engineering', 'platform-delivery', 'release', '2026.10']
paths << ['acme-engineering', 'platform-delivery', 'release', '2027.01']
paths << ['acme-engineering', 'agent-images', 'PR-77']
paths << ['acme-engineering', 'agent-images', 'release', '2026.11']
paths << ['acme-engineering', 'ladm-package', 'main']          // "ladm" => Build Stats
paths << ['acme-engineering', 'ladm-test', 'main']             // groups these as package/test
paths << ['infrastructure', 'windows-agents', 'main']
paths << ['infrastructure', 'windows-agents', 'PR-12']
paths << ['infrastructure', 'linux-agents', 'nightly']
paths << ['infrastructure', 'linux-agents', 'main']
paths << ['infrastructure', 'macos-agents', 'nightly']
paths << ['infrastructure', 'macos-agents', 'PR-9']
paths << ['infrastructure', 'terraform-estate', 'apply']
paths << ['observability', 'log-pipeline', 'main']
paths << ['observability', 'log-pipeline', 'nightly']
paths << ['observability', 'metrics-store', 'main']
paths << ['observability', 'trace-collector', 'PR-404']
paths << ['services', 'gateway', 'integration', 'main']

// ============================== PLAN ==============================

/** Logical "a/b/c" path, independent of NESTED vs FLAT. Used for reporting. */
final Closure<String> logicalPath = { List<String> segs -> segs.join('/') }

/**
 * The item name to create inside the resolved container. In NESTED mode the container is the
 * parent folder, so only the last segment is the item name. In FLAT mode the container is
 * Jenkins itself, so the whole path has to be flattened into the single name - using just
 * the last segment there would collapse all 99 ".../integration/main" paths onto one job.
 */
final Closure<String> itemNameOf = { List<String> segs ->
    NESTED ? segs[-1] : segs.join(' -- ')
}

/** Full Jenkins name of the created item: real nesting, or one flattened name. */
final Closure<String> fullNameOf = { List<String> segs ->
    NESTED ? segs.join('/') : segs.join(' -- ')
}

final int deepest = paths.collect { logicalPath(it).length() }.max()
final List<String> longOnes = paths.findAll { logicalPath(it).length() >= 120 }.collect { logicalPath(it) }

println()
println "ABOUT TO DO THIS${DRY_RUN ? ' (DRY RUN - nothing will be written)' : ''}:"
println "  jobs planned          : ${paths.size()}"
println "  roots touched          : ${ROOTS.join(', ')}"
println "  jobs with long history : ${HEAVY_JOBS} (30-60 runs each)"
println "  every other job        : 1-3 runs"
println "  timestamps spread over : last 14 months (crosses a year boundary and 2024-02-29)"
println "  deepest logical path   : ${deepest} chars"
println "  120+ char paths        : ${longOnes.size()}"
longOnes.each { println "      ${it}" }
println "  escaping probe         : name '${ESCAPE_PROBE[-1]}' (space) / display name '${ESCAPE_DISPLAY_NAME}' (ampersand)"
println "  running build          : ${SEED_RUNNING_BUILD ? 'yes, one' : 'no'}"
println "  every item tagged      : ${MARKER}"
println()

if (paths.size() < 120) {
    println "ABORT: only ${paths.size()} jobs planned, need at least 120. Catalogue is wrong."
    return
}
if (!paths.collect { it[0] }.every { ROOTS.contains(it) }) {
    println 'ABORT: a planned path starts outside ROOTS. Refusing to touch unknown top-level names.'
    return
}

// ============================== HELPERS ==============================

/** True if an existing item carries our marker, i.e. we created it and may touch it. */
final Closure<Boolean> isOurs = { item ->
    item != null && item.description != null && item.description.contains(MARKER)
}

/**
 * Resolve or create the container for all but the last segment.
 * Returns null if any existing item in the way is NOT ours (never hijack a real folder).
 */
final Closure containerFor = { List<String> segs ->
    if (!NESTED) {
        return JENKINS
    }
    def parent = JENKINS
    for (String seg : segs[0..-2]) {
        def existing = parent.getItem(seg)
        if (existing != null) {
            if (!isOurs(existing)) {
                println "  SKIP: '${seg}' already exists and is not ours - refusing to touch it."
                return null
            }
            parent = existing
            continue
        }
        if (DRY_RUN) {
            return null
        }
        def created = parent.createProject(folderDescriptor, seg, false)
        created.description = "Seeded dev folder.\n${MARKER}"
        created.save()
        parent = created
    }
    return parent
}

/**
 * Pick a result. Mostly SUCCESS, with every other completed state represented so the
 * charts have something in each bucket.
 */
final Closure<Result> pickResult = { Random rng ->
    int roll = rng.nextInt(100)
    if (roll < 68) return Result.SUCCESS
    if (roll < 83) return Result.FAILURE
    if (roll < 92) return Result.UNSTABLE
    if (roll < 97) return Result.ABORTED
    return Result.NOT_BUILT
}

/** A cause, so the trigger/actor charts are not all one colour. */
final Closure pickCause = { Random rng ->
    switch (rng.nextInt(4)) {
        case 0:  return new Cause.UserIdCause('devuser')
        case 1:  return new TimerTrigger.TimerTriggerCause()
        case 2:  return new SCMTrigger.SCMTriggerCause('seeded')
        default: return new Cause.RemoteCause('10.0.0.8', 'seeded webhook')
    }
}

/*
 * Timestamp spread. Covers the last 14 months so the 12-month heatmap default is tested
 * across its own edge: two months of data fall OUTSIDE the window and must be excluded.
 * One run per job is forced onto a 29 February so leap-day bucketing is exercised.
 */
final long NOW = System.currentTimeMillis()
final long DAY = 86_400_000L
final long WINDOW_DAYS = 427L               // ~14 months

/**
 * The most recent 29 February at or before now, computed rather than hardcoded so the
 * script stays correct after Feb 2028. With "now" in Nov 2026 this is 2024-02-29, which
 * falls outside the 14-month window on purpose - see the anchor comment below.
 */
final long LEAP_DAY = { ->
    Calendar probe = Calendar.getInstance()
    probe.timeInMillis = NOW
    int year = probe.get(Calendar.YEAR)
    for (int y = year; y > year - 12; y--) {
        Calendar c = Calendar.getInstance()
        c.clear()
        c.setLenient(false)                      // refuses 29 Feb in a non-leap year
        try {
            c.set(y, Calendar.FEBRUARY, 29, 11, 17, 0)
            long t = c.timeInMillis               // throws here if the date is invalid
            if (t <= NOW) {
                return t
            }
        } catch (IllegalArgumentException ignored) {
            // not a leap year, try the one before
        }
    }
    return NOW - 365L * DAY                       // unreachable in practice
}()

/**
 * Set a field declared anywhere up the class hierarchy. Needed because the run fields we
 * must control are protected/private on hudson.model.Run with no setters:
 *   number    (public transient int - but set here too, for uniformity)
 *   timestamp (protected long)      startTime (private long)
 *   duration  (protected long)      result    (protected volatile Result)
 *   state     (private transient Run$State)
 * All six confirmed present in jenkins-core 2.555.3. Jenkins core is on the unnamed module,
 * so setAccessible succeeds without --add-opens.
 */
final Closure setField = { Object target, String name, Object value ->
    Class<?> c = target.getClass()
    while (c != null) {
        try {
            def f = c.getDeclaredField(name)
            f.setAccessible(true)
            f.set(target, value)
            return
        } catch (NoSuchFieldException ignored) {
            c = c.getSuperclass()
        }
    }
    throw new NoSuchFieldException("no field '${name}' on ${target.getClass().name}")
}

/** Read the Run$State enum constant by name; the enum type is package-private. */
final Closure stateValue = { String name ->
    def f = hudson.model.Run.class.getDeclaredField('state')
    f.setAccessible(true)
    return f.type.getEnumConstants().find { it.name() == name }
}

/**
 * Write one synthesized run onto a job and return it.
 *
 * Order matters and is dictated by core 2.555.3:
 *   1. RunMap.put(run) FIRST. put() throws IllegalStateException if the build directory
 *      already exists, and calls proposeNewNumber(), which throws unless the number is
 *      above the highest number already on disk. put() then creates the directory itself
 *      via Util.createDirectories.
 *   2. Mutate the fields.
 *   3. Run.save() LAST. save() -> XmlFile.write() serializes through Run.XSTREAM and
 *      mkdirs() on the way, so build.xml lands in builds/<n>/build.xml.
 *
 * put() is invoked reflectively against the exact (Run) signature: RunMap exposes both
 * put(R) and the erased put(Object) bridge, and letting Groovy choose is ambiguous.
 */
final Closure writeRun = { FreeStyleProject job, int number, long whenMs, Result result,
                           long durationMs, Object cause, boolean building ->
    FreeStyleBuild run = new FreeStyleBuild(job)

    // The FreeStyleBuild(project) constructor called assignBuildNumber(), which consumed a
    // number that may not be the one we want. Force ours before put(), which validates
    // against run.getNumber(), and before save(), since getRootDir() is buildDir/<number>.
    setField(run, 'number', number)

    def runs = job._getRuns()
    def putMethod = runs.getClass().getMethod('put', hudson.model.Run)
    putMethod.invoke(runs, run)

    // timestamp = queued at, startTime = execution began. The gap feeds queue-time charts.
    // Both are serialized (non-transient), so they survive the reload from disk.
    long queueMs = 2_000L + (long) (durationMs * 0.05d)
    setField(run, 'timestamp', whenMs - queueMs)
    setField(run, 'startTime', whenMs)
    setField(run, 'duration', building ? 0L : durationMs)
    setField(run, 'charset', 'UTF-8')

    if (building) {
        // BUILDING makes isBuilding() true and leaves getResult() null, which is what the
        // UI reads. No <result> is written, so a restart loads it back as ABORTED.
        setField(run, 'state', stateValue('BUILDING'))
    } else {
        setField(run, 'state', stateValue('COMPLETED'))
        setField(run, 'result', result)
    }

    if (cause != null) {
        run.addAction(new CauseAction(cause))
    }
    run.save()
    return run
}

// ============================== SEED ==============================

if (DRY_RUN) {
    println 'DRY RUN: stopping here. Set DRY_RUN = false to seed.'
    println "Wall time: ${System.currentTimeMillis() - WALL_START} ms"
    return
}

int jobsCreated = 0
int jobsReused = 0
int jobsSkipped = 0
int runsCreated = 0
int deepestOnDisk = 0
int tightPaths = 0
String runningJobName = null

/* Heavy-history jobs: the five required shapes plus named branches and a spread of teams. */
final Set<Integer> HEAVY = new LinkedHashSet<Integer>()
[0, 1, 2, 3, 4].each { HEAVY << it }                       // the five required shapes
paths.eachWithIndex { segs, i ->
    if (HEAVY.size() >= HEAVY_JOBS) return
    String lp = logicalPath(segs)
    if (lp.endsWith('ladm-package/main') || lp.endsWith('ladm-test/main')
            || lp.endsWith('terraform-estate/apply') || lp.endsWith('metrics-store/main')
            || lp == 'services/team-01/integration/main' || lp == 'services/team-25/integration/main'
            || lp == 'services/team-50/integration/main' || lp == 'services/team-75/integration/main'
            || lp == 'services/team-99/integration/main' || lp.endsWith('gateway/integration/main')) {
        HEAVY << i
    }
}

paths.eachWithIndex { List<String> segs, int idx ->
    String leaf = itemNameOf(segs)
    String fullName = fullNameOf(segs)

    def parent = containerFor(segs)
    if (parent == null) {
        jobsSkipped++
        return
    }

    def existingForPath = parent.getItem(leaf)

    /*
     * Windows MAX_PATH guard. Check the real on-disk path before writing anything, so a
     * too-deep job is skipped cleanly rather than failing partway through its builds.
     */
    File wouldBe = existingForPath != null
            ? existingForPath.rootDir
            : new File(new File(NESTED ? parent.rootDir : JENKINS.rootDir, 'jobs'), leaf)
    int projectedLen = wouldBe.path.length() + LONGEST_BUILD_SUFFIX.length()
    if (projectedLen > MAX_PATH_BUDGET) {
        println "  SKIP '${fullName}': projected path ${projectedLen} chars exceeds MAX_PATH_BUDGET ${MAX_PATH_BUDGET}."
        println '        Windows MAX_PATH would fail mid-write. Move JENKINS_HOME higher up, or shorten the name.'
        jobsSkipped++
        return
    }
    if (260 - projectedLen < 25) {
        println "  NOTE '${fullName}': only ${260 - projectedLen} chars of Windows MAX_PATH headroom left."
        println '        It will seed fine, but a real build writing deeper paths here may fail.'
        tightPaths++
    }

    FreeStyleProject job
    if (existingForPath != null) {
        if (!isOurs(existingForPath) || !(existingForPath instanceof FreeStyleProject)) {
            println "  SKIP '${fullName}': item exists and is not ours - leaving it untouched."
            jobsSkipped++
            return
        }
        job = existingForPath as FreeStyleProject
        jobsReused++
    } else {
        job = parent.createProject(TopLevelItemDescriptor_FREESTYLE, leaf, false) as FreeStyleProject
        job.description = "Seeded dev job for sidebar stress-testing.\n${MARKER}"
        if (segs == ESCAPE_PROBE) {
            // The ampersand lives here because checkGoodName() forbids it in the name.
            job.setDisplayName(ESCAPE_DISPLAY_NAME)
        }
        job.save()
        jobsCreated++
    }

    deepestOnDisk = Math.max(deepestOnDisk, job.rootDir.path.length())

    // Idempotency: a job that already has runs is left exactly as it is.
    if (!job.builds.isEmpty()) {
        return
    }

    boolean heavy = HEAVY.contains(idx)
    int count = heavy ? 30 + RNG.nextInt(31) : 1 + RNG.nextInt(3)

    // Collect timestamps first, then sort, so build numbers increase with time.
    List<Long> times = []
    if (heavy) {
        long span = WINDOW_DAYS * DAY
        (0..<count).each { k ->
            long slot = (long) (span * (k + 0.5d) / count)
            times << (NOW - span + slot + RNG.nextInt(86_400) * 1000L)
        }
    } else {
        // Light jobs stay recent so their "last build" reads as plausible. The hour offset is
        // SUBTRACTED, never added: adding it to a 1-day-old base could land in the future,
        // where the "it < NOW" filter below would drop it and leave the job with no builds.
        (0..<count).each { k ->
            times << (NOW - (1 + RNG.nextInt(120)) * DAY - RNG.nextInt(72) * 3_600_000L)
        }
    }

    /*
     * Date-edge anchors on the heavy jobs:
     *   - the last minutes of the previous calendar year and the first of this one;
     *   - a 29 February. The most recent one (2024-02-29) deliberately falls OUTSIDE the
     *     14-month window: it proves leap-day arithmetic does not throw AND that the
     *     12-month heatmap correctly excludes older runs. Do not move it inside.
     */
    if (heavy) {
        Calendar cal = Calendar.getInstance()
        cal.timeInMillis = NOW
        int thisYear = cal.get(Calendar.YEAR)
        cal.clear()
        cal.set(thisYear, Calendar.JANUARY, 1, 0, 0, 0)
        long jan1 = cal.timeInMillis
        times << (jan1 - 7 * 60_000L)     // 23:53 on 31 Dec
        times << (jan1 + 11 * 60_000L)    // 00:11 on 1 Jan
        times << LEAP_DAY
    }

    times = times.findAll { it > 0 && it < NOW }.sort()
    count = times.size()

    times.eachWithIndex { Long whenMs, int k ->
        long durationMs = 60_000L + RNG.nextInt(2_280_000)        // 1 - 40 min
        if (RNG.nextInt(25) == 0) {
            durationMs += 1_800_000L                              // occasional slow outlier
        }
        writeRun(job, k + 1, whenMs, pickResult(RNG), durationMs, pickCause(RNG), false)
        runsCreated++
    }

    // Keep nextBuildNumber consistent with what is now on disk.
    job.updateNextBuildNumber(count + 1)

    /*
     * Make the runs visible without a restart. purgeCache() re-scans builds/ for directory
     * names matching [0-9]+ and lazy-loads each through FreeStyleBuild(project, dir), which
     * parses the number from the directory name and calls Run.reload() to unmarshal
     * build.xml. This drops the in-memory objects written above in favour of ones read back
     * from disk, which also proves the XML we wrote is genuinely loadable.
     */
    job._getRuns().purgeCache()

    if (count > 0 && job.lastBuild == null) {
        println "  WARNING '${fullName}': ${count} runs written but none loaded back. Inspect build.xml."
    }
}

// ============================== RUNNING BUILD ==============================

/*
 * One job keeps a build in BUILDING state. This is a real Run object in the job's RunMap, so
 * isBuilding() is true, getResult() is null and the UI renders it as in progress. It is NOT
 * executing: no executor is assigned, no log is produced, and it will sit there forever.
 * It is added after that job's purgeCache() so it is not immediately discarded.
 * On restart it loads back as ABORTED, the same as any build killed by a crash.
 */
if (SEED_RUNNING_BUILD) {
    def target = null
    for (List<String> segs : paths) {
        def candidate = JENKINS.getItemByFullName(fullNameOf(segs))
        if (candidate instanceof FreeStyleProject && isOurs(candidate) && candidate.lastBuild != null) {
            target = candidate
            break
        }
    }
    if (target == null) {
        println 'NOTE: no seeded job was available for a running build, so none was created.'
    } else if (target.lastBuild != null && target.lastBuild.isBuilding()) {
        println "NOTE: '${target.fullName}' already has a running build; left as is."
        runningJobName = target.fullName
    } else {
        int next = target._getRuns().maxNumberOnDisk() + 1
        writeRun(target, next, NOW - 420_000L, null, 0L, new Cause.UserIdCause('devuser'), true)
        target.updateNextBuildNumber(next + 1)
        runsCreated++
        runningJobName = target.fullName
        println "Running build: #${next} on '${runningJobName}' (in-memory BUILDING, no executor)."
    }
}

// ============================== REPORT ==============================

final long WALL_MS = System.currentTimeMillis() - WALL_START
int totalJobs = 0
int totalRuns = 0
JENKINS.getAllItems(hudson.model.Job).each { j ->
    if (isOurs(j)) {
        totalJobs++
        totalRuns += j.builds.size()
    }
}

println()
println '=' * 78
println 'SEEDING COMPLETE'
println '=' * 78
println "  jobs created this run  : ${jobsCreated}"
println "  jobs reused (idempotent): ${jobsReused}"
println "  jobs skipped           : ${jobsSkipped}"
println "  runs created this run  : ${runsCreated}"
println "  seeded jobs now present: ${totalJobs}"
println "  seeded runs now present: ${totalRuns}"
println "  sidebar pages @25/page : ${(int) Math.ceil(totalJobs / 25.0d)}"
println "  deepest logical path   : ${deepest} chars"
println "  deepest on-disk path   : ${deepestOnDisk} chars (budget ${MAX_PATH_BUDGET})"
println "  paths near MAX_PATH    : ${tightPaths}"
println "  running build          : ${runningJobName ?: 'none'}"
println "  mode                   : ${NESTED ? 'NESTED folders' : 'FLAT names (cloudbees-folder missing)'}"
println "  wall time              : ${WALL_MS} ms (${String.format('%.1f', WALL_MS / 1000.0d)} s)"
println()
println 'Reminder: these runs are synthesized. They have no console log, no changelog and no'
println 'artifacts, and their durations are invented. See the header comment.'
println '=' * 78

/* ============================================================================
 * ============================== CLEANUP =====================================
 * ============================================================================
 *
 * Uncomment the block below and run it to remove everything this script created.
 *
 * It deletes an item only when BOTH hold:
 *   1. its top-level name is one of ROOTS, and
 *   2. its description contains MARKER.
 * Anything you created by hand is therefore untouched, including a folder named e.g.
 * "services" that already existed - that folder is left in place and only the marked
 * children inside it are removed. Deepest-first ordering means children go before parents.
 *
 * Copy ROOTS and MARKER from the top of this file if you run this block on its own.

final String MARKER = 'seeded-by=preview/seed-jobs.groovy'
final List<String> ROOTS = ['acme-engineering', 'infrastructure', 'observability', 'services']
def jenkins = jenkins.model.Jenkins.get()

def doomed = jenkins.getAllItems(hudson.model.Item).findAll { item ->
    String top = item.fullName.contains('/') ? item.fullName.substring(0, item.fullName.indexOf('/')) : item.fullName
    boolean inRoots = ROOTS.contains(top) || ROOTS.any { top.startsWith(it + ' -- ') }
    boolean marked = item.description != null && item.description.contains(MARKER)
    return inRoots && marked
}.sort { -it.fullName.count('/') }     // deepest first

println "CLEANUP: ${doomed.size()} marked items will be deleted:"
doomed.each { println "  ${it.fullName}" }

int deleted = 0
doomed.each { item ->
    try {
        item.delete()
        deleted++
    } catch (Exception e) {
        println "  FAILED to delete ${item.fullName}: ${e.message}"
    }
}

// Remove the now-empty root folders, but only the ones we marked ourselves.
ROOTS.each { name ->
    def root = jenkins.getItem(name)
    if (root != null && root.description != null && root.description.contains(MARKER)) {
        try {
            root.delete()
            deleted++
        } catch (Exception e) {
            println "  FAILED to delete ${name}: ${e.message}"
        }
    }
}

println "CLEANUP: deleted ${deleted} items."

 * ========================================================================== */
