package com.nornorhub.fedfinder.core;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class SearchResponse {

    public final List<Job> jobs = new ArrayList<>();
    /** Total matches across all pages. */
    public int totalCount;

    public static SearchResponse parse(String json) throws JSONException {
        SearchResponse r = new SearchResponse();
        JSONObject result = new JSONObject(json).getJSONObject("SearchResult");
        r.totalCount = result.optInt("SearchResultCountAll", 0);
        JSONArray items = result.optJSONArray("SearchResultItems");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item != null && item.has("MatchedObjectDescriptor")) {
                    r.jobs.add(Job.fromSearchItem(item));
                }
            }
        }
        return r;
    }
}
