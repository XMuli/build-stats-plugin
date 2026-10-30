package io.jenkins.plugins.buildvalue.logic;

/** Which status plate matches one build result. */
public final class StatusSign {
    public final String image;
    public final String alt;
    public final String caption;
    public final boolean live;
    public final String buildUrl;

    private StatusSign(String image, String alt, String caption, boolean live, String buildUrl) {
        this.image = image;
        this.alt = alt;
        this.caption = caption;
        this.live = live;
        this.buildUrl = buildUrl == null ? "" : buildUrl;
    }

    public static StatusSign none() {
        return new StatusSign("", "", "", false, "");
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
        String subject = BuildSample.shortName(jobFullName) + " #" + number;
        String state = Texts.resultLabel(result);
        return new StatusSign(image, alt, subject + "\n" + state, live, buildUrl);
    }
}
