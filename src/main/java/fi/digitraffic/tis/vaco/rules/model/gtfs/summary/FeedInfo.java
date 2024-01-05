package fi.digitraffic.tis.vaco.rules.model.gtfs.summary;

import com.opencsv.bean.CsvBindByName;

public class FeedInfo extends CsvBean {

    @CsvBindByName(column = "feed_publisher_name")
    String feedPublisherName;

    @CsvBindByName(column = "feed_publisher_url")
    String feedPublisherUrl;

    @CsvBindByName(column = "feed_lang")
    String feedLang;

    @CsvBindByName(column = "default_lang")
    String defaultLang;

    @CsvBindByName(column = "feed_start_date")
    String feedStartDate;

    @CsvBindByName(column = "feed_end_date")
    String feedEndDate;

    @CsvBindByName(column = "feed_version")
    String feedVersion;

    @CsvBindByName(column = "feed_contact_email")
    String feedContactEmail;

    public FeedInfo() {
    }

    public FeedInfo(String feedPublisherName, String feedPublisherUrl, String feedLang, String defaultLang, String feedStartDate, String feedEndDate, String feedVersion, String feedContactEmail, String feedContactUrl) {
        this.feedPublisherName = feedPublisherName;
        this.feedPublisherUrl = feedPublisherUrl;
        this.feedLang = feedLang;
        this.defaultLang = defaultLang;
        this.feedStartDate = feedStartDate;
        this.feedEndDate = feedEndDate;
        this.feedVersion = feedVersion;
        this.feedContactEmail = feedContactEmail;
        this.feedContactUrl = feedContactUrl;
    }

    public String getFeedPublisherName() {
        return feedPublisherName;
    }

    public void setFeedPublisherName(String feedPublisherName) {
        this.feedPublisherName = feedPublisherName;
    }

    public String getFeedPublisherUrl() {
        return feedPublisherUrl;
    }

    public void setFeedPublisherUrl(String feedPublisherUrl) {
        this.feedPublisherUrl = feedPublisherUrl;
    }

    public String getFeedLang() {
        return feedLang;
    }

    public void setFeedLang(String feedLang) {
        this.feedLang = feedLang;
    }

    public String getDefaultLang() {
        return defaultLang;
    }

    public void setDefaultLang(String defaultLang) {
        this.defaultLang = defaultLang;
    }

    public String getFeedStartDate() {
        return feedStartDate;
    }

    public void setFeedStartDate(String feedStartDate) {
        this.feedStartDate = feedStartDate;
    }

    public String getFeedEndDate() {
        return feedEndDate;
    }

    public void setFeedEndDate(String feedEndDate) {
        this.feedEndDate = feedEndDate;
    }

    public String getFeedVersion() {
        return feedVersion;
    }

    public void setFeedVersion(String feedVersion) {
        this.feedVersion = feedVersion;
    }

    public String getFeedContactEmail() {
        return feedContactEmail;
    }

    public void setFeedContactEmail(String feedContactEmail) {
        this.feedContactEmail = feedContactEmail;
    }

    public String getFeedContactUrl() {
        return feedContactUrl;
    }

    public void setFeedContactUrl(String feedContactUrl) {
        this.feedContactUrl = feedContactUrl;
    }

    String feedContactUrl;
}
