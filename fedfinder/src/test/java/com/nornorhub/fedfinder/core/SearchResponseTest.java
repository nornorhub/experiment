package com.nornorhub.fedfinder.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;

public class SearchResponseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private SearchResponse response;

    @Before
    public void setUp() throws IOException, JSONException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("search.json")) {
            response = SearchResponse.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void parsesCounts() {
        assertEquals(1234, response.totalCount);
        assertEquals(3, response.jobs.size());
    }

    @Test
    public void parsesFullJob() {
        Job j = response.jobs.get(0);
        assertEquals("812345600", j.id);
        assertEquals("IT Specialist (INFOSEC)", j.title);
        assertEquals("Veterans Health Administration", j.organization);
        assertEquals("Department of Veterans Affairs", j.department);
        assertEquals("Newark, New Jersey", j.locationDisplay);
        assertEquals("https://www.usajobs.gov:443/job/812345600/apply", j.applyUrl);
        assertEquals("GS-11", j.gradeLabel());
        assertEquals("Full-time", j.schedule);
        assertEquals("Permanent", j.appointmentType);
        assertEquals("2026-10-20", j.openDate);
        assertEquals("2026-10-30", j.closeDate);
        assertEquals("Protect VA networks.", j.summary);
        assertEquals("Monitor systems.\n\nRespond to incidents.", j.duties);
        assertEquals("", j.education);
        assertEquals("United States Citizens", j.whoMayApply);
        assertEquals(List.of("public", "vet"), j.hiringPaths);
        assertTrue(j.telework);
        assertFalse(j.remote);
        assertEquals("2", j.openings);
        assertEquals("Secret", j.securityClearance);
        assertFalse(j.isInternship());
        assertEquals("$74,678–$97,087/yr", Format.pay(j));
        assertEquals("Closes in 7 days", Format.deadline(j, TODAY).replace("⏰ ", ""));
        assertTrue(Format.deadline(j, TODAY).startsWith("⏰"));
    }

    @Test
    public void parsesHourlyInternship() {
        Job j = response.jobs.get(1);
        assertEquals("2 locations", j.locationDisplay);
        assertEquals(List.of("Morristown, New Jersey", "Sandy Hook, New Jersey"), j.locations);
        assertEquals("https://www.usajobs.gov:443/job/812345601", j.applyUrl);
        assertEquals("GS-4–5", j.gradeLabel());
        assertEquals("", j.summary);
        assertFalse(j.remote);
        assertTrue(j.isInternship());
        assertEquals("$18.50–$24.00/hr (≈ $39k–$50k/yr)", Format.pay(j));
        assertEquals("⏰ Closes today", Format.deadline(j, TODAY));
        assertEquals("⏰ Closes tomorrow", Format.deadline(j, TODAY.minusDays(1)));
    }

    @Test
    public void parsesSparseUnpaidRemoteJob() {
        Job j = response.jobs.get(2);
        assertEquals("Unpaid", Format.pay(j));
        assertTrue(j.remote);
        assertTrue(j.isInternship());
        assertEquals("", j.gradeLabel());
        assertNull(j.openDate);
        assertEquals("Closed", Format.deadline(j, TODAY));
        assertEquals(Long.valueOf(-22), Format.daysLeft(j, TODAY));
    }

    @Test
    public void savedJobRoundTrips() throws JSONException {
        Job j = response.jobs.get(0);
        Job again = Job.fromDescriptor(new org.json.JSONObject(j.rawJson));
        assertEquals(j.title, again.title);
        assertEquals(Format.pay(j), Format.pay(again));
    }
}
