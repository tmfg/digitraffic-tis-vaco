package fi.digitraffic.tis.vaco.summary.model.gtfs;

import com.opencsv.bean.CsvBindByName;

public class Agency {

    @CsvBindByName(column = "agency_name")
    String agencyName;

    @CsvBindByName(column = "agency_url")
    String agencyUrl;

    @CsvBindByName(column = "agency_phone")
    String agencyPhone;

    @CsvBindByName(column = "agency_email")
    String agencyEmail;

    // These are just for the record what agency.txt can contain (but now not needed)
    //@CsvBindByName(column = "agency_id")
    //String agencyId;

    //@CsvBindByName(column = "agency_timezone")
    //String agencyTimezone;

    //@CsvBindByName(column = "agency_lang", required = false)
    //String agencyLang;

    //@CsvBindByName(column = "agency_fare_url", required = false)
    //String agencyFareUrl;

    public Agency() {
    }

    public Agency(String agencyName, String agencyUrl, String agencyPhone, String agencyEmail) {
        this.agencyName = agencyName;
        this.agencyUrl = agencyUrl;
        this.agencyPhone = agencyPhone;
        this.agencyEmail = agencyEmail;
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

    public String getAgencyPhone() {
        return agencyPhone;
    }

    public void setAgencyPhone(String agencyPhone) {
        this.agencyPhone = agencyPhone;
    }

    public String getAgencyEmail() {
        return agencyEmail;
    }

    public void setAgencyEmail(String agencyEmail) {
        this.agencyEmail = agencyEmail;
    }
}
