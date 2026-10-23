package com.nornorhub.fedfinder.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Map;
import org.json.JSONException;
import org.junit.Test;

public class SearchQueryTest {

    @Test
    public void defaultsAskForFullResults() {
        SearchQuery q = new SearchQuery();
        assertEquals("Fields=Full&ResultsPerPage=25&Page=1", q.toQueryString());
        assertEquals("All jobs", q.describe());
    }

    @Test
    public void buildsAllFilters() {
        SearchQuery q = new SearchQuery();
        q.keyword = " cyber security ";
        q.location = "Newark, NJ";
        q.radiusMiles = 25;
        q.remoteOnly = true;
        q.gradeLow = 7;
        q.gradeHigh = 11;
        q.minSalary = 50000;
        q.schedule = SearchQuery.SCHEDULE_FULL_TIME;
        q.sort = SearchQuery.Sort.CLOSING;
        q.whoMayApply = SearchQuery.WhoMayApply.VETERANS;
        q.page = 3;

        Map<String, String> p = q.params();
        assertEquals("cyber security", p.get("Keyword"));
        assertEquals("Newark, NJ", p.get("LocationName"));
        assertEquals("25", p.get("Radius"));
        assertEquals("True", p.get("RemoteIndicator"));
        assertEquals("vet", p.get("HiringPath"));
        assertEquals("07", p.get("PayGradeLow"));
        assertEquals("11", p.get("PayGradeHigh"));
        assertEquals("50000", p.get("RemunerationMinimumAmount"));
        assertEquals("1", p.get("PositionScheduleTypeCode"));
        assertEquals("closedate", p.get("SortField"));
        assertEquals("Asc", p.get("SortDirection"));
        assertEquals("3", p.get("Page"));
        assertEquals("\"cyber security\" · Newark, NJ (25 mi) · Remote · Veterans · GS-7–11 · $50,000+ · Full-time",
                q.describe());
        assertEquals(
                "Keyword=cyber%20security&LocationName=Newark%2C%20NJ&Radius=25&RemoteIndicator=True&HiringPath=vet"
                        + "&PayGradeLow=07&PayGradeHigh=11&RemunerationMinimumAmount=50000&PositionScheduleTypeCode=1"
                        + "&SortField=closedate&SortDirection=Asc&Fields=Full&ResultsPerPage=25&Page=3",
                q.toQueryString());
    }

    @Test
    public void internshipsOverrideWhoMayApply() {
        SearchQuery q = new SearchQuery();
        q.whoMayApply = SearchQuery.WhoMayApply.PUBLIC;
        q.internships = true;
        assertEquals("student", q.params().get("HiringPath"));
        q.recentGraduates = true;
        assertEquals("student;graduates", q.params().get("HiringPath"));
        assertEquals("All jobs · Internships · Recent grads", q.describe());
    }

    @Test
    public void radiusNeedsALocation() {
        SearchQuery q = new SearchQuery();
        q.radiusMiles = 50;
        assertFalse(q.params().containsKey("Radius"));
    }

    @Test
    public void jsonRoundTrip() throws JSONException {
        SearchQuery q = new SearchQuery();
        q.keyword = "nurse";
        q.internships = true;
        q.whoMayApply = SearchQuery.WhoMayApply.FEDERAL;
        q.sort = SearchQuery.Sort.PAY;
        q.gradeLow = 5;
        SearchQuery back = SearchQuery.fromJson(q.toJson());
        assertEquals(q.toQueryString(), back.toQueryString());
        assertEquals(q.describe(), back.describe());
        assertEquals(SearchQuery.WhoMayApply.FEDERAL, back.whoMayApply);
    }
}
