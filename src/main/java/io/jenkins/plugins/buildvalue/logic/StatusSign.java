package io.jenkins.plugins.buildvalue.logic;

/**
 * Which status plate matches one build result.
 *
 * <p>The job path, build number and result are separate fields. Earlier versions packed them into
 * one caption string and split it apart again in the view, which cannot survive a job name
 * containing the separator.
 */
public final class StatusSign {
    public final String image;
    public final String alt;
    /** Full Jenkins job path, for the tooltip and the accessible name. */
    public final String jobFullName;
    /** Build number, or 0 when there is no build. */
    public final int number;
    /** Result key: SUCCESS, FAILURE, UNSTABLE, ABORTED, NOT_BUILT or RUNNING. */
    public final String result;
    public final boolean live;
    public final String buildUrl;

    private StatusSign(
            String image, String alt, String jobFullName, int number, String result, boolean live, String buildUrl) {
        this.image = image;
        this.alt = alt;
        this.jobFullName = jobFullName == null ? "" : jobFullName;
        this.number = number;
        this.result = result == null ? "" : result;
        this.live = live;
        this.buildUrl = buildUrl == null ? "" : buildUrl;
    }

    public static StatusSign none() {
        return new StatusSign("", "", "", 0, "", false, "");
    }

    public static StatusSign of(String result, String jobFullName, int number) {
        return of(result, jobFullName, number, "");
    }

    public static StatusSign of(String result, String jobFullName, int number, String buildUrl) {
        boolean live = "RUNNING".equals(result);
        String image;
        String alt;
        if (live) {
            image = "hybrid-zaiganle.jpg";
            alt = "ON IT!";
        } else if ("SUCCESS".equals(result)) {
            image = "hybrid-ganwanle.jpg";
            alt = "ALL DONE!";
        } else if ("FAILURE".equals(result) || "UNSTABLE".equals(result)) {
            image = "hybrid-ganbuwan.jpg";
            alt = "NEVER-ENDING WORK";
        } else if ("ABORTED".equals(result) || "NOT_BUILT".equals(result)) {
            image = "hybrid-buganle.jpg";
            alt = "I QUIT!";
        } else {
            return none();
        }
        return new StatusSign(image, alt, jobFullName, number, result, live, buildUrl);
    }

    public boolean present() {
        return !image.isEmpty();
    }

    /**
     * The artwork for the request's language. Chinese requests get the Chinese plate, everything
     * else the English one. Chosen on the server so the first paint is already correct.
     */
    public String localizedImage() {
        if (image.isEmpty()) {
            return "";
        }
        if (Texts.chineseRequest()) {
            return image;
        }
        int dot = image.lastIndexOf('.');
        return dot < 0 ? image + "-en" : image.substring(0, dot) + "-en" + image.substring(dot);
    }

    /** Localized result word, so text carries the state as well as colour. */
    public String stateText() {
        return Texts.stateText(result);
    }

    /** "Latest build", shown at the right of the last line under the illustration. */
    public String kicker() {
        return io.jenkins.plugins.buildvalue.Messages.sign_latest();
    }

    /**
     * Accessible name of the build link. The visible text is only the number, so the name carries
     * the full job path a reader would otherwise have to go looking for.
     */
    public String buildLinkLabel() {
        return io.jenkins.plugins.buildvalue.Messages.sign_openBuild(buildLabel(), jobFullName);
    }

    /** Modifier for the badge class: ok, run, bad, warn or stop. */
    public String stateClass() {
        return Texts.stateClass(result);
    }

    /** Last path segment, for the compact line under the illustration. */
    public String shortName() {
        return BuildSample.shortName(jobFullName);
    }

    /** Build number with its {@code #}, for the compact link. */
    public String buildLabel() {
        return "#" + number;
    }

    public String getImage() {
        return image;
    }

    public String getAlt() {
        return alt;
    }

    public String getJobFullName() {
        return jobFullName;
    }

    public int getNumber() {
        return number;
    }

    public String getResult() {
        return result;
    }

    public boolean isLive() {
        return live;
    }

    public String getBuildUrl() {
        return buildUrl;
    }
}
