package com.xibao.iptv;

import java.util.ArrayList;
import java.util.List;

final class Channel {
    final long id;
    final String name;
    final List<String> sources = new ArrayList<>();
    final String group;
    final String tvgId;
    final String logo;
    int sourceIndex = 0;

    Channel(long id, String name, String url, String group, String tvgId, String logo) {
        this.id = id;
        this.name = name;
        addSource(url);
        this.group = group == null || group.isBlank() ? "未分组" : group;
        this.tvgId = tvgId == null ? "" : tvgId;
        this.logo = logo == null ? "" : logo;
    }

    void addSource(String url) {
        if (url != null && !url.isBlank() && !sources.contains(url)) sources.add(url);
    }

    String currentUrl() {
        return sources.isEmpty() ? "" : sources.get(Math.min(sourceIndex, sources.size() - 1));
    }

    boolean moveSource(int delta) {
        if (sources.size() < 2) return false;
        sourceIndex = (sourceIndex + delta + sources.size()) % sources.size();
        return true;
    }
}
