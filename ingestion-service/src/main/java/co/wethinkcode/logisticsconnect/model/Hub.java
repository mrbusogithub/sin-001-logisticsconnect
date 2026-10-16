package co.wethinkcode.logisticsconnect.model;

/**
 * Domain model: one row from the CSV represented as a Java object.
 */

public class Hub {

    private String hubId;
    private String province;
    private String sortingCenter;
    // can also be null when the source value is unknown or missing.
    private Boolean active;

    public Hub() {
    }

    // Convenience constructor used when all field values are already available.
    public Hub(String hubId, String province, String sortingCenter, Boolean active) {
        this.hubId = hubId;
        this.province = province;
        this.sortingCenter = sortingCenter;
        this.active = active;
    }

    public String getHubId() {
        return hubId;
    }

    public void setHubId(String hubId) {
        this.hubId = hubId;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getSortingCenter() {
        return sortingCenter;
    }

    public void setSortingCenter(String sortingCenter) {
        this.sortingCenter = sortingCenter;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

}
