package fi.digitraffic.tis.vaco.rules.model.gtfs.summary;

import com.opencsv.bean.CsvBindByName;

public class Agency extends CsvBean {

    @CsvBindByName(column = "agency_id")
    String agencyId;

    @CsvBindByName(column = "agency_name")
    String agencyName;

    @CsvBindByName(column = "agency_url")
    String agencyUrl;

    @CsvBindByName(column = "agency_timezone")
    String agencyTimezone;

    @CsvBindByName(column = "agency_lang")
    String agencyLang;

    @CsvBindByName(column = "agency_phone")
    String agencyPhone;

    @CsvBindByName(column = "agency_fare_url")
    String agencyFareUrl;

    @CsvBindByName(column = "agency_email")
    String agencyEmail;

    public Agency() {
    }

    public Agency(String agencyId, String agencyName, String agencyUrl, String agencyTimezone, String agencyLang, String agencyPhone, String agencyFareUrl, String agencyEmail) {
        this.agencyId = agencyId;
        this.agencyName = agencyName;
        this.agencyUrl = agencyUrl;
        this.agencyTimezone = agencyTimezone;
        this.agencyLang = agencyLang;
        this.agencyPhone = agencyPhone;
        this.agencyFareUrl = agencyFareUrl;
        this.agencyEmail = agencyEmail;
    }

    public String getAgencyId() {
        return agencyId;
    }

    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId;
    }

    public String getAgencyName() {
        return agencyName;
    }

    public void setAgencyName(String agencyName) {
        this.agencyName = agencyName;
    }

    public String getAgencyUrl() {
        return agencyUrl;
    }

    public void setAgencyUrl(String agencyUrl) {
        this.agencyUrl = agencyUrl;
    }

    public String getAgencyTimezone() {
        return agencyTimezone;
    }

    public void setAgencyTimezone(String agencyTimezone) {
        this.agencyTimezone = agencyTimezone;
    }

    public String getAgencyLang() {
        return agencyLang;
    }

    public void setAgencyLang(String agencyLang) {
        this.agencyLang = agencyLang;
    }

    public String getAgencyPhone() {
        return agencyPhone;
    }

    public void setAgencyPhone(String agencyPhone) {
        this.agencyPhone = agencyPhone;
    }

    public String getAgencyFareUrl() {
        return agencyFareUrl;
    }

    public void setAgencyFareUrl(String agencyFareUrl) {
        this.agencyFareUrl = agencyFareUrl;
    }

    public String getAgencyEmail() {
        return agencyEmail;
    }

    public void setAgencyEmail(String agencyEmail) {
        this.agencyEmail = agencyEmail;
    }
}
